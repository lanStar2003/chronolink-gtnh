package dev.chronolink.v2.net;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.*;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import dev.chronolink.ChronoLink;
import dev.chronolink.v2.gui.NetworkContainer;

/** Display-only snapshots and allow-listed GUI actions for this Minecraft mod. */
public final class Packets {
    public static SimpleNetworkWrapper channel;
    private static final ConcurrentLinkedQueue<Request> requests=new ConcurrentLinkedQueue<Request>();
    private static final AtomicInteger queued=new AtomicInteger();
    private Packets(){}
    public static void init(){channel=NetworkRegistry.INSTANCE.newSimpleChannel("chronolink-v2");channel.registerMessage(SnapshotHandler.class,Snapshot.class,0,Side.CLIENT);channel.registerMessage(ActionHandler.class,Action.class,1,Side.SERVER);}
    public static boolean validAction(int id){return id>=0&&id<=18||id>=100&&id<=105||id>=200&&id<=205;}
    public static void clear(){requests.clear();queued.set(0);}
    public static final class Snapshot implements IMessage {
        public int window;public NBTTagCompound data;
        public Snapshot(){} public Snapshot(int w,NBTTagCompound d){window=w;data=d;}
        @Override public void toBytes(ByteBuf b){b.writeInt(window);ByteBufUtils.writeTag(b,data);}
        @Override public void fromBytes(ByteBuf b){if(b.readableBytes()<6||b.readableBytes()>131072)throw new IllegalArgumentException("snapshot size");window=b.readInt();data=ByteBufUtils.readTag(b);}
    }
    public static final class SnapshotHandler implements IMessageHandler<Snapshot,IMessage> {
        @Override public IMessage onMessage(Snapshot m,MessageContext c){ChronoLink.proxy.receiveV2Snapshot(m.window,m.data);return null;}
    }
    public static final class Action implements IMessage {
        public int window,id,revision;public String text="";
        public Action(){}public Action(int w,int i,int r,String t){window=w;id=i;revision=r;text=t==null?"":t;}
        @Override public void toBytes(ByteBuf b){b.writeInt(window);b.writeInt(id);b.writeInt(revision);ByteBufUtils.writeUTF8String(b,text);}
        @Override public void fromBytes(ByteBuf b){if(b.readableBytes()<13||b.readableBytes()>256)throw new IllegalArgumentException("action size");window=b.readInt();id=b.readInt();revision=b.readInt();text=ByteBufUtils.readUTF8String(b);if(text.length()>64||window<0||window>255||!validAction(id)||b.isReadable())throw new IllegalArgumentException("invalid GUI action");}
    }
    private static final class Request {final EntityPlayerMP player;final Action action;Request(EntityPlayerMP p,Action a){player=p;action=a;}}
    public static final class ActionHandler implements IMessageHandler<Action,IMessage> {
        @Override public IMessage onMessage(Action a,MessageContext c){if(!validAction(a.id))return null;if(queued.incrementAndGet()<=512)requests.add(new Request(c.getServerHandler().playerEntity,a));else queued.decrementAndGet();return null;}
    }
    public static void processActions(){for(int i=0;i<512;i++){Request r=requests.poll();if(r==null)break;queued.decrementAndGet();
        if(r.player.openContainer instanceof NetworkContainer){NetworkContainer c=(NetworkContainer)r.player.openContainer;if(c.windowId==r.action.window)c.action(r.player,r.action.id,r.action.revision,r.action.text);}}}
}
