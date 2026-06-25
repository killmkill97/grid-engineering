# Grid Engineering

[한국어](README.md) | [English](README_EN.md)

Grid Engineering is a power transmission and electrical grid design mod for
Minecraft 1.21.1 NeoForge.

The project is currently an early prototype for validating connected wires, FE
transmission, amperage limits, wire fires, and power control devices.

## Currently Implemented

- Six-direction connected copper wires with a central 2×2-pixel cross-section
- Automatic connections between adjacent copper wires
- Extend or retract a wire end into an empty direction by holding copper wire,
  sneaking, and right-clicking an existing wire face
- Cut or restore selected connections with wire cutters
- Cycle a branch through automatic → input → output port modes by sneaking and
  right-clicking it with wire cutters
- Input ports are shown in bright copper, while output ports are shown in dark copper
- Tin wire supports LV 65,536 V at 1 A; copper wire supports MV 524,288 V at 1 A
- Wire materials: tin, lead, nickel, copper, iron, gold, electrum, steel,
  tungsten, titanium, NTT alloy, neutronium, and NBB alloy
- Every material has 1 mm, 2 mm, 4 mm, 8 mm, and 16 mm variants; current
  capacity is the material's base amperage multiplied by its thickness
- Every wire is rendered by tinting a shared iron-block-based `block/cable` texture
  with the material color
- Route-based transmission loss of 0.2%/m/A for copper wire and 0.5%/m/A for tin wire
- When multiple devices are connected, power is distributed evenly by using
  Power of Two Choices to favor the device that has received less power
- If voltage or amperage exceeds a wire's rating, one wire is destroyed and
  replaced by fire
- Lower voltages can travel normally through higher-voltage wires
- If actual current exceeds 1 A, one copper wire in the connected network is
  destroyed by an electrical fire
- The network monitor displays voltage, current/max amperage, input/delivered/lost
  FE/t, and source/destination coordinates
- The current regulator accepts power through its front face and distributes its
  configured voltage × current evenly through the other five faces
- The current regulator is a pass-through device with no world-persistent energy
  storage; output disappears immediately when input stops
- The regulator's input face can be oriented in all six directions, including up
  and down, and it automatically extracts power from a device in front of that face
- The regulator accepts arbitrary numeric voltage input and rounds the wire rating
  up to the next voltage tier
- The test battery and current regulator provide dedicated GUIs for voltage tier
  and 1–64 A configuration
- The test battery can also be configured to output an arbitrary numeric voltage
  and amperage
- A dedicated Grid Engineering creative tab using the network monitor as its icon
- Batteries, regulators, and wire networks use a custom `long`-based internal
  power system; FE conversion occurs only at the boundary with external mods
- Cut states are saved to the world and synchronized to clients
- Voltage × current based FE transmission through connected wire networks
- An FE test battery with input, infinite output, and buffer modes
- Mekanism, the standard EMI release, and Jade are included in the development environment
- General material recipes are not provided; only wire thickness combination and
  splitting recipes are included

Wires, cutters, the network monitor, the test battery, and the current regulator
can be found in the Grid Engineering creative tab.
Right-clicking a wire with the network monitor displays the wire network's live
transmission status in chat.
Right-clicking a connected wire branch with wire cutters toggles that connection.
Sneak-right-clicking configures the branch's port mode. Input means power travels
from the wire network into a device; output means power is extracted from a device
into the wire network.

The test battery defaults to FE input mode.

- Right-click: open the dedicated GUI
- GUI: configure input/infinite output/buffer mode, voltage tier, and 1–64 A

The current regulator uses the model's front face as its input and the back,
sides, top, and bottom as its five outputs. Its configured voltage × current is
the maximum output per tick.

Setting an infinite-output battery to LV 1 A and connecting it to an input battery
transfers 65,536 FE per tick. Mekanism devices connect the same way when a wire is
attached to a face exposing the NeoForge FE capability. The current transmission
implementation is intended to validate compatibility before the final grid engine.

## Development Environment

- JDK 21
- Minecraft 1.21.1
- NeoForge 21.1.234
- ModDevGradle

## Opening in IntelliJ IDEA

1. Open this directory in IntelliJ IDEA.
2. Approve importing it as a Gradle project.
3. Set the Gradle JVM to JDK 21.
4. After the initial synchronization completes, run `runClient` or the generated
   Minecraft Client configuration.

Command-line build:

```powershell
.\gradlew.bat build
```

See [docs/IMPLEMENTATION_PLAN_EN.md](docs/IMPLEMENTATION_PLAN_EN.md) for the full
implementation plan.
