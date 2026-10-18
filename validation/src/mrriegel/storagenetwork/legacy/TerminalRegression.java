package mrriegel.storagenetwork.legacy;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import mrriegel.storagenetwork.legacy.core.BrowserViewport;
import mrriegel.storagenetwork.legacy.core.TerminalLayout;
import mrriegel.storagenetwork.legacy.core.TerminalLayout.Rect;
import mrriegel.storagenetwork.legacy.core.Transfers;
import mrriegel.storagenetwork.legacy.core.Position;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.InventoryLargeChest;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;

/** Deterministic native-class checks, without OpenGL, sockets, or a running world. */
public final class TerminalRegression {
    private static Item item;
    private static int checks;

    public static void main(String[] args) throws Exception {
        try {
            Class<?> bootstrap = Class.forName("net.minecraft.init.Bootstrap");
            try { bootstrap.getMethod("register").invoke(null); }
            catch (NoSuchMethodException oldMappings) { bootstrap.getMethod("func_151354_b").invoke(null); }
            item = (Item) Class.forName("net.minecraft.init.Items").getField("stick").get(null);
        } catch (ClassNotFoundException minecraft164) {
            // Match the dedicated server's initialization order, before touching Item.
            Class.forName("net.minecraft.stats.StatList").getMethod("nopInit").invoke(null);
            item = (Item) Item.class.getField("stick").get(null);
        }
        layout(); viewport(); containers(); craftingResults(); transfers(); topology(); snapshots();
        System.out.println("TERMINAL_REGRESSION_PASSED: " + checks + " checks (headless native classes; no live multiplayer/render acceptance)");
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static void layout() {
        Rect[] controls = {TerminalLayout.SEARCH, TerminalLayout.SORT, TerminalLayout.DIRECTION, TerminalLayout.REFRESH,
            TerminalLayout.BROWSER, TerminalLayout.SCROLLBAR, TerminalLayout.CRAFT_GRID, TerminalLayout.RESULT, TerminalLayout.CLEAR};
        for (int i = 0; i < controls.length; i++) for (int j = i + 1; j < controls.length; j++) {
            Rect a = controls[i], b = controls[j];
            check(a.x + a.width <= b.x || b.x + b.width <= a.x || a.y + a.height <= b.y || b.y + b.height <= a.y, "overlapping controls " + i + "/" + j);
        }
        for (boolean crafting : new boolean[] {false, true}) {
            TerminalLayout layout = new TerminalLayout(crafting);
            check(layout.width <= 320 && TerminalLayout.HEIGHT <= 240, "minimum scaled screen");
            check(layout.playerX >= 6 && layout.playerX + 162 < layout.width, "player inventory inside frame");
            check(TerminalLayout.HOTBAR_Y + 18 < TerminalLayout.HEIGHT, "hotbar inside frame");
        }
        for (int kind = 0; kind < 10; kind++) check(TerminalLayout.hasBlockGui(kind) == (kind == 1 || kind == 2 || kind >= 5 && kind <= 7 || kind == 9), "GUI routing " + kind);
        check(!TerminalLayout.hasBlockGui(4) && !TerminalLayout.hasBlockGui(8), "ordinary links have no GUI");
    }
    private static void viewport() {
        BrowserViewport view = new BrowserViewport(9, 4);
        int[] sizes = {0, 1, 27, 36, 37, 54, 500, 1000, 4096, 100000};
        for (int size : sizes) {
            view.update(size, true);
            check(view.first() == 0, "initial offset");
            int expectedMax = Math.max(0, (size + 8) / 9 - 4);
            check(view.maxRow() == expectedMax, "last row " + size);
            view.setRow(Integer.MAX_VALUE); check(view.row() == expectedMax, "clamp end");
            view.scroll(-1); check(view.row() == expectedMax, "wheel past end");
            view.drag(Integer.MAX_VALUE, TerminalLayout.SCROLLBAR.height); check(view.row() == expectedMax, "drag end");
            view.drag(Integer.MIN_VALUE, TerminalLayout.SCROLLBAR.height); check(view.row() == 0, "drag start");
            for (int row = 0; row <= expectedMax; row++) {
                view.setRow(row);
                int expected = row * 9;
                check(view.indexAt(TerminalLayout.BROWSER, 8, 44) == (expected < size ? expected : -1), "row hit mapping");
                check(view.first() % 9 == 0 && view.capacity() == 36, "bounded row viewport");
            }
            check(view.indexAt(TerminalLayout.BROWSER, 7, 44) == -1 && view.indexAt(TerminalLayout.BROWSER, 170, 44) == -1, "outside browser");
        }
        view.update(4096, true); view.setRow(150); view.update(5000, false); check(view.row() == 150, "preserve on growth");
        view.update(500, false); check(view.row() == view.maxRow(), "clamp after removals");
        view.update(7, true); check(view.row() == 0, "search while scrolled");
        for (int i = 0; i < 1000; i++) { view.update(i % 2 == 0 ? 4096 : i % 57, false); view.scroll(-1); check(view.row() <= view.maxRow(), "rapid catalog changes"); }
    }
    private static void containers() throws Exception {
        EntityPlayerMP player = player(true);
        FixtureNode node = new FixtureNode(1);
        NetworkContainer container = new NetworkContainer(player, node, -1);
        check(container.crafting && container.inventorySlots.size() == 46, "fixed result + nine inputs + 36 player slots");
        List<Slot> slots = new ArrayList<Slot>();
        for (Object object : container.inventorySlots) slots.add((Slot) object);
        for (int size : new int[] {0, 27, 54, 500, 4096, 100000, 3, 0}) {
            container.catalog.clear(); for (int i = 0; i < size; i++) container.catalog.add(stack(i, 64));
            check(container.inventorySlots.size() == 46, "catalog never changes slot count");
            for (int i = 0; i < slots.size(); i++) check(container.inventorySlots.get(i) == slots.get(i), "permanent slot identity");
            Slot result = slots.get(0); check(result.xDisplayPosition == TerminalLayout.RESULT.x + 1 && result.yDisplayPosition == TerminalLayout.RESULT.y + 1, "fixed result coordinates");
            for (int i = 1; i <= 9; i++) {
                Slot slot = slots.get(i); check(slot.xDisplayPosition == TerminalLayout.CRAFT_GRID.x + 1 + (i - 1) % 3 * 18 && slot.yDisplayPosition == TerminalLayout.CRAFT_GRID.y + 1 + (i - 1) / 3 * 18, "fixed matrix coordinates");
            }
        }
        ItemStack synchronizedInput = stack(7, 23); container.matrix.setInventorySlotContents(0, synchronizedInput);
        node.matrix[0] = stack(8, 1); TileNode.readStacks(new NBTTagCompound(), "matrix", node.matrix);
        check(container.matrix.getStackInSlot(0) == synchronizedInput, "tile NBT cannot erase client container matrix");
        NetworkContainer inventory = new NetworkContainer(player, new FixtureNode(2), -1);
        check(!inventory.crafting && inventory.inventorySlots.size() == 36, "inventory-only fixed slots");
        check(((Slot) container.inventorySlots.get(10)).xDisplayPosition == container.layout.playerX + 1, "player coordinates from shared layout");
    }
    private static void craftingResults() throws Exception {
        EntityPlayerMP serverPlayer = player(false), clientPlayer = player(true);
        FixtureNode node = new FixtureNode(1);
        NetworkContainer server = new NetworkContainer(serverPlayer, node, -1);
        NetworkContainer client = new NetworkContainer(clientPlayer, new FixtureNode(1), -1);
        FixtureWorld.ResultPackets connection = allocate(FixtureWorld.ResultPackets.class);
        connection.packets = new ArrayList<Object>(); serverPlayer.playerNetServerHandler = connection;
        server.windowId = client.windowId = 37; serverPlayer.openContainer = server;
        java.util.List recipes = net.minecraft.item.crafting.CraftingManager.getInstance().getRecipeList();
        ItemStack[] inputs = new ItemStack[9];
        for (int i = 0; i < 9; i++) inputs[i] = new ItemStack(item, 1);
        Object shaped = new net.minecraft.item.crafting.ShapedRecipes(3, 3, inputs, stack(901, 2));
        Object shapeless = new net.minecraft.item.crafting.ShapelessRecipes(stack(902, 1), java.util.Arrays.asList(new ItemStack(item, 1)));
        recipes.add(0, shaped); recipes.add(0, shapeless);
        try {
            for (int i = 0; i < 9; i++) server.matrix.setInventorySlotContents(i, new ItemStack(item, 2));
            assertResultPacket(connection, client, stack(901, 2), "shaped 3x3 preview");
            // Input packets must never recompute or erase the authoritative result on the client.
            for (int i = 1; i <= 9; i++) client.putStackInSlot(i, new ItemStack(item, 2));
            check(ItemStack.areItemStacksEqual(client.getSlot(0).getStack(), stack(901, 2)), "input packet cannot erase synchronized preview");
            server.matrix.decrStackSize(8, 1);
            assertResultPacket(connection, client, stack(901, 2), "consumption retains same output");
            server.matrix.decrStackSize(8, 1);
            assertResultPacket(connection, client, null, "removal invalidates preview");
            for (int i = 1; i < 9; i++) server.matrix.setInventorySlotContents(i, null);
            assertResultPacket(connection, client, stack(902, 1), "shapeless preview");
            server.matrix.decrStackSize(0, 2);
            assertResultPacket(connection, client, null, "consumed shapeless clears preview");
            NetworkContainer inventory = new NetworkContainer(clientPlayer, new FixtureNode(2), -1);
            for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
                Slot mapped = NEIIntegration.craftingSlot(client, 25 + col * 18, 6 + row * 18);
                check(mapped == client.getSlot(1 + row * 3 + col) && mapped.inventory == client.matrix, "NEI maps only native grid cells");
                check(NEIIntegration.craftingSlot(inventory, 25 + col * 18, 6 + row * 18) == null, "NEI rejects noncrafting screen");
            }
            check(NEIIntegration.craftingSlot(client, 119, 24) == null, "NEI excludes result");
            check(NEIIntegration.craftingSlot(client, 26, 6) == null && NEIIntegration.craftingSlot(client, 7, 6) == null, "NEI rejects off-grid coordinates");
            check(client.inventorySlots.size() == 46, "NEI keeps virtual catalog out of real slots");
        } finally { recipes.remove(shaped); recipes.remove(shapeless); }
    }
    private static void assertResultPacket(FixtureWorld.ResultPackets connection, NetworkContainer client, ItemStack expected, String label) throws Exception {
        check(!connection.packets.isEmpty(), label + " sent packet");
        Object packet = connection.packets.get(connection.packets.size() - 1);
        int window, slot; ItemStack actual;
        try {
            window = ((Number) packet.getClass().getMethod("func_149175_c").invoke(packet)).intValue();
            slot = ((Number) packet.getClass().getMethod("func_149173_d").invoke(packet)).intValue();
            actual = (ItemStack) packet.getClass().getMethod("func_149174_e").invoke(packet);
        } catch (NoSuchMethodException minecraft164) {
            window = packet.getClass().getField("windowId").getInt(packet);
            slot = packet.getClass().getField("itemSlot").getInt(packet);
            actual = (ItemStack) packet.getClass().getField("myItemStack").get(packet);
        }
        check(window == 37 && slot == 0 && ItemStack.areItemStacksEqual(actual, expected), label + " authoritative window/slot/stack");
        client.putStackInSlot(slot, actual);
        check(ItemStack.areItemStacksEqual(client.getSlot(0).getStack(), expected), label + " visible inventory receives packet");
        connection.packets.clear();
    }
    private static void transfers() throws Exception {
        FixtureChest left = new FixtureChest(), right = new FixtureChest();
        Object chestIdentity = new Object();
        NativeInventory single = new NativeInventory(left, -1, left);
        NativeInventory doubleChest = new NativeInventory(new InventoryLargeChest("double", left, right), -1, chestIdentity);
        check(single.slots() == 27 && doubleChest.slots() == 54, "single/double chest sizes");
        check(doubleChest.identity() == chestIdentity, "canonical double chest identity retained");
        ItemStack offered = stack(1, 4096);
        ItemStack remainder = Transfers.insert(doubleChest, offered, false, Stacks.INSTANCE);
        check(Stacks.INSTANCE.count(remainder) == 4096 - 54 * 64 && offered.stackSize == 4096, "thousands of items and exact remainder");
        EntityPlayerMP player = player(false);
        FixtureNode node = new FixtureNode(1); node.network = new FixtureMaster(doubleChest);
        NetworkContainer container = new NetworkContainer(player, node, -1);
        for (int i = 0; i < 36; i++) player.inventory.setInventorySlotContents(i, stack(1, 64));
        // Partial player stack receives exactly four; remaining items follow the existing drop fallback.
        player.inventory.setInventorySlotContents(0, stack(1, 60));
        // Use empty grid slots here to avoid depending on a live EntityPlayer drop implementation.
        container.matrix.setInventorySlotContents(0, stack(1, 4)); container.clearRecipe();
        check(container.matrix.getStackInSlot(0) == null && player.inventory.getStackInSlot(0).stackSize == 64, "clear into partial player stack with full network");
        // Full player inventory, partial network capacity: grid must clear exactly once into storage.
        right.setInventorySlotContents(26, stack(1, 60));
        container.matrix.setInventorySlotContents(1, stack(1, 4)); container.clearRecipe();
        check(container.matrix.getStackInSlot(1) == null && right.getStackInSlot(26).stackSize == 64, "clear into partial network with full player inventory");
        int before = total(doubleChest); container.clearRecipe(); check(total(doubleChest) == before, "repeated clear does not duplicate");
        container.matrix.setInventorySlotContents(0, stack(1, 17)); container.clearRecipe();
        check(container.matrix.getStackInSlot(0) == null && ((FixturePlayer) player).dropped == 17 && total(doubleChest) == before, "full network and full inventory preserve dropped remainder");
        container.matrix.setInventorySlotContents(2, stack(1, 7));
        check(container.transferStackInSlot(player, 3) == null && container.matrix.getStackInSlot(2).stackSize == 7, "full network shift-click makes no progress");
    }
    private static int total(NativeInventory inventory) { int result = 0; for (int i = 0; i < inventory.slots(); i++) result += Stacks.INSTANCE.count(inventory.peek(i)); return result; }
    private static void topology() throws Exception {
        final FixtureWorld world = allocate(FixtureWorld.class); world.tiles = new java.util.HashMap<Position, TileEntity>();
        Field provider = net.minecraft.world.World.class.getDeclaredField("provider"); provider.setAccessible(true); provider.set(world, new net.minecraft.world.WorldProviderSurface());
        world.chunks = (net.minecraft.world.chunk.IChunkProvider) java.lang.reflect.Proxy.newProxyInstance(
            TerminalRegression.class.getClassLoader(), new Class<?>[] {net.minecraft.world.chunk.IChunkProvider.class}, new java.lang.reflect.InvocationHandler() {
                public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
                    if (method.getName().equals("chunkExists")) return true;
                    throw new UnsupportedOperationException(method.getName());
                }
            });
        GraphMaster master = new GraphMaster(); put(world, master, 0, 64, 0);
        check(master.stacks().isEmpty(), "empty native graph");
        List<TileEntityChest> chests = new ArrayList<TileEntityChest>();
        for (int x = 1; x <= 256; x++) put(world, new FixtureNode(x % 4 == 0 || x == 5 ? 4 : 3), x, 64, 0);
        int distinct = 0;
        for (int x = 4; x <= 256; x += 4) {
            FixtureChest chest = new FixtureChest();
            for (int slot = 0; slot < 27; slot++) chest.setInventorySlotContents(slot, stack(distinct++, 64));
            chests.add(chest); put(world, chest, x, 64, 1);
        }
        FixtureChest partner = new FixtureChest();
        for (int slot = 0; slot < 27; slot++) partner.setInventorySlotContents(slot, stack(distinct++, 64));
        put(world, partner, 5, 64, 1);
        NativeInventory a = (NativeInventory) NativeInventory.at(world, new Position(0, 4, 64, 1), -1);
        NativeInventory b = (NativeInventory) NativeInventory.at(world, new Position(0, 5, 64, 1), -1);
        check(a.slots() == 54 && b.slots() == 54 && a.identity().equals(b.identity()), "native double-chest lookup has one identity from either half");
        master.refresh(); List<ItemStack> catalog = master.stacks();
        check(catalog.size() == distinct, "65 chests aggregate every distinct stack once");
        int sum = 0; for (ItemStack stack : catalog) sum += stack.stackSize;
        check(sum == distinct * 64, "many-chest totals have no duplicated inventories");
        BrowserViewport view = new BrowserViewport(9, 4); view.update(catalog.size(), false); view.setRow(Integer.MAX_VALUE);
        for (int i = 0; i < 40; i++) {
            TileEntityChest chest = chests.get(i); chest.setInventorySlotContents(0, null);
            List<ItemStack> changed = master.stacks(); view.update(changed.size(), false);
            check(changed.size() == distinct - i - 1 && view.row() <= view.maxRow(), "live inventory mutation while browsing");
        }
        world.tiles.remove(new Position(0, 128, 64, 0)); master.refresh();
        check(master.stacks().size() < distinct / 2, "disconnecting inventories removes downstream entries");
        put(world, new FixtureNode(4), 128, 64, 0); master.refresh();
        check(master.stacks().size() == distinct - 40, "reconnecting inventories restores entries without duplicates");
        check(master.request(partner.getStackInSlot(0), 11, false, true, false, true, null).stackSize == 11, "native request from double chest");
        check(partner.getStackInSlot(0).stackSize == 53, "native extraction occurs once");
    }
    private static void put(FixtureWorld world, TileEntity tile, int x, int y, int z) throws Exception {
        tile.setWorldObj(world);
        try { tile.getClass().getField("xCoord").setInt(tile, x); tile.getClass().getField("yCoord").setInt(tile, y); tile.getClass().getField("zCoord").setInt(tile, z); }
        catch (NoSuchFieldException minecraft189) {
            Class<?> pos = Class.forName("net.minecraft.util.BlockPos");
            tile.getClass().getMethod("setPos", pos).invoke(tile, pos.getConstructor(int.class, int.class, int.class).newInstance(x, y, z));
        }
        world.tiles.put(new Position(0, x, y, z), tile);
    }
    private static void snapshots() {
        NBTTagCompound snapshot = new NBTTagCompound(); NBTTagList entries = new NBTTagList();
        java.util.Random random = new java.util.Random(1641710189L);
        for (int i = 0; i < 4096; i++) {
            NBTTagCompound entry = new NBTTagCompound(); stack(i, 1).writeToNBT(entry);
            entry.setInteger("SSNCount", 100000 + i); entry.setInteger("index", i); entries.appendTag(entry);
            entry.setString("fixturePayload", Long.toHexString(random.nextLong()) + Long.toHexString(random.nextLong()));
        }
        snapshot.setTag("changes", entries); snapshot.setInteger("size", 4096); snapshot.setInteger("revision", 42); snapshot.setInteger("window", 7);
        byte[] encoded = Protocol.encode(snapshot);
        NBTTagCompound decoded = Protocol.decode(encoded);
        check(decoded.equals(snapshot), "large catalog native compression round trip");
        check(encoded.length > 30000, "large snapshot spans native fragments");
        NBTTagList actual = Platform.list(decoded, "changes");
        check(actual.tagCount() == 4096 && Platform.compound(actual, 4095).getInteger("SSNCount") == 104095, "aggregate counts survive NBT byte count limits");
    }
    private static ItemStack stack(int variant, int count) {
        ItemStack stack = new ItemStack(item, count, 0); NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("terminalFixture", variant); stack.setTagCompound(tag); return stack;
    }
    private static EntityPlayerMP player(boolean client) throws Exception {
        WorldServer world = allocate(WorldServer.class);
        Field remote = net.minecraft.world.World.class.getDeclaredField("isRemote"); remote.setAccessible(true); remote.setBoolean(world, client);
        EntityPlayerMP player = allocate(FixturePlayer.class); player.worldObj = world; player.inventory = new InventoryPlayer(player);
        player.capabilities = new net.minecraft.entity.player.PlayerCapabilities(); return player;
    }
    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe"); Field field = unsafe.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.cast(unsafe.getMethod("allocateInstance", Class.class).invoke(field.get(null), type));
    }
    private static final class FixtureNode extends TileNode {
        final int type; FixtureMaster network;
        FixtureNode(int type) { this.type = type; }
        @Override public int kind() { return type; }
        @Override public TileMaster master() { return network; }
        @Override public boolean isUseableByPlayer(EntityPlayer player) { return true; }
        public void markDirty() {}
        public void onInventoryChanged() {}
    }
    private static final class FixtureMaster extends TileMaster {
        final NativeInventory inventory;
        FixtureMaster(NativeInventory inventory) { this.inventory = inventory; }
        @Override public boolean contains(TileNode node) { return true; }
        @Override public ItemStack insert(ItemStack offered, boolean simulate, Object excluded) { return Transfers.insert(inventory, offered, simulate, Stacks.INSTANCE); }
    }
    private static final class FixturePlayer extends EntityPlayerMP {
        int dropped;
        // Allocated without invoking this constructor; no server or connection exists.
        FixturePlayer() { super(null, null, null, null); }
        @Override public net.minecraft.entity.item.EntityItem dropPlayerItemWithRandomChoice(ItemStack stack, boolean scatter) { dropped += stack.stackSize; return null; }
    }
    private static final class FixtureChest extends TileEntityChest {
        public void markDirty() {}
        public void onInventoryChanged() {}
    }
    private static final class GraphMaster extends TileMaster {
        public void markDirty() {}
        public void onInventoryChanged() {}
    }
}
