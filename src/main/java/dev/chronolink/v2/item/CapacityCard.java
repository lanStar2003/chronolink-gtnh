package dev.chronolink.v2.item;

import java.util.List;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StatCollector;

public final class CapacityCard extends Item {
    public CapacityCard(){setHasSubtypes(true);setUnlocalizedName("chronolink.capacity");setTextureName("chronolink:capacity_card");setCreativeTab(CreativeTabs.tabRedstone);}
    @Override public String getUnlocalizedName(ItemStack s){return "item.chronolink.capacity."+Math.max(0,Math.min(2,s.getItemDamage()));}
    @SuppressWarnings("unchecked") @SideOnly(Side.CLIENT) @Override public void getSubItems(Item item,CreativeTabs tab,List out){for(int i=0;i<3;i++)out.add(new ItemStack(this,1,i));}
    @Override public int getColorFromItemStack(ItemStack s,int pass){return s.getItemDamage()==0?0xffd978:s.getItemDamage()==1?0x77dfff:0xbc9cff;}
    @SuppressWarnings("unchecked") @SideOnly(Side.CLIENT) @Override public void addInformation(ItemStack s,EntityPlayer p,List list,boolean advanced){list.add(StatCollector.translateToLocal("chronolink.v2.card."+s.getItemDamage()));list.add(StatCollector.translateToLocal("chronolink.v2.cardHint"));}
}
