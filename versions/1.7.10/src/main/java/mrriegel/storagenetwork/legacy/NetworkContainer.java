package mrriegel.storagenetwork.legacy;

import java.util.ArrayList;
import java.util.List;
import mrriegel.storagenetwork.legacy.core.Transfers;
import mrriegel.storagenetwork.legacy.core.TerminalLayout;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

public final class NetworkContainer extends Container {
    public final EntityPlayer player;
    public final TileNode node;
    public final int remoteSlot;
    private final ItemStack remoteIdentity;
    public final boolean cable, controller, crafting;
    public final TerminalLayout layout;
    public final InventoryCrafting matrix;
    private final InventoryCraftResult result = new InventoryCraftResult();
    public List<ItemStack> catalog = new ArrayList<ItemStack>();
    public int revision;
    private NBTTagCompound lastState;
    private List<ItemStack> sent = new ArrayList<ItemStack>();
    private long lastUpdate = Long.MIN_VALUE;
    private boolean force = true, resetCatalog = true, batching, closed;
    public NetworkContainer(EntityPlayer player, TileNode node, int remoteSlot) {
        this.player = player; this.node = node; this.remoteSlot = remoteSlot;
        remoteIdentity = remoteSlot >= 0 ? player.inventory.getStackInSlot(remoteSlot) : null;
        cable = remoteSlot < 0 && node != null && node.kind() >= 5 && node.kind() <= 7;
        controller = remoteSlot < 0 && node != null && node.kind() == 9;
        crafting = !cable && !controller && (remoteSlot < 0 ? node != null && node.kind() == 1 : remoteIdentity != null && remoteIdentity.getItemDamage() != 3);
        layout = new TerminalLayout(crafting);
        // Inputs belong to this open container, never to the table or another viewer.
        matrix = new InventoryCrafting(this, 3, 3);
        if (crafting) {
            addSlotToContainer(new CraftSlot(player, matrix, result, 0, TerminalLayout.RESULT.x + 1, TerminalLayout.RESULT.y + 1));
            for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) addSlotToContainer(new Slot(matrix, row * 3 + col, TerminalLayout.CRAFT_GRID.x + 1 + col * TerminalLayout.CELL, TerminalLayout.CRAFT_GRID.y + 1 + row * TerminalLayout.CELL));
        }
        if (cable && (node.kind() == 5 || node.kind() == 6)) {
            for (int i = 0; i < 4; i++) addSlotToContainer(new Slot(node, i, 98 + i * 18, 6) {
                @Override public int getSlotStackLimit() { return 1; }
                @Override public boolean isItemValid(ItemStack stack) { return stack != null && stack.getItem() == StorageNetwork.upgrade; }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) bindPlayerSlot(row * 9 + col + 9, (cable || controller ? 8 : layout.playerX + 1) + col * TerminalLayout.CELL, (cable || controller ? 174 : TerminalLayout.PLAYER_Y + 1) + row * TerminalLayout.CELL);
        for (int col = 0; col < 9; col++) bindPlayerSlot(col, (cable || controller ? 8 : layout.playerX + 1) + col * TerminalLayout.CELL, cable || controller ? 232 : TerminalLayout.HOTBAR_Y + 1);
        if (crafting && node != null && !player.worldObj.isRemote) node.recoverLegacyCrafting(player);
        onCraftMatrixChanged(matrix);
    }
    private void bindPlayerSlot(final int slot, int x, int y) {
        addSlotToContainer(new Slot(player.inventory, slot, x, y) {
            @Override public boolean canTakeStack(EntityPlayer player) { return slot != remoteSlot; }
            @Override public boolean isItemValid(ItemStack stack) { return slot != remoteSlot; }
        });
    }
    public TileMaster master() { return remoteSlot >= 0 ? RemoteItem.validMaster(player, player.inventory.getStackInSlot(remoteSlot)) : node instanceof TileMaster ? (TileMaster) node : node == null ? null : node.master(); }
    @Override public boolean canInteractWith(EntityPlayer player) {
        if (closed) return false;
        if (player.worldObj.isRemote) return true;
        if (remoteSlot >= 0) return player.inventory.getStackInSlot(remoteSlot) == remoteIdentity && master() != null;
        if (node == null || !node.isUseableByPlayer(player)) return false;
        TileMaster master = master(); return cable || master != null && master.contains(node);
    }
    public void refresh() { force = true; }
    public void resendCatalog() { force = true; resetCatalog = true; }
    @Override public void detectAndSendChanges() {
        // Native listeners suppress accepted click echoes while the client predicts ahead.
        // Recompute before snapshotting, including count changes made in place by clicks.
        if (crafting && !batching) for (int i = 0; i < 9; i++)
            if (!ItemStack.areItemStacksEqual((ItemStack) inventoryItemStacks.get(i + 1), matrix.getStackInSlot(i))) {
                updateCraftingResult(); break;
            }
        super.detectAndSendChanges();
        if (player.worldObj.isRemote || !(player instanceof EntityPlayerMP) || !canInteractWith(player)) return;
        long now = player.worldObj.getTotalWorldTime(); if (!force && now - lastUpdate < 10) return;
        lastUpdate = now;
        NBTTagCompound update = new NBTTagCompound(); update.setInteger("window", windowId);
        if (cable) {
            NBTTagCompound state = new NBTTagCompound(); node.writeToNBT(state); update.setTag("node", state);
        }
        else if (controller) {
            NBTTagList list = new NBTTagList(); TileMaster master = master();
            if (master != null) for (TileNode processor : master.processors()) {
                NBTTagCompound state = new NBTTagCompound(); state.setInteger("count", processor.processCount); state.setBoolean("always", processor.alwaysActive);
                if (processor.filters[9] != null) processor.filters[9].writeToNBT(state); list.appendTag(state);
            }
            update.setTag("processors", list);
        }
        else {
            TileMaster master = master(); if (master == null) return;
            List<ItemStack> current = master.stacks(); NBTTagList changes = new NBTTagList();
            for (int i = 0; i < current.size(); i++) {
                ItemStack stack = current.get(i);
                if (!resetCatalog && i < sent.size() && Stacks.INSTANCE.same(stack, sent.get(i)) && stack.stackSize == sent.get(i).stackSize) continue;
                NBTTagCompound item = new NBTTagCompound(); stack.writeToNBT(item); item.setInteger("SSNCount", stack.stackSize); item.setInteger("index", i); changes.appendTag(item);
            }
            if (!force && !resetCatalog && current.size() == sent.size() && changes.tagCount() == 0) { force = false; return; }
            catalog = current; sent = new ArrayList<ItemStack>(current); revision++;
            update.setTag("changes", changes); update.setInteger("size", current.size()); update.setInteger("revision", revision);
            update.setBoolean("reset", resetCatalog);
            ItemStack remote = remoteSlot >= 0 ? player.inventory.getStackInSlot(remoteSlot) : null;
            update.setInteger("sort", node != null ? node.sort : remote != null && remote.hasTagCompound() ? remote.getTagCompound().getInteger("sort") : 1);
            update.setBoolean("down", node != null ? node.downwards : remote != null && remote.hasTagCompound() && remote.getTagCompound().getBoolean("down"));
        }
        if (cable || controller) { if (!force && update.equals(lastState)) return; lastState = (NBTTagCompound) update.copy(); }
        force = false; resetCatalog = false; Protocol.sendTo(update, (EntityPlayerMP) player);
    }
    private void updateCraftingResult() {
        result.setInventorySlotContents(0, closed ? null : CraftingManager.getInstance().findMatchingRecipe(matrix, player.worldObj));
    }
    @Override public void onCraftMatrixChanged(IInventory inventory) {
        if (batching || !crafting) return;
        // Like ContainerWorkbench, predict recipes on the client as well. The server
        // independently resolves and consumes the real recipe for every click.
        updateCraftingResult();
    }
    @Override public ItemStack slotClick(int slot, int button, int mode, EntityPlayer player) {
        if (closed || !player.worldObj.isRemote && !canInteractWith(player)) return null;
        if (mode == 2 && button == remoteSlot || mode == 6 && remoteSlot >= 0) return null;
        if (crafting) updateCraftingResult();
        boolean previous = batching; batching = true;
        try { return super.slotClick(slot, button, mode, player); }
        finally { batching = previous; onCraftMatrixChanged(matrix); refresh(); }
    }
    @Override public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= inventorySlots.size() || !canInteractWith(player)) return null;
        Slot slot = (Slot) inventorySlots.get(slotIndex); if (slot == null || !slot.getHasStack() || !slot.canTakeStack(player)) return null;
        if (crafting && slotIndex == 0) {
            ItemStack selected = slot.getStack().copy();
            NativeInventory destination = new NativeInventory(player.inventory, -1, player.inventory);
            // Bound by actual initial output capacity, not by one output stack.
            // This also terminates recipes whose reusable inputs never disappear.
            int capacity = 0;
            for (int i = 10; i < inventorySlots.size(); i++) {
                Slot target = (Slot) inventorySlots.get(i); ItemStack existing = target.getStack();
                if (target.isItemValid(selected) && (existing == null || Stacks.INSTANCE.same(existing, selected)))
                    capacity += Math.max(0, Math.min(target.getSlotStackLimit(), selected.getMaxStackSize()) - Stacks.INSTANCE.count(existing));
            }
            boolean previous = batching; batching = true;
            try {
                for (int craft = 0; selected.stackSize > 0 && craft < capacity / selected.stackSize; craft++) {
                    updateCraftingResult();
                    ItemStack output = result.getStackInSlot(0);
                    if (!ItemStack.areItemStacksEqual(selected, output) || Transfers.insert(destination, output, true, Stacks.INSTANCE) != null) break;
                    // Player inventory is owned by this thread. No callbacks occur
                    // between the successful capacity check and the real insertion.
                    Transfers.insert(destination, output, false, Stacks.INSTANCE);
                    ItemStack taken = slot.decrStackSize(output.stackSize);
                    slot.onPickupFromSlot(player, taken);
                }
            } finally { batching = previous; onCraftMatrixChanged(matrix); refresh(); }
            // We already repeated the full lifecycle; do not invoke vanilla's retry recursion.
            return null;
        }
        if (player.worldObj.isRemote) return null;
        ItemStack stack = slot.getStack(); if (Stacks.INSTANCE.empty(stack)) return null;
        ItemStack original = stack.copy();
        int ownSlots = inventorySlots.size() - 36;
        if (cable) {
            if (slotIndex < ownSlots) { if (!mergeItemStack(stack, ownSlots, inventorySlots.size(), true)) return null; }
            else if (node.kind() == 5 || node.kind() == 6) { if (!mergeItemStack(stack, 0, ownSlots, false)) return null; }
            else return null;
            slot.putStack(stack.stackSize > 0 ? stack : null);
        }
        else {
            TileMaster master = master(); if (master == null) return null;
            ItemStack remainder = master.insert(original, false, null);
            // Native slotClick retries successful transfers while the source still has this item.
            // Returning success without progress recurses forever when the network is full.
            if (Stacks.INSTANCE.count(remainder) >= original.stackSize) return null;
            slot.putStack(Stacks.INSTANCE.empty(remainder) ? null : remainder);
        }
        slot.onSlotChanged(); if (node != null) node.markDirty(); refresh(); return original;
    }
    public void request(int index, int button, int modifiers, int expectedRevision) {
        TileMaster master = master();
        if (master == null || expectedRevision != revision || index < 0 || index >= catalog.size() || button < 0 || button > 1) { refresh(); return; }
        ItemStack pattern = catalog.get(index); ItemStack cursor = player.inventory.getItemStack();
        if (!Stacks.INSTANCE.empty(cursor)) { insertCursor(button); return; }
        int amount = (modifiers & 2) != 0 ? 1 : button == 0 ? pattern.getMaxStackSize() : Math.max(1, Math.min(pattern.getMaxStackSize() / 2, pattern.stackSize / 2));
        boolean shift = (modifiers & 1) != 0;
        NativeInventory playerInventory = new NativeInventory(player.inventory, -1, player.inventory);
        if (shift) amount -= Stacks.INSTANCE.count(Transfers.insert(playerInventory, Stacks.INSTANCE.copy(pattern, amount), true, Stacks.INSTANCE));
        ItemStack extracted = master.request(pattern, amount, false, true, false, true, null);
        if (shift) { ItemStack remainder = Transfers.insert(playerInventory, extracted, false, Stacks.INSTANCE); if (remainder != null) player.dropPlayerItemWithRandomChoice(remainder, false); }
        else player.inventory.setItemStack(extracted);
        Protocol.syncCursor((EntityPlayerMP) player); refresh();
    }
    public void insertCursor(int button) {
        TileMaster master = master(); ItemStack cursor = player.inventory.getItemStack(); if (master == null || Stacks.INSTANCE.empty(cursor)) return;
        int offered = button == 1 ? 1 : cursor.stackSize;
        int remainder = Stacks.INSTANCE.count(master.insert(Stacks.INSTANCE.copy(cursor, offered), false, null));
        player.inventory.setItemStack(Stacks.INSTANCE.copy(cursor, cursor.stackSize - offered + remainder));
        Protocol.syncCursor((EntityPlayerMP) player); refresh();
    }
    public void clearRecipe() {
        if (!crafting || player.worldObj.isRemote) return;
        batching = true;
        try {
            for (int slot = 0; slot < 9; slot++) {
                ItemStack stack = matrix.getStackInSlot(slot); if (Stacks.INSTANCE.empty(stack)) continue;
                // Transfer copies: player inventory insertion may mutate its argument.
                TileMaster master = master(); ItemStack remainder = master == null ? stack.copy() : master.insert(stack.copy(), false, null);
                if (remainder != null) { player.inventory.addItemStackToInventory(remainder); if (remainder.stackSize > 0) player.dropPlayerItemWithRandomChoice(remainder, false); }
                matrix.setInventorySlotContents(slot, null);
            }
        } finally { batching = false; onCraftMatrixChanged(matrix); }
        refresh();
    }
    @Override public void onContainerClosed(EntityPlayer player) {
        // A client screen can close temporarily while NEI retains this container.
        // Only the server's window-close lifecycle may retire it or return inputs.
        if (player.worldObj.isRemote) return;
        if (closed) return;
        closed = true; batching = true;
        try {
            super.onContainerClosed(player);
            for (int i = 0; i < 9; i++) {
                ItemStack stack = matrix.getStackInSlotOnClosing(i);
                if (!player.worldObj.isRemote && !Stacks.INSTANCE.empty(stack)) {
                    ItemStack remainder = Transfers.insert(new NativeInventory(player.inventory, -1, player.inventory), stack, false, Stacks.INSTANCE);
                    if (!Stacks.INSTANCE.empty(remainder)) player.dropPlayerItemWithRandomChoice(remainder, false);
                }
            }
            result.setInventorySlotContents(0, null);
        } finally { batching = false; }
    }
    private final class CraftSlot extends SlotCrafting {
        CraftSlot(EntityPlayer player, InventoryCrafting matrix, IInventory result, int slot, int x, int y) { super(player, matrix, result, slot, x, y); }
        @Override public void onPickupFromSlot(EntityPlayer player, ItemStack stack) {
            boolean previous = batching; batching = true;
            try { super.onPickupFromSlot(player, stack); }
            finally { batching = previous; onCraftMatrixChanged(matrix); refresh(); }
        }
    }
}
