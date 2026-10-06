package dev.chronolink.v2.store;

import java.util.LinkedHashMap;
import java.util.Map;
import dev.chronolink.v2.core.RelayMath;

/** Actual successful transfers only. Twenty-tick rolling buckets, never simulated throughput. */
public final class Meter {
    public final Map<Key,long[][]> perResource=new LinkedHashMap<Key,long[][]>();
    private final long[][][] totals=new long[5][2][20];
    private long tick=Long.MIN_VALUE;
    public void advance(long now){if(tick==Long.MIN_VALUE||now<tick||now-tick>=20){clear();tick=now;return;}
        while(tick<now){tick++;int b=(int)Math.floorMod(tick,20);for(int k=0;k<5;k++)for(int d=0;d<2;d++)totals[k][d][b]=0;
            java.util.Iterator<long[][]> it=perResource.values().iterator();while(it.hasNext()){long[][] a=it.next();a[0][b]=0;a[1][b]=0;boolean live=false;for(int d=0;d<2;d++)for(long x:a[d])if(x!=0)live=true;if(!live)it.remove();}}}
    private void clear(){for(int k=0;k<5;k++)for(int d=0;d<2;d++)java.util.Arrays.fill(totals[k][d],0);perResource.clear();}
    public void moved(Key k,long n,boolean input,long now){if(n<=0)return;advance(now);int d=input?0:1,b=(int)Math.floorMod(now,20);
        totals[k.kind][d][b]=RelayMath.add(totals[k.kind][d][b],n);
        if(!perResource.containsKey(k))perResource.put(k,new long[2][20]);long[][] a=perResource.get(k);a[d][b]=RelayMath.add(a[d][b],n);}
    public long rate(int k,int d){long n=0;for(long x:totals[k][d])n=RelayMath.add(n,x);return n;}
    public long rate(Key k,int d){long[][] a=perResource.get(k);long n=0;if(a!=null)for(long x:a[d])n=RelayMath.add(n,x);return n;}
}
