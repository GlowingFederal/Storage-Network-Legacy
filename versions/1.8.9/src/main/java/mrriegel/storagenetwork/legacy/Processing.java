package mrriegel.storagenetwork.legacy;

import mrriegel.storagenetwork.legacy.core.Transfers;
import net.minecraft.item.ItemStack;

public final class Processing {
    private Processing() {}
    public static void run(TileNode node, TileMaster master) {
        if (node.processStatus == 0 || !node.alwaysActive && node.processCount <= 0) return;
        boolean inputPresent = false, outputPresent = false;
        for (int i = 0; i < 9; i++) { inputPresent |= !Stacks.INSTANCE.empty(node.filters[i]); outputPresent |= !Stacks.INSTANCE.empty(node.filters[i + 9]); }
        if (!inputPresent || !outputPresent) return;
        mrriegel.storagenetwork.legacy.core.InventoryAccess<ItemStack> target = node.inventory(node.processStatus == 2 ? node.processingInput : node.processingOutput);
        if (target == null) return;
        int offset = node.processStatus == 2 ? 0 : 9;
        while (node.processIndex < 9) {
            ItemStack filter = node.filters[node.processIndex + offset];
            if (Stacks.INSTANCE.empty(filter)) { node.processIndex++; continue; }
            int needed = node.processRemaining > 0 ? node.processRemaining : filter.stackSize;
            if (node.processStatus == 2) {
                ItemStack available = master.request(filter, needed, true, node.meta, node.ores, node.nbt, target.identity());
                if (Stacks.INSTANCE.count(available) < needed || !Stacks.INSTANCE.empty(Transfers.insert(target, available, true, Stacks.INSTANCE))) return;
                ItemStack extracted = master.request(available, needed, false, true, false, true, target.identity());
                ItemStack remaining = Transfers.insert(target, extracted, false, Stacks.INSTANCE);
                int moved = Stacks.INSTANCE.count(extracted) - Stacks.INSTANCE.count(remaining);
                node.recover(remaining); node.processRemaining = needed - moved;
            }
            else {
                int found = 0;
                for (int slot = 0; slot < target.slots() && found < needed; slot++) {
                    ItemStack stack = target.peek(slot); if (!Stacks.matches(filter, stack, true, false, true)) continue;
                    found += Stacks.INSTANCE.count(target.extract(slot, needed - found, true));
                }
                if (found < needed || !Stacks.INSTANCE.empty(master.insert(Stacks.INSTANCE.copy(filter, needed), true, target.identity()))) return;
                int extractedCount = 0;
                for (int slot = 0; slot < target.slots() && extractedCount < needed; slot++) {
                    if (!Stacks.matches(filter, target.peek(slot), true, false, true)) continue;
                    ItemStack extracted = target.extract(slot, needed - extractedCount, false); extractedCount += Stacks.INSTANCE.count(extracted);
                    node.recover(master.insert(extracted, false, target.identity()));
                }
                node.processRemaining = needed - extractedCount;
            }
            node.markDirty();
            if (node.processRemaining > 0) return;
            node.processRemaining = 0; node.processIndex++;
        }
        node.processIndex = 0; node.processStatus = node.processStatus == 2 ? 1 : 2;
        if (node.processStatus == 2 && !node.alwaysActive && node.processCount > 0) node.processCount--;
        node.markDirty();
    }
}
