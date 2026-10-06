package dev.chronolink.item;

import java.util.List;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dev.chronolink.tile.TileConnector;

/** Right-click samples a channel; sneak-right-click applies it to your own empty node. */
public final class ItemBinder extends Item {
    public ItemBinder() {
        setUnlocalizedName("chronolink.binder"); setTextureName("chronolink:binder");
        setMaxStackSize(1); setCreativeTab(CreativeTabs.tabRedstone);
    }
    @Override public boolean onItemUse(ItemStack stack,EntityPlayer player,World world,int x,int y,int z,int side,float hx,float hy,float hz) {
        TileEntity raw=world.getTileEntity(x,y,z);
        if(!(raw instanceof TileConnector)) return false;
        if(world.isRemote) return true;
        TileConnector tile=(TileConnector)raw;
        if(!tile.mayEdit(player)) return true;
        if(!player.isSneaking()) {
            NBTTagCompound tag=new NBTTagCompound();
            tag.setString("Owner",player.getUniqueID().toString()); tag.setInteger("Channel",tile.settings.channel);
            stack.setTagCompound(tag);
            player.addChatMessage(new ChatComponentTranslation("chronolink.sampled",tile.settings.channel));
        } else if(stack.hasTagCompound() && player.getUniqueID().toString().equals(stack.getTagCompound().getString("Owner"))) {
            if(tile.hasPayload() || tile.hasFault()) {
                player.addChatMessage(new ChatComponentTranslation("chronolink.locked")); return true;
            }
            tile.settings.channel=Math.max(1,Math.min(64,stack.getTagCompound().getInteger("Channel")));
            tile.settings.enabled=false; tile.changed();
            player.addChatMessage(new ChatComponentTranslation("chronolink.bound",tile.settings.channel));
        }
        return true;
    }
    @SideOnly(Side.CLIENT) @Override public void addInformation(ItemStack stack,EntityPlayer player,List lines,boolean advanced) {
        lines.add(StatCollector.translateToLocal("chronolink.tip.binder"));
        if(stack.hasTagCompound()) lines.add(StatCollector.translateToLocal("chronolink.channel")+": "+stack.getTagCompound().getInteger("Channel"));
    }
}
