package dev.gridengineering.laser;

import dev.gridengineering.config.LaserConfig;

public enum LaserTier {
    MK1(1),
    MK2(2),
    MK3(3),
    MK4(4),
    MK5(5);

    private final int level;

    LaserTier(int level) {
        this.level = level;
    }

    public int level() {
        return this.level;
    }

    public String id() {
        return "mk" + this.level;
    }

    public long maxVoltage() {
        return LaserConfig.tierMaxVoltage(this);
    }

    public int maxDistance() {
        return LaserConfig.tierMaxDistance(this);
    }

    public long lossPerBlockPerAmpPpm() {
        return Math.round(LaserConfig.tierLossPerBlockPerAmpere(this) * 10_000.0D);
    }

    public int color() {
        return LaserConfig.tierColor(this);
    }

    public boolean isRainbow() {
        return this == MK5 && LaserConfig.mk5Rainbow();
    }
}
