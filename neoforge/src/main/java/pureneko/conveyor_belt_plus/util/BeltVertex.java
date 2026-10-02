package pureneko.conveyor_belt_plus.util;

import net.minecraft.world.phys.Vec3;

/**
 * One vertex of a belt mesh. Platform-neutral on purpose: block entities store the meshes their
 * renderers build, and must not depend on client rendering classes.
 */
public record BeltVertex(float x, float y, float z, float u, float v) {
    public static BeltVertex create(Vec3 pos, float u, float v) {
        return new BeltVertex((float) pos.x, (float) pos.y, (float) pos.z, u, v);
    }
}
