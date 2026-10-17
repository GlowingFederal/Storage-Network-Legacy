package mrriegel.storagenetwork.legacy;

import mrriegel.storagenetwork.legacy.core.Position;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

public final class Platform {
    private Platform() {}
    public static int dimension(World world) { return world.provider.dimensionId; }
    public static Position position(TileEntity tile) { return new Position(dimension(tile.getWorldObj()), tile.xCoord, tile.yCoord, tile.zCoord); }
    public static boolean loaded(World world, Position pos) {
        return world != null && dimension(world) == pos.dimension && pos.y >= 0 && pos.y < world.getHeight()
            && world.getChunkProvider().chunkExists(pos.x >> 4, pos.z >> 4);
    }
    public static TileEntity tile(World world, Position pos) { return loaded(world, pos) ? world.getTileEntity(pos.x, pos.y, pos.z) : null; }
    public static Block block(World world, Position pos) { return world.getBlock(pos.x, pos.y, pos.z); }
    public static int metadata(World world, Position pos) { return world.getBlockMetadata(pos.x, pos.y, pos.z); }
    public static String blockName(World world, Position pos) { return String.valueOf(Block.blockRegistry.getNameForObject(block(world, pos))); }
    public static String itemName(Item item) { return String.valueOf(Item.itemRegistry.getNameForObject(item)); }
    public static String modName(Item item) {
        String domain = itemName(item).split(":")[0];
        cpw.mods.fml.common.ModContainer mod = cpw.mods.fml.common.Loader.instance().getIndexedModList().get(domain);
        return mod == null ? domain : mod.getName();
    }
    public static Block findBlock(String name) { return (Block) Block.blockRegistry.getObject(name); }
    public static World world(int dimension) { return DimensionManager.getWorld(dimension); }
    public static void sync(TileEntity tile) {
        tile.markDirty(); Position pos = position(tile); tile.getWorldObj().markBlockForUpdate(pos.x, pos.y, pos.z);
    }
    public static NBTTagCompound writePosition(Position pos) {
        NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("Dim", pos.dimension);
        tag.setInteger("x", pos.x); tag.setInteger("y", pos.y); tag.setInteger("z", pos.z); return tag;
    }
    public static Position readPosition(NBTTagCompound tag) {
        return new Position(tag.getInteger("Dim"), tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z"));
    }
    public static NBTTagList list(NBTTagCompound tag, String name) { return tag.getTagList(name, 10); }
    public static NBTTagCompound compound(NBTTagList list, int index) { return list.getCompoundTagAt(index); }
}
