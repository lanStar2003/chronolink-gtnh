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
        uiChecks();
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
        SelectionRevision<String> revision=new SelectionRevision<String>();
        int firstRevision=revision.update("face0/items/page0",Arrays.asList("iron","gold"));
        for(int i=0;i<200;i++)check(revision.update("face0/items/page0",Arrays.asList("iron","gold"))==firstRevision,"live-count-refresh-does-not-invalidate-selection");
        check(revision.accepts(firstRevision),"unchanged-row-selection-accepted");
        revision.update("face0/items/page0",Arrays.asList("gold","iron"));check(!revision.accepts(firstRevision),"reordered-row-selection-rejected");
        int beforeFace=revision.update("face0/items/page0",Arrays.asList("iron"));
        revision.update("face1/items/page0",Arrays.asList("iron"));check(!revision.accepts(beforeFace),"late-click-never-changes-new-face");
        int beforeSearch=revision.update("face1/items/page0",Arrays.asList("iron"));
        revision.update("face1/items/search",Arrays.asList("iron"));check(!revision.accepts(beforeSearch),"new-search-invalidates-old-selection");
        Random random=new Random(28402);for(int i=0;i<20000;i++){long a=random.nextInt(65536),cap=random.nextInt(65536);Memory f=new Memory(a,a),t=new Memory(0,cap);t.rejectReal=random.nextBoolean();Rescue rescue=new Rescue();
            long moved=Transfer.move(f,t,"native-unit",random.nextInt(100000),rescue);
            check(f.amount+t.amount+rescue.retained==a,"random-conservation");check(moved==t.amount&&moved<=cap&&f.amount>=0,"random-bounds");}
        try{Transfer.checked(-1,5);throw new AssertionError("negative accepted");}catch(IllegalStateException expected){checks++;}
        try{Transfer.checked(6,5);throw new AssertionError("excess accepted");}catch(IllegalStateException expected){checks++;}
        System.out.println("PASS V2 geometry, local no-buffer transaction, rollback, voltage and capacity policies: "+checks+" assertions. No game startup claim.");
    }
    private static void uiChecks(){int start=checks;
        check(RateDisplay.rate(8192L*20,3,false).equals("8,192"),"IV 1A sustained rate is 8192 EU/t, not 163840");
        check(RateDisplay.rate(8192L*4*20,3,false).equals("32,768"),"IV 4A throughput");
        check(RateDisplay.rate(8192L*20,3,true).equals("8,192"),"common GT rate not rounded to 8.2k");
        check(RateDisplay.rate(1,3,false).equals("0.05"),"fractional average not truncated to zero");
        check(RateDisplay.rate(20,4,false).equals("1"),"RF uses per tick too");
        check(RateDisplay.rate(200,0,false).equals("200")&&RateDisplay.rate(200,1,false).equals("200"),"item and fluid per second unchanged");
        check(RateDisplay.amount("163840",false).equals("163,840"),"stored EU not divided by twenty");
        check(RateDisplay.capacity(8192,4).equals("32,768"),"V times A maximum separate from actual flow");
        check(RateDisplay.unit(3).equals("EU/t")&&RateDisplay.stockUnit(3).equals("EU"),"flow and stored units distinct");
        check(RateDisplay.perTick(Long.MAX_VALUE).multiply(java.math.BigDecimal.valueOf(20)).compareTo(java.math.BigDecimal.valueOf(Long.MAX_VALUE))==0,"long rate conversion is exact and overflow safe");
        check(RateDisplay.amount("10000000000000000000000000000000000000000",true).length()<12,"huge wireless balance stays within compact column");
        check(RateDisplay.rate(0,3,false).equals("0")&&RateDisplay.rate(-1,3,false).equals("0"),"idle and negative rates safe");
        int[] modes={0,1,2,0,1,2};
        for(int face=0;face<6;face++)for(int mode=0;mode<3;mode++){
            int command=SideConfiguration.action(face,mode);check(SideConfiguration.isSideAction(command)&&SideConfiguration.face(command)==face&&SideConfiguration.mode(command)==mode,"side mode identity round trip");
            int[] next=modes.clone();check(SideConfiguration.apply(next,command)&&next[face]==mode,"one packet sets exact physical face");
            for(int other=0;other<6;other++)if(other!=face)check(next[other]==modes[other],"painting preserves other faces");
            check(SideConfiguration.apply(next,command)&&next[face]==mode,"paint command is idempotent");
        }
        for(int face=0;face<6;face++){
            int[] next={0,0,0,0,0,0};int forward=SideConfiguration.cycleAction(face,false),reverse=SideConfiguration.cycleAction(face,true);
            check(SideConfiguration.apply(next,forward)&&next[face]==1,"left click starts input");
            check(SideConfiguration.apply(next,forward)&&next[face]==2,"queued double click cycles server state");
            check(SideConfiguration.apply(next,reverse)&&next[face]==1,"right click reverse cycle");
            int[] middle=SideConfiguration.center(face);check(SideConfiguration.hit(middle[0],middle[1])==face,"six visible targets hit correct Forge side");
            for(int[] point:SideConfiguration.polygon(face))check(point[0]+SideConfiguration.ORIGIN_X>=198&&point[0]+SideConfiguration.ORIGIN_X<=304&&point[1]+SideConfiguration.ORIGIN_Y>=60&&point[1]+SideConfiguration.ORIGIN_Y<=140,"face widget stays above inventory and inside panel");
        }
        check(SideConfiguration.hit(0,0)==-1&&SideConfiguration.hit(50,61)==-1&&SideConfiguration.hit(120,90)==-1,"empty corners do not change any side");
        int count=0;for(int id=0;id<400;id++)if(SideConfiguration.isSideAction(id))count++;check(count==30,"only thirty explicit side commands permitted");
        for(int bad:new int[]{-1,299,318,319,326,329,336,999,Integer.MAX_VALUE})check(!SideConfiguration.apply(modes.clone(),bad),"invalid side command rejected");
        check(!SideConfiguration.apply(new int[5],300)&&!SideConfiguration.apply(null,300),"wrong size side state rejected");
        UiActionBudget budget=new UiActionBudget();for(int i=0;i<8;i++)check(budget.allow(10),"paint multiple sides in one tick");check(!budget.allow(10),"GUI action burst remains bounded");check(budget.allow(11)&&budget.allow(1),"budget resets on tick change or rewind");
        System.out.println("PASS EU/t and visual six-face UI policies: "+(checks-start)+" assertions; actual OpenGL/game GUI not launched.");
    }
}
