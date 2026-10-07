package dev.chronolink.v2.client;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import org.lwjgl.opengl.GL11;
import dev.chronolink.v2.core.SideConfiguration;

/** Original fixed-function cube widget. All six faces are directly accessible without rotating. */
public final class SideConfigurationPanel extends Gui {
    private static final String[] FACE={"下","上","北","南","西","东"};
    private static final String[] MODE={"关","入","出"};
    private static final int[] COLORS={0xff263441,0xff196e80,0xff925126};
    public void draw(FontRenderer font,int x,int y,int[] modes,int selected,int hovered,int mask){
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_CURRENT_BIT|GL11.GL_LINE_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glLineWidth(1);
        for(int face:SideConfiguration.order()){
            int mode=face<modes.length&&SideConfiguration.validMode(modes[face])?modes[face]:0;
            int[][] p=SideConfiguration.polygon(face);color(COLORS[mode]);GL11.glBegin(GL11.GL_QUADS);
            for(int[] point:p)GL11.glVertex3d(x+point[0],y+point[1],0);GL11.glEnd();
            color(face==selected?0xffffdc84:face==hovered?0xfff0ffff:0xff527589);
            GL11.glBegin(GL11.GL_LINE_LOOP);for(int[] point:p)GL11.glVertex3d(x+point[0],y+point[1],0);GL11.glEnd();
        }
        GL11.glPopAttrib();
        for(int face:SideConfiguration.order()){
            int[] at=SideConfiguration.center(face);int mode=face<modes.length&&SideConfiguration.validMode(modes[face])?modes[face]:0;
            String text=FACE[face]+"·"+MODE[mode];font.drawString(text,x+at[0]-font.getStringWidth(text)/2,y+at[1]-4,0xe8f6fa);
            if((mask&(1<<face))!=0)drawRect(x+at[0]-1,y+at[1]+6,x+at[0]+2,y+at[1]+8,0xff8decc6);
        }
    }
    private static void color(int rgb){GL11.glColor4f(((rgb>>>16)&255)/255F,((rgb>>>8)&255)/255F,(rgb&255)/255F,1);}
}
