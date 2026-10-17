package mrriegel.storagenetwork.legacy.core;

/** Pixel geometry shared by native screens and their permanent container slots. */
public final class TerminalLayout {
    public static final int CELL = 18, HEIGHT = 224;
    public static final Rect NETWORK_PANEL = new Rect(6, 6, 182, 114);
    public static final Rect BROWSER = new Rect(8, 44, 9 * CELL, 4 * CELL);
    public static final Rect SCROLLBAR = new Rect(174, BROWSER.y, 10, BROWSER.height);
    public static final Rect SEARCH = new Rect(8, 22, 94, 14);
    public static final Rect SORT = new Rect(106, 22, 40, 14);
    public static final Rect DIRECTION = new Rect(148, 22, 16, 14);
    public static final Rect REFRESH = new Rect(166, 22, 18, 14);
    public static final Rect CRAFT_PANEL = new Rect(190, 6, 106, 114);
    public static final Rect CRAFT_GRID = new Rect(198, 28, 3 * CELL, 3 * CELL);
    public static final Rect RESULT = new Rect(274, 46, CELL, CELL);
    public static final Rect CLEAR = new Rect(198, 96, 90, 16);
    public static final int PLAYER_Y = 142, HOTBAR_Y = 200;
    public final int width, playerX;
    public final Rect playerPanel;

    public TerminalLayout(boolean crafting) {
        width = crafting ? 302 : 194;
        playerX = (width - 9 * CELL) / 2;
        playerPanel = new Rect(6, 124, width - 12, 94);
    }

    /** Ordinary links have no screen; transfer/processing variants still do. */
    public static boolean hasBlockGui(int kind) { return kind == 1 || kind == 2 || kind >= 5 && kind <= 7 || kind == 9; }

    public static final class Rect {
        public final int x, y, width, height;
        public Rect(int x, int y, int width, int height) { this.x = x; this.y = y; this.width = width; this.height = height; }
        public boolean contains(int px, int py) { return px >= x && px < x + width && py >= y && py < y + height; }
    }
}
