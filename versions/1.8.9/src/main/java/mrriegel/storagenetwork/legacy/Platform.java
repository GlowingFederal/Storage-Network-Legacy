package mrriegel.storagenetwork.legacy;

import mrriegel.storagenetwork.legacy.core.Position;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

public final class Platform {
    private Platform() {}
    public static int dimension(World world) { return world.provider.getDimensionId(); }
    public static Position position(TileEntity tile) { BlockPos pos = tile.getPos(); return new Position(dimension(tile.getWorld()), pos.getX(), pos.getY(), pos.getZ()); }
    public static BlockPos nativePos(Position pos) { return new BlockPos(pos.x, pos.y, pos.z); }
    public static boolean loaded(World world, Position pos) { return world != null && dimension(world) == pos.dimension && pos.y >= 0 && pos.y < world.getHeight() && world.getChunkProvider().chunkExists(pos.x >> 4, pos.z >> 4); }
    public static TileEntity tile(World world, Position pos) { return loaded(world, pos) ? world.getTileEntity(nativePos(pos)) : null; }
    public static Block block(World world, Position pos) { return world.getBlockState(nativePos(pos)).getBlock(); }
    public static int metadata(World world, Position pos) { net.minecraft.block.state.IBlockState state = world.getBlockState(nativePos(pos)); return state.getBlock().getMetaFromState(state); }
    public static String blockName(World world, Position pos) { return String.valueOf(Block.blockRegistry.getNameForObject(block(world, pos))); }
    public static String itemName(Item item) { return String.valueOf(Item.itemRegistry.getNameForObject(item)); }
    public static String modName(Item item) {
        String domain = itemName(item).split(":")[0];
        net.minecraftforge.fml.common.ModContainer mod = net.minecraftforge.fml.common.Loader.instance().getIndexedModList().get(domain);
        return mod == null ? domain : mod.getName();
    }
    public static Block findBlock(String name) { return (Block) Block.blockRegistry.getObject(new ResourceLocation(name)); }
    public static World world(int dimension) { return DimensionManager.getWorld(dimension); }
    public static void sync(TileEntity tile) { tile.markDirty(); tile.getWorld().markBlockForUpdate(tile.getPos()); }
    public static NBTTagCompound writePosition(Position pos) { NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("Dim", pos.dimension); tag.setInteger("x", pos.x); tag.setInteger("y", pos.y); tag.setInteger("z", pos.z); return tag; }
    public static Position readPosition(NBTTagCompound tag) { return new Position(tag.getInteger("Dim"), tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z")); }
    public static NBTTagList list(NBTTagCompound tag, String name) { return tag.getTagList(name, 10); }
    public static NBTTagCompound compound(NBTTagList list, int index) { return list.getCompoundTagAt(index); }
}
