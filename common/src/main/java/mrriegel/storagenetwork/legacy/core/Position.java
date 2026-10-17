package mrriegel.storagenetwork.legacy.core;

/** Dimension and coordinates; deliberately independent of Minecraft mappings. */
public final class Position implements Comparable<Position> {
    public final int dimension, x, y, z;
    public static final int[] DX = {0, 0, 0, 0, -1, 1};
    public static final int[] DY = {-1, 1, 0, 0, 0, 0};
    public static final int[] DZ = {0, 0, -1, 1, 0, 0};
    public Position(int dimension, int x, int y, int z) {
        this.dimension = dimension; this.x = x; this.y = y; this.z = z;
    }
    public Position offset(int side) {
        if (side < 0 || side > 5) throw new IllegalArgumentException("Invalid side");
        return new Position(dimension, x + DX[side], y + DY[side], z + DZ[side]);
    }
    public int compareTo(Position other) {
        if (dimension != other.dimension) return dimension < other.dimension ? -1 : 1;
        if (x != other.x) return x < other.x ? -1 : 1;
        if (y != other.y) return y < other.y ? -1 : 1;
        return z == other.z ? 0 : z < other.z ? -1 : 1;
    }
    @Override public boolean equals(Object other) {
        return other instanceof Position && compareTo((Position) other) == 0;
    }
    @Override public int hashCode() { return ((dimension * 31 + x) * 31 + y) * 31 + z; }
}
