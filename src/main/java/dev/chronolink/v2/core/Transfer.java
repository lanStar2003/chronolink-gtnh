package dev.chronolink.v2.core;

/** Destination-first simulation, source escrow, actual delivery and explicit rollback.
 * The normal path owns NO inventory. Only a failed/partial third-party transaction may need rescue.
 */
public final class Transfer {
    private Transfer() {}
    public interface Store<K> {
        Object identity();
        long insert(K key,long count,boolean simulate);
        long extract(K key,long count,boolean simulate);
    }
    public interface Rescue<K> {
        void retain(K key,long knownRemainder);
        void uncertain(K key,long inFlight,String phase);
    }
    public static <K> long move(Store<K> from,Store<K> to,K key,long limit,Rescue<K> rescue) {
        if(limit<=0 || from==to || from.identity()==to.identity())return 0;
        long offered=checked(from.extract(key,limit,true),limit);
        long accepted=checked(to.insert(key,offered,true),offered);
        if(accepted==0)return 0;
        long acquired;
        try { acquired=checked(from.extract(key,accepted,false),accepted); }
        catch(RuntimeException e){rescue.uncertain(key,accepted,"extract");throw e;}
        if(acquired==0)return 0;
        long sent;
        try {sent=checked(to.insert(key,acquired,false),acquired);}
        catch(RuntimeException e){rescue.uncertain(key,acquired,"insert");throw e;}
        long remainder=acquired-sent;
        if(remainder>0) {
            long returned;
            try {returned=checked(from.insert(key,remainder,false),remainder);}
            catch(RuntimeException e){rescue.uncertain(key,remainder,"rollback");throw e;}
            if(returned<remainder)rescue.retain(key,remainder-returned);
        }
        return sent;
    }
    public static long checked(long n,long max) {
        if(n<0||n>max)throw new IllegalStateException("Invalid transfer result: "+n+" / "+max);
        return n;
    }
}
