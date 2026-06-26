package dev.gridengineering.gridcontroller;

public enum GridControllerTier {
    MK1(1, "LV", 65_536L, 128L),
    MK2(2, "MV", 524_288L, 512L),
    MK3(3, "HV", 4_194_304L, 2_048L),
    MK4(4, "SHV", 268_435_456L, 8_192L),
    MK5(5, "CHV", 2_147_483_648L, 32_768L);

    private final int level;
    private final String voltageTierName;
    private final long maxVoltage;
    private final long maxAmps;

    GridControllerTier(int level, String voltageTierName, long maxVoltage, long maxAmps) {
        this.level = level;
        this.voltageTierName = voltageTierName;
        this.maxVoltage = maxVoltage;
        this.maxAmps = maxAmps;
    }

    public int level() {
        return this.level;
    }

    public String id() {
        return "mk" + this.level;
    }

    public String voltageTierName() {
        return this.voltageTierName;
    }

    public long maxVoltage() {
        return this.maxVoltage;
    }

    public long maxAmps() {
        return this.maxAmps;
    }

    public long maxPower() {
        return Math.multiplyExact(this.maxVoltage, this.maxAmps);
    }
}
