package dev.chronolink.client;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import dev.chronolink.CommonProxy;
import dev.chronolink.gui.ContainerConnector;
import dev.chronolink.tile.TileConnector;

public final class ClientProxy extends CommonProxy {
    @Override public Object getClientGuiElement(int id,EntityPlayer player,World world,int x,int y,int z) {
        if(id!=0)return null;
        TileEntity raw=world.getTileEntity(x,y,z);
        return raw instanceof TileConnector?new GuiConnector(new ContainerConnector(player.inventory,(TileConnector)raw)):null;
    }
}
