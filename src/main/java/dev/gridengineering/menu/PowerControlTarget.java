package dev.gridengineering.menu;

import dev.gridengineering.energy.VoltageTiers;
import net.minecraft.world.level.block.entity.BlockEntity;

public interface PowerControlTarget {
    int REGULATOR_MODE = -1;
    int MIN_AMPS = 1;
    int MAX_AMPS = 16_384;

    BlockEntity asBlockEntity();

    int getVoltageTierIndex();

    void setVoltageTierIndex(int index);

    default long getConfiguredVoltage() {
        return VoltageTiers.voltage(this.getVoltageTierIndex());
    }

    default void setConfiguredVoltage(long voltage) {
        this.setVoltageTierIndex(VoltageTiers.tierIndexForVoltage(voltage));
    }

    int getConfiguredAmps();

    void setConfiguredAmps(int amps);

    int getControlMode();

    void cycleControlMode();

    long getStoredEnergyForDisplay();

    default long getInputPowerForDisplay() {
        return 0L;
    }

    default long getOutputPowerForDisplay() {
        return 0L;
    }
}
