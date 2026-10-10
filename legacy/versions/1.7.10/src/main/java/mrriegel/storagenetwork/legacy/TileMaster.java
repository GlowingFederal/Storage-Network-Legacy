package mrriegel.storagenetwork.legacy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mrriegel.storagenetwork.legacy.core.Position;
import mrriegel.storagenetwork.legacy.core.Transfers;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/** Owning-thread graph and transfer service. No chunk loads or world-wide discovery. */
public class TileMaster extends TileNode {
    private boolean dirty = true;
    private final List<TileNode> members = new ArrayList<TileNode>();
    private final List<TileNode> links = new ArrayList<TileNode>(), io = new ArrayList<TileNode>(), processors = new ArrayList<TileNode>();
    public void refresh() { dirty = true; }
    public List<TileNode> processors() { ensureGraph(); return Collections.unmodifiableList(processors); }
    public boolean contains(TileNode node) { ensureGraph(); return node == this || members.contains(node) && live(node); }
    private boolean live(TileNode node) { return !node.isInvalid() && Platform.tile(worldObj, node.position()) == node; }
    private void ensureGraph() { if (dirty && worldObj != null && !worldObj.isRemote) rebuild(); }
    private void rebuild() {
        List<TileNode> previous = new ArrayList<TileNode>(members);
        members.clear(); links.clear(); io.clear(); processors.clear();
        ArrayDeque<Position> pending = new ArrayDeque<Position>(); Set<Position> visited = new HashSet<Position>();
        pending.add(position()); visited.add(position());
        while (!pending.isEmpty()) {
            Position current = pending.removeFirst();
            for (int side = 0; side < 6; side++) {
                Position next = current.offset(side); if (!visited.add(next)) continue;
                TileEntity tile = Platform.tile(worldObj, next); if (!(tile instanceof TileNode)) continue;
                TileNode node = (TileNode) tile;
                if (node instanceof TileMaster && node != this) {
                    StorageNetwork.removeExtraMaster(worldObj, next); continue;
                }
                node.master = position(); node.markDirty(); node.clearInventoryCache(); members.add(node); pending.add(next);
            }
        }
        for (TileNode node : previous) if (!members.contains(node) && live(node) && position().equals(node.master)) { node.master = null; node.markDirty(); }
        Collections.sort(members, new Comparator<TileNode>() {
            public int compare(TileNode a, TileNode b) { return a.position().compareTo(b.position()); }
        });
        for (TileNode node : members) {
            int kind = node.kind();
            if (kind >= 4 && kind <= 8) node.findDirection();
            if (kind == 4 || kind == 8) {
                mrriegel.storagenetwork.legacy.core.InventoryAccess<ItemStack> inventory = node.inventory(node.direction ^ 1);
                if (inventory != null) links.add(node);
            }
            else if (kind == 5 || kind == 6) io.add(node);
            else if (kind == 7) processors.add(node);
        }
        Comparator<TileNode> priorities = new Comparator<TileNode>() {
            public int compare(TileNode a, TileNode b) {
                if (a.priority != b.priority) return a.priority < b.priority ? -1 : 1;
                return a.position().compareTo(b.position());
            }
        };
        Collections.sort(links, priorities); Collections.sort(io, priorities); dirty = false;
    }
    public ItemStack insert(ItemStack offered, boolean simulate, Object excluded) {
        ensureGraph(); if (Stacks.INSTANCE.empty(offered)) return null;
        ItemStack remaining = offered.copy(); Set<Object> targets = new HashSet<Object>();
        // Higher priority stores receive inserts first; lower priority stores are extracted first.
        for (int index = links.size() - 1; index >= 0 && !Stacks.INSTANCE.empty(remaining); index--) {
            TileNode link = links.get(index); if (!live(link) || link.way != 2 || link.filtered(remaining)) continue;
            mrriegel.storagenetwork.legacy.core.InventoryAccess<ItemStack> inventory = link.inventory(link.direction ^ 1);
            if (inventory == null || inventory.identity().equals(excluded) || !targets.add(inventory.identity())) continue;
            remaining = Transfers.insert(inventory, remaining, simulate, Stacks.INSTANCE);
        }
        return remaining;
    }
    public ItemStack request(ItemStack pattern, int amount, boolean simulate, boolean matchMeta, boolean matchOre, boolean matchNbt, Object excluded) {
        ensureGraph(); if (Stacks.INSTANCE.empty(pattern) || amount <= 0) return null;
        ItemStack result = null; Set<Object> targets = new HashSet<Object>();
        for (TileNode link : links) {
            if (!live(link) || link.way == 0) continue;
            mrriegel.storagenetwork.legacy.core.InventoryAccess<ItemStack> inventory = link.inventory(link.direction ^ 1);
            if (inventory == null || inventory.identity().equals(excluded) || !targets.add(inventory.identity())) continue;
            for (int slot = 0; slot < inventory.slots() && Stacks.INSTANCE.count(result) < amount; slot++) {
                ItemStack stack = inventory.peek(slot);
                if (link.filtered(stack) || !Stacks.matches(pattern, stack, matchMeta, matchOre, matchNbt)
                    || result != null && !Stacks.INSTANCE.same(result, stack)) continue;
                // Tighten the match BEFORE extraction so broad filters never destroy a different variant.
                ItemStack extracted = inventory.extract(slot, amount - Stacks.INSTANCE.count(result), simulate);
                if (Stacks.INSTANCE.empty(extracted)) continue;
                if (result == null) result = extracted.copy(); else result.stackSize += extracted.stackSize;
            }
            if (Stacks.INSTANCE.count(result) >= amount) break;
        }
        return result;
    }
    public List<ItemStack> stacks() {
        ensureGraph(); Map<Key, ItemStack> result = new LinkedHashMap<Key, ItemStack>(); Set<Object> targets = new HashSet<Object>();
        for (TileNode link : links) {
            if (!live(link)) continue;
            mrriegel.storagenetwork.legacy.core.InventoryAccess<ItemStack> inventory = link.inventory(link.direction ^ 1); if (inventory == null || !targets.add(inventory.identity())) continue;
            for (int slot = 0; slot < inventory.slots(); slot++) {
                ItemStack stack = inventory.peek(slot); if (Stacks.INSTANCE.empty(stack) || link.filtered(stack)) continue;
                Key key = new Key(stack); ItemStack merged = result.get(key);
                if (merged == null) result.put(key, stack.copy());
                else merged.stackSize = (int) Math.min(Integer.MAX_VALUE, (long) merged.stackSize + stack.stackSize);
            }
        }
        return new ArrayList<ItemStack>(result.values());
    }
    public int amount(ItemStack pattern, boolean meta, boolean ore, boolean nbt) {
        long total = 0; for (ItemStack stack : stacks()) if (Stacks.matches(pattern, stack, meta, ore, nbt)) total += stack.stackSize;
        return (int) Math.min(Integer.MAX_VALUE, total);
    }
    private static final class Key {
        final ItemStack stack;
        Key(ItemStack stack) { this.stack = stack; }
        @Override public int hashCode() { return (System.identityHashCode(stack.getItem()) * 31 + stack.getItemDamage()) * 31 + (stack.hasTagCompound() ? stack.getTagCompound().hashCode() : 0); }
        @Override public boolean equals(Object other) { return other instanceof Key && Stacks.INSTANCE.same(stack, ((Key) other).stack); }
    }
    @Override public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) return;
        if (worldObj.getTotalWorldTime() % StorageNetwork.refreshTicks == 0) dirty = true;
        ensureGraph();
        for (TileNode node : io) {
            if (!live(node) || !node.flushRecovery(this) || !node.runNow(this)) continue;
            mrriegel.storagenetwork.legacy.core.InventoryAccess<ItemStack> target = node.inventory(node.direction ^ 1); if (target == null) continue;
            if (node.kind() == 6) importItems(node, target); else exportItems(node, target);
        }
        for (TileNode node : processors) if (live(node) && node.flushRecovery(this)) Processing.run(node, this);
    }
    private void importItems(TileNode node, mrriegel.storagenetwork.legacy.core.InventoryAccess<ItemStack> source) {
        for (int slot = 0; slot < source.slots(); slot++) {
            ItemStack available = source.extract(slot, node.transferRate(), true);
            if (Stacks.INSTANCE.empty(available) || node.filtered(available)) continue;
            int moved = available.stackSize - Stacks.INSTANCE.count(insert(available, true, source.identity()));
            if (moved <= 0) continue;
            node.recover(insert(source.extract(slot, moved, false), false, source.identity())); break;
        }
    }
    private void exportItems(TileNode node, mrriegel.storagenetwork.legacy.core.InventoryAccess<ItemStack> destination) {
        for (ItemStack filter : node.filters) {
            if (Stacks.INSTANCE.empty(filter)) continue;
            int amount = node.transferRate();
            if (node.upgrade(3) > 0) {
                int stored = 0; for (int slot = 0; slot < destination.slots(); slot++) {
                    ItemStack stack = destination.peek(slot); if (Stacks.matches(filter, stack, node.meta, node.ores, node.nbt)) stored += stack.stackSize;
                }
                amount = Math.min(amount, Math.max(0, filter.stackSize - stored));
            }
            ItemStack available = request(filter, amount, true, node.meta, node.ores, node.nbt, destination.identity());
            if (Stacks.INSTANCE.empty(available)) continue;
            int moved = available.stackSize - Stacks.INSTANCE.count(Transfers.insert(destination, available, true, Stacks.INSTANCE));
            if (moved <= 0) continue;
            ItemStack extracted = request(available, moved, false, true, false, true, destination.identity());
            node.recover(Transfers.insert(destination, extracted, false, Stacks.INSTANCE)); break;
        }
    }
    @Override public void readFromNBT(NBTTagCompound tag) { super.readFromNBT(tag); dirty = true; }
}
