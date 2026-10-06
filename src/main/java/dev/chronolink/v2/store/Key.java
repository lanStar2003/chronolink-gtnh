package dev.chronolink.v2.store;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

/** Resource identity excludes quantity and includes metadata and complete NBT. */
public final class Key {
    public static final int ITEM=0,LIQUID=1,GAS=2,EU=3,RF=4;
    public static final Key EU_KEY=new Key(EU,null,null),RF_KEY=new Key(RF,null,null);
    public final int kind;
    public final ItemStack item;
    public final FluidStack fluid;
    private Key(int k,ItemStack i,FluidStack f){kind=k;item=i;fluid=f;}
    public static Key of(ItemStack s){if(s==null||s.getItem()==null)return null;ItemStack c=s.copy();c.stackSize=1;return new Key(ITEM,c,null);}
    public static Key of(FluidStack s){if(s==null||s.getFluid()==null)return null;FluidStack c=s.copy();c.amount=1;return new Key(c.getFluid().isGaseous()?GAS:LIQUID,null,c);}
    public ItemStack item(int n){ItemStack c=item.copy();c.stackSize=n;return c;}
    public FluidStack fluid(int n){FluidStack c=fluid.copy();c.amount=n;return c;}
    public String label(){return kind==ITEM?item.getDisplayName():fluid!=null?fluid.getLocalizedName():kind==EU?"EU":"RF";}
    public boolean isFluid(){return kind==LIQUID||kind==GAS;}
    @Override public boolean equals(Object obj){if(!(obj instanceof Key))return false;Key b=(Key)obj;
        if(kind!=b.kind)return false;
        return kind==ITEM?item.isItemEqual(b.item)&&ItemStack.areItemStackTagsEqual(item,b.item):isFluid()?fluid.isFluidEqual(b.fluid):true;}
    @Override public int hashCode(){return kind==ITEM?31*(31*Item.getIdFromItem(item.getItem())+item.getItemDamage())+(item.stackTagCompound==null?0:item.stackTagCompound.hashCode()):isFluid()?31*fluid.getFluid().getName().hashCode()+(fluid.tag==null?0:fluid.tag.hashCode()):kind;}
    public NBTTagCompound write(){NBTTagCompound n=new NBTTagCompound();n.setInteger("k",kind);
        if(item!=null)n.setTag("i",item.writeToNBT(new NBTTagCompound()));if(fluid!=null)n.setTag("f",fluid.writeToNBT(new NBTTagCompound()));return n;}
    public static Key read(NBTTagCompound n){int k=n.getInteger("k");if(k==ITEM)return of(ItemStack.loadItemStackFromNBT(n.getCompoundTag("i")));
        if(k==LIQUID||k==GAS)return of(FluidStack.loadFluidStackFromNBT(n.getCompoundTag("f")));return k==EU?EU_KEY:k==RF?RF_KEY:null;}
}
