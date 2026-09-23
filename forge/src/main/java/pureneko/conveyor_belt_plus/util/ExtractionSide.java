package pureneko.conveyor_belt_plus.util;

import net.minecraft.util.math.Direction;

/** Stable saved bit positions. Front is the attached container face carrying this chute. */
public enum ExtractionSide {
    FRONT(1, 1), BACK(0, 2), LEFT(0, 1), RIGHT(2, 1), TOP(1, 0), BOTTOM(1, 2);

    public static final int DEFAULT_MASK = 1;
    public static final int ALL_MASK = 63;
    private static final ExtractionSide[] SIDES = values();
    private final int column, row;

    ExtractionSide(int column, int row) { this.column = column; this.row = row; }
    public int column() { return column; }
    public int row() { return row; }
    public int bit() { return 1 << ordinal(); }
    public boolean selected(int mask) { return (mask & bit()) != 0; }
    public String translationKey() { return "screen.conveyor_belt_plus.extraction." + name().toLowerCase(java.util.Locale.ROOT); }
    public Direction direction(Direction front) {
        return switch (this) {
            case FRONT -> front;
            case BACK -> front.getOpposite();
            // Left/right as seen by a player outside the front, looking into the container.
            case LEFT -> front.rotateYClockwise();
            case RIGHT -> front.rotateYCounterclockwise();
            case TOP -> Direction.UP;
            case BOTTOM -> Direction.DOWN;
        };
    }
    public static ExtractionSide byId(int id) { return id >= 0 && id < SIDES.length ? SIDES[id] : null; }
    public static boolean validMask(int mask) { return mask >= 0 && mask <= ALL_MASK; }
    public static int savedMask(int mask) { return validMask(mask) ? mask : DEFAULT_MASK; }
}
