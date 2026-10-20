package mrriegel.storagenetwork.legacy;

import java.util.Map;
import mrriegel.storagenetwork.legacy.core.Position;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.IChunkProvider;

/** In-memory loaded tile lookup; no chunks, threads, saves, or sockets are created. */
public final class FixtureWorld extends WorldServer {
    Map<Position, TileEntity> tiles;
    IChunkProvider chunks;
    FixtureWorld() { super(null, null, null, 0, null, null, null); }
    @Override public long getTotalWorldTime() { return 0L; }
    @Override public IChunkProvider getChunkProvider() { return chunks; }
    @Override public int getHeight() { return 256; }
    @Override public TileEntity getBlockTileEntity(int x, int y, int z) { return tiles.get(new Position(0, x, y, z)); }
    @Override public int getBlockId(int x, int y, int z) { return getBlockTileEntity(x, y, z) instanceof TileEntityChest ? net.minecraft.block.Block.chest.blockID : 0; }
    @Override public int getBlockMetadata(int x, int y, int z) { return 0; }
    /** Captures native slot packets without opening a connection. Allocated by the existing harness. */
    static final class ResultPackets extends net.minecraft.network.NetServerHandler {
        java.util.List<Object> packets;
        Runnable afterSend;
        ResultPackets() { super(null, null, null); }
        @Override public void sendPacketToPlayer(net.minecraft.network.packet.Packet packet) { packets.add(packet); if (afterSend != null) afterSend.run(); }
    }
}
