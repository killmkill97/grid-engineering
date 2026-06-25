package dev.gridengineering.energy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToLongFunction;
import net.minecraft.util.RandomSource;

public final class PowerOfTwoChoices {
    private static final int MIN_DISTRIBUTION_STEPS = 256;
    private static final int STEPS_PER_DESTINATION = 32;
    private static final int MAX_DISTRIBUTION_STEPS = 8_192;

    private PowerOfTwoChoices() {
    }

    public static int chooseIndex(
            int destinationCount,
            IntToLongFunction loadAtIndex,
            RandomSource random
    ) {
        if (destinationCount <= 1) {
            return 0;
        }

        int first = random.nextInt(destinationCount);
        int second = random.nextInt(destinationCount - 1);
        if (second >= first) {
            second++;
        }

        long firstLoad = loadAtIndex.applyAsLong(first);
        long secondLoad = loadAtIndex.applyAsLong(second);
        if (firstLoad < secondLoad) {
            return first;
        }
        if (secondLoad < firstLoad) {
            return second;
        }
        return random.nextBoolean() ? first : second;
    }

    public static int distributionSteps(int destinationCount) {
        long requested = Math.max(
                MIN_DISTRIBUTION_STEPS,
                (long)destinationCount * STEPS_PER_DESTINATION
        );
        return (int)Math.min(requested, MAX_DISTRIBUTION_STEPS);
    }

    public static long packetSize(long totalPower, int destinationCount) {
        if (totalPower <= 0L || destinationCount <= 0) {
            return 0L;
        }

        int steps = distributionSteps(destinationCount);
        return 1L + (totalPower - 1L) / steps;
    }

    public static long[] distribute(
            long totalPower,
            long[] capacities,
            long[] existingLoads,
            RandomSource random
    ) {
        if (capacities.length != existingLoads.length) {
            throw new IllegalArgumentException("Capacity and load arrays must have the same length");
        }

        long[] allocations = new long[capacities.length];
        if (totalPower <= 0L || capacities.length == 0) {
            return allocations;
        }

        List<Integer> active = new ArrayList<>(capacities.length);
        for (int index = 0; index < capacities.length; index++) {
            if (capacities[index] > 0L) {
                active.add(index);
            }
        }
        if (active.isEmpty()) {
            return allocations;
        }

        int baseSteps = distributionSteps(active.size());
        int remainingSteps = baseSteps + active.size();
        long packetSize = packetSize(totalPower, active.size());
        long remaining = totalPower;

        while (remaining > 0L && remainingSteps-- > 0 && !active.isEmpty()) {
            int selectedActiveIndex = chooseIndex(
                    active.size(),
                    activeIndex -> {
                        int destinationIndex = active.get(activeIndex);
                        return saturatedAdd(
                                existingLoads[destinationIndex],
                                allocations[destinationIndex]
                        );
                    },
                    random
            );
            int destinationIndex = active.get(selectedActiveIndex);
            long availableCapacity = capacities[destinationIndex] - allocations[destinationIndex];
            if (availableCapacity <= 0L) {
                active.remove(selectedActiveIndex);
                continue;
            }

            long assigned = Math.min(Math.min(packetSize, remaining), availableCapacity);
            allocations[destinationIndex] += assigned;
            remaining -= assigned;
            if (allocations[destinationIndex] >= capacities[destinationIndex]) {
                active.remove(selectedActiveIndex);
            }
        }

        return allocations;
    }

    private static long saturatedAdd(long first, long second) {
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }
}
