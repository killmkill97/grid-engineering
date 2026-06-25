package dev.gridengineering.block.entity;

import dev.gridengineering.block.LaserTransformerBlock;
import dev.gridengineering.energy.ConfiguredPowerSource;
import dev.gridengineering.energy.InternalPowerEndpoint;
import dev.gridengineering.energy.VoltageAwarePowerSink;
import dev.gridengineering.laser.LaserLinkManager;
import dev.gridengineering.laser.LaserRole;
import dev.gridengineering.laser.LaserTier;
import dev.gridengineering.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class LaserTransformerBlockEntity extends BlockEntity
        implements InternalPowerEndpoint, VoltageAwarePowerSink, ConfiguredPowerSource {
    private static final String LINKED_POS_TAG = "LinkedPos";
    @Nullable
    private BlockPos linkedPos;
    private boolean linkDirty = true;

    private long laserPower;
    private long laserVoltage = 1L;
    private long laserReceiveTick = Long.MIN_VALUE;
    private long laserTransmitTick = Long.MIN_VALUE;
    private long outputTick = Long.MIN_VALUE;
    private long outputThisTick;

    public LaserTransformerBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.LASER_TRANSFORMER_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            LaserTransformerBlockEntity transformer
    ) {
        if (transformer.role() == LaserRole.SENDER) {
            LaserLinkManager.ensureLink(level, transformer);
        }
    }

    public LaserTier tier() {
        return this.getBlockState().getBlock() instanceof LaserTransformerBlock block
                ? block.tier()
                : LaserTier.MK1;
    }

    public LaserRole role() {
        return this.getBlockState().getBlock() instanceof LaserTransformerBlock block
                ? block.role()
                : LaserRole.SENDER;
    }

    public Direction facing() {
        return this.getBlockState().getValue(LaserTransformerBlock.FACING);
    }

    public Direction gridSide() {
        return this.facing().getOpposite();
    }

    @Nullable
    public BlockPos linkedPos() {
        return this.linkedPos;
    }

    public boolean isLinkDirty() {
        return this.linkDirty;
    }

    public void markLinkDirty() {
        this.linkDirty = true;
    }

    public void setLinkedPos(@Nullable BlockPos linkedPos) {
        BlockPos immutable = linkedPos == null ? null : linkedPos.immutable();
        if (java.util.Objects.equals(this.linkedPos, immutable) && !this.linkDirty) {
            return;
        }
        this.linkedPos = immutable;
        this.linkDirty = false;
        this.setChanged();
        this.sync();
    }

    @Override
    public long receiveInternalPower(Direction side, long amount, boolean simulate) {
        return 0L;
    }

    @Override
    public long receiveInternalPower(
            Direction side,
            long amount,
            long voltage,
            boolean simulate
    ) {
        if (this.role() != LaserRole.SENDER
                || side != this.gridSide()
                || amount <= 0L
                || voltage <= 0L
                || !(this.level instanceof ServerLevel serverLevel)) {
            return 0L;
        }
        long transferred = LaserLinkManager.transmit(
                serverLevel,
                this,
                amount,
                voltage,
                simulate
        );
        if (!simulate && transferred > 0L) {
            this.laserTransmitTick = serverLevel.getGameTime();
        }
        return transferred;
    }

    @Override
    public long extractInternalPower(Direction side, long amount, boolean simulate) {
        if (this.role() != LaserRole.RECEIVER
                || side != this.gridSide()
                || amount <= 0L
                || this.level == null) {
            return 0L;
        }

        this.refreshReceiverOutputTick();
        if (!this.hasFreshLaserPower()) {
            return 0L;
        }
        long extracted = Math.min(amount, this.laserPower);
        if (!simulate && extracted > 0L) {
            this.laserPower -= extracted;
            this.outputThisTick += extracted;
            this.setChanged();
        }
        return extracted;
    }

    @Override
    public boolean canReceiveInternalPower(Direction side) {
        return this.role() == LaserRole.SENDER && side == this.gridSide();
    }

    @Override
    public boolean canExtractInternalPower(Direction side) {
        return this.role() == LaserRole.RECEIVER
                && side == this.gridSide()
                && this.linkedPos != null;
    }

    @Override
    public long getOutputVoltage() {
        return this.role() == LaserRole.RECEIVER ? Math.max(1L, this.laserVoltage) : 1L;
    }

    @Override
    public long getOutputAmps() {
        return Long.MAX_VALUE;
    }

    @Override
    public long getAvailableOutputPower() {
        if (this.role() != LaserRole.RECEIVER || !this.hasFreshLaserPower()) {
            return 0L;
        }
        return this.laserPower;
    }

    @Override
    public boolean canOutputToGrid(Direction side) {
        return this.role() == LaserRole.RECEIVER
                && side == this.gridSide()
                && this.linkedPos != null;
    }

    public long offerLaserPower(long amount, long voltage, boolean simulate) {
        if (this.role() != LaserRole.RECEIVER
                || amount <= 0L
                || voltage <= 0L
                || this.level == null) {
            return 0L;
        }

        long gameTime = this.level.getGameTime();
        long existingPower = this.laserReceiveTick == gameTime ? this.laserPower : 0L;
        if (existingPower > 0L && this.laserVoltage != voltage) {
            return 0L;
        }

        long accepted = Math.min(amount, Math.max(0L, Long.MAX_VALUE - existingPower));
        if (!simulate && accepted > 0L) {
            if (this.laserReceiveTick != gameTime) {
                this.laserPower = 0L;
                this.outputThisTick = 0L;
            }
            this.laserReceiveTick = gameTime;
            this.laserVoltage = voltage;
            this.laserPower = saturatedAdd(this.laserPower, accepted);
            this.setChanged();
        }
        return accepted;
    }

    public boolean isTransmittingForDisplay() {
        if (this.level == null || this.linkedPos == null) {
            return false;
        }
        long activityTick = this.role() == LaserRole.SENDER
                ? this.laserTransmitTick
                : this.laserReceiveTick;
        long gameTime = this.level.getGameTime();
        return activityTick == gameTime || activityTick == gameTime - 1L;
    }

    public long getMaximumVoltageForDisplay() {
        long maximumVoltage = this.tier().maxVoltage();
        if (this.level != null
                && this.linkedPos != null
                && this.level.getBlockEntity(this.linkedPos)
                instanceof LaserTransformerBlockEntity linkedTransformer) {
            maximumVoltage = Math.min(maximumVoltage, linkedTransformer.tier().maxVoltage());
        }
        return maximumVoltage;
    }

    private boolean hasFreshLaserPower() {
        if (this.level == null || this.laserPower <= 0L) {
            return false;
        }
        long gameTime = this.level.getGameTime();
        return this.laserReceiveTick == gameTime || this.laserReceiveTick == gameTime - 1L;
    }

    private void refreshReceiverOutputTick() {
        if (this.level == null) {
            return;
        }
        long gameTime = this.level.getGameTime();
        if (this.outputTick != gameTime) {
            this.outputTick = gameTime;
            this.outputThisTick = 0L;
            if (this.laserReceiveTick < gameTime - 1L) {
                this.laserPower = 0L;
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel serverLevel) {
            this.linkDirty = true;
            LaserLinkManager.register(serverLevel, this);
        }
    }

    @Override
    public void setRemoved() {
        if (this.level instanceof ServerLevel serverLevel) {
            LaserLinkManager.unregister(serverLevel, this.worldPosition);
        }
        super.setRemoved();
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
        this.linkedPos = tag.contains(LINKED_POS_TAG)
                ? BlockPos.of(tag.getLong(LINKED_POS_TAG))
                : null;
        this.linkDirty = true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.linkedPos != null) {
            tag.putLong(LINKED_POS_TAG, this.linkedPos.asLong());
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

    @Override
    public void onDataPacket(
            Connection connection,
            ClientboundBlockEntityDataPacket packet,
            HolderLookup.Provider registries
    ) {
        this.loadWithComponents(packet.getTag(), registries);
    }

    private static long saturatedAdd(long first, long second) {
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }
}
