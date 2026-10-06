package dev.chronolink.transfer;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.*;
import cofh.api.energy.IEnergyProvider;
import cofh.api.energy.IEnergyReceiver;
import gregtech.api.interfaces.tileentity.IEnergyConnected;
import dev.chronolink.ModConfig;
import dev.chronolink.core.Amounts;
import dev.chronolink.core.ResourceKind;
import dev.chronolink.tile.TileConnector;

/** Calls only public inventory/fluid/energy APIs. Unknown responses quarantine rather than retry. */
public final class BoundaryTransfer {
    private BoundaryTransfer() {}
    private static final Map<Class<?>,Method> voltageReaders=new ConcurrentHashMap<Class<?>,Method>();
    private static final java.util.Set<Class<?>> noVoltageReader=java.util.Collections.newSetFromMap(new ConcurrentHashMap<Class<?>,Boolean>());
    public static void tick(TileConnector n) {
        TileEntity raw=n.neighbor();
        if(raw==null){n.status=2;return;}
        ForgeDirection side=n.neighborFace();
        switch(n.settings.resource) {
            case ITEM:
                if(!(raw instanceof IInventory)){n.status=3;return;}
                if(n.now()%5==0) {
                    if(n.settings.importing) pullItem(n,(IInventory)raw,side.ordinal());
                    else pushItem(n,(IInventory)raw,side.ordinal());
                }
                break;
            case FLUID:
                if(!(raw instanceof IFluidHandler)){n.status=3;return;}
                if(n.settings.importing)pullFluid(n,(IFluidHandler)raw,side);else pushFluid(n,(IFluidHandler)raw,side);
                break;
            case RF:
                if(n.settings.importing && raw instanceof IEnergyProvider)pullRF(n,(IEnergyProvider)raw,side);
                else if(!n.settings.importing && raw instanceof IEnergyReceiver)pushRF(n,(IEnergyReceiver)raw,side);
                else n.status=3;
                break;
            case EU:
                if(!n.settings.importing)pushEU(n,raw,side);
                // Input deliberately waits for a native GT source to offer packets.
                break;
            default:break;
        }
    }
    private static int[] slots(IInventory inventory,int side) {
        if(inventory instanceof ISidedInventory) {
            int[] result=((ISidedInventory)inventory).getAccessibleSlotsFromSide(side);
            return result==null?new int[0]:result;
        }
        int size=Math.max(0,Math.min(65536,inventory.getSizeInventory()));
        int[] result=new int[size];for(int i=0;i<size;i++)result[i]=i;return result;
    }
    private static boolean valid(IInventory inv,int slot) {return slot>=0 && slot<inv.getSizeInventory();}
    private static void pullItem(TileConnector n,IInventory inv,int side) {
        if(n.itemBuffer!=null)return;
        int[] slots=slots(inv,side);if(slots.length==0)return;
        int start=(n.inventoryCursor & Integer.MAX_VALUE)%slots.length;
        int checked=Math.min(slots.length,128);
        n.inventoryCursor=(start+checked)%slots.length;
        for(int i=0;i<checked;i++) {
            int slot=slots[(start+i)%slots.length];if(!valid(inv,slot))continue;
            ItemStack seen=inv.getStackInSlot(slot);
            if(seen==null || seen.stackSize<=0 || !n.matches(seen))continue;
            if(inv instanceof ISidedInventory && !((ISidedInventory)inv).canExtractItem(slot,seen,side))continue;
            int request=Math.min(Math.min(ModConfig.itemBatch,seen.stackSize),seen.getMaxStackSize());
            ItemStack actual=inv.decrStackSize(slot,request);
            if(actual==null || actual.stackSize<=0)return;
            n.itemBuffer=actual.copy();n.moved(actual.stackSize);inv.markDirty();
            if(actual.stackSize>request || !n.matches(actual))n.quarantine("inventory extraction violated preview");
            n.inventoryCursor=(start+i+1)%slots.length;return;
        }
    }
    private static void pushItem(TileConnector n,IInventory inv,int side) {
        if(n.itemBuffer==null)return;
        int[] slots=slots(inv,side);if(slots.length==0)return;
        int start=(n.inventoryCursor & Integer.MAX_VALUE)%slots.length;
        int checked=Math.min(slots.length,128);
        n.inventoryCursor=(start+checked)%slots.length;
        for(int i=0;i<checked && n.itemBuffer!=null;i++) {
            int slot=slots[(start+i)%slots.length];if(!valid(inv,slot))continue;
            ItemStack carried=n.itemBuffer;
            if(!inv.isItemValidForSlot(slot,carried))continue;
            if(inv instanceof ISidedInventory && !((ISidedInventory)inv).canInsertItem(slot,carried,side))continue;
            ItemStack old=inv.getStackInSlot(slot);
            if(old!=null && !TileConnector.sameItem(old,carried))continue;
            int room=Amounts.slotRoom(inv.getInventoryStackLimit(),carried.getMaxStackSize(),old==null?0:old.stackSize);
            int moved=Math.min(carried.stackSize,room);if(moved<=0)continue;
            ItemStack replacement=old==null?carried.copy():old.copy();replacement.stackSize=(old==null?0:old.stackSize)+moved;
            // Generic IInventory has no transactional insert. After a throwing write the caller quarantines.
            int before=old==null?0:old.stackSize;
            inv.setInventorySlotContents(slot,replacement);
            ItemStack readBack=inv.getStackInSlot(slot);
            if(readBack==null || !TileConnector.sameItem(readBack,carried)
                || readBack.stackSize<before || readBack.stackSize>before+moved) {
                n.quarantine("inventory insert result cannot be verified");return;
            }
            int accepted=readBack.stackSize-before;
            carried.stackSize-=accepted;if(carried.stackSize<=0)n.itemBuffer=null;
            n.moved(accepted);inv.markDirty();
        }
        if(n.itemBuffer!=null)n.status=4;
    }
    private static void pullFluid(TileConnector n,IFluidHandler inv,ForgeDirection side) {
        if(n.fluidBuffer!=null)return;
        int room=(int)n.fluidIn.remaining(n.now(),ModConfig.fluidRate);if(room<=0)return;
        FluidStack filter=n.filterFluid();
        if(n.filter!=null && filter==null){n.status=4;return;}
        FluidStack preview;
        if(filter!=null){filter=filter.copy();filter.amount=room;preview=inv.drain(side,filter,false);}
        else preview=inv.drain(side,room,false);
        if(preview==null || preview.amount<=0 || !n.matches(preview) || !inv.canDrain(side,preview.getFluid()))return;
        preview=preview.copy();preview.amount=Math.min(preview.amount,room);
        FluidStack actual=inv.drain(side,preview,true);
        if(actual==null || actual.amount<=0)return;
        n.fluidBuffer=actual.copy();n.fluidIn.spend(n.now(),actual.amount,ModConfig.fluidRate);n.moved(actual.amount);
        if(actual.amount>preview.amount || !actual.isFluidEqual(preview))n.quarantine("fluid extraction violated preview");
    }
    private static void pushFluid(TileConnector n,IFluidHandler inv,ForgeDirection side) {
        if(n.fluidBuffer==null)return;
        int max=(int)Math.min(n.fluidBuffer.amount,n.fluidOut.remaining(n.now(),ModConfig.fluidRate));if(max<=0)return;
        if(!inv.canFill(side,n.fluidBuffer.getFluid())){n.status=4;return;}
        FluidStack offer=n.fluidBuffer.copy();offer.amount=max;
        int preview=inv.fill(side,offer.copy(),false);
        if(preview<0 || preview>max){n.quarantine("invalid fluid simulation result");return;}
        if(preview==0){n.status=4;return;}
        offer.amount=preview;int actual=inv.fill(side,offer.copy(),true);
        if(actual<0 || actual>preview){n.quarantine("uncertain fluid delivery result");return;}
        n.fluidBuffer.amount-=actual;if(n.fluidBuffer.amount==0)n.fluidBuffer=null;
        n.fluidOut.spend(n.now(),actual,ModConfig.fluidRate);n.moved(actual);
    }
    private static void pullRF(TileConnector n,IEnergyProvider provider,ForgeDirection side) {
        if(!provider.canConnectEnergy(side))return;
        int wanted=(int)Math.min(Math.max(0,TileConnector.RF_CAPACITY-n.rf.amount()),n.rfIn.remaining(n.now(),ModConfig.rfRate));
        if(wanted<=0)return;
        int preview=provider.extractEnergy(side,wanted,true);
        if(preview<0 || preview>wanted){n.quarantine("invalid RF extraction simulation");return;}
        if(preview==0)return;
        int actual=provider.extractEnergy(side,preview,false);
        if(actual>0){n.rf.insert(actual,false);n.rfIn.spend(n.now(),actual,ModConfig.rfRate);n.moved(actual);}
        if(actual<0 || actual>preview)n.quarantine("uncertain RF extraction result");
    }
    private static void pushRF(TileConnector n,IEnergyReceiver receiver,ForgeDirection side) {
        if(!receiver.canConnectEnergy(side))return;
        int offer=(int)Math.min(n.rf.amount(),n.rfOut.remaining(n.now(),ModConfig.rfRate));if(offer<=0)return;
        int preview=receiver.receiveEnergy(side,offer,true);
        if(preview<0 || preview>offer){n.quarantine("invalid RF delivery simulation");return;}
        if(preview==0){n.status=4;return;}
        int actual=receiver.receiveEnergy(side,preview,false);
        if(actual<0 || actual>preview){n.quarantine("uncertain RF delivery result");return;}
        n.rf.extract(actual,false);n.rfOut.spend(n.now(),actual,ModConfig.rfRate);n.moved(actual);
    }
    private static long ratedVoltage(TileEntity target) {
        Class<?> type=target.getClass();
        if(noVoltageReader.contains(type))return -1;
        try {
            Method reader=voltageReaders.get(type);
            if(reader==null){reader=type.getMethod("getInputVoltage");voltageReaders.put(type,reader);}
            Object value=reader.invoke(target);
            return value instanceof Number?((Number)value).longValue():-1;
        } catch(ReflectiveOperationException ex) { noVoltageReader.add(type);return -1; }
    }
    private static void pushEU(TileConnector n,TileEntity raw,ForgeDirection side) {
        if(!(raw instanceof IEnergyConnected)){n.status=3;return;}
        IEnergyConnected target=(IEnergyConnected)raw;
        if(!target.inputEnergyFrom(side)){n.status=4;return;}
        long voltage=n.settings.voltage(),rating=ratedVoltage(raw);
        if(rating>=0 && voltage>rating){n.status=5;return;}
        if(rating<0 && !ModConfig.allowUnratedEUOutputs){n.status=6;return;}
        long offered=Math.min(n.eu.amount()/voltage,n.euOut.remaining(n.now(),n.settings.amps));
        if(offered<=0)return;
        long accepted=target.injectEnergyUnits(side,voltage,offered);
        if(accepted<0 || accepted>offered){n.quarantine("uncertain GT EU delivery result");return;}
        long actual=voltage*accepted;
        n.eu.extract(actual,false);n.euOut.spend(n.now(),accepted,n.settings.amps);n.moved(actual);
    }
}
