package mrriegel.storagenetwork.legacy;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3Pool;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.ForgeDirection;

public final class FacadeAccess implements IBlockAccess {
    private final IBlockAccess delegate;
    private final int x, y, z, metadata;
    private final Block block;
    public FacadeAccess(IBlockAccess delegate, int x, int y, int z, Block block, int metadata) { this.delegate = delegate; this.x = x; this.y = y; this.z = z; this.block = block; this.metadata = metadata; }
    private boolean here(int x, int y, int z) { return this.x == x && this.y == y && this.z == z; }
    public int getBlockId(int x, int y, int z) { return here(x, y, z) ? block.blockID : delegate.getBlockId(x, y, z); }
    public int getBlockMetadata(int x, int y, int z) { return here(x, y, z) ? metadata : delegate.getBlockMetadata(x, y, z); }
    public TileEntity getBlockTileEntity(int x, int y, int z) { return here(x, y, z) ? null : delegate.getBlockTileEntity(x, y, z); }
    public int getLightBrightnessForSkyBlocks(int x, int y, int z, int light) { return delegate.getLightBrightnessForSkyBlocks(x, y, z, light); }
    public float getBrightness(int x, int y, int z, int light) { return delegate.getBrightness(x, y, z, light); }
    public float getLightBrightness(int x, int y, int z) { return delegate.getLightBrightness(x, y, z); }
    public Material getBlockMaterial(int x, int y, int z) { return here(x, y, z) ? block.blockMaterial : delegate.getBlockMaterial(x, y, z); }
    public boolean isBlockOpaqueCube(int x, int y, int z) { return here(x, y, z) ? block.isOpaqueCube() : delegate.isBlockOpaqueCube(x, y, z); }
    public boolean isBlockNormalCube(int x, int y, int z) { return here(x, y, z) ? block.isOpaqueCube() && block.renderAsNormalBlock() : delegate.isBlockNormalCube(x, y, z); }
    public boolean isAirBlock(int x, int y, int z) { return !here(x, y, z) && delegate.isAirBlock(x, y, z); }
    public BiomeGenBase getBiomeGenForCoords(int x, int z) { return delegate.getBiomeGenForCoords(x, z); }
    public int getHeight() { return delegate.getHeight(); }
    public boolean extendedLevelsInChunkCache() { return delegate.extendedLevelsInChunkCache(); }
    public boolean doesBlockHaveSolidTopSurface(int x, int y, int z) { return delegate.doesBlockHaveSolidTopSurface(x, y, z); }
    public Vec3Pool getWorldVec3Pool() { return delegate.getWorldVec3Pool(); }
    public int isBlockProvidingPowerTo(int x, int y, int z, int side) { return delegate.isBlockProvidingPowerTo(x, y, z, side); }
    public boolean isBlockSolidOnSide(int x, int y, int z, ForgeDirection side, boolean defaultValue) { return here(x, y, z) ? block.isOpaqueCube() : delegate.isBlockSolidOnSide(x, y, z, side, defaultValue); }
}
