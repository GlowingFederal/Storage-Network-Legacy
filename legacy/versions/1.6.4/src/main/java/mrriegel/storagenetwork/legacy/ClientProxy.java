package mrriegel.storagenetwork.legacy;

import java.util.EnumSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import cpw.mods.fml.client.registry.KeyBindingRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;

public final class ClientProxy extends CommonProxy implements ITickHandler {
    private int renderId;
    private final ConcurrentLinkedQueue<NBTTagCompound> pending = new ConcurrentLinkedQueue<NBTTagCompound>();
    @Override public void preInit() { renderId = RenderingRegistry.getNextAvailableRenderId(); RenderingRegistry.registerBlockHandler(new CableRenderer(renderId)); }
    @Override public void init() {
        TickRegistry.registerTickHandler(this, Side.CLIENT);
        KeyBindingRegistry.registerKeyBinding(new KeyBindingRegistry.KeyHandler(new KeyBinding[] {new KeyBinding("key.storagenetwork.remote", Keyboard.KEY_V)}, new boolean[] {false}) {
            public void keyDown(EnumSet<TickType> types, KeyBinding key, boolean end, boolean repeat) {
                if (end && Minecraft.getMinecraft().thePlayer != null && Minecraft.getMinecraft().currentScreen == null) Protocol.send(-1, 0, Actions.OPEN_REMOTE, 0, 0, 0);
            }
            public void keyUp(EnumSet<TickType> types, KeyBinding key, boolean end) {}
            public EnumSet<TickType> ticks() { return EnumSet.of(TickType.CLIENT); }
            public String getLabel() { return "SSN remote hotkey"; }
        });
    }
    @Override public int cableRenderId() { return renderId; }
    @Override public void receive(NBTTagCompound tag) { pending.add(tag); }
    public void tickStart(EnumSet<TickType> types, Object... data) {}
    public void tickEnd(EnumSet<TickType> types, Object... data) {
        for (int i = 0; i < 128; i++) {
            NBTTagCompound tag = pending.poll(); if (tag == null) break;
            if (Minecraft.getMinecraft().currentScreen instanceof NetworkGui) ((NetworkGui) Minecraft.getMinecraft().currentScreen).receive(tag);
        }
    }
    public EnumSet<TickType> ticks() { return EnumSet.of(TickType.CLIENT); }
    public String getLabel() { return "SSN client updates"; }
    @Override public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == 1 && x >= 0 && x < 36) return new NetworkGui(new NetworkContainer(player, null, x));
        TileEntity tile = world.getBlockTileEntity(x, y, z); return tile instanceof TileNode ? new NetworkGui(new NetworkContainer(player, (TileNode) tile, -1)) : null;
    }
}
