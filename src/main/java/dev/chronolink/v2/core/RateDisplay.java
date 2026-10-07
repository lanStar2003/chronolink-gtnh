package dev.chronolink.v2.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Display conversion only. Meter values are actual totals over a 20-game-tick window. */
public final class RateDisplay {
    public static final int WINDOW_TICKS=20;
    private RateDisplay() {}
    public static boolean energy(int kind) { return kind==3 || kind==4; }
    public static String unit(int kind) { return kind==3?"EU/t":kind==4?"RF/t":kind==0?"个/s":"mB/s"; }
    public static String stockUnit(int kind) { return kind==3?"EU":kind==4?"RF":kind==0?"个":"mB"; }
    public static BigDecimal perTick(long windowAmount) {
        return BigDecimal.valueOf(Math.max(0,windowAmount)).divide(BigDecimal.valueOf(WINDOW_TICKS));
    }
    public static String rate(long windowAmount,int kind,boolean compact) {
        return format(energy(kind)?perTick(windowAmount):BigDecimal.valueOf(Math.max(0,windowAmount)),compact);
    }
    public static String amount(String value,boolean compact) {
        if(value==null||value.isEmpty())return "—";
        try { return format(new BigDecimal(value),compact); }
        catch (NumberFormatException error) { return value==null?"—":value; }
    }
    public static String capacity(long voltage,long amperage) {
        return format(new BigDecimal(BigInteger.valueOf(Math.max(0,voltage)).multiply(BigInteger.valueOf(Math.max(0,amperage)))),false);
    }
    private static String format(BigDecimal value,boolean compact) {
        BigDecimal threshold=BigDecimal.valueOf(1000000);
        // Keep common GT voltages exact: 8192 must not become an ambiguous rounded 8.2k.
        if(compact&&value.abs().compareTo(new BigDecimal("1e21"))>=0){
            int exponent=value.precision()-value.scale()-1;
            return value.movePointLeft(exponent).setScale(2,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()+"e"+exponent;
        }
        if(compact&&value.abs().compareTo(threshold)>=0){
            String[] suffix={"M","G","T","P","E"};
            int group=0;
            while(group<suffix.length-1&&value.abs().compareTo(threshold.multiply(BigDecimal.valueOf(1000)))>=0){threshold=threshold.multiply(BigDecimal.valueOf(1000));group++;}
            return value.divide(threshold,2,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()+suffix[group];
        }
        DecimalFormat number=new DecimalFormat("#,##0.##",DecimalFormatSymbols.getInstance(Locale.ROOT));
        number.setRoundingMode(RoundingMode.HALF_UP);
        return number.format(value);
    }
}
