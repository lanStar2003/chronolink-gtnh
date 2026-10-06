package dev.chronolink;

import cpw.mods.fml.common.network.IGuiHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.tileentity.TileEntity;
import dev.chronolink.gui.ContainerConnector;
import dev.chronolink.tile.TileConnector;

/** Dedicated server must never load a GuiScreen class. */
public class CommonProxy implements IGuiHandler {
    @Override public Object getServerGuiElement(int id,EntityPlayer player,World world,int x,int y,int z) {
        if(!world.blockExists(x,y,z)) return null;
        if(id==1||id==2){TileEntity t=world.getTileEntity(x,y,z);
            if(t instanceof dev.chronolink.v2.tile.OwnedTile && ((dev.chronolink.v2.tile.OwnedTile)t).mayEdit(player))
                return new dev.chronolink.v2.gui.NetworkContainer(player.inventory,(dev.chronolink.v2.tile.OwnedTile)t);
            return null;}
        if(id!=0)return null;
        TileEntity tile=world.getTileEntity(x,y,z);
        return tile instanceof TileConnector && ((TileConnector)tile).mayEdit(player)
            ? new ContainerConnector(player.inventory,(TileConnector)tile) : null;
    }
    public void initV2() {}
    public void receiveV2Snapshot(int window,net.minecraft.nbt.NBTTagCompound data) {}
    @Override public Object getClientGuiElement(int id,EntityPlayer player,World world,int x,int y,int z) { return null; }
}
