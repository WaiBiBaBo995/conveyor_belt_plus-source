package pureneko.conveyor_belt_plus.util;

import net.minecraft.world.phys.Vec3;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity.BeltData;

/** Shared vertical frame, obtained by rotating the horizontal belt with its interface model. */
public final class BeltGeometry {
    private BeltGeometry() {}
    // Horizontal belt surface y=.455 rotates to model z=.455-.125=.330.
    public static final double LIFT_SURFACE_OFFSET_Z = -.17;
    // The .60-wide carrier's rear edge touches that surface.
    public static final double LIFT_CARGO_OFFSET_Z = LIFT_SURFACE_OFFSET_Z + .30;
    public static Vec3 surfacePoint(BeltData data, Vec3 point) {
        return data.isVertical() ? point.add(0, 0, LIFT_SURFACE_OFFSET_Z) : point;
    }
    public static Vec3 cargoPoint(BeltData data, Vec3 point) {
        return data.isVertical() ? point.add(0, 0, LIFT_CARGO_OFFSET_Z) : point;
    }
}
