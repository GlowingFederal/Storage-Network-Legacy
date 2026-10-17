package mrriegel.storagenetwork.legacy;

import java.util.Arrays;
import mrriegel.storagenetwork.legacy.core.Position;
import mrriegel.storagenetwork.legacy.core.Upgrades;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet132TileEntityData;
import net.minecraft.tileentity.TileEntity;

public class TileNode extends TileEntity implements IInventory {
    public Position master;
    public int direction = -1, priority, way = 2, sort = 1;
    public boolean whitelist, meta = true, ores, nbt, downwards;
    public final ItemStack[] filters = new ItemStack[18], upgrades = new ItemStack[4], matrix = new ItemStack[9];
    public ItemStack operationStack;
    public int operationLimit, processCount, processStatus = 2, processIndex, processRemaining, processingInput = 1, processingOutput;
    public boolean operationSmaller = true, alwaysActive = true;
    public String facade = "";
    public int facadeMeta;
    private final ItemStack[] recovery = new ItemStack[18];
    private mrriegel.storagenetwork.legacy.core.InventoryAccess<ItemStack> cached;
    private TileEntity cachedTarget;
    private int cachedSide = -2;
    private TileEntity cachedPartner;

    public Position position() { return Platform.position(this); }
    public int kind() { return ((NetworkBlock) getBlockType()).kind; }
    public TileMaster master() {
        if (master == null) return null;
        TileEntity tile = Platform.tile(Platform.world(master.dimension), master);
        return tile instanceof TileMaster ? (TileMaster) tile : null;
    }
    public boolean filtered(ItemStack stack) {
        boolean matched = false;
        for (ItemStack filter : filters) if (Stacks.matches(filter, stack, meta, ores, nbt)) { matched = true; break; }
        return whitelist ? !matched : matched;
    }
    public int upgrade(int type) {
        int count = 0;
        for (ItemStack stack : upgrades) if (!Stacks.INSTANCE.empty(stack) && stack.getItem() == StorageNetwork.upgrade
            && stack.getItemDamage() == type) count++;
        return count;
    }
    public int transferRate() { return Upgrades.rate(upgrade(Upgrades.SINGLE) > 0, upgrade(Upgrades.STACK) > 0); }
    public boolean runNow(TileMaster network) {
        if (worldObj.getTotalWorldTime() % Upgrades.interval(upgrade(0), upgrade(5)) != 0) return false;
        return upgrade(1) == 0 || Stacks.INSTANCE.empty(operationStack)
            || Upgrades.operation(network.amount(operationStack, meta, ores, nbt), operationLimit, operationSmaller);
    }
    public mrriegel.storagenetwork.legacy.core.InventoryAccess<ItemStack> inventory(int side) {
        if (direction < 0 || direction > 5) return null;
        Position target = position().offset(direction); TileEntity tile = Platform.tile(worldObj, target);
        if (tile == null) { cached = null; cachedTarget = null; return null; }
        // Check the partner identity without rebuilding an unchanged double-chest wrapper.
        TileEntity partner = NativeInventory.chestPartner(worldObj, target, tile);
        if (tile != cachedTarget || side != cachedSide || partner != cachedPartner) {
            cached = NativeInventory.at(worldObj, target, side); cachedTarget = tile; cachedSide = side; cachedPartner = partner;
        }
        if (cached instanceof NativeInventory) ((NativeInventory) cached).refreshSlots();
        return cached;
    }
    public void clearInventoryCache() { cached = null; cachedTarget = null; cachedSide = -2; }
    public void findDirection() {
        if (inventory(direction ^ 1) != null) return;
        for (int side = 0; side < 6; side++) if (NativeInventory.at(worldObj, position().offset(side), side ^ 1) != null) {
            direction = side; cached = null; onInventoryChanged(); return;
        }
        direction = -1; cached = null;
    }
    public void rotate() {
        int previous = direction;
        for (int i = 1; i <= 6; i++) {
            int face = (Math.max(previous, 0) + i) % 6;
            if (NativeInventory.at(worldObj, position().offset(face), face ^ 1) != null) { direction = face; break; }
        }
        cached = null; Platform.sync(this); TileMaster network = master(); if (network != null) network.refresh();
    }
    public void recover(ItemStack stack) {
        if (Stacks.INSTANCE.empty(stack)) return;
        for (int slot = 0; slot < recovery.length; slot++) if (Stacks.INSTANCE.empty(recovery[slot])) {
            recovery[slot] = stack.copy(); onInventoryChanged(); return;
        }
        // Unexpected mod inventory changes must never delete the rejected remainder.
        StorageNetwork.drop(worldObj, position(), stack);
    }
    public boolean flushRecovery(TileMaster network) {
        boolean empty = true;
        for (int slot = 0; slot < recovery.length; slot++) if (!Stacks.INSTANCE.empty(recovery[slot])) {
            recovery[slot] = network.insert(recovery[slot], false, null); onInventoryChanged();
            if (!Stacks.INSTANCE.empty(recovery[slot])) empty = false;
        }
        return empty;
    }
    public void dropContents() {
        for (ItemStack[] contents : new ItemStack[][] {upgrades, matrix, recovery}) for (int i = 0; i < contents.length; i++) {
            StorageNetwork.drop(worldObj, position(), contents[i]); contents[i] = null;
        }
    }
    public void importFilters() {
        if (kind() == 7) {
            mrriegel.storagenetwork.legacy.core.InventoryAccess<ItemStack> input = inventory(processingInput), output = inventory(processingOutput);
            if (input == null || output == null) return;
            Arrays.fill(filters, null); int next = 0;
            for (int slot = 0; slot < input.slots() && next < 9; slot++) { ItemStack stack = input.peek(slot); if (!Stacks.INSTANCE.empty(stack)) filters[next++] = Stacks.INSTANCE.copy(stack, 1); }
            next = 9;
            for (int slot = 0; slot < output.slots() && next < 18; slot++) { ItemStack stack = output.extract(slot, 1, true); if (!Stacks.INSTANCE.empty(stack)) filters[next++] = stack; }
            processIndex = 0; processRemaining = 0; processStatus = 2;
        }
        else {
            mrriegel.storagenetwork.legacy.core.InventoryAccess<ItemStack> inventory = inventory(direction ^ 1); if (inventory == null) return;
            Arrays.fill(filters, null); int next = 0;
            for (int slot = 0; slot < inventory.slots() && next < filters.length; slot++) {
                ItemStack stack = inventory.peek(slot); if (Stacks.INSTANCE.empty(stack)) continue;
                boolean seen = false; for (ItemStack filter : filters) if (Stacks.matches(filter, stack, meta, ores, nbt)) { seen = true; break; }
                if (!seen) filters[next++] = Stacks.INSTANCE.copy(stack, Math.min(64, stack.stackSize));
            }
        }
        Platform.sync(this);
    }
    @Override public void validate() { super.validate(); cached = null; }
    @Override public void invalidate() {
        if (worldObj != null && !worldObj.isRemote) { TileMaster network = master(); if (network != null) network.refresh(); } cached = null; super.invalidate();
    }
    @Override public void onChunkUnload() {
        if (worldObj != null && !worldObj.isRemote && StorageNetwork.reloadOnUnload) { TileMaster network = master(); if (network != null) network.refresh(); }
        cached = null; super.onChunkUnload();
    }
    public static void writeStacks(NBTTagCompound tag, String key, ItemStack[] stacks) {
        NBTTagList list = new NBTTagList();
        for (int slot = 0; slot < stacks.length; slot++) if (!Stacks.INSTANCE.empty(stacks[slot])) {
            NBTTagCompound item = new NBTTagCompound(); stacks[slot].writeToNBT(item);
            item.setByte("Slot", (byte) slot); item.setInteger("SSNCount", stacks[slot].stackSize); list.appendTag(item);
        }
        tag.setTag(key, list);
    }
    public static void readStacks(NBTTagCompound tag, String key, ItemStack[] stacks) {
        Arrays.fill(stacks, null); NBTTagList list = Platform.list(tag, key);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound item = Platform.compound(list, i); int slot = item.getByte("Slot") & 255;
            if (slot >= stacks.length) continue;
            ItemStack stack = ItemStack.loadItemStackFromNBT(item);
            if (stack != null && item.hasKey("SSNCount")) stack.stackSize = Math.max(0, item.getInteger("SSNCount"));
            stacks[slot] = Stacks.INSTANCE.empty(stack) ? null : stack;
        }
    }
    @Override public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); tag.setInteger("ssnSchema", 1);
        if (master != null) { NBTTagCompound connectable = new NBTTagCompound(); connectable.setTag("master", Platform.writePosition(master)); tag.setTag("connectable", connectable); }
        tag.setInteger("direction", direction); tag.setInteger("prio", priority); tag.setInteger("way", way);
        tag.setBoolean("whitelist", whitelist); tag.setBoolean("meta", meta); tag.setBoolean("ores", ores); tag.setBoolean("nbt", nbt);
        tag.setInteger("sort", sort); tag.setBoolean("dir", downwards);
        writeStacks(tag, "filters", filters); writeStacks(tag, "upgrades", upgrades); writeStacks(tag, "matrix", matrix); writeStacks(tag, "recovery", recovery);
        tag.setInteger("operationLimit", operationLimit); tag.setBoolean("operationSmaller", operationSmaller);
        if (operationStack != null) { NBTTagCompound stack = new NBTTagCompound(); operationStack.writeToNBT(stack); tag.setTag("operationStack", stack); }
        tag.setInteger("sn_process_count", processCount); tag.setInteger("sn_process_status", processStatus);
        tag.setInteger("sn_process_stack", processIndex); tag.setInteger("sn_process_remaining", processRemaining); tag.setBoolean("sn_process_always", alwaysActive);
        tag.setInteger("processingTop", processingInput); tag.setInteger("processingBottom", processingOutput);
        NBTTagCompound facadeTag = new NBTTagCompound(); facadeTag.setString("block", facade); facadeTag.setInteger("meta", facadeMeta); tag.setTag("facade", facadeTag);
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag); cached = null;
        NBTTagCompound connectable = tag.getCompoundTag("connectable"); master = connectable.hasKey("master") ? Platform.readPosition(connectable.getCompoundTag("master")) : null;
        direction = tag.hasKey("direction") ? bound(tag.getInteger("direction"), -1, 5, -1) : -1;
        priority = tag.getInteger("prio"); way = tag.hasKey("way") ? bound(tag.getInteger("way"), 0, 2, 2) : 2;
        whitelist = tag.getBoolean("whitelist"); meta = !tag.hasKey("meta") || tag.getBoolean("meta"); ores = tag.getBoolean("ores"); nbt = tag.getBoolean("nbt");
        sort = tag.hasKey("sort") ? bound(tag.getInteger("sort"), 0, 2, 1) : 1; downwards = tag.getBoolean("dir");
        readStacks(tag, "filters", filters); readStacks(tag, "upgrades", upgrades); readStacks(tag, "matrix", matrix); readStacks(tag, "recovery", recovery);
        for (int i = 0; i < upgrades.length; i++) if (upgrades[i] != null) upgrades[i].stackSize = 1;
        operationLimit = Math.max(0, tag.getInteger("operationLimit")); operationSmaller = !tag.hasKey("operationSmaller") || tag.getBoolean("operationSmaller");
        operationStack = tag.hasKey("operationStack") ? ItemStack.loadItemStackFromNBT(tag.getCompoundTag("operationStack")) : null;
        processCount = Math.max(0, tag.getInteger("sn_process_count")); processStatus = tag.hasKey("sn_process_status") ? bound(tag.getInteger("sn_process_status"), 0, 2, 2) : 2;
        processIndex = bound(tag.getInteger("sn_process_stack"), 0, 9, 0); processRemaining = Math.max(0, tag.getInteger("sn_process_remaining")); alwaysActive = !tag.hasKey("sn_process_always") || tag.getBoolean("sn_process_always");
        processingInput = tag.hasKey("processingTop") ? bound(tag.getInteger("processingTop"), 0, 5, 1) : 1;
        processingOutput = bound(tag.getInteger("processingBottom"), 0, 5, 0);
        NBTTagCompound facadeTag = tag.getCompoundTag("facade"); facade = facadeTag.getString("block"); facadeMeta = facadeTag.getInteger("meta") & 15;
    }
    public static int bound(int value, int low, int high, int fallback) { return value < low || value > high ? fallback : value; }
    @Override public Packet getDescriptionPacket() { NBTTagCompound tag = new NBTTagCompound(); writeToNBT(tag); return new Packet132TileEntityData(xCoord, yCoord, zCoord, 1, tag); }
    @Override public void onDataPacket(INetworkManager manager, Packet132TileEntityData packet) { readFromNBT(packet.data); }
    public int getSizeInventory() { return 4; }
    public ItemStack getStackInSlot(int slot) { return slot >= 0 && slot < 4 ? upgrades[slot] : null; }
    public ItemStack decrStackSize(int slot, int count) {
        ItemStack stack = getStackInSlot(slot); if (Stacks.INSTANCE.empty(stack) || count <= 0) return null;
        ItemStack result = Stacks.INSTANCE.copy(stack, Math.min(count, stack.stackSize));
        setInventorySlotContents(slot, Stacks.INSTANCE.copy(stack, stack.stackSize - result.stackSize)); return result;
    }
    public ItemStack getStackInSlotOnClosing(int slot) { ItemStack stack = getStackInSlot(slot); setInventorySlotContents(slot, null); return stack; }
    public void setInventorySlotContents(int slot, ItemStack stack) { if (slot >= 0 && slot < 4) { upgrades[slot] = Stacks.INSTANCE.copy(stack, 1); onInventoryChanged(); } }
    public String getInvName() { return "container.storagenetwork.cable"; }
    public boolean isInvNameLocalized() { return false; }
    public int getInventoryStackLimit() { return 1; }
    public boolean isUseableByPlayer(net.minecraft.entity.player.EntityPlayer player) {
        return Platform.tile(worldObj, position()) == this && player.getDistanceSq(xCoord + .5, yCoord + .5, zCoord + .5) <= 64;
    }
    public void openChest() {}
    public void closeChest() {}
    public boolean isItemValidForSlot(int slot, ItemStack stack) { return kind() == 5 || kind() == 6 ? stack != null && stack.getItem() == StorageNetwork.upgrade : false; }
}
