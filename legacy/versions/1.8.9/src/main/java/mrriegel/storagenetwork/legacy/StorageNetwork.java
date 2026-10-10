package mrriegel.storagenetwork.legacy;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import mrriegel.storagenetwork.legacy.core.Position;

@Mod(modid = StorageNetwork.MODID, name = "Simple Storage Network", version = BuildVersion.VERSION, acceptedMinecraftVersions = "[1.8.9]")
public class StorageNetwork {
    public static final String MODID = "storagenetwork";
    @Instance(MODID) public static StorageNetwork instance;
    @SidedProxy(clientSide = "mrriegel.storagenetwork.legacy.ClientProxy", serverSide = "mrriegel.storagenetwork.legacy.CommonProxy")
    public static CommonProxy proxy;
    public static final String[] NAMES = {"master", "request", "inventory", "kabel", "storage_kabel", "ex_kabel", "im_kabel", "process_kabel", "simple_kabel", "controller"};
    public static final NetworkBlock[] blocks = new NetworkBlock[NAMES.length];
    public static Item upgrade, remote, picker, collector;
    public static int refreshTicks = 200, remoteRange = 128;
    public static boolean reloadOnUnload = true, facadeCollision = true, logEverything;
    public static String[] blacklist = {"extrautils2:playerchest"}, facadeBlacklist = {"minecraft:double_plant", "minecraft:ladder", "minecraft:rail", "minecraft:detector_rail", "minecraft:activator_rail", "minecraft:golden_rail", "minecraft:waterlily"};
    public static final CreativeTabs TAB = new CreativeTabs(MODID) {
        @Override public Item getTabIconItem() { return Item.getItemFromBlock(blocks[0]); }
    };
    @EventHandler public void preInit(FMLPreInitializationEvent event) {
        Configuration config = new Configuration(event.getSuggestedConfigurationFile()); config.load();
        refreshTicks = Math.max(1, Math.min(10000, config.get("general", "AutoRefreshTicks", 200).getInt(200)));
        remoteRange = Math.max(1, Math.min(10000, config.get("general", "StorageRemoteRange", 128).getInt(128)));
        reloadOnUnload = config.get("general", "ReloadNetworkWhenUnloadChunk", true).getBoolean(true);
        facadeCollision = config.get("general", "facadesUseCollisionBoundingBox", true).getBoolean(true);
        logEverything = config.get("general", "LogSpamAllTheThings", false).getBoolean(false);
        blacklist = config.get("general", "BlacklistBlocks", blacklist).getStringList();
        facadeBlacklist = config.get("general", "BlacklistFacadeCableItems", facadeBlacklist).getStringList();
        config.save();
        for (int kind = 0; kind < blocks.length; kind++) { blocks[kind] = kind >= 3 && kind <= 8 ? new NetworkBlock.CableBlock(kind) : new NetworkBlock(kind); GameRegistry.registerBlock(blocks[kind], NAMES[kind]); }
        upgrade = new NetworkItem("upgrade", 6); remote = new RemoteItem("remote", 4); picker = new RemoteItem("picker_remote", 1); collector = new RemoteItem("collector_remote", 1);
        GameRegistry.registerItem(upgrade, "upgrade"); GameRegistry.registerItem(remote, "remote");
        GameRegistry.registerItem(picker, "picker_remote"); GameRegistry.registerItem(collector, "collector_remote");
        GameRegistry.registerTileEntity(TileMaster.class, "storagenetwork:tilemaster");
        GameRegistry.registerTileEntity(TileNode.class, "storagenetwork:tilenode");
        Protocol.init(); MinecraftForge.EVENT_BUS.register(new RemoteEvents());
        FMLCommonHandler.instance().bus().register(new Protocol.Ticks());
        for (net.minecraftforge.fml.common.discovery.ASMDataTable.ASMData data : event.getAsmData().getAll(mrriegel.storagenetwork.legacy.api.StorageNetworkPlugin.class.getName())) {
            try { Object plugin = Class.forName(data.getClassName()).getDeclaredConstructor().newInstance(); if (!(plugin instanceof mrriegel.storagenetwork.legacy.api.LegacyPlugin)) throw new IllegalStateException("Invalid SSN legacy plugin " + data.getClassName()); ((mrriegel.storagenetwork.legacy.api.LegacyPlugin) plugin).helpersReady(mrriegel.storagenetwork.legacy.api.LegacyApi.INSTANCE); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException("Unable to initialize SSN plugin " + data.getClassName(), error); }
        }
        proxy.preInit();
    }
    @EventHandler public void postInit(net.minecraftforge.fml.common.event.FMLPostInitializationEvent event) { mrriegel.storagenetwork.legacy.api.LegacyApi.INSTANCE.finishRegistration(); }
    @EventHandler public void init(FMLInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(this, proxy); Recipes.register(); proxy.init();
    }
    public static boolean allowed(String name, String[] blacklist) { for (String entry : blacklist) if (entry.equals(name)) return false; return true; }
    public static void drop(World world, Position pos, ItemStack stack) {
        if (world != null && !world.isRemote && !Stacks.INSTANCE.empty(stack)) world.spawnEntityInWorld(new EntityItem(world, pos.x + .5, pos.y + .5, pos.z + .5, stack.copy()));
    }
    public static void removeExtraMaster(World world, Position pos) { Platform.block(world, pos).dropBlockAsItem(world, new net.minecraft.util.BlockPos(pos.x, pos.y, pos.z), Platform.block(world, pos).getDefaultState(), 0); world.setBlockToAir(new net.minecraft.util.BlockPos(pos.x, pos.y, pos.z)); }
}
