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
    FixtureWorld() { super(null, null, null, 0, null); }
    @Override public long getTotalWorldTime() { return 0L; }
    @Override public boolean isCallingFromMinecraftThread() { return true; }
    @Override public IChunkProvider getChunkProvider() { return chunks; }
    @Override public int getHeight() { return 256; }
    @Override public TileEntity getTileEntity(net.minecraft.util.BlockPos pos) { return tiles.get(new Position(0, pos.getX(), pos.getY(), pos.getZ())); }
    @Override public net.minecraft.block.state.IBlockState getBlockState(net.minecraft.util.BlockPos pos) { return (getTileEntity(pos) instanceof TileEntityChest ? net.minecraft.init.Blocks.chest : net.minecraft.init.Blocks.air).getDefaultState(); }
    /** Captures native slot packets without opening a connection. Allocated by the existing harness. */
    static final class ResultPackets extends net.minecraft.network.NetHandlerPlayServer {
        java.util.List<Object> packets;
        Runnable afterSend;
        ResultPackets() { super(null, null, null); }
        @Override public void sendPacket(net.minecraft.network.Packet packet) { packets.add(packet); if (afterSend != null) afterSend.run(); }
    }
}
