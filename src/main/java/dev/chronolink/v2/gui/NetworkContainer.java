package dev.chronolink.v2.gui;

import java.util.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.ICrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.*;
import dev.chronolink.v2.Networks;
import dev.chronolink.v2.core.RelayMath;
import dev.chronolink.v2.core.SelectionRevision;
import dev.chronolink.v2.core.SideConfiguration;
import dev.chronolink.v2.core.UiActionBudget;
import dev.chronolink.v2.store.*;
import dev.chronolink.v2.tile.*;
import dev.chronolink.v2.net.Packets;

public final class NetworkContainer extends Container {
    public final OwnedTile tile;
    public NBTTagCompound display=new NBTTagCompound();
    private int face,tab,page;
    private String query="",notice="";
    private final SelectionRevision<Key> selectionRevision=new SelectionRevision<Key>();
    private final UiActionBudget actionBudget=new UiActionBudget();
    private long lastSend=Long.MIN_VALUE;
    private final List<Key> rows=new ArrayList<Key>();
    public NetworkContainer(InventoryPlayer inv,OwnedTile tile){this.tile=tile;int x=tile instanceof TileCenter?140:76;
        if(tile instanceof TileCenter){final TileCenter c=(TileCenter)tile;for(int i=0;i<3;i++){final int slot=i;addSlotToContainer(new Slot(c,i,14+i*34,163){
            @Override public boolean isItemValid(ItemStack s){return c.isItemValidForSlot(slot,s);}
            @Override public boolean canTakeStack(EntityPlayer p){return c.mayEdit(p)&&c.canRemove(slot,c.cards[slot]==null?0:c.cards[slot].stackSize);}
        });}}
        for(int r=0;r<3;r++)for(int col=0;col<9;col++)addSlotToContainer(new Slot(inv,col+r*9+9,x+col*18,148+r*18));
        for(int col=0;col<9;col++)addSlotToContainer(new Slot(inv,col,x+col*18,206));}
    @Override public boolean canInteractWith(EntityPlayer p){return tile.inRange(p)&&(tile.getWorldObj()!=null&&tile.getWorldObj().isRemote||tile.loaded()&&tile.mayEdit(p));}
    public void action(EntityPlayer p,int id,int rev,String text){
        if(!Packets.validAction(id)||tile.getWorldObj()==null||tile.getWorldObj().isRemote||p.openContainer!=this||!canInteractWith(p)||!actionBudget.allow(tile.now()))return;
        notice="";
        if(SideConfiguration.isSideAction(id)){
            if(tab!=0||!(tile instanceof TileConduit)||!tile.fault.isEmpty()||tile.recovery!=null)return;
            TileConduit n=(TileConduit)tile;
            if(!SideConfiguration.apply(n.modes,id))return;
            face=SideConfiguration.face(id);n.changedSettings();notifyNeighbors();
        }else if(id>=200&&id<206){
            if(tab==0||!selectionRevision.accepts(rev)||id-200>=rows.size()||!(tile instanceof TileConduit)||!tile.fault.isEmpty()||tile.recovery!=null)return;
            TileConduit n=(TileConduit)tile;Key key=rows.get(id-200);
            OutputSelection set=key.kind==Key.ITEM?n.itemFilters[face]:n.fluidFilters[face];
            if(!set.toggle(key))notice="本面最多选择9种物品及9种介质";
            n.changedSettings();notifyNeighbors();
        }else if(id==10){page=Math.max(0,page-1);}
        else if(id==11){page=Math.min(10000000,page+1);}
        else if(id==12){tab=(tab+1)%3;page=0;query="";}
        else if(id==14){tab=0;page=0;query="";}
        else if(id==18){query=CatalogueView.cleanQuery(text);page=0;}
        else if(id>=100&&id<106){face=id-100;}
        else if(id==0&&tile.fault.isEmpty()&&tile.recovery==null&&(!(tile instanceof TileConduit)||!tile.hasContents())){tile.enabled=!tile.enabled;tile.changed();notifyNeighbors();}
        else if(tile instanceof TileCenter){TileCenter c=(TileCenter)tile;
            if(id==1&&!c.changeMode())notice="先取空容量卡中的资源，再切换模式";
            else if(id==15){String clean=CatalogueView.cleanQuery(text);if(!clean.isEmpty()){c.name=clean.substring(0,Math.min(32,clean.length()));c.changed();}}
        }else if(tile instanceof TileConduit&&tile.fault.isEmpty()&&tile.recovery==null){TileConduit n=(TileConduit)tile;
            if(id==2){List<TileCenter> available=Networks.INSTANCE.centers(n.owner);if(!available.isEmpty()){int found=-1;for(int i=0;i<available.size();i++)if(available.get(i).network.equals(n.network))found=i;n.network=available.get((found+1)%available.size()).network;n.enabled=false;}}
            if(id==3)n.modes[face]=(n.modes[face]+1)%3;
            if(id==4)Arrays.fill(n.modes,n.modes[face]);
            if(id==5){n.tiers[face]=-1;n.amps[face]=0;}
            if(id==8||id==9){n.tiers[face]=Math.max(0,Math.min(14,(n.tiers[face]<0?1:n.tiers[face])+(id==8?-1:1)));n.enabled=false;}
            if(id==13){n.amps[face]=n.amps[face]==0?1:n.amps[face]>=64?0:n.amps[face]*2;n.enabled=false;}
            if(id==6||id==7){ItemStack sample=p.inventory.getItemStack();
                if(sample==null){tab=id==6?1:2;page=0;query="";}
                else if(id==6)n.itemFilters[face].replace(Key.of(sample));
                else{FluidStack f=FluidContainerRegistry.getFluidForFilledItem(sample);if(f==null&&sample.getItem() instanceof IFluidContainerItem)f=((IFluidContainerItem)sample.getItem()).getFluid(sample.copy());if(f!=null)n.fluidFilters[face].replace(Key.of(f));}
            }
            if(id==16)n.itemFilters[face].clear();if(id==17)n.fluidFilters[face].clear();n.changedSettings();notifyNeighbors();
        }
        lastSend=Long.MIN_VALUE;detectAndSendChanges();
    }
    private void notifyNeighbors(){tile.getWorldObj().notifyBlocksOfNeighborChange(tile.xCoord,tile.yCoord,tile.zCoord,tile.getBlockType());}
    @Override public ItemStack slotClick(int slot,int button,int mode,EntityPlayer player){return canInteractWith(player)?super.slotClick(slot,button,mode,player):null;}
    @Override public ItemStack transferStackInSlot(EntityPlayer p,int index){if(!canInteractWith(p)||index<0||index>=inventorySlots.size())return null;Slot s=(Slot)inventorySlots.get(index);if(!s.getHasStack()||!s.canTakeStack(p))return null;ItemStack stack=s.getStack(),before=stack.copy();int start=tile instanceof TileCenter?3:0;
        if(index<start){if(!mergeItemStack(stack,start,inventorySlots.size(),true))return null;}
        else if(start==3&&stack.getItem()==dev.chronolink.ChronoLink.capacityCard){int slot=stack.getItemDamage();if(slot<0||slot>=3||!mergeItemStack(stack,slot,slot+1,false))return null;}
        else if(index<start+27){if(!mergeItemStack(stack,start+27,start+36,false))return null;}
        else if(!mergeItemStack(stack,start,start+27,false))return null;
        if(stack.stackSize==0)s.putStack(null);else s.onSlotChanged();return before;}
    @Override public void addCraftingToCrafters(ICrafting listener){super.addCraftingToCrafters(listener);lastSend=Long.MIN_VALUE;}
    @Override public void detectAndSendChanges(){super.detectAndSendChanges();if(tile.getWorldObj()==null||tile.getWorldObj().isRemote||lastSend!=Long.MIN_VALUE&&tile.now()-lastSend<10)return;lastSend=tile.now();
        NBTTagCompound n;
        try{n=snapshot();display=n;}
        catch(RuntimeException ex){
            rows.clear();selectionRevision.update("unavailable",rows);
            n=(NBTTagCompound)display.copy();n.setString("Warning","目录暂不可读取："+ex.getClass().getSimpleName());n.setBoolean("Stale",true);n.setTag("Rows",new NBTTagList());
        }
        for(Object c:crafters)if(c instanceof EntityPlayerMP)Packets.channel.sendTo(new Packets.Snapshot(windowId,n),(EntityPlayerMP)c);}
    public NBTTagCompound snapshot(){
        NBTTagCompound out=new NBTTagCompound();out.setInteger("Face",face);out.setInteger("Tab",tab);out.setInteger("Page",page);out.setBoolean("Enabled",tile.enabled);out.setString("Fault",tile.fault);out.setString("Warning",notice);out.setBoolean("Center",tile instanceof TileCenter);out.setString("Query",query);
        // In*/Out* remain unmodified, actual 20-tick totals; EU/RF conversion is display-only.
        out.setInteger("RateWindowTicks",20);
        TileCenter c=tile instanceof TileCenter?(TileCenter)tile:((TileConduit)tile).center();
        out.setString("Network",c==null?"未选择 / 中心未加载":c.name);out.setBoolean("Online",c!=null&&c.active());out.setBoolean("Storage",c!=null&&c.storageMode);
        rows.clear();if(c!=null){
            out.setInteger("Nodes",Networks.INSTANCE.members(c).size());out.setBoolean("AEAttached",c.storageMode&&c.ae.attached());out.setBoolean("AEActive",c.storageMode&&c.ae.allowed(false));
            for(int i=0;i<3;i++){out.setLong("Cap"+i,c.capacity(i,0));out.setLong("Used"+i,c.stock.total(i));out.setInteger("Types"+i,c.stock.types(i));out.setInteger("TypeCap"+i,c.typeLimit(i));}
            long[] amount=new long[5];for(Map.Entry<Key,Long> e:c.stock.entries.entrySet())amount[e.getKey().kind]=RelayMath.add(amount[e.getKey().kind],e.getValue());
            if(c.storageMode&&c.ae.attached()){long[] ae=c.ae.totals();for(int i=0;i<5;i++)amount[i]=RelayMath.add(amount[i],ae[i]);}
            for(int i=0;i<5;i++){out.setLong("In"+i,c.meter.rate(i,0));out.setLong("Out"+i,c.meter.rate(i,1));out.setString("Stored"+i,!c.storageMode?"—":i==Key.EU?c.wirelessEU().toString():Long.toString(amount[i]));}
            if(tab>0){
                Map<Key,Long> catalogue=Networks.INSTANCE.catalogue(c,tab-1,page);
                List<Key> selected=tile instanceof TileConduit?((TileConduit)tile).configured(face,tab==1):Collections.<Key>emptyList();
                CatalogueView.Page view=CatalogueView.page(catalogue,c.meter.perResource.keySet(),selected,tab-1,query,page);
                page=view.index;out.setInteger("Page",page);out.setInteger("Pages",view.pages);out.setInteger("Matches",view.total);
                NBTTagList entries=new NBTTagList();
                for(CatalogueView.Row row:view.rows){Key k=row.key;rows.add(k);NBTTagCompound entry=new NBTTagCompound();entry.setString("Label",row.label);entry.setString("Id",row.id);entry.setInteger("Kind",k.kind);entry.setBoolean("Selected",selected.contains(k));entry.setLong("Count",row.count);entry.setLong("In",c.meter.rate(k,0));entry.setLong("Out",c.meter.rate(k,1));entries.appendTag(entry);}
                out.setTag("Rows",entries);
            }
        }
        if(tile instanceof TileConduit){TileConduit n=(TileConduit)tile;out.setIntArray("Modes",n.modes);out.setIntArray("Protocols",n.capabilities);out.setInteger("Mask",n.mask);out.setInteger("Tier",n.tiers[face]);out.setInteger("Amps",n.amps[face]);
            out.setString("Item",n.itemFilters[face].label());out.setString("Fluid",n.fluidFilters[face].label());out.setInteger("ItemCount",n.itemFilters[face].size());out.setInteger("FluidCount",n.fluidFilters[face].size());
            try{out.setLong("Voltage",n.voltage(face));out.setString("Rating",n.rating(face).reason);out.setLong("RatedAmps",n.amperage(face));}catch(RuntimeException ex){out.setString("Rating","unknown");}
            NBTTagList neighbors=new NBTTagList();for(int s=0;s<6;s++){NBTTagCompound entry=new NBTTagCompound();String label="无可识别端口";
                try{net.minecraft.tileentity.TileEntity other=n.neighbor(s);if(TileConduit.internal(other))label="本网络设备";else if(other!=null)label=other instanceof net.minecraft.inventory.IInventory?((net.minecraft.inventory.IInventory)other).getInventoryName():other.getBlockType().getLocalizedName();}
                catch(RuntimeException ex){label="端口名称暂不可用";}entry.setString("Name",label==null?"未命名端口":label);neighbors.appendTag(entry);}out.setTag("Neighbors",neighbors);
            out.setBoolean("Recovery",n.hasContents());
        }
        String context=face+":"+tab+":"+page+":"+query+":"+String.valueOf(tile.network);
        out.setInteger("Revision",selectionRevision.update(context,rows));
        return out;
    }
}
