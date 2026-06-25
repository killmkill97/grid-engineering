package dev.gridengineering.block;

public enum WireMaterial {
    TIN("tin", "LV", 65_536L, 1L, 8_000L, 0xC8CDD4),
    LEAD("lead", "LV", 65_536L, 2L, 8_000L, 0x68717C),
    NICKEL("nickel", "LV", 65_536L, 3L, 6_000L, 0xC2BE98),

    COPPER("copper", "MV", 524_288L, 1L, 5_000L, 0xC87533),
    IRON("iron", "MV", 524_288L, 2L, 5_000L, 0xD8D8D8),

    STEEL("steel", "HV", 4_194_304L, 3L, 4_000L, 0x737F88),
    GOLD("gold", "HV", 4_194_304L, 1L, 3_000L, 0xFFD34D),
    ELECTRUM("electrum", "HV", 4_194_304L, 2L, 3_000L, 0xE4C260),

    TUNGSTEN("tungsten", "SHV", 268_435_456L, 1L, 2_000L, 0xD6D6D6),
    TITANIUM("titanium", "SHV", 268_435_456L, 1L, 2_000L, 0x67655D),
    NTT_ALLOY("ntt_alloy", "SHV", 268_435_456L, 4L, 2_000L, 0x8D8B85),

    NEUTRONIUM("neutronium", "CHV", 2_147_483_648L, 1L, 2_000L, 0x292D35),
    NBB_ALLOY("nbb_alloy", "CHV", 2_147_483_648L, 4L, 1_000L, 0x587374);

    private final String id;
    private final String voltageTierName;
    private final long ratedVoltage;
    private final long baseAmps;
    private final long lossPerMeterPerAmpPpm;
    private final int tintColor;

    WireMaterial(
            String id,
            String voltageTierName,
            long ratedVoltage,
            long baseAmps,
            long lossPerMeterPerAmpPpm,
            int tintColor
    ) {
        this.id = id;
        this.voltageTierName = voltageTierName;
        this.ratedVoltage = ratedVoltage;
        this.baseAmps = baseAmps;
        this.lossPerMeterPerAmpPpm = lossPerMeterPerAmpPpm;
        this.tintColor = tintColor;
    }

    public String id() {
        return this.id;
    }

    public String voltageTierName() {
        return this.voltageTierName;
    }

    public long ratedVoltage() {
        return this.ratedVoltage;
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
