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
        layout(); viewport(); containers(); craftingResults(); craftingLifecycle(); clickTransactions(); transfers(); topology(); snapshots();
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
        NBTTagCompound tileUpdate = new NBTTagCompound();
        TileNode.writeStacks(tileUpdate, "matrix", new ItemStack[] {stack(8, 1)}); node.readFromNBT(tileUpdate);
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
        listen(server, serverPlayer); connection.packets.clear();
        java.util.List recipes = net.minecraft.item.crafting.CraftingManager.getInstance().getRecipeList();
        ItemStack[] inputs = new ItemStack[9];
        for (int i = 0; i < 9; i++) inputs[i] = new ItemStack(item, 1);
        Object shaped = new net.minecraft.item.crafting.ShapedRecipes(3, 3, inputs, stack(901, 2));
        Object shapeless = new net.minecraft.item.crafting.ShapelessRecipes(stack(902, 1), java.util.Arrays.asList(new ItemStack(item, 1)));
        recipes.add(0, shaped); recipes.add(0, shapeless);
        try {
            for (int i = 0; i < 9; i++) server.matrix.setInventorySlotContents(i, new ItemStack(item, 2));
            assertNativePreview(server, connection, client, stack(901, 2), "shaped 3x3 preview");
            // Replaying unchanged inputs must retain the same locally computed preview.
            for (int i = 1; i <= 9; i++) client.putStackInSlot(i, new ItemStack(item, 2));
            check(ItemStack.areItemStacksEqual(client.getSlot(0).getStack(), stack(901, 2)), "input packet cannot erase synchronized preview");
            server.matrix.decrStackSize(8, 1);
            assertNativePreview(server, connection, client, stack(901, 2), "consumption retains same output");
            server.matrix.decrStackSize(8, 1);
            assertNativePreview(server, connection, client, null, "removal invalidates preview");
            for (int i = 1; i < 9; i++) server.matrix.setInventorySlotContents(i, null);
            assertNativePreview(server, connection, client, stack(902, 1), "shapeless preview");
            server.matrix.decrStackSize(0, 2);
            assertNativePreview(server, connection, client, null, "consumed shapeless clears preview");
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
    private static void assertNativePreview(NetworkContainer server, FixtureWorld.ResultPackets connection, NetworkContainer client, ItemStack expected, String label) throws Exception {
        server.detectAndSendChanges();
        for (Object packet : connection.packets) {
            int[] address = packetAddress(packet);
            check(address[0] == 37 && address[1] > 0 && address[1] < 46, label + " native input/player window/slot");
            client.putStackInSlot(address[1], ItemStack.copyItemStack(packetStack(packet)));
        }
        check(ItemStack.areItemStacksEqual(server.getSlot(0).getStack(), expected), label + " authoritative result");
        check(ItemStack.areItemStacksEqual(client.getSlot(0).getStack(), expected), label + " preview from native input updates");
        connection.packets.clear();
    }
    private static int[] packetAddress(Object packet) throws Exception {
        try { return new int[] {((Number) packet.getClass().getMethod("func_149175_c").invoke(packet)).intValue(), ((Number) packet.getClass().getMethod("func_149173_d").invoke(packet)).intValue()}; }
        catch (NoSuchMethodException minecraft164) { return new int[] {packet.getClass().getField("windowId").getInt(packet), packet.getClass().getField("itemSlot").getInt(packet)}; }
    }
    private static ItemStack packetStack(Object packet) throws Exception {
        try { return (ItemStack) packet.getClass().getMethod("func_149174_e").invoke(packet); }
        catch (NoSuchMethodException minecraft164) { return (ItemStack) packet.getClass().getField("myItemStack").get(packet); }
    }
    private static FixtureNode craftingNode() {
        FixtureNode node = new FixtureNode(1); node.network = new FixtureMaster(new NativeInventory(new FixtureChest(), -1, node)); return node;
    }
    private static void listen(NetworkContainer container, EntityPlayerMP player) throws Exception {
        java.lang.reflect.Method add;
        try { add = net.minecraft.inventory.Container.class.getMethod("addCraftingToCrafters", net.minecraft.inventory.ICrafting.class); }
        catch (NoSuchMethodException minecraft189) { add = net.minecraft.inventory.Container.class.getMethod("onCraftGuiOpened", net.minecraft.inventory.ICrafting.class); }
        add.invoke(container, player);
    }
    private static NetworkContainer observed(EntityPlayerMP player, FixtureNode node) throws Exception {
        NetworkContainer container = new NetworkContainer(player, node, -1); container.windowId = 37; player.openContainer = container;
        FixtureMaster network = node.network; node.network = null;
        try { listen(container, player); } finally { node.network = network; }
        ((FixtureWorld.ResultPackets) player.playerNetServerHandler).packets.clear(); return container;
    }
    private static int playerCount(EntityPlayer player) {
        int count = 0; for (int i = 0; i < 36; i++) count += Stacks.INSTANCE.count(player.inventory.getStackInSlot(i)); return count;
    }
    private static void fillPlayer(EntityPlayer player, ItemStack stack) { for (int i = 0; i < 36; i++) player.inventory.setInventorySlotContents(i, ItemStack.copyItemStack(stack)); }
    private static void craftingLifecycle() throws Exception {
        java.util.List recipes = net.minecraft.item.crafting.CraftingManager.getInstance().getRecipeList();
        ItemStack output = stack(911, 4);
        Object shapeless = new net.minecraft.item.crafting.ShapelessRecipes(output, java.util.Arrays.asList(new ItemStack(item, 1)));
        ItemStack[] shape = new ItemStack[9]; for (int i = 0; i < 9; i++) shape[i] = new ItemStack(item, 1);
        Object shaped = new net.minecraft.item.crafting.ShapedRecipes(3, 3, shape, stack(912, 2));
        Item milk;
        try { milk = (Item) Class.forName("net.minecraft.init.Items").getField("milk_bucket").get(null); }
        catch (ClassNotFoundException minecraft164) { milk = (Item) Item.class.getField("bucketMilk").get(null); }
        Object containers = new net.minecraft.item.crafting.ShapelessRecipes(stack(913, 1), java.util.Arrays.asList(new ItemStack(milk, 1)));
        recipes.add(0, shapeless); recipes.add(0, shaped); recipes.add(0, containers);
        try {
            EntityPlayerMP player = player(false); FixtureNode node = craftingNode(); NetworkContainer container = observed(player, node);
            container.matrix.setInventorySlotContents(0, new ItemStack(item, 64));
            container.slotClick(0, 0, 1, player);
            check(playerCount(player) == 256 && container.matrix.getStackInSlot(0) == null && container.getSlot(0).getStack() == null, "shift crafts beyond one stack, consuming 64 operations for 256 outputs");
            check(((FixturePlayer) player).dropped == 0, "shift delivers all outputs to inventory");
            fillPlayer(player, stack(999, 64)); player.inventory.setInventorySlotContents(0, stack(911, 58));
            container.matrix.setInventorySlotContents(0, new ItemStack(item, 5)); container.slotClick(0, 0, 1, player);
            check(player.inventory.getStackInSlot(0).stackSize == 62 && container.matrix.getStackInSlot(0).stackSize == 4, "partial space accepts only whole four-item craft");
            fillPlayer(player, stack(999, 64)); container.slotClick(0, 0, 1, player);
            check(container.matrix.getStackInSlot(0).stackSize == 4 && playerCount(player) == 2304, "full inventory consumes nothing");
            fillPlayer(player, null); container.matrix.setInventorySlotContents(0, new ItemStack(item, 3));
            container.slotClick(0, 0, 0, player);
            check(player.inventory.getItemStack().stackSize == 4 && container.matrix.getStackInSlot(0).stackSize == 2, "single shapeless native consumption");
            player.inventory.setItemStack(null); container.slotClick(0, 0, 1, player);
            check(container.matrix.getStackInSlot(0) == null && playerCount(player) == 8, "shift consumes remaining inputs");
            for (int i = 0; i < 9; i++) container.matrix.setInventorySlotContents(i, new ItemStack(item, 1));
            container.slotClick(0, 0, 0, player);
            check(player.inventory.getItemStack().stackSize == 2, "single shaped output uses native crafting slot");
            for (int i = 0; i < 9; i++) check(container.matrix.getStackInSlot(i) == null, "shaped consumes each grid cell once");
            player.inventory.setItemStack(null); fillPlayer(player, null);
            container.matrix.setInventorySlotContents(0, new ItemStack(milk, 1)); container.slotClick(0, 0, 1, player);
            check(playerCount(player) + Stacks.INSTANCE.count(container.matrix.getStackInSlot(0)) == 2 && container.getSlot(0).getStack() == null, "native bucket remainder and one crafted output survive");
            for (int i = 0; i < 9; i++) container.matrix.setInventorySlotContents(i, null);
            fillPlayer(player, null); player.inventory.setInventorySlotContents(20, stack(921, 60));
            container.matrix.setInventorySlotContents(0, stack(921, 9)); container.onContainerClosed(player);
            check(player.inventory.getStackInSlot(20).stackSize == 64 && playerCount(player) == 69 && container.getSlot(0).getStack() == null, "close merges occupied stacks before empty slots");
            check(container.matrix.getStackInSlot(0) == null, "close clears transient matrix");
            container.onContainerClosed(player); check(playerCount(player) == 69, "repeated close cannot duplicate");
            NetworkContainer reopened = observed(player, node); check(reopened.matrix.getStackInSlot(0) == null, "reopen starts empty");
            fillPlayer(player, stack(999, 64)); reopened.matrix.setInventorySlotContents(3, stack(922, 7)); reopened.onContainerClosed(player);
            check(((FixturePlayer) player).dropped == 7 && reopened.matrix.getStackInSlot(3) == null, "full inventory close drops exact remainder once");
            EntityPlayerMP first = player(false), second = player(false); FixtureNode shared = craftingNode();
            NetworkContainer one = observed(first, shared), two = observed(second, shared);
            one.matrix.setInventorySlotContents(2, stack(931, 11)); two.matrix.setInventorySlotContents(2, stack(932, 13));
            one.onContainerClosed(first);
            check(playerCount(first) == 11 && playerCount(second) == 0 && two.matrix.getStackInSlot(2).stackSize == 13, "closing one viewer cannot take another viewer's inputs");
            two.onContainerClosed(second); check(playerCount(second) == 13, "each viewer receives only its own items");
            NBTTagCompound old = new NBTTagCompound(); TileNode.writeStacks(old, "matrix", new ItemStack[] {stack(941, 17)}); shared.readFromNBT(old);
            fillPlayer(first, null); fillPlayer(second, null);
            one = observed(first, shared); two = observed(second, shared);
            check(playerCount(first) == 17 && playerCount(second) == 0 && one.matrix.getStackInSlot(0) == null && two.matrix.getStackInSlot(0) == null, "saved legacy inputs recovered once, outside isolated grids");
            shared.recoverLegacyCrafting(first); check(playerCount(first) == 17, "legacy recovery is idempotent");
            // Same slot writes as NEI's ordinary click transfer, followed by both craft modes.
            for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) NEIIntegration.craftingSlot(one, 25 + col * 18, 6 + row * 18).putStack(new ItemStack(item, 3));
            one.slotClick(0, 0, 0, first); check(first.inventory.getItemStack().stackSize == 2, "NEI-mapped shaped fill supports single craft");
            first.inventory.setItemStack(null); one.slotClick(0, 0, 1, first);
            check(one.matrix.getStackInSlot(0) == null && playerCount(first) == 21, "NEI-mapped fill supports repeated shift craft without network extraction");
        } finally { recipes.remove(shapeless); recipes.remove(shaped); recipes.remove(containers); }
    }
    /** Native prediction and server click handling, including replies delayed past later predictions. */
    private static void clickTransactions() throws Exception {
        EntityPlayerMP serverPlayer = player(false), clientPlayer = player(true);
        NetworkContainer server = observed(serverPlayer, craftingNode());
        NetworkContainer client = new NetworkContainer(clientPlayer, new FixtureNode(1), -1);
        client.windowId = server.windowId; clientPlayer.openContainer = client;
        FixtureWorld.ResultPackets connection = (FixtureWorld.ResultPackets) serverPlayer.playerNetServerHandler;
        Field owner = connection.getClass().getSuperclass().getDeclaredField("playerEntity");
        owner.setAccessible(true); owner.set(connection, serverPlayer);
        for (Field field : connection.getClass().getSuperclass().getDeclaredFields()) if (field.getType() == net.minecraft.util.IntHashMap.class) {
            field.setAccessible(true); field.set(connection, new net.minecraft.util.IntHashMap());
        }
        serverPlayer.inventory.setInventorySlotContents(9, new ItemStack(item, 16));
        clientPlayer.inventory.setInventorySlotContents(9, new ItemStack(item, 16));
        final Field force = NetworkContainer.class.getDeclaredField("force"); force.setAccessible(true); force.setBoolean(server, false);
        final NetworkContainer observedServer = server;
        // Ignore browser-catalog transport; the native confirmation precedes detectAndSendChanges.
        connection.afterSend = new Runnable() { public void run() {
            try { force.setBoolean(observedServer, false); } catch (IllegalAccessException failure) { throw new AssertionError(failure); }
        }};
        Field lastUpdate = NetworkContainer.class.getDeclaredField("lastUpdate"); lastUpdate.setAccessible(true); lastUpdate.setLong(server, 0L);
        server.detectAndSendChanges(); connection.packets.clear();
        visitRecipeScreen(client);
        queuedClicks(server, client, connection, new int[][] {{10, 0}, {1, 0}, {1, 1}, {2, 1}, {11, 0}, {1, 0}, {10, 0}});
        check(serverPlayer.inventory.getStackInSlot(9).stackSize == 8, "consecutive clicks return eight inputs to physical inventory");
        check(server.matrix.getStackInSlot(0) == null && server.matrix.getStackInSlot(1).stackSize == 1, "split/move leaves exact matrix remainder");
        // NEI uses these ordinary pickup/right-place/return clicks. This is not a real NEI launch.
        visitRecipeScreen(client);
        queuedClicks(server, client, connection, new int[][] {{10, 0}, {1, 1}, {10, 0}});
        java.util.List recipes = net.minecraft.item.crafting.CraftingManager.getInstance().getRecipeList();
        Object recipe = new net.minecraft.item.crafting.ShapelessRecipes(stack(955, 2), java.util.Arrays.asList(new ItemStack(item, 1), new ItemStack(item, 1)));
        recipes.add(0, recipe);
        try {
            server.onCraftMatrixChanged(server.matrix); client.onCraftMatrixChanged(client.matrix);
            queuedClicks(server, client, connection, new int[][] {{0, 0}, {12, 0}});
            check(server.matrix.getStackInSlot(0) == null && server.matrix.getStackInSlot(1) == null, "normal craft predicts native ingredient consumption");
            queuedClicks(server, client, connection, new int[][] {{10, 0}, {1, 1}, {2, 1}, {1, 1}, {2, 1}, {10, 0}});
            queuedClicks(server, client, connection, new int[][] {{0, 0, 1}});
            check(playerCount(serverPlayer) == playerCount(clientPlayer), "shift craft predicts delivered outputs");
            deleteHeldStack(server, client, connection);
            visitRecipeScreen(client);
            queuedClicks(server, client, connection, new int[][] {{10, 0}, {1, 1}, {10, 0}});
            int count = playerCount(serverPlayer) + Stacks.INSTANCE.count(server.matrix.getStackInSlot(0));
            client.onContainerClosed(clientPlayer); server.onContainerClosed(serverPlayer);
            check(playerCount(serverPlayer) == count && server.matrix.getStackInSlot(0) == null, "close returns remaining physical input once");
            NetworkContainer reopened = observed(serverPlayer, craftingNode());
            check(reopened.matrix.getStackInSlot(0) == null && playerCount(serverPlayer) == count, "reopen retains inventory and starts empty");
        } finally { recipes.remove(recipe); }
    }
    private static void visitRecipeScreen(NetworkContainer client) {
        ItemStack[] before = new ItemStack[47];
        for (int i = 0; i < 46; i++) before[i] = ItemStack.copyItemStack(client.getSlot(i).getStack());
        before[46] = ItemStack.copyItemStack(client.player.inventory.getItemStack());
        // Minecraft.displayGuiScreen invokes GuiContainer.onGuiClosed on entry to
        // NEI's GuiRecipe. RecipeHandlerRef later reuses that same GUI/container.
        client.onContainerClosed(client.player);
        check(client.canInteractWith(client.player), "NEI recipe screen does not permanently close the client container");
        for (int i = 0; i < 46; i++) check(ItemStack.areItemStacksEqual(before[i], client.getSlot(i).getStack()), "NEI navigation preserves client slot " + i);
        check(ItemStack.areItemStacksEqual(before[46], client.player.inventory.getItemStack()), "NEI navigation preserves cursor");
    }
    private static void deleteHeldStack(NetworkContainer server, NetworkContainer client, FixtureWorld.ResultPackets connection) throws Exception {
        queuedClicks(server, client, connection, new int[][] {{10, 0}});
        check(client.player.inventory.getItemStack() != null, "pick up physical stack for NEI deletion");
        visitRecipeScreen(client);
        // HeldItemHandler -> NEIClientUtils.deleteHeldItem -> SET_SLOT(-999),
        // authenticated on the server before NEIServerUtils.setSlotContents.
        client.player.inventory.setItemStack(null);
        if (Boolean.getBoolean("ssn.neiRegression")) {
            Class.forName("codechicken.nei.NEIServerUtils").getMethod("setSlotContents", EntityPlayer.class, int.class, ItemStack.class, boolean.class)
                .invoke(null, server.player, -999, null, true);
            System.out.println("REAL_NEI_HELD_DELETE_PASSED: " + Class.forName("codechicken.nei.NEIServerUtils").getProtectionDomain().getCodeSource().getLocation());
        } else server.player.inventory.setItemStack(null);
        check(server.player.inventory.getItemStack() == null && client.player.inventory.getItemStack() == null, "NEI deletes the cursor on both sides");
        queuedClicks(server, client, connection, new int[][] {{11, 0}, {10, 0}});
    }
    private static void queuedClicks(NetworkContainer server, NetworkContainer client, FixtureWorld.ResultPackets connection, int[][] moves) throws Exception {
        Class<?> packetType; String handler;
        try { packetType = Class.forName("net.minecraft.network.play.client.C0EPacketClickWindow"); handler = "processClickWindow"; }
        catch (ClassNotFoundException minecraft164) { packetType = Class.forName("net.minecraft.network.packet.Packet102WindowClick"); handler = "handleWindowClick"; }
        java.lang.reflect.Constructor<?> packet = packetType.getConstructor(int.class, int.class, int.class, int.class, ItemStack.class, short.class);
        java.lang.reflect.Method receive = connection.getClass().getSuperclass().getMethod(handler, packetType);
        List<Object> requests = new ArrayList<Object>();
        List<ItemStack[]> predictions = new ArrayList<ItemStack[]>();
        for (int[] move : moves) {
            int mode = move.length == 3 ? move[2] : 0;
            ItemStack predicted = client.slotClick(move[0], move[1], mode, client.player);
            requests.add(packet.newInstance(server.windowId, move[0], move[1], mode, ItemStack.copyItemStack(predicted), (short) requests.size()));
            ItemStack[] state = new ItemStack[47];
            for (int i = 0; i < 46; i++) state[i] = ItemStack.copyItemStack(client.getSlot(i).getStack());
            state[46] = ItemStack.copyItemStack(client.player.inventory.getItemStack()); predictions.add(state);
        }
        for (int operation = 0; operation < requests.size(); operation++) {
            receive.invoke(connection, requests.get(operation));
            ItemStack[] predicted = predictions.get(operation);
            for (int i = 0; i < 46; i++) check(ItemStack.areItemStacksEqual(predicted[i], server.getSlot(i).getStack()), "native server matches prediction after operation " + operation + " slot " + i);
            check(ItemStack.areItemStacksEqual(predicted[46], server.player.inventory.getItemStack()), "native cursor matches prediction after each operation");
            for (Object reply : connection.packets) {
                String name = reply.getClass().getSimpleName();
                if (name.equals("S32PacketConfirmTransaction") || name.equals("Packet106Transaction")) {
                    boolean accepted;
                    try { accepted = (Boolean) reply.getClass().getMethod("func_148888_e").invoke(reply); }
                    catch (NoSuchMethodException minecraft164) { accepted = reply.getClass().getField("accepted").getBoolean(reply); }
                    check(accepted, "native transaction accepts predicted click");
                } else if (name.equals("S2FPacketSetSlot") || name.equals("Packet103SetSlot")) {
                    int[] address = packetAddress(reply);
                    check(address[1] == 0, "accepted predicted clicks never echo stale real slots or cursor");
                    check(address[0] == server.windowId || address[0] == -1, "native reply uses current window or cursor");
                    if (address[0] == -1) client.player.inventory.setItemStack(ItemStack.copyItemStack(packetStack(reply)));
                    else client.putStackInSlot(address[1], ItemStack.copyItemStack(packetStack(reply)));
                } else throw new AssertionError("Unexpected correction during predicted clicks: " + name);
            }
            connection.packets.clear();
        }
        for (int i = 0; i < 46; i++) {
            check(server.getSlot(i).slotNumber == i && client.getSlot(i).slotNumber == i, "stable native slot identity after burst");
            check(ItemStack.areItemStacksEqual(server.getSlot(i).getStack(), client.getSlot(i).getStack()), "predicted client matches server slot " + i);
        }
        check(ItemStack.areItemStacksEqual(server.player.inventory.getItemStack(), client.player.inventory.getItemStack()), "predicted cursor matches server after burst");
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
        WorldServer world = allocate(FixtureWorld.class);
        Field remote = net.minecraft.world.World.class.getDeclaredField("isRemote"); remote.setAccessible(true); remote.setBoolean(world, client);
        EntityPlayerMP player = allocate(FixturePlayer.class); player.worldObj = world; player.inventory = new InventoryPlayer(player);
        player.capabilities = new net.minecraft.entity.player.PlayerCapabilities();
        FixtureWorld.ResultPackets connection = allocate(FixtureWorld.ResultPackets.class); connection.packets = new ArrayList<Object>(); player.playerNetServerHandler = connection;
        return player;
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
        public boolean isSpectator() { return false; }
        @Override public void addStat(net.minecraft.stats.StatBase stat, int amount) {}
        public net.minecraft.entity.item.EntityItem dropPlayerItem(ItemStack stack) { dropped += stack.stackSize; return null; }
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
