package mrriegel.storagenetwork.legacy;

import java.util.concurrent.ConcurrentLinkedQueue;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import mrriegel.storagenetwork.legacy.core.TerminalLayout;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;

public final class ClientProxy extends CommonProxy {
    private int renderId;
    private final ConcurrentLinkedQueue<NBTTagCompound> pending = new ConcurrentLinkedQueue<NBTTagCompound>();
    private final KeyBinding remoteKey = new KeyBinding("key.storagenetwork.remote", Keyboard.KEY_V, "key.categories.inventory");
    @Override public void preInit() { renderId = RenderingRegistry.getNextAvailableRenderId(); RenderingRegistry.registerBlockHandler(new CableRenderer(renderId)); }
    @Override public void init() { ClientRegistry.registerKeyBinding(remoteKey); FMLCommonHandler.instance().bus().register(this); }
    @Override public int cableRenderId() { return renderId; }
    @Override public void receive(NBTTagCompound tag) { pending.add(tag); }
    @SubscribeEvent public void key(InputEvent.KeyInputEvent event) {
        if (remoteKey.isPressed() && Minecraft.getMinecraft().thePlayer != null) Protocol.send(-1, 0, Actions.OPEN_REMOTE, 0, 0, 0);
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (int i = 0; i < 128; i++) {
            NBTTagCompound tag = pending.poll(); if (tag == null) break;
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft.currentScreen instanceof NetworkGui) ((NetworkGui) minecraft.currentScreen).receive(tag);
        }
    }
    @Override public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == 1 && x >= 0 && x < 36) return new NetworkGui(new NetworkContainer(player, null, x));
        TileEntity tile = world.getTileEntity(x, y, z); return id == 0 && tile instanceof TileNode && TerminalLayout.hasBlockGui(((TileNode) tile).kind()) ? new NetworkGui(new NetworkContainer(player, (TileNode) tile, -1)) : null;
    }
}
