package pureneko.conveyor_belt_plus.util;

/** Shared by both upgrade interactions; no downgrade or same-tier consumption. */
public final class TierUpgrade {
    private TierUpgrade() {}
    public static boolean isUpgrade(int current, int offered) {
        return current >= 1 && current < offered && offered <= 3;
    }
}
