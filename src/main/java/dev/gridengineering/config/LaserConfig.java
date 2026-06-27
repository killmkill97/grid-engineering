package dev.gridengineering.config;

import dev.gridengineering.energy.VoltageTiers;
import dev.gridengineering.laser.LaserTier;
import java.util.EnumMap;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class LaserConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue RENDER_DISTANCE;
    private static final ModConfigSpec.BooleanValue MK5_RAINBOW;
    private static final Map<LaserTier, ModConfigSpec.IntValue> TIER_MAX_DISTANCES =
            new EnumMap<>(LaserTier.class);
    private static final Map<LaserTier, ModConfigSpec.DoubleValue> TIER_LOSSES =
            new EnumMap<>(LaserTier.class);
    private static final Map<LaserTier, ModConfigSpec.IntValue> TIER_COLORS =
            new EnumMap<>(LaserTier.class);

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("laser");
        RENDER_DISTANCE = builder
                .comment("Maximum client render distance for laser beams.")
                .defineInRange("renderDistance", 512, 16, 4096);
        MK5_RAINBOW = builder
                .comment("Whether Mk5 beams cycle through rainbow colors.")
                .define("mk5Rainbow", true);

        int[] defaultDistances = {8, 16, 32, 64, 16_384};
        double[] defaultLosses = {0.1D, 0.05D, 0.025D, 0.0125D, 0.0D};
        int[] defaultColors = {0xFF2A2A, 0xFF8A1A, 0xB144FF, 0x3185FF, 0xFFFFFF};
        builder.push("tiers");
        for (LaserTier tier : LaserTier.values()) {
            String tierId = tier.id();
            TIER_MAX_DISTANCES.put(
                    tier,
                    builder.comment("Maximum link distance for " + tierId + ".")
                            .defineInRange(
                                    tierId + "MaxDistance",
                                    defaultDistances[tier.ordinal()],
                                    1,
                                    16_384
                            )
            );
            TIER_LOSSES.put(
                    tier,
                    builder.comment(
                                    "Loss percentage per beam block per ampere for " + tierId + "."
                            )
                            .defineInRange(
                                    tierId + "LossPerBlockPerAmpere",
                                    defaultLosses[tier.ordinal()],
                                    0.0D,
                                    100.0D
                            )
            );
            TIER_COLORS.put(
                    tier,
                    builder.comment("RGB beam color for " + tierId + " as a decimal integer.")
                            .defineInRange(
                                    tierId + "Color",
                                    defaultColors[tier.ordinal()],
                                    0,
                                    0xFFFFFF
                            )
            );
        }
        builder.pop();
        builder.pop();
        SPEC = builder.build();
    }

    private LaserConfig() {
    }

    public static int renderDistance() {
        return RENDER_DISTANCE.get();
    }

    public static boolean mk5Rainbow() {
        return MK5_RAINBOW.get();
    }

    public static long tierMaxVoltage(LaserTier tier) {
        return tier == LaserTier.MK5
                ? Long.MAX_VALUE
                : VoltageTiers.voltage(tier.level());
    }

    public static int tierMaxDistance(LaserTier tier) {
        return TIER_MAX_DISTANCES.get(tier).get();
    }

    public static double tierLossPerBlockPerAmpere(LaserTier tier) {
        return TIER_LOSSES.get(tier).get();
    }

    public static int tierColor(LaserTier tier) {
        return TIER_COLORS.get(tier).get();
    }
}
