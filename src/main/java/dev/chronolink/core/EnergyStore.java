package dev.chronolink.core;

/** Native-unit buffer; EU and RF must use separate instances. Server-thread confined. */
public final class EnergyStore {
    private final long capacity;
    private long stored;
    public EnergyStore(long capacity) {
        if(capacity<1) throw new IllegalArgumentException("capacity");
        this.capacity=capacity;
    }
    public long amount() { return stored; }
    public long capacity() { return capacity; }
    public long room() { return capacity-stored; }
    public long insert(long requested,boolean simulate) {
        long n=Amounts.bounded(requested,room());
        if(!simulate) stored+=n;
        return n;
    }
    public long extract(long requested,boolean simulate) {
        long n=Amounts.bounded(requested,stored);
        if(!simulate) stored-=n;
        return n;
    }
    public long acceptPacket(long voltage,long offeredAmps,long remainingAmps,boolean simulate) {
        long amps=Amounts.acceptedAmps(voltage,offeredAmps,room(),remainingAmps);
        if(!simulate) stored+=voltage*amps; // product <= room by construction
        return amps; // GT returns AMPS, not EU
    }
    /** Reject invalid saved values rather than silently deleting resources. */
    public void restore(long value) {
        if(value<0 || value>capacity) throw new IllegalArgumentException("invalid saved energy");
        stored=value;
    }
    public long moveTo(EnergyStore target,long limit) {
        if(target==this) return 0;
        long n=Math.min(Amounts.bounded(limit,stored),target.room());
        stored-=n; target.stored+=n;
        return n;
    }
}
