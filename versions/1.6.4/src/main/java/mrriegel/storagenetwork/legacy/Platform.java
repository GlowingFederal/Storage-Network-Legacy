package mrriegel.storagenetwork.legacy;

import cpw.mods.fml.common.registry.GameRegistry;
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
    public static Position position(TileEntity tile) { return new Position(dimension(tile.worldObj), tile.xCoord, tile.yCoord, tile.zCoord); }
    public static boolean loaded(World world, Position pos) { return world != null && dimension(world) == pos.dimension && pos.y >= 0 && pos.y < world.getHeight() && world.getChunkProvider().chunkExists(pos.x >> 4, pos.z >> 4); }
    public static TileEntity tile(World world, Position pos) { return loaded(world, pos) ? world.getBlockTileEntity(pos.x, pos.y, pos.z) : null; }
    public static Block block(World world, Position pos) { return Block.blocksList[world.getBlockId(pos.x, pos.y, pos.z)]; }
    public static int metadata(World world, Position pos) { return world.getBlockMetadata(pos.x, pos.y, pos.z); }
    public static String blockKey(Block block) { GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(block); return id == null ? "legacy:" + block.blockID : id.modId + ":" + id.name; }
    public static String blockName(World world, Position pos) { Block block = block(world, pos); return block == null ? "minecraft:air" : blockKey(block); }
    public static String itemName(Item item) {
        GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(item);
        if (id != null) return id.modId + ":" + id.name;
        // Vanilla objects have no named GameRegistry entry in 1.6.4. Keep facade blacklist defaults meaningful.
        switch (item.itemID) {
            case 65: return "minecraft:ladder"; case 66: return "minecraft:rail";
            case 28: return "minecraft:detector_rail"; case 157: return "minecraft:activator_rail";
            case 27: return "minecraft:golden_rail"; case 111: return "minecraft:waterlily";
            default: return "legacy:" + item.itemID;
        }
    }
    public static String modName(Item item) {
        String domain = itemName(item).split(":")[0];
        if (domain.equals("legacy") && (item.itemID <= 173 || item.itemID >= 256 && item.itemID <= 422)) return "Minecraft";
        cpw.mods.fml.common.ModContainer mod = cpw.mods.fml.common.Loader.instance().getIndexedModList().get(domain);
        return mod == null ? domain : mod.getName();
    }
    public static Block findBlock(String name) {
        String[] parts = name.split(":", 2); if (parts.length != 2) return null;
        if (parts[0].equals("legacy")) { try { int id = Integer.parseInt(parts[1]); return id >= 0 && id < Block.blocksList.length ? Block.blocksList[id] : null; } catch (NumberFormatException invalid) { return null; } }
        return GameRegistry.findBlock(parts[0], parts[1]);
    }
    public static Item findItem(String name) {
        String[] parts = name.split(":", 2); if (parts.length != 2) return null;
        // Forge 1.6.4 builds its name lookup after mod initialization. Recipes run earlier.
        if (parts[0].equals(StorageNetwork.MODID)) {
            for (int i = 0; i < StorageNetwork.NAMES.length; i++) if (StorageNetwork.NAMES[i].equals(parts[1])) return Item.itemsList[StorageNetwork.blocks[i].blockID];
            if (parts[1].equals("upgrade")) return StorageNetwork.upgrade;
            if (parts[1].equals("remote")) return StorageNetwork.remote;
            if (parts[1].equals("picker_remote")) return StorageNetwork.picker;
            if (parts[1].equals("collector_remote")) return StorageNetwork.collector;
        }
        if (!parts[0].equals("minecraft")) {
            Item item = GameRegistry.findItem(parts[0], parts[1]); if (item != null) return item;
            Block block = GameRegistry.findBlock(parts[0], parts[1]); return block == null ? null : Item.itemsList[block.blockID];
        }
        String[] names = {"lapis_block", "obsidian", "piston", "hopper", "dropper", "stone_slab", "iron_ingot", "quartz_block", "diamond", "gold_nugget", "daylight_detector", "blaze_rod", "nether_star", "netherrack", "ender_pearl", "glowstone", "gold_ingot", "slime_ball", "crafting_table", "chest", "redstone_block", "comparator", "redstone", "blaze_powder", "coal"};
        int[] ids = {22, 49, 33, 154, 158, 44, 265, 155, 264, 371, 151, 369, 399, 87, 368, 89, 266, 341, 58, 54, 152, 404, 331, 377, 263};
        for (int i = 0; i < names.length; i++) if (names[i].equals(parts[1])) return Item.itemsList[ids[i]];
        return null;
    }
    public static World world(int dimension) { return DimensionManager.getWorld(dimension); }
    public static void sync(TileEntity tile) { tile.onInventoryChanged(); tile.worldObj.markBlockForUpdate(tile.xCoord, tile.yCoord, tile.zCoord); }
    public static NBTTagCompound writePosition(Position pos) { NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("Dim", pos.dimension); tag.setInteger("x", pos.x); tag.setInteger("y", pos.y); tag.setInteger("z", pos.z); return tag; }
    public static Position readPosition(NBTTagCompound tag) { return new Position(tag.getInteger("Dim"), tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z")); }
    public static NBTTagList list(NBTTagCompound tag, String name) { return tag.getTag(name) instanceof NBTTagList ? tag.getTagList(name) : new NBTTagList(); }
    public static NBTTagCompound compound(NBTTagList list, int index) { return list.tagAt(index) instanceof NBTTagCompound ? (NBTTagCompound) list.tagAt(index) : new NBTTagCompound(); }
}
