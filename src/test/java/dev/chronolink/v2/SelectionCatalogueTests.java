package dev.chronolink.v2;

import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.*;
import dev.chronolink.v2.store.*;
import dev.chronolink.v2.tile.TileConduit;

/** Production selection, catalogue and conduit save data; real MC/Forge value objects, no world. */
public final class SelectionCatalogueTests {
    private static int checks;
    private static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
    public static void run(){
        Key iron=Key.of(new ItemStack(Items.iron_ingot)),gold=Key.of(new ItemStack(Items.gold_ingot));
        Fluid gas=new Fluid("chronolink_selection_gas").setGaseous(true);FluidRegistry.registerFluid(gas);
        Key water=Key.of(new FluidStack(FluidRegistry.WATER,1)),steam=Key.of(new FluidStack(gas,1));
        OutputSelection items=new OutputSelection(true),media=new OutputSelection(false);
        check(items.toggle(iron)&&items.toggle(gold)&&items.size()==2,"two item targets on one face");
        check(!items.toggle(water)&&items.size()==2,"wrong resource class does not mutate filters");
        check(media.toggle(water)&&media.toggle(steam)&&media.size()==2,"liquid and gas selected simultaneously");
        check(items.toggle(iron)&&!items.contains(iron),"second click deselects");
        check(items.replace(iron)&&items.size()==1&&!items.contains(gold),"cursor sample replaces selection explicitly");
        items.clear();List<Key> variants=new ArrayList<Key>();
        for(int i=0;i<10;i++){ItemStack stack=new ItemStack(Items.iron_ingot);stack.setTagCompound(new NBTTagCompound());stack.stackTagCompound.setInteger("variant",i);variants.add(Key.of(stack));}
        for(int i=0;i<9;i++)check(items.toggle(variants.get(i)),"selection allows nine NBT-distinct keys");
        check(!items.toggle(variants.get(9))&&items.size()==9,"tenth key rejected without losing previous nine");
        OutputSelection loaded=new OutputSelection(true);loaded.read(items.write());check(loaded.keys().equals(items.keys()),"multi-target NBT round trip");
        NBTTagList bad=new NBTTagList();bad.appendTag(water.write());
        try{loaded.read(bad);throw new AssertionError("invalid filter accepted");}catch(IllegalArgumentException expected){check(loaded.keys().equals(items.keys()),"bad save cannot partially replace selection");}
        TileConduit conduit=new TileConduit();conduit.itemFilters[0].replace(iron);conduit.itemFilters[0].toggle(gold);conduit.fluidFilters[0].replace(water);conduit.fluidFilters[0].toggle(steam);
        NBTTagCompound saved=new NBTTagCompound();conduit.writeToNBT(saved);TileConduit restored=new TileConduit();restored.readFromNBT(saved);
        check(restored.configured(0,true).equals(Arrays.asList(iron,gold))&&restored.configured(0,false).equals(Arrays.asList(water,steam)),"real conduit multi-selection save/load");
        check(restored.allows(water,0)&&restored.allows(steam,0)&&restored.allows(Key.EU_KEY,0)&&restored.allows(Key.RF_KEY,0),"native energy unaffected by simultaneous media filters");
        NBTTagCompound legacy=new NBTTagCompound(),side=new NBTTagCompound();side.setTag("Item",iron.write());side.setTag("Fluid",water.write());NBTTagList sides=new NBTTagList();sides.appendTag(side);legacy.setTag("Filters",sides);
        TileConduit upgraded=new TileConduit();upgraded.readFromNBT(legacy);check(upgraded.configured(0,true).equals(Arrays.asList(iron))&&upgraded.configured(0,false).equals(Arrays.asList(water)),"alpha1 single filters migrate without resetting data");
        conduit.itemFilters[1].clear();for(int i=0;i<5;i++)conduit.itemFilters[1].toggle(variants.get(i));Set<Key> firsts=new HashSet<Key>();
        for(int i=0;i<5;i++){conduit.cursor=i*5;firsts.add(conduit.outputKeys(1,true).get(0));}
        check(firsts.size()==5,"five-tick item cadence cannot starve five selected resources");
        conduit.cursor=0;Key first=conduit.outputKeys(0,false).get(0);conduit.cursor=1;check(!first.equals(conduit.outputKeys(0,false).get(0)),"liquid gas first choice rotates");
        Map<Key,Long> stock=new LinkedHashMap<Key,Long>();stock.put(gold,8L);stock.put(iron,12L);
        CatalogueView.Page page=CatalogueView.page(stock,Collections.<Key>emptyList(),Collections.<Key>emptyList(),0,"MINECRAFT:IRON",0);
        check(page.total==1&&page.rows.get(0).key.equals(iron),"registry ID search is case insensitive");
        CatalogueView.Page empty=CatalogueView.page(stock,Collections.<Key>emptyList(),Collections.<Key>emptyList(),0,"not-present",500);
        check(empty.pages==1&&empty.index==0&&empty.rows.isEmpty(),"empty search pages are safe");
        CatalogueView.Page recent=CatalogueView.page(Collections.<Key,Long>emptyMap(),Arrays.asList(iron,water),Arrays.asList(gold),0,"",0);
        check(recent.total==2&&recent.rows.get(0).count==0&&recent.rows.get(1).count==0,"zero stock preserves active flows and selected keys without inventing inventory");
        Map<Key,Long> many=new LinkedHashMap<Key,Long>();for(Key key:variants)many.put(key,1L);
        CatalogueView.Page second=CatalogueView.page(many,Collections.<Key>emptyList(),Collections.<Key>emptyList(),0,"",99);
        check(second.pages==2&&second.index==1&&second.rows.size()==4,"nine-plus types searchable across clamped pages");
        List<Key> oldOrder=new ArrayList<Key>();for(CatalogueView.Row row:second.rows)oldOrder.add(row.key);
        List<Key> reversed=new ArrayList<Key>(variants);Collections.reverse(reversed);many.clear();for(Key key:reversed)many.put(key,100L);
        CatalogueView.Page changed=CatalogueView.page(many,Collections.<Key>emptyList(),Collections.<Key>emptyList(),0,"",1);
        for(int i=0;i<oldOrder.size();i++)check(oldOrder.get(i).equals(changed.rows.get(i).key),"stock amount and provider order cannot reorder identical page");
        check(CatalogueView.cleanQuery("  abc\n§  ").equals("abc"),"search text control characters removed");
        System.out.println("PASS network selection, multi-resource output and searchable catalogue: "+checks+" native-data checks. No world launched.");
    }
}
