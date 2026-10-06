package dev.chronolink;

import java.io.File;
import net.minecraftforge.common.config.Configuration;

public final class ModConfig {
    private ModConfig() {}
    public static int itemBatch=16, fluidRate=1000, rfRate=100000, routeComparisons=4096;
    public static boolean allowUnratedEUOutputs=false, particles=true;
    public static void load(File file) {
        Configuration c=new Configuration(file); c.load();
        itemBatch=c.getInt("itemBatch","transfer",16,1,64,"Items extracted per five ticks per source.");
        fluidRate=c.getInt("fluidPerTick","transfer",1000,1,16000,"mB/t per endpoint, active + passive shared.");
        rfRate=c.getInt("rfPerTick","transfer",100000,1,10000000,"Native RF/t per endpoint; no EU conversion.");
        routeComparisons=c.getInt("maxRouteComparisons","performance",4096,64,65536,"Global bounded route work per tick.");
        allowUnratedEUOutputs=c.getBoolean("allowUnratedEUOutputs","safety",false,
            "DANGEROUS: permit EU outputs to endpoints with no readable voltage rating. Leave false until cable tests pass.");
        particles=c.getBoolean("particles","client",true,"Sparse particles only while actual transfers are active.");
        if(c.hasChanged()) c.save();
    }
}
