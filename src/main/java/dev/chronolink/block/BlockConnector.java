package dev.chronolink.block;

import java.util.ArrayList;
import java.util.Random;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraft.world.IBlockAccess;
import dev.chronolink.ChronoLink;
import dev.chronolink.ModConfig;
import dev.chronolink.tile.TileConnector;

public final class BlockConnector extends BlockContainer {
    @SideOnly(Side.CLIENT) private IIcon shell,idle,flow;
    public BlockConnector() {
        super(Material.iron);
        setBlockName("chronolink.connector"); setCreativeTab(CreativeTabs.tabRedstone);
        setHardness(2.0F); setResistance(6000000F); setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe",1);
    }
    @Override public int getRenderType() { return 0; }
    @Override public TileEntity createNewTileEntity(World world,int metadata) { return new TileConnector(); }
    @Override public int onBlockPlaced(World world,int x,int y,int z,int side,float hx,float hy,float hz,int meta) {return side^1;}
    @Override public void onBlockPlacedBy(World world,int x,int y,int z,EntityLivingBase placer,ItemStack stack) {
        if(world.isRemote || !(placer instanceof EntityPlayer)) return;
        TileEntity raw=world.getTileEntity(x,y,z);
        if(!(raw instanceof TileConnector)) return;
        TileConnector tile=(TileConnector)raw;
        if(stack.hasTagCompound() && stack.getTagCompound().hasKey("ChronoLinkData",10))
            tile.readPortable(stack.getTagCompound().getCompoundTag("ChronoLinkData"));
        else tile.owner=((EntityPlayer)placer).getUniqueID();
        tile.settings.face=world.getBlockMetadata(x,y,z)%6;
        tile.settings.enabled=false;tile.changed();
    }
    @Override public boolean onBlockActivated(World world,int x,int y,int z,EntityPlayer player,int side,float hx,float hy,float hz) {
        if(player.getHeldItem()!=null && player.getHeldItem().getItem()==ChronoLink.binder) return false;
        if(!world.isRemote) {
            TileEntity raw=world.getTileEntity(x,y,z);
            if(raw instanceof TileConnector && ((TileConnector)raw).mayEdit(player)) {
                TileConnector old=(TileConnector)raw;
                if(player.isSneaking()&&!old.hasPayload()) {
                    world.setBlock(x,y,z,ChronoLink.conduit,0,3);
                    TileEntity replacement=world.getTileEntity(x,y,z);
                    if(replacement instanceof dev.chronolink.v2.tile.TileConduit) {
                        dev.chronolink.v2.tile.TileConduit node=(dev.chronolink.v2.tile.TileConduit)replacement;
                        node.owner=old.owner;node.enabled=false;
                        java.util.Arrays.fill(node.modes,old.settings.importing?1:2);
                        dev.chronolink.v2.Networks.INSTANCE.register(node);
                        dev.chronolink.v2.Networks.INSTANCE.autoBind(node);node.scan();node.changed();
                    }
                } else player.openGui(ChronoLink.instance,0,world,x,y,z);
            }
        }
        return true;
    }
    @Override public boolean removedByPlayer(World world,EntityPlayer player,int x,int y,int z,boolean willHarvest) {
        TileEntity raw=world.getTileEntity(x,y,z);
        if(raw instanceof TileConnector) {
            TileConnector tile=(TileConnector)raw;
            if(!world.isRemote && !tile.mayEdit(player)) return false;
            if(player.capabilities.isCreativeMode && tile.hasPayload()) return false;
        }
        if(willHarvest) return true;
        return super.removedByPlayer(world,player,x,y,z,false);
    }
    @Override public void harvestBlock(World world,EntityPlayer player,int x,int y,int z,int meta) {
        super.harvestBlock(world,player,x,y,z,meta);world.setBlockToAir(x,y,z);
    }
    @Override public ArrayList<ItemStack> getDrops(World world,int x,int y,int z,int meta,int fortune) {
        ArrayList<ItemStack> drops=new ArrayList<ItemStack>();ItemStack stack=new ItemStack(this);
        TileEntity raw=world.getTileEntity(x,y,z);if(raw instanceof TileConnector) ((TileConnector)raw).writeIntoItem(stack);
        drops.add(stack); return drops;
    }
    @Override public boolean canSilkHarvest(World world,EntityPlayer player,int x,int y,int z,int metadata) { return false; }
    @Override public ItemStack getPickBlock(MovingObjectPosition target,World world,int x,int y,int z,EntityPlayer player) {return new ItemStack(this);}
    @SideOnly(Side.CLIENT) @Override public void registerBlockIcons(IIconRegister register) {
        shell=register.registerIcon("chronolink:connector_shell");idle=register.registerIcon("chronolink:connector_idle");flow=register.registerIcon("chronolink:connector_flow");
    }
    @SideOnly(Side.CLIENT) @Override public IIcon getIcon(int side,int meta) { return side==(meta%6)?idle:shell; }
    @SideOnly(Side.CLIENT) @Override public IIcon getIcon(IBlockAccess world,int x,int y,int z,int side) {
        TileEntity raw=world.getTileEntity(x,y,z);if(raw instanceof TileConnector) {TileConnector tile=(TileConnector)raw;if(side==tile.settings.face) return tile.clientFlow?flow:idle;}return shell;
    }
    @SideOnly(Side.CLIENT) @Override public void randomDisplayTick(World world,int x,int y,int z,Random random) {
        if(!ModConfig.particles || random.nextInt(4)!=0) return;TileEntity raw=world.getTileEntity(x,y,z);
        if(raw instanceof TileConnector && ((TileConnector)raw).clientFlow)world.spawnParticle("reddust",x+0.5,y+1.02,z+0.5,0.10,0.75,0.95);
    }
}
