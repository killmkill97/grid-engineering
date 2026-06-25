package dev.gridengineering.block.entity;

import dev.gridengineering.energy.ConfiguredPowerSource;
import dev.gridengineering.energy.InternalPowerEndpoint;
import dev.gridengineering.energy.VoltageTiers;
import dev.gridengineering.menu.PowerControlMenu;
import dev.gridengineering.menu.PowerControlTarget;
import dev.gridengineering.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

public final class TestBatteryBlockEntity extends BlockEntity
        implements MenuProvider, PowerControlTarget, ConfiguredPowerSource, InternalPowerEndpoint {
    public static final long CAPACITY = 1_000_000_000_000L;
    private static final String ENERGY_TAG = "Energy";
    private static final String MODE_TAG = "Mode";
    private static final String VOLTAGE_TIER_TAG = "VoltageTier";
    private static final String OUTPUT_VOLTAGE_TAG = "OutputVoltage";
    private static final String AMPS_TAG = "Amps";
    private static final long MAX_CONFIGURED_VOLTAGE = Long.MAX_VALUE / MAX_AMPS;

    private final IEnergyStorage energyStorage = new BatteryEnergyStorage();
    private long energy;
    private Mode mode = Mode.SINK;
    private int voltageTierIndex;
    private long configuredVoltage = VoltageTiers.voltage(0);
    private int configuredAmps = 1;
    private long lastOutputTick = Long.MIN_VALUE;
    private long outputThisTick;

    public TestBatteryBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.TEST_BATTERY_BLOCK_ENTITY.get(), pos, state);
    }

    public IEnergyStorage getEnergyStorage() {
        return this.energyStorage;
    }

    public Mode getMode() {
        return this.mode;
    }

    public long getDisplayedEnergy() {
        return this.mode == Mode.SOURCE ? CAPACITY : this.mode == Mode.TRASH ? 0L : this.energy;
    }

    public void cycleMode() {
        this.mode = this.mode.next();
        this.settingsChanged();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.gridengineering.test_battery");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new PowerControlMenu(containerId, inventory, this);
    }

    @Override
    public BlockEntity asBlockEntity() {
        return this;
    }

    @Override
    public int getVoltageTierIndex() {
        return this.voltageTierIndex;
    }

    @Override
    public void setVoltageTierIndex(int index) {
        int clamped = VoltageTiers.clampIndex(index);
        long tierVoltage = VoltageTiers.voltage(clamped);
        if (clamped != this.voltageTierIndex || tierVoltage != this.configuredVoltage) {
            this.voltageTierIndex = clamped;
            this.configuredVoltage = tierVoltage;
            this.settingsChanged();
        }
    }

    @Override
    public long getConfiguredVoltage() {
        return this.configuredVoltage;
    }

    @Override
    public void setConfiguredVoltage(long voltage) {
        long clamped = Mth.clamp(voltage, 1L, MAX_CONFIGURED_VOLTAGE);
        int tier = VoltageTiers.tierIndexForVoltage(clamped);
        if (clamped != this.configuredVoltage || tier != this.voltageTierIndex) {
            this.configuredVoltage = clamped;
            this.voltageTierIndex = tier;
            this.settingsChanged();
        }
    }

    @Override
    public int getConfiguredAmps() {
        return this.configuredAmps;
    }

    @Override
    public void setConfiguredAmps(int amps) {
        int clamped = Mth.clamp(amps, MIN_AMPS, MAX_AMPS);
        if (clamped != this.configuredAmps) {
            this.configuredAmps = clamped;
            this.settingsChanged();
        }
    }

    @Override
    public int getControlMode() {
        return this.mode.ordinal();
    }

    @Override
    public void cycleControlMode() {
        this.cycleMode();
    }

    @Override
    public long getStoredEnergyForDisplay() {
        return this.getDisplayedEnergy();
    }

    @Override
    public long getOutputVoltage() {
        return this.configuredVoltage;
    }

    @Override
    public long getOutputAmps() {
        return this.configuredAmps;
    }

    @Override
    public long getAvailableOutputPower() {
        long budget = this.remainingOutputBudget();
        return this.mode == Mode.SOURCE ? budget : Math.min(budget, this.energy);
    }

    @Override
    public boolean canOutputToGrid(Direction side) {
        return this.mode == Mode.SOURCE || this.mode == Mode.BUFFER;
    }

    @Override
    public long receiveInternalPower(Direction side, long amount, boolean simulate) {
        if (!this.canReceiveInternalPower(side) || amount <= 0L) {
            return 0L;
        }

        long accepted = Math.min(amount, CAPACITY - this.energy);
        if (this.mode == Mode.TRASH) {
            return amount;
        }
        if (!simulate && accepted > 0L) {
            this.energy += accepted;
            this.setChanged();
        }
        return accepted;
    }

    @Override
    public long extractInternalPower(Direction side, long amount, boolean simulate) {
        if (!this.canExtractInternalPower(side) || amount <= 0L) {
            return 0L;
        }

        long extracted = Math.min(amount, this.remainingOutputBudget());
        if (this.mode == Mode.BUFFER) {
            extracted = Math.min(extracted, this.energy);
        }

        if (!simulate && extracted > 0L) {
            this.outputThisTick += extracted;
            if (this.mode == Mode.BUFFER) {
                this.energy -= extracted;
            }
            this.setChanged();
        }
        return extracted;
    }

    @Override
    public boolean canReceiveInternalPower(Direction side) {
        return this.mode == Mode.SINK || this.mode == Mode.BUFFER || this.mode == Mode.TRASH;
    }

    @Override
    public boolean canExtractInternalPower(Direction side) {
        return this.mode == Mode.SOURCE || this.mode == Mode.BUFFER;
    }

    private long configuredOutputPerTick() {
        return Math.multiplyExact(this.getOutputVoltage(), this.getOutputAmps());
    }

    private long remainingOutputBudget() {
        if (this.level == null) {
            return this.configuredOutputPerTick();
        }
        long gameTime = this.level.getGameTime();
        if (gameTime != this.lastOutputTick) {
            this.lastOutputTick = gameTime;
            this.outputThisTick = 0L;
        }
        return Math.max(0L, this.configuredOutputPerTick() - this.outputThisTick);
    }

    private void settingsChanged() {
        this.setChanged();
        this.sync();
    }

    private void sync() {
        if (this.level != null && !this.level.isClientSide) {
            BlockState state = this.getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, 2);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.energy = Mth.clamp(tag.getLong(ENERGY_TAG), 0L, CAPACITY);
        this.mode = Mode.byName(tag.getString(MODE_TAG));
        this.configuredVoltage = tag.contains(OUTPUT_VOLTAGE_TAG)
                ? Mth.clamp(tag.getLong(OUTPUT_VOLTAGE_TAG), 1L, MAX_CONFIGURED_VOLTAGE)
                : VoltageTiers.voltage(VoltageTiers.clampIndex(tag.getInt(VOLTAGE_TIER_TAG)));
        this.voltageTierIndex = VoltageTiers.tierIndexForVoltage(this.configuredVoltage);
        this.configuredAmps = tag.contains(AMPS_TAG)
                ? Mth.clamp(tag.getInt(AMPS_TAG), MIN_AMPS, MAX_AMPS)
                : 1;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong(ENERGY_TAG, this.energy);
        tag.putString(MODE_TAG, this.mode.serializedName);
        tag.putInt(VOLTAGE_TIER_TAG, this.voltageTierIndex);
        tag.putLong(OUTPUT_VOLTAGE_TAG, this.configuredVoltage);
        tag.putInt(AMPS_TAG, this.configuredAmps);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    public enum Mode {
        SINK("sink"),
        SOURCE("source"),
        BUFFER("buffer"),
        TRASH("trash");

        private final String serializedName;

        Mode(String serializedName) {
            this.serializedName = serializedName;
        }

        public String translationKey() {
            return "mode.gridengineering.test_battery." + this.serializedName;
        }

        private Mode next() {
            return switch (this) {
                case SINK -> SOURCE;
                case SOURCE -> BUFFER;
                case BUFFER -> TRASH;
                case TRASH -> SINK;
            };
        }

        private static Mode byName(String name) {
            for (Mode mode : values()) {
                if (mode.serializedName.equals(name)) {
                    return mode;
                }
            }
            return SINK;
        }
    }

    private final class BatteryEnergyStorage implements IEnergyStorage {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            return (int)TestBatteryBlockEntity.this.receiveInternalPower(
                    Direction.UP,
                    toReceive,
                    simulate
            );
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return (int)TestBatteryBlockEntity.this.extractInternalPower(
                    Direction.UP,
                    toExtract,
                    simulate
            );
        }

        @Override
        public int getEnergyStored() {
            return (int)Math.min(TestBatteryBlockEntity.this.getDisplayedEnergy(), Integer.MAX_VALUE);
        }

        @Override
        public int getMaxEnergyStored() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean canExtract() {
            return TestBatteryBlockEntity.this.canExtractInternalPower(Direction.UP);
        }

        @Override
        public boolean canReceive() {
            return TestBatteryBlockEntity.this.canReceiveInternalPower(Direction.UP);
        }
    }
}
