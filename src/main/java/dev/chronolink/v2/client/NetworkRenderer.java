package dev.chronolink.v2.client;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.IBlockAccess;
import org.lwjgl.opengl.GL11;
import dev.chronolink.ChronoLink;
import dev.chronolink.v2.block.NetworkBlock;
import dev.chronolink.v2.core.ConduitGeometry;
import dev.chronolink.v2.tile.TileConduit;

/** Standard block tessellation; no full cube, shared geometry with collision/ray selection. */
public final class NetworkRenderer implements ISimpleBlockRenderingHandler {
    @Override public boolean renderWorldBlock(IBlockAccess w,int x,int y,int z,Block block,int model,RenderBlocks r){
        if(((NetworkBlock)block).center){r.setRenderBounds(.125,0,.125,.875,.1875,.875);r.renderStandardBlock(block,x,y,z);r.setRenderBounds(.4375,.1875,.4375,.5625,.375,.5625);r.renderStandardBlock(block,x,y,z);}
        else{int mask=w.getTileEntity(x,y,z) instanceof TileConduit?((TileConduit)w.getTileEntity(x,y,z)).mask:0;for(double[] b:ConduitGeometry.boxes(mask)){r.setRenderBounds(b[0],b[1],b[2],b[3],b[4],b[5]);r.renderStandardBlock(block,x,y,z);}}
        r.setRenderBounds(0,0,0,1,1,1);return true;}
    @Override public void renderInventoryBlock(Block b,int meta,int model,RenderBlocks r){GL11.glPushMatrix();GL11.glTranslatef(-.5F,-.5F,-.5F);r.setRenderBounds(.3125,.3125,.3125,.6875,.6875,.6875);
        Tessellator t=Tessellator.instance;t.startDrawingQuads();t.setNormal(0,-1,0);r.renderFaceYNeg(b,0,0,0,b.getIcon(0,meta));t.setNormal(0,1,0);r.renderFaceYPos(b,0,0,0,b.getIcon(1,meta));t.setNormal(0,0,-1);r.renderFaceZNeg(b,0,0,0,b.getIcon(2,meta));t.setNormal(0,0,1);r.renderFaceZPos(b,0,0,0,b.getIcon(3,meta));t.setNormal(-1,0,0);r.renderFaceXNeg(b,0,0,0,b.getIcon(4,meta));t.setNormal(1,0,0);r.renderFaceXPos(b,0,0,0,b.getIcon(5,meta));t.draw();r.setRenderBounds(0,0,0,1,1,1);GL11.glPopMatrix();}
    @Override public boolean shouldRender3DInInventory(int id){return true;}
    @Override public int getRenderId(){return ChronoLink.networkRenderId;}
}
