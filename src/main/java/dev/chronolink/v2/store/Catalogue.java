package dev.chronolink.v2.store;

import java.util.*;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.IFluidHandler;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.common.util.ForgeDirection;
import dev.chronolink.v2.Networks;
import dev.chronolink.v2.tile.TileCenter;
import dev.chronolink.v2.tile.TileConduit;
import dev.chronolink.v2.core.RelayMath;

/** Read-only catalogue: actual stored amounts, or unique accessible source slots in direct mode. */
public final class Catalogue {
    private Catalogue(){}
    private static void add(Map<Key,Long> out,Key key,long count){if(key!=null&&count>0)out.put(key,RelayMath.add(out.containsKey(key)?out.get(key):0,count));}
    public static Map<Key,Long> list(TileCenter c,int group){Map<Key,Long> out=new LinkedHashMap<Key,Long>();if(c==null||!c.active())return out;
        if(c.storageMode){for(Map.Entry<Key,Long> e:c.stock.entries.entrySet())if(group==0?e.getKey().kind==Key.ITEM:e.getKey().isFluid())add(out,e.getKey(),e.getValue());
            if(c.ae.attached())for(Map.Entry<Key,Long> e:c.ae.sample(0,Integer.MAX_VALUE,group).entrySet())add(out,e.getKey(),e.getValue());
            return out;
        }
        Map<TileEntity,Set<Integer>> seen=new IdentityHashMap<TileEntity,Set<Integer>>();
        for(TileConduit n:Networks.INSTANCE.members(c)){if(!n.ready())continue;for(int face=0;face<6;face++){
            if(!n.input(face))continue;TileEntity t=n.endpoint(face);if(t==null)continue;
            Set<Integer> used=seen.get(t);if(used==null){used=new HashSet<Integer>();seen.put(t,used);}ForgeDirection side=ForgeDirection.getOrientation(face).getOpposite();
            if(group==0&&t instanceof IInventory){IInventory inv=(IInventory)t;int[] slots;
                if(inv instanceof ISidedInventory)slots=((ISidedInventory)inv).getAccessibleSlotsFromSide(side.ordinal());
                else{slots=new int[Math.min(65536,Math.max(0,inv.getSizeInventory()))];for(int i=0;i<slots.length;i++)slots[i]=i;}
                if(slots==null)continue;
                for(int slot:slots){if(slot<0||slot>=inv.getSizeInventory()||used.contains(slot))continue;ItemStack st=inv.getStackInSlot(slot);Key k=Key.of(st);
                    if(k==null||st.stackSize<=0||!n.allows(k,face)||inv instanceof ISidedInventory&&!((ISidedInventory)inv).canExtractItem(slot,st,side.ordinal()))continue;
                    used.add(slot);add(out,k,st.stackSize);}
            }else if(group!=0&&t instanceof IFluidHandler){IFluidHandler fluid=(IFluidHandler)t;FluidTankInfo[] tanks=fluid.getTankInfo(side);if(tanks==null)continue;
                for(int i=0;i<tanks.length;i++){FluidTankInfo tank=tanks[i];if(used.contains(i)||tank==null||tank.fluid==null)continue;Key k=Key.of(tank.fluid);
                    if(k==null||!n.allows(k,face)||!fluid.canDrain(side,tank.fluid.getFluid()))continue;used.add(i);add(out,k,tank.fluid.amount);}
            }
        }}return out;
    }
}
