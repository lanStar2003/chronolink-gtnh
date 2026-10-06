package dev.chronolink.gui;

import java.util.Arrays;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import dev.chronolink.core.NodeSettings;
import dev.chronolink.core.ResourceKind;
import dev.chronolink.tile.TileConnector;

/** Uses vanilla container button packets. Client never submits an owner UUID or resource balance. */
public final class ContainerConnector extends Container {
    public final TileConnector tile;
    public boolean clientHasPayload,clientFault;
    private final InventoryBasic ghost=new InventoryBasic("filter",true,1);
    private final int[] last=new int[14];
    private long lastAction=Long.MIN_VALUE;
    public ContainerConnector(InventoryPlayer inventory,TileConnector tile) {
        this.tile=tile;Arrays.fill(last,Integer.MIN_VALUE);
        addSlotToContainer(new Slot(ghost,0,124,94) {
            @Override public boolean isItemValid(ItemStack s) { return false; }
            @Override public boolean canTakeStack(EntityPlayer p) { return false; }
        });
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)
            addSlotToContainer(new Slot(inventory,col+row*9+9,34+col*18,154+row*18));
        for(int col=0;col<9;col++)addSlotToContainer(new Slot(inventory,col,34+col*18,212));
    }
    @Override public boolean canInteractWith(EntityPlayer player) {
        if(tile.getWorldObj()==null || !tile.inRange(player))return false;
        return tile.getWorldObj().isRemote || (tile.isLoaded() && tile.mayEdit(player));
    }
    @Override public boolean enchantItem(EntityPlayer player,int action) {
        if(tile.getWorldObj().isRemote || !canInteractWith(player) || tile.hasFault() || player.openContainer!=this)return false;
        if(tile.now()==lastAction)return false;
        lastAction=tile.now();
        if(!tile.settings.apply(action,tile.hasPayload()))return false;
        tile.changed();detectAndSendChanges();return true;
    }
    @Override public ItemStack slotClick(int slot,int button,int mode,EntityPlayer player) {
        if(slot==0) {
            if(!tile.getWorldObj().isRemote && canInteractWith(player) && player.openContainer==this && mode==0 && (button==0 || button==1)) {
                // Ghost filter copies the authoritative server cursor; it never consumes or returns a real item.
                tile.setFilter(button==1?null:player.inventory.getItemStack());
                detectAndSendChanges();
            }
            return null;
        }
        return super.slotClick(slot,button,mode,player);
    }
    @Override public ItemStack transferStackInSlot(EntityPlayer player,int index) {
        if(index<=0 || index>=inventorySlots.size() || !canInteractWith(player))return null;
        Slot slot=(Slot)inventorySlots.get(index);if(!slot.getHasStack())return null;
        ItemStack stack=slot.getStack(),copy=stack.copy();
        if(index<28){if(!mergeItemStack(stack,28,37,false))return null;}
        else if(!mergeItemStack(stack,1,28,false))return null;
        if(stack.stackSize==0)slot.putStack(null);else slot.onSlotChanged();
        return copy;
    }
    @Override public boolean canDragIntoSlot(Slot slot) { return slot.slotNumber!=0 && super.canDragIntoSlot(slot); }
    private int[] snapshot() {
        long amount=tile.available();
        return new int[]{tile.settings.channel,tile.settings.importing?1:0,tile.settings.resource.ordinal(),tile.settings.face,
            tile.settings.enabled?1:0,tile.settings.tier,tile.settings.amps,tile.hasPayload()?1:0,tile.hasFault()?1:0,tile.status,
            (int)(amount&65535),(int)((amount>>>16)&65535),(int)((amount>>>32)&65535),(int)((amount>>>48)&65535)};
    }
    @Override public void addCraftingToCrafters(ICrafting listener) {
        super.addCraftingToCrafters(listener);
        int[] values=snapshot();for(int i=0;i<values.length;i++)listener.sendProgressBarUpdate(this,i,values[i]);
    }
    @Override public void detectAndSendChanges() {
        if(!tile.getWorldObj().isRemote)ghost.setInventorySlotContents(0,tile.filter==null?null:tile.filter.copy());
        super.detectAndSendChanges();
        if(tile.getWorldObj().isRemote)return;
        int[] values=snapshot();
        for(int i=0;i<values.length;i++)if(values[i]!=last[i]) {
            for(Object obj:crafters)((ICrafting)obj).sendProgressBarUpdate(this,i,values[i]);
            last[i]=values[i];
        }
    }
    @Override public void updateProgressBar(int id,int value) {
        int n=value&65535;
        switch(id) {
            case 0:tile.settings.channel=NodeSettings.clamp(n,1,64);break;
            case 1:tile.settings.importing=n==1;break;
            case 2:tile.settings.resource=ResourceKind.safe(n);break;
            case 3:tile.settings.face=NodeSettings.clamp(n,0,5);break;
            case 4:tile.settings.enabled=n==1;break;
            case 5:tile.settings.tier=NodeSettings.clamp(n,0,14);break;
            case 6:tile.settings.amps=NodeSettings.clamp(n,1,16);break;
            case 7:clientHasPayload=n==1;break;
            case 8:clientFault=n==1;break;
            case 9:tile.status=n;break;
            default:
                if(id>=10 && id<=13) {
                    int shift=(id-10)*16;long mask=65535L<<shift;
                    tile.displayAmount=(tile.displayAmount&~mask)|((long)n<<shift);
                }
        }
    }
}
