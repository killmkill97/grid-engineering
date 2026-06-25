package dev.gridengineering.energy;

import net.minecraft.core.Direction;

public interface InternalPowerEndpoint {
    long receiveInternalPower(Direction side, long amount, boolean simulate);

    long extractInternalPower(Direction side, long amount, boolean simulate);

    boolean canReceiveInternalPower(Direction side);

    boolean canExtractInternalPower(Direction side);
}
