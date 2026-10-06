package dev.chronolink.v2.tile;

import java.util.EnumSet;
import java.util.UUID;
import java.math.BigInteger;
import net.minecraft.inventory.IInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.ForgeDirection;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import gregtech.common.misc.WirelessNetworkManager;
import dev.chronolink.ChronoLink;
import dev.chronolink.v2.Networks;
import dev.chronolink.v2.compat.AEBridge;
import dev.chronolink.v2.core.Transfer;
import dev.chronolink.v2.store.Key;
import dev.chronolink.v2.store.Stock;
import dev.chronolink.v2.store.Meter;

/** LOCAL has no storage. STORAGE uses cards, AE cells and native GT team wireless EU. */
public final class TileCenter extends OwnedTile implements IInventory,IGridProxyable,IActionHost,Transfer.Store<Key> {
    public boolean storageMode=false,aeBlocked=false;
    public String name="";
    public final Stock stock=new Stock();
    public final Meter meter=new Meter();
    public final ItemStack[] cards=new ItemStack[3];
    public final AEBridge ae=new AEBridge(this);
    private AENetworkProxy proxy;
    private NBTTagCompound pendingAE;
    private boolean ready=false,euReady=false;
    public TileCenter(){proxy=new AENetworkProxy(this,"AE",null,true);proxy.setFlags(GridFlags.REQUIRE_CHANNEL);proxy.setIdlePowerUsage(0.5);proxy.setValidSides(EnumSet.noneOf(ForgeDirection.class));}
    @Override public void placed(EntityPlayer p,ItemStack stack){super.placed(p,stack);if(network==null)network=UUID.randomUUID();if(name.isEmpty())name=stack.hasDisplayName()?stack.getDisplayName():"中心 "+xCoord+","+zCoord;proxy.setOwner(p);changed();}
    @Override public void updateEntity(){if(worldObj==null||worldObj.isRemote)return;Networks.INSTANCE.register(this);
        if(owner!=null&&!euReady){WirelessNetworkManager.strongCheckOrAddUser(owner);euReady=true;}
        if(storageMode&&owner!=null&&!aeBlocked&&!ready){
            proxy.setVisualRepresentation(new ItemStack(ChronoLink.center));
            // Restore native AE identity BEFORE exposing any cable sides, including offline-owner reloads.
            proxy.setValidSides(EnumSet.noneOf(ForgeDirection.class));proxy.onReady();
            if(proxy.getNode()!=null)proxy.getNode().setPlayerID(appeng.core.worlddata.WorldData.instance().playerData().getPlayerID(new com.mojang.authlib.GameProfile(owner,owner.toString())));
            proxy.setValidSides(EnumSet.of(ForgeDirection.DOWN,ForgeDirection.UP,ForgeDirection.NORTH,ForgeDirection.SOUTH,ForgeDirection.WEST,ForgeDirection.EAST));ready=true;pendingAE=null;
        }
        meter.advance(now());if(now()%10==0)worldObj.markBlockForUpdate(xCoord,yCoord,zCoord);
    }
    public boolean changeMode(){if(!stock.entries.isEmpty()||recovery!=null)return false;storageMode=!storageMode;proxy.invalidate();ready=false;if(!storageMode)proxy.setValidSides(EnumSet.noneOf(ForgeDirection.class));changed();return true;}
    @Override public void invalidate(){Networks.INSTANCE.unregister(this);proxy.invalidate();ready=false;super.invalidate();}
    @Override public void onChunkUnload(){Networks.INSTANCE.unregister(this);proxy.onChunkUnload();ready=false;super.onChunkUnload();}
    @Override public AENetworkProxy getProxy(){return proxy;}
    @Override public DimensionalCoord getLocation(){return new DimensionalCoord(this);}
    @Override public void gridChanged(){markDirty();}
    @Override public IGridNode getGridNode(ForgeDirection d){return storageMode&&!aeBlocked?proxy.getNode():null;}
    @Override public IGridNode getActionableNode(){return getGridNode(ForgeDirection.UNKNOWN);}
    @Override public AECableType getCableConnectionType(ForgeDirection d){return AECableType.SMART;}
    @Override public void securityBreak(){aeBlocked=true;proxy.invalidate();ready=false;fail("AE security: bridge disconnected; stored resources retained");}
    public long capacity(int slot,int removed){int count=cards[slot]==null?0:cards[slot].stackSize;count=Math.max(0,count-removed);return count*(slot==0?65536L:slot==1?16000000L:1000000000L);}
    public int typeLimit(int slot){int count=cards[slot]==null?0:cards[slot].stackSize;return Math.min(slot==0?256:128,count*(slot==0?16:8));}
    public boolean canRemove(int s,int count){if(s<0||s>=3||count<0)return false;int left=(cards[s]==null?0:cards[s].stackSize)-count;
        return stock.total(s)<=capacity(s,count)&&stock.types(s)<=Math.min(s==0?256:128,Math.max(0,left)*(s==0?16:8));}
    @Override public Object identity(){return this;}
    @Override public long insert(Key k,long n,boolean simulate){if(!active()||!storageMode||n<=0)return 0;
        if(k.kind==Key.EU){if(!euReady)return 0;return simulate?n:WirelessNetworkManager.addEUToGlobalEnergyMap(owner,n)?n:0;}
        if((k.kind==Key.ITEM||k.isFluid())&&ae.attached())return ae.insert(k,n,simulate);
        int slot=Stock.slot(k);if(slot<0)return 0;long a=stock.insert(k,n,capacity(slot,0),typeLimit(slot),simulate);if(!simulate&&a>0)markDirty();return a;}
    @Override public long extract(Key k,long n,boolean simulate){if(!active()||!storageMode||n<=0)return 0;
        if(k.kind==Key.EU){if(!euReady)return 0;long a=WirelessNetworkManager.getUserEU(owner).min(BigInteger.valueOf(n)).max(BigInteger.ZERO).longValue();return simulate?a:WirelessNetworkManager.addEUToGlobalEnergyMap(owner,-a)?a:0;}
        long a=stock.extract(k,n,simulate);if(!simulate&&a>0)markDirty();if(a<n&&(k.kind==Key.ITEM||k.isFluid())&&ae.attached())a+=ae.extract(k,n-a,simulate);return a;}
    public BigInteger wirelessEU(){return owner==null||!euReady?BigInteger.ZERO:WirelessNetworkManager.getUserEU(owner);}
    @Override public boolean hasContents(){return !stock.entries.isEmpty()||cards[0]!=null||cards[1]!=null||cards[2]!=null||recovery!=null;}
    @Override public void writeToNBT(NBTTagCompound n){super.writeToNBT(n);n.setBoolean("StorageMode",storageMode);n.setBoolean("AEBlocked",aeBlocked);n.setString("Name",name);n.setTag("Stock",stock.write());
        NBTTagList list=new NBTTagList();for(int i=0;i<3;i++)if(cards[i]!=null){NBTTagCompound c=cards[i].writeToNBT(new NBTTagCompound());c.setInteger("Slot",i);list.appendTag(c);}n.setTag("Cards",list);if(proxy.getNode()==null&&pendingAE!=null&&pendingAE.hasKey("AE"))n.setTag("AE",pendingAE.getTag("AE").copy());proxy.writeToNBT(n);}
    @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);storageMode=n.getBoolean("StorageMode");aeBlocked=n.getBoolean("AEBlocked");name=n.getString("Name");pendingAE=(NBTTagCompound)n.copy();proxy.readFromNBT(n);java.util.Arrays.fill(cards,null);
        try{stock.read(n.getTagList("Stock",10));NBTTagList list=n.getTagList("Cards",10);if(list.tagCount()>3)throw new IllegalArgumentException("invalid cards");for(int j=0;j<list.tagCount();j++){NBTTagCompound c=list.getCompoundTagAt(j);int slot=c.getInteger("Slot");ItemStack s=ItemStack.loadItemStackFromNBT(c);if(slot<0||slot>=3||cards[slot]!=null||s==null||s.getItem()!=ChronoLink.capacityCard||s.getItemDamage()!=slot||s.stackSize<=0||s.stackSize>64)throw new IllegalArgumentException("unknown card");cards[slot]=s;}}
        catch(RuntimeException e){recovery=(NBTTagCompound)n.copy();fault="Saved center data needs recovery";enabled=false;}}
    @Override public NBTTagCompound visual(){NBTTagCompound n=super.visual();n.setBoolean("Storage",storageMode);n.setString("Name",name);return n;}
    @Override public void readVisual(NBTTagCompound n){super.readVisual(n);storageMode=n.getBoolean("Storage");name=n.getString("Name");}
    @Override public int getSizeInventory(){return 3;}
    @Override public ItemStack getStackInSlot(int i){return i>=0&&i<3?cards[i]:null;}
    @Override public ItemStack decrStackSize(int i,int n){if(i<0||i>=3||n<=0||cards[i]==null||!canRemove(i,Math.min(n,cards[i].stackSize)))return null;ItemStack s=cards[i].splitStack(Math.min(n,cards[i].stackSize));if(cards[i].stackSize<=0)cards[i]=null;changed();return s;}
    @Override public ItemStack getStackInSlotOnClosing(int i){return null;}
    @Override public void setInventorySlotContents(int i,ItemStack s){if(i<0||i>=3)return;if(s!=null&&(!isItemValidForSlot(i,s)||s.stackSize>64||s.stackSize<=0))return;
        int old=cards[i]==null?0:cards[i].stackSize,count=s==null?0:s.stackSize;if(count<old&&!canRemove(i,old-count))return;cards[i]=s;changed();}
    @Override public String getInventoryName(){return "chronolink.center";}
    @Override public boolean hasCustomInventoryName(){return false;}
    @Override public int getInventoryStackLimit(){return 64;}
    @Override public boolean isUseableByPlayer(EntityPlayer p){return loaded()&&mayEdit(p)&&inRange(p);}
    @Override public void openInventory(){}
    @Override public void closeInventory(){}
    @Override public boolean isItemValidForSlot(int i,ItemStack s){return s!=null&&s.getItem()==ChronoLink.capacityCard&&s.getItemDamage()==i;}
}
