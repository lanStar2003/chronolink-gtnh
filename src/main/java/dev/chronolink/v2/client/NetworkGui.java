package dev.chronolink.v2.client;

import java.util.Arrays;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.lwjgl.opengl.GL11;
import dev.chronolink.ModConfig;
import net.minecraft.client.Minecraft;
import dev.chronolink.core.NodeSettings;
import dev.chronolink.v2.gui.NetworkContainer;
import dev.chronolink.v2.net.Packets;
import dev.chronolink.v2.tile.TileCenter;

/** Single compact native screen: input / real storage / output. */
public final class NetworkGui extends GuiContainer {
    private final NetworkContainer c;private final boolean center;private GuiTextField name;
    private static final String[] FACES={"下","上","北","南","西","东"},MODES={"关闭","输入","输出"},KINDS={"物品","液体","气体","EU · GT团队","RF"};
    public NetworkGui(NetworkContainer c){super(c);this.c=c;center=c.tile instanceof TileCenter;xSize=314;ySize=232;}
    private static final class FlatButton extends GuiButton {
        FlatButton(int id,int x,int y,int width,String text){super(id,x,y,width,20,text);}
        @Override public void drawButton(Minecraft mc,int mx,int my){if(!visible)return;boolean over=mx>=xPosition&&my>=yPosition&&mx<xPosition+width&&my<yPosition+height;
            drawRect(xPosition,yPosition,xPosition+width,yPosition+height,!enabled?0xff15202c:over?0xff24485e:0xff1b3245);
            drawRect(xPosition,yPosition+height-1,xPosition+width,yPosition+height,0xff388aab);
            drawCenteredString(mc.fontRenderer,displayString,xPosition+width/2,yPosition+(height-8)/2,enabled?0xffe6f4f6:0xff71818d);}
    }
    private NBTTagCompound d(){return c.display;}
    private void button(int id,int x,int y,int width,String title){buttonList.add(new FlatButton(id,guiLeft+x,guiTop+y,width,title));}
    @Override public void initGui(){super.initGui();buttonList.clear();
        button(0,252,7,50,"启停");
        if(center){name=new GuiTextField(fontRendererObj,guiLeft+84,guiTop+8,122,16);name.setMaxStringLength(32);name.setText(d().getString("Network"));button(15,210,7,36,"命名");button(1,12,29,110,"");button(12,128,29,86,"资源总览");button(10,224,29,35,"<");button(11,267,29,35,">");}
        else{button(2,12,27,290,"");for(int s=0;s<6;s++)button(100+s,12+s*49,49,45,FACES[s]);button(3,12,71,92,"");button(4,110,71,92,"全设同向");button(14,208,71,94,"返回配置");
            button(6,12,93,140,"选择物品");button(7,162,93,140,"选择介质");button(5,12,115,46,"自动");button(8,62,115,20,"-");button(9,132,115,20,"+");button(13,162,115,140,"");
            button(10,12,49,38,"<");button(11,56,49,38,">");for(int i=0;i<6;i++)button(200+i,12,72+i*11,290,"");}
        labels();}
    private void labels(){int tab=d().getInteger("Tab"),face=d().getInteger("Face");int[] modes=d().getIntArray("Modes");
        for(Object raw:buttonList){GuiButton b=(GuiButton)raw;b.enabled=true;if(b.id==0){b.displayString=d().getBoolean("Enabled")?"暂停":"启用";b.enabled=d().getString("Fault").isEmpty();}
            if(center){if(b.id==1)b.displayString=d().getBoolean("Storage")?"存储网络":"局域直连";if(b.id==12)b.displayString=tab==0?"资源总览":tab==1?"物品目录":"流体 / 气体";if(b.id==10||b.id==11)b.visible=tab>0;}
            else{if(b.id==2)b.displayString="网络："+trim(d().getString("Network"),240);
                if(b.id>=100&&b.id<106){b.visible=tab==0;int s=b.id-100;b.displayString=(s==face?"[":"")+FACES[s]+(s<modes.length?MODES[modes[s]].substring(0,1):"")+(s==face?"]":"");}
                if(b.id==3)b.displayString="本面："+(face<modes.length?MODES[modes[face]]:"输入");
                if(b.id>=3&&b.id<=9||b.id==13)b.visible=tab==0;
                if(b.id==14)b.visible=tab>0;if(b.id==10||b.id==11)b.visible=tab>0;
                if(b.id==6)b.displayString="物品："+trim(d().getString("Item"),93);if(b.id==7)b.displayString="介质："+trim(d().getString("Fluid"),93);
                if(b.id==13)b.displayString="安培："+(d().getInteger("Amps")==0?"自动 "+d().getLong("RatedAmps"):d().getInteger("Amps"))+" A";
                if(b.id>=200){int row=b.id-200;NBTTagList rows=d().getTagList("Rows",10);b.visible=tab>0&&row<rows.tagCount();b.height=11;if(b.visible){NBTTagCompound r=rows.getCompoundTagAt(row);b.displayString=trim(r.getString("Label"),190)+"   "+num(r.getLong("Count"));}}
            }
        }}
    @Override public void updateScreen(){super.updateScreen();if(name!=null){name.updateCursorCounter();if(!name.isFocused()&&!d().getString("Network").isEmpty())name.setText(d().getString("Network"));}labels();}
    private void send(int id,String text){Packets.channel.sendToServer(new Packets.Action(c.windowId,id,d().getInteger("Revision"),text));}
    @Override protected void actionPerformed(GuiButton b){if(b.enabled)send(b.id,b.id==15&&name!=null?name.getText():"");}
    @Override protected void mouseClicked(int x,int y,int button){if(name!=null)name.mouseClicked(x,y,button);
        if(!center&&d().getInteger("Tab")==0&&button==1&&y>=guiTop+93&&y<guiTop+111){if(x>=guiLeft+12&&x<guiLeft+152){send(16,"");return;}if(x>=guiLeft+162&&x<guiLeft+302){send(17,"");return;}}
        super.mouseClicked(x,y,button);}
    @Override protected void keyTyped(char ch,int key){if(name!=null&&name.isFocused()&&key!=1){if(key==28){send(15,name.getText());name.setFocused(false);}else name.textboxKeyTyped(ch,key);return;}super.keyTyped(ch,key);}
    @Override public void handleMouseInput(){super.handleMouseInput();int wheel=org.lwjgl.input.Mouse.getEventDWheel();if(wheel!=0&&d().getInteger("Tab")>0)send(wheel>0?10:11,"");}
    private void rect(int x,int y,int w,int h,int color){drawRect(guiLeft+x,guiTop+y,guiLeft+x+w,guiTop+y+h,color);}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int mouseX,int mouseY){rect(-1,-1,xSize+2,ySize+2,0xff28677b);rect(0,0,xSize,ySize,0xff101c27);rect(0,0,xSize,2,0xff5de0ec);rect(8,139,298,1,0xff294c5b);
        if(center){for(int i=0;i<3;i++)slot(13+i*34,162);for(int r=0;r<6;r++)rect(11,64+r*12,292,11,r%2==0?0xff162b39:0xff122330);}
        int x=center?140:76;for(int r=0;r<3;r++)for(int col=0;col<9;col++)slot(x-1+col*18,147+r*18);for(int col=0;col<9;col++)slot(x-1+col*18,205);
        if(center&&name!=null)name.drawTextBox();}
    private void slot(int x,int y){rect(x,y,18,18,0xff345363);rect(x+1,y+1,16,16,0xff142431);}
    private void text(String s,int x,int y,int color){fontRendererObj.drawString(s,x,y,color);}
    private String trim(String s,int pixels){return fontRendererObj.trimStringToWidth(s==null?"":s,pixels);}
    @Override protected void drawGuiContainerForegroundLayer(int mx,int my){text(center?"时枢中心":"时枢连接器",12,12,0xd9f4f6);int tab=d().getInteger("Tab");
        if(center){text("资源",14,53,0x96bdcd);text("输入 /s",132,53,0x75dbd5);text(d().getBoolean("Storage")?"存储":"直连无库存",192,53,0xd7c48d);text("输出 /s",264,53,0xeba97b);
            if(tab==0){for(int k=0;k<5;k++){int y=66+k*12;text(KINDS[k],14,y,0xc7e5ee);text(num(d().getLong("In"+k)),132,y,0x79dfd5);text(numString(d().getString("Stored"+k)),192,y,0xe6d99a);text(num(d().getLong("Out"+k)),264,y,0xeac194);}}
            else{NBTTagList list=d().getTagList("Rows",10);for(int i=0;i<list.tagCount();i++){NBTTagCompound row=list.getCompoundTagAt(i);int y=66+i*12;text(trim(row.getString("Label"),111),14,y,0xc7e5ee);text(num(row.getLong("In")),132,y,0x79dfd5);text(num(row.getLong("Count")),192,y,0xe6d99a);text(num(row.getLong("Out")),264,y,0xeac194);}}
            text("容量卡",12,148,0xb6d8e4);text("物品  介质  RF",12,184,0x96adb8);text("节点："+d().getInteger("Nodes"),12,197,0x96adb8);
            text(d().getBoolean("AEAttached")?(d().getBoolean("AEActive")?"AE 已连接":"AE 离线 / 权限受限"):"AE 未连接",12,210,d().getBoolean("AEActive")?0x77dfba:0xb2a887);
            orbit(68,15,6);
        }else{if(tab==0){int tier=d().getInteger("Tier");text(tier<0?"AUTO":NodeSettings.TIER_NAMES[Math.min(14,tier)],86,120,0x79dce6);
                text(trim(d().getLong("Voltage")+" V  "+rating(d().getString("Rating")),285),12,136,0x97bdcb);}
            else text((tab==1?"选择要输出的物品":"选择要输出的流体 / 气体")+"   "+(d().getInteger("Page")+1)+"/"+d().getInteger("Pages"),106,55,0xbbdfeb);
            text("六面自动",12,156,0x83bccf);text("多资源并行",12,171,0x83bccf);text(d().getBoolean("Storage")?"存储网络":"局域直连",12,187,0xb8cb9d);text(d().getBoolean("Online")?"中心在线":"中心离线",12,204,d().getBoolean("Online")?0x7cd5b3:0xe0a075);}
        if(!d().getString("Fault").isEmpty())text(trim("已隔离："+d().getString("Fault"),290),12,226,0xff9085);
    }
    private static String rating(String r){return "hatch".equals(r)?"舱室额定值":"cable-minimum".equals(r)?"线缆及已知末端最小值":"unverified-cable".equals(r)?"线路未完全确认（自动输出关闭）":"no-consumer".equals(r)?"未发现用电端":"未识别 EU 输入端";}
    private void orbit(int x,int y,int radius){double t=ModConfig.particles?c.tile.now()*.04:0;GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_CURRENT_BIT);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glColor4f(.3F,.85F,.93F,1);GL11.glBegin(GL11.GL_LINE_LOOP);for(int i=0;i<24;i++){double a=i*Math.PI/12+t;GL11.glVertex3d(x+Math.cos(a)*radius,y+Math.sin(a)*radius*.6,0);}GL11.glEnd();GL11.glPopAttrib();}
    private static String numString(String s){try{return num(Long.parseLong(s));}catch(NumberFormatException ex){if(s.length()>16)return s.substring(0,1)+"."+s.substring(1,3)+"e"+(s.length()-1);return s;}}
    private static String num(long n){if(n>=1000000000000L)return String.format(java.util.Locale.ROOT,"%.1fT",n/1e12);if(n>=1000000000L)return String.format(java.util.Locale.ROOT,"%.1fG",n/1e9);if(n>=1000000L)return String.format(java.util.Locale.ROOT,"%.1fM",n/1e6);if(n>=1000L)return String.format(java.util.Locale.ROOT,"%.1fk",n/1e3);return Long.toString(n);}
    @Override public void drawScreen(int x,int y,float partial){super.drawScreen(x,y,partial);
        if(!center&&x>=guiLeft+12&&x<guiLeft+302&&d().getInteger("Tab")==0&&y>=guiTop+93&&y<guiTop+112)drawHoveringText(Arrays.asList("左键：网络目录；光标有样品时复制过滤条件","右键：清除。本面物品、介质、EU、RF同时工作。","存储网络输出必须选择物品/介质；局域模式可不筛选。"),x,y,fontRendererObj);
        if(!center&&x>=guiLeft+12&&x<guiLeft+302&&y>=guiTop+115&&y<guiTop+135)drawHoveringText(Arrays.asList("自动：读取舱室；线缆保守检查已加载末端","手动调整后需要重新启用；不超过已知额定值","局域 EU 保留输入电压包，不变压、不存电","存储模式从 GT 原生无线 EU 账户取电并按目标输出"),x,y,fontRendererObj);
    }
}
