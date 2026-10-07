package dev.chronolink.v2.core;

/** Allow painting several faces in one tick while keeping GUI requests bounded. */
public final class UiActionBudget {
    private long tick=Long.MIN_VALUE;
    private int count;
    public boolean allow(long now){if(now!=tick){tick=now;count=0;}if(count>=8)return false;count++;return true;}
}
