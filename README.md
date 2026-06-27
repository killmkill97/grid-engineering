# Grid Engineering

Grid Engineering is a Minecraft 1.21.1 NeoForge mod about building better power
grids.

Instead of adding new machines or production chains, it focuses on the
infrastructure between machines: wires, voltage tiers, amperage limits,
transmission loss, transformers, controllers, and long-distance power delivery.

The mod is designed for players who want FE-compatible power systems to have
more engineering depth without becoming as punishing or complex as GregTech.
Machines still consume normal FE, but the grid itself uses its own internal
long-based power calculations so very large late-game power values are not
limited by FE's integer range.

## Core Ideas

- Machines do not have voltage tiers.
- Wires and grid infrastructure do have voltage and amperage limits.
- Power is calculated from voltage and current.
- Long-distance transmission can lose power depending on the wire.
- Higher voltage is useful for moving large amounts of power efficiently.
- Grid design matters, but machines are not meant to explode from overvoltage.
- FE compatibility is handled at the boundary with other mods.

In short: Grid Engineering is not about protecting every machine from the wrong
voltage. It is about designing an efficient power network.

## Current Features

- Connected wire networks with side-based connections
- Multiple wire materials and wire thicknesses
- Bare and coated wire variants
- Voltage tiers from LV to CHV
- Configurable voltage scaling
- Amperage limits on wires
- Wire fires when a wire receives too much voltage or current
- Route-based transmission loss
- Power distribution using Power of Two Choices
- Network monitor item for inspecting live grid state
- Wire cutter for cutting wire connections and changing side modes
- Creative power block for testing FE input, output, buffer, and trash behavior
- Current regulator for limiting voltage and current output
- Grid Controller blocks for controller-based branch load distribution
- Laser transformers for long-distance power transmission
- Laser Transmission Anchor blocks for keeping laser links stable across chunks
- Jade integration for displaying useful grid information
- EMI and Mekanism included in the development runtime for compatibility testing

## Voltage and Power

Grid Engineering models power as:

```text
Power = Voltage × Current
```

Example:

```text
65,536 V × 1 A = 65,536 FE/t
524,288 V × 2 A = 1,048,576 FE/t
```

Internally, the mod does not rely on FE's integer-sized energy values. It uses
long-based internal power values and converts to FE only when interacting with
external FE blocks.

## Configuration

Voltage tiers can be scaled with:

```toml
[voltage]
baseVoltage = 65536
tierMultiplier = 8
```

For example, setting:

```toml
baseVoltage = 1000
tierMultiplier = 4
```

produces:

```text
LV  = 1,000 V
MV  = 4,000 V
HV  = 16,000 V
SHV = 64,000 V
CHV = 256,000 V
```

Laser behavior and Grid Controller failure behavior also have their own config
files.

## Compatibility

Grid Engineering is intended to work with normal FE-based mods. External
machines only see FE input and output; voltage and amperage are handled inside
the Grid Engineering network.

The development environment currently includes Mekanism, EMI, and Jade for
testing common modded-power interactions.

## Development

- Minecraft: 1.21.1
- Mod loader: NeoForge
- Java: 21
- License: MIT

Build:

```powershell
.\gradlew.bat build
```

The built jar is generated in:

```text
build/libs
```

## Status

Grid Engineering is still in active development. Systems, balance values, and
internal APIs may change between versions.
