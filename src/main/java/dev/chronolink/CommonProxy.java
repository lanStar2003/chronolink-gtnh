package dev.chronolink;

import cpw.mods.fml.common.network.IGuiHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.tileentity.TileEntity;
import dev.chronolink.gui.ContainerConnector;
import dev.chronolink.tile.TileConnector;

/** No client imports: dedicated server must never load a GuiScreen class. */
public class CommonProxy implements IGuiHandler {
    @Override public Object getServerGuiElement(int id,EntityPlayer player,World world,int x,int y,int z) {
        if(id!=0 || !world.blockExists(x,y,z)) return null;
        TileEntity tile=world.getTileEntity(x,y,z);
        return tile instanceof TileConnector && ((TileConnector)tile).mayEdit(player)
            ? new ContainerConnector(player.inventory,(TileConnector)tile) : null;
    }
    @Override public Object getClientGuiElement(int id,EntityPlayer player,World world,int x,int y,int z) { return null; }
}
