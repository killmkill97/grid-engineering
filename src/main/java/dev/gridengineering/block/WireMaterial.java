package dev.gridengineering.block;

import dev.gridengineering.energy.VoltageTiers;

public enum WireMaterial {
    TIN("tin", 0, 1L, 8_000L, 0xC8CDD4),
    LEAD("lead", 0, 2L, 8_000L, 0x68717C),
    NICKEL("nickel", 0, 3L, 6_000L, 0xC2BE98),

    COPPER("copper", 1, 1L, 5_000L, 0xC87533),
    IRON("iron", 1, 2L, 5_000L, 0xD8D8D8),

    STEEL("steel", 2, 3L, 4_000L, 0x737F88),
    GOLD("gold", 2, 1L, 3_000L, 0xFFD34D),
    ELECTRUM("electrum", 2, 2L, 3_000L, 0xE4C260),

    TUNGSTEN("tungsten", 3, 1L, 2_000L, 0xD6D6D6),
    TITANIUM("titanium", 3, 1L, 2_000L, 0x67655D),
    NTT_ALLOY("ntt_alloy", 3, 4L, 2_000L, 0x8D8B85),

    NEUTRONIUM("neutronium", 4, 1L, 2_000L, 0x292D35),
    NBB_ALLOY("nbb_alloy", 4, 4L, 1_000L, 0x587374);

    private final String id;
    private final int voltageTierIndex;
    private final long baseAmps;
    private final long lossPerMeterPerAmpPpm;
    private final int tintColor;

    WireMaterial(
            String id,
            int voltageTierIndex,
            long baseAmps,
            long lossPerMeterPerAmpPpm,
            int tintColor
    ) {
        this.id = id;
        this.voltageTierIndex = voltageTierIndex;
        this.baseAmps = baseAmps;
        this.lossPerMeterPerAmpPpm = lossPerMeterPerAmpPpm;
        this.tintColor = tintColor;
    }

    public String id() {
        return this.id;
    }

    public String voltageTierName() {
        return VoltageTiers.name(this.voltageTierIndex);
    }

    public long ratedVoltage() {
        return VoltageTiers.voltage(this.voltageTierIndex);
    }

    public long baseAmps() {
        return this.baseAmps;
    }

    public long lossPerMeterPerAmpPpm() {
        return this.lossPerMeterPerAmpPpm;
    }

    public int tintColor() {
        return this.tintColor;
    }
}
