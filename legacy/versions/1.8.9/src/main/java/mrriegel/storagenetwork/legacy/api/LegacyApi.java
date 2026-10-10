package mrriegel.storagenetwork.legacy.api;

import java.util.ArrayList;
import java.util.List;
import mrriegel.storagenetwork.legacy.TileMaster;
import mrriegel.storagenetwork.legacy.Platform;
import mrriegel.storagenetwork.legacy.core.InventoryAccess;
import mrriegel.storagenetwork.legacy.core.Position;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

/** Startup registration; queries and mutations must run on the owning world thread. */
public final class LegacyApi {
    public static final LegacyApi INSTANCE = new LegacyApi();
    private final List<InventoryProvider> providers = new ArrayList<InventoryProvider>();
    private boolean ready;
    private LegacyApi() {}
    public void registerInventoryProvider(InventoryProvider provider) {
        if (ready) throw new IllegalStateException("Inventory providers must register before SSN post-init");
        if (provider == null) throw new IllegalArgumentException("Null inventory provider");
        providers.add(provider);
    }
    public void finishRegistration() { ready = true; }
    public InventoryAccess<ItemStack> inventory(TileEntity tile, int side) {
        for (InventoryProvider provider : providers) { InventoryAccess<ItemStack> view = provider.inventory(tile, side); if (view != null) return view; }
        return null;
    }
    public TileMaster master(Position position) {
        TileEntity tile = Platform.tile(Platform.world(position.dimension), position); return tile instanceof TileMaster ? (TileMaster) tile : null;
    }
}
