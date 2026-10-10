package mrriegel.storagenetwork.legacy.core;

public final class Transfers {
    private Transfers() {}
    /** Merge occupied slots before filling empty slots, without mutating the caller's stack. */
    public static <S> S insert(InventoryAccess<S> destination, S offered, boolean simulate, StackAccess<S> stacks) {
        if (destination == null || stacks.empty(offered)) return offered;
        S remaining = stacks.copy(offered, stacks.count(offered));
        for (int pass = 0; pass < 2 && !stacks.empty(remaining); pass++) {
            for (int slot = 0; slot < destination.slots() && !stacks.empty(remaining); slot++) {
                S current = destination.peek(slot);
                if ((pass == 0) == stacks.empty(current)) continue;
                if (!stacks.empty(current) && !stacks.same(current, remaining)) continue;
                remaining = destination.insert(slot, remaining, simulate);
            }
        }
        return remaining;
    }
    /** A changed destination may reject a real transfer. Return the remainder to the caller. */
    public static <S> S move(InventoryAccess<S> source, int slot, InventoryAccess<S> destination, int amount, StackAccess<S> stacks) {
        if (source == null || destination == null || source.identity().equals(destination.identity())) return null;
        S available = source.extract(slot, amount, true);
        if (stacks.empty(available)) return null;
        S simulated = insert(destination, available, true, stacks);
        int accepted = stacks.count(available) - stacks.count(simulated);
        if (accepted <= 0) return null;
        S extracted = source.extract(slot, accepted, false);
        return insert(destination, extracted, false, stacks);
    }
}
