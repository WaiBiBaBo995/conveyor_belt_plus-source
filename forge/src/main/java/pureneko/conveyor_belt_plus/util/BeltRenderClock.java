package pureneko.conveyor_belt_plus.util;

import net.minecraft.world.World;

/**
 * Client ticks without the periodic jumps caused by server world-time synchronization.
 * Only the client advances it, so on a dedicated server it stays at zero; the class itself
 * holds no client class so shared code can query it safely.
 */
public final class BeltRenderClock {
    private static World currentWorld;
    private static long ticks;

    private BeltRenderClock() {}

    public static void advance(World world, boolean paused) {
        bind(world);
        if (world != null && !paused) ticks++;
    }

    private static void bind(World world) {
        if (world != currentWorld) {
            currentWorld = world;
            ticks = 0;
        }
    }

    public static double now(World world) {
        bind(world);
        return ticks;
    }

    public static double renderTime(World world, float tickDelta) {
        return now(world) + tickDelta;
    }
}
