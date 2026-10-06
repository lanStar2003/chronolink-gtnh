package dev.chronolink.v2.tile;

import java.util.Arrays;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.*;
import cofh.api.energy.IEnergyHandler;
import gregtech.api.interfaces.tileentity.IEnergyConnected;
import appeng.api.networking.IGridHost;
import dev.chronolink.ModConfig;
import dev.chronolink.core.TickBudget;
import dev.chronolink.core.NodeSettings;
import dev.chronolink.v2.Networks;
import dev.chronolink.v2.core.Transfer;
import dev.chronolink.v2.store.Key;
import dev.chronolink.v2.store.Stock;
import dev.chronolink.v2.compat.VoltageProbe;

/** Six auto-detected sides, simultaneous native protocols. No normal local inventory or energy buffer. */
public final class TileConduit extends OwnedTile implements ISidedInventory,IFluidHandler,IEnergyHandler,IEnergyConnected,Transfer.Rescue<Key> {
    public static final int OFF=0,IN=1,OUT=2;
    public final int[] modes={IN,IN,IN,IN,IN,IN},tiers={-1,-1,-1,-1,-1,-1},amps=new int[6],capabilities=new int[6];
    public final Key[] itemFilters=new Key[6],fluidFilters=new Key[6];
    public final TickBudget[] inbound=new TickBudget[5],outbound=new TickBudget[5];
    public final Stock rescue=new Stock();
    public int mask,cursor;
    private final VoltageProbe.Rating[] ratings=new VoltageProbe.Rating[6];
    private final long[] ratingTime={Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE};
    private final ItemStack[] advertised=new ItemStack[6];
    private final Key[] refundKey=new Key[6];private final long[] refundCount=new long[6],refundTick=new long[6];
    public final Transfer.Store<Key>[] refundStore;
    @SuppressWarnings("unchecked") public TileConduit(){refundStore=(Transfer.Store<Key>[])new Transfer.Store<?>[6];for(int i=0;i<5;i++){inbound[i]=new TickBudget();outbound[i]=new TickBudget();}enabled=false;}
    @Override public void placed(EntityPlayer p,ItemStack stack){super.placed(p,stack);enabled=false;Networks.INSTANCE.register(this);Networks.INSTANCE.autoBind(this);scan();}
    public TileEntity neighbor(int s){if(worldObj==null||s<0||s>=6)return null;ForgeDirection d=ForgeDirection.getOrientation(s);int x=xCoord+d.offsetX,y=yCoord+d.offsetY,z=zCoord+d.offsetZ;
        if(y<0||y>=worldObj.getHeight()||!worldObj.blockExists(x,y,z))return null;return worldObj.getTileEntity(x,y,z);}
    public static boolean internal(TileEntity t){return t instanceof OwnedTile||t instanceof dev.chronolink.tile.TileConnector;}
    public TileEntity endpoint(int s){TileEntity t=neighbor(s);return internal(t)?null:t;}
    public void scan(){int m=0;Arrays.fill(ratingTime,Long.MIN_VALUE);for(int s=0;s<6;s++){TileEntity t=neighbor(s);int c=0;
        if(t instanceof net.minecraft.inventory.IInventory)c|=1;if(t instanceof IFluidHandler)c|=2;if(t instanceof IEnergyConnected)c|=4;if(t instanceof cofh.api.energy.IEnergyConnection)c|=8;if(t instanceof IGridHost)c|=16;
        capabilities[s]=c;if(c!=0||t instanceof OwnedTile)m|=1<<s;}if(m!=mask){mask=m;changed();}}
    @Override public void updateEntity(){if(worldObj==null||worldObj.isRemote)return;Networks.INSTANCE.register(this);if(now()%20==0){scan();if(network==null)Networks.INSTANCE.autoBind(this);}if(now()%10==0)worldObj.markBlockForUpdate(xCoord,yCoord,zCoord);}
    @Override public void invalidate(){Networks.INSTANCE.unregister(this);super.invalidate();}
    @Override public void onChunkUnload(){Networks.INSTANCE.unregister(this);super.onChunkUnload();}
    public TileCenter center(){return Networks.INSTANCE.center(this);}
    public boolean ready(){TileCenter c=center();return active()&&c!=null&&c.active();}
    public boolean input(int s){return s>=0&&s<6&&ready()&&modes[s]==IN;}
    public boolean output(int s){return s>=0&&s<6&&ready()&&modes[s]==OUT;}
    public boolean allows(Key k,int side){if(k==null||side<0||side>=6)return false;
        Key f=k.kind==Key.ITEM?itemFilters[side]:k.isFluid()?fluidFilters[side]:null;
        if(k.kind==Key.EU||k.kind==Key.RF)return true;if(f!=null)return f.equals(k);
        TileCenter c=center();return modes[side]!=OUT||c==null||!c.storageMode;
    }
    public long limit(Key k){return k.kind==Key.ITEM?ModConfig.itemBatch:k.isFluid()?ModConfig.fluidRate:k.kind==Key.RF?ModConfig.rfRate:64;}
    private int budgetIndex(Key k){return k.kind==Key.GAS?Key.LIQUID:k.kind;}
    private long budgetTime(Key k){return k.kind==Key.ITEM?now()/5:now();}
    public long remaining(Key k,boolean input){return (input?inbound:outbound)[budgetIndex(k)].remaining(budgetTime(k),limit(k));}
    public void spent(Key k,long n,boolean input){if(n<=0)return;(input?inbound:outbound)[budgetIndex(k)].spend(budgetTime(k),n,limit(k));lastMovement=now();markDirty();}
    public void changedSettings(){Arrays.fill(advertised,null);Arrays.fill(refundCount,0);Arrays.fill(ratingTime,Long.MIN_VALUE);changed();}
    public VoltageProbe.Rating rating(int s){if(s<0||s>=6)return new VoltageProbe.Rating(0,0,"invalid");
        if(ratings[s]==null||ratingTime[s]!=now()){ratings[s]=VoltageProbe.inspect(endpoint(s),ForgeDirection.getOrientation(s).getOpposite());ratingTime[s]=now();}return ratings[s];}
    public long voltage(int s){VoltageProbe.Rating r=rating(s);if(tiers[s]<0)return r.voltage;
        long requested=(8L << (2*Math.max(0,Math.min(14,tiers[s]))));long local=VoltageProbe.cableLimit(endpoint(s));
        long ceiling=r.voltage>0?r.voltage:local;return ceiling>0?Math.min(requested,ceiling):0;}
    public long amperage(int s){VoltageProbe.Rating r=rating(s);long safe=r.amps<=0?1:r.amps;return Math.min(64,Math.min(safe,amps[s]==0?safe:amps[s]));}
    @Override public void retain(Key k,long n){if(n>0){rescue.entries.put(k,dev.chronolink.v2.core.RelayMath.add(rescue.count(k),n));fail("Partial external transfer: known remainder retained");}}
    @Override public void uncertain(Key k,long n,String phase){NBTTagCompound r=k.write();r.setLong("InFlight",n);r.setString("Phase",phase);recovery=r;fail("Uncertain external transaction; no automatic replay");}
    @Override public boolean hasContents(){return !rescue.entries.isEmpty()||recovery!=null;}
    @Override public void writeToNBT(NBTTagCompound n){super.writeToNBT(n);n.setIntArray("Modes",modes);n.setIntArray("Tiers",tiers);n.setIntArray("Amps",amps);n.setTag("Rescue",rescue.write());
        NBTTagList filters=new NBTTagList();for(int s=0;s<6;s++){NBTTagCompound f=new NBTTagCompound();if(itemFilters[s]!=null)f.setTag("Item",itemFilters[s].write());if(fluidFilters[s]!=null)f.setTag("Fluid",fluidFilters[s].write());filters.appendTag(f);}n.setTag("Filters",filters);}
    @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);int[] m=n.getIntArray("Modes"),t=n.getIntArray("Tiers"),a=n.getIntArray("Amps");
        for(int s=0;s<6;s++){modes[s]=s<m.length?Math.max(0,Math.min(2,m[s])):IN;tiers[s]=s<t.length?Math.max(-1,Math.min(14,t[s])):-1;amps[s]=s<a.length?Math.max(0,Math.min(64,a[s])):0;}
        NBTTagList f=n.getTagList("Filters",10);for(int s=0;s<6;s++){NBTTagCompound tag=s<f.tagCount()?f.getCompoundTagAt(s):new NBTTagCompound();itemFilters[s]=tag.hasKey("Item",10)?Key.read(tag.getCompoundTag("Item")):null;fluidFilters[s]=tag.hasKey("Fluid",10)?Key.read(tag.getCompoundTag("Fluid")):null;}
        try{rescue.read(n.getTagList("Rescue",10));}catch(RuntimeException e){recovery=(NBTTagCompound)n.copy();fault="Unknown saved recovery data";enabled=false;}
        if(hasContents())enabled=false;}
    @Override public NBTTagCompound visual(){NBTTagCompound n=super.visual();n.setInteger("Mask",mask);n.setIntArray("Modes",modes);return n;}
    @Override public void readVisual(NBTTagCompound n){super.readVisual(n);mask=n.getInteger("Mask")&63;int[] a=n.getIntArray("Modes");for(int i=0;i<Math.min(6,a.length);i++)modes[i]=a[i];}
    @Override public long injectEnergyUnits(ForgeDirection from,long voltage,long amperage){return from!=null&&input(from.ordinal())?Networks.INSTANCE.offerEU(this,from.ordinal(),voltage,amperage):0;}
    @Override public boolean inputEnergyFrom(ForgeDirection d){return d!=null&&input(d.ordinal());}
    @Override public boolean outputsEnergyTo(ForgeDirection d){return d!=null&&output(d.ordinal());}
    @Override public byte getColorization(){return -1;}
    @Override public byte setColorization(byte c){return -1;}
    @Override public boolean canConnectEnergy(ForgeDirection d){return d!=null&&d.ordinal()<6&&ready()&&modes[d.ordinal()]!=OFF;}
    @Override public int receiveEnergy(ForgeDirection d,int n,boolean sim){return d!=null&&input(d.ordinal())?(int)Networks.INSTANCE.offer(this,d.ordinal(),Key.RF_KEY,n,sim):0;}
    @Override public int extractEnergy(ForgeDirection d,int n,boolean sim){return d!=null&&output(d.ordinal())?(int)Networks.INSTANCE.draw(this,d.ordinal(),Key.RF_KEY,n,sim):0;}
    @Override public int getEnergyStored(ForgeDirection d){return d!=null&&output(d.ordinal())?(int)Networks.INSTANCE.draw(this,d.ordinal(),Key.RF_KEY,Integer.MAX_VALUE,true):0;}
    @Override public int getMaxEnergyStored(ForgeDirection d){return canConnectEnergy(d)?ModConfig.rfRate:0;}
    @Override public boolean canFill(ForgeDirection d,Fluid f){return d!=null&&input(d.ordinal())&&f!=null;}
    @Override public boolean canDrain(ForgeDirection d,Fluid f){return d!=null&&output(d.ordinal())&&(f==null||allows(Key.of(new FluidStack(f,1)),d.ordinal()));}
    @Override public int fill(ForgeDirection d,FluidStack f,boolean doFill){return d!=null&&input(d.ordinal())&&f!=null?(int)Networks.INSTANCE.offer(this,d.ordinal(),Key.of(f),f.amount,!doFill):0;}
    @Override public FluidStack drain(ForgeDirection d,FluidStack f,boolean doDrain){if(d==null||f==null||!output(d.ordinal()))return null;Key k=Key.of(f);long n=Networks.INSTANCE.draw(this,d.ordinal(),k,f.amount,!doDrain);return n>0?k.fluid((int)n):null;}
    @Override public FluidStack drain(ForgeDirection d,int n,boolean doDrain){if(d==null||!output(d.ordinal())||n<=0)return null;Key k=Networks.INSTANCE.first(this,d.ordinal(),false);if(k==null)return null;long a=Networks.INSTANCE.draw(this,d.ordinal(),k,n,!doDrain);return a>0?k.fluid((int)a):null;}
    @Override public FluidTankInfo[] getTankInfo(ForgeDirection d){if(d==null||d.ordinal()>=6||!ready()||modes[d.ordinal()]==OFF)return new FluidTankInfo[0];
        if(input(d.ordinal()))return new FluidTankInfo[]{new FluidTankInfo(null,ModConfig.fluidRate)};Key k=Networks.INSTANCE.first(this,d.ordinal(),false);
        if(k==null)return new FluidTankInfo[]{new FluidTankInfo(null,ModConfig.fluidRate)};int n=(int)Networks.INSTANCE.draw(this,d.ordinal(),k,ModConfig.fluidRate,true);return new FluidTankInfo[]{new FluidTankInfo(k.fluid(n),ModConfig.fluidRate)};}
    @Override public int getSizeInventory(){return 6;}
    @Override public int[] getAccessibleSlotsFromSide(int s){return s>=0&&s<6&&ready()&&modes[s]!=OFF?new int[]{s}:new int[0];}
    @Override public ItemStack getStackInSlot(int s){if(s<0||s>=6||!output(s))return null;Key k=Networks.INSTANCE.first(this,s,true);long n=k==null?0:Networks.INSTANCE.draw(this,s,k,k.item.getMaxStackSize(),true);advertised[s]=n>0?k.item((int)n):null;return advertised[s]==null?null:advertised[s].copy();}
    @Override public boolean canInsertItem(int s,ItemStack st,int face){return s==face&&input(s)&&st!=null&&Networks.INSTANCE.offer(this,s,Key.of(st),Math.min(st.stackSize,st.getMaxStackSize()),true)>=Math.min(st.stackSize,st.getMaxStackSize());}
    @Override public boolean canExtractItem(int s,ItemStack st,int face){return s==face&&output(s)&&st!=null&&allows(Key.of(st),s);}
    @Override public ItemStack decrStackSize(int s,int count){if(s<0||s>=6||!output(s)||count<=0)return null;ItemStack old=advertised[s];if(old==null)old=getStackInSlot(s);if(old==null)return null;Key k=Key.of(old);
        long n=Networks.INSTANCE.draw(this,s,k,Math.min(count,old.stackSize),false);if(n<=0)return null;refundKey[s]=k;refundCount[s]=n;refundTick[s]=now();advertised[s]=old.stackSize>n?k.item((int)(old.stackSize-n)):null;return k.item((int)n);}
    @Override public void setInventorySlotContents(int s,ItemStack st){if(s<0||s>=6||!ready())return;
        if(input(s)){if(st==null)return;Key k=Key.of(st);long got=Networks.INSTANCE.offer(this,s,k,st.stackSize,false);if(got<st.stackSize)retain(k,st.stackSize-got);return;}
        if(!output(s))return;ItemStack before=advertised[s];int old=before==null?0:before.stackSize,next=st==null?0:st.stackSize;
        if(next<old&&(st==null||Key.of(st).equals(Key.of(before)))){long n=Networks.INSTANCE.draw(this,s,Key.of(before),old-next,false);if(n!=old-next)fail("Stale native inventory extraction");advertised[s]=st==null?null:st.copy();}
        else if(next>old&&next-old<=refundCount[s]&&refundCount[s]>0&&refundTick[s]==now()&&refundKey[s].equals(Key.of(st))){long n=Math.min(refundCount[s],next-old);refundCount[s]-=n;long returned=refundStore[s]==null?0:refundStore[s].insert(refundKey[s],n,false);if(returned<n)retain(refundKey[s],n-returned);advertised[s]=st.copy();}
    }
    @Override public ItemStack getStackInSlotOnClosing(int i){return null;}
    @Override public String getInventoryName(){return "chronolink.conduit";}
    @Override public boolean hasCustomInventoryName(){return false;}
    @Override public int getInventoryStackLimit(){return 64;}
    @Override public boolean isUseableByPlayer(EntityPlayer p){return mayEdit(p)&&inRange(p);}
    @Override public void openInventory(){}
    @Override public void closeInventory(){}
    @Override public boolean isItemValidForSlot(int i,ItemStack st){return i>=0&&i<6&&canInsertItem(i,st,i);}
}
