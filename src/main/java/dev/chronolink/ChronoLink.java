package dev.chronolink;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraft.init.Blocks;
import org.apache.logging.log4j.Logger;
import dev.chronolink.block.BlockConnector;
import dev.chronolink.block.ItemBlockConnector;
import dev.chronolink.item.ItemBinder;
import dev.chronolink.tile.TileConnector;
import dev.chronolink.transfer.NetworkHub;

@Mod(modid=ChronoLink.ID,name="ChronoLink",version=ChronoLink.VERSION,
    acceptedMinecraftVersions="[1.7.10]",
    dependencies="required-after:gregtech;required-after:gregtech_nh@[5.09.51.482];required-after:CoFHCore;required-after:appliedenergistics2")
public final class ChronoLink {
    public static final String ID="chronolink", VERSION="0.2.0-alpha.1";
    @Mod.Instance(ID) public static ChronoLink instance;
    @SidedProxy(clientSide="dev.chronolink.client.ClientProxy",serverSide="dev.chronolink.CommonProxy")
    public static CommonProxy proxy;
    public static Logger log;
    public static Block connector;
    public static Item binder;
    public static Block conduit,center;
    public static Item capacityCard;
    public static int networkRenderId=-1;
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        log=event.getModLog();
        ModConfig.load(event.getSuggestedConfigurationFile());
        connector=new BlockConnector(); binder=new ItemBinder();
        // Legacy IDs remain for saves, but no new binder or cubic connector recipes.
        connector.setCreativeTab(null);binder.setCreativeTab(null);
        conduit=new dev.chronolink.v2.block.NetworkBlock(false);
        center=new dev.chronolink.v2.block.NetworkBlock(true);
        capacityCard=new dev.chronolink.v2.item.CapacityCard();
        GameRegistry.registerBlock(conduit,dev.chronolink.v2.item.NetworkBlockItem.class,"conduit");
        GameRegistry.registerBlock(center,dev.chronolink.v2.item.NetworkBlockItem.class,"center");
        GameRegistry.registerItem(capacityCard,"capacity_card");
        GameRegistry.registerTileEntity(dev.chronolink.v2.tile.TileConduit.class,"chronolink:conduit_v2");
        GameRegistry.registerTileEntity(dev.chronolink.v2.tile.TileCenter.class,"chronolink:center_v2");
        dev.chronolink.v2.net.Packets.init();
        FMLCommonHandler.instance().bus().register(dev.chronolink.v2.Networks.INSTANCE);
        proxy.initV2();
        GameRegistry.registerBlock(connector,ItemBlockConnector.class,"connector");
        GameRegistry.registerItem(binder,"binder");
        GameRegistry.registerTileEntity(TileConnector.class,"chronolink:connector");
        NetworkRegistry.INSTANCE.registerGuiHandler(instance,proxy);
        FMLCommonHandler.instance().bus().register(NetworkHub.INSTANCE);
        log.info("ChronoLink {} targets GTNH 2.8.4 only. EU/RF conversion is disabled.",VERSION);
    }
    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(conduit,8),
            "IGI","CPC","IGI",'I',"ingotIron",'G',"blockGlass",'C',Blocks.cobblestone,'P',"plankWood"));
        GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(center),
            "GIG","ICI","PPP",'I',"ingotIron",'G',"blockGlass",'C',Blocks.chest,'P',"plankWood"));
        GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(capacityCard,1,0),
            "IGI","PCP","IGI",'I',"ingotIron",'G',"blockGlass",'C',Blocks.chest,'P',"plankWood"));
        GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(capacityCard,1,1),
            "IGI","GCG","IGI",'I',"ingotIron",'G',"blockGlass",'C',net.minecraft.init.Items.bucket));
        GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(capacityCard,1,2),
            "IGI","ICI","IGI",'I',"ingotIron",'G',"blockGlass",'C',Blocks.cobblestone));
    }
    @Mod.EventHandler public void stopped(FMLServerStoppedEvent event) { NetworkHub.INSTANCE.clear();dev.chronolink.v2.Networks.INSTANCE.clear();dev.chronolink.v2.net.Packets.clear(); }
}
