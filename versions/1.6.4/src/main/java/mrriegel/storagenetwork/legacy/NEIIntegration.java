package mrriegel.storagenetwork.legacy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import mrriegel.storagenetwork.legacy.core.TerminalLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/** Client-only bridge to this target's optional NEI overlay API; never loaded by CommonProxy. */
public final class NEIIntegration implements InvocationHandler {
    // NEI shaped/shapeless recipes start at (25, 6), with 18-pixel cells.
    static final int OFFSET_X = TerminalLayout.CRAFT_GRID.x + 1 - 25;
    static final int OFFSET_Y = TerminalLayout.CRAFT_GRID.y + 1 - 6;
    private static boolean registered;
    private final Object delegate;
    private final Method ingredients;
    private NEIIntegration(Object delegate, Method ingredients) { this.delegate = delegate; this.ingredients = ingredients; }

    public static void register() {
        if (registered) return;
        registered = true;
        try {
            // Resolve NEI only after a client world/screen exists. Reflection keeps it
            // optional and accommodates the additional default methods in GTNH NEI.
            Class<?> api;
            try { api = Class.forName("codechicken.nei.api.API"); }
            catch (ClassNotFoundException absent) { return; }
            Class<?> overlay = Class.forName("codechicken.nei.api.IOverlayHandler");
            Class<?> recipe = Class.forName("codechicken.nei.recipe.IRecipeHandler");
            Object normal = Class.forName("codechicken.nei.recipe.DefaultOverlayHandler")
                .getConstructor(int.class, int.class).newInstance(OFFSET_X, OFFSET_Y);
            Object handler = Proxy.newProxyInstance(overlay.getClassLoader(), new Class<?>[] {overlay},
                new NEIIntegration(normal, recipe.getMethod("getIngredientStacks", int.class)));
            api.getMethod("registerGuiOverlayHandler", Class.class, overlay, String.class)
                .invoke(null, NetworkGui.class, handler, "crafting");
            api.getMethod("registerGuiOverlay", Class.class, String.class, int.class, int.class)
                .invoke(null, NetworkGui.class, "crafting", OFFSET_X, OFFSET_Y);
        } catch (ReflectiveOperationException failure) {
            Logger.getLogger("storagenetwork").log(Level.WARNING, "Unable to register optional NEI crafting transfer", failure);
        } catch (LinkageError failure) {
            Logger.getLogger("storagenetwork").log(Level.WARNING, "Incompatible optional NEI crafting API", failure);
        }
    }

    /** Exact destination mapping, excluding the result, player inventory and virtual browser. */
    static Slot craftingSlot(NetworkContainer container, int recipeX, int recipeY) {
        int x = recipeX - 25, y = recipeY - 6;
        if (!container.crafting || x < 0 || y < 0 || x >= 54 || y >= 54 || x % 18 != 0 || y % 18 != 0) return null;
        Slot slot = (Slot) container.inventorySlots.get(1 + x / 18 + y / 18 * 3);
        return slot.inventory == container.matrix && slot.xDisplayPosition == recipeX + OFFSET_X && slot.yDisplayPosition == recipeY + OFFSET_Y ? slot : null;
    }

    @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        if (method.getDeclaringClass() == Object.class) {
            if (name.equals("equals")) return proxy == args[0];
            if (name.equals("hashCode")) return System.identityHashCode(proxy);
            return "SSN crafting overlay";
        }
        // Expose recipe filling; keep GTNH's separate automatic craft loop disabled.
        // Players explicitly craft through the terminal's native result-slot clicks.
        if (name.equals("craft") || name.equals("canCraft")) return false;
        if (args != null && args.length >= 3 && args[0] instanceof GuiContainer) {
            GuiContainer gui = (GuiContainer) args[0];
            boolean valid = gui instanceof NetworkGui && gui.inventorySlots instanceof NetworkContainer;
            NetworkContainer container = valid ? (NetworkContainer) gui.inventorySlots : null;
            List<?> stacks = (List<?>) ingredients.invoke(args[1], args[2]);
            valid = valid && container.crafting && !stacks.isEmpty() && stacks.size() <= 9;
            boolean[] used = new boolean[10];
            if (valid) for (Object stack : stacks) {
                int x = stack.getClass().getField("relx").getInt(stack), y = stack.getClass().getField("rely").getInt(stack);
                Slot slot = craftingSlot(container, x, y);
                if (slot == null || used[slot.slotNumber]) { valid = false; break; }
                used[slot.slotNumber] = true;
                // Older NEI counts all player slots, even a locked bound remote.
                // Refuse recipes that could select that remote as an ingredient.
                if (container.remoteSlot >= 0) {
                    ItemStack remote = container.player.inventory.getStackInSlot(container.remoteSlot);
                    for (ItemStack choice : (ItemStack[]) stack.getClass().getField("items").get(stack))
                        if (remote != null && choice != null && remote.getItem() == choice.getItem()
                            && (choice.getItemDamage() == 32767 || remote.getItemDamage() == choice.getItemDamage())) valid = false;
                }
            }
            if (name.equals("canFillCraftingGrid")) return valid;
            if (name.equals("presenceOverlay") && !valid) return Collections.emptyList();
            if (name.equals("overlayRecipe") || name.equals("transferRecipe")) {
                if (!valid || !clearToPlayer(container)) return method.getReturnType() == int.class ? Integer.valueOf(0) : null;
            }
        }
        try { return method.invoke(delegate, args); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }

    private static boolean clearToPlayer(NetworkContainer container) {
        if (container.player.inventory.getItemStack() != null) return false;
        // Ordinary validated window clicks, not SSN's shift-click-to-network operation.
        // If inventory space runs out, return the cursor remainder to its original cell.
        for (int input = 1; input <= 9; input++) {
            Slot source = (Slot) container.inventorySlots.get(input);
            if (!source.getHasStack()) continue;
            click(container, input);
            for (int index = 10; index < 46 && container.player.inventory.getItemStack() != null; index++) {
                Slot destination = (Slot) container.inventorySlots.get(index);
                ItemStack held = container.player.inventory.getItemStack(), existing = destination.getStack();
                if (destination.inventory != container.player.inventory || !destination.isItemValid(held)) continue;
                if (existing != null && (!Stacks.INSTANCE.same(existing, held)
                    || existing.stackSize >= Math.min(existing.getMaxStackSize(), destination.getSlotStackLimit()))) continue;
                click(container, index);
            }
            if (container.player.inventory.getItemStack() != null) { click(container, input); return false; }
            if (source.getHasStack()) return false;
        }
        return true;
    }
    private static void click(NetworkContainer container, int slot) {
        Minecraft.getMinecraft().playerController.windowClick(container.windowId, slot, 0, 0, container.player);
    }
}
