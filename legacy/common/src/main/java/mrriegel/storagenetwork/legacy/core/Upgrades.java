package mrriegel.storagenetwork.legacy.core;

public final class Upgrades {
    public static final int SPEED = 0, OPERATION = 1, STACK = 2, STOCK = 3, SINGLE = 4, SLOW = 5;
    private Upgrades() {}
    public static int interval(int speed, int slow) {
        return speed > 0 ? Math.max(1, 30 / (speed + 1)) : 30 * (slow + 1);
    }
    public static int rate(boolean single, boolean stack) { return single ? 1 : stack ? 64 : 4; }
    public static boolean operation(int count, int limit, boolean smaller) {
        return smaller ? count <= limit : count > limit;
    }
}
