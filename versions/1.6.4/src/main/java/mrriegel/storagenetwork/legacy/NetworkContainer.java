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
import net.minecraft.network.packet.Packet103SetSlot;

public final class NetworkContainer extends Container {
    public final EntityPlayer player;
    public final TileNode node;
    public final int remoteSlot;
    private final ItemStack remoteIdentity;
    public final boolean cable, controller, crafting;
    public final TerminalLayout layout;
    public final InventoryCrafting matrix;
    private final InventoryCraftResult result = new InventoryCraftResult();
    private final ItemStack[] recipeMemory = new ItemStack[9], lastMatrix = new ItemStack[9];
    public List<ItemStack> catalog = new ArrayList<ItemStack>();
    public int revision;
    private NBTTagCompound lastState;
    private List<ItemStack> sent = new ArrayList<ItemStack>();
    private long lastUpdate = Long.MIN_VALUE;
    private boolean force = true, resetCatalog = true, batching;
    public NetworkContainer(EntityPlayer player, TileNode node, int remoteSlot) {
        this.player = player; this.node = node; this.remoteSlot = remoteSlot;
        remoteIdentity = remoteSlot >= 0 ? player.inventory.getStackInSlot(remoteSlot) : null;
        cable = remoteSlot < 0 && node != null && node.kind() >= 5 && node.kind() <= 7;
        controller = remoteSlot < 0 && node != null && node.kind() == 9;
        crafting = !cable && !controller && (remoteSlot < 0 ? node != null && node.kind() == 1 : remoteIdentity != null && remoteIdentity.getItemDamage() != 3);
        layout = new TerminalLayout(crafting);
        // The server retains the saved table matrix. Client slots have one authority:
        // vanilla container packets, never tile-description NBT or browser snapshots.
        matrix = new SharedMatrix(this, !player.worldObj.isRemote && remoteSlot < 0 && node != null ? node.matrix : new ItemStack[9]);
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
        if (player.worldObj.isRemote) return true;
        if (remoteSlot >= 0) return player.inventory.getStackInSlot(remoteSlot) == remoteIdentity && master() != null;
        if (node == null || !node.isUseableByPlayer(player)) return false;
        TileMaster master = master(); return cable || master != null && master.contains(node);
    }
    public void refresh() { force = true; }
    public void resendCatalog() { force = true; resetCatalog = true; }
    @Override public void detectAndSendChanges() {
        // Another viewer can change the saved matrix without calling this container.
        if (crafting && !player.worldObj.isRemote) {
            boolean changed = false;
            for (int i = 0; i < 9; i++) if (!ItemStack.areItemStacksEqual(lastMatrix[i], matrix.getStackInSlot(i))) { changed = true; break; }
            if (changed) {
                for (int i = 0; i < 9; i++) lastMatrix[i] = Stacks.INSTANCE.copy(matrix.getStackInSlot(i), Stacks.INSTANCE.count(matrix.getStackInSlot(i)));
                onCraftMatrixChanged(matrix);
            }
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
    @Override public void onCraftMatrixChanged(IInventory inventory) {
        if (batching || !crafting || player.worldObj.isRemote) return;
        result.setInventorySlotContents(0, CraftingManager.getInstance().findMatchingRecipe(matrix, player.worldObj));
        // EntityPlayerMP.sendSlotContents deliberately skips SlotCrafting. Unlike a
        // vanilla workbench, this client does not recompute recipes: send the real result.
        // Opening sends a full inventory after the window ID and listener are installed.
        if (player instanceof EntityPlayerMP && player.openContainer == this)
            ((EntityPlayerMP) player).playerNetServerHandler.sendPacketToPlayer(new Packet103SetSlot(windowId, 0, result.getStackInSlot(0)));
    }
    @Override public ItemStack slotClick(int slot, int button, int mode, EntityPlayer player) {
        if (!player.worldObj.isRemote && !canInteractWith(player)) return null;
        if (mode == 2 && button == remoteSlot || mode == 6 && remoteSlot >= 0) return null;
        onCraftMatrixChanged(matrix);
        ItemStack result = super.slotClick(slot, button, mode, player); if (node != null && !player.worldObj.isRemote) node.onInventoryChanged(); refresh(); return result;
    }
    @Override public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        if (player.worldObj.isRemote || slotIndex < 0 || slotIndex >= inventorySlots.size() || !canInteractWith(player)) return null;
        Slot slot = (Slot) inventorySlots.get(slotIndex); if (slot == null || !slot.getHasStack() || !slot.canTakeStack(player)) return null;
        if (crafting && slotIndex == 0) {
            // Native SlotCrafting owns container-item remainders and Forge crafting hooks.
            int crafted = 0;
            while (result.getStackInSlot(0) != null && crafted < 64) {
                ItemStack output = result.getStackInSlot(0).copy();
                NativeInventory playerInventory = new NativeInventory(player.inventory, -1, player.inventory);
                if (crafted + output.stackSize > output.getMaxStackSize() || Transfers.insert(playerInventory, output, true, Stacks.INSTANCE) != null) break;
                ItemStack taken = slot.decrStackSize(output.stackSize); slot.onPickupFromSlot(player, taken);
                ItemStack rejected = Transfers.insert(playerInventory, taken, false, Stacks.INSTANCE); if (rejected != null) player.dropPlayerItemWithRandomChoice(rejected, false);
                crafted += output.stackSize; onCraftMatrixChanged(matrix);
            }
            refresh(); return null;
        }
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
        slot.onSlotChanged(); if (node != null) node.onInventoryChanged(); refresh(); return original;
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
    @Override public void onContainerClosed(EntityPlayer player) { if (remoteSlot >= 0 && !player.worldObj.isRemote) clearRecipe(); super.onContainerClosed(player); if (node != null && !player.worldObj.isRemote) node.onInventoryChanged(); }
    private final class CraftSlot extends SlotCrafting {
        CraftSlot(EntityPlayer player, InventoryCrafting matrix, IInventory result, int slot, int x, int y) { super(player, matrix, result, slot, x, y); }
        @Override public void onPickupFromSlot(EntityPlayer player, ItemStack stack) {
            if (player.worldObj.isRemote) return;
            for (int i = 0; i < 9; i++) recipeMemory[i] = Stacks.INSTANCE.copy(matrix.getStackInSlot(i), 1);
            batching = true;
            try {
                super.onPickupFromSlot(player, stack); TileMaster master = master();
                if (master != null) for (int i = 0; i < 9; i++) if (Stacks.INSTANCE.empty(matrix.getStackInSlot(i)) && recipeMemory[i] != null)
                    matrix.setInventorySlotContents(i, master.request(recipeMemory[i], 1, false, true, false, true, null));
            } finally { batching = false; }
            onCraftMatrixChanged(matrix); refresh();
        }
    }
    private static final class SharedMatrix extends InventoryCrafting {
        private final ItemStack[] stacks;
        private final NetworkContainer owner;
        SharedMatrix(NetworkContainer owner, ItemStack[] stacks) { super(owner, 3, 3); this.owner = owner; this.stacks = stacks; }
        @Override public ItemStack getStackInSlot(int slot) { return slot >= 0 && slot < 9 ? stacks[slot] : null; }
        @Override public ItemStack getStackInRowAndColumn(int row, int col) { return row >= 0 && row < 3 && col >= 0 && col < 3 ? getStackInSlot(row + col * 3) : null; }
        @Override public void setInventorySlotContents(int slot, ItemStack stack) { if (slot >= 0 && slot < 9) { stacks[slot] = stack; owner.onCraftMatrixChanged(this); if (owner.node != null && !owner.player.worldObj.isRemote) owner.node.onInventoryChanged(); } }
        @Override public ItemStack decrStackSize(int slot, int amount) {
            ItemStack stack = getStackInSlot(slot); if (Stacks.INSTANCE.empty(stack) || amount <= 0) return null;
            ItemStack taken = Stacks.INSTANCE.copy(stack, Math.min(amount, stack.stackSize)); setInventorySlotContents(slot, Stacks.INSTANCE.copy(stack, stack.stackSize - taken.stackSize)); return taken;
        }
        @Override public ItemStack getStackInSlotOnClosing(int slot) { ItemStack stack = getStackInSlot(slot); setInventorySlotContents(slot, null); return stack; }
    }
}
