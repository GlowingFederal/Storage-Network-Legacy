package mrriegel.storagenetwork.legacy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public final class NetworkGui extends GuiContainer {
    private final net.minecraft.client.renderer.entity.RenderItem itemRender = new net.minecraft.client.renderer.entity.RenderItem();
    private final NetworkContainer container;
    private final List<ItemStack> catalog = new ArrayList<ItemStack>();
    private final List<Integer> displayed = new ArrayList<Integer>();
    private final List<ItemStack> processorItems = new ArrayList<ItemStack>();
    private final List<String> processorLabels = new ArrayList<String>();
    private GuiTextField search, limit;
    private int revision, page, selectedProcessor, sort = 1;
    private boolean down, rebuild = true;
    private String previousSearch = "";
    public NetworkGui(NetworkContainer container) { super(container); this.container = container; xSize = 176; ySize = 256; }
    @Override public void initGui() {
        super.initGui(); Keyboard.enableRepeatEvents(true); buttonList.clear();
        search = new GuiTextField(fontRenderer, guiLeft + 8, guiTop + 7, 89, 12); search.setMaxStringLength(128);
        limit = new GuiTextField(fontRenderer, guiLeft + 30, guiTop + 125, 60, 12); limit.setMaxStringLength(10);
        if (container.node != null) { sort = container.node.sort; down = container.node.downwards; }
        else if (container.remoteSlot >= 0) {
            ItemStack remote = container.player.inventory.getStackInSlot(container.remoteSlot);
            if (remote != null && remote.hasTagCompound()) { sort = remote.getTagCompound().getInteger("sort"); down = remote.getTagCompound().getBoolean("down"); }
        }
        if (container.cable) {
            add(Actions.WHITELIST, 8, 82, 43, "List"); add(Actions.META, 53, 82, 35, "Meta"); add(Actions.ORE, 90, 82, 35, "Ore"); add(Actions.NBT, 127, 82, 41, "NBT");
            add(100, 8, 101, 25, "Pr-"); add(101, 35, 101, 25, "Pr+"); add(Actions.IMPORT_FILTER, 62, 101, 46, "Import");
            if (container.node.kind() == 4) add(Actions.WAY, 110, 101, 58, "Access");
            if (container.node.kind() == 5 || container.node.kind() == 6) {
                add(Actions.OP_STACK, 8, 125, 20, "Item"); add(Actions.OP_LIMIT, 92, 125, 30, "Set"); add(Actions.OP_SMALLER, 124, 125, 44, "Mode");
            }
            if (container.node.kind() == 7) {
                add(Actions.PROC_INPUT, 8, 122, 47, "Input"); add(Actions.PROC_OUTPUT, 57, 122, 47, "Output");
                add(Actions.PROC_ALWAYS, 106, 122, 62, "Always"); add(102, 8, 144, 28, "-1"); add(103, 38, 144, 28, "+1"); add(Actions.PROC_RESTART, 106, 144, 62, "Restart");
            }
        }
        else if (container.controller) {
            add(102, 8, 151, 30, "-1"); add(103, 40, 151, 30, "+1"); add(Actions.PROC_ALWAYS, 72, 151, 49, "Always"); add(Actions.PROC_RESTART, 123, 151, 45, "Restart");
        }
        else {
            add(Actions.SORT, 100, 6, 29, "Sort"); add(Actions.DOWN, 131, 6, 17, "^"); add(Actions.REFRESH, 150, 6, 18, "R");
            add(104, 120, 145, 22, "<"); add(105, 144, 145, 22, ">");
            if (container.crafting) add(Actions.CLEAR, 100, 110, 66, "Clear grid");
        }
        send(Actions.REFRESH, 0, 0, 0);
    }
    @SuppressWarnings("unchecked") private void add(int id, int x, int y, int width, String text) { buttonList.add(new GuiButton(id, guiLeft + x, guiTop + y, width, 16, text)); }
    private void send(int action, int a, int b, int c) { Protocol.send(container.windowId, revision, action, a, b, c); }
    public void receive(NBTTagCompound tag) {
        if (tag.getInteger("window") != container.windowId) return;
        if (tag.hasKey("node") && container.node != null) {
            container.node.readFromNBT(tag.getCompoundTag("node")); if (!limit.isFocused()) limit.setText(Integer.toString(container.node.operationLimit));
        }
        else if (tag.hasKey("processors")) {
            processorItems.clear(); processorLabels.clear(); NBTTagList list = Platform.list(tag, "processors");
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound entry = Platform.compound(list, i); ItemStack item = ItemStack.loadItemStackFromNBT(entry);
                processorItems.add(item); processorLabels.add((item == null ? "Processor" : item.getDisplayName()) + " x" + entry.getInteger("count") + (entry.getBoolean("always") ? " *" : ""));
            }
        }
        else {
            if (tag.getBoolean("reset")) catalog.clear(); int size = Math.max(0, Math.min(100000, tag.getInteger("size")));
            while (catalog.size() > size) catalog.remove(catalog.size() - 1); while (catalog.size() < size) catalog.add(null);
            NBTTagList changes = Platform.list(tag, "changes");
            for (int i = 0; i < changes.tagCount(); i++) {
                NBTTagCompound entry = Platform.compound(changes, i); int index = entry.getInteger("index");
                if (index < 0 || index >= size) continue; ItemStack stack = ItemStack.loadItemStackFromNBT(entry);
                if (stack != null) stack.stackSize = Math.max(0, entry.getInteger("SSNCount")); catalog.set(index, stack);
            }
            revision = tag.getInteger("revision"); rebuild = true;
        }
    }
    @Override protected void actionPerformed(GuiButton button) {
        int id = button.id;
        if (id == 104 || id == 105) { page = Math.max(0, page + (id == 104 ? -1 : 1)); return; }
        if (id == Actions.SORT) { sort = (sort + 1) % 3; rebuild = true; send(id, sort, 0, 0); return; }
        if (id == Actions.DOWN) { down = !down; rebuild = true; send(id, down ? 1 : 0, 0, 0); return; }
        if (id == 100 || id == 101) { send(Actions.PRIORITY, 0, id == 100 ? -1 : 1, 0); return; }
        if (id == 102 || id == 103) { send(Actions.PROC_COUNT, selectedProcessor, (id == 102 ? -1 : 1) * (isShiftKeyDown() ? 64 : 1), 0); return; }
        if (id == Actions.PROC_INPUT || id == Actions.PROC_OUTPUT) {
            int side = id == Actions.PROC_INPUT ? container.node.processingInput : container.node.processingOutput; send(id, 0, (side + 1) % 6, 0); return;
        }
        if (id == Actions.OP_LIMIT) { try { send(id, Math.max(0, Integer.parseInt(limit.getText())), 0, 0); } catch (NumberFormatException invalid) { limit.setText(Integer.toString(container.node.operationLimit)); } return; }
        send(id, selectedProcessor, 0, 0);
    }
    private boolean matchesSearch(ItemStack stack) {
        String text = search.getText().toLowerCase(Locale.ROOT); String haystack = stack.getDisplayName();
        if (text.startsWith("@")) { haystack = Platform.modName(stack.getItem()); text = text.substring(1); }
        else if (text.startsWith("#")) { haystack = stack.getTooltip(mc.thePlayer, false).toString(); text = text.substring(1); }
        else if (text.startsWith("$")) { StringBuilder ores = new StringBuilder(); for (int ore : Stacks.oreIds(stack)) ores.append(OreDictionary.getOreName(ore)).append(' '); haystack = ores.toString(); text = text.substring(1); }
        else if (text.startsWith("%")) { haystack = stack.getItem().getCreativeTab() == null ? "" : stack.getItem().getCreativeTab().getTranslatedTabLabel(); text = text.substring(1); }
        return haystack.toLowerCase(Locale.ROOT).contains(text);
    }
    private void rebuild() {
        if (!rebuild && previousSearch.equals(search.getText())) return;
        previousSearch = search.getText(); rebuild = false; displayed.clear();
        for (int i = 0; i < catalog.size(); i++) if (!Stacks.INSTANCE.empty(catalog.get(i)) && matchesSearch(catalog.get(i))) displayed.add(i);
        Collections.sort(displayed, new Comparator<Integer>() {
            public int compare(Integer a, Integer b) {
                ItemStack left = catalog.get(a), right = catalog.get(b); int compared;
                if (sort == 0) compared = Integer.compare(right.stackSize, left.stackSize);
                else if (sort == 2) compared = Platform.modName(left.getItem()).compareToIgnoreCase(Platform.modName(right.getItem()));
                else compared = left.getDisplayName().compareToIgnoreCase(right.getDisplayName());
                if (compared == 0) compared = a.compareTo(b); return down ? -compared : compared;
            }
        });
        page = Math.min(page, Math.max(0, (displayed.size() - 1) / 36));
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partial, int mouseX, int mouseY) {
        GL11.glColor4f(1, 1, 1, 1); mc.getTextureManager().bindTexture(new ResourceLocation("storagenetwork", "textures/gui/request_full.png"));
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
        if (container.cable) {
            TileNode node = container.node;
            fontRenderer.drawString("Priority: " + node.priority, guiLeft + 8, guiTop + 25, 0x333333);
            for (int i = 0; i < 18; i++) drawStack(node.filters[i], 8 + i % 9 * 18, 42 + i / 9 * 18);
            fontRenderer.drawString((node.whitelist ? "Whitelist" : "Blacklist") + " " + (node.meta ? "M " : "") + (node.ores ? "O " : "") + (node.nbt ? "N" : ""), guiLeft + 8, guiTop + 116, 0x333333);
            if (node.kind() == 7) fontRenderer.drawString("Count " + node.processCount + "  " + (node.alwaysActive ? "Always" : "Batch"), guiLeft + 8, guiTop + 135, 0x333333);
            if (node.kind() == 5 || node.kind() == 6) { limit.drawTextBox(); fontRenderer.drawString(node.operationSmaller ? "Count <= limit" : "Count > limit", guiLeft + 8, guiTop + 145, 0x333333); }
        }
        else if (container.controller) {
            for (int i = page * 5; i < processorLabels.size() && i < page * 5 + 5; i++) {
                int y = 25 + (i - page * 5) * 22;
                if (i == selectedProcessor) drawRect(guiLeft + 6, guiTop + y, guiLeft + 170, guiTop + y + 20, 0x557777FF);
                drawStack(processorItems.get(i), 8, y); fontRenderer.drawString(processorLabels.get(i), guiLeft + 28, guiTop + y + 4, 0x333333);
            }
        }
        else {
            search.drawTextBox(); rebuild();
            for (int i = page * 36; i < displayed.size() && i < page * 36 + 36; i++) drawStack(catalog.get(displayed.get(i)), 8 + i % 9 * 18, 26 + (i % 36) / 9 * 18);
            fontRenderer.drawString((page + 1) + "/" + Math.max(1, (displayed.size() + 35) / 36), guiLeft + 120, guiTop + 134, 0x333333);
        }
    }
    private void drawStack(ItemStack stack, int x, int y) {
        drawRect(guiLeft + x, guiTop + y, guiLeft + x + 16, guiTop + y + 16, 0x55888888);
        if (Stacks.INSTANCE.empty(stack)) return;
        itemRender.renderItemAndEffectIntoGUI(fontRenderer, mc.getTextureManager(), stack, guiLeft + x, guiTop + y);
        itemRender.renderItemOverlayIntoGUI(fontRenderer, mc.getTextureManager(), stack, guiLeft + x, guiTop + y, Integer.toString(stack.stackSize));
    }
    @Override public void drawScreen(int x, int y, float partial) {
        super.drawScreen(x, y, partial);
        int rx = x - guiLeft, ry = y - guiTop; ItemStack hover = null;
        if (container.cable && rx >= 8 && rx < 170 && ry >= 42 && ry < 78) hover = container.node.filters[(rx - 8) / 18 + (ry - 42) / 18 * 9];
        else if (!container.controller && !container.cable && rx >= 8 && rx < 170 && ry >= 26 && ry < 98) {
            int index = page * 36 + (rx - 8) / 18 + (ry - 26) / 18 * 9;
            if (index < displayed.size()) hover = catalog.get(displayed.get(index));
        }
        if (!Stacks.INSTANCE.empty(hover)) drawHoveringText(hover.getTooltip(mc.thePlayer, false), x, y, fontRenderer);
    }
    @Override protected void mouseClicked(int x, int y, int button) {
        int rx = x - guiLeft, ry = y - guiTop;
        if (container.cable && rx >= 8 && rx < 170 && ry >= 42 && ry < 78) {
            int slot = (rx - 8) / 18 + (ry - 42) / 18 * 9; send(Actions.FILTER, slot, isCtrlKeyDown() ? button == 0 ? 1 : -1 : 0, 0); return;
        }
        if (container.controller && ry >= 25 && ry < 135) { selectedProcessor = page * 5 + (ry - 25) / 22; return; }
        if (!container.cable && !container.controller) {
            search.mouseClicked(x, y, button);
            if (rx >= 8 && rx < 170 && ry >= 26 && ry < 98) {
                int index = page * 36 + (rx - 8) / 18 + (ry - 26) / 18 * 9;
                if (index < displayed.size()) send(Actions.REQUEST, displayed.get(index), button, (isShiftKeyDown() ? 1 : 0) | (isCtrlKeyDown() ? 2 : 0));
                else send(Actions.INSERT, 0, button, 0); return;
            }
        }
        else limit.mouseClicked(x, y, button);
        super.mouseClicked(x, y, button);
    }
    @Override protected void keyTyped(char typed, int key) {
        if (search.isFocused() && search.textboxKeyTyped(typed, key)) { page = 0; rebuild = true; return; }
        if (limit.isFocused() && (Character.isDigit(typed) || key == Keyboard.KEY_BACK) && limit.textboxKeyTyped(typed, key)) return;
        super.keyTyped(typed, key);
    }
    @Override public void handleMouseInput() {
        super.handleMouseInput(); int wheel = Mouse.getEventDWheel();
        if (wheel != 0) page = Math.max(0, Math.min(Math.max(0, (container.controller ? processorItems.size() - 1 : displayed.size() - 1) / (container.controller ? 5 : 36)), page + (wheel < 0 ? 1 : -1)));
    }
    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); super.onGuiClosed(); }
}
