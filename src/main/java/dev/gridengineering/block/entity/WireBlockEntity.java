package dev.gridengineering.block.entity;

import dev.gridengineering.registry.ModContent;
import dev.gridengineering.energy.WireEnergyTransfer;
import dev.gridengineering.block.WireBlock;
import dev.gridengineering.block.WirePortMode;
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

public final class WireBlockEntity extends BlockEntity {
    private static final String CUT_SIDES_TAG = "CutSides";
    private static final String MANUAL_SIDES_TAG = "ManualSides";
    private static final String INPUT_SIDES_TAG = "InputSides";
    private static final String OUTPUT_SIDES_TAG = "OutputSides";
    private static final int ALL_SIDES_MASK = 0b11_1111;

    private int cutSides;
    private int manualSides;
    private int inputSides;
    private int outputSides;

    public WireBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.WIRE_BLOCK_ENTITY.get(), pos, state);
    }

    public boolean isSideCut(Direction direction) {
        return (this.cutSides & bit(direction)) != 0;
    }

    public boolean isSideManuallyExtended(Direction direction) {
        return (this.manualSides & bit(direction)) != 0;
    }

    public WirePortMode getPortMode(Direction direction) {
        int directionBit = bit(direction);
        if ((this.inputSides & directionBit) != 0) {
            return WirePortMode.INPUT;
        }
        if ((this.outputSides & directionBit) != 0) {
            return WirePortMode.OUTPUT;
        }
        return WirePortMode.AUTO;
    }

    public void setSideCut(Direction direction, boolean cut) {
        int oldValue = this.cutSides;
        if (cut) {
            this.cutSides |= bit(direction);
        } else {
            this.cutSides &= ~bit(direction);
        }

        if (this.cutSides != oldValue) {
            this.setChanged();
        }
    }

    public void setSideManuallyExtended(Direction direction, boolean extended) {
        int oldValue = this.manualSides;
        if (extended) {
            this.manualSides |= bit(direction);
        } else {
            this.manualSides &= ~bit(direction);
        }

        if (this.manualSides != oldValue) {
            this.setChanged();
        }
    }

    public WirePortMode cyclePortMode(Direction direction) {
        WirePortMode nextMode = this.getPortMode(direction).next();
        int directionBit = bit(direction);
        this.inputSides &= ~directionBit;
        this.outputSides &= ~directionBit;
        if (nextMode == WirePortMode.INPUT) {
            this.inputSides |= directionBit;
        } else if (nextMode == WirePortMode.OUTPUT) {
            this.outputSides |= directionBit;
        }
        this.setChanged();
        this.sync();
        return nextMode;
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, WireBlockEntity wire) {
        WireBlock.refreshConnections(level, pos);
        WireEnergyTransfer.tickFromWire(level, pos, level.getBlockState(pos));
    }

    public void sync() {
        if (this.level != null && !this.level.isClientSide) {
            BlockState state = this.getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, 2);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.cutSides = tag.getByte(CUT_SIDES_TAG) & ALL_SIDES_MASK;
        this.manualSides = tag.getByte(MANUAL_SIDES_TAG) & ALL_SIDES_MASK;
        this.inputSides = tag.getByte(INPUT_SIDES_TAG) & ALL_SIDES_MASK;
        this.outputSides = tag.getByte(OUTPUT_SIDES_TAG) & ALL_SIDES_MASK;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putByte(CUT_SIDES_TAG, (byte)this.cutSides);
        tag.putByte(MANUAL_SIDES_TAG, (byte)this.manualSides);
        tag.putByte(INPUT_SIDES_TAG, (byte)this.inputSides);
        tag.putByte(OUTPUT_SIDES_TAG, (byte)this.outputSides);
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
        CompoundTag tag = packet.getTag();
        if (!tag.isEmpty()) {
            this.loadWithComponents(tag, registries);
        }

        this.requestModelDataUpdate();
        if (this.level != null && this.level.isClientSide) {
            BlockState state = this.getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, 2);
        }
    }

    private static int bit(Direction direction) {
        return 1 << direction.get3DDataValue();
    }
}
