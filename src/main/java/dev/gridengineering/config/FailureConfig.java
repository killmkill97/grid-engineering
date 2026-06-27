package dev.gridengineering.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class FailureConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.EnumValue<FailureAction> GRID_CONTROLLER_FAILURE;
    private static final ModConfigSpec.EnumValue<FailureAction> LASER_TRANSFORMER_FAILURE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("gridController");
        GRID_CONTROLLER_FAILURE = builder
                .comment("What happens when a Grid Controller receives too much voltage.")
                .defineEnum("failureAction", FailureAction.EXPLODE);
        builder.pop();

        builder.push("laserTransformer");
        LASER_TRANSFORMER_FAILURE = builder
                .comment("What happens when a Laser Transformer receives more voltage than its tier can handle.")
                .defineEnum("failureAction", FailureAction.EXPLODE);
        builder.pop();

        SPEC = builder.build();
    }

    private FailureConfig() {
    }

    public static FailureAction gridControllerFailure() {
        return GRID_CONTROLLER_FAILURE.get();
    }

    public static FailureAction laserTransformerFailure() {
        return LASER_TRANSFORMER_FAILURE.get();
    }

    public enum FailureAction {
        BREAK,
        EXPLODE
    }
}
