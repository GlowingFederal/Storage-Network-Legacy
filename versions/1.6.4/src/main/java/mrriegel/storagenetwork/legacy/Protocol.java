package mrriegel.storagenetwork.legacy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.EnumSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet103SetSlot;
import net.minecraft.network.packet.Packet250CustomPayload;

/** 1.6.4 custom payloads, fragmented below the native 32767-byte packet ceiling. */
public final class Protocol implements IPacketHandler {
    private static final ConcurrentLinkedQueue<Runnable> SERVER = new ConcurrentLinkedQueue<Runnable>();
    private static final AtomicInteger QUEUED = new AtomicInteger();
    private static int sequence;
    private static ByteArrayOutputStream assembly;
    private static int receivedId = -1, receivedParts, expectedPart, expectedSize;
    public Protocol() {}
    public static void init() {}
    public static void send(int window, int revision, int action, int a, int b, int c) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream output = new DataOutputStream(bytes);
            output.writeByte(0); output.writeInt(window); output.writeInt(revision); output.writeInt(action); output.writeInt(a); output.writeInt(b); output.writeInt(c);
            PacketDispatcher.sendPacketToServer(new Packet250CustomPayload("SSNLegacy", bytes.toByteArray()));
        } catch (IOException error) { throw new IllegalStateException(error); }
    }
    public static void sendTo(NBTTagCompound tag, EntityPlayerMP player) {
        byte[] bytes = encode(tag); int id = sequence++, parts = (bytes.length + 29999) / 30000;
        if (bytes.length > 8388608) throw new IllegalStateException("SSN snapshot exceeds 8 MiB");
        for (int part = 0; part < parts; part++) {
            try {
                ByteArrayOutputStream frame = new ByteArrayOutputStream(); DataOutputStream output = new DataOutputStream(frame);
                output.writeByte(1); output.writeInt(id); output.writeInt(part); output.writeInt(parts); output.writeInt(bytes.length);
                output.write(bytes, part * 30000, Math.min(30000, bytes.length - part * 30000));
                PacketDispatcher.sendPacketToPlayer(new Packet250CustomPayload("SSNLegacy", frame.toByteArray()), (Player) player);
            } catch (IOException error) { throw new IllegalStateException(error); }
        }
    }
    public static void syncCursor(EntityPlayerMP player) { player.playerNetServerHandler.sendPacketToPlayer(new Packet103SetSlot(-1, -1, player.inventory.getItemStack())); }
    public void onPacketData(INetworkManager manager, Packet250CustomPayload packet, Player recipient) {
        if (!packet.channel.equals("SSNLegacy") || packet.data == null || packet.data.length == 0) return;
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(packet.data)); int type = input.readUnsignedByte();
            if (recipient instanceof EntityPlayerMP) {
                if (type != 0 || packet.data.length != 25) return;
                final EntityPlayerMP player = (EntityPlayerMP) recipient;
                final int window = input.readInt(), revision = input.readInt(), action = input.readInt(), a = input.readInt(), b = input.readInt(), c = input.readInt();
                if (QUEUED.incrementAndGet() > 512) { QUEUED.decrementAndGet(); return; }
                SERVER.add(new Runnable() { public void run() { Actions.handle(player, window, revision, action, a, b, c); } });
            }
            else if (type == 1 && packet.data.length >= 17) {
                int id = input.readInt(), part = input.readInt(), parts = input.readInt(), size = input.readInt();
                if (parts <= 0 || parts > 280 || size < 0 || size > 8388608) return;
                if (part == 0) { receivedId = id; receivedParts = parts; expectedSize = size; expectedPart = 0; assembly = new ByteArrayOutputStream(); }
                if (assembly == null || receivedId != id || part != expectedPart || receivedParts != parts || expectedSize != size) { assembly = null; return; }
                int count = input.available(); if (count > 30000 || assembly.size() + count > size) { assembly = null; return; }
                assembly.write(packet.data, 17, count); expectedPart++;
                if (expectedPart == parts) {
                    if (assembly.size() == size) StorageNetwork.proxy.receive(decode(assembly.toByteArray())); assembly = null;
                }
            }
        } catch (IOException invalid) { throw new IllegalArgumentException("Invalid SSN payload", invalid); }
    }
    public static byte[] encode(NBTTagCompound tag) {
        try { ByteArrayOutputStream bytes = new ByteArrayOutputStream(); CompressedStreamTools.writeCompressed(tag, bytes); return bytes.toByteArray(); }
        catch (IOException error) { throw new IllegalStateException(error); }
    }
    public static NBTTagCompound decode(byte[] bytes) {
        try { return CompressedStreamTools.readCompressed(new ByteArrayInputStream(bytes)); }
        catch (IOException error) { throw new IllegalArgumentException("Invalid SSN snapshot", error); }
    }
    public static final class Ticks implements ITickHandler {
        public void tickStart(EnumSet<TickType> types, Object... data) {}
        public void tickEnd(EnumSet<TickType> types, Object... data) {
            for (int i = 0; i < 128; i++) { Runnable task = SERVER.poll(); if (task == null) break; QUEUED.decrementAndGet(); task.run(); }
        }
        public EnumSet<TickType> ticks() { return EnumSet.of(TickType.SERVER); }
        public String getLabel() { return "SSN server actions"; }
    }
}
