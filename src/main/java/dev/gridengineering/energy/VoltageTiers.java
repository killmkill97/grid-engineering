package dev.gridengineering.energy;

public final class VoltageTiers {
    private static final String[] NAMES = {"LV", "MV", "HV", "SHV", "CHV"};
    private static final long[] VALUES = {
            65_536L,
            524_288L,
            4_194_304L,
            268_435_456L,
            2_147_483_648L
    };

    private VoltageTiers() {
    }

    public static int count() {
        return VALUES.length;
    }

    public static int clampIndex(int index) {
        return Math.max(0, Math.min(index, VALUES.length - 1));
    }

    public static String name(int index) {
        return NAMES[clampIndex(index)];
    }

    public static long voltage(int index) {
        return VALUES[clampIndex(index)];
    }

    public static int tierIndexForVoltage(long voltage) {
        if (voltage <= VALUES[0]) {
            return 0;
        }

        for (int upperIndex = 1; upperIndex < VALUES.length; upperIndex++) {
            long lowerVoltage = VALUES[upperIndex - 1];
            long upperVoltage = VALUES[upperIndex];
            if (voltage == upperVoltage) {
                return upperIndex;
            }
            if (voltage < upperVoltage) {
                long midpoint = lowerVoltage + (upperVoltage - lowerVoltage) / 2L;
                return voltage <= midpoint
                        ? upperIndex
                        : Math.min(upperIndex + 1, VALUES.length - 1);
            }
        }
        return VALUES.length - 1;
    }

    public static String nameForVoltage(long voltage) {
        return name(tierIndexForVoltage(voltage));
    }
}
