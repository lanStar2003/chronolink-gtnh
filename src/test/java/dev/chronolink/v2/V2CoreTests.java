package dev.chronolink.v2;

import java.util.*;
import dev.chronolink.v2.core.*;

/** Fixtures implement our store contract; not substitute Minecraft or Forge APIs. */
public final class V2CoreTests {
    private static int checks;
    private static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
    private static final class Memory implements Transfer.Store<String> {
        long amount,capacity;int changes;boolean rejectReal;
        Memory(long amount,long capacity){this.amount=amount;this.capacity=capacity;}
        public Object identity(){return this;}
        public long insert(String k,long n,boolean sim){long a=Math.max(0,Math.min(n,capacity-amount));if(!sim){if(rejectReal)a/=2;amount+=a;changes++;}return a;}
        public long extract(String k,long n,boolean sim){long a=Math.max(0,Math.min(n,amount));if(!sim){amount-=a;changes++;}return a;}
    }
    private static final class Rescue implements Transfer.Rescue<String>{long retained,uncertain;public void retain(String k,long n){retained+=n;}public void uncertain(String k,long n,String phase){uncertain+=n;}}
    public static void main(String[] args){
        for(int mask=0;mask<64;mask++){List<double[]> boxes=ConduitGeometry.boxes(mask);check(boxes.size()==1+Integer.bitCount(mask),"shape-"+mask);
            for(double[] b:boxes)for(int axis=0;axis<3;axis++)check(b[axis]>=0&&b[axis+3]<=1&&b[axis]<b[axis+3],"bounded-shape");}
        check(ConduitGeometry.boxes(0).get(0)[0]>.25,"isolated-core-not-full-block");
        check(RelayMath.safeVoltage(512,32,false)==32,"HV-cable-LV-hatch-minimum");
        check(RelayMath.safeVoltage(512,32,true)==0,"unknown-cable-blocks-auto-output");
        check(RelayMath.safeVoltage(0,32,false)==0,"unknown-local-rating");
        check(RelayMath.packets(127,32,8)==3,"whole-EU-packets-only");
        check(RelayMath.packets(Long.MAX_VALUE,8,64)==64,"amp-cap");
        check(RelayMath.product(Long.MAX_VALUE,2)==Long.MAX_VALUE,"saturated-product");
        check(!RelayMath.cardRemovable(1,0),"nonempty-capacity-not-removable");
        check(RelayMath.cardRemovable(65536,65536),"exact-capacity-removable");
        Memory source=new Memory(64,64),full=new Memory(64,64);Rescue r=new Rescue();
        check(Transfer.move(source,full,"item",16,r)==0&&source.amount==64&&source.changes==0,"full-destination-does-not-extract");
        Memory dest=new Memory(0,8);check(Transfer.move(source,dest,"item",16,r)==8&&source.amount==56&&dest.amount==8,"capacity-before-extraction");
        check(Transfer.move(source,source,"item",16,r)==0,"self-route-not-a-transfer");
        Memory flaky=new Memory(0,64);flaky.rejectReal=true;long before=source.amount;
        check(Transfer.move(source,flaky,"item",16,r)==8&&source.amount==before-8&&flaky.amount==8&&r.retained==0,"partial-real-acceptance-is-rolled-back");
        Random random=new Random(28402);for(int i=0;i<20000;i++){long a=random.nextInt(65536),cap=random.nextInt(65536);Memory f=new Memory(a,a),t=new Memory(0,cap);t.rejectReal=random.nextBoolean();Rescue rescue=new Rescue();
            long moved=Transfer.move(f,t,"native-unit",random.nextInt(100000),rescue);
            check(f.amount+t.amount+rescue.retained==a,"random-conservation");check(moved==t.amount&&moved<=cap&&f.amount>=0,"random-bounds");}
        try{Transfer.checked(-1,5);throw new AssertionError("negative accepted");}catch(IllegalStateException expected){checks++;}
        try{Transfer.checked(6,5);throw new AssertionError("excess accepted");}catch(IllegalStateException expected){checks++;}
        System.out.println("PASS V2 geometry, local no-buffer transaction, rollback, voltage and capacity policies: "+checks+" assertions. No game startup claim.");
    }
}
