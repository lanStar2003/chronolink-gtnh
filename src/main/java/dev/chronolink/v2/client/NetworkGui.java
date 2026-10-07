package dev.chronolink.v2.client;

import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.lwjgl.opengl.GL11;
import dev.chronolink.ModConfig;
import dev.chronolink.core.NodeSettings;
import dev.chronolink.v2.core.RateDisplay;
import dev.chronolink.v2.core.SideConfiguration;
import dev.chronolink.v2.gui.NetworkContainer;
import dev.chronolink.v2.net.Packets;
import dev.chronolink.v2.tile.TileCenter;

/** Compact native screen with per-tick power and direct, absolute six-face editing. */
public final class NetworkGui extends GuiContainer {
    private final NetworkContainer c;
    private final boolean center;
    private final SideConfigurationPanel sides=new SideConfigurationPanel();
    private GuiTextField name,search;
    private int brush=-1,paintedMask;
    private static final String[] FACES={"下","上","北","南","西","东"};
    private static final String[] MODES={"关闭","输入","输出"};
    private static final String[] KINDS={"物品 [个/s]","液体 [mB/s]","气体 [mB/s]","能源 [EU/t]","能源 [RF/t]"};
    public NetworkGui(NetworkContainer c){super(c);this.c=c;center=c.tile instanceof TileCenter;xSize=314;ySize=236;}
    private static final class FlatButton extends GuiButton {
        FlatButton(int id,int x,int y,int width,String text){super(id,x,y,width,20,text);}
        @Override public void drawButton(Minecraft mc,int mx,int my){if(!visible)return;boolean over=mx>=xPosition&&my>=yPosition&&mx<xPosition+width&&my<yPosition+height;
            drawRect(xPosition,yPosition,xPosition+width,yPosition+height,!enabled?0xff15202c:over?0xff24485e:0xff1b3245);
            drawRect(xPosition,yPosition+height-1,xPosition+width,yPosition+height,0xff388aab);
            drawCenteredString(mc.fontRenderer,displayString,xPosition+width/2,yPosition+(height-8)/2,enabled?0xffe6f4f6:0xff71818d);}
    }
    private NBTTagCompound d(){return c.display;}
    private void button(int id,int x,int y,int width,int height,String title){GuiButton b=new FlatButton(id,guiLeft+x,guiTop+y,width,title);b.height=height;buttonList.add(b);}
    private int selected(){return Math.max(0,Math.min(5,d().getInteger("Face")));}
    private int mode(int face){int[] a=d().getIntArray("Modes");return face<a.length&&SideConfiguration.validMode(a[face])?a[face]:0;}
    @Override public void initGui(){super.initGui();buttonList.clear();
        button(0,252,7,50,20,"启停");
        if(center){
            name=new GuiTextField(fontRendererObj,guiLeft+84,guiTop+8,122,16);name.setMaxStringLength(32);name.setText(d().getString("Network"));
            button(15,210,7,36,20,"命名");button(1,12,29,110,20,"");button(12,128,29,86,20,"资源总览");button(10,224,29,35,20,"<");button(11,267,29,35,20,">");
        }else{
            button(2,12,27,290,20,"");button(14,252,7,50,20,"完成");
            button(6,12,63,180,20,"选择物品");button(7,12,85,180,20,"选择介质");
            button(5,12,108,42,18,"自动");button(8,58,108,18,18,"-");button(9,116,108,18,18,"+");button(13,140,108,52,18,"");
            button(10,224,49,35,20,"<");button(11,267,49,35,20,">");
            for(int i=0;i<6;i++)button(200+i,12,71+i*10,290,10,"");
            int[] tools={1,2,0,-1};for(int i=0;i<tools.length;i++)button(tools[i]<0?903:900+tools[i],246,153+i*18,56,16,"");
        }
        search=new GuiTextField(fontRendererObj,guiLeft+13,guiTop+(center?31:52),center?106:201,16);
        search.setMaxStringLength(64);search.setText(d().getString("Query"));labels();}
    private void labels(){int tab=d().getInteger("Tab");
        for(Object raw:buttonList){GuiButton b=(GuiButton)raw;b.enabled=true;
            if(b.id==0){b.visible=center||tab==0;b.displayString=d().getBoolean("Enabled")?"暂停":"启用";b.enabled=d().getString("Fault").isEmpty();}
            if(center){
                if(b.id==1){b.visible=tab==0;b.displayString=d().getBoolean("Storage")?"存储网络":"局域直连";}
                if(b.id==12)b.displayString=tab==0?"资源总览":tab==1?"物品目录":"流体 / 气体";
                if(b.id==10||b.id==11)b.visible=tab>0;
            }else{
                if(b.id==2)b.displayString="网络："+trim(d().getString("Network"),240);
                if(b.id>=5&&b.id<=9||b.id==13)b.visible=tab==0;
                if(b.id==14||b.id==10||b.id==11)b.visible=tab>0;
                if(b.id==6)b.displayString="物品："+trim(d().getString("Item"),132);
                if(b.id==7)b.displayString="介质："+trim(d().getString("Fluid"),132);
                if(b.id==5)b.displayString=d().getInteger("Tier")<0?"[自动]":"自动";
                if(b.id==13)b.displayString=(d().getInteger("Amps")==0?"自 "+d().getLong("RatedAmps"):d().getInteger("Amps"))+" A";
                if(b.id>=200&&b.id<206){int row=b.id-200;NBTTagList rows=d().getTagList("Rows",10);b.visible=tab>0&&row<rows.tagCount();
                    if(b.visible){NBTTagCompound r=rows.getCompoundTagAt(row);b.displayString=(r.getBoolean("Selected")?"[x] ":"[ ] ")+trim(r.getString("Label"),181)+"   "+amount(r.getLong("Count"));}}
                if(b.id>=900&&b.id<=903){b.visible=tab==0;int tool=b.id==903?-1:b.id-900;String title=tool<0?"循环切换":MODES[tool]+"画笔";b.displayString=tool==brush?"["+(tool<0?"循环":MODES[tool])+"]":title;}
            }
        }
    }
    @Override public void updateScreen(){super.updateScreen();
        if(name!=null){name.updateCursorCounter();if(!name.isFocused()&&!d().getString("Network").isEmpty())name.setText(d().getString("Network"));}
        if(search!=null){search.updateCursorCounter();if(!search.isFocused())search.setText(d().getString("Query"));}labels();}
    private void send(int id,String text){Packets.channel.sendToServer(new Packets.Action(c.windowId,id,d().getInteger("Revision"),text));}
    @Override protected void actionPerformed(GuiButton b){if(!b.enabled)return;
        if(b.id>=900&&b.id<=903){brush=b.id==903?-1:b.id-900;labels();return;}
        send(b.id,b.id==15&&name!=null?name.getText():"");}
    private int hitFace(int x,int y){return SideConfiguration.hit(x-guiLeft-SideConfiguration.ORIGIN_X,y-guiTop-SideConfiguration.ORIGIN_Y);}
    private void paintFace(int face,int mouseButton){
        if(isShiftKeyDown()||mouseButton==2){send(100+face,"");return;}
        if(mouseButton==1)send(SideConfiguration.cycleAction(face,true),"");
        else if(mouseButton==0){send(brush<0?SideConfiguration.cycleAction(face,false):SideConfiguration.action(face,brush),"");paintedMask|=1<<face;}
    }
    @Override protected void mouseClicked(int x,int y,int button){paintedMask=0;
        if(!center&&d().getInteger("Tab")==0){int face=hitFace(x,y);if(face>=0){paintFace(face,button);return;}}
        if(name!=null)name.mouseClicked(x,y,button);
        if(search!=null&&d().getInteger("Tab")>0){search.mouseClicked(x,y,button);if(search.isFocused()&&button==1){search.setText("");send(18,"");return;}}
        if(!center&&d().getInteger("Tab")==0&&button==1&&x>=guiLeft+12&&x<guiLeft+192){
            if(y>=guiTop+63&&y<guiTop+83){send(16,"");return;}
            if(y>=guiTop+85&&y<guiTop+105){send(17,"");return;}}
        super.mouseClicked(x,y,button);}
    @Override protected void mouseClickMove(int x,int y,int button,long held){
        if(!center&&d().getInteger("Tab")==0&&brush>=0&&button==0&&!isShiftKeyDown()){
            int face=hitFace(x,y);if(face>=0){if((paintedMask&(1<<face))==0)paintFace(face,0);return;}}
        super.mouseClickMove(x,y,button,held);}
    @Override protected void keyTyped(char ch,int key){
        if(search!=null&&search.isFocused()&&d().getInteger("Tab")>0&&key!=1){if(key==28){send(18,search.getText());search.setFocused(false);}else search.textboxKeyTyped(ch,key);return;}
        if(name!=null&&name.isFocused()&&key!=1){if(key==28){send(15,name.getText());name.setFocused(false);}else name.textboxKeyTyped(ch,key);return;}super.keyTyped(ch,key);}
    @Override public void handleMouseInput(){super.handleMouseInput();int wheel=org.lwjgl.input.Mouse.getEventDWheel();if(wheel!=0&&d().getInteger("Tab")>0)send(wheel>0?10:11,"");}
    private void rect(int x,int y,int w,int h,int color){drawRect(guiLeft+x,guiTop+y,guiLeft+x+w,guiTop+y+h,color);}
    private void slot(int x,int y){rect(x,y,18,18,0xff345363);rect(x+1,y+1,16,16,0xff142431);}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int mouseX,int mouseY){
        rect(-1,-1,xSize+2,ySize+2,0xff28677b);rect(0,0,xSize,ySize,0xff101c27);rect(0,0,xSize,2,0xff5de0ec);rect(8,144,298,1,0xff294c5b);
        if(center){for(int i=0;i<3;i++)slot(13+i*34,162);for(int r=0;r<6;r++)rect(11,64+r*11,292,10,r%2==0?0xff162b39:0xff122330);}
        else if(d().getInteger("Tab")==0){rect(198,49,106,94,0xff142634);sides.draw(fontRendererObj,guiLeft+SideConfiguration.ORIGIN_X,guiTop+SideConfiguration.ORIGIN_Y,d().getIntArray("Modes"),selected(),hitFace(mouseX,mouseY),d().getInteger("Mask"));}
        int x=center?140:76;for(int r=0;r<3;r++)for(int col=0;col<9;col++)slot(x-1+col*18,147+r*18);for(int col=0;col<9;col++)slot(x-1+col*18,205);
        if(center&&name!=null)name.drawTextBox();
        if(search!=null&&d().getInteger("Tab")>0){search.drawTextBox();if(search.getText().isEmpty()&&!search.isFocused())fontRendererObj.drawString("搜索 (回车)",guiLeft+17,guiTop+(center?35:56),0x718e9d);}
        if(center)for(int i=0;i<3;i++){long used=d().getLong("Used"+i),cap=d().getLong("Cap"+i);rect(13+i*34,191,29,3,0xff293b48);if(cap>0)rect(13+i*34,191,(int)Math.min(29,29.0*used/cap),3,used>=cap?0xffe99b65:0xff6ed9cd);}
    }
    private void text(String s,int x,int y,int color){fontRendererObj.drawString(s,x,y,color);}
    private void right(String s,int edge,int y,int color){text(s,edge-fontRendererObj.getStringWidth(s),y,color);}
    private String trim(String s,int pixels){return fontRendererObj.trimStringToWidth(s==null?"":s,pixels);}
    private static String amount(long n){return RateDisplay.amount(Long.toString(n),true);}
    private static String actualTier(long voltage){if(voltage<=0)return "--";int tier=0;while(tier<14&&(8L<<(2*tier))<voltage)tier++;return NodeSettings.TIER_NAMES[tier];}
    @Override protected void drawGuiContainerForegroundLayer(int mx,int my){
        text(center?"时枢中心":"时枢连接器",12,12,0xd9f4f6);int tab=d().getInteger("Tab");
        if(center){
            text("资源 / 单位",14,53,0x96bdcd);text("输入",148,53,0x75dbd5);text(d().getBoolean("Storage")?"存储":tab>0?"源可提取":"无库存",203,53,0xd7c48d);text("输出",274,53,0xeba97b);
            if(tab==0){for(int k=0;k<5;k++){int y=66+k*11;text(KINDS[k],14,y,0xc7e5ee);right(RateDisplay.rate(d().getLong("In"+k),k,true),181,y,0x79dfd5);right(RateDisplay.amount(d().getString("Stored"+k),true),253,y,0xe6d99a);right(RateDisplay.rate(d().getLong("Out"+k),k,true),303,y,0xeac194);}}
            else{NBTTagList list=d().getTagList("Rows",10);for(int i=0;i<list.tagCount();i++){NBTTagCompound row=list.getCompoundTagAt(i);int y=66+i*11;int kind=row.getInteger("Kind");text(trim(row.getString("Label"),111),14,y,0xc7e5ee);right(RateDisplay.rate(row.getLong("In"),kind,true),181,y,0x79dfd5);right(amount(row.getLong("Count")),253,y,0xe6d99a);right(RateDisplay.rate(row.getLong("Out"),kind,true),303,y,0xeac194);}}
            if(tab>0)text((d().getInteger("Page")+1)+"/"+Math.max(1,d().getInteger("Pages"))+" 页 · "+d().getInteger("Matches")+" 种",12,132,0x96bdcd);
            else text("能量按 tick；库存仍为总量",12,132,0x96bdcd);
            text("容量卡",12,148,0xb6d8e4);text("物品  介质  RF",12,184,0x96adb8);text("节点："+d().getInteger("Nodes"),12,197,0x96adb8);
            text(d().getBoolean("AEAttached")?(d().getBoolean("AEActive")?"AE 已连接":"AE 离线 / 权限受限"):"AE 未连接",12,210,d().getBoolean("AEActive")?0x77dfba:0xb2a887);orbit(68,15,6);
        }else{
            if(tab==0){text("当前："+FACES[selected()]+"面 · "+MODES[mode(selected())],12,51,0xc7e5ee);text("六面配置",226,50,0xaed6e4);
                text(d().getInteger("Tier")<0?actualTier(d().getLong("Voltage")):NodeSettings.TIER_NAMES[Math.max(0,Math.min(14,d().getInteger("Tier")))],82,113,0x79dce6);
                String max=d().getLong("Voltage")>0?"上限 "+RateDisplay.capacity(d().getLong("Voltage"),d().getLong("RatedAmps"))+" EU/t":"自动输出：等待额定值";
                text(trim(max,180),12,131,0x97bdcb);}
            else{text((d().getInteger("Page")+1)+"/"+Math.max(1,d().getInteger("Pages"))+" 页 · 已选 "+d().getInteger(tab==1?"ItemCount":"FluidCount")+"/9 · 点击勾选/取消",12,134,0xbbdfeb);
                if(d().getTagList("Rows",10).tagCount()==0)text("没有匹配资源；也可用光标样品指定",12,80,0xb1c7d0);}
            text("左键设面",12,152,0x83bccf);text("右键反切",12,165,0x83bccf);text("Shift查看",12,178,0x83bccf);
            text(d().getBoolean("Storage")?"存储网络":"局域直连",12,194,0xb8cb9d);text(d().getBoolean("Online")?"中心在线":"中心离线",12,208,d().getBoolean("Online")?0x7cd5b3:0xe0a075);
        }
        if(!d().getString("Fault").isEmpty())text(trim("已隔离："+d().getString("Fault"),290),12,226,0xff9085);
        else if(!d().getString("Warning").isEmpty())text(trim(d().getString("Warning"),290),12,226,0xffc080);
    }
    private static String rating(String r){return "hatch".equals(r)?"舱室额定值":"cable-minimum".equals(r)?"线缆及已知末端最小值":"unverified-cable".equals(r)?"线路未完全确认，自动输出关闭":"no-consumer".equals(r)?"未发现用电端":"未识别 EU 输入端";}
    private void orbit(int x,int y,int radius){double t=ModConfig.particles?c.tile.now()*.04:0;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_CURRENT_BIT|GL11.GL_LINE_BIT);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glLineWidth(1);
        for(int ring=0;ring<2;ring++){GL11.glColor4f(.3F,.85F,.93F,1);GL11.glBegin(GL11.GL_LINE_LOOP);for(int i=0;i<32;i++){double a=i*Math.PI/16;GL11.glVertex3d(x+Math.cos(a)*radius,y+Math.sin(a)*radius*(ring==0?.45:.8),0);}GL11.glEnd();}
        GL11.glPopAttrib();int px=x+(int)(Math.cos(t)*radius),py=y+(int)(Math.sin(t)*radius*.8);drawRect(px-1,py-1,px+2,py+2,0xffb6fff2);}
    private String protocols(int face){int[] values=d().getIntArray("Protocols");int bits=face<values.length?values[face]:0;String s="";
        if((bits&1)!=0)s+="物品 ";if((bits&2)!=0)s+="流体/气体 ";if((bits&4)!=0)s+="EU ";if((bits&8)!=0)s+="RF ";return s.isEmpty()?"未识别资源端口":s;}
    @Override public void drawScreen(int x,int y,float partial){super.drawScreen(x,y,partial);int rx=x-guiLeft,ry=y-guiTop,tab=d().getInteger("Tab");
        if(!center&&tab==0){int face=hitFace(x,y);
            if(face>=0){NBTTagList neighbors=d().getTagList("Neighbors",10);String neighbor=face<neighbors.tagCount()?neighbors.getCompoundTagAt(face).getString("Name"):"未知";
                drawHoveringText(Arrays.asList(FACES[face]+"面 · "+MODES[mode(face)],trim("相邻："+neighbor,290),"接口："+protocols(face),
                    brush<0?"左键：关闭 → 输入 → 输出；右键反向":"左键/拖动：设为"+MODES[brush]+"；右键反向切换",
                    "Shift点击：只选中，编辑该面的过滤/电压","北/东方向固定；下方三个格子是西、南、下","输入=进入网络；输出=离开网络"),x,y,fontRendererObj);return;}
            if(rx>=12&&rx<192&&ry>=63&&ry<105)drawHoveringText(Arrays.asList("正在编辑："+FACES[selected()]+"面","左键目录多选；光标样品改为单项；右键清空","每面9种物品 + 9种介质，EU/RF并行"),x,y,fontRendererObj);
            if(rx>=12&&rx<192&&ry>=108&&ry<143)drawHoveringText(Arrays.asList("正在编辑："+FACES[selected()]+"面的输出限制",
                "电压："+d().getLong("Voltage")+" V（"+actualTier(d().getLong("Voltage"))+"）  安培："+d().getLong("RatedAmps")+" A",
                "理论上限："+RateDisplay.capacity(d().getLong("Voltage"),d().getLong("RatedAmps"))+" EU/t，不等于实测流量",
                rating(d().getString("Rating")),"全网实际输入："+RateDisplay.rate(d().getLong("In3"),3,false)+" EU/t",
                "全网实际输出："+RateDisplay.rate(d().getLong("Out3"),3,false)+" EU/t（20tick平均）","手动调整后重新启用；局域模式不变压"),x,y,fontRendererObj);
        }
        if(center&&tab==0&&rx>=12&&rx<304&&ry>=64&&ry<119){int kind=(ry-64)/11;
            drawHoveringText(Arrays.asList(KINDS[kind],"实际输入："+RateDisplay.rate(d().getLong("In"+kind),kind,false)+" "+RateDisplay.unit(kind),
                "实际输出："+RateDisplay.rate(d().getLong("Out"+kind),kind,false)+" "+RateDisplay.unit(kind),
                "存储总量："+RateDisplay.amount(d().getString("Stored"+kind),false)+" "+RateDisplay.stockUnit(kind),
                RateDisplay.energy(kind)?"最近20个游戏tick的实际能量总数 ÷ 20":"最近20个游戏tick的搬运总数；1游戏秒=20tick",
                kind==3?"IV 为8192V，持续1A=8192EU/t；4A=32768EU/t":"库存是总量，不除以20"),x,y,fontRendererObj);}
        if(center&&rx>=12&&rx<113&&ry>=162&&ry<195){int slot=Math.min(2,(rx-12)/34);String unit=slot==0?"个":slot==1?"mB":"RF";
            drawHoveringText(Arrays.asList("容量卡："+d().getLong("Used"+slot)+" / "+d().getLong("Cap"+slot)+" "+unit,"类型："+d().getInteger("Types"+slot)+" / "+d().getInteger("TypeCap"+slot),"卡内总量，不是每tick流量；不重复计算AE/GT账户"),x,y,fontRendererObj);}
        if(tab>0&&rx>=12&&rx<302){int first=center?64:71,step=center?11:10,row=(ry-first)/step;NBTTagList list=d().getTagList("Rows",10);
            if(ry>=first&&row<list.tagCount()){NBTTagCompound r=list.getCompoundTagAt(row);int kind=r.getInteger("Kind");drawHoveringText(Arrays.asList(r.getString("Label"),r.getString("Id"),
                (d().getBoolean("Storage")?"存储：":"源可提取（非缓存）：")+r.getLong("Count")+" "+RateDisplay.stockUnit(kind),
                "输入 "+RateDisplay.rate(r.getLong("In"),kind,false)+" / 输出 "+RateDisplay.rate(r.getLong("Out"),kind,false)+" "+RateDisplay.unit(kind),
                center?"库存为0时仍显示正在流转的资源":r.getBoolean("Selected")?"已勾选，点击取消；完成后返回配置":"点击勾选；不会直接取出物品"),x,y,fontRendererObj);}}
    }
}
