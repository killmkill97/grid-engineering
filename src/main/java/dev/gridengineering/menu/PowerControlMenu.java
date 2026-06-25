package dev.gridengineering.menu;

import dev.gridengineering.energy.VoltageTiers;
import dev.gridengineering.registry.ModContent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public final class PowerControlMenu extends AbstractContainerMenu {
    public static final int BUTTON_VOLTAGE_DOWN = 0;
    public static final int BUTTON_VOLTAGE_UP = 1;
    public static final int BUTTON_AMPS_DOWN = 2;
    public static final int BUTTON_AMPS_UP = 3;
    public static final int BUTTON_AMPS_DOWN_FAST = 4;
    public static final int BUTTON_AMPS_UP_FAST = 5;
    public static final int BUTTON_MODE = 6;
    public static final int BUTTON_VOLTAGE_DIGIT_BASE = 100;
    public static final int BUTTON_VOLTAGE_INPUT_CLEAR = 110;
    public static final int BUTTON_VOLTAGE_INPUT_APPLY = 111;
    public static final int BUTTON_AMPS_DIGIT_BASE = 200;
    public static final int BUTTON_AMPS_INPUT_CLEAR = 210;
    public static final int BUTTON_AMPS_INPUT_NEGATIVE = 211;
    public static final int BUTTON_AMPS_INPUT_APPLY = 212;
    private static final int DATA_COUNT = 11;

    @Nullable
    private final PowerControlTarget target;
    private final ContainerData data;
    private long pendingVoltageInput;
    private int pendingVoltageDigits;
    private long pendingAmpsInput;
    private int pendingAmpsDigits;
    private boolean pendingAmpsNegative;

    public PowerControlMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null);
    }

    public PowerControlMenu(int containerId, Inventory inventory, @Nullable PowerControlTarget target) {
        super(ModContent.POWER_CONTROL_MENU.get(), containerId);
        this.target = target;
        this.data = target == null ? new SimpleContainerData(DATA_COUNT) : createTargetData(target);
        checkContainerDataCount(this.data, DATA_COUNT);
        this.addDataSlots(this.data);
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.target == null) {
            return true;
        }

        BlockEntity blockEntity = this.target.asBlockEntity();
        return !blockEntity.isRemoved()
                && blockEntity.getLevel() == player.level()
                && player.distanceToSqr(
                        blockEntity.getBlockPos().getX() + 0.5,
                        blockEntity.getBlockPos().getY() + 0.5,
                        blockEntity.getBlockPos().getZ() + 0.5
                ) <= 64.0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (this.target == null || !this.stillValid(player)) {
            return false;
        }

        if (id >= BUTTON_VOLTAGE_DIGIT_BASE
                && id < BUTTON_VOLTAGE_DIGIT_BASE + 10) {
            if (this.pendingVoltageDigits >= 18) {
                return false;
            }
            int digit = id - BUTTON_VOLTAGE_DIGIT_BASE;
            if (this.pendingVoltageInput <= (Long.MAX_VALUE - digit) / 10L) {
                this.pendingVoltageInput = this.pendingVoltageInput * 10L + digit;
                this.pendingVoltageDigits++;
            }
            return true;
        }
        if (id == BUTTON_VOLTAGE_INPUT_CLEAR) {
            this.pendingVoltageInput = 0L;
            this.pendingVoltageDigits = 0;
            return true;
        }
        if (id == BUTTON_VOLTAGE_INPUT_APPLY) {
            if (this.pendingVoltageDigits == 0) {
                return false;
            }
            this.target.setConfiguredVoltage(Math.max(1L, this.pendingVoltageInput));
            this.pendingVoltageInput = 0L;
            this.pendingVoltageDigits = 0;
            this.broadcastChanges();
            return true;
        }

        if (id >= BUTTON_AMPS_DIGIT_BASE
                && id < BUTTON_AMPS_DIGIT_BASE + 10) {
            if (this.pendingAmpsDigits >= 19) {
                return false;
            }
            int digit = id - BUTTON_AMPS_DIGIT_BASE;
            if (this.pendingAmpsInput <= (Long.MAX_VALUE - digit) / 10L) {
                this.pendingAmpsInput = this.pendingAmpsInput * 10L + digit;
                this.pendingAmpsDigits++;
            } else {
                this.pendingAmpsInput = Long.MAX_VALUE;
                this.pendingAmpsDigits++;
            }
            return true;
        }
        if (id == BUTTON_AMPS_INPUT_CLEAR) {
            this.clearPendingAmpsInput();
            return true;
        }
        if (id == BUTTON_AMPS_INPUT_NEGATIVE) {
            this.pendingAmpsNegative = true;
            return true;
        }
        if (id == BUTTON_AMPS_INPUT_APPLY) {
            if (this.pendingAmpsDigits == 0) {
                return false;
            }
            int amps;
            if (this.pendingAmpsNegative || this.pendingAmpsInput <= PowerControlTarget.MIN_AMPS) {
                amps = PowerControlTarget.MIN_AMPS;
            } else if (this.pendingAmpsInput >= PowerControlTarget.MAX_AMPS) {
                amps = PowerControlTarget.MAX_AMPS;
            } else {
                amps = (int)this.pendingAmpsInput;
            }
            this.target.setConfiguredAmps(amps);
            this.clearPendingAmpsInput();
            this.broadcastChanges();
            return true;
        }

        switch (id) {
            case BUTTON_VOLTAGE_DOWN ->
                    this.target.setVoltageTierIndex(this.target.getVoltageTierIndex() - 1);
            case BUTTON_VOLTAGE_UP ->
                    this.target.setVoltageTierIndex(this.target.getVoltageTierIndex() + 1);
            case BUTTON_AMPS_DOWN ->
                    this.target.setConfiguredAmps(this.target.getConfiguredAmps() - 1);
            case BUTTON_AMPS_UP ->
                    this.target.setConfiguredAmps(this.target.getConfiguredAmps() + 1);
            case BUTTON_AMPS_DOWN_FAST ->
                    this.target.setConfiguredAmps(this.target.getConfiguredAmps() - 4);
            case BUTTON_AMPS_UP_FAST ->
                    this.target.setConfiguredAmps(this.target.getConfiguredAmps() + 4);
            case BUTTON_MODE -> {
                if (this.target.getControlMode() == PowerControlTarget.REGULATOR_MODE) {
                    return false;
                }
                this.target.cycleControlMode();
            }
            default -> {
                return false;
            }
        }

        this.broadcastChanges();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    public int getVoltageTierIndex() {
        return VoltageTiers.clampIndex(this.data.get(0));
    }

    public long getVoltage() {
        return combineLong(9);
    }

    public String getVoltageTierName() {
        return VoltageTiers.nameForVoltage(this.getVoltage());
    }

    public int getAmps() {
        return Math.max(PowerControlTarget.MIN_AMPS, this.data.get(1));
    }

    public int getControlMode() {
        return this.data.get(2);
    }

    public long getStoredEnergy() {
        return combineLong(3);
    }

    public long getInputPower() {
        return combineLong(5);
    }

    public long getOutputPower() {
        return combineLong(7);
    }

    private long combineLong(int lowIndex) {
        return Integer.toUnsignedLong(this.data.get(lowIndex))
                | Integer.toUnsignedLong(this.data.get(lowIndex + 1)) << 32;
    }

    private void clearPendingAmpsInput() {
        this.pendingAmpsInput = 0L;
        this.pendingAmpsDigits = 0;
        this.pendingAmpsNegative = false;
    }

    private static ContainerData createTargetData(PowerControlTarget target) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                long energy = target.getStoredEnergyForDisplay();
                long inputPower = target.getInputPowerForDisplay();
                long outputPower = target.getOutputPowerForDisplay();
                return switch (index) {
                    case 0 -> target.getVoltageTierIndex();
                    case 1 -> target.getConfiguredAmps();
                    case 2 -> target.getControlMode();
                    case 3 -> (int)energy;
                    case 4 -> (int)(energy >>> 32);
                    case 5 -> (int)inputPower;
                    case 6 -> (int)(inputPower >>> 32);
                    case 7 -> (int)outputPower;
                    case 8 -> (int)(outputPower >>> 32);
                    case 9 -> (int)target.getConfiguredVoltage();
                    case 10 -> (int)(target.getConfiguredVoltage() >>> 32);
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) {
                    target.setVoltageTierIndex(value);
                } else if (index == 1) {
                    target.setConfiguredAmps(value);
                }
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }
}
