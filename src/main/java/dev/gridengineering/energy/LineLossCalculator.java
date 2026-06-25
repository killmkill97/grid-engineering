package dev.gridengineering.energy;

import java.math.BigInteger;

public final class LineLossCalculator {
    public static final long PERCENT_SCALE_PPM = 1_000_000L;

    private LineLossCalculator() {
    }

    /**
     * Calculates I²R-style line loss from the configured percentage per wire block per ampere.
     *
     * <p>For example, a route coefficient of 2,000 ppm is 0.2% per block at 1 A.
     * At 2 A the same block loses 0.4%, and two such blocks at 1 A also lose 0.4%.</p>
     */
    public static long calculateLoss(
            long inputPower,
            long voltage,
            long routeLossPerAmpPpm
    ) {
        if (inputPower <= 0L || voltage <= 0L || routeLossPerAmpPpm <= 0L) {
            return 0L;
        }

        BigInteger power = BigInteger.valueOf(inputPower);
        BigInteger numerator = power
                .multiply(power)
                .multiply(BigInteger.valueOf(routeLossPerAmpPpm));
        BigInteger denominator = BigInteger.valueOf(voltage)
                .multiply(BigInteger.valueOf(PERCENT_SCALE_PPM));
        BigInteger loss = numerator.divide(denominator);

        return loss.min(power).longValue();
    }
}
