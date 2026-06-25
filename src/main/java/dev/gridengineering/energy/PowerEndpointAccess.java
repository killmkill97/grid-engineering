package dev.gridengineering.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

public final class PowerEndpointAccess {
    private PowerEndpointAccess() {
    }

    @Nullable
    public static PowerEndpoint find(Level level, BlockPos pos, Direction side) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof InternalPowerEndpoint internal) {
            return new InternalAdapter(internal, side);
        }

        IEnergyStorage feStorage = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side);
        return feStorage == null ? null : new FeAdapter(feStorage);
    }

    public static boolean exists(Level level, BlockPos pos, Direction side) {
        PowerEndpoint endpoint = find(level, pos, side);
        return endpoint != null && (endpoint.canReceive() || endpoint.canExtract());
    }

    private record InternalAdapter(InternalPowerEndpoint endpoint, Direction side) implements PowerEndpoint {
        @Override
        public long receive(long amount, boolean simulate) {
            return this.endpoint.receiveInternalPower(this.side, amount, simulate);
        }

        @Override
        public long receive(long amount, long voltage, boolean simulate) {
            if (this.endpoint instanceof VoltageAwarePowerSink voltageAware) {
                return voltageAware.receiveInternalPower(this.side, amount, voltage, simulate);
            }
            return this.receive(amount, simulate);
        }

        @Override
        public long extract(long amount, boolean simulate) {
            return this.endpoint.extractInternalPower(this.side, amount, simulate);
        }

        @Override
        public boolean canReceive() {
            return this.endpoint.canReceiveInternalPower(this.side);
        }

        @Override
        public boolean canExtract() {
            return this.endpoint.canExtractInternalPower(this.side);
        }
    }

    private record FeAdapter(IEnergyStorage storage) implements PowerEndpoint {
        @Override
        public long receive(long amount, boolean simulate) {
            if (amount <= 0L) {
                return 0L;
            }
            return this.storage.receiveEnergy((int)Math.min(amount, Integer.MAX_VALUE), simulate);
        }

        @Override
        public long extract(long amount, boolean simulate) {
            if (amount <= 0L) {
                return 0L;
            }
            return this.storage.extractEnergy((int)Math.min(amount, Integer.MAX_VALUE), simulate);
        }

        @Override
        public boolean canReceive() {
            return this.storage.canReceive();
        }

        @Override
        public boolean canExtract() {
            return this.storage.canExtract();
        }
    }
}
