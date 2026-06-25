package dev.gridengineering.block.entity;

import dev.gridengineering.block.CurrentRegulatorBlock;
import dev.gridengineering.energy.ConfiguredPowerSource;
import dev.gridengineering.energy.InternalPowerEndpoint;
import dev.gridengineering.energy.PowerEndpoint;
import dev.gridengineering.energy.PowerEndpointAccess;
import dev.gridengineering.energy.PowerOfTwoChoices;
import dev.gridengineering.energy.VoltageTiers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

public final class CurrentRegulatorBlockEntity extends BlockEntity
        implements MenuProvider, PowerControlTarget, ConfiguredPowerSource, InternalPowerEndpoint {
    private static final String VOLTAGE_TIER_TAG = "VoltageTier";
    private static final String OUTPUT_VOLTAGE_TAG = "OutputVoltage";
    private static final String AMPS_TAG = "Amps";
    private static final long MAX_CONFIGURED_VOLTAGE = Long.MAX_VALUE / MAX_AMPS;

    private final IEnergyStorage inputStorage = new InputEnergyStorage();
    private final IEnergyStorage outputStorage = new OutputEnergyStorage();
    private long transitPower;
    private long lastInputTick = Long.MIN_VALUE;
    private int voltageTierIndex;
    private long configuredVoltage = VoltageTiers.voltage(0);
    private int configuredAmps = 1;
    private long stateTick = Long.MIN_VALUE;
    private long inputThisTick;
    private long outputThisTick;
    private long previousInput;
    private long previousOutput;
    private final long[] outputBySideThisTick = new long[Direction.values().length];

    public CurrentRegulatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.CURRENT_REGULATOR_BLOCK_ENTITY.get(), pos, state);
    }

    @Nullable
    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        if (side == null) {
            return this.outputStorage;
        }
        return side == this.getInputSide() ? this.inputStorage : this.outputStorage;
    }

    public Direction getInputSide() {
        return this.getBlockState().getValue(CurrentRegulatorBlock.FACING);
    }

    public static void serverTick(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
        CurrentRegulatorBlockEntity regulator
    ) {
        regulator.refreshTickState();
        Direction inputSide = regulator.getInputSide();
        List<DirectDestination> destinations = new ArrayList<>(5);

        for (Direction direction : Direction.values()) {
            if (direction == inputSide) {
                continue;
            }

            BlockPos destinationPos = pos.relative(direction);
            if (level.getBlockState(destinationPos).getBlock()
                    instanceof dev.gridengineering.block.WireBlock
                    || isCurrentRegulator(level, destinationPos)) {
                continue;
            }

            PowerEndpoint destination = PowerEndpointAccess.find(
                    level,
                    destinationPos,
                    direction.getOpposite()
            );
            if (destination == null || !destination.canReceive()) {
                continue;
            }
            if (destination.receive(1L, true) > 0L) {
                destinations.add(new DirectDestination(direction, destination));
            }
        }

        long remaining = regulator.remainingOutputBudget();
        long[] capacities = new long[destinations.size()];
        long[] existingLoads = new long[destinations.size()];
        for (int index = 0; index < destinations.size(); index++) {
            DirectDestination destination = destinations.get(index);
            capacities[index] = destination.endpoint().receive(remaining, true);
            existingLoads[index] = regulator.outputBySideThisTick[
                    destination.direction().get3DDataValue()
            ];
        }
        long[] allocations = PowerOfTwoChoices.distribute(
                remaining,
                capacities,
                existingLoads,
                level.random
        );

        for (int index = 0; index < allocations.length && remaining > 0L; index++) {
            DirectDestination selected = destinations.get(index);
            long accepted = Math.min(remaining, allocations[index]);
            if (accepted <= 0L) {
                continue;
            }

            long extracted = regulator.extractInternalPower(
                    selected.direction(),
                    accepted,
                    false
            );
            if (extracted > 0L) {
                long inserted = selected.endpoint().receive(extracted, false);
                regulator.outputBySideThisTick[
                        selected.direction().get3DDataValue()
                ] += inserted;
                remaining -= extracted;
            }
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.gridengineering.current_regulator");
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
        return REGULATOR_MODE;
    }

    @Override
    public void cycleControlMode() {
    }

    @Override
    public long getStoredEnergyForDisplay() {
        return 0L;
    }

    @Override
    public long getInputPowerForDisplay() {
        this.refreshTickState();
        return this.previousInput;
    }

    @Override
    public long getOutputPowerForDisplay() {
        this.refreshTickState();
        return this.previousOutput;
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
        this.refreshTickState();
        long budget = this.remainingOutputBudget();
        long fromInput = this.availableInputPower(Math.max(0L, budget - this.transitPower));
        return Math.min(budget, saturatedAdd(this.transitPower, fromInput));
    }

    @Override
    public boolean canOutputToGrid(Direction side) {
        return side != this.getInputSide();
    }

    @Override
    public long receiveInternalPower(Direction side, long amount, boolean simulate) {
        if (!this.canReceiveInternalPower(side) || amount <= 0L) {
            return 0L;
        }
        this.refreshTickState();
        long accepted = Math.min(
                amount,
                Math.max(0L, this.configuredOutputPerTick() - this.transitPower)
        );
        if (!simulate && accepted > 0L) {
            this.transitPower += accepted;
            this.inputThisTick += accepted;
            this.lastInputTick = this.level == null ? 0L : this.level.getGameTime();
            this.setChanged();
        }
        return accepted;
    }

    @Override
    public long extractInternalPower(Direction side, long amount, boolean simulate) {
        if (side == this.getInputSide() || amount <= 0L) {
            return 0L;
        }
        this.refreshTickState();
        long requested = Math.min(amount, this.remainingOutputBudget());
        if (requested <= 0L) {
            return 0L;
        }

        long available = Math.min(
                requested,
                saturatedAdd(this.transitPower, this.availableInputPower(requested))
        );
        if (simulate) {
            return available;
        }

        if (this.transitPower < available) {
            this.pullFromInput(available - this.transitPower);
        }
        long extracted = Math.min(available, this.transitPower);
        if (!simulate && extracted > 0L) {
            this.transitPower -= extracted;
            this.outputThisTick += extracted;
            this.setChanged();
        }
        return extracted;
    }

    @Override
    public boolean canReceiveInternalPower(Direction side) {
        this.refreshTickState();
        return side == this.getInputSide()
                && this.transitPower < this.configuredOutputPerTick()
                && this.hasOutputConnection();
    }

    @Override
    public boolean canExtractInternalPower(Direction side) {
        this.refreshTickState();
        return side != this.getInputSide()
                && (this.transitPower > 0L || this.availableInputPower(this.remainingOutputBudget()) > 0L);
    }

    private long configuredOutputPerTick() {
        return Math.multiplyExact(this.getOutputVoltage(), this.getOutputAmps());
    }

    private long remainingOutputBudget() {
        this.refreshTickState();
        return Math.max(0L, this.configuredOutputPerTick() - this.outputThisTick);
    }

    private void refreshTickState() {
        if (this.level == null) {
            return;
        }
        long gameTime = this.level.getGameTime();
        if (gameTime != this.stateTick) {
            if (this.stateTick != Long.MIN_VALUE) {
                this.previousInput = this.inputThisTick;
                this.previousOutput = this.outputThisTick;
            }
            this.stateTick = gameTime;
            this.inputThisTick = 0L;
            this.outputThisTick = 0L;
            Arrays.fill(this.outputBySideThisTick, 0L);
            if (this.lastInputTick < gameTime - 1L) {
                this.transitPower = 0L;
            }
        }
    }

    private boolean hasOutputConnection() {
        if (this.level == null) {
            return true;
        }

        Direction inputSide = this.getInputSide();
        for (Direction direction : Direction.values()) {
            if (direction == inputSide) {
                continue;
            }

            BlockPos neighborPos = this.worldPosition.relative(direction);
            if (this.level.getBlockState(neighborPos).getBlock()
                    instanceof dev.gridengineering.block.WireBlock) {
                return true;
            }
            if (isCurrentRegulator(this.level, neighborPos)) {
                continue;
            }

            PowerEndpoint endpoint = PowerEndpointAccess.find(
                    this.level,
                    neighborPos,
                    direction.getOpposite()
            );
            if (endpoint != null && endpoint.canReceive()) {
                return true;
            }
        }
        return false;
    }

    private long availableInputPower(long request) {
        if (request <= 0L || this.level == null) {
            return 0L;
        }
        PowerEndpoint source = this.inputSource();
        return source == null || !source.canExtract()
                ? 0L
                : source.extract(request, true);
    }

    private void pullFromInput(long request) {
        if (request <= 0L) {
            return;
        }

        PowerEndpoint source = this.inputSource();
        if (source == null || !source.canExtract()) {
            return;
        }

        long available = source.extract(request, true);
        if (available <= 0L) {
            return;
        }

        long extracted = source.extract(Math.min(request, available), false);
        if (extracted > 0L) {
            this.transitPower += extracted;
            this.inputThisTick += extracted;
            this.lastInputTick = this.level == null ? 0L : this.level.getGameTime();
            this.setChanged();
        }
    }

    @Nullable
    private PowerEndpoint inputSource() {
        if (this.level == null) {
            return null;
        }
        Direction inputSide = this.getInputSide();
        BlockPos sourcePos = this.worldPosition.relative(inputSide);
        if (isCurrentRegulator(this.level, sourcePos)) {
            return null;
        }
        return PowerEndpointAccess.find(
                this.level,
                sourcePos,
                inputSide.getOpposite()
        );
    }

    private static boolean isCurrentRegulator(
            net.minecraft.world.level.Level level,
            BlockPos pos
    ) {
        return level.getBlockState(pos).getBlock() instanceof CurrentRegulatorBlock;
    }

    private static long saturatedAdd(long first, long second) {
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
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
        this.transitPower = 0L;
        this.lastInputTick = Long.MIN_VALUE;
        this.configuredVoltage = tag.contains(OUTPUT_VOLTAGE_TAG)
                ? Mth.clamp(tag.getLong(OUTPUT_VOLTAGE_TAG), 1L, MAX_CONFIGURED_VOLTAGE)
                : VoltageTiers.voltage(VoltageTiers.clampIndex(tag.getInt(VOLTAGE_TIER_TAG)));
        this.voltageTierIndex = VoltageTiers.tierIndexForVoltage(this.configuredVoltage);
        this.configuredAmps = Mth.clamp(tag.getInt(AMPS_TAG), MIN_AMPS, MAX_AMPS);
        if (!tag.contains(AMPS_TAG)) {
            this.configuredAmps = 1;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
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

    private final class InputEnergyStorage implements IEnergyStorage {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            return (int)CurrentRegulatorBlockEntity.this.receiveInternalPower(
                    CurrentRegulatorBlockEntity.this.getInputSide(),
                    toReceive,
                    simulate
            );
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return (int)Math.min(CurrentRegulatorBlockEntity.this.transitPower, Integer.MAX_VALUE);
        }

        @Override
        public int getMaxEnergyStored() {
            return (int)Math.min(
                    CurrentRegulatorBlockEntity.this.configuredOutputPerTick(),
                    Integer.MAX_VALUE
            );
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }

    private final class OutputEnergyStorage implements IEnergyStorage {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            Direction outputSide = CurrentRegulatorBlockEntity.this.getInputSide().getOpposite();
            return (int)CurrentRegulatorBlockEntity.this.extractInternalPower(
                    outputSide,
                    toExtract,
                    simulate
            );
        }

        @Override
        public int getEnergyStored() {
            return (int)Math.min(CurrentRegulatorBlockEntity.this.transitPower, Integer.MAX_VALUE);
        }

        @Override
        public int getMaxEnergyStored() {
            return (int)Math.min(
                    CurrentRegulatorBlockEntity.this.configuredOutputPerTick(),
                    Integer.MAX_VALUE
            );
        }

        @Override
        public boolean canExtract() {
            return CurrentRegulatorBlockEntity.this.transitPower > 0L;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    }

    private record DirectDestination(Direction direction, PowerEndpoint endpoint) {
    }
}
