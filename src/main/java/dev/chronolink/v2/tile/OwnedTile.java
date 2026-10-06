package dev.chronolink.v2.tile;

import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import dev.chronolink.ChronoLink;

public abstract class OwnedTile extends TileEntity {
    public UUID owner,network;
    public boolean enabled=true;
    public String fault="";
    public NBTTagCompound recovery,view=new NBTTagCompound();
    public long lastMovement=Long.MIN_VALUE;
    public long now(){return worldObj==null?0:worldObj.getTotalWorldTime();}
    public boolean loaded(){return worldObj!=null&&!worldObj.isRemote&&!isInvalid()&&worldObj.blockExists(xCoord,yCoord,zCoord)&&worldObj.getTileEntity(xCoord,yCoord,zCoord)==this;}
    public boolean active(){return loaded()&&owner!=null&&network!=null&&enabled&&fault.isEmpty();}
    public boolean mayEdit(EntityPlayer p){return p!=null&&owner!=null&&owner.equals(p.getUniqueID());}
    public boolean inRange(EntityPlayer p){return p!=null&&p.getDistanceSq(xCoord+.5,yCoord+.5,zCoord+.5)<=64;}
    public boolean flow(){return lastMovement!=Long.MIN_VALUE&&now()>=lastMovement&&now()-lastMovement<12;}
    public void changed(){markDirty();if(worldObj!=null&&!worldObj.isRemote)worldObj.markBlockForUpdate(xCoord,yCoord,zCoord);}
    public void fail(String reason){enabled=false;fault=reason==null?"unknown":reason.substring(0,Math.min(120,reason.length()));changed();
        if(ChronoLink.log!=null)ChronoLink.log.error("ChronoLink v2 quarantined {} {},{},{}: {}",getClass().getSimpleName(),xCoord,yCoord,zCoord,fault);}
    protected static UUID uuid(String s){try{return s==null||s.isEmpty()?null:UUID.fromString(s);}catch(IllegalArgumentException e){return null;}}
    @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);owner=uuid(n.getString("Owner"));network=uuid(n.getString("Network"));enabled=!n.hasKey("Enabled")||n.getBoolean("Enabled");fault=n.getString("Fault");
        recovery=n.hasKey("Recovery",10)?(NBTTagCompound)n.getCompoundTag("Recovery").copy():null;if(recovery!=null)enabled=false;}
    @Override public void writeToNBT(NBTTagCompound n){super.writeToNBT(n);n.setInteger("Schema",2);if(owner!=null)n.setString("Owner",owner.toString());if(network!=null)n.setString("Network",network.toString());n.setBoolean("Enabled",enabled);n.setString("Fault",fault);if(recovery!=null)n.setTag("Recovery",recovery.copy());}
    public abstract boolean hasContents();
    public void placed(EntityPlayer p,ItemStack stack){if(stack.hasTagCompound()&&stack.getTagCompound().hasKey("NetworkData",10)){
            int x=xCoord,y=yCoord,z=zCoord;readFromNBT(stack.getTagCompound().getCompoundTag("NetworkData"));xCoord=x;yCoord=y;zCoord=z;enabled=false;
        }else{owner=p.getUniqueID();enabled=true;}changed();}
    public ItemStack pack(ItemStack stack){NBTTagCompound tag=new NBTTagCompound(),data=new NBTTagCompound();writeToNBT(data);data.removeTag("x");data.removeTag("y");data.removeTag("z");tag.setTag("NetworkData",data);stack.setTagCompound(tag);return stack;}
    public NBTTagCompound visual(){NBTTagCompound n=new NBTTagCompound();n.setBoolean("Flow",flow());n.setBoolean("Enabled",enabled);return n;}
    public void readVisual(NBTTagCompound n){lastMovement=n.getBoolean("Flow")?now():Long.MIN_VALUE;enabled=n.getBoolean("Enabled");}
    @Override public Packet getDescriptionPacket(){return new S35PacketUpdateTileEntity(xCoord,yCoord,zCoord,0,visual());}
    @Override public void onDataPacket(NetworkManager manager,S35PacketUpdateTileEntity packet){readVisual(packet.func_148857_g());if(worldObj!=null)worldObj.markBlockForUpdate(xCoord,yCoord,zCoord);}
}
