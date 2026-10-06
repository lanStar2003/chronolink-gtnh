package dev.chronolink.v2.block;

import java.util.ArrayList;
import java.util.List;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import dev.chronolink.ChronoLink;
import dev.chronolink.v2.tile.*;
import dev.chronolink.v2.core.ConduitGeometry;

public final class NetworkBlock extends BlockContainer {
    public final boolean center;
    @SideOnly(Side.CLIENT) private IIcon shell,port,flow;
    public NetworkBlock(boolean center){super(Material.iron);this.center=center;setBlockName("chronolink."+(center?"center":"conduit"));setCreativeTab(CreativeTabs.tabRedstone);setHardness(1.5F);setResistance(6000000F);setStepSound(soundTypeMetal);setHarvestLevel("pickaxe",1);}
    @Override public TileEntity createNewTileEntity(World w,int meta){return center?new TileCenter():new TileConduit();}
    @Override public int getRenderType(){return ChronoLink.networkRenderId;}
    @Override public boolean isOpaqueCube(){return false;}
    @Override public boolean renderAsNormalBlock(){return false;}
    private List<double[]> boxes(IBlockAccess w,int x,int y,int z){if(center){List<double[]> b=new ArrayList<double[]>();b.add(new double[]{.125,0,.125,.875,.1875,.875});b.add(new double[]{.25,.1875,.25,.75,.875,.75});return b;}
        TileEntity t=w.getTileEntity(x,y,z);return ConduitGeometry.boxes(t instanceof TileConduit?((TileConduit)t).mask:0);}
    private void box(double[] b){setBlockBounds((float)b[0],(float)b[1],(float)b[2],(float)b[3],(float)b[4],(float)b[5]);}
    @SuppressWarnings("unchecked") @Override public void addCollisionBoxesToList(World w,int x,int y,int z,AxisAlignedBB area,List list,Entity e){for(double[] b:boxes(w,x,y,z)){box(b);super.addCollisionBoxesToList(w,x,y,z,area,list,e);}setBlockBounds(0,0,0,1,1,1);}
    @Override public void setBlockBoundsBasedOnState(IBlockAccess w,int x,int y,int z){double[] union={1,1,1,0,0,0};for(double[] b:boxes(w,x,y,z)){for(int i=0;i<3;i++)union[i]=Math.min(union[i],b[i]);for(int i=3;i<6;i++)union[i]=Math.max(union[i],b[i]);}box(union);}
    @Override public MovingObjectPosition collisionRayTrace(World w,int x,int y,int z,Vec3 start,Vec3 end){MovingObjectPosition best=null;double distance=Double.MAX_VALUE;
        // Calling Block.collisionRayTrace here would reset bounds to the union, making empty corners clickable.
        for(double[] b:boxes(w,x,y,z)){AxisAlignedBB a=AxisAlignedBB.getBoundingBox(x+b[0],y+b[1],z+b[2],x+b[3],y+b[4],z+b[5]);MovingObjectPosition hit=a.calculateIntercept(start,end);
            if(hit!=null){double d=start.squareDistanceTo(hit.hitVec);if(d<distance){distance=d;best=new MovingObjectPosition(x,y,z,hit.sideHit,hit.hitVec);}}}return best;}
    @Override public void onBlockPlacedBy(World w,int x,int y,int z,EntityLivingBase p,ItemStack s){if(!w.isRemote&&p instanceof EntityPlayer&&w.getTileEntity(x,y,z) instanceof OwnedTile)((OwnedTile)w.getTileEntity(x,y,z)).placed((EntityPlayer)p,s);}
    @Override public void onNeighborBlockChange(World w,int x,int y,int z,Block b){TileEntity t=w.getTileEntity(x,y,z);if(!w.isRemote&&t instanceof TileConduit)((TileConduit)t).scan();}
    @Override public boolean onBlockActivated(World w,int x,int y,int z,EntityPlayer p,int side,float hx,float hy,float hz){if(!w.isRemote&&w.getTileEntity(x,y,z) instanceof OwnedTile){OwnedTile t=(OwnedTile)w.getTileEntity(x,y,z);if(t.mayEdit(p))p.openGui(ChronoLink.instance,center?2:1,w,x,y,z);else p.addChatMessage(new ChatComponentTranslation("chronolink.v2.private"));}return true;}
    @Override public boolean removedByPlayer(World w,EntityPlayer p,int x,int y,int z,boolean harvest){TileEntity raw=w.getTileEntity(x,y,z);if(raw instanceof OwnedTile){OwnedTile t=(OwnedTile)raw;if(!w.isRemote&&!t.mayEdit(p))return false;if(p.capabilities.isCreativeMode&&t.hasContents())return false;}
        return harvest||super.removedByPlayer(w,p,x,y,z,false);}
    @Override public void harvestBlock(World w,EntityPlayer p,int x,int y,int z,int meta){super.harvestBlock(w,p,x,y,z,meta);w.setBlockToAir(x,y,z);}
    @Override public ArrayList<ItemStack> getDrops(World w,int x,int y,int z,int meta,int fortune){ArrayList<ItemStack> list=new ArrayList<ItemStack>();ItemStack s=new ItemStack(this);TileEntity raw=w.getTileEntity(x,y,z);if(raw instanceof OwnedTile)((OwnedTile)raw).pack(s);list.add(s);return list;}
    @Override public boolean canSilkHarvest(World w,EntityPlayer p,int x,int y,int z,int m){return false;}
    @Override public ItemStack getPickBlock(MovingObjectPosition hit,World w,int x,int y,int z,EntityPlayer p){return new ItemStack(this);}
    @SideOnly(Side.CLIENT) @Override public void registerBlockIcons(IIconRegister reg){shell=reg.registerIcon("chronolink:connector_shell");port=reg.registerIcon("chronolink:connector_idle");flow=reg.registerIcon("chronolink:connector_flow");}
    @SideOnly(Side.CLIENT) @Override public IIcon getIcon(int side,int meta){return side==1?port:shell;}
    @SideOnly(Side.CLIENT) @Override public IIcon getIcon(IBlockAccess w,int x,int y,int z,int side){TileEntity t=w.getTileEntity(x,y,z);return t instanceof OwnedTile&&((OwnedTile)t).flow()?flow:port;}
}
