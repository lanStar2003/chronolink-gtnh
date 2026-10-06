package dev.chronolink.core;

/** Different currencies NEVER share a route. Gaseous Forge fluids use FLUID. */
public enum ResourceKind {
    ITEM, FLUID, EU, RF;
    public static ResourceKind safe(int value) {
        return value >= 0 && value < values().length ? values()[value] : ITEM;
    }
}
