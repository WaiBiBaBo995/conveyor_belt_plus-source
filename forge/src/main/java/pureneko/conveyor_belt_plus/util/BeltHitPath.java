package pureneko.conveyor_belt_plus.util;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import pureneko.conveyor_belt_plus.blocks.BeltPickup;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity;

/** Cached narrow belt envelopes, shared by client selection and authoritative server ray checks. */
public final class BeltHitPath {
    public record Hit(Vec3 point, float progress) {}
    private final Vec3[] points;
    private final AABB[] segments;
    private final AABB bounds;

    public BeltHitPath(ChuteBlockEntity.BeltData data) {
        int count = Math.max(1, Math.min(4096, (int) Math.ceil(data.totalLength() / .375)));
        points = new Vec3[count + 1];
        segments = new AABB[count];
        for (int i = 0; i <= count; i++) points[i] = SplineUtil.getPositionOnSpline(data, i / (float) count);
        AABB all = null;
        for (int i = 0; i < count; i++) {
            var a = points[i]; var b = points[i + 1];
            var box = new AABB(Math.min(a.x, b.x) - .34, Math.min(a.y, b.y) - .16, Math.min(a.z, b.z) - .34,
                    Math.max(a.x, b.x) + .34, Math.max(a.y, b.y) + .04, Math.max(a.z, b.z) + .34);
            segments[i] = box;
            all = all == null ? box : all.minmax(box);
        }
        bounds = all;
    }

    public Hit raycast(Vec3 eye, Vec3 end) {
        if (BeltPickup.rayHit(bounds, eye, end) == null) return null;
        Hit nearest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < segments.length; i++) {
            var hit = BeltPickup.rayHit(segments[i], eye, end);
            if (hit == null || eye.distanceToSqr(hit) >= distance) continue;
            var delta = points[i + 1].subtract(points[i]);
            double along = delta.lengthSqr() < 1e-12 ? 0
                    : Math.max(0, Math.min(1, hit.subtract(points[i]).dot(delta) / delta.lengthSqr()));
            nearest = new Hit(hit, (float) ((i + along) / segments.length));
            distance = eye.distanceToSqr(hit);
        }
        return nearest;
    }
}
