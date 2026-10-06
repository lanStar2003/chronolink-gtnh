package dev.chronolink.core;

import java.util.*;

/** Real production core is compiled with --release 8. No Minecraft API stubs are used. */
public final class CoreTests {
    private static int tests,assertions;
    private static final UUID A=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID B=UUID.fromString("00000000-0000-0000-0000-000000000002");
    private interface Case { void run(); }
    private static void check(boolean condition) {
        assertions++; if(!condition) throw new AssertionError("assertion "+assertions);
    }
    private static void test(String name,Case body) {
        body.run(); tests++; System.out.println("PASS "+name);
    }
    private static final class P implements Router.Port {
        RouteKey key; boolean enabled=true,source,fault,broken;
        long count,cap=100000,rate=100000;
        P(UUID owner,int channel,ResourceKind kind,boolean source,long count) {
            key=new RouteKey(owner,channel,kind);this.source=source;this.count=count;
        }
        public RouteKey routeKey() { return key; }
        public boolean active() { return enabled && !fault; }
        public boolean importing() { return source; }
        public long available() { return count; }
        public long routeLimit() { return rate; }
        public long moveTo(Router.Port raw,long limit) {
            if(broken) throw new IllegalStateException("injected failure");
            P p=(P)raw; long n=Math.min(Math.min(limit,count),p.cap-p.count);
            count-=n;p.count+=n;return n;
        }
        public void quarantine(String reason) { fault=true; }
    }
    private static P port(boolean source,long n) { return new P(A,1,ResourceKind.EU,source,n); }
    public static void main(String[] args) {
        test("new-node-is-off",()->check(!new NodeSettings().enabled));
        test("channel-wrap",()->{NodeSettings s=new NodeSettings();s.apply(0,false);check(s.channel==64);s.apply(1,false);check(s.channel==1);});
        test("reconfigure-disarms",()->{NodeSettings s=new NodeSettings();s.enabled=true;s.apply(3,false);check(!s.enabled);});
        test("payload-blocks-rebinding",()->{NodeSettings s=new NodeSettings();check(!s.apply(1,true));check(s.channel==1);});
        test("payload-voltage-change-preserves-route",()->{NodeSettings s=new NodeSettings();s.resource=ResourceKind.EU;s.enabled=true;check(s.apply(6,true));check(s.tier==0 && s.channel==1 && !s.enabled);});
        test("payload-can-change-attached-face",()->{NodeSettings s=new NodeSettings();check(s.apply(4,true));check(s.face==3);});
        test("payload-can-pause",()->{NodeSettings s=new NodeSettings();s.enabled=true;check(s.apply(5,true));check(!s.enabled);});
        test("invalid-actions-rejected",()->{NodeSettings s=new NodeSettings();check(!s.apply(-1,false));check(!s.apply(100,false));});
        test("settings-sanitize",()->{NodeSettings s=new NodeSettings();s.channel=-100;s.face=255;s.tier=200;s.amps=100;s.resource=null;s.sanitize();check(s.channel==1 && s.face==5 && s.tier==14 && s.amps==16 && s.resource==ResourceKind.ITEM);});
        test("voltage-table",()->{NodeSettings s=new NodeSettings();s.tier=0;check(s.voltage()==8);s.tier=1;check(s.voltage()==32);s.tier=14;check(s.voltage()==2147483648L);});
        test("tier-bounds",()->{NodeSettings s=new NodeSettings();for(int i=0;i<100;i++)s.apply(7,false);check(s.tier==14);for(int i=0;i<100;i++)s.apply(6,false);check(s.tier==0);});
        test("owner-isolation-key",()->check(!new RouteKey(A,1,ResourceKind.EU).equals(new RouteKey(B,1,ResourceKind.EU))));
        test("currency-isolation-key",()->check(!new RouteKey(A,1,ResourceKind.EU).equals(new RouteKey(A,1,ResourceKind.RF))));
        test("equal-key-hash",()->{RouteKey a=new RouteKey(A,2,ResourceKind.FLUID),b=new RouteKey(A,2,ResourceKind.FLUID);check(a.equals(b));check(a.hashCode()==b.hashCode());});
        test("invalid-key-rejected",()->{boolean caught=false;try{new RouteKey(A,0,ResourceKind.ITEM);}catch(IllegalArgumentException e){caught=true;}check(caught);});
        test("simulation-no-mutation",()->{EnergyStore e=new EnergyStore(100);check(e.insert(80,true)==80);check(e.amount()==0);e.insert(70,false);check(e.extract(40,true)==40);check(e.amount()==70);});
        test("partial-energy-accept",()->{EnergyStore e=new EnergyStore(100);check(e.insert(200,false)==100);check(e.insert(1,false)==0);check(e.extract(200,false)==100);});
        test("negative-energy-rejected",()->{EnergyStore e=new EnergyStore(100);check(e.insert(-1,false)==0);check(e.extract(-1,false)==0);check(e.amount()==0);});
        test("eu-returns-amps",()->{EnergyStore e=new EnergyStore(100);check(e.acceptPacket(32,4,4,false)==3);check(e.amount()==96);});
        test("eu-no-fractional-packet",()->{EnergyStore e=new EnergyStore(31);check(e.acceptPacket(32,1,1,false)==0);check(e.amount()==0);});
        test("eu-packet-simulation",()->{EnergyStore e=new EnergyStore(1024);check(e.acceptPacket(128,8,4,true)==4);check(e.amount()==0);});
        test("eu-overflow-safe",()->{EnergyStore e=new EnergyStore(Long.MAX_VALUE);check(e.acceptPacket(Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,false)==1);check(e.amount()==Long.MAX_VALUE);});
        test("eu-invalid-voltage",()->{EnergyStore e=new EnergyStore(100);check(e.acceptPacket(0,9,9,false)==0);check(e.acceptPacket(-1,9,9,false)==0);});
        test("saved-invalid-energy-rejected",()->{EnergyStore e=new EnergyStore(10);boolean caught=false;try{e.restore(11);}catch(IllegalArgumentException ex){caught=true;}check(caught);check(e.amount()==0);});
        test("energy-handoff-conservation",()->{EnergyStore a=new EnergyStore(100),b=new EnergyStore(20);a.insert(99,false);check(a.moveTo(b,90)==20);check(a.amount()+b.amount()==99);});
        test("self-handoff-noop",()->{EnergyStore a=new EnergyStore(100);a.insert(99,false);check(a.moveTo(a,80)==0);check(a.amount()==99);});
        test("tick-budget-shared",()->{TickBudget b=new TickBudget();check(b.spend(1,7,10)==7);check(b.spend(1,7,10)==3);check(b.remaining(1,10)==0);check(b.spend(2,7,10)==7);});
        test("time-rewind-resets-budget",()->{TickBudget b=new TickBudget();b.spend(100,10,10);check(b.remaining(99,10)==10);});
        test("saturated-statistics",()->check(Amounts.saturatingAdd(Long.MAX_VALUE-4,10)==Long.MAX_VALUE));
        test("slot-room",()->{check(Amounts.slotRoom(64,16,10)==6);check(Amounts.slotRoom(16,64,20)==0);check(Amounts.slotRoom(0,64,0)==0);});
        test("router-basic",()->{P a=port(true,100),b=port(false,0);Router.Result r=new Router().tick(Arrays.asList(a,b),100);check(a.count==0 && b.count==100);check(r.moved==100 && r.transfers==1);});
        test("router-offline-retention",()->{P a=port(true,100);new Router().tick(Arrays.asList(a),100);check(a.count==100);});
        test("router-disabled-retention",()->{P a=port(true,100),b=port(false,0);b.enabled=false;new Router().tick(Arrays.asList(a,b),100);check(a.count==100 && b.count==0);});
        test("router-owner-isolation",()->{P a=port(true,100),b=new P(B,1,ResourceKind.EU,false,0);new Router().tick(Arrays.asList(a,b),100);check(a.count==100 && b.count==0);});
        test("router-channel-isolation",()->{P a=port(true,100),b=new P(A,2,ResourceKind.EU,false,0);new Router().tick(Arrays.asList(a,b),100);check(a.count==100);});
        test("router-eu-rf-never-converts",()->{P a=port(true,100),b=new P(A,1,ResourceKind.RF,false,0);new Router().tick(Arrays.asList(a,b),100);check(a.count==100 && b.count==0);});
        test("router-partial-retention",()->{P a=port(true,100),b=port(false,0);b.cap=7;new Router().tick(Arrays.asList(a,b),100);check(a.count==93 && b.count==7);});
        test("router-source-rate-limit",()->{P a=port(true,100),b=port(false,0),c=port(false,0);a.rate=9;new Router().tick(Arrays.asList(a,b,c),100);check(a.count==91 && b.count+c.count==9);});
        test("router-exception-quarantine",()->{P a=port(true,100),b=port(false,0);a.broken=true;Router.Result r=new Router().tick(Arrays.asList(a,b),100);check(a.fault && b.fault && r.faults==1);});
        test("router-comparison-cap",()->{List<P> ps=new ArrayList<P>();ps.add(port(true,100));for(int i=0;i<200;i++){P p=port(false,0);p.cap=0;ps.add(p);}Router.Result r=new Router().tick(ps,7);check(r.comparisons==7);});
        test("router-target-fairness",()->{P a=port(true,100);a.rate=1;List<P> ps=new ArrayList<P>();ps.add(a);for(int i=0;i<10;i++)ps.add(port(false,0));Router router=new Router();for(int i=0;i<10;i++)router.tick(ps,100);for(int i=1;i<11;i++)check(ps.get(i).count==1);});
        test("router-group-fairness",()->{List<P> ps=new ArrayList<P>();for(int i=1;i<=10;i++){P a=new P(A,i,ResourceKind.EU,true,10);a.rate=1;ps.add(a);ps.add(new P(A,i,ResourceKind.EU,false,0));}Router router=new Router();for(int i=0;i<10;i++)router.tick(ps,1);for(int i=1;i<20;i+=2)check(ps.get(i).count==1);});
        test("random-energy-conservation-50000",()->{Random random=new Random(284);EnergyStore a=new EnergyStore(1L<<50),b=new EnergyStore(1L<<50);a.insert(1L<<49,false);long total=a.amount()+b.amount();for(int i=0;i<50000;i++){long request=random.nextLong() & Long.MAX_VALUE;if(random.nextBoolean())a.moveTo(b,request);else b.moveTo(a,request);check(a.amount()+b.amount()==total);check(a.amount()>=0 && b.amount()>=0);}});
        test("random-packet-accounting-10000",()->{Random random=new Random(482);for(int i=0;i<10000;i++){long cap=1+(random.nextLong() & ((1L<<60)-1));EnergyStore e=new EnergyStore(cap);long v=1+(random.nextLong() & ((1L<<50)-1));long a=random.nextInt(10000);long accepted=e.acceptPacket(v,a,32,false);check(accepted>=0 && accepted<=a && accepted<=32);check(e.amount()==accepted*v && e.amount()<=cap);}});
        test("random-routes-conserve-2000",()->{Random random=new Random(1710);List<P> ports=new ArrayList<P>();for(int i=0;i<50;i++){P p=new P(i%2==0?A:B,1+(i%4),ResourceKind.values()[i%4],i<25,i<25?random.nextInt(10000):0);p.rate=13;ports.add(p);}long before=0;for(P p:ports)before+=p.count;Router router=new Router();for(int i=0;i<2000;i++){Router.Result rr=router.tick(ports,57);check(rr.comparisons<=57);long after=0;for(P p:ports)after+=p.count;check(after==before);}});
        System.out.println("RESULT: "+tests+" tests passed; "+assertions+" assertions; Minecraft/Forge integration NOT tested.");
    }
}
