package dev.gridengineering.energy;

public interface PowerEndpoint {
    long receive(long amount, boolean simulate);

    default long receive(long amount, long voltage, boolean simulate) {
        return this.receive(amount, simulate);
    }

    long extract(long amount, boolean simulate);

    boolean canReceive();

    boolean canExtract();
}
