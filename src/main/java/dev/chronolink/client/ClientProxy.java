package dev.chronolink.client;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import dev.chronolink.CommonProxy;
import dev.chronolink.gui.ContainerConnector;
import dev.chronolink.tile.TileConnector;

public final class ClientProxy extends CommonProxy {
    private final java.util.concurrent.ConcurrentLinkedQueue<dev.chronolink.v2.net.Packets.Snapshot> snapshots=new java.util.concurrent.ConcurrentLinkedQueue<dev.chronolink.v2.net.Packets.Snapshot>();
    @Override public void initV2(){
        dev.chronolink.ChronoLink.networkRenderId=cpw.mods.fml.client.registry.RenderingRegistry.getNextAvailableRenderId();
        cpw.mods.fml.client.registry.RenderingRegistry.registerBlockHandler(new dev.chronolink.v2.client.NetworkRenderer());
        cpw.mods.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(dev.chronolink.v2.tile.TileCenter.class,new dev.chronolink.v2.client.CenterRenderer());
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(this);
    }
    @Override public void receiveV2Snapshot(int window,net.minecraft.nbt.NBTTagCompound data){if(snapshots.size()<32)snapshots.add(new dev.chronolink.v2.net.Packets.Snapshot(window,data));}
    @cpw.mods.fml.common.eventhandler.SubscribeEvent public void clientTick(cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent event){
        if(event.phase!=cpw.mods.fml.common.gameevent.TickEvent.Phase.END)return;
        net.minecraft.client.Minecraft mc=net.minecraft.client.Minecraft.getMinecraft();
        dev.chronolink.v2.net.Packets.Snapshot s;while((s=snapshots.poll())!=null){
            if(mc.thePlayer!=null&&mc.thePlayer.openContainer instanceof dev.chronolink.v2.gui.NetworkContainer&&mc.thePlayer.openContainer.windowId==s.window)
                ((dev.chronolink.v2.gui.NetworkContainer)mc.thePlayer.openContainer).display=s.data;
        }
    }
    @Override public Object getClientGuiElement(int id,EntityPlayer player,World world,int x,int y,int z) {
        if(id==1||id==2){TileEntity t=world.getTileEntity(x,y,z);return t instanceof dev.chronolink.v2.tile.OwnedTile?
            new dev.chronolink.v2.client.NetworkGui(new dev.chronolink.v2.gui.NetworkContainer(player.inventory,(dev.chronolink.v2.tile.OwnedTile)t)):null;}
        if(id!=0)return null;
        TileEntity raw=world.getTileEntity(x,y,z);
        return raw instanceof TileConnector?new GuiConnector(new ContainerConnector(player.inventory,(TileConnector)raw)):null;
    }
}
