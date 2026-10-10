package mrriegel.storagenetwork.legacy;

import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.statemap.StateMap;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;

public final class ClientProxy extends CommonProxy {
    private final ConcurrentLinkedQueue<NBTTagCompound> pending = new ConcurrentLinkedQueue<NBTTagCompound>();
    private final KeyBinding remoteKey = new KeyBinding("key.storagenetwork.remote", Keyboard.KEY_V, "key.categories.inventory");
    @Override public void preInit() {
        for (NetworkBlock block : StorageNetwork.blocks) {
            if (!block.cable()) ModelLoader.setCustomStateMapper(block, new StateMap.Builder().ignore(NetworkBlock.CONNECTED[0], NetworkBlock.CONNECTED[1], NetworkBlock.CONNECTED[2], NetworkBlock.CONNECTED[3], NetworkBlock.CONNECTED[4], NetworkBlock.CONNECTED[5], NetworkBlock.COVERED, NetworkBlock.TARGET).build());
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(block), 0, new ModelResourceLocation("storagenetwork:" + StorageNetwork.NAMES[block.kind], "inventory"));
        }
        for (Item item : new Item[] {StorageNetwork.upgrade, StorageNetwork.remote, StorageNetwork.picker, StorageNetwork.collector}) {
            NetworkItem networkItem = (NetworkItem) item;
            for (int metadata = 0; metadata < networkItem.variants; metadata++) ModelLoader.setCustomModelResourceLocation(item, metadata, new ModelResourceLocation("storagenetwork:" + networkItem.name + (networkItem.variants > 1 ? "_" + metadata : ""), "inventory"));
        }
        ClientRegistry.bindTileEntitySpecialRenderer(TileNode.class, new FacadeRenderer());
    }
    @Override public void init() { ClientRegistry.registerKeyBinding(remoteKey); FMLCommonHandler.instance().bus().register(this); }
    @Override public void receive(NBTTagCompound tag) { pending.add(tag); }
    @SubscribeEvent public void key(InputEvent.KeyInputEvent event) { if (remoteKey.isPressed() && Minecraft.getMinecraft().thePlayer != null) Protocol.send(-1, 0, Actions.OPEN_REMOTE, 0, 0, 0); }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (int i = 0; i < 128; i++) { NBTTagCompound tag = pending.poll(); if (tag == null) break; if (Minecraft.getMinecraft().currentScreen instanceof NetworkGui) ((NetworkGui) Minecraft.getMinecraft().currentScreen).receive(tag); }
    }
    @Override public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == 1 && x >= 0 && x < 36) return new NetworkGui(new NetworkContainer(player, null, x));
        TileEntity tile = world.getTileEntity(new BlockPos(x, y, z)); return tile instanceof TileNode ? new NetworkGui(new NetworkContainer(player, (TileNode) tile, -1)) : null;
    }
}
