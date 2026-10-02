package pureneko.conveyor_belt_plus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

/** Client ticks without the periodic jumps caused by server world-time synchronization. */
public final class BeltRenderClock {
    private static Level currentWorld;
    private static long ticks;

    private BeltRenderClock() {}

    public static void tick(Minecraft client) {
        bind(client.level);
        if (client.level != null && !client.isPaused()) ticks++;
    }

    private static void bind(Level world) {
        if (world != currentWorld) {
            currentWorld = world;
            ticks = 0;
        }
    }

    public static double now(Level world) {
        bind(world);
        return ticks;
    }

    public static double renderTime(Level world, float tickDelta) {
        return now(world) + tickDelta;
    }
}
