package dev.chronolink.v2.core;

/** Absolute Forge side IDs: down/up/north/south/west/east. No camera-dependent remapping. */
public final class SideConfiguration {
    private SideConfiguration() {}
    public static final int ACTION_BASE=300;
    public static final int ORIGIN_X=201,ORIGIN_Y=60;
    private static final int[] DRAW_ORDER={1,2,5,4,3,0};
    private static final int[][][] POLYGONS={
        {{71,64},{99,64},{99,80},{71,80}},
        {{50,0},{80,13},{50,26},{20,13}},
        {{20,13},{50,26},{50,57},{20,44}},
        {{37,64},{65,64},{65,80},{37,80}},
        {{3,64},{31,64},{31,80},{3,80}},
        {{50,26},{80,13},{80,44},{50,57}}
    };
    private static final int[][] CENTERS={{85,72},{50,13},{35,36},{51,72},{17,72},{65,36}};
    public static boolean validFace(int face){return face>=0&&face<6;}
    public static boolean validMode(int mode){return mode>=0&&mode<3;}
    public static boolean isModeAction(int action){return action>=ACTION_BASE&&action<ACTION_BASE+18;}
    public static boolean isSideAction(int action){return isModeAction(action)||action>=320&&action<326||action>=330&&action<336;}
    public static int cycleAction(int face,boolean reverse){if(!validFace(face))throw new IllegalArgumentException("Invalid face");return (reverse?330:320)+face;}
    public static int action(int face,int mode){if(!validFace(face)||!validMode(mode))throw new IllegalArgumentException("Invalid side setting");return ACTION_BASE+face*3+mode;}
    public static int face(int action){return isModeAction(action)?(action-ACTION_BASE)/3:action>=320&&action<326?action-320:action>=330&&action<336?action-330:-1;}
    public static int mode(int action){return isModeAction(action)?(action-ACTION_BASE)%3:-1;}
    public static int cycle(int mode,boolean reverse){if(!validMode(mode))throw new IllegalArgumentException("Invalid mode");return (mode+(reverse?2:1))%3;}
    public static boolean apply(int[] modes,int action){if(modes==null||modes.length!=6||!isSideAction(action))return false;int side=face(action);if(!validMode(modes[side]))return false;modes[side]=isModeAction(action)?mode(action):cycle(modes[side],action>=330);return true;}
    public static int[] order(){return DRAW_ORDER.clone();}
    public static int[][] polygon(int face){if(!validFace(face))throw new IllegalArgumentException("Invalid face");int[][] out=new int[4][2];for(int i=0;i<4;i++)out[i]=POLYGONS[face][i].clone();return out;}
    public static int[] center(int face){return CENTERS[face].clone();}
    public static int hit(double x,double y){for(int face:DRAW_ORDER)if(contains(POLYGONS[face],x,y))return face;return -1;}
    private static boolean contains(int[][] p,double x,double y){
        boolean in=false;
        for(int i=0,j=p.length-1;i<p.length;j=i++)if((p[i][1]>y)!=(p[j][1]>y)&&x<(double)(p[j][0]-p[i][0])*(y-p[i][1])/(p[j][1]-p[i][1])+p[i][0])in=!in;
        return in;
    }
}
