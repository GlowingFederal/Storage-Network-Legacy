package mrriegel.storagenetwork.legacy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import mrriegel.storagenetwork.legacy.core.BrowserViewport;
import mrriegel.storagenetwork.legacy.core.TerminalLayout;
import mrriegel.storagenetwork.legacy.core.TerminalLayout.Rect;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.oredict.OreDictionary;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class NetworkGui extends GuiContainer {
    private static String retainedSearch = "";
    private final NetworkContainer container;
    private final TerminalLayout layout;
    private final BrowserViewport viewport = new BrowserViewport(TerminalLayout.BROWSER.width / TerminalLayout.CELL, TerminalLayout.BROWSER.height / TerminalLayout.CELL);
    private final List<ItemStack> catalog = new ArrayList<ItemStack>();
    private final List<Integer> displayed = new ArrayList<Integer>();
    private final List<ItemStack> processorItems = new ArrayList<ItemStack>();
    private final List<String> processorLabels = new ArrayList<String>();
    private GuiTextField search, limit;
    private int revision, processorOffset, selectedProcessor, sort = 1, dragGrab = -1;
    private boolean down, rebuild = true;
    private String previousSearch = "";

    public NetworkGui(NetworkContainer container) {
        super(container); this.container = container; layout = container.layout;
        xSize = browser() ? layout.width : 176; ySize = browser() ? TerminalLayout.HEIGHT : 256;
    }
    private boolean browser() { return !container.cable && !container.controller; }
    @Override public void initGui() {
        String text = search == null ? retainedSearch : search.getText();
        NEIIntegration.register();
        super.initGui(); Keyboard.enableRepeatEvents(true); buttonList.clear(); dragGrab = -1;
        if (browser()) {
            Rect bounds = TerminalLayout.SEARCH;
            search = new GuiTextField(0, fontRendererObj, guiLeft + bounds.x, guiTop + bounds.y, bounds.width, bounds.height);
            search.setMaxStringLength(128); search.setText(text); search.setFocused(true);
            add(Actions.SORT, TerminalLayout.SORT, "Sort");
            add(Actions.DOWN, TerminalLayout.DIRECTION, "^");
            add(Actions.REFRESH, TerminalLayout.REFRESH, "R");
            if (container.crafting) add(Actions.CLEAR, TerminalLayout.CLEAR, "Clear grid");
        }
        else if (container.cable) {
            add(Actions.WHITELIST, 8, 82, 43, "List"); add(Actions.META, 53, 82, 35, "Meta"); add(Actions.ORE, 90, 82, 35, "Ore"); add(Actions.NBT, 127, 82, 41, "NBT");
            add(100, 8, 101, 25, "Pr-"); add(101, 35, 101, 25, "Pr+"); add(Actions.IMPORT_FILTER, 62, 101, 46, "Import");
            if (container.node.kind() == 5 || container.node.kind() == 6) {
                limit = new GuiTextField(0, fontRendererObj, guiLeft + 30, guiTop + 125, 60, 12); limit.setMaxStringLength(10);
                limit.setText(Integer.toString(container.node.operationLimit));
                add(Actions.OP_STACK, 8, 125, 20, "Item"); add(Actions.OP_LIMIT, 92, 125, 30, "Set"); add(Actions.OP_SMALLER, 124, 125, 44, "Mode");
            }
            if (container.node.kind() == 7) {
                add(Actions.PROC_INPUT, 8, 122, 47, "Input"); add(Actions.PROC_OUTPUT, 57, 122, 47, "Output");
                add(Actions.PROC_ALWAYS, 106, 122, 62, "Always"); add(102, 8, 144, 28, "-1"); add(103, 38, 144, 28, "+1"); add(Actions.PROC_RESTART, 106, 144, 62, "Restart");
            }
        }
        else {
            add(102, 8, 151, 30, "-1"); add(103, 40, 151, 30, "+1"); add(Actions.PROC_ALWAYS, 72, 151, 49, "Always"); add(Actions.PROC_RESTART, 123, 151, 45, "Restart");
        }
        if (container.node != null) { sort = container.node.sort; down = container.node.downwards; }
        else if (container.remoteSlot >= 0) {
            ItemStack remote = container.player.inventory.getStackInSlot(container.remoteSlot);
            if (remote != null && remote.hasTagCompound()) { sort = remote.getTagCompound().getInteger("sort"); down = remote.getTagCompound().getBoolean("down"); }
        }
        rebuild = true; send(Actions.REFRESH, 0, 0, 0);
    }
    private void add(int id, int x, int y, int width, String text) { add(id, new Rect(x, y, width, 16), text); }
    @SuppressWarnings("unchecked") private void add(int id, Rect bounds, String text) { buttonList.add(new GuiButton(id, guiLeft + bounds.x, guiTop + bounds.y, bounds.width, bounds.height, text)); }
    private void send(int action, int a, int b, int c) { Protocol.send(container.windowId, revision, action, a, b, c); }
    public void receive(NBTTagCompound tag) {
        if (tag.getInteger("window") != container.windowId) return;
        if (tag.hasKey("node") && container.node != null) {
            container.node.readFromNBT(tag.getCompoundTag("node"));
            if (limit != null && !limit.isFocused()) limit.setText(Integer.toString(container.node.operationLimit));
        }
        else if (tag.hasKey("processors")) {
            processorItems.clear(); processorLabels.clear(); NBTTagList list = Platform.list(tag, "processors");
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound entry = Platform.compound(list, i); ItemStack item = ItemStack.loadItemStackFromNBT(entry);
                processorItems.add(item); processorLabels.add((item == null ? "Processor" : item.getDisplayName()) + " x" + entry.getInteger("count") + (entry.getBoolean("always") ? " *" : ""));
            }
            processorOffset = Math.min(processorOffset, Math.max(0, processorItems.size() - 5));
            selectedProcessor = Math.min(selectedProcessor, Math.max(0, processorItems.size() - 1));
        }
        else if (browser()) {
            if (tag.getBoolean("reset")) catalog.clear(); int size = Math.max(0, Math.min(100000, tag.getInteger("size")));
            while (catalog.size() > size) catalog.remove(catalog.size() - 1); while (catalog.size() < size) catalog.add(null);
            NBTTagList changes = Platform.list(tag, "changes");
            for (int i = 0; i < changes.tagCount(); i++) {
                NBTTagCompound entry = Platform.compound(changes, i); int index = entry.getInteger("index");
                if (index < 0 || index >= size) continue; ItemStack stack = ItemStack.loadItemStackFromNBT(entry);
                if (stack != null) stack.stackSize = Math.max(0, entry.getInteger("SSNCount")); catalog.set(index, stack);
            }
            if (tag.hasKey("sort")) { sort = tag.getInteger("sort"); down = tag.getBoolean("down"); }
            revision = tag.getInteger("revision"); rebuild = true;
        }
    }
    @Override protected void actionPerformed(GuiButton button) {
        int id = button.id;
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
    private boolean matchesSearch(ItemStack stack, String text) {
        if (text.isEmpty()) return true;
        String haystack = stack.getDisplayName();
        if (text.startsWith("@")) { haystack = Platform.modName(stack.getItem()); text = text.substring(1); }
        else if (text.startsWith("#")) { haystack = stack.getTooltip(mc.thePlayer, false).toString(); text = text.substring(1); }
        else if (text.startsWith("$")) { StringBuilder ores = new StringBuilder(); for (int ore : Stacks.oreIds(stack)) ores.append(OreDictionary.getOreName(ore)).append(' '); haystack = ores.toString(); text = text.substring(1); }
        else if (text.startsWith("%")) { haystack = stack.getItem().getCreativeTab() == null ? "" : stack.getItem().getCreativeTab().getTranslatedTabLabel(); text = text.substring(1); }
        return haystack.toLowerCase(Locale.ROOT).contains(text);
    }
    private void rebuild() {
        if (!browser() || search == null || !rebuild && previousSearch.equals(search.getText())) return;
        boolean changedSearch = !previousSearch.equals(search.getText());
        previousSearch = search.getText(); retainedSearch = previousSearch; rebuild = false; displayed.clear();
        final String[] keys = new String[catalog.size()];
        String query = previousSearch.toLowerCase(Locale.ROOT);
        for (int i = 0; i < catalog.size(); i++) {
            ItemStack stack = catalog.get(i);
            if (!Stacks.INSTANCE.empty(stack) && matchesSearch(stack, query)) {
                displayed.add(i);
                if (sort != 0) keys[i] = sort == 2 ? Platform.modName(stack.getItem()) : stack.getDisplayName();
            }
        }
        Collections.sort(displayed, new Comparator<Integer>() {
            public int compare(Integer a, Integer b) {
                int compared = sort == 0 ? Integer.compare(catalog.get(b).stackSize, catalog.get(a).stackSize) : keys[a].compareToIgnoreCase(keys[b]);
                if (compared == 0) compared = a.compareTo(b); return down ? -compared : compared;
            }
        });
        viewport.update(displayed.size(), changedSearch);
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partial, int mouseX, int mouseY) {
        flatState();
        panel(0, 0, xSize, ySize, 0xffc6c6c6);
        if (browser()) {
            rebuild();
            Rect network = TerminalLayout.NETWORK_PANEL, player = layout.playerPanel;
            panel(network.x, network.y, network.width, network.height, 0xffb6b3aa);
            panel(player.x, player.y, player.width, player.height, 0xffb6b6b6);
            fontRendererObj.drawString("Storage", guiLeft + network.x + 2, guiTop + network.y + 2, 0x333333);
            String count = displayed.size() + " types";
            fontRendererObj.drawString(count, guiLeft + network.x + network.width - 4 - fontRendererObj.getStringWidth(count), guiTop + network.y + 2, 0x555555);
            fontRendererObj.drawString("Inventory", guiLeft + layout.playerX, guiTop + TerminalLayout.PLAYER_Y - 12, 0x333333);
            Rect area = TerminalLayout.BROWSER;
            for (int cell = 0; cell < viewport.capacity(); cell++) {
                int sx = area.x + cell % viewport.columns * TerminalLayout.CELL;
                int sy = area.y + cell / viewport.columns * TerminalLayout.CELL;
                recess(sx, sy);
                if (mouseX - guiLeft >= sx && mouseX - guiLeft < sx + TerminalLayout.CELL && mouseY - guiTop >= sy && mouseY - guiTop < sy + TerminalLayout.CELL)
                    fill(sx + 1, sy + 1, 16, 16, 0xffaaaaaa);
                int index = viewport.first() + cell;
                if (index < displayed.size()) drawStack(catalog.get(displayed.get(index)), sx + 1, sy + 1);
            }
            if (container.crafting) {
                Rect craft = TerminalLayout.CRAFT_PANEL;
                panel(craft.x, craft.y, craft.width, craft.height, 0xffb0b4ae);
                fontRendererObj.drawString("Crafting", guiLeft + craft.x + 8, guiTop + craft.y + 4, 0x333333);
                fontRendererObj.drawString("->", guiLeft + TerminalLayout.CRAFT_GRID.x + TerminalLayout.CRAFT_GRID.width + 4, guiTop + TerminalLayout.RESULT.y + 5, 0x555555);
            }
            drawScrollbar(); search.drawTextBox();
            for (Object object : buttonList) {
                GuiButton button = (GuiButton) object;
                if (button.id == Actions.SORT) button.displayString = sort == 0 ? "Count" : sort == 2 ? "Mod" : "Name";
                else if (button.id == Actions.DOWN) button.displayString = down ? "v" : "^";
            }
        }
        else if (container.cable) {
            TileNode node = container.node;
            fontRendererObj.drawString("Priority: " + node.priority, guiLeft + 8, guiTop + 25, 0x333333);
            for (int i = 0; i < 18; i++) { recess(7 + i % 9 * 18, 41 + i / 9 * 18); drawStack(node.filters[i], 8 + i % 9 * 18, 42 + i / 9 * 18); }
            fontRendererObj.drawString((node.whitelist ? "Whitelist" : "Blacklist") + " " + (node.meta ? "M " : "") + (node.ores ? "O " : "") + (node.nbt ? "N" : ""), guiLeft + 8, guiTop + 116, 0x333333);
            if (node.kind() == 7) fontRendererObj.drawString("Count " + node.processCount + "  " + (node.alwaysActive ? "Always" : "Batch"), guiLeft + 8, guiTop + 135, 0x333333);
            if (limit != null) { limit.drawTextBox(); fontRendererObj.drawString(node.operationSmaller ? "Count <= limit" : "Count > limit", guiLeft + 8, guiTop + 145, 0x333333); }
        }
        else {
            for (int i = processorOffset; i < processorLabels.size() && i < processorOffset + 5; i++) {
                int y = 25 + (i - processorOffset) * 22;
                if (i == selectedProcessor) fill(6, y, 164, 20, 0xffb0b8cc);
                drawStack(processorItems.get(i), 8, y); fontRendererObj.drawString(processorLabels.get(i), guiLeft + 28, guiTop + y + 4, 0x333333);
            }
        }
        // Slot backgrounds are permanent, including an entirely empty crafting matrix.
        for (Object object : container.inventorySlots) {
            Slot slot = (Slot) object; recess(slot.xDisplayPosition - 1, slot.yDisplayPosition - 1);
        }
    }
    private void fill(int x, int y, int width, int height, int color) { drawRect(guiLeft + x, guiTop + y, guiLeft + x + width, guiTop + y + height, color); }
    private void panel(int x, int y, int width, int height, int color) {
        fill(x, y, width, height, 0xff373737); fill(x + 1, y + 1, width - 2, height - 2, 0xffeeeeee);
        fill(x + 2, y + 2, width - 3, height - 3, 0xff555555); fill(x + 2, y + 2, width - 4, height - 4, color);
    }
    private void recess(int x, int y) {
        fill(x, y, TerminalLayout.CELL, TerminalLayout.CELL, 0xffeeeeee);
        fill(x, y, TerminalLayout.CELL - 1, TerminalLayout.CELL - 1, 0xff373737);
        fill(x + 1, y + 1, 16, 16, 0xff8b8b8b);
    }
    private void drawScrollbar() {
        Rect track = TerminalLayout.SCROLLBAR;
        fill(track.x, track.y, track.width, track.height, 0xff373737);
        int top = track.y + viewport.thumbOffset(track.height), height = viewport.thumbHeight(track.height);
        panel(track.x + 1, top, track.width - 2, height, viewport.maxRow() == 0 ? 0xff888888 : 0xffc6c6c6);
    }
    private void flatState() {
        RenderHelper.disableStandardItemLighting(); GlStateManager.disableDepth(); GlStateManager.disableBlend(); GlStateManager.color(1, 1, 1, 1);
    }
    private void drawStack(ItemStack stack, int x, int y) {
        if (Stacks.INSTANCE.empty(stack)) return;
        // Backgrounds never tint or light items; establish vanilla GUI lighting for each native render.
        GlStateManager.pushMatrix();
        try {
            GlStateManager.color(1, 1, 1, 1); GlStateManager.enableDepth(); GlStateManager.enableRescaleNormal();
            RenderHelper.enableGUIStandardItemLighting();
            ItemStack icon = Stacks.INSTANCE.copy(stack, 1);
            itemRender.renderItemAndEffectIntoGUI(icon, guiLeft + x, guiTop + y);
            itemRender.renderItemOverlayIntoGUI(fontRendererObj, icon, guiLeft + x, guiTop + y, null);
            drawCount(stack.stackSize, x, y);
        } finally { GlStateManager.disableRescaleNormal(); flatState(); GlStateManager.popMatrix(); }
    }
    private void drawCount(int count, int x, int y) {
        if (count <= 1) return;
        String label = countLabel(count); int width = fontRendererObj.getStringWidth(label);
        float scale = Math.min(1.0F, 16.0F / Math.max(1, width));
        RenderHelper.disableStandardItemLighting(); GlStateManager.disableDepth(); GlStateManager.disableBlend();
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(guiLeft + x + 17, guiTop + y + 18, 0);
            GlStateManager.scale(scale, scale, 1);
            fontRendererObj.drawStringWithShadow(label, -width, -fontRendererObj.FONT_HEIGHT, 0xffffff);
        } finally { GlStateManager.popMatrix(); }
    }
    private static String countLabel(int count) {
        if (count < 10000) return Integer.toString(count);
        if (count < 1000000) return count / 1000 + "k";
        if (count < 1000000000) return count / 1000000 + "M";
        return count / 1000000000 + "." + count / 100000000 % 10 + "B";
    }
    @Override public void drawScreen(int x, int y, float partial) {
        super.drawScreen(x, y, partial);
        if (!Stacks.INSTANCE.empty(mc.thePlayer.inventory.getItemStack())) return;
        int rx = x - guiLeft, ry = y - guiTop; ItemStack hover = null;
        if (container.cable && rx >= 8 && rx < 170 && ry >= 42 && ry < 78) hover = container.node.filters[(rx - 8) / 18 + (ry - 42) / 18 * 9];
        else if (browser()) {
            int index = viewport.indexAt(TerminalLayout.BROWSER, rx, ry);
            if (index >= 0) hover = catalog.get(displayed.get(index));
        }
        if (!Stacks.INSTANCE.empty(hover)) {
            List<String> tooltip = new ArrayList<String>();
            for (Object line : hover.getTooltip(mc.thePlayer, false)) tooltip.add(String.valueOf(line));
            if (browser()) tooltip.add("Stored: " + hover.stackSize);
            drawHoveringText(tooltip, x, y, fontRendererObj);
        }
        else if (browser() && TerminalLayout.SEARCH.contains(rx, ry)) drawHoveringText(java.util.Arrays.asList("Search storage", "@ mod | # tooltip | $ ore | % tab", "Right-click to clear. Tab/Enter releases focus."), x, y, fontRendererObj);
    }
    @Override protected void mouseClicked(int x, int y, int button) throws java.io.IOException {
        int rx = x - guiLeft, ry = y - guiTop;
        if (container.cable && rx >= 8 && rx < 170 && ry >= 42 && ry < 78) {
            int slot = (rx - 8) / 18 + (ry - 42) / 18 * 9; send(Actions.FILTER, slot, isCtrlKeyDown() ? button == 0 ? 1 : -1 : 0, 0); return;
        }
        if (container.controller && rx >= 6 && rx < 170 && ry >= 25 && ry < 135) { selectedProcessor = processorOffset + (ry - 25) / 22; return; }
        if (browser()) {
            search.mouseClicked(x, y, button);
            if (TerminalLayout.SEARCH.contains(rx, ry)) {
                if (button == 1) search.setText(""); rebuild(); return;
            }
            rebuild();
            if (button == 0 && TerminalLayout.SCROLLBAR.contains(rx, ry)) {
                Rect track = TerminalLayout.SCROLLBAR;
                int offset = ry - track.y - viewport.thumbOffset(track.height);
                dragGrab = offset >= 0 && offset < viewport.thumbHeight(track.height) ? offset : viewport.thumbHeight(track.height) / 2;
                viewport.drag(ry - track.y - dragGrab, track.height); return;
            }
            if (TerminalLayout.BROWSER.contains(rx, ry)) {
                if (button != 0 && button != 1) return;
                int index = viewport.indexAt(TerminalLayout.BROWSER, rx, ry);
                if (index >= 0) send(Actions.REQUEST, displayed.get(index), button, (isShiftKeyDown() ? 1 : 0) | (isCtrlKeyDown() ? 2 : 0));
                else send(Actions.INSERT, 0, button, 0); return;
            }
        }
        if (limit != null) limit.mouseClicked(x, y, button);
        super.mouseClicked(x, y, button);
    }
    @Override protected void mouseClickMove(int x, int y, int button, long elapsed) {
        if (browser() && dragGrab >= 0 && button == 0) {
            rebuild(); viewport.drag(y - guiTop - TerminalLayout.SCROLLBAR.y - dragGrab, TerminalLayout.SCROLLBAR.height); return;
        }
        super.mouseClickMove(x, y, button, elapsed);
    }
    @Override protected void mouseReleased(int x, int y, int button) {
        if (dragGrab >= 0 && button == 0) { dragGrab = -1; return; }
        super.mouseReleased(x, y, button);
    }
    @Override protected void keyTyped(char typed, int key) throws java.io.IOException {
        if (search != null && search.isFocused() && key != Keyboard.KEY_ESCAPE) {
            if (key == Keyboard.KEY_TAB || key == Keyboard.KEY_RETURN) search.setFocused(false);
            else search.textboxKeyTyped(typed, key);
            rebuild(); return;
        }
        if (limit != null && limit.isFocused() && (Character.isDigit(typed) || key == Keyboard.KEY_BACK) && limit.textboxKeyTyped(typed, key)) return;
        super.keyTyped(typed, key);
    }
    @Override public void updateScreen() {
        super.updateScreen(); if (search != null) search.updateCursorCounter(); if (limit != null) limit.updateCursorCounter();
    }
    @Override public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput(); int wheel = Mouse.getEventDWheel();
        int rx = Mouse.getEventX() * width / mc.displayWidth - guiLeft;
        int ry = height - Mouse.getEventY() * height / mc.displayHeight - 1 - guiTop;
        if (wheel == 0) return;
        if (browser() && (TerminalLayout.BROWSER.contains(rx, ry) || TerminalLayout.SCROLLBAR.contains(rx, ry))) { rebuild(); viewport.scroll(wheel); }
        else if (container.controller && rx >= 6 && rx < 170 && ry >= 25 && ry < 135) processorOffset = Math.max(0, Math.min(Math.max(0, processorItems.size() - 5), processorOffset + (wheel < 0 ? 1 : -1)));
    }
    @Override public void onGuiClosed() { if (search != null) retainedSearch = search.getText(); Keyboard.enableRepeatEvents(false); super.onGuiClosed(); }
}
