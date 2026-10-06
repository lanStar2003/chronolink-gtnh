package dev.chronolink.v2.store;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import dev.chronolink.v2.core.RelayMath;

/** Real capacity-card inventory. Cards live in the center; EU is NEVER stored here. */
public final class Stock {
    public final Map<Key,Long> entries=new LinkedHashMap<Key,Long>();
    public long count(Key k){Long n=entries.get(k);return n==null?0:n;}
    public long total(int slot){long n=0;for(Map.Entry<Key,Long> e:entries.entrySet())if(slot(e.getKey())==slot)n=RelayMath.add(n,e.getValue());return n;}
    public int types(int slot){int n=0;for(Key k:entries.keySet())if(slot(k)==slot)n++;return n;}
    public static int slot(Key k){return k.kind==Key.ITEM?0:k.isFluid()?1:k.kind==Key.RF?2:-1;}
    public long insert(Key k,long n,long capacity,int typeLimit,boolean simulate){int s=slot(k);if(s<0||n<=0)return 0;
        if(!entries.containsKey(k)&&types(s)>=typeLimit)return 0;
        long accepted=Math.max(0,Math.min(n,capacity-total(s)));if(!simulate&&accepted>0)entries.put(k,count(k)+accepted);return accepted;}
    public long extract(Key k,long n,boolean simulate){long a=Math.max(0,Math.min(n,count(k)));if(!simulate&&a>0){long left=count(k)-a;if(left==0)entries.remove(k);else entries.put(k,left);}return a;}
    public NBTTagList write(){NBTTagList list=new NBTTagList();for(Map.Entry<Key,Long> e:entries.entrySet()){NBTTagCompound n=e.getKey().write();n.setLong("count",e.getValue());list.appendTag(n);}return list;}
    public void read(NBTTagList list){entries.clear();if(list.tagCount()>2048)throw new IllegalArgumentException("Oversized card inventory");
        for(int i=0;i<list.tagCount();i++){NBTTagCompound n=list.getCompoundTagAt(i);Key k=Key.read(n);long v=n.getLong("count");
            if(k==null||slot(k)<0||v<=0||entries.containsKey(k))throw new IllegalArgumentException("Unknown, duplicate or invalid stored resource");entries.put(k,v);}}
}
