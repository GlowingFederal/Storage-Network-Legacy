package mrriegel.storagenetwork.legacy.api;

import mrriegel.storagenetwork.legacy.core.InventoryAccess;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

/** Return null for unsupported targets. Identity must denote the underlying storage, independent of side. */
public interface InventoryProvider {
    InventoryAccess<ItemStack> inventory(TileEntity tile, int side);
}
