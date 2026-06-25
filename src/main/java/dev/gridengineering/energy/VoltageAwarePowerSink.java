package dev.gridengineering.energy;

import net.minecraft.core.Direction;

public interface VoltageAwarePowerSink {
    long receiveInternalPower(Direction side, long amount, long voltage, boolean simulate);
}
