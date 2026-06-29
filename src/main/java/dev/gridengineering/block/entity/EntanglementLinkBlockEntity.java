package dev.gridengineering.block.entity;

import dev.gridengineering.block.EntanglementLinkBlock;
import dev.gridengineering.block.WireBlock;
import dev.gridengineering.energy.ConfiguredPowerSource;
import dev.gridengineering.energy.InternalPowerEndpoint;
import dev.gridengineering.energy.PowerEndpoint;
import dev.gridengineering.energy.PowerEndpointAccess;
import dev.gridengineering.energy.PowerOfTwoChoices;
import dev.gridengineering.energy.VoltageAwarePowerSink;
import dev.gridengineering.energy.VoltageTiers;
import dev.gridengineering.energy.WireEnergyTransfer;
import dev.gridengineering.entanglement.EntanglementLinkManager;
import dev.gridengineering.entanglement.EntanglementLinkMode;
import dev.gridengineering.entanglement.EntanglementLinkStatus;
import dev.gridengineering.entanglement.EntanglementLossCalculator;
import dev.gridengineering.item.EntangledElectronItem;
import dev.gridengineering.menu.EntanglementLinkMenu;
import dev.gridengineering.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity for Entanglement Link power transmission.
 *
 * <p>The input side accepts power only when its paired output side has real
 * downstream demand, preventing the link from behaving like an energy void.</p>
 */
public final class EntanglementLinkBlockEntity extends BlockEntity
        implements MenuProvider, Container, InternalPowerEndpoint, VoltageAwarePowerSink, ConfiguredPowerSource {
    private static final String MODE_TAG = "Mode";
    private static final String ELECTRON_TAG = "Electron";
    private static final String LAST_PAIR_DIMENSION_TAG = "LastPairDimension";
    private static final String LAST_PAIR_X_TAG = "LastPairX";
    private static final String LAST_PAIR_Y_TAG = "LastPairY";
    private static final String LAST_PAIR_Z_TAG = "LastPairZ";
    private static final long DEFAULT_VOLTAGE = VoltageTiers.voltage(0);

    private final IEnergyStorage energyStorage = new LinkEnergyStorage();
    private ItemStack electron = ItemStack.EMPTY;
    private EntanglementLinkMode mode = EntanglementLinkMode.INPUT;
    private EntanglementLinkStatus cachedStatus = EntanglementLinkStatus.NO_ELECTRON;
    @Nullable
    private ResourceLocation lastPairDimension;
    @Nullable
    private BlockPos lastPairPos;
    private long stateTick = Long.MIN_VALUE;
    private long inputThisTick;
    private long outputThisTick;
    private long lossThisTick;
    private long previousInput;
    private long previousOutput;
    private long previousLoss;
    private long transitPower;
    private long transitVoltage = DEFAULT_VOLTAGE;
    private long lastTransitTick = Long.MIN_VALUE;

    public EntanglementLinkBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.ENTANGLEMENT_LINK_BLOCK_ENTITY.get(), pos, state);
        if (state.getBlock() instanceof EntanglementLinkBlock) {
            this.mode = state.getValue(EntanglementLinkBlock.MODE);
        }
    }

    /**
     * Server tick keeps the pairing cache fresh and synchronizes display state.
     */
    public static void serverTick(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            EntanglementLinkBlockEntity link
    ) {
        link.refreshTickState();
        link.ensureStateMatchesMode();
        EntanglementLinkManager.ResolvedLink resolved = EntanglementLinkManager.resolve(level, link);
        link.updateResolvedState(resolved);
        link.pushDirectOutputs(level);
    }

    /**
     * Exposes the NeoForge FE adapter for compatibility with other mods.
     */
    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return this.energyStorage;
    }

    /**
     * Returns the current input/output role.
     */
    public EntanglementLinkMode getMode() {
        return this.mode;
    }

    /**
     * Changes role from the GUI and mirrors it to the block state.
     */
    public void setMode(EntanglementLinkMode mode) {
        if (this.mode == mode) {
            return;
        }
        this.mode = mode;
        this.transitPower = 0L;
        this.settingsChanged();
        this.ensureStateMatchesMode();
        if (this.level instanceof ServerLevel serverLevel) {
            EntanglementLinkManager.refresh(serverLevel, this);
        }
    }

    /**
     * Cycles input/output mode for the GUI button.
     */
    public void cycleMode() {
        this.setMode(this.mode.next());
    }

    /**
     * Returns the UUID stored in the installed Entangled Electron.
     */
    public Optional<UUID> entanglementId() {
        return EntangledElectronItem.entanglementId(this.electron);
    }

    public EntanglementLinkStatus getCachedStatus() {
        return this.cachedStatus;
    }

    public long getPreviousInput() {
        this.refreshTickState();
        return this.previousInput;
    }

    public long getPreviousOutput() {
        this.refreshTickState();
        return this.previousOutput;
    }

    public long getPreviousLoss() {
        this.refreshTickState();
        return this.previousLoss;
    }

    @Nullable
    public ResourceLocation getLastPairDimension() {
        return this.lastPairDimension;
    }

    @Nullable
    public BlockPos getLastPairPos() {
        return this.lastPairPos;
    }

    /**
     * Creates synchronized integer data for the Entanglement Link menu.
     */
    public ContainerData createMenuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                long input = EntanglementLinkBlockEntity.this.getPreviousInput();
                long output = EntanglementLinkBlockEntity.this.getPreviousOutput();
                long loss = EntanglementLinkBlockEntity.this.getPreviousLoss();
                return switch (index) {
                    case 0 -> EntanglementLinkBlockEntity.this.mode.ordinal();
                    case 1 -> EntanglementLinkBlockEntity.this.cachedStatus.ordinal();
                    case 2 -> (int)input;
                    case 3 -> (int)(input >>> 32);
                    case 4 -> (int)output;
                    case 5 -> (int)(output >>> 32);
                    case 6 -> (int)loss;
                    case 7 -> (int)(loss >>> 32);
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return EntanglementLinkMenu.DATA_COUNT;
            }
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.gridengineering.entanglement_link");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new EntanglementLinkMenu(containerId, inventory, this);
    }

    @Override
    public long receiveInternalPower(Direction side, long amount, boolean simulate) {
        return this.receiveInternalPower(side, amount, DEFAULT_VOLTAGE, simulate);
    }

    @Override
    public long receiveInternalPower(Direction side, long amount, long voltage, boolean simulate) {
        if (amount <= 0L || voltage <= 0L || this.mode != EntanglementLinkMode.INPUT) {
            return 0L;
        }
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return 0L;
        }

        this.refreshTickState();
        EntanglementLinkManager.ResolvedLink resolved =
                EntanglementLinkManager.resolve(serverLevel, this);
        this.updateResolvedState(resolved);
        if (!resolved.connected()
                || !(resolved.other() instanceof EntanglementLinkBlockEntity output)
                || output.mode != EntanglementLinkMode.OUTPUT) {
            return 0L;
        }

        int chunkRadius = EntanglementLossCalculator.chunkRadius(
                serverLevel.dimension(),
                this.worldPosition,
                output.serverLevel().dimension(),
                output.worldPosition
        );
        long remoteDemand = output.outputDemandCapacity(amount, voltage);
        long accepted = EntanglementLossCalculator.maxInputForRequestedOutput(
                remoteDemand,
                amount,
                chunkRadius
        );
        long delivered = EntanglementLossCalculator.deliverablePower(accepted, chunkRadius);
        if (accepted <= 0L || delivered <= 0L) {
            return 0L;
        }

        if (!simulate) {
            long inserted = output.acceptFromPair(delivered, voltage);
            long actualInput = accepted;
            long actualLoss = Math.max(0L, actualInput - inserted);
            this.inputThisTick = saturatedAdd(this.inputThisTick, actualInput);
            this.lossThisTick = saturatedAdd(this.lossThisTick, actualLoss);
            this.rememberPair(output);
            output.rememberPair(this);
            this.setChanged();
        }
        return accepted;
    }

    @Override
    public long extractInternalPower(Direction side, long amount, boolean simulate) {
        if (amount <= 0L || this.mode != EntanglementLinkMode.OUTPUT || !this.hasConnectedPair()) {
            return 0L;
        }
        this.refreshTickState();
        long extracted = Math.min(amount, this.transitPower);
        if (!simulate && extracted > 0L) {
            this.transitPower -= extracted;
            this.outputThisTick = saturatedAdd(this.outputThisTick, extracted);
            this.setChanged();
        }
        return extracted;
    }

    @Override
    public boolean canReceiveInternalPower(Direction side) {
        return this.mode == EntanglementLinkMode.INPUT && this.hasConnectedPair();
    }

    @Override
    public boolean canExtractInternalPower(Direction side) {
        return this.mode == EntanglementLinkMode.OUTPUT
                && this.transitPower > 0L
                && this.hasConnectedPair();
    }

    @Override
    public long getOutputVoltage() {
        return Math.max(1L, this.transitVoltage);
    }

    @Override
    public long getOutputAmps() {
        if (this.transitPower <= 0L || this.transitVoltage <= 0L) {
            return 0L;
        }
        return (this.transitPower - 1L) / this.transitVoltage + 1L;
    }

    @Override
    public long getAvailableOutputPower() {
        this.refreshTickState();
        return this.mode == EntanglementLinkMode.OUTPUT && this.hasConnectedPair()
                ? this.transitPower
                : 0L;
    }

    @Override
    public boolean canOutputToGrid(Direction side) {
        return this.canExtractInternalPower(side);
    }

    private ServerLevel serverLevel() {
        return (ServerLevel)this.level;
    }

    /**
     * Adds remotely transmitted power to the output link's per-tick transit
     * buffer.
     */
    private long acceptFromPair(long amount, long voltage) {
        if (amount <= 0L || this.mode != EntanglementLinkMode.OUTPUT) {
            return 0L;
        }

        this.refreshTickState();
        this.transitPower = saturatedAdd(this.transitPower, amount);
        this.transitVoltage = Math.max(1L, Math.max(this.transitVoltage, voltage));
        this.lastTransitTick = this.level == null ? 0L : this.level.getGameTime();
        this.setChanged();
        return amount;
    }

    /**
     * Simulates how much power the output side's local network can currently
     * consume.
     */
    private long outputDemandCapacity(long requested, long voltage) {
        if (requested <= 0L || this.mode != EntanglementLinkMode.OUTPUT || !(this.level instanceof ServerLevel serverLevel)) {
            return 0L;
        }

        long accepted = 0L;
        long remaining = requested;
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = this.worldPosition.relative(direction);
            if (this.level.getBlockState(neighborPos).getBlock() instanceof WireBlock) {
                long wireAccepted = WireEnergyTransfer.simulateWireNetworkAccepted(
                        serverLevel,
                        neighborPos,
                        this.worldPosition,
                        remaining,
                        voltage
                );
                accepted = saturatedAdd(accepted, wireAccepted);
            } else if (!(this.level.getBlockEntity(neighborPos) instanceof EntanglementLinkBlockEntity)) {
                PowerEndpoint endpoint = PowerEndpointAccess.find(
                        this.level,
                        neighborPos,
                        direction.getOpposite()
                );
                if (endpoint != null && endpoint.canReceive()) {
                    accepted = saturatedAdd(accepted, endpoint.receive(remaining, voltage, true));
                }
            }

            remaining = Math.max(0L, requested - accepted);
            if (remaining <= 0L) {
                return requested;
            }
        }
        return Math.min(requested, accepted);
    }

    private boolean hasConnectedPair() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return false;
        }
        EntanglementLinkManager.ResolvedLink resolved =
                EntanglementLinkManager.resolve(serverLevel, this);
        this.updateResolvedState(resolved);
        return resolved.connected();
    }

    /**
     * Pushes buffered output power into adjacent non-wire FE/GE consumers.
     *
     * <p>Wire networks pull from the link through {@link ConfiguredPowerSource};
     * this method only covers directly adjacent machines that expect a provider
     * to push energy into them.</p>
     */
    private void pushDirectOutputs(ServerLevel level) {
        this.refreshTickState();
        if (this.mode != EntanglementLinkMode.OUTPUT
                || this.transitPower <= 0L
                || !this.hasConnectedPair()) {
            return;
        }

        List<DirectDestination> destinations = new ArrayList<>(6);
        for (Direction direction : Direction.values()) {
            BlockPos destinationPos = this.worldPosition.relative(direction);
            if (level.getBlockState(destinationPos).getBlock() instanceof WireBlock
                    || level.getBlockEntity(destinationPos) instanceof EntanglementLinkBlockEntity) {
                continue;
            }

            PowerEndpoint endpoint = PowerEndpointAccess.find(
                    level,
                    destinationPos,
                    direction.getOpposite()
            );
            if (endpoint != null && endpoint.canReceive()
                    && endpoint.receive(1L, this.transitVoltage, true) > 0L) {
                destinations.add(new DirectDestination(direction, endpoint));
            }
        }
        if (destinations.isEmpty()) {
            return;
        }

        long remaining = this.transitPower;
        long[] capacities = new long[destinations.size()];
        long[] existingLoads = new long[destinations.size()];
        for (int index = 0; index < destinations.size(); index++) {
            capacities[index] = destinations.get(index)
                    .endpoint()
                    .receive(remaining, this.transitVoltage, true);
        }

        long[] allocations = PowerOfTwoChoices.distribute(
                remaining,
                capacities,
                existingLoads,
                level.random
        );
        for (int index = 0; index < allocations.length && this.transitPower > 0L; index++) {
            long offered = Math.min(this.transitPower, allocations[index]);
            if (offered <= 0L) {
                continue;
            }

            long inserted = destinations.get(index)
                    .endpoint()
                    .receive(offered, this.transitVoltage, false);
            if (inserted > 0L) {
                this.transitPower -= inserted;
                this.outputThisTick = saturatedAdd(this.outputThisTick, inserted);
                this.setChanged();
            }
        }
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
                this.previousLoss = this.lossThisTick;
            }
            this.stateTick = gameTime;
            this.inputThisTick = 0L;
            this.outputThisTick = 0L;
            this.lossThisTick = 0L;
            if (this.lastTransitTick < gameTime - 1L) {
                this.transitPower = 0L;
                this.transitVoltage = DEFAULT_VOLTAGE;
            }
        }
    }

    private void updateResolvedState(EntanglementLinkManager.ResolvedLink resolved) {
        EntanglementLinkStatus oldStatus = this.cachedStatus;
        this.cachedStatus = resolved.status();
        if (resolved.other() != null) {
            this.rememberPair(resolved.other());
        }
        if (oldStatus != this.cachedStatus) {
            this.sync();
        }
    }

    private void rememberPair(EntanglementLinkBlockEntity other) {
        if (other.level instanceof ServerLevel otherLevel) {
            this.lastPairDimension = otherLevel.dimension().location();
            this.lastPairPos = other.worldPosition.immutable();
        }
    }

    private void ensureStateMatchesMode() {
        if (this.level == null
                || this.level.isClientSide
                || !(this.getBlockState().getBlock() instanceof EntanglementLinkBlock)
                || this.getBlockState().getValue(EntanglementLinkBlock.MODE) == this.mode) {
            return;
        }
        this.level.setBlock(
                this.worldPosition,
                this.getBlockState().setValue(EntanglementLinkBlock.MODE, this.mode),
                3
        );
        this.level.invalidateCapabilities(this.worldPosition);
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

    private static long saturatedAdd(long first, long second) {
        if (first < 0L || second < 0L || first > Long.MAX_VALUE - second) {
            return Long.MAX_VALUE;
        }
        return first + second;
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return this.electron.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot == 0 ? this.electron : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot != 0 || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = this.electron.split(amount);
        if (!removed.isEmpty()) {
            if (this.electron.isEmpty()) {
                this.electron = ItemStack.EMPTY;
            }
            this.settingsChanged();
            if (this.level instanceof ServerLevel serverLevel) {
                EntanglementLinkManager.refresh(serverLevel, this);
            }
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot != 0) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = this.electron;
        this.electron = ItemStack.EMPTY;
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot != 0) {
            return;
        }
        if (stack.isEmpty()) {
            this.electron = ItemStack.EMPTY;
        } else if (stack.is(ModContent.ENTANGLED_ELECTRON.get())) {
            this.electron = stack.copyWithCount(1);
            EntangledElectronItem.getOrCreateEntanglementId(this.electron);
        } else {
            return;
        }

        this.transitPower = 0L;
        this.settingsChanged();
        if (this.level instanceof ServerLevel serverLevel) {
            EntanglementLinkManager.refresh(serverLevel, this);
        }
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == 0 && stack.is(ModContent.ENTANGLED_ELECTRON.get());
    }

    @Override
    public void clearContent() {
        this.setItem(0, ItemStack.EMPTY);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.mode = EntanglementLinkMode.valueOf(
                tag.getString(MODE_TAG).isBlank()
                        ? EntanglementLinkMode.INPUT.name()
                        : tag.getString(MODE_TAG)
        );
        this.electron = tag.contains(ELECTRON_TAG)
                ? ItemStack.parseOptional(registries, tag.getCompound(ELECTRON_TAG))
                : ItemStack.EMPTY;
        this.lastPairDimension = tag.contains(LAST_PAIR_DIMENSION_TAG)
                ? ResourceLocation.tryParse(tag.getString(LAST_PAIR_DIMENSION_TAG))
                : null;
        this.lastPairPos = tag.contains(LAST_PAIR_X_TAG)
                ? new BlockPos(
                tag.getInt(LAST_PAIR_X_TAG),
                tag.getInt(LAST_PAIR_Y_TAG),
                tag.getInt(LAST_PAIR_Z_TAG)
        )
                : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(MODE_TAG, this.mode.name());
        if (!this.electron.isEmpty()) {
            tag.put(ELECTRON_TAG, this.electron.save(registries, new CompoundTag()));
        }
        if (this.lastPairDimension != null) {
            tag.putString(LAST_PAIR_DIMENSION_TAG, this.lastPairDimension.toString());
        }
        if (this.lastPairPos != null) {
            tag.putInt(LAST_PAIR_X_TAG, this.lastPairPos.getX());
            tag.putInt(LAST_PAIR_Y_TAG, this.lastPairPos.getY());
            tag.putInt(LAST_PAIR_Z_TAG, this.lastPairPos.getZ());
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    private final class LinkEnergyStorage implements IEnergyStorage {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            return (int)Math.min(
                    Integer.MAX_VALUE,
                    EntanglementLinkBlockEntity.this.receiveInternalPower(
                            Direction.UP,
                            toReceive,
                            simulate
                    )
            );
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return (int)Math.min(
                    Integer.MAX_VALUE,
                    EntanglementLinkBlockEntity.this.extractInternalPower(
                            Direction.UP,
                            toExtract,
                            simulate
                    )
            );
        }

        @Override
        public int getEnergyStored() {
            return (int)Math.min(EntanglementLinkBlockEntity.this.transitPower, Integer.MAX_VALUE);
        }

        @Override
        public int getMaxEnergyStored() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean canExtract() {
            return EntanglementLinkBlockEntity.this.canExtractInternalPower(Direction.UP);
        }

        @Override
        public boolean canReceive() {
            return EntanglementLinkBlockEntity.this.canReceiveInternalPower(Direction.UP);
        }
    }

    private record DirectDestination(Direction direction, PowerEndpoint endpoint) {
    }
}
