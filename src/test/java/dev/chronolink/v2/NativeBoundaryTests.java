package dev.chronolink.v2;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.*;
import dev.chronolink.v2.core.Transfer;
import dev.chronolink.v2.store.*;

/** Production adapters against genuine vanilla/Forge containers in an isolated JVM. */
public final class NativeBoundaryTests {
    private static int checks;
    private static void check(boolean b,String name){if(!b)throw new AssertionError(name);checks++;}
    public static void run(){
        TileEntityChest source=new TileEntityChest(),dest=new TileEntityChest();
        ItemStack sample=new ItemStack(Items.iron_ingot,64);sample.setTagCompound(new NBTTagCompound());sample.stackTagCompound.setString("serial","must-survive");
        Key iron=Key.of(sample);source.setInventorySlotContents(0,sample);
        Boundary from=new Boundary(source,ForgeDirection.UP),to=new Boundary(dest,ForgeDirection.DOWN);
        Transfer.Rescue<Key> rescue=new Transfer.Rescue<Key>(){public void retain(Key k,long n){throw new AssertionError("unexpected remainder "+n);}public void uncertain(Key k,long n,String p){throw new AssertionError("unexpected uncertain transaction "+p);}};
        check(Transfer.move(from,to,iron,17,rescue)==17,"actual chest transfer");
        check(source.getStackInSlot(0).stackSize==47&&dest.getStackInSlot(0).stackSize==17,"actual source and destination quantities");
        check(iron.equals(Key.of(dest.getStackInSlot(0))),"actual native item NBT retained");
        check(from.extract(iron,10,true)==10&&source.getStackInSlot(0).stackSize==47,"native inventory simulation does not remove");
        for(int i=0;i<dest.getSizeInventory();i++)dest.setInventorySlotContents(i,new ItemStack(Items.gold_ingot,64));
        check(Transfer.move(from,to,iron,17,rescue)==0&&source.getStackInSlot(0).stackSize==47,"full real chest does not extract source");
        check(Transfer.move(from,new Boundary(source,ForgeDirection.NORTH),iron,17,rescue)==0,"same inventory across two faces not transferred");
        TileEntityFurnace furnace=new TileEntityFurnace();furnace.setInventorySlotContents(2,new ItemStack(Items.iron_ingot,4));
        check(new Boundary(furnace,ForgeDirection.UP).extract(Key.of(new ItemStack(Items.iron_ingot)),4,true)==0,"furnace top cannot extract output");
        check(new Boundary(furnace,ForgeDirection.DOWN).extract(Key.of(new ItemStack(Items.iron_ingot)),4,false)==4,"furnace bottom can extract output");
        TileFluidHandler a=new TileFluidHandler(),b=new TileFluidHandler();a.fill(ForgeDirection.UP,new FluidStack(FluidRegistry.WATER,600),true);b.fill(ForgeDirection.DOWN,new FluidStack(FluidRegistry.WATER,900),true);
        Boundary fa=new Boundary(a,ForgeDirection.UP),fb=new Boundary(b,ForgeDirection.DOWN);Key water=Key.of(new FluidStack(FluidRegistry.WATER,1));
        check(Transfer.move(fa,fb,water,400,rescue)==100,"actual Forge tanks partial capacity");
        check(a.drain(ForgeDirection.UP,1000,false).amount==500&&b.drain(ForgeDirection.DOWN,1000,false).amount==1000,"actual Forge tank conservation");
        check(Transfer.move(fa,fb,water,400,rescue)==0&&a.drain(ForgeDirection.UP,1000,false).amount==500,"full tank never drains source");
        System.out.println("PASS actual vanilla chest/furnace and Forge tank adapter contracts: "+checks+" checks. No world or modpack launched.");
    }
}
