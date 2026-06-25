package dev.gridengineering.block;

public enum WireCoating {
    BARE("", 0, 10L),
    COATED("coated_", 2, 1L);

    private final String idPrefix;
    private final int extraModelPixels;
    private final long lossMultiplier;

    WireCoating(String idPrefix, int extraModelPixels, long lossMultiplier) {
        this.idPrefix = idPrefix;
        this.extraModelPixels = extraModelPixels;
        this.lossMultiplier = lossMultiplier;
    }

    public String idPrefix() {
        return this.idPrefix;
    }

    public int extraModelPixels() {
        return this.extraModelPixels;
    }

    public long applyLossMultiplier(long baseLossPerMeterPerAmpPpm) {
        return Math.multiplyExact(baseLossPerMeterPerAmpPpm, this.lossMultiplier);
    }

    public boolean isCoated() {
        return this == COATED;
    }
}
