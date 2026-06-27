package dev.gridengineering.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class VoltageConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.LongValue BASE_VOLTAGE;
    private static final ModConfigSpec.LongValue TIER_MULTIPLIER;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("voltage");
        BASE_VOLTAGE = builder
                .comment("Voltage used by the LV tier.")
                .defineInRange("baseVoltage", 65_536L, 1L, Long.MAX_VALUE);
        TIER_MULTIPLIER = builder
                .comment("Multiplier applied for each tier above LV. Example: base 1000 and multiplier 4 gives LV 1000, MV 4000, HV 16000.")
                .defineInRange("tierMultiplier", 8L, 2L, Long.MAX_VALUE);
        builder.pop();
        SPEC = builder.build();
    }

    private VoltageConfig() {
    }

    public static long baseVoltage() {
        return BASE_VOLTAGE.get();
    }

    public static long tierMultiplier() {
        return TIER_MULTIPLIER.get();
    }
}
