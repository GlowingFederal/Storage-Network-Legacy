package mrriegel.storagenetwork.legacy.core;

/** Slots are physical identities, even when the exposed sided view is sparse. */
public interface InventoryAccess<S> {
    int slots();
    S peek(int slot);
    S insert(int slot, S stack, boolean simulate);
    S extract(int slot, int amount, boolean simulate);
    Object identity();
}
