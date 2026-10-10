package mrriegel.storagenetwork.legacy;

import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class CommonProxy implements IGuiHandler {
    public void preInit() {}
    public void init() {}
    public int cableRenderId() { return -1; }
    public void receive(NBTTagCompound tag) {}
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == 1) {
            if (x < 0 || x >= 36 || RemoteItem.validMaster(player, player.inventory.getStackInSlot(x)) == null) return null;
            return new NetworkContainer(player, null, x);
        }
        TileEntity tile = world.getTileEntity(new net.minecraft.util.BlockPos(x, y, z));
        if (!(tile instanceof TileNode) || !((TileNode) tile).isUseableByPlayer(player)) return null;
        return new NetworkContainer(player, (TileNode) tile, -1);
    }
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) { return null; }
}
