package dev.chronolink.core;

/** Small, bounded settings model. Every mutation is confirmed by the server container. */
public final class NodeSettings {
    public static final int CHANNEL_DOWN=0, CHANNEL_UP=1, DIRECTION=2, RESOURCE=3,
        FACE=4, ENABLE=5, VOLTAGE_DOWN=6, VOLTAGE_UP=7, AMPS=8;
    public static final String[] TIER_NAMES = {
        "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UMV", "UXV", "MAX"
    };
    public int channel = 1;
    public int face = 2;
    public ResourceKind resource = ResourceKind.ITEM;
    /** Import = adjacent machine -> network. Export = network -> adjacent machine. */
    public boolean importing = true;
    public boolean enabled = false;
    public int tier = 1;
    public int amps = 1;

    public long voltage() { return 8L << (2 * clamp(tier, 0, 14)); }
    public void sanitize() {
        channel=clamp(channel,1,64); face=clamp(face,0,5); tier=clamp(tier,0,14);
        amps=clamp(amps,1,16); if(resource==null) resource=ResourceKind.ITEM;
    }
    public boolean apply(int action, boolean hasPayload) {
        if (action < 0 || action > AMPS) return false;
        if (hasPayload && (action==CHANNEL_DOWN || action==CHANNEL_UP || action==DIRECTION || action==RESOURCE)) return false;
        // Configuration always disarms a node; enabling remains an explicit action.
        switch(action) {
            case CHANNEL_DOWN: channel=channel==1?64:channel-1; break;
            case CHANNEL_UP: channel=channel==64?1:channel+1; break;
            case DIRECTION: importing=!importing; break;
            case RESOURCE: resource=ResourceKind.values()[(resource.ordinal()+1)%4]; break;
            case FACE: face=(face+1)%6; break;
            case ENABLE: enabled=!enabled; return true;
            case VOLTAGE_DOWN: tier=Math.max(0,tier-1); break;
            case VOLTAGE_UP: tier=Math.min(14,tier+1); break;
            case AMPS: amps=amps==16?1:amps*2; break;
            default: return false;
        }
        enabled=false; sanitize(); return true;
    }
    public static int clamp(int n,int lo,int hi) { return Math.max(lo,Math.min(hi,n)); }
}
