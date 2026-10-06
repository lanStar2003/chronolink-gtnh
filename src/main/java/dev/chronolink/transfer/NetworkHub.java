package dev.chronolink.transfer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.chronolink.ModConfig;
import dev.chronolink.core.Router;
import dev.chronolink.tile.TileConnector;

/** References only to loaded TileEntities; never calls DimensionManager.getWorld or loads chunks. */
public final class NetworkHub {
    public static final NetworkHub INSTANCE=new NetworkHub();
    private final Set<TileConnector> nodes=new LinkedHashSet<TileConnector>();
    private Router router=new Router();
    public Router.Result lastResult=new Router.Result();
    private NetworkHub() {}
    public void register(TileConnector node) { if(node.getWorldObj()!=null && !node.getWorldObj().isRemote) nodes.add(node); }
    public void unregister(TileConnector node) { nodes.remove(node); }
    public void clear() { nodes.clear(); router=new Router(); lastResult=new Router.Result(); }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END) return;
        ArrayList<TileConnector> loaded=new ArrayList<TileConnector>();
        for(TileConnector node:new ArrayList<TileConnector>(nodes)) {
            if(!node.isLoaded()) nodes.remove(node); else loaded.add(node);
        }
        lastResult=router.tick(loaded,ModConfig.routeComparisons);
    }
}
