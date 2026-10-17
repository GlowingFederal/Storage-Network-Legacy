package mrriegel.storagenetwork.legacy;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import mrriegel.storagenetwork.legacy.core.Position;

public class RemoteItem extends NetworkItem {
    public RemoteItem(String name, int variants) { super(name, variants); }
    @Override public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hx, float hy, float hz) {
        if (!(world.getTileEntity(x, y, z) instanceof TileMaster)) {
            if (world.isRemote || this != StorageNetwork.picker) return false;
            TileMaster network = boundMaster(stack); if (network == null) return false;
            Position pos = new Position(Platform.dimension(world), x, y, z);
            net.minecraft.block.Block block = Platform.block(world, pos);
            ItemStack pattern = new ItemStack(block, 1, block.damageDropped(Platform.metadata(world, pos)));
            ItemStack found = network.request(pattern, player.isSneaking() ? 1 : 64, false, true, false, false, null);
            if (found != null) { player.inventory.addItemStackToInventory(found); if (found.stackSize > 0) player.dropPlayerItemWithRandomChoice(found, false); }
            return true;
        }
        if (!world.isRemote) {
            NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
            tag.setInteger("x", x); tag.setInteger("y", y); tag.setInteger("z", z); tag.setInteger("dim", Platform.dimension(world));
            tag.setBoolean("bound", true); tag.setInteger("sort", 1); stack.setTagCompound(tag);
        }
        return true;
    }
    @Override public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote && this == StorageNetwork.remote && validMaster(player, stack) != null)
            player.openGui(StorageNetwork.instance, 1, world, player.inventory.currentItem, 0, 0);
        return stack;
    }
    public static TileMaster boundMaster(ItemStack stack) {
        if (Stacks.INSTANCE.empty(stack) || !stack.hasTagCompound() || !stack.getTagCompound().getBoolean("bound")) return null;
        NBTTagCompound tag = stack.getTagCompound(); Position pos = new Position(tag.getInteger("dim"), tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z"));
        TileEntity tile = Platform.tile(Platform.world(pos.dimension), pos); return tile instanceof TileMaster ? (TileMaster) tile : null;
    }
    public static TileMaster validMaster(EntityPlayer player, ItemStack stack) {
        if (Stacks.INSTANCE.empty(stack) || stack.getItem() != StorageNetwork.remote || stack.getItemDamage() < 0 || stack.getItemDamage() > 3) return null;
        TileMaster master = boundMaster(stack); if (master == null) return null;
        int tier = stack.getItemDamage(); Position pos = master.position();
        if (tier != 1 && pos.dimension != Platform.dimension(player.worldObj)) return null;
        if (tier == 0 && player.getDistanceSq(pos.x, pos.y, pos.z) > (double) StorageNetwork.remoteRange * StorageNetwork.remoteRange) return null;
        return master;
    }
}
