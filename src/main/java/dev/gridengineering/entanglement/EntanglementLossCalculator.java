package dev.gridengineering.entanglement;

import dev.gridengineering.config.EntanglementConfig;
import java.math.BigInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Centralized loss model for Entanglement Link transmission.
 *
 * <p>The numbers are deliberately isolated here so future balancing can change
 * the behavior without touching the block entity or pairing code.</p>
 */
public final class EntanglementLossCalculator {
    /**
     * Parts-per-million scale used to avoid floating-point power math.
     */
    private static final long PERCENT_SCALE_PPM = 1_000_000L;

    /**
     * Converts one percent into ppm. 100% is 1,000,000 ppm.
     */
    private static final long PPM_PER_PERCENT = 10_000L;

    /**
     * Maximum useful loss coefficient. Values above this are capped to full loss.
     */
    private static final long MAX_LOSS_PPM = PERCENT_SCALE_PPM;

    private EntanglementLossCalculator() {
    }

    /**
     * Calculates chunk-radius distance between two links.
     *
     * <p>The radius is measured as max(abs(deltaChunkX), abs(deltaChunkZ)), so a
     * value of 2 means "two chunks outward from the source chunk" in the usual
     * square chunk-radius sense. Cross-dimensional links use the greater value
     * of their X/Z chunk coordinate radius and the configured virtual
     * cross-dimensional minimum radius.</p>
     */
    public static int chunkRadius(
            ResourceKey<Level> firstDimension,
            BlockPos firstPos,
            ResourceKey<Level> secondDimension,
            BlockPos secondPos
    ) {
        int dx = Math.abs((firstPos.getX() >> 4) - (secondPos.getX() >> 4));
        int dz = Math.abs((firstPos.getZ() >> 4) - (secondPos.getZ() >> 4));
        int radius = Math.max(dx, dz);
        return firstDimension.equals(secondDimension)
                ? radius
                : Math.max(radius, EntanglementConfig.crossDimensionMinimumChunkRadius());
    }

    /**
     * Calculates how many chunk-radius steps are subject to loss.
     */
    public static int lossyChunks(int chunkRadius) {
        return Math.max(0, chunkRadius - EntanglementConfig.freeChunkRadius());
    }

    /**
     * Converts configured per-chunk loss into a total ppm coefficient.
     */
    public static long lossPpm(int chunkRadius) {
        int lossyChunks = lossyChunks(chunkRadius);
        double lossPerChunk = EntanglementConfig.lossPerChunkPercent();
        if (lossyChunks <= 0 || lossPerChunk <= 0.0D) {
            return 0L;
        }

        double totalLossPercent = lossyChunks * lossPerChunk;
        if (totalLossPercent >= 100.0D) {
            return MAX_LOSS_PPM;
        }
        return Math.max(0L, Math.min(MAX_LOSS_PPM, Math.round(totalLossPercent * PPM_PER_PERCENT)));
    }

    /**
     * Calculates lost power for a single entanglement transfer.
     */
    public static long calculateLoss(long inputPower, int chunkRadius) {
        long lossPpm = lossPpm(chunkRadius);
        if (inputPower <= 0L || lossPpm <= 0L) {
            return 0L;
        }
        if (lossPpm >= MAX_LOSS_PPM) {
            return inputPower;
        }

        BigInteger numerator = BigInteger.valueOf(inputPower)
                .multiply(BigInteger.valueOf(lossPpm));
        BigInteger loss = numerator
                .add(BigInteger.valueOf(PERCENT_SCALE_PPM - 1L))
                .divide(BigInteger.valueOf(PERCENT_SCALE_PPM));
        return loss.min(BigInteger.valueOf(inputPower)).longValue();
    }

    /**
     * Returns the amount that can arrive at the remote output after loss.
     */
    public static long deliverablePower(long inputPower, int chunkRadius) {
        long loss = calculateLoss(inputPower, chunkRadius);
        return Math.max(0L, inputPower - loss);
    }

    /**
     * Finds the largest input amount whose delivered power does not exceed the
     * remote side's demand.
     */
    public static long maxInputForRequestedOutput(
            long requestedOutput,
            long offeredInput,
            int chunkRadius
    ) {
        if (requestedOutput <= 0L || offeredInput <= 0L) {
            return 0L;
        }
        if (deliverablePower(offeredInput, chunkRadius) <= requestedOutput) {
            return offeredInput;
        }

        long low = 0L;
        long high = offeredInput;
        while (low < high) {
            long middle = low + (high - low + 1L) / 2L;
            if (deliverablePower(middle, chunkRadius) <= requestedOutput) {
                low = middle;
            } else {
                high = middle - 1L;
            }
        }
        return low;
    }
}
