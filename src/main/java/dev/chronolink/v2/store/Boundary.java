package dev.chronolink.v2.store;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;
import cofh.api.energy.IEnergyProvider;
import cofh.api.energy.IEnergyReceiver;
import dev.chronolink.v2.core.Transfer;

/** External native boundary, including sided rules. No direct access to a machine's private fields. */
public final class Boundary implements Transfer.Store<Key> {
    public final TileEntity tile;
    public final ForgeDirection side;
    public Boundary(TileEntity tile,ForgeDirection side){this.tile=tile;this.side=side;}
    @Override public Object identity(){return tile;}
    private int[] slots(){IInventory inventory=(IInventory)tile;int size=Math.min(65536,Math.max(0,inventory.getSizeInventory()));
        if(inventory instanceof ISidedInventory){int[] raw=((ISidedInventory)inventory).getAccessibleSlotsFromSide(side.ordinal());
            if(raw==null)return new int[0];java.util.LinkedHashSet<Integer> unique=new java.util.LinkedHashSet<Integer>();
            for(int j=0;j<Math.min(raw.length,65536);j++)if(raw[j]>=0&&raw[j]<size)unique.add(raw[j]);
            int[] slots=new int[unique.size()];int i=0;for(int slot:unique)slots[i++]=slot;return slots;}
        int[] slots=new int[size];for(int i=0;i<size;i++)slots[i]=i;return slots;}
    private boolean valid(int slot){return slot>=0&&slot<((IInventory)tile).getSizeInventory();}
    private boolean canIn(int s,ItemStack st){IInventory i=(IInventory)tile;return valid(s)&&i.isItemValidForSlot(s,st)&&(!(i instanceof ISidedInventory)||((ISidedInventory)i).canInsertItem(s,st,side.ordinal()));}
    private boolean canOut(int s,ItemStack st){return valid(s)&&(!(tile instanceof ISidedInventory)||((ISidedInventory)tile).canExtractItem(s,st,side.ordinal()));}
    public List<Key> samples(int cursor,boolean includeItems){List<Key> keys=new ArrayList<Key>();
        if(includeItems&&tile instanceof IInventory){int[] slots=slots();if(slots!=null&&slots.length>0){int count=Math.min(64,slots.length);
            for(int j=0;j<count;j++){int s=slots[(Math.floorMod(cursor,slots.length)+j)%slots.length];if(!valid(s))continue;ItemStack st=((IInventory)tile).getStackInSlot(s);
                Key k=Key.of(st);if(st!=null&&st.stackSize>0&&canOut(s,st)&&k!=null&&!keys.contains(k))keys.add(k);if(keys.size()>=8)break;}}}
        if(tile instanceof IFluidHandler){FluidTankInfo[] tanks=((IFluidHandler)tile).getTankInfo(side);if(tanks!=null)for(int j=0;j<Math.min(64,tanks.length);j++){
            FluidTankInfo t=tanks[(Math.floorMod(cursor,tanks.length)+j)%tanks.length];if(t==null||t.fluid==null||t.fluid.amount<=0)continue;Key k=Key.of(t.fluid);if(k!=null&&!keys.contains(k))keys.add(k);}}
        if(tile instanceof IEnergyProvider && ((IEnergyProvider)tile).canConnectEnergy(side))keys.add(Key.RF_KEY);
        return keys;
    }
    @Override public long insert(Key k,long count,boolean simulate){if(count<=0)return 0;int max=(int)Math.min(Integer.MAX_VALUE,count);
        if(k.kind==Key.ITEM&&tile instanceof IInventory){IInventory inv=(IInventory)tile;int n=Math.min(max,k.item.getMaxStackSize()),left=n;int[] slots=slots();if(slots==null)return 0;
            for(int s:slots){if(!canIn(s,k.item))continue;ItemStack old=inv.getStackInSlot(s);if(old!=null&&!k.equals(Key.of(old)))continue;
                int limit=Math.min(inv.getInventoryStackLimit(),k.item.getMaxStackSize());int accepted=Math.min(left,Math.max(0,limit-(old==null?0:old.stackSize)));if(accepted<=0)continue;
                if(!simulate){int previous=old==null?0:old.stackSize;inv.setInventorySlotContents(s,k.item(previous+accepted));ItemStack after=inv.getStackInSlot(s);
                    if(after==null||!k.equals(Key.of(after))||after.stackSize!=previous+accepted)throw new IllegalStateException("Inventory rejected/mutated an accepted write");inv.markDirty();}
                left-=accepted;if(left==0)break;}return n-left;}
        if(k.isFluid()&&tile instanceof IFluidHandler)return Transfer.checked(((IFluidHandler)tile).fill(side,k.fluid(max),!simulate),max);
        if(k.kind==Key.RF&&tile instanceof IEnergyReceiver)return Transfer.checked(((IEnergyReceiver)tile).receiveEnergy(side,max,simulate),max);
        return 0;
    }
    @Override public long extract(Key k,long count,boolean simulate){if(count<=0)return 0;int max=(int)Math.min(Integer.MAX_VALUE,count);
        if(k.kind==Key.ITEM&&tile instanceof IInventory){IInventory inv=(IInventory)tile;int n=Math.min(max,k.item.getMaxStackSize()),left=n;int[] slots=slots();if(slots==null)return 0;
            for(int s:slots){if(!valid(s))continue;ItemStack st=inv.getStackInSlot(s);if(st==null||st.stackSize<=0||!k.equals(Key.of(st))||!canOut(s,st))continue;
                int take=Math.min(left,st.stackSize);if(!simulate){ItemStack got=inv.decrStackSize(s,take);if(got==null)continue;if(!k.equals(Key.of(got))||got.stackSize<0||got.stackSize>take)throw new IllegalStateException("Inventory extraction violated request");take=got.stackSize;inv.markDirty();}
                left-=take;if(left==0)break;}return n-left;}
        if(k.isFluid()&&tile instanceof IFluidHandler){FluidStack got=((IFluidHandler)tile).drain(side,k.fluid(max),!simulate);if(got==null)return 0;
            if(!k.equals(Key.of(got)))throw new IllegalStateException("Fluid handler returned a different fluid");return Transfer.checked(got.amount,max);}
        if(k.kind==Key.RF&&tile instanceof IEnergyProvider)return Transfer.checked(((IEnergyProvider)tile).extractEnergy(side,max,simulate),max);
        return 0;
    }
}
