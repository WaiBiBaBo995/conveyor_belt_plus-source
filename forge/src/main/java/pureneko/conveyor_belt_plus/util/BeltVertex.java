package pureneko.conveyor_belt_plus.util;

import net.minecraft.util.math.Vec3d;

/**
 * One vertex of a belt mesh. Platform-neutral on purpose: block entities store the meshes their
 * renderers build, and must not depend on client rendering classes.
 */
public record BeltVertex(float x, float y, float z, float u, float v) {
    public static BeltVertex create(Vec3d pos, float u, float v) {
        return new BeltVertex((float) pos.x, (float) pos.y, (float) pos.z, u, v);
    }
}
