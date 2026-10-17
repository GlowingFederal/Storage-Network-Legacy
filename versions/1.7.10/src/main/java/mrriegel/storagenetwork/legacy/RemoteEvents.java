package mrriegel.storagenetwork.legacy;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;

public final class RemoteEvents {
    @SubscribeEvent public void pickup(EntityItemPickupEvent event) {
        EntityPlayer player = event.entityPlayer; if (player.worldObj.isRemote) return;
        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            ItemStack collector = player.inventory.getStackInSlot(slot);
            if (Stacks.INSTANCE.empty(collector) || collector.getItem() != StorageNetwork.collector) continue;
            TileMaster master = RemoteItem.boundMaster(collector); if (master == null) continue;
            ItemStack remainder = master.insert(event.item.getEntityItem(), false, null);
            if (Stacks.INSTANCE.empty(remainder)) { event.item.setDead(); event.setCanceled(true); }
            else event.item.setEntityItemStack(remainder);
            break;
        }
    }
}
