package mrriegel.storagenetwork.legacy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S2FPacketSetSlot;

public final class Protocol {
    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("SSNLegacy");
    private static final ConcurrentLinkedQueue<Runnable> SERVER = new ConcurrentLinkedQueue<Runnable>();
    private static final AtomicInteger QUEUED = new AtomicInteger();
    private static int sequence;
    private Protocol() {}
    public static void init() { CHANNEL.registerMessage(ActionHandler.class, Action.class, 0, Side.SERVER); CHANNEL.registerMessage(SnapshotHandler.class, Snapshot.class, 1, Side.CLIENT); }
    public static void send(int window, int revision, int action, int a, int b, int c) { CHANNEL.sendToServer(new Action(window, revision, action, a, b, c)); }
    public static void sendTo(NBTTagCompound tag, EntityPlayerMP player) {
        byte[] bytes = encode(tag); if (bytes.length > 8388608) throw new IllegalStateException("SSN snapshot exceeds 8 MiB");
        int id = sequence++, parts = (bytes.length + 29999) / 30000;
        for (int part = 0; part < parts; part++) CHANNEL.sendTo(new Snapshot(id, part, parts, bytes), player);
    }
    public static void syncCursor(EntityPlayerMP player) { player.playerNetServerHandler.sendPacket(new S2FPacketSetSlot(-1, -1, player.inventory.getItemStack())); }
    public static void enqueue(Runnable task) {
        if (QUEUED.incrementAndGet() > 512) { QUEUED.decrementAndGet(); return; }
        SERVER.add(task);
    }
    public static final class Ticks {
        @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            for (int i = 0; i < 128; i++) { Runnable task = SERVER.poll(); if (task == null) break; QUEUED.decrementAndGet(); task.run(); }
        }
    }
    public static final class Action implements IMessage {
        int window, revision, action, a, b, c;
        public Action() {}
        Action(int window, int revision, int action, int a, int b, int c) { this.window = window; this.revision = revision; this.action = action; this.a = a; this.b = b; this.c = c; }
        public void fromBytes(ByteBuf buffer) { if (buffer.readableBytes() != 24) throw new IllegalArgumentException("Invalid SSN action"); window = buffer.readInt(); revision = buffer.readInt(); action = buffer.readInt(); a = buffer.readInt(); b = buffer.readInt(); c = buffer.readInt(); }
        public void toBytes(ByteBuf buffer) { buffer.writeInt(window); buffer.writeInt(revision); buffer.writeInt(action); buffer.writeInt(a); buffer.writeInt(b); buffer.writeInt(c); }
    }
    public static final class ActionHandler implements IMessageHandler<Action, IMessage> {
        public IMessage onMessage(final Action message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            enqueue(new Runnable() { public void run() { Actions.handle(player, message.window, message.revision, message.action, message.a, message.b, message.c); } }); return null;
        }
    }
    public static byte[] encode(NBTTagCompound tag) {
        try { ByteArrayOutputStream bytes = new ByteArrayOutputStream(); CompressedStreamTools.writeCompressed(tag, bytes); return bytes.toByteArray(); }
        catch (IOException error) { throw new IllegalStateException("SSN snapshot encoding failed", error); }
    }
    public static NBTTagCompound decode(byte[] bytes) {
        try { return CompressedStreamTools.readCompressed(new ByteArrayInputStream(bytes)); }
        catch (IOException error) { throw new IllegalArgumentException("Invalid SSN snapshot", error); }
    }
    public static final class Snapshot implements IMessage {
        int id, part, parts, size;
        byte[] bytes;
        public Snapshot() {}
        Snapshot(int id, int part, int parts, byte[] source) {
            this.id = id; this.part = part; this.parts = parts; size = source.length;
            bytes = java.util.Arrays.copyOfRange(source, part * 30000, Math.min(source.length, (part + 1) * 30000));
        }
        public void toBytes(ByteBuf buffer) { buffer.writeInt(id); buffer.writeInt(part); buffer.writeInt(parts); buffer.writeInt(size); buffer.writeBytes(bytes); }
        public void fromBytes(ByteBuf buffer) {
            if (buffer.readableBytes() < 16) throw new IllegalArgumentException("Invalid SSN fragment header");
            id = buffer.readInt(); part = buffer.readInt(); parts = buffer.readInt(); size = buffer.readInt();
            if (parts < 1 || parts > 280 || part < 0 || part >= parts || size < 0 || size > 8388608 || buffer.readableBytes() > 30000) throw new IllegalArgumentException("Invalid SSN fragment size");
            bytes = new byte[buffer.readableBytes()]; buffer.readBytes(bytes);
        }
    }
    public static final class SnapshotHandler implements IMessageHandler<Snapshot, IMessage> {
        private ByteArrayOutputStream assembly;
        private int id, parts, size, next;
        public IMessage onMessage(Snapshot message, MessageContext context) {
            if (message.part == 0) { assembly = new ByteArrayOutputStream(); id = message.id; parts = message.parts; size = message.size; next = 0; }
            if (assembly == null || id != message.id || parts != message.parts || size != message.size || next != message.part || assembly.size() + message.bytes.length > size) { assembly = null; return null; }
            assembly.write(message.bytes, 0, message.bytes.length); next++;
            if (next == parts) { if (assembly.size() == size) StorageNetwork.proxy.receive(decode(assembly.toByteArray())); assembly = null; }
            return null;
        }
    }
}
