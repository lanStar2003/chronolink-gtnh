package dev.chronolink.v2.item;

import java.util.List;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

public final class NetworkBlockItem extends ItemBlock {
    public NetworkBlockItem(Block b){super(b);}
    @Override public boolean placeBlockAt(ItemStack s,EntityPlayer player,World world,int x,int y,int z,int side,float hx,float hy,float hz,int metadata){
        if(s.hasTagCompound()&&s.getTagCompound().hasKey("NetworkData",10)){
            String owner=s.getTagCompound().getCompoundTag("NetworkData").getString("Owner");
            if(!player.getUniqueID().toString().equals(owner)){if(!world.isRemote)player.addChatMessage(new ChatComponentTranslation("chronolink.v2.private"));return false;}
        }
        return super.placeBlockAt(s,player,world,x,y,z,side,hx,hy,hz,metadata);
    }
    @SuppressWarnings("unchecked") @SideOnly(Side.CLIENT) @Override public void addInformation(ItemStack s,EntityPlayer p,List out,boolean advanced){
        if(s.hasTagCompound()&&s.getTagCompound().hasKey("NetworkData",10)){NBTTagCompound n=s.getTagCompound().getCompoundTag("NetworkData");out.add(StatCollector.translateToLocal("chronolink.v2.packed"));if(n.hasKey("Name"))out.add(n.getString("Name"));if(!n.getString("Fault").isEmpty())out.add(n.getString("Fault"));}
    }
}
