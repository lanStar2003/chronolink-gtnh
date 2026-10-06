package dev.chronolink.tile;

import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.*;
import cofh.api.energy.IEnergyHandler;
import gregtech.api.interfaces.tileentity.IEnergyConnected;
import dev.chronolink.ModConfig;
import dev.chronolink.ChronoLink;
import dev.chronolink.core.*;
import dev.chronolink.transfer.*;

/** Single adjacent face. Buffers persist locally; no invisible central inventory is lost on controller removal. */
public final class TileConnector extends TileEntity implements ISidedInventory,IFluidHandler,IEnergyHandler,IEnergyConnected,Router.Port {
    public static final int FLUID_CAPACITY=16000,RF_CAPACITY=16000000;
    public final NodeSettings settings=new NodeSettings();
    public UUID owner;
    public ItemStack itemBuffer,filter;
    public FluidStack fluidBuffer;
    public final EnergyStore eu=new EnergyStore(1L<<60),rf=new EnergyStore(1L<<60);
    public final TickBudget euIn=new TickBudget(),euOut=new TickBudget(),rfIn=new TickBudget(),rfOut=new TickBudget(),
        fluidIn=new TickBudget(),fluidOut=new TickBudget(),routeIn=new TickBudget();
    public int inventoryCursor,status;
    public boolean clientFlow;
    public long displayAmount;
    private String fault="";
    private NBTTagCompound recovery;
    private long lastMovement=Long.MIN_VALUE;
    private boolean lastVisual;

    public long now() { return worldObj==null?0:worldObj.getTotalWorldTime(); }
    public boolean isLoaded() {
        return worldObj!=null && !worldObj.isRemote && !isInvalid()
            && worldObj.blockExists(xCoord,yCoord,zCoord) && worldObj.getTileEntity(xCoord,yCoord,zCoord)==this;
    }
    @Override public boolean active() { return isLoaded() && owner!=null && settings.enabled && !hasFault(); }
    public boolean hasFault() { return !fault.isEmpty(); }
    public String faultReason() { return fault; }
    public boolean mayEdit(EntityPlayer player) { return player!=null && owner!=null && owner.equals(player.getUniqueID()); }
    public boolean inRange(EntityPlayer player) { return player.getDistanceSq(xCoord+0.5,yCoord+0.5,zCoord+0.5)<=64.0; }
    public ForgeDirection face() { return ForgeDirection.getOrientation(settings.face); }
    public ForgeDirection neighborFace() { return face().getOpposite(); }
    public TileEntity neighbor() {
        ForgeDirection d=face(); int x=xCoord+d.offsetX,y=yCoord+d.offsetY,z=zCoord+d.offsetZ;
        if(!worldObj.blockExists(x,y,z)) return null; // must not load an adjacent chunk
        TileEntity te=worldObj.getTileEntity(x,y,z);
        return te instanceof TileConnector?null:te; // no recursive native-interface loops between connectors
    }
    public boolean hasPayload() {
        return itemBuffer!=null || fluidBuffer!=null || eu.amount()>0 || rf.amount()>0 || recovery!=null;
    }
    public void changed() {
        markDirty(); if(worldObj!=null && !worldObj.isRemote) worldObj.markBlockForUpdate(xCoord,yCoord,zCoord);
    }
    public void moved(long n) {
        if(n>0) { lastMovement=now();status=1;markDirty(); }
    }
    @Override public void quarantine(String reason) {
        settings.enabled=false;status=7;
        fault=reason==null?"unknown boundary failure":reason.substring(0,Math.min(160,reason.length()));
        if(ChronoLink.log!=null) ChronoLink.log.error("Quarantined ChronoLink at {},{},{}: {}",xCoord,yCoord,zCoord,fault);
        changed();
    }
    @Override public void updateEntity() {
        if(worldObj==null || worldObj.isRemote) return;
        NetworkHub.INSTANCE.register(this);
        if(active()) {
            status=0;
            try { BoundaryTransfer.tick(this); }
            catch(RuntimeException ex) { quarantine("External API failed: "+ex.getClass().getSimpleName()); }
        }
        boolean visual=lastMovement!=Long.MIN_VALUE && now()>=lastMovement && now()-lastMovement<12;
        if(now()%10==0 && visual!=lastVisual) { lastVisual=visual;worldObj.markBlockForUpdate(xCoord,yCoord,zCoord); }
    }
    @Override public void invalidate() { NetworkHub.INSTANCE.unregister(this);super.invalidate(); }
    @Override public void onChunkUnload() { NetworkHub.INSTANCE.unregister(this);super.onChunkUnload(); }

    @Override public RouteKey routeKey() { return owner==null?null:new RouteKey(owner,settings.channel,settings.resource); }
    @Override public boolean importing() { return settings.importing; }
    @Override public long available() {
        switch(settings.resource) {
            case ITEM:return itemBuffer==null?0:itemBuffer.stackSize;
            case FLUID:return fluidBuffer==null?0:fluidBuffer.amount;
            case EU:return eu.amount();
            case RF:return rf.amount();
            default:return 0;
        }
    }
    @Override public long routeLimit() {
        switch(settings.resource) {
            case ITEM:return ModConfig.itemBatch;
            case FLUID:return ModConfig.fluidRate;
            case EU:return settings.voltage()*settings.amps;
            case RF:return ModConfig.rfRate;
            default:return 0;
        }
    }
    @Override public long moveTo(Router.Port raw,long limit) {
        if(!(raw instanceof TileConnector)) return 0;
        TileConnector to=(TileConnector)raw;
        if(to==this || !active() || !to.active() || !settings.importing || to.settings.importing
            || !routeKey().equals(to.routeKey())) return 0;
        limit=Math.min(limit,to.routeIn.remaining(to.now(),to.routeLimit()));
        if(limit<=0) return 0;
        long n=0;
        switch(settings.resource) {
            case ITEM:
                if(itemBuffer==null || !to.matches(itemBuffer)) return 0;
                if(to.itemBuffer!=null && !sameItem(itemBuffer,to.itemBuffer)) return 0;
                int itemRoom=Math.min(64,itemBuffer.getMaxStackSize())-(to.itemBuffer==null?0:to.itemBuffer.stackSize);
                n=Math.min(Math.min(limit,itemBuffer.stackSize),Math.max(0,itemRoom));
                if(n>0) {
                    if(to.itemBuffer==null) { to.itemBuffer=itemBuffer.copy();to.itemBuffer.stackSize=(int)n; }
                    else to.itemBuffer.stackSize+=(int)n;
                    itemBuffer.stackSize-=(int)n;if(itemBuffer.stackSize==0)itemBuffer=null;
                }
                break;
            case FLUID:
                if(fluidBuffer==null || !to.matches(fluidBuffer)) return 0;
                if(to.fluidBuffer!=null && !fluidBuffer.isFluidEqual(to.fluidBuffer)) return 0;
                n=Math.min(Math.min(limit,fluidBuffer.amount),Math.max(0,FLUID_CAPACITY-(to.fluidBuffer==null?0:to.fluidBuffer.amount)));
                if(n>0) {
                    if(to.fluidBuffer==null) { to.fluidBuffer=fluidBuffer.copy();to.fluidBuffer.amount=(int)n; }
                    else to.fluidBuffer.amount+=(int)n;
                    fluidBuffer.amount-=(int)n;if(fluidBuffer.amount==0)fluidBuffer=null;
                }
                break;
            case EU:n=eu.moveTo(to.eu,limit);break;
            case RF:n=rf.moveTo(to.rf,Math.min(limit,Math.max(0,RF_CAPACITY-to.rf.amount())));break;
            default:return 0;
        }
        if(n>0) { to.routeIn.spend(to.now(),n,to.routeLimit());moved(n);to.moved(n); }
        return n;
    }
    public static boolean sameItem(ItemStack a,ItemStack b) {
        return a!=null && b!=null && a.isItemEqual(b) && ItemStack.areItemStackTagsEqual(a,b);
    }
    public boolean matches(ItemStack stack) { return stack!=null && (filter==null || sameItem(filter,stack)); }
    public FluidStack filterFluid() {
        if(filter==null) return null;
        FluidStack f=FluidContainerRegistry.getFluidForFilledItem(filter);
        if(f==null && filter.getItem() instanceof IFluidContainerItem)
            f=((IFluidContainerItem)filter.getItem()).getFluid(filter.copy());
        return f;
    }
    public boolean matches(FluidStack fluid) {
        if(fluid==null) return false;
        if(filter==null) return true;
        FluidStack f=filterFluid();return f!=null && f.isFluidEqual(fluid);
    }
    public boolean setFilter(ItemStack stack) {
        if(hasPayload() || hasFault()) return false;
        filter=stack==null?null:stack.copy();if(filter!=null)filter.stackSize=1;
        settings.enabled=false;changed();return true;
    }
    private boolean accepts(ForgeDirection from,ResourceKind kind,boolean input) {
        return active() && from!=null && from==face() && settings.resource==kind && settings.importing==input;
    }

    // GT native EU: offered packets are passive input. No field access or generation from RF.
    @Override public long injectEnergyUnits(ForgeDirection from,long voltage,long amperage) {
        if(!accepts(from,ResourceKind.EU,true) || voltage<=0 || voltage>settings.voltage()) return 0;
        long n=eu.acceptPacket(voltage,amperage,euIn.remaining(now(),settings.amps),false);
        if(n>0) { euIn.spend(now(),n,settings.amps);moved(voltage*n); }
        return n;
    }
    public boolean inputEnergyFrom(ForgeDirection from) { return accepts(from,ResourceKind.EU,true); }
    public boolean outputsEnergyTo(ForgeDirection from) { return accepts(from,ResourceKind.EU,false); }
    public boolean outputEnergyTo(ForgeDirection from) { return outputsEnergyTo(from); }
    public boolean inputEnergyFrom(ForgeDirection from,boolean waitForActive) { return inputEnergyFrom(from); }
    public boolean outputsEnergyTo(ForgeDirection from,boolean waitForActive) { return outputsEnergyTo(from); }
    public byte getColorization() { return -1; }
    public byte setColorization(byte color) { return -1; }

    // RF active and passive callers consume the same directional budget.
    @Override public boolean canConnectEnergy(ForgeDirection from) { return active() && from==face() && settings.resource==ResourceKind.RF; }
    @Override public int receiveEnergy(ForgeDirection from,int offered,boolean simulate) {
        if(!accepts(from,ResourceKind.RF,true)) return 0;
        long n=Amounts.bounded(offered,Math.min(Math.max(0,RF_CAPACITY-rf.amount()),rfIn.remaining(now(),ModConfig.rfRate)));
        if(!simulate && n>0) {rf.insert(n,false);rfIn.spend(now(),n,ModConfig.rfRate);moved(n);}
        return (int)n;
    }
    @Override public int extractEnergy(ForgeDirection from,int requested,boolean simulate) {
        if(!accepts(from,ResourceKind.RF,false)) return 0;
        long n=Amounts.bounded(requested,Math.min(rf.amount(),rfOut.remaining(now(),ModConfig.rfRate)));
        if(!simulate && n>0) {rf.extract(n,false);rfOut.spend(now(),n,ModConfig.rfRate);moved(n);}
        return (int)n;
    }
    @Override public int getEnergyStored(ForgeDirection from) { return canConnectEnergy(from)?(int)Math.min(Integer.MAX_VALUE,rf.amount()):0; }
    @Override public int getMaxEnergyStored(ForgeDirection from) { return canConnectEnergy(from)?RF_CAPACITY:0; }

    // Forge fluids: liquid and gaseous FluidStacks use one native-unit accounting path.
    @Override public boolean canFill(ForgeDirection from,Fluid fluid) {
        return accepts(from,ResourceKind.FLUID,true) && fluid!=null;
    }
    @Override public boolean canDrain(ForgeDirection from,Fluid fluid) {
        return accepts(from,ResourceKind.FLUID,false) && fluidBuffer!=null
            && (fluid==null || fluidBuffer.getFluid()==fluid);
    }
    @Override public int fill(ForgeDirection from,FluidStack offered,boolean doFill) {
        if(offered==null || !canFill(from,offered.getFluid()) || !matches(offered)) return 0;
        if(fluidBuffer!=null && !fluidBuffer.isFluidEqual(offered)) return 0;
        long room=Math.max(0,FLUID_CAPACITY-(fluidBuffer==null?0:fluidBuffer.amount));
        int n=(int)Amounts.bounded(offered.amount,Math.min(room,fluidIn.remaining(now(),ModConfig.fluidRate)));
        if(doFill && n>0) {
            if(fluidBuffer==null){fluidBuffer=offered.copy();fluidBuffer.amount=n;}else fluidBuffer.amount+=n;
            fluidIn.spend(now(),n,ModConfig.fluidRate);moved(n);
        }
        return n;
    }
    @Override public FluidStack drain(ForgeDirection from,FluidStack requested,boolean doDrain) {
        return requested!=null && fluidBuffer!=null && fluidBuffer.isFluidEqual(requested)
            ?drain(from,requested.amount,doDrain):null;
    }
    @Override public FluidStack drain(ForgeDirection from,int requested,boolean doDrain) {
        if(!canDrain(from,null)) return null;
        int n=(int)Amounts.bounded(requested,Math.min(fluidBuffer.amount,fluidOut.remaining(now(),ModConfig.fluidRate)));
        if(n<=0)return null;
        FluidStack result=fluidBuffer.copy();result.amount=n;
        if(doDrain){fluidBuffer.amount-=n;if(fluidBuffer.amount==0)fluidBuffer=null;fluidOut.spend(now(),n,ModConfig.fluidRate);moved(n);}
        return result;
    }
    @Override public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        if(!active() || from!=face() || settings.resource!=ResourceKind.FLUID) return new FluidTankInfo[0];
        return new FluidTankInfo[]{new FluidTankInfo(fluidBuffer==null?null:fluidBuffer.copy(),FLUID_CAPACITY)};
    }

    // Sided 1-slot inventory for native item pipes. Filter is NEVER an accessible item.
    @Override public int[] getAccessibleSlotsFromSide(int side) {
        return active() && settings.resource==ResourceKind.ITEM && side==settings.face?new int[]{0}:new int[0];
    }
    @Override public boolean canInsertItem(int slot,ItemStack stack,int side) {
        return slot==0 && accepts(ForgeDirection.getOrientation(side),ResourceKind.ITEM,true) && matches(stack);
    }
    @Override public boolean canExtractItem(int slot,ItemStack stack,int side) {
        return slot==0 && accepts(ForgeDirection.getOrientation(side),ResourceKind.ITEM,false);
    }
    @Override public int getSizeInventory() { return 1; }
    @Override public ItemStack getStackInSlot(int slot) { return slot==0?itemBuffer:null; }
    @Override public ItemStack decrStackSize(int slot,int count) {
        if(slot!=0 || count<=0 || itemBuffer==null || !active() || settings.importing || settings.resource!=ResourceKind.ITEM) return null;
        ItemStack out=itemBuffer.splitStack(Math.min(count,itemBuffer.stackSize));
        if(itemBuffer.stackSize<=0)itemBuffer=null;moved(out.stackSize);return out;
    }
    @Override public ItemStack getStackInSlotOnClosing(int slot) { return null; }
    @Override public void setInventorySlotContents(int slot,ItemStack stack) {
        if(slot!=0 || !active() || settings.resource!=ResourceKind.ITEM) return;
        if(settings.importing) {
            if(stack!=null && !isItemValidForSlot(slot,stack)) return;
        } else {
            // Some native pipes extract by replacing the remainder instead of decrStackSize.
            if(stack!=null && (itemBuffer==null || !sameItem(stack,itemBuffer) || stack.stackSize>itemBuffer.stackSize)) return;
        }
        int previous=itemBuffer==null?0:itemBuffer.stackSize;
        itemBuffer=stack==null?null:stack.copy();
        moved(Math.abs(previous-(itemBuffer==null?0:itemBuffer.stackSize)));markDirty();
        if(itemBuffer!=null && (itemBuffer.stackSize<1 || itemBuffer.stackSize>Math.min(64,itemBuffer.getMaxStackSize())))
            quarantine("native inventory supplied invalid stack size");
    }
    @Override public String getInventoryName() { return "container.chronolink"; }
    @Override public boolean hasCustomInventoryName() { return false; }
    @Override public int getInventoryStackLimit() { return 64; }
    @Override public boolean isUseableByPlayer(EntityPlayer player) { return mayEdit(player) && inRange(player); }
    @Override public void openInventory() {}
    @Override public void closeInventory() {}
    @Override public boolean isItemValidForSlot(int slot,ItemStack stack) {
        return slot==0 && active() && settings.resource==ResourceKind.ITEM && settings.importing && matches(stack)
            && (itemBuffer==null || sameItem(itemBuffer,stack));
    }

    private void writeState(NBTTagCompound n) {
        n.setInteger("Schema",1);n.setString("Owner",owner==null?"":owner.toString());
        n.setInteger("Channel",settings.channel);n.setInteger("Face",settings.face);n.setInteger("Kind",settings.resource.ordinal());
        n.setBoolean("Import",settings.importing);n.setBoolean("Enabled",settings.enabled);
        n.setInteger("Tier",settings.tier);n.setInteger("Amps",settings.amps);n.setString("Fault",fault);
        n.setLong("EU",eu.amount());n.setLong("RF",rf.amount());
        if(itemBuffer!=null) {
            n.setTag("Item",itemBuffer.writeToNBT(new NBTTagCompound()));
            // Preserve an anomalous supplier's full count for recovery; vanilla Count is only a byte.
            n.setInteger("ItemCount",itemBuffer.stackSize);
        }
        if(fluidBuffer!=null)n.setTag("Fluid",fluidBuffer.writeToNBT(new NBTTagCompound()));
        if(filter!=null)n.setTag("Filter",filter.writeToNBT(new NBTTagCompound()));
        if(recovery!=null)n.setTag("Recovery",recovery.copy());
    }
    private void readState(NBTTagCompound n) {
        try {
            if(n.hasKey("Schema") && n.getInteger("Schema")!=1) throw new IllegalArgumentException("unsupported save schema");
            String savedOwner=n.getString("Owner");owner=savedOwner.isEmpty()?null:UUID.fromString(savedOwner);
            settings.channel=n.hasKey("Channel")?n.getInteger("Channel"):1;
            settings.face=n.getInteger("Face");
            int kind=n.getInteger("Kind");
            if(kind<0 || kind>=ResourceKind.values().length)throw new IllegalArgumentException("invalid resource kind");
            settings.resource=ResourceKind.values()[kind];
            settings.importing=!n.hasKey("Import") || n.getBoolean("Import");settings.enabled=n.getBoolean("Enabled");
            settings.tier=n.hasKey("Tier")?n.getInteger("Tier"):1;settings.amps=n.hasKey("Amps")?n.getInteger("Amps"):1;
            if(settings.channel<1 || settings.channel>64 || settings.face<0 || settings.face>5
                || settings.tier<0 || settings.tier>14 || settings.amps<1 || settings.amps>16)
                throw new IllegalArgumentException("saved settings out of range");
            fault=n.getString("Fault");eu.restore(n.getLong("EU"));rf.restore(n.getLong("RF"));
            itemBuffer=n.hasKey("Item",10)?ItemStack.loadItemStackFromNBT(n.getCompoundTag("Item")):null;
            if(itemBuffer!=null && n.hasKey("ItemCount"))itemBuffer.stackSize=n.getInteger("ItemCount");
            fluidBuffer=n.hasKey("Fluid",10)?FluidStack.loadFluidStackFromNBT(n.getCompoundTag("Fluid")):null;
            filter=n.hasKey("Filter",10)?ItemStack.loadItemStackFromNBT(n.getCompoundTag("Filter")):null;
            if(n.hasKey("Item",10) && (itemBuffer==null || itemBuffer.stackSize<1))throw new IllegalArgumentException("unreadable item");
            if(n.hasKey("Fluid",10) && (fluidBuffer==null || fluidBuffer.amount<1))throw new IllegalArgumentException("unreadable fluid");
            if(n.hasKey("Filter",10) && filter==null)throw new IllegalArgumentException("unreadable filter");
            recovery=n.hasKey("Recovery",10)?n.getCompoundTag("Recovery"):null;
            if(recovery!=null && fault.isEmpty())fault="recovery payload needs inspection";
            if(itemBuffer!=null && itemBuffer.stackSize>Math.min(64,itemBuffer.getMaxStackSize()))
                throw new IllegalArgumentException("saved item exceeds normal buffer capacity");
            if(fluidBuffer!=null && fluidBuffer.amount>FLUID_CAPACITY)
                throw new IllegalArgumentException("saved fluid exceeds normal buffer capacity");
            if(rf.amount()>RF_CAPACITY)throw new IllegalArgumentException("saved RF exceeds normal buffer capacity");
            if((itemBuffer!=null && settings.resource!=ResourceKind.ITEM)
                || (fluidBuffer!=null && settings.resource!=ResourceKind.FLUID)
                || (eu.amount()>0 && settings.resource!=ResourceKind.EU)
                || (rf.amount()>0 && settings.resource!=ResourceKind.RF))
                throw new IllegalArgumentException("payload kind differs from configured resource");
            if(owner==null && hasPayload())throw new IllegalArgumentException("saved payload has no owner");
            if(hasFault())settings.enabled=false;
        } catch(RuntimeException ex) {
            // Retain the first complete snapshot instead of nesting a fresh Recovery on every reload.
            recovery=(NBTTagCompound)(n.hasKey("Recovery",10)?n.getCompoundTag("Recovery").copy():n.copy());
            fault="saved payload needs inspection: "+ex.getMessage();settings.enabled=false;
            settings.sanitize(); // safe inert UI fields; resource contents are not rewritten
        }
    }
    @Override public void writeToNBT(NBTTagCompound n) { super.writeToNBT(n);writeState(n); }
    @Override public void readFromNBT(NBTTagCompound n) { super.readFromNBT(n);readState(n); }
    public void readPortable(NBTTagCompound n) { readState(n);settings.enabled=false; }
    public void writeIntoItem(ItemStack stack) {
        NBTTagCompound data=new NBTTagCompound();writeState(data);data.setBoolean("Enabled",false);
        NBTTagCompound root=new NBTTagCompound();root.setTag("ChronoLinkData",data);stack.setTagCompound(root);
    }
    @Override public Packet getDescriptionPacket() {
        NBTTagCompound n=new NBTTagCompound();n.setInteger("Face",settings.face);n.setBoolean("Flow",lastVisual);
        return new S35PacketUpdateTileEntity(xCoord,yCoord,zCoord,1,n);
    }
    @Override public void onDataPacket(NetworkManager manager,S35PacketUpdateTileEntity packet) {
        NBTTagCompound n=packet.func_148857_g();settings.face=NodeSettings.clamp(n.getInteger("Face"),0,5);clientFlow=n.getBoolean("Flow");
        if(worldObj!=null)worldObj.markBlockForUpdate(xCoord,yCoord,zCoord);
    }
}
