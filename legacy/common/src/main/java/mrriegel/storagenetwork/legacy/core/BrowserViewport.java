package mrriegel.storagenetwork.legacy.core;

/** Client-only row offset; catalog revisions and real container slots are independent. */
public final class BrowserViewport {
    public final int columns, rows;
    private int entries, row;
    public BrowserViewport(int columns, int rows) {
        if (columns <= 0 || rows <= 0) throw new IllegalArgumentException("Empty viewport");
        this.columns = columns; this.rows = rows;
    }
    public int row() { return row; }
    public int first() { return row * columns; }
    public int capacity() { return columns * rows; }
    public int maxRow() { return Math.max(0, (int) (((long) entries + columns - 1) / columns) - rows); }
    public void update(int count, boolean reset) { entries = Math.max(0, count); setRow(reset ? 0 : row); }
    public void setRow(int value) { row = Math.max(0, Math.min(maxRow(), value)); }
    public void scroll(int wheel) { if (wheel != 0) setRow(row + (wheel < 0 ? 1 : -1)); }
    public int indexAt(TerminalLayout.Rect bounds, int x, int y) {
        if (!bounds.contains(x, y)) return -1;
        int index = first() + (x - bounds.x) / TerminalLayout.CELL + (y - bounds.y) / TerminalLayout.CELL * columns;
        return index < entries ? index : -1;
    }
    public int thumbHeight(int track) {
        int totalRows = Math.max(rows, (int) (((long) entries + columns - 1) / columns));
        return Math.min(track, Math.max(12, (int) ((long) track * rows / totalRows)));
    }
    public int thumbOffset(int track) { return maxRow() == 0 ? 0 : (int) ((long) row * (track - thumbHeight(track)) / maxRow()); }
    public void drag(int offset, int track) {
        int travel = track - thumbHeight(track);
        setRow(travel <= 0 ? 0 : (int) Math.round((double) Math.max(0, Math.min(travel, offset)) * maxRow() / travel));
    }
}
