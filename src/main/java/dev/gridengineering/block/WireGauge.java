package dev.gridengineering.block;

public enum WireGauge {
    MM_1(1, 2),
    MM_2(2, 4),
    MM_4(4, 6),
    MM_8(8, 8),
    MM_16(16, 10);

    private final int millimeters;
    private final int modelPixels;

    WireGauge(int millimeters, int modelPixels) {
        this.millimeters = millimeters;
        this.modelPixels = modelPixels;
    }

    public int millimeters() {
        return this.millimeters;
    }

    public int modelPixels() {
        return this.modelPixels;
    }

    public float apothem() {
        return this.modelPixels / 32.0F;
    }

    public float apothem(WireCoating coating) {
        return (this.modelPixels + coating.extraModelPixels()) / 32.0F;
    }

    public String suffix() {
        return this == MM_1 ? "" : "_" + this.millimeters + "mm";
    }

    public String modelName() {
        return "wire_" + this.millimeters + "mm";
    }
}
