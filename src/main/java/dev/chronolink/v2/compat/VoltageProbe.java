package dev.chronolink.v2.compat;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import gregtech.api.interfaces.tileentity.IBasicEnergyContainer;
import gregtech.api.interfaces.tileentity.IEnergyConnected;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTECable;
import dev.chronolink.v2.tile.TileConduit;

/** Conservative read-only rating probe. Unknown/unloaded branches never get an automatic voltage. */
public final class VoltageProbe {
    private VoltageProbe(){}
    public static final class Rating {
        public final long voltage,amps;public final String reason;
        public Rating(long v,long a,String r){voltage=v;amps=a;reason=r;}
    }
    private static MTECable cable(TileEntity t){if(t instanceof IGregTechTileEntity && ((IGregTechTileEntity)t).getMetaTileEntity() instanceof MTECable)return (MTECable)((IGregTechTileEntity)t).getMetaTileEntity();return null;}
    private static boolean connected(TileEntity tile,ForgeDirection side){
        Object meta=tile instanceof IGregTechTileEntity?((IGregTechTileEntity)tile).getMetaTileEntity():null;
        return meta instanceof gregtech.api.interfaces.metatileentity.IConnectable && ((gregtech.api.interfaces.metatileentity.IConnectable)meta).isConnectedAtSide(side);
    }
    public static Rating inspect(TileEntity target,ForgeDirection side){
        if(!(target instanceof IEnergyConnected)||!((IEnergyConnected)target).inputEnergyFrom(side))return new Rating(0,0,"not-input");
        MTECable first=cable(target);
        if(first==null){if(target instanceof IBasicEnergyContainer){IBasicEnergyContainer e=(IBasicEnergyContainer)target;return new Rating(Math.max(0,e.getInputVoltage()),Math.max(0,e.getInputAmperage()),"hatch");}return new Rating(0,0,"unknown");}
        long voltage=first.mVoltage,amps=first.mAmperage;boolean consumer=false,unknown=false;
        ArrayDeque<TileEntity> queue=new ArrayDeque<TileEntity>();Set<TileEntity> seen=new HashSet<TileEntity>();queue.add(target);
        while(!queue.isEmpty()){
            TileEntity t=queue.remove();if(!seen.add(t))continue;if(seen.size()>256){unknown=true;break;}
            MTECable c=cable(t);if(c==null)continue;voltage=Math.min(voltage,c.mVoltage);amps=Math.min(amps,c.mAmperage);
            World w=t.getWorldObj();if(w==null){unknown=true;break;}
            for(ForgeDirection d:ForgeDirection.VALID_DIRECTIONS){if(!connected(t,d))continue;int x=t.xCoord+d.offsetX,y=t.yCoord+d.offsetY,z=t.zCoord+d.offsetZ;
                if(y<0||y>=w.getHeight())continue;if(!w.blockExists(x,y,z)){unknown=true;continue;}TileEntity n=w.getTileEntity(x,y,z);
                if(n instanceof TileConduit)continue;if(cable(n)!=null){if(connected(n,d.getOpposite()))queue.add(n);continue;}
                if(n instanceof IEnergyConnected && ((IEnergyConnected)n).inputEnergyFrom(d.getOpposite())){
                    if(n instanceof IBasicEnergyContainer){long v=((IBasicEnergyContainer)n).getInputVoltage();if(v>0){consumer=true;voltage=Math.min(voltage,v);}else unknown=true;}
                    else unknown=true;
                }
            }
        }
        return new Rating(!unknown&&consumer?Math.max(0,voltage):0,Math.max(0,amps),unknown?"unverified-cable":consumer?"cable-minimum":"no-consumer");
    }
    public static long cableLimit(TileEntity target){MTECable c=cable(target);return c==null?0:c.mVoltage;}
}
