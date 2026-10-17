package mrriegel.storagenetwork.legacy;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

public final class FacadeAccess implements IBlockAccess {
    private final IBlockAccess delegate;
    private final int x, y, z, metadata;
    private final Block block;
    public FacadeAccess(IBlockAccess delegate, int x, int y, int z, Block block, int metadata) { this.delegate = delegate; this.x = x; this.y = y; this.z = z; this.block = block; this.metadata = metadata; }
    private boolean here(int x, int y, int z) { return this.x == x && this.y == y && this.z == z; }
    public Block getBlock(int x, int y, int z) { return here(x, y, z) ? block : delegate.getBlock(x, y, z); }
    public int getBlockMetadata(int x, int y, int z) { return here(x, y, z) ? metadata : delegate.getBlockMetadata(x, y, z); }
    public TileEntity getTileEntity(int x, int y, int z) { return here(x, y, z) ? null : delegate.getTileEntity(x, y, z); }
    public int getLightBrightnessForSkyBlocks(int x, int y, int z, int light) { return delegate.getLightBrightnessForSkyBlocks(x, y, z, light); }
    public boolean isAirBlock(int x, int y, int z) { return !here(x, y, z) && delegate.isAirBlock(x, y, z); }
    public BiomeGenBase getBiomeGenForCoords(int x, int z) { return delegate.getBiomeGenForCoords(x, z); }
    public int getHeight() { return delegate.getHeight(); }
    public boolean extendedLevelsInChunkCache() { return delegate.extendedLevelsInChunkCache(); }
    public int isBlockProvidingPowerTo(int x, int y, int z, int side) { return delegate.isBlockProvidingPowerTo(x, y, z, side); }
    public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean defaultValue) { return here(x, y, z) ? block.isSideSolid(this, x, y, z, side) : delegate.isSideSolid(x, y, z, side, defaultValue); }
}
