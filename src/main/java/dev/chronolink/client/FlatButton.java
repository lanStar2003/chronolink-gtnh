package dev.chronolink.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

final class FlatButton extends GuiButton {
    FlatButton(int id,int x,int y,int width,String text) {super(id,x,y,width,20,text);}
    @Override public void drawButton(Minecraft mc,int mouseX,int mouseY) {
        if(!visible)return;
        boolean hover=mouseX>=xPosition && mouseY>=yPosition && mouseX<xPosition+width && mouseY<yPosition+height;
        int fill=!enabled?0xff15202c:hover?0xff24485e:0xff1b3245;
        drawRect(xPosition,yPosition,xPosition+width,yPosition+height,fill);
        drawRect(xPosition,yPosition+height-1,xPosition+width,yPosition+height,!enabled?0xff253345:0xff388aab);
        drawCenteredString(mc.fontRenderer,displayString,xPosition+width/2,yPosition+6,enabled?0xffe6f4f6:0xff71818d);
    }
}
