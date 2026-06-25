package dev.gridengineering.energy;

import net.minecraft.core.Direction;

public interface ConfiguredPowerSource {
    long getOutputVoltage();

    long getOutputAmps();

    long getAvailableOutputPower();

    boolean canOutputToGrid(Direction side);
}
