package dev.gridengineering.energy;

import dev.gridengineering.config.VoltageConfig;

public final class VoltageTiers {
    private static final String[] NAMES = {"LV", "MV", "HV", "SHV", "CHV"};

    private VoltageTiers() {
    }

    public static int count() {
        return NAMES.length;
    }

    public static int clampIndex(int index) {
        return Math.max(0, Math.min(index, NAMES.length - 1));
    }

    public static String name(int index) {
        return NAMES[clampIndex(index)];
    }

    public static long voltage(int index) {
        long value = Math.max(1L, VoltageConfig.baseVoltage());
        long multiplier = Math.max(2L, VoltageConfig.tierMultiplier());
        int clamped = clampIndex(index);
        for (int step = 0; step < clamped; step++) {
            if (value > Long.MAX_VALUE / multiplier) {
                return Long.MAX_VALUE;
            }
            value *= multiplier;
        }
        return value;
    }

    public static int tierIndexForVoltage(long voltage) {
        if (voltage <= voltage(0)) {
            return 0;
        }

        for (int upperIndex = 1; upperIndex < NAMES.length; upperIndex++) {
            long lowerVoltage = voltage(upperIndex - 1);
            long upperVoltage = voltage(upperIndex);
            if (upperVoltage <= lowerVoltage) {
                continue;
            }
            if (voltage == upperVoltage) {
                return upperIndex;
            }
            if (voltage < upperVoltage) {
                long midpoint = lowerVoltage + (upperVoltage - lowerVoltage) / 2L;
                return voltage <= midpoint
                        ? upperIndex
                        : Math.min(upperIndex + 1, NAMES.length - 1);
            }
        }
        return NAMES.length - 1;
    }

    public static String nameForVoltage(long voltage) {
        return name(tierIndexForVoltage(voltage));
    }
}
