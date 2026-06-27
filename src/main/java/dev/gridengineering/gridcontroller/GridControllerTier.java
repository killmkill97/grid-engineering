package dev.gridengineering.gridcontroller;

import dev.gridengineering.energy.VoltageTiers;

public enum GridControllerTier {
    MK1(1, 0, 128L),
    MK2(2, 1, 512L),
    MK3(3, 2, 2_048L),
    MK4(4, 3, 8_192L),
    MK5(5, 4, 32_768L);

    private final int level;
    private final int voltageTierIndex;
    private final long maxAmps;

    GridControllerTier(int level, int voltageTierIndex, long maxAmps) {
        this.level = level;
        this.voltageTierIndex = voltageTierIndex;
        this.maxAmps = maxAmps;
    }

    public int level() {
        return this.level;
    }

    public String id() {
        return "mk" + this.level;
    }

    public String voltageTierName() {
        return VoltageTiers.name(this.voltageTierIndex);
    }

    public long maxVoltage() {
        return VoltageTiers.voltage(this.voltageTierIndex);
    }

    public long maxAmps() {
        return this.maxAmps;
    }

    public long maxPower() {
        long voltage = this.maxVoltage();
        if (voltage > Long.MAX_VALUE / this.maxAmps) {
            return Long.MAX_VALUE;
        }
        return voltage * this.maxAmps;
    }
}
