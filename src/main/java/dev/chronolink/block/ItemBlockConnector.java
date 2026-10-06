package dev.chronolink.block;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.util.StatCollector;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class ItemBlockConnector extends ItemBlock {
    public ItemBlockConnector(Block block) { super(block); }
    @Override public boolean placeBlockAt(ItemStack stack,EntityPlayer player,World world,int x,int y,int z,int side,float hx,float hy,float hz,int meta) {
        if(stack.hasTagCompound() && stack.getTagCompound().hasKey("ChronoLinkData",10)) {
            if(player.capabilities.isCreativeMode) return false;
            String owner=stack.getTagCompound().getCompoundTag("ChronoLinkData").getString("Owner");
            if(!player.getUniqueID().toString().equals(owner)) return false;
        }
        return super.placeBlockAt(stack,player,world,x,y,z,side,hx,hy,hz,meta);
    }
    @SideOnly(Side.CLIENT) @Override public void addInformation(ItemStack stack,EntityPlayer player,List lines,boolean advanced) {
        lines.add(StatCollector.translateToLocal("chronolink.tip.place"));
        if(stack.hasTagCompound() && stack.getTagCompound().hasKey("ChronoLinkData",10))
            lines.add(StatCollector.translateToLocal("chronolink.tip.saved"));
    }
}
