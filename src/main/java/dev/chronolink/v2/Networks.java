package dev.chronolink.v2;

import java.util.*;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import gregtech.api.interfaces.tileentity.IEnergyConnected;
import dev.chronolink.ModConfig;
import dev.chronolink.v2.core.RelayMath;
import dev.chronolink.v2.core.Transfer;
import dev.chronolink.v2.store.*;
import dev.chronolink.v2.tile.*;

/** Server-side registry and bounded scheduler. Local transfers never use an inventory in this class. */
public final class Networks {
    public static final Networks INSTANCE=new Networks();
    private final Set<TileConduit> nodes=new LinkedHashSet<TileConduit>();
    private final Set<TileCenter> centers=new LinkedHashSet<TileCenter>();
    private boolean busy;
    private int cursor,work;
    private Networks(){}
    public void register(TileConduit t){nodes.add(t);}
    public void unregister(TileConduit t){nodes.remove(t);}
    public void register(TileCenter c){centers.add(c);}
    public void unregister(TileCenter c){centers.remove(c);}
    public void clear(){nodes.clear();centers.clear();busy=false;cursor=0;}
    public List<TileCenter> centers(UUID owner){List<TileCenter> out=new ArrayList<TileCenter>();for(TileCenter c:centers)if(c.loaded()&&owner!=null&&owner.equals(c.owner))out.add(c);return out;}
    public TileCenter center(TileConduit n){if(n.network==null||n.owner==null)return null;TileCenter found=null;
        for(TileCenter c:centers)if(c.loaded()&&n.network.equals(c.network)&&n.owner.equals(c.owner)){if(found!=null&&found!=c)return null;found=c;}return found;}
    public List<TileConduit> members(TileCenter c){List<TileConduit> out=new ArrayList<TileConduit>();for(TileConduit n:nodes)if(n.loaded()&&c.network!=null&&c.network.equals(n.network)&&c.owner!=null&&c.owner.equals(n.owner))out.add(n);return out;}
    public void autoBind(TileConduit n){if(n.network!=null)return;double best=32*32+1;TileCenter chosen=null;for(TileCenter c:centers(n.owner)){
        if(c.getWorldObj()!=n.getWorldObj())continue;double d=n.getDistanceFrom(c.xCoord+.5,c.yCoord+.5,c.zCoord+.5);if(d<best){best=d;chosen=c;}}
        if(chosen!=null){n.network=chosen.network;n.changed();return;}
        for(int s=0;s<6;s++){TileEntity raw=n.neighbor(s);if(raw instanceof TileConduit){TileConduit other=(TileConduit)raw;if(n.owner!=null&&n.owner.equals(other.owner)&&other.network!=null){n.network=other.network;n.changed();return;}}}}
    private boolean same(TileConduit a,TileConduit b){return b!=a&&b.ready()&&a.network!=null&&a.network.equals(b.network)&&a.owner.equals(b.owner);}
    private void record(TileCenter c,TileConduit n,Key k,long amount,boolean input){if(amount<=0)return;n.spent(k,amount,input);c.lastMovement=c.now();c.meter.moved(k,amount,input,c.now());}
    private static Boundary boundary(TileConduit n,int face){TileEntity raw=n.endpoint(face);return raw==null?null:new Boundary(raw,ForgeDirection.getOrientation(face).getOpposite());}
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e){if(e.phase!=TickEvent.Phase.END)return;
        dev.chronolink.v2.net.Packets.processActions();
        nodes.removeIf(n->!n.loaded());centers.removeIf(c->!c.loaded());if(busy||nodes.isEmpty())return;
        busy=true;work=ModConfig.routeComparisons;
        try{List<TileConduit> all=new ArrayList<TileConduit>(nodes);int start=Math.floorMod(cursor++,all.size());
            for(int i=0;i<all.size()&&work>0;i++){TileConduit n=all.get((start+i)%all.size());if(!n.ready())continue;TileCenter c=center(n);
                try{for(int s=0;s<6&&work>0;s++){Boundary b=boundary(n,s);if(b==null)continue;work--;
                    if(n.modes[s]==TileConduit.IN){for(Key k:b.samples(n.cursor,n.now()%5==0)){if(work--<=0)break;if(!n.allows(k,s)||n.remaining(k,true)<=0)continue;
                            if(c.storageMode){long moved=Transfer.move(b,c,k,n.remaining(k,true),n);record(c,n,k,moved,true);}
                            else localPush(n,b,k,all);if(!n.ready())break;}}
                    else if(n.modes[s]==TileConduit.OUT&&c.storageMode){Key[] keys={n.now()%5==0?n.itemFilters[s]:null,n.fluidFilters[s],Key.RF_KEY};
                        for(Key k:keys){if(k==null||work--<=0)continue;long moved=Transfer.move(c,b,k,n.remaining(k,false),n);record(c,n,k,moved,false);if(!n.ready())break;}
                        if(n.ready())wirelessOutput(c,n,s);
                    }
                }n.cursor+=64;}catch(RuntimeException ex){n.fail("Native boundary: "+ex.getClass().getSimpleName());}
            }
        }finally{busy=false;}}
    private void localPush(TileConduit from,Boundary source,Key k,List<TileConduit> all){TileCenter c=center(from);int start=Math.floorMod(cursor,all.size());
        for(int j=0;j<all.size()&&work>0&&from.remaining(k,true)>0;j++){TileConduit to=all.get((start+j)%all.size());if(!same(from,to))continue;
            for(int s=0;s<6&&work>0;s++){if(to.modes[s]!=TileConduit.OUT||!to.allows(k,s))continue;Boundary dest=boundary(to,s);if(dest==null)continue;work--;
                long max=Math.min(from.remaining(k,true),to.remaining(k,false));long moved=Transfer.move(source,dest,k,max,from);
                if(moved>0){record(c,from,k,moved,true);record(c,to,k,moved,false);}if(!from.ready()||from.remaining(k,true)==0)break;}}}
    public long offer(TileConduit n,int face,Key k,long count,boolean sim){if(busy||k==null||count<=0||!n.input(face)||!n.allows(k,face))return 0;busy=true;
        long sent=0,pending=0,max=0;TileCenter c=center(n);
        try{max=Math.min(count,n.remaining(k,true));if(max<=0)return 0;
            if(c.storageMode){pending=max;long accepted=Transfer.checked(c.insert(k,max,sim),max);pending=0;if(!sim)record(c,n,k,accepted,true);return accepted;}
            int checks=0;for(TileConduit to:members(c)){if(++checks>ModConfig.routeComparisons)break;if(!same(n,to))continue;for(int s=0;s<6&&sent<max;s++){if(!to.output(s)||!to.allows(k,s))continue;Boundary b=boundary(to,s);if(b==null||b.tile==n.endpoint(face))continue;
                    long limit=Math.min(max-sent,to.remaining(k,false));pending=limit;long accepted=Transfer.checked(b.insert(k,limit,sim),limit);pending=0;sent+=accepted;if(!sim)record(c,to,k,accepted,false);}}
            if(!sim)record(c,n,k,sent,true);return sent;
        }catch(RuntimeException ex){n.uncertain(k,pending,sim?"simulate-offer":"passive-offer");if(!sim)record(c,n,k,sent,true);
            // Ambiguous native mutations are quarantined, not automatically repeated or counted as successful throughput.
            return sim?0:Math.min(max,sent+pending);
        }finally{busy=false;}}
    public long draw(TileConduit n,int side,Key k,long count,boolean sim){if(busy||k==null||count<=0||!n.output(side)||!n.allows(k,side))return 0;busy=true;
        try{TileCenter c=center(n);long max=Math.min(count,n.remaining(k,false));if(max<=0)return 0;
            if(c.storageMode){long got=Transfer.checked(c.extract(k,max,sim),max);if(!sim){n.refundStore[side]=c;record(c,n,k,got,false);}return got;}
            for(TileConduit source:members(c)){if(!same(n,source))continue;for(int s=0;s<6;s++){if(!source.input(s)||!source.allows(k,s))continue;Boundary b=boundary(source,s);if(b==null||b.tile==n.endpoint(side))continue;
                    long limit=Math.min(max,source.remaining(k,true));long got=Transfer.checked(b.extract(k,limit,sim),limit);if(got<=0)continue;
                    if(!sim){n.refundStore[side]=b;record(c,source,k,got,true);record(c,n,k,got,false);}return got;}}
            return 0;
        }catch(RuntimeException ex){n.uncertain(k,count,sim?"simulate-draw":"passive-draw");return 0;}finally{busy=false;}}
    public Key first(TileConduit n,int side,boolean item){if(busy||!n.output(side))return null;Key configured=item?n.itemFilters[side]:n.fluidFilters[side];if(configured!=null)return configured;
        TileCenter c=center(n);if(c==null||c.storageMode)return null;for(TileConduit source:members(c)){if(!same(n,source))continue;for(int s=0;s<6;s++){if(!source.input(s))continue;Boundary b=boundary(source,s);if(b==null||b.tile==n.endpoint(side))continue;
                for(Key k:b.samples(source.cursor,true))if((item?k.kind==Key.ITEM:k.isFluid())&&source.allows(k,s)&&b.extract(k,1,true)>0)return k;}}return null;}
    public Map<Key,Long> catalogue(TileCenter c,int group,int page){Map<Key,Long> out=new LinkedHashMap<Key,Long>();if(c==null||!c.active())return out;
        if(c.storageMode){for(Map.Entry<Key,Long> e:c.stock.entries.entrySet())if(group==0?e.getKey().kind==Key.ITEM:e.getKey().isFluid())out.put(e.getKey(),e.getValue());
            if(c.ae.attached()){Map<Key,Long> ae=c.ae.sample(0,4096,group);for(Map.Entry<Key,Long> e:ae.entrySet())out.put(e.getKey(),RelayMath.add(out.containsKey(e.getKey())?out.get(e.getKey()):0,e.getValue()));}}
        else{Set<TileEntity> seen=new HashSet<TileEntity>();for(TileConduit source:members(c)){if(!source.ready())continue;for(int s=0;s<6;s++){if(!source.input(s))continue;Boundary b=boundary(source,s);if(b==null||!seen.add(b.tile))continue;
                    for(Key k:b.samples(source.cursor,true)){if(group==0?k.kind!=Key.ITEM:!k.isFluid())continue;long count=b.extract(k,Integer.MAX_VALUE,true);if(count>0)out.put(k,RelayMath.add(out.containsKey(k)?out.get(k):0,count));}if(out.size()>256)break;}}}
        return out;
    }
    public long offerEU(TileConduit from,int face,long voltage,long amps){if(busy||voltage<=0||voltage>(8L<<28)||amps<=0||!from.input(face))return 0;busy=true;
        long used=0,pending=0,max=0;TileCenter c=center(from);
        try{max=Math.min(amps,from.remaining(Key.EU_KEY,true));if(max<=0)return 0;
            if(c.storageMode){long eu=RelayMath.product(voltage,max);pending=max;long accepted=c.insert(Key.EU_KEY,eu,false);pending=0;used=accepted/voltage;
                if(used>0){from.spent(Key.EU_KEY,used,true);c.meter.moved(Key.EU_KEY,voltage*used,true,c.now());c.lastMovement=c.now();}return used;}
            int checks=0;for(TileConduit to:members(c)){if(++checks>ModConfig.routeComparisons)break;if(!same(from,to))continue;for(int s=0;s<6&&used<max;s++){if(!to.output(s))continue;TileEntity raw=to.endpoint(s);if(!(raw instanceof IEnergyConnected)||raw==from.endpoint(face))continue;
                    long safe=to.voltage(s);if(safe<voltage||(to.tiers[s]>=0&&safe!=voltage))continue;long offer=Math.min(max-used,Math.min(to.remaining(Key.EU_KEY,false),to.amperage(s)));
                    pending=offer;long accepted=Transfer.checked(((IEnergyConnected)raw).injectEnergyUnits(ForgeDirection.getOrientation(s).getOpposite(),voltage,offer),offer);pending=0;
                    if(accepted>0){used+=accepted;to.spent(Key.EU_KEY,accepted,false);c.meter.moved(Key.EU_KEY,voltage*accepted,false,c.now());}}}
            if(used>0){from.spent(Key.EU_KEY,used,true);c.meter.moved(Key.EU_KEY,voltage*used,true,c.now());c.lastMovement=c.now();}return used;
        }catch(RuntimeException ex){from.uncertain(Key.EU_KEY,RelayMath.product(voltage,pending),"EU injection");return Math.min(max,used+pending);}finally{busy=false;}}
    private void wirelessOutput(TileCenter c,TileConduit n,int side){TileEntity t=n.endpoint(side);if(!(t instanceof IEnergyConnected))return;long v=n.voltage(side);long amps=Math.min(n.amperage(side),n.remaining(Key.EU_KEY,false));if(v<=0||amps<=0)return;
        long have=c.extract(Key.EU_KEY,RelayMath.product(v,amps),true);long packets=RelayMath.packets(have,v,amps);if(packets<=0)return;long reserved=v*packets;
        if(c.extract(Key.EU_KEY,reserved,false)!=reserved)throw new IllegalStateException("Wireless EU reservation mismatch");
        long accepted;try{accepted=Transfer.checked(((IEnergyConnected)t).injectEnergyUnits(ForgeDirection.getOrientation(side).getOpposite(),v,packets),packets);}
        catch(RuntimeException ex){n.uncertain(Key.EU_KEY,reserved,"wireless-output");throw ex;}
        if(accepted<packets&&c.insert(Key.EU_KEY,(packets-accepted)*v,false)!=(packets-accepted)*v)throw new IllegalStateException("Wireless EU refund failed");
        if(accepted>0){n.spent(Key.EU_KEY,accepted,false);c.meter.moved(Key.EU_KEY,accepted*v,false,c.now());c.lastMovement=c.now();}}
}
