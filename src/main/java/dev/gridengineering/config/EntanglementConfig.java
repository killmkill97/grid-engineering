package dev.gridengineering.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common configuration for Entanglement Link transmission.
 */
public final class EntanglementConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue FREE_CHUNK_RADIUS;
    private static final ModConfigSpec.DoubleValue LOSS_PER_CHUNK_PERCENT;
    private static final ModConfigSpec.IntValue CROSS_DIMENSION_MINIMUM_CHUNK_RADIUS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("entanglement");
        FREE_CHUNK_RADIUS = builder
                .comment("Chunk radius that Entanglement Links can cross without power loss. Radius uses max(abs(deltaChunkX), abs(deltaChunkZ)). Example: 2 means radius 0-2 is lossless, radius 3 loses one chunk of loss.")
                .defineInRange("freeChunkRadius", 2, 0, 1_000_000);
        LOSS_PER_CHUNK_PERCENT = builder
                .comment("Power loss percentage for each lossy chunk of radius. Set to 0 for lossless Entanglement Links.")
                .defineInRange("lossPerChunkPercent", 10.0D, 0.0D, 100.0D);
        CROSS_DIMENSION_MINIMUM_CHUNK_RADIUS = builder
                .comment("Minimum virtual chunk radius used when the paired Entanglement Links are in different dimensions. Set to 0 to use only X/Z chunk coordinate distance.")
                .defineInRange("crossDimensionMinimumChunkRadius", 16, 0, 1_000_000);
        builder.pop();
        SPEC = builder.build();
    }

    private EntanglementConfig() {
    }

    public static int freeChunkRadius() {
        return FREE_CHUNK_RADIUS.get();
    }

    public static double lossPerChunkPercent() {
        return LOSS_PER_CHUNK_PERCENT.get();
    }

    public static int crossDimensionMinimumChunkRadius() {
        return CROSS_DIMENSION_MINIMUM_CHUNK_RADIUS.get();
    }
}
