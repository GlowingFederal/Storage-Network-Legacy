package mrriegel.storagenetwork.legacy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import mrriegel.storagenetwork.legacy.core.InventoryAccess;
import mrriegel.storagenetwork.legacy.core.Position;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.inventory.InventoryLargeChest;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.World;

/** Native legacy inventory access. Simulation only reads; real changes notify the inventory. */
public final class NativeInventory implements InventoryAccess<ItemStack> {
    private final IInventory inventory;
    private final int side;
    private int[] exposed;
    private final Object identity;
    public NativeInventory(IInventory inventory, int side, Object identity) {
        this.inventory = inventory; this.side = side; this.identity = identity;
        refreshSlots();
    }
    public void refreshSlots() {
        int[] candidates;
        if (inventory instanceof ISidedInventory && side >= 0) candidates = ((ISidedInventory) inventory).getSlotsForFace(net.minecraft.util.EnumFacing.getFront(side));
        else { if (exposed != null && exposed.length == (inventory instanceof net.minecraft.entity.player.InventoryPlayer ? 36 : inventory.getSizeInventory())) return; candidates = new int[(inventory instanceof net.minecraft.entity.player.InventoryPlayer ? 36 : inventory.getSizeInventory())]; for (int i = 0; i < candidates.length; i++) candidates[i] = i; }
        if (exposed != null && java.util.Arrays.equals(candidates, exposed)) return;
        List<Integer> slots = new ArrayList<Integer>(); Set<Integer> seen = new HashSet<Integer>();
        if (candidates != null) for (int slot : candidates)
            if (slot >= 0 && slot < (inventory instanceof net.minecraft.entity.player.InventoryPlayer ? 36 : inventory.getSizeInventory()) && seen.add(slot)) slots.add(slot);
        exposed = new int[slots.size()]; for (int i = 0; i < exposed.length; i++) exposed[i] = slots.get(i);
    }
    public static mrriegel.storagenetwork.legacy.core.InventoryAccess<ItemStack> at(World world, Position pos, int side) {
        if (!Platform.loaded(world, pos)) return null;
        TileEntity tile = Platform.tile(world, pos);
        if (tile == null || tile instanceof TileNode) return null;
        if (!StorageNetwork.allowed(Platform.blockName(world, pos), StorageNetwork.blacklist)) return null;
        InventoryAccess<ItemStack> custom = mrriegel.storagenetwork.legacy.api.LegacyApi.INSTANCE.inventory(tile, side); if (custom != null) return custom;
        if (!(tile instanceof IInventory)) return null;
        IInventory inventory = (IInventory) tile; Object identity = tile;
        if (tile instanceof TileEntityChest) {
            Position canonical = pos;
            for (int face = 2; face < 6; face++) {
                Position neighbor = pos.offset(face);
                if (!Platform.loaded(world, neighbor)) continue;
                TileEntity other = Platform.tile(world, neighbor);
                if (!(other instanceof TileEntityChest) || Platform.block(world, pos) != Platform.block(world, neighbor)) continue;
                if (neighbor.compareTo(canonical) < 0) canonical = neighbor;
                inventory = neighbor.compareTo(pos) < 0
                    ? new InventoryLargeChest("container.chestDouble", (net.minecraft.world.ILockableContainer) other, (net.minecraft.world.ILockableContainer) inventory)
                    : new InventoryLargeChest("container.chestDouble", (net.minecraft.world.ILockableContainer) inventory, (net.minecraft.world.ILockableContainer) other);
                break;
            }
            identity = canonical;
        }
        return new NativeInventory(inventory, side, identity);
    }
    public static TileEntity chestPartner(World world, Position pos, TileEntity tile) {
        if (!(tile instanceof TileEntityChest)) return null;
        for (int face = 2; face < 6; face++) { Position other = pos.offset(face); TileEntity partner = Platform.tile(world, other); if (partner instanceof TileEntityChest && Platform.block(world, pos) == Platform.block(world, other)) return partner; }
        return null;
    }
    public int slots() { return exposed.length; }
    public ItemStack peek(int slot) { return inventory.getStackInSlot(exposed[slot]); }
    public Object identity() { return identity; }
    public ItemStack insert(int slot, ItemStack offered, boolean simulate) {
        if (Stacks.INSTANCE.empty(offered)) return null;
        int actual = exposed[slot]; ItemStack current = peek(slot);
        if (!inventory.isItemValidForSlot(actual, offered) || inventory instanceof ISidedInventory && side >= 0
            && !((ISidedInventory) inventory).canInsertItem(actual, offered, net.minecraft.util.EnumFacing.getFront(side))) return Stacks.INSTANCE.copy(offered, offered.stackSize);
        if (!Stacks.INSTANCE.empty(current) && !Stacks.INSTANCE.same(current, offered)) return Stacks.INSTANCE.copy(offered, offered.stackSize);
        int maximum = Math.min(inventory.getInventoryStackLimit(), offered.getMaxStackSize());
        int amount = Math.min(offered.stackSize, Math.max(0, maximum - Stacks.INSTANCE.count(current)));
        if (!simulate && amount > 0) {
            inventory.setInventorySlotContents(actual, Stacks.INSTANCE.copy(offered, Stacks.INSTANCE.count(current) + amount));
            inventory.markDirty();
        }
        return Stacks.INSTANCE.copy(offered, offered.stackSize - amount);
    }
    public ItemStack extract(int slot, int amount, boolean simulate) {
        ItemStack stack = peek(slot); int actual = exposed[slot];
        if (Stacks.INSTANCE.empty(stack) || amount <= 0 || inventory instanceof ISidedInventory && side >= 0
            && !((ISidedInventory) inventory).canExtractItem(actual, stack, net.minecraft.util.EnumFacing.getFront(side))) return null;
        int count = Math.min(amount, stack.stackSize);
        if (simulate) return Stacks.INSTANCE.copy(stack, count);
        ItemStack result = inventory.decrStackSize(actual, count); inventory.markDirty(); return result;
    }
}
