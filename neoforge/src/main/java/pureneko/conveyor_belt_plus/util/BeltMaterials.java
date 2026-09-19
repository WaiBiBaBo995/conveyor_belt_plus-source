package pureneko.conveyor_belt_plus.util;

/** Names match the supplied Blockbench models and 16-frame PNG sprite sheets. */
public final class BeltMaterials {
    public static final int FRAME_COUNT = 16;
    public static final double TILE_LENGTH = 0.75;

    private BeltMaterials() {}

    public static String textureName(int tier) {
        return switch (BeltTiers.normalize(tier)) {
            case BeltTiers.ADVANCED -> "advanced_belt";
            case BeltTiers.ULTIMATE -> "ultimate_belt";
            default -> "conveyorbelt";
        };
    }

    public static int frame(double distance) {
        return Math.floorMod((long) Math.floor(distance / TILE_LENGTH * FRAME_COUNT), FRAME_COUNT);
    }

    // Half-texel insets keep edges from sampling the adjacent frame in the vertical strip.
    public static float u(float value) { return (0.5f + value * 15) / 16; }
    public static float v(float value, int frame) { return (frame * 16 + 0.5f + value * 15) / 256; }
}
