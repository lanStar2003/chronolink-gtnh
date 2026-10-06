package dev.chronolink.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.util.StatCollector;
import dev.chronolink.core.NodeSettings;
import dev.chronolink.core.ResourceKind;
import dev.chronolink.gui.ContainerConnector;
import dev.chronolink.tile.TileConnector;

/** One native game screen. No tabs, topology editor, webview, or external font dependency. */
public final class GuiConnector extends GuiContainer {
    private final ContainerConnector container;
    private final TileConnector tile;
    public GuiConnector(ContainerConnector container) { super(container);this.container=container;tile=container.tile;xSize=230;ySize=238; }
    private String tr(String key) {return StatCollector.translateToLocal("chronolink."+key);}
    @Override public void initGui() {
        super.initGui();buttonList.clear();
        buttonList.add(new FlatButton(0,guiLeft+12,guiTop+44,20,"-"));
        buttonList.add(new FlatButton(1,guiLeft+88,guiTop+44,20,"+"));
        buttonList.add(new FlatButton(2,guiLeft+116,guiTop+44,102,""));
        buttonList.add(new FlatButton(3,guiLeft+12,guiTop+68,96,""));
        buttonList.add(new FlatButton(4,guiLeft+116,guiTop+68,102,""));
        buttonList.add(new FlatButton(5,guiLeft+12,guiTop+92,96,""));
        buttonList.add(new FlatButton(6,guiLeft+12,guiTop+116,20,"-"));
        buttonList.add(new FlatButton(7,guiLeft+88,guiTop+116,20,"+"));
        buttonList.add(new FlatButton(8,guiLeft+116,guiTop+116,102,""));
        labels();
    }
    private void labels() {
        for(Object raw:buttonList) {
            GuiButton b=(GuiButton)raw;
            b.enabled=!container.clientFault;
            if(container.clientHasPayload && b.id>=0 && b.id<=3)b.enabled=false;
            if(b.id>=6)b.visible=tile.settings.resource==ResourceKind.EU;
            switch(b.id) {
                case 2:b.displayString=tr(tile.settings.importing?"import":"export");break;
                case 3:b.displayString=tr("kind."+tile.settings.resource.name().toLowerCase(java.util.Locale.ROOT));break;
                case 4:b.displayString=tr("face")+": "+tr("face."+tile.settings.face);break;
                case 5:b.displayString=tr(tile.settings.enabled?"on":"off");break;
                case 8:b.displayString=tile.settings.amps+" A";break;
                default:break;
            }
        }
    }
    @Override public void updateScreen() {super.updateScreen();labels();}
    @Override protected void actionPerformed(GuiButton button) {
        if(button.enabled)mc.playerController.sendEnchantPacket(inventorySlots.windowId,button.id);
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int mouseX,int mouseY) {
        drawRect(guiLeft-1,guiTop-1,guiLeft+xSize+1,guiTop+ySize+1,0xff286680);
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff0c1824);
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+2,0xff4fd7e5);
        drawRect(guiLeft+12,guiTop+37,guiLeft+xSize-12,guiTop+38,0xff244051);
        drawRect(guiLeft+122,guiTop+92,guiLeft+142,guiTop+112,0xff34576a);
        drawRect(guiLeft+123,guiTop+93,guiLeft+141,guiTop+111,0xff102333);
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)slot(33+col*18,153+row*18);
        for(int col=0;col<9;col++)slot(33+col*18,211);
    }
    private void slot(int x,int y) {
        drawRect(guiLeft+x,guiTop+y,guiLeft+x+18,guiTop+y+18,0xff294255);
        drawRect(guiLeft+x+1,guiTop+y+1,guiLeft+x+17,guiTop+y+17,0xff142330);
    }
    @Override protected void drawGuiContainerForegroundLayer(int mouseX,int mouseY) {
        fontRendererObj.drawString(tr("title"),12,10,0xd7f1f5);
        fontRendererObj.drawString(tr("private"),12,25,0x819aab);
        String number=String.format(java.util.Locale.ROOT,"%02d",tile.settings.channel);
        fontRendererObj.drawString(tr("channel")+" "+number,37,50,0xd7f1f5);
        fontRendererObj.drawString(tr("filter"),148,98,0xa4bdcc);
        if(tile.settings.resource==ResourceKind.EU) {
            String tier=NodeSettings.TIER_NAMES[tile.settings.tier];
            fontRendererObj.drawString(tier,45,122,0x76dce7);
        } else fontRendererObj.drawString(tr(tile.settings.resource==ResourceKind.FLUID?"fluidFilterHint":"simpleHint"),12,120,0x819aab);
        String status=container.clientFault?tr("status.7"):tr("status."+tile.status);
        String amount=format(tile.displayAmount);
        fontRendererObj.drawString(tr("buffer")+": "+amount+"  "+status,12,141,container.clientFault?0xff9c92:0x95b7c7);
    }
    private String format(long n) {
        if(n>=1000000000000L)return (n/1000000000L)+"G";
        if(n>=1000000000L)return (n/1000000L)+"M";
        if(n>=1000000L)return (n/1000L)+"k";
        return Long.toString(n);
    }
    @Override public void drawScreen(int mouseX,int mouseY,float partial) {
        super.drawScreen(mouseX,mouseY,partial);
        if(tile.settings.resource==ResourceKind.EU && mouseY>=guiTop+116 && mouseY<guiTop+136 && mouseX>=guiLeft+12 && mouseX<guiLeft+108)
            drawHoveringText(java.util.Arrays.asList(tile.settings.voltage()+" EU / packet",tr("voltageWarning")),mouseX,mouseY,fontRendererObj);
        else if(mouseX>=guiLeft+122 && mouseX<guiLeft+143 && mouseY>=guiTop+92 && mouseY<guiTop+112)
            drawHoveringText(java.util.Arrays.asList(tr("ghostHint")),mouseX,mouseY,fontRendererObj);
    }
}
