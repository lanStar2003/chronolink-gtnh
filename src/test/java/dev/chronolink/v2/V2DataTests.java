package dev.chronolink.v2;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import dev.chronolink.v2.store.*;

/** Real Minecraft/Forge value objects in an isolated JVM, not a launched world. */
public final class V2DataTests {
    private static int checks;
    private static void check(boolean b,String name){if(!b)throw new AssertionError(name);checks++;}
    public static void main(String[] args){Bootstrap.func_151354_b();
        Key iron=Key.of(new ItemStack(Items.iron_ingot,64));Stock stock=new Stock();
        check(stock.insert(iron,400,65536,16,true)==400&&stock.entries.isEmpty(),"simulate-card-insert");
        check(stock.insert(iron,400,65536,16,false)==400,"real-item-card");
        ItemStack tagged=new ItemStack(Items.iron_ingot,1);tagged.setTagCompound(new NBTTagCompound());tagged.stackTagCompound.setString("test","NBT must survive");Key special=Key.of(tagged);
        check(!iron.equals(special),"NBT-distinguishes-item-identity");check(stock.insert(special,12,65536,16,false)==12,"tagged-item-stored");
        Fluid gas=new Fluid("chronolink_test_gas").setGaseous(true);FluidRegistry.registerFluid(gas);
        Key water=Key.of(new FluidStack(FluidRegistry.WATER,1)),steam=Key.of(new FluidStack(gas,1));
        check(water.kind==Key.LIQUID&&steam.kind==Key.GAS,"simultaneous-fluid-and-gas-classification");
        check(stock.insert(water,1000,16000000,16,false)==1000&&stock.insert(steam,2000,16000000,16,false)==2000,"two-media-same-card");
        check(stock.total(1)==3000,"gas-liquid-share-capacity-not-duplicate-ledgers");
        check(stock.insert(Key.EU_KEY,32,1000,16,false)==0,"no-private-EU-store");
        check(stock.insert(Key.RF_KEY,100000,1000000000L,8,false)==100000,"RF-separate-card");
        Stock loaded=new Stock();loaded.read(stock.write());check(loaded.count(iron)==400&&loaded.count(special)==12&&loaded.count(water)==1000&&loaded.count(steam)==2000&&loaded.count(Key.RF_KEY)==100000,"save-load-all-resources");
        check(loaded.extract(special,5,true)==5&&loaded.count(special)==12,"simulate-extraction-no-mutation");
        check(loaded.extract(special,99,false)==12&&loaded.count(special)==0,"actual-extraction-capped");
        check(Key.read(water.write()).equals(water)&&Key.read(special.write()).equals(special),"filter-NBT-round-trip");
        Meter meter=new Meter();meter.moved(iron,10,true,100);meter.moved(water,30,false,100);check(meter.rate(0,0)==10&&meter.rate(1,1)==30,"meter-real-directions");meter.advance(120);check(meter.rate(0,0)==0,"idle-meter-expires");
        System.out.println("PASS V2 real Minecraft/Forge data contracts: "+checks+" checks. No AE grid or world initialized.");
    }
}
