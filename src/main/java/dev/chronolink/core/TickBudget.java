package dev.chronolink.core;

/** Shared by active and passive APIs to prevent rate multiplication in one tick. */
public final class TickBudget {
    private long tick=Long.MIN_VALUE;
    private long used;
    public long remaining(long now,long limit) {
        if(now!=tick) { tick=now; used=0; }
        return Math.max(0,limit-used);
    }
    public long spend(long now,long requested,long limit) {
        long n=Amounts.bounded(requested,remaining(now,limit));
        used+=n;
        return n;
    }
}
