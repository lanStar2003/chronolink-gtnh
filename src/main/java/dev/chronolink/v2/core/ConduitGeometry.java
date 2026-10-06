package dev.chronolink.v2.core;

import java.util.ArrayList;
import java.util.List;

/** Identical server collision and client model boxes; coordinates are fractions of a block. */
public final class ConduitGeometry {
    private ConduitGeometry() {}
    public static List<double[]> boxes(int mask) {
        List<double[]> b=new ArrayList<double[]>();
        b.add(new double[]{.3125,.3125,.3125,.6875,.6875,.6875});
        double l=.40625,h=.59375;
        if((mask&1)!=0)b.add(new double[]{l,0,l,h,.3125,h});
        if((mask&2)!=0)b.add(new double[]{l,.6875,l,h,1,h});
        if((mask&4)!=0)b.add(new double[]{l,l,0,h,h,.3125});
        if((mask&8)!=0)b.add(new double[]{l,l,.6875,h,h,1});
        if((mask&16)!=0)b.add(new double[]{0,l,l,.3125,h,h});
        if((mask&32)!=0)b.add(new double[]{.6875,l,l,1,h,h});
        return b;
    }
}
