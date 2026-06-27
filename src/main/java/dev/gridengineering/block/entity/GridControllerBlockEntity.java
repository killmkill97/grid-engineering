package dev.gridengineering.block.entity;

import dev.gridengineering.block.GridControllerBlock;
import dev.gridengineering.config.FailureConfig;
import dev.gridengineering.energy.ConfiguredPowerSource;
import dev.gridengineering.energy.InternalPowerEndpoint;
import dev.gridengineering.energy.VoltageAwarePowerSink;
import dev.gridengineering.energy.WireEnergyTransfer;
import dev.gridengineering.gridcontroller.GridControllerTier;
import dev.gridengineering.registry.ModContent;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class GridControllerBlockEntity extends BlockEntity
        implements InternalPowerEndpoint, VoltageAwarePowerSink, ConfiguredPowerSource {
    private long stateTick = Long.MIN_VALUE;
    private long transitPower;
    private long activeVoltage;
    private long inputThisTick;
    private long outputThisTick;
    private long usedMicroAmpsThisTick;
    private long previousInput;
    private long previousOutput;
    private long previousUsedMicroAmps;
    private long lastInputTick = Long.MIN_VALUE;
    private boolean failed;
    private final long[] outputBySideThisTick = new long[Direction.values().length];

    public GridControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.GRID_CONTROLLER_BLOCK_ENTITY.get(), pos, state);
        this.activeVoltage = this.tier().maxVoltage();
    }

    public static void serverTick(
            BlockPos pos,
            BlockState state,
            GridControllerBlockEntity controller
    ) {
        controller.refreshTickState();
        if (controller.level == null || !(controller.level instanceof ServerLevel level)) {
            return;
        }

        Direction inputSide = controller.getInputSide();
        for (Direction direction : Direction.values()) {
            if (direction == inputSide) {
                continue;
            }

            BlockPos neighborPos = pos.relative(direction);
            if (level.getBlockState(neighborPos).getBlock() instanceof dev.gridengineering.block.WireBlock) {
                continue;
            }
            controller.outputBySideThisTick[direction.get3DDataValue()] = 0L;
        }
    }

    public Direction getInputSide() {
        return this.getBlockState().getValue(GridControllerBlock.FACING);
    }

    public GridControllerTier tier() {
        return this.getBlockState().getBlock() instanceof GridControllerBlock controller
                ? controller.tier()
                : GridControllerTier.MK1;
    }

    public long getMaxVoltage() {
        return this.tier().maxVoltage();
    }

    public String getVoltageTierName() {
        return this.tier().voltageTierName();
    }

    public long getMaxAmps() {
        return this.tier().maxAmps();
    }

    public long getInputPowerForDisplay() {
        this.refreshTickState();
        return this.previousInput;
    }

    public long getOutputPowerForDisplay() {
        this.refreshTickState();
        return this.previousOutput;
    }

    public long getUsedMicroAmpsForDisplay() {
        this.refreshTickState();
        return this.previousUsedMicroAmps;
    }

    @Override
    public long getOutputVoltage() {
        this.refreshTickState();
        return Math.max(1L, this.activeVoltage);
    }

    @Override
    public long getOutputAmps() {
        return this.tier().maxAmps();
    }

    @Override
    public long getAvailableOutputPower() {
        this.refreshTickState();
        return this.transitPower;
    }

    @Override
    public boolean canOutputToGrid(Direction side) {
        return side != this.getInputSide();
    }

    @Override
    public long receiveInternalPower(Direction side, long amount, boolean simulate) {
        return this.receiveInternalPower(side, amount, this.tier().maxVoltage(), simulate);
    }

    @Override
    public long receiveInternalPower(Direction side, long amount, long voltage, boolean simulate) {
        if (!this.canReceiveInternalPower(side) || amount <= 0L) {
            return 0L;
        }
        this.refreshTickState();
        long safeVoltage = Math.max(1L, voltage);
        // Grid Controllers distribute same-tick transit power and do not currently enforce
        // an amperage limit. Keep the old calculation here for future balancing if controller
        // amperage limits become meaningful again.
        // long incomingMicroAmps = WireEnergyTransfer.toMicroAmps(amount, safeVoltage);
        // long maxMicroAmps = this.tier().maxAmps() * WireEnergyTransfer.MICRO_AMPS_PER_AMP;
        boolean overloaded = safeVoltage > this.tier().maxVoltage();
        if (overloaded) {
            if (!simulate) {
                this.fail();
            }
            return amount;
        }

        long accepted = amount;
        if (!simulate) {
            this.activeVoltage = safeVoltage;
            this.transitPower = saturatedAdd(this.transitPower, accepted);
            this.inputThisTick = saturatedAdd(this.inputThisTick, accepted);
            this.usedMicroAmpsThisTick = saturatedAdd(
                    this.usedMicroAmpsThisTick,
                    WireEnergyTransfer.toMicroAmps(accepted, safeVoltage)
            );
            this.lastInputTick = this.level == null ? 0L : this.level.getGameTime();
            this.setChanged();
            this.sync();
        }
        return accepted;
    }

    @Override
    public long extractInternalPower(Direction side, long amount, boolean simulate) {
        if (!this.canExtractInternalPower(side) || amount <= 0L) {
            return 0L;
        }
        this.refreshTickState();
        long extracted = Math.min(amount, this.transitPower);
        if (!simulate && extracted > 0L) {
            this.transitPower -= extracted;
            this.outputThisTick = saturatedAdd(this.outputThisTick, extracted);
            this.outputBySideThisTick[side.get3DDataValue()] =
                    saturatedAdd(this.outputBySideThisTick[side.get3DDataValue()], extracted);
            this.setChanged();
            this.sync();
        }
        return extracted;
    }

    @Override
    public boolean canReceiveInternalPower(Direction side) {
        return !this.failed && side == this.getInputSide();
    }

    @Override
    public boolean canExtractInternalPower(Direction side) {
        this.refreshTickState();
        return !this.failed && side != this.getInputSide() && this.transitPower > 0L;
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
                this.previousUsedMicroAmps = this.usedMicroAmpsThisTick;
            }
            this.stateTick = gameTime;
            this.inputThisTick = 0L;
            this.outputThisTick = 0L;
            this.usedMicroAmpsThisTick = 0L;
            Arrays.fill(this.outputBySideThisTick, 0L);
            if (this.lastInputTick < gameTime - 1L) {
                this.transitPower = 0L;
                this.activeVoltage = this.tier().maxVoltage();
            }
        }
    }

    private void fail() {
        if (this.failed || this.level == null || this.level.isClientSide) {
            return;
        }
        this.failed = true;
        if (FailureConfig.gridControllerFailure() == FailureConfig.FailureAction.BREAK) {
            this.level.destroyBlock(this.worldPosition, false);
            this.level.playSound(
                    null,
                    this.worldPosition,
                    SoundEvents.GLASS_BREAK,
                    SoundSource.BLOCKS,
                    1.0F,
                    0.75F
            );
            return;
        }

        this.level.removeBlock(this.worldPosition, false);
        this.level.explode(
                null,
                this.worldPosition.getX() + 0.5D,
                this.worldPosition.getY() + 0.5D,
                this.worldPosition.getZ() + 0.5D,
                4.0F,
                Level.ExplosionInteraction.BLOCK
        );
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
        this.activeVoltage = this.tier().maxVoltage();
        this.lastInputTick = Long.MIN_VALUE;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        this.saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static boolean wouldExceed(long current, long incoming, long max) {
        return incoming > max || current > max - incoming;
    }

    private static long saturatedAdd(long first, long second) {
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }
}
