package dev.chronolink.v2.core;

/** Pure integer policy used by the real relay. No energy conversions or overflow. */
public final class RelayMath {
    private RelayMath() {}
    public static long bounded(long offered,long available,long room,long budget) {
        return Math.max(0,Math.min(Math.min(offered,available),Math.min(room,budget)));
    }
    public static long add(long a,long b) { return a<0||b<0?0:(a>Long.MAX_VALUE-b?Long.MAX_VALUE:a+b); }
    public static long product(long a,long b) { return a<=0||b<=0?0:(a>Long.MAX_VALUE/b?Long.MAX_VALUE:a*b); }
    public static long packets(long available,long voltage,long amps) {
        return voltage<=0||available<=0||amps<=0?0:Math.min(available/voltage,amps);
    }
    public static long safeVoltage(long local,long endpoint,boolean incomplete) {
        return incomplete||local<=0||endpoint<=0?0:Math.min(local,endpoint);
    }
    public static boolean cardRemovable(long stored,long capacityAfter) {
        return stored>=0 && capacityAfter>=stored;
    }
}
