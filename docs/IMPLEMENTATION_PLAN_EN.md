# Grid Engineering Implementation Plan

[한국어](IMPLEMENTATION_PLAN.md) | [English](IMPLEMENTATION_PLAN_EN.md)

## 1. Core Principles

- The target platform is Minecraft 1.21.1, NeoForge, and Java 21.
- Machines do not have voltage ratings. Voltage and amperage matter only within
  the transmission grid.
- Internal energy, power, and accumulated loss calculations must not use `int`.
- The standard representation for ordinary tick operations is `long`. Addition
  and multiplication use `Math.addExact`, `Math.multiplyExact`, and similar
  methods to detect overflow.
- Calculations whose intermediate multiplication may exceed the `long` range,
  such as percentage loss, use `BigInteger`; only the validated final result is
  converted to `long`.
- Data definitions exceeding the `long` range are rejected during loading.
  Silent saturation, wrapping, and `int` casts are not allowed.
- FE is used only in the compatibility layer. The `int`-based input and output of
  FE capabilities is split into multiple safe transfer chunks while internal
  network state remains `long`-based.

## 2. Module and Package Structure

```text
dev.gridengineering
├─ api                 Public API and events
├─ energy              Internal energy values, voltage, amperage, and power calculations
├─ network             Topology, segment graph, routing, and network manager
├─ conductor           Wire blocks, block entities, and data definitions
├─ device              Transformers, FE ports, analyzers, and monitors
├─ loss                Loss models and registry
├─ data                JSON loaders, validation, and synchronization
├─ config              Server and client configuration
├─ persistence         SavedData and data migration
└─ client              Screens, overlays, and rendering
```

The `api` package must not depend on implementation packages. Compatibility mods
use only API interfaces and registration events and do not access internal
network classes directly.

## 3. Power Network Structure

### World Manager

Each `ServerLevel` has one `PowerNetworkManager`. The manager owns:

- `networkId -> PowerNetwork`
- `BlockPos.asLong() -> nodeId`
- Per-chunk node indexes
- A topology change dirty queue
- A power recalculation dirty queue
- The next network ID

Clients have no authority to perform calculations. Only snapshots required by
analyzers and monitors are synchronized from the server.

### Nodes and Segments

- `ConductorNode`: an actual connection point such as a wire, port, or transformer face
- `JunctionNode`: a branch or a point where material/rating changes
- `EndpointNode`: generation/consumption FE ports and Grid Engineering devices
- `TransmissionSegment`: a straight or non-branching group of wires between two important nodes

To avoid scanning every block every tick, consecutive identical wires are
compressed into a single segment. Each segment caches its length, voltage limit,
amperage limit, loss model, and current load.

### Merging and Splitting

- Placing a wire immediately merges adjacent networks.
- Removing a wire marks only the affected existing network as a split candidate.
- Splitting is processed within that component using a budget-limited BFS/DFS.
- Chunk unloading is not treated as destruction. Boundary connections are stored
  in a dormant state.
- While topology is changing, the last confirmed safe snapshot is used or
  transmission is temporarily suspended.

A transformer is an active device whose two ports belong to separate wired
networks. Voltage conversion therefore does not merge those networks.

## 4. Wire Data Structure

Wire types are loaded from data definitions instead of being fixed in a code enum.

```text
ConductorDefinition
- id: ResourceLocation
- voltageTier: ResourceLocation
- maxAmperage: long
- lossModel: ResourceLocation
- lossParameters: Map<String, value>
- overcurrentProfile: ResourceLocation or global default
- appearance: model/texture reference
- tags: Set<ResourceLocation>
```

Only validated immutable objects are used at runtime. During data loading, the
system verifies `maxAmperage > 0`, a valid voltage tier, a valid loss model, and
a calculable maximum power value.

The final structure uses a shared wire block/item that stores a definition ID so
dynamically added wires can also be used. The definition ID is stored in the block
entity and item data component. Built-in wires are default data on top of the same
system.

## 5. Power Calculation

### Value Model

```text
PowerPacket
- voltage: long
- amperage: long
- powerPerTick: long  // multiplyExact(voltage, amperage)
```

Whenever possible, energy is represented as `powerPerTick` plus a tick count.
Only devices that require stored energy or accumulated totals hold a `long energy`
value.

### Tick Processing Order

1. Input ports and transformers announce the supply available during this tick.
2. Output ports announce demand and an optional priority.
3. A route is selected from the cached segment graph.
4. The route's minimum voltage limit and remaining amperage are applied.
5. Loss is calculated from route length and per-segment loss.
6. The amount accepted by the FE destination is simulated.
7. Actual extraction and insertion are committed.
8. Input, output, loss, efficiency, and overcurrent snapshots are updated.

Expensive global max-flow optimization across all supply and demand is avoided in
the default tick loop. The initial version uses priority, fair round-robin
distribution, and remaining segment capacity. Routes are cached per destination
and invalidated only when topology or wire definitions change.

### Loss Models

The target public interface is:

```text
LossModel.calculate(inputPower, segmentLength, parameters) -> LossResult
```

Default implementations:

- `percentage`: lose a specified percentage per reference block count
- `fixed`: lose a specified fixed amount of power per reference block count
- `lossless`: zero loss

Percentages are stored as integer fractions (`numerator/denominator`) or basis
points rather than floating-point values. Segment rounding rules are fixed instead
of configurable to guarantee reproducibility and testability.

### Overcurrent

When the actual amperage through a route segment exceeds its rating, one of these
policies is applied:

- Additional loss
- Efficiency limit
- Durability damage
- Wire destruction
- Ignore

Destruction and damage do not modify the world during calculation. They are added
to a post-processing queue to prevent topology from changing during a graph
traversal in the same tick.

## 6. Persistence

- Wire definition IDs, damage, and device settings are stored in block entities.
- Wireless/interdimensional links, stable ID allocators, and required global
  state are stored in `SavedData`.
- The complete graph of ordinary wired networks is not persisted. It is rebuilt
  from blocks and chunk indexes in loaded chunks to avoid stale position data.
- Network IDs are diagnostic and are not used as persistent gameplay identifiers.
- Saved data includes a schema version and uses explicit migration steps separate
  from DataFixer.

## 7. Performance Strategy

- Topology is marked dirty only by block placement/removal, neighbor changes, and
  chunk load/unload events.
- The entire network is not scanned every tick.
- Continuous wires are compressed into segments, and routes and network
  statistics are cached.
- Topology rebuilding has a per-tick node/time budget.
- Networks with no power flow and no state changes become dormant.
- FE capability lookup results are invalidated only on neighbor changes.
- Primitive-key collections and `BlockPos.asLong()` reduce object allocation.
- Monitor packets are rate-limited, size-limited, and sent only for players
  observing the network.
- Profiling metrics expose processed network counts, rebuilt node counts, route
  cache hit rates, transferred power, and packet volume.

## 8. API Structure

Initial public API candidates:

- `GridEngineeringAPI`
- `VoltageTier`
- `ConductorDefinition`
- `GridPowerPort`
- `PowerOffer` / `PowerDemand`
- `LossModel`
- `LossModelType`
- `RegisterGridEngineeringTypesEvent`
- A read-only `PowerNetworkView` for querying network state

The API provides a NeoForge capability so custom devices can participate as
supply or demand ports. The FE adapter is a separate implementation so the
internal port API does not depend on FE types.

Compatibility principles:

- Public types are immutable value objects whenever possible.
- Registration IDs use `ResourceLocation`.
- Read-only views are returned instead of implementation objects.
- The API version is specified separately from the mod version.
- Calculation events have restricted cancellation/modification scope to protect
  server performance and determinism.

## 9. Configuration Structure

Configuration is separated by responsibility.

### NeoForge Server Configuration

Per-world `serverconfig/gridengineering-server.toml`:

- Default overcurrent policy
- Topology recalculation budget
- Transmission operation budget per network
- FE input/output chunk and invocation limits
- Monitor update interval
- Whether wire damage and destruction are enabled
- Operational limits for quantum/interdimensional transmission

Client configuration:

- Analyzer display format
- Overlay position and update frequency
- Wire state rendering quality

### Data-Driven Configuration

Data pack JSON:

```text
data/<namespace>/gridengineering/voltage_tier/*.json
data/<namespace>/gridengineering/conductor/*.json
data/<namespace>/gridengineering/overcurrent_profile/*.json
data/<namespace>/gridengineering/device_profile/*.json
```

Voltage tiers and wires are added through JSON. Server TOML controls only
operational policy. This division avoids forcing list-like content into TOML and
allows data pack overrides, resource packs, and server synchronization.

Default voltages:

| ID | Display Name | Voltage |
|---|---|---:|
| `lv` | Low Voltage | 65,536 |
| `mv` | Medium Voltage | 524,288 |
| `hv` | High Voltage | 4,194,304 |
| `shv` | Super High Voltage | 268,435,456 |
| `chv` | Critical High Voltage | 2,147,483,648 |

## 10. Data-Driven Extension and Synchronization

- JSON is parsed and all references are validated at server start or data pack reload.
- Invalid definitions produce clear errors containing the file path and field.
- A compressed snapshot of server-approved definitions is sent to connecting clients.
- Clients use server definitions only for UI and rendering.
- If a definition disappears after a reload, affected wires enter a safe
  `missing definition` state and stop transmitting instead of immediately
  damaging the world.
- Loss models and device types are registered through code/API, while actual
  values and instances are registered through JSON.

## 11. Implementation Phases

### Phase 0 — Project Foundation

- NeoForge MDK, Java 21, and IntelliJ/Gradle setup
- Mod entry point and metadata
- Design documents and CI build

### Phase 1 — Numeric and Data Layers

- `long`-based value objects and overflow tests
- Voltage tier/wire/loss JSON loaders
- Default data and validation error reporting

### Phase 2 — Wires and Topology

- Shared wire block/item/block entity
- Connection rules, chunk indexes, and network merging/splitting
- Segment compression and network statistics

### Phase 3 — FE Boundary and Transmission

- Input/output FE ports
- Supply/demand collection, amperage capacity, and basic routing
- Percentage/fixed/lossless models

### Phase 4 — Player Feedback

- Power analyzer
- Network state snapshots and monitor UI
- Debug commands and profiling metrics

### Phase 5 — Risk and Conversion

- Overcurrent policies and wire damage
- Transformers
- Detailed transmission tests and server load tests

### Phase 6 — Endgame Systems and API Stabilization

- Superconducting cables
- Quantum power tunnels
- Interdimensional transmitters
- Public API documentation, compatibility tests, and migration policy

## 12. Validation Plan

- Unit tests: overflow, power formula, loss rounding, and amperage limits
- Property-based tests: verify that output + loss never exceeds input
- GameTests: placement/removal/splitting/merging, chunk boundaries, and transformers
- Compatibility tests: representative FE generators, storage devices, and consumers
- Dedicated server tests: prevent accidental client-class loading
- Load tests: thousands to tens of thousands of wires, many independent networks,
  and repeated chunk loading
- Save/restart/data pack reload and missing-definition recovery tests
