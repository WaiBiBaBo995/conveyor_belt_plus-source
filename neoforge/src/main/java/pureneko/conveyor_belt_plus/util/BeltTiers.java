package pureneko.conveyor_belt_plus.util;

/** Speed tiers shared by belt items and the block entities that simulate them. */
public final class BeltTiers {

    public static final int STANDARD = 1;
    public static final int ADVANCED = 2;
    public static final int ULTIMATE = 3;

    private BeltTiers() {
    }

    public static int normalize(int tier) {
        return Math.clamp(tier, STANDARD, ULTIMATE);
    }

    public static float speed(int tier) {
        return pureneko.conveyor_belt_plus.config.ConveyorConfig.beltSpeed(tier);
    }
}
