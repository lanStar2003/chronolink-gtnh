package dev.chronolink.v2.client;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.opengl.GL11;
import dev.chronolink.ModConfig;
import dev.chronolink.v2.tile.OwnedTile;

/** Small three-orbit core, inexpensive fixed-function rendering compatible with the target game. */
public final class CenterRenderer extends TileEntitySpecialRenderer {
    @Override public void renderTileEntityAt(TileEntity raw,double x,double y,double z,float partial){OwnedTile t=(OwnedTile)raw;double time=ModConfig.particles?(t.now()+partial):0;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_CURRENT_BIT|GL11.GL_LINE_BIT|GL11.GL_COLOR_BUFFER_BIT);GL11.glPushMatrix();
        GL11.glTranslated(x+.5,y+.53,z+.5);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_LIGHTING);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(.18F,.85F,.95F,t.enabled?.9F:.35F);GL11.glLineWidth(1.5F);
        for(int ring=0;ring<3;ring++){GL11.glPushMatrix();GL11.glRotated(ring*60+time*(ring==1?-.35:.35),1,ring%2,0);GL11.glBegin(GL11.GL_LINE_LOOP);
            for(int i=0;i<48;i++){double a=i*Math.PI/24;GL11.glVertex3d(Math.cos(a)*.32,0,Math.sin(a)*.32);}GL11.glEnd();GL11.glPopMatrix();}
        GL11.glRotatef((float)(time*.7),0,1,0);double r=t.flow()?.115:.085;GL11.glBegin(GL11.GL_LINES);
        double[][] p={{r,0,0},{-r,0,0},{0,r,0},{0,-r,0},{0,0,r},{0,0,-r}};for(int i=0;i<6;i++)for(int j=i+1;j<6;j++)if(i/2!=j/2){GL11.glVertex3d(p[i][0],p[i][1],p[i][2]);GL11.glVertex3d(p[j][0],p[j][1],p[j][2]);}GL11.glEnd();
        GL11.glPopMatrix();GL11.glPopAttrib();}
}
