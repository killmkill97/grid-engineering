package dev.gridengineering.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class GridControllerConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.EnumValue<FailureMode> FAILURE_MODE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("gridController");
        FAILURE_MODE = builder
                .comment("What happens when a power controller receives too much voltage or current.")
                .defineEnum("failureMode", FailureMode.EXPLODE);
        builder.pop();
        SPEC = builder.build();
    }

    private GridControllerConfig() {
    }

    public static FailureMode failureMode() {
        return FAILURE_MODE.get();
    }

    public enum FailureMode {
        BREAK,
        EXPLODE
    }
}
