package mrriegel.storagenetwork.legacy;

import java.util.List;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

/** All actions resolve their authority from the live server container. No client ItemStacks. */
public final class Actions {
    public static final int REQUEST = 0, INSERT = 1, REFRESH = 2, FILTER = 3, WHITELIST = 4, META = 5, ORE = 6,
        NBT = 7, PRIORITY = 8, OP_LIMIT = 10, OP_STACK = 11, OP_SMALLER = 12, IMPORT_FILTER = 13,
        PROC_INPUT = 14, PROC_OUTPUT = 15, PROC_ALWAYS = 16, PROC_COUNT = 17, PROC_RESTART = 18,
        SORT = 19, DOWN = 20, CLEAR = 21, OPEN_REMOTE = 22;
    private Actions() {}
    public static void handle(EntityPlayerMP player, int window, int revision, int action, int a, int b, int c) {
        if (player.isDead || !player.worldObj.playerEntities.contains(player) || action < 0 || action > OPEN_REMOTE) return;
        if (action == OPEN_REMOTE) {
            for (int slot = 0; slot < 36; slot++) if (RemoteItem.validMaster(player, player.inventory.getStackInSlot(slot)) != null) {
                player.openGui(StorageNetwork.instance, 1, player.worldObj, slot, 0, 0); return;
            }
            return;
        }
        if (!(player.openContainer instanceof NetworkContainer)) return;
        NetworkContainer container = (NetworkContainer) player.openContainer;
        if (container.windowId != window || !container.canInteractWith(player)) return;
        if (action == REQUEST && !container.cable && !container.controller) container.request(a, b, c, revision);
        else if (action == INSERT && !container.cable && !container.controller) container.insertCursor(b == 1 ? 1 : 0);
        else if (action == CLEAR) container.clearRecipe();
        else if (action == SORT || action == DOWN) {
            if (container.remoteSlot >= 0) {
                ItemStack remote = player.inventory.getStackInSlot(container.remoteSlot);
                if (remote != null && remote.hasTagCompound()) {
                    if (action == SORT) remote.getTagCompound().setInteger("sort", TileNode.bound(a, 0, 2, 1));
                    else remote.getTagCompound().setBoolean("down", a != 0);
                }
            }
            else if (container.node != null) {
                if (action == SORT) container.node.sort = TileNode.bound(a, 0, 2, 1); else container.node.downwards = a != 0;
                container.node.markDirty();
            }
        }
        else if (container.cable || container.controller) {
            TileNode node = container.node;
            if (container.controller) {
                TileMaster master = container.master(); if (master == null) return;
                List<TileNode> processors = master.processors(); if (a < 0 || a >= processors.size()) return;
                node = processors.get(a);
                if (action != PROC_ALWAYS && action != PROC_COUNT && action != PROC_RESTART) return;
            }
            if (node == null || !player.canPlayerEdit(container.node.getPos(), net.minecraft.util.EnumFacing.UP, player.getHeldItem())) return;
            switch (action) {
                case FILTER:
                    if (a < 0 || a >= 18) return;
                    ItemStack held = player.inventory.getItemStack();
                    if (Stacks.INSTANCE.empty(held)) {
                        if (b == 0) node.filters[a] = null;
                        else if (node.filters[a] != null) node.filters[a] = Stacks.INSTANCE.copy(node.filters[a], Math.max(1, Math.min(64, node.filters[a].stackSize + (b < 0 ? -1 : 1))));
                    }
                    else node.filters[a] = Stacks.INSTANCE.copy(held, Math.min(64, Math.max(1, held.stackSize)));
                    // A changed recipe starts a new processing cycle.
                    if (node.kind() == 7) { node.processIndex = 0; node.processRemaining = 0; node.processStatus = 2; }
                    break;
                case WHITELIST: node.whitelist = !node.whitelist; break;
                case META: node.meta = !node.meta; break;
                case ORE: node.ores = !node.ores; break;
                case NBT: node.nbt = !node.nbt; break;
                case PRIORITY: node.priority = (int) Math.max(-1000000L, Math.min(1000000L, (long) node.priority + (b < 0 ? -1 : 1))); break;
                case OP_LIMIT: node.operationLimit = Math.max(0, a); break;
                case OP_STACK: node.operationStack = Stacks.INSTANCE.copy(player.inventory.getItemStack(), 1); break;
                case OP_SMALLER: node.operationSmaller = !node.operationSmaller; break;
                case IMPORT_FILTER: node.importFilters(); break;
                case PROC_INPUT: node.processingInput = TileNode.bound(b, 0, 5, 1); break;
                case PROC_OUTPUT: node.processingOutput = TileNode.bound(b, 0, 5, 0); break;
                case PROC_ALWAYS: node.alwaysActive = !node.alwaysActive; break;
                case PROC_COUNT:
                    int previous = node.processCount;
                    node.processCount = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, (long) previous + Math.max(-64, Math.min(64, b))));
                    if (previous == 0 && node.processCount > 0) { node.processStatus = 2; node.processIndex = 0; node.processRemaining = 0; }
                    break;
                case PROC_RESTART: node.processStatus = 2; node.processIndex = 0; node.processRemaining = 0; break;
                default: break;
            }
            Platform.sync(node); TileMaster master = node.master(); if (master != null && action == PRIORITY) master.refresh();
        }
        if (action == REFRESH) container.resendCatalog();
        container.refresh(); container.detectAndSendChanges();
    }
}
