package dev.chronolink.core;

/** Arithmetic used at resource boundaries. Never round resource quantities up. */
public final class Amounts {
    private Amounts() {}
    public static long bounded(long request,long limit) {
        return request<=0 || limit<=0 ? 0 : Math.min(request,limit);
    }
    public static long saturatingAdd(long a,long b) {
        if(a<0 || b<0) throw new IllegalArgumentException("negative amount");
        return b>Long.MAX_VALUE-a?Long.MAX_VALUE:a+b;
    }
    public static long acceptedAmps(long voltage,long offered,long room,long remainingAmps) {
        if(voltage<=0 || offered<=0 || room<=0 || remainingAmps<=0) return 0;
        return Math.min(Math.min(offered,remainingAmps),room/voltage);
    }
    public static int slotRoom(int inventoryLimit,int itemLimit,int existing) {
        return Math.max(0,Math.min(inventoryLimit,itemLimit)-Math.max(0,existing));
    }
}
