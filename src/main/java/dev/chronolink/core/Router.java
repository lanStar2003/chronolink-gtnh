package dev.chronolink.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loaded nodes only. Bounded comparisons, rotating groups/sources/destinations. */
public final class Router {
    public interface Port {
        RouteKey routeKey();
        boolean active();
        boolean importing();
        long available();
        long routeLimit();
        /** Local in-memory handoff only, never call an external machine in this method. */
        long moveTo(Port target,long limit);
        void quarantine(String reason);
    }
    public static final class Result {
        public int comparisons, transfers, faults;
        public long moved;
    }
    private static final class Group {
        final List<Port> sources=new ArrayList<Port>(), targets=new ArrayList<Port>();
    }
    private long turn;
    public Result tick(Collection<? extends Port> ports,int comparisonLimit) {
        Result result=new Result();
        if(comparisonLimit<=0) return result;
        Map<RouteKey,Group> byKey=new LinkedHashMap<RouteKey,Group>();
        for(Port port:ports) {
            if(port==null || !port.active()) continue;
            RouteKey key=port.routeKey();
            if(key==null) continue;
            Group group=byKey.get(key);
            if(group==null) { group=new Group(); byKey.put(key,group); }
            (port.importing()?group.sources:group.targets).add(port);
        }
        List<Group> groups=new ArrayList<Group>(byKey.values());
        long offset=turn++ & Long.MAX_VALUE;
        for(int gi=0;gi<groups.size() && result.comparisons<comparisonLimit;gi++) {
            Group g=groups.get(index(offset+gi,groups.size()));
            for(int si=0;si<g.sources.size() && result.comparisons<comparisonLimit;si++) {
                Port source=g.sources.get(index(offset+si,g.sources.size()));
                long left=Amounts.bounded(source.available(),source.routeLimit());
                // A single source cannot consume an unbounded budget with blocked targets.
                int attempts=Math.min(g.targets.size(),64);
                for(int di=0;di<attempts && left>0 && result.comparisons<comparisonLimit;di++) {
                    Port target=g.targets.get(index(offset+si+di,g.targets.size()));
                    if(!source.active()) break;
                    if(!target.active() || source==target) continue;
                    result.comparisons++;
                    try {
                        long n=source.moveTo(target,left);
                        if(n<0 || n>left) throw new IllegalStateException("invalid handoff result");
                        if(n>0) {
                            left-=n; result.transfers++;
                            result.moved=Amounts.saturatingAdd(result.moved,n);
                        }
                    } catch(RuntimeException ex) {
                        source.quarantine("wireless handoff failed");
                        target.quarantine("wireless handoff failed");
                        result.faults++; break;
                    }
                }
            }
        }
        return result;
    }
    private static int index(long n,int size) { return (int)((n & Long.MAX_VALUE)%size); }
}
