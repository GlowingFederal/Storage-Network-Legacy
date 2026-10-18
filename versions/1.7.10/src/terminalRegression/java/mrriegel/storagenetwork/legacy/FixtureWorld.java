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
    FixtureWorld() { super(null, null, null, 0, null, null); }
    @Override public IChunkProvider getChunkProvider() { return chunks; }
    @Override public int getHeight() { return 256; }
    @Override public TileEntity getTileEntity(int x, int y, int z) { return tiles.get(new Position(0, x, y, z)); }
    @Override public net.minecraft.block.Block getBlock(int x, int y, int z) { return getTileEntity(x, y, z) instanceof TileEntityChest ? net.minecraft.init.Blocks.chest : net.minecraft.init.Blocks.air; }
    @Override public int getBlockMetadata(int x, int y, int z) { return 0; }
    /** Captures native slot packets without opening a connection. Allocated by the existing harness. */
    static final class ResultPackets extends net.minecraft.network.NetHandlerPlayServer {
        java.util.List<Object> packets;
        ResultPackets() { super(null, null, null); }
        @Override public void sendPacket(net.minecraft.network.Packet packet) { packets.add(packet); }
    }
}
