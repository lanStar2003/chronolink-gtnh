package dev.chronolink.v2.compat;

import java.util.LinkedHashMap;
import java.util.Map;
import appeng.api.config.Actionable;
import appeng.api.config.SecurityPermissions;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEFluidStack;
import appeng.me.GridAccessException;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEFluidStack;
import dev.chronolink.v2.tile.TileCenter;
import dev.chronolink.v2.store.Key;
import dev.chronolink.v2.core.Transfer;

/** Uses the center's REAL channel-bearing AE node and existing AE cells, not a mirrored inventory. */
public final class AEBridge implements Transfer.Store<Key> {
    private final TileCenter center;
    public AEBridge(TileCenter c){center=c;}
    @Override public Object identity(){return center;}
    public boolean attached(){return center.getProxy().getNode()!=null&&!center.getProxy().getNode().getConnectedSides().isEmpty();}
    public boolean allowed(boolean input){try{return !center.aeBlocked&&center.getProxy().isActive()&&center.getProxy().getNode()!=null&&
            center.getProxy().getSecurity().hasPermission(center.getProxy().getNode().getPlayerID(),input?SecurityPermissions.INJECT:SecurityPermissions.EXTRACT);
        }catch(GridAccessException e){return false;}}
    @Override public long insert(Key key,long n,boolean simulate){if(n<=0||!allowed(true))return 0;Actionable mode=simulate?Actionable.SIMULATE:Actionable.MODULATE;
        try{if(key.kind==Key.ITEM){IAEItemStack q=AEItemStack.create(key.item);q.setStackSize(n);IAEItemStack left=center.getProxy().getStorage().getItemInventory().injectItems(q,mode,new MachineSource(center));return Transfer.checked(n-(left==null?0:left.getStackSize()),n);}
            if(key.isFluid()){IAEFluidStack q=AEFluidStack.create(key.fluid);q.setStackSize(n);IAEFluidStack left=center.getProxy().getStorage().getFluidInventory().injectItems(q,mode,new MachineSource(center));return Transfer.checked(n-(left==null?0:left.getStackSize()),n);}
        }catch(GridAccessException e){return 0;}return 0;}
    @Override public long extract(Key key,long n,boolean simulate){if(n<=0||!allowed(false))return 0;Actionable mode=simulate?Actionable.SIMULATE:Actionable.MODULATE;
        try{if(key.kind==Key.ITEM){IAEItemStack q=AEItemStack.create(key.item);q.setStackSize(n);IAEItemStack got=center.getProxy().getStorage().getItemInventory().extractItems(q,mode,new MachineSource(center));return Transfer.checked(got==null?0:got.getStackSize(),n);}
            if(key.isFluid()){IAEFluidStack q=AEFluidStack.create(key.fluid);q.setStackSize(n);IAEFluidStack got=center.getProxy().getStorage().getFluidInventory().extractItems(q,mode,new MachineSource(center));return Transfer.checked(got==null?0:got.getStackSize(),n);}
        }catch(GridAccessException e){return 0;}return 0;}
    /** A bounded display/catalogue view. The authoritative amount remains in AE. */
    public Map<Key,Long> sample(int offset,int limit,int kind){Map<Key,Long> out=new LinkedHashMap<Key,Long>();if(!allowed(false))return out;
        try{int skip=0;if(kind==0){for(IAEItemStack s:center.getProxy().getStorage().getItemInventory().getStorageList()){if(s.getStackSize()<=0)continue;if(skip++<offset)continue;Key k=Key.of(s.getItemStack());if(k!=null)out.put(k,s.getStackSize());if(out.size()>=limit)break;}}
            else {for(IAEFluidStack s:center.getProxy().getStorage().getFluidInventory().getStorageList()){if(s.getStackSize()<=0)continue;if(skip++<offset)continue;Key k=Key.of(s.getFluidStack());if(k!=null)out.put(k,s.getStackSize());if(out.size()>=limit)break;}}
        }catch(GridAccessException ignored){}return out;}
    public long[] totals(){long[] n=new long[5];if(!allowed(false))return n;try{
        for(IAEItemStack s:center.getProxy().getStorage().getItemInventory().getStorageList())n[0]=dev.chronolink.v2.core.RelayMath.add(n[0],Math.max(0,s.getStackSize()));
        for(IAEFluidStack s:center.getProxy().getStorage().getFluidInventory().getStorageList()){Key k=Key.of(s.getFluidStack());if(k!=null)n[k.kind]=dev.chronolink.v2.core.RelayMath.add(n[k.kind],Math.max(0,s.getStackSize()));}
        }catch(GridAccessException ignored){}return n;}
}
