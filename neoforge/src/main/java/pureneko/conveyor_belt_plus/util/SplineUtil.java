package pureneko.conveyor_belt_plus.util;

import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Tuple;
import net.minecraft.world.phys.Vec3;

public final class SplineUtil {

    private SplineUtil() {}

    private static final int PATH_SAMPLES = 96;
    private static final double MIN_TURN_RADIUS = 1.25;
    private static final double MIN_ENDPOINT_ALIGNMENT = Math.cos(Math.toRadians(60));

    public static Vec3 getPositionOnSpline(ChuteBlockEntity.BeltData data, double t) {
        return data.arcPath().position(t);
    }

    /** Cached arc-length lookup on the original Hermite geometry; equal progress means equal distance. */
    public static final class ArcLengthPath {
        private final Vec3[] points;
        private final double[] distances;

        private ArcLengthPath(Vec3[] points, double[] distances) {
            this.points = points;
            this.distances = distances;
        }

        public static ArcLengthPath create(List<Tuple<Vec3, Vec3>> controls) {
            return create(controls, getSegmentLengths(controls));
        }

        public static ArcLengthPath create(List<Tuple<Vec3, Vec3>> controls, double[] lengths) {
            var samples = new ArrayList<Vec3>();
            var cumulative = new ArrayList<Double>();
            samples.add(controls.getFirst().getA());
            cumulative.add(0d);
            double distance = 0;
            for (int segment = 0; segment < controls.size() - 1; segment++) {
                var from = controls.get(segment);
                var to = controls.get(segment + 1);
                double tangentLength = lengths[segment] * 1.5;
                int count = Math.clamp((int) Math.ceil(lengths[segment] * 32), 32, 4096);
                for (int step = 1; step <= count; step++) {
                    var point = getPointOnHermiteSpline(from.getA(), from.getB().scale(tangentLength),
                            to.getA(), to.getB().scale(tangentLength), step / (double) count);
                    distance += samples.getLast().distanceTo(point);
                    samples.add(point);
                    cumulative.add(distance);
                }
            }
            return new ArcLengthPath(samples.toArray(Vec3[]::new),
                    cumulative.stream().mapToDouble(Double::doubleValue).toArray());
        }

        public double length() { return distances[distances.length - 1]; }

        public Vec3 position(double progress) {
            if (progress <= 0 || length() <= 0) return points[0];
            if (progress >= 1) return points[points.length - 1];
            double target = progress * length();
            int index = java.util.Arrays.binarySearch(distances, target);
            if (index >= 0) return points[index];
            int next = -index - 1;
            int previous = Math.max(0, next - 1);
            double span = distances[next] - distances[previous];
            return points[previous].lerp(points[next], span > 0 ? (target - distances[previous]) / span : 0);
        }
    }

    // approximates segment length by sampling 2 points along the line, and returning the total distance
    public static double getLineLength(Vec3 from, Vec3 fromTangent, Vec3 to, Vec3 toTangent) {

        var approxLength = from.distanceTo(to);
        if (fromTangent.distanceToSqr(toTangent) < 0.1)
            approxLength += 1;

        var midPointA = getPointOnHermiteSpline(from, fromTangent.scale(approxLength), to, toTangent.scale(approxLength), 0.33f);
        var midPointB = getPointOnHermiteSpline(from, fromTangent.scale(approxLength), to, toTangent.scale(approxLength), 0.66f);

        return from.distanceTo(midPointA) + midPointA.distanceTo(midPointB) + midPointB.distanceTo(to);
    }

    /** Validates the same cached path used by transport and rendering. */
    public static boolean isPathUsable(ArcLengthPath arc, Vec3 startDir, Vec3 endDir) {
        var totalLength = arc.length();
        if (!Double.isFinite(totalLength) || totalLength < 0.35) return false;

        var samples = new Vec3[PATH_SAMPLES + 1];
        for (int i = 0; i <= PATH_SAMPLES; i++)
            samples[i] = arc.position(i / (double) PATH_SAMPLES);

        var startTravel = samples[1].subtract(samples[0]);
        var endTravel = samples[PATH_SAMPLES].subtract(samples[PATH_SAMPLES - 1]);
        if (startTravel.lengthSqr() < 1.0E-8 || endTravel.lengthSqr() < 1.0E-8)
            return false;
        if (startTravel.normalize().dot(startDir.normalize()) < MIN_ENDPOINT_ALIGNMENT
                || endTravel.normalize().dot(endDir.normalize()) < MIN_ENDPOINT_ALIGNMENT)
            return false;

        for (int i = 1; i < PATH_SAMPLES; i++) {
            var incoming = samples[i].subtract(samples[i - 1]);
            var outgoing = samples[i + 1].subtract(samples[i]);
            if (incoming.lengthSqr() < 1.0E-8 || outgoing.lengthSqr() < 1.0E-8) return false;

            var dot = Math.max(-1, Math.min(1,
                    incoming.normalize().dot(outgoing.normalize())));
            var turnAngle = Math.acos(dot);
            if (turnAngle > 0.05) {
                var localArcLength = (incoming.length() + outgoing.length()) * 0.5;
                if (localArcLength / turnAngle < MIN_TURN_RADIUS) return false;
            }
        }
        return true;
    }

    private static double[] getSegmentLengths(List<Tuple<Vec3, Vec3>> points) {
        var lengths = new double[points.size() - 1];
        for (int i = 0; i < lengths.length; i++) {
            var from = points.get(i);
            var to = points.get(i + 1);
            lengths[i] = getLineLength(from.getA(), from.getB(), to.getA(), to.getB());
        }
        return lengths;
    }

    // calculates the facing of the middle points automatically. Returns a pair for each point with the desired tangent (to the next point)
    public static List<Tuple<Vec3, Vec3>> getPointPairs(Vec3 start, Vec3 startDir, Vec3 end, Vec3 endDir, List<Tuple<Vec3, Vec3>> middlePoints) {

        var pendingPoints = new ArrayList<Tuple<Vec3, Vec3>>();
        pendingPoints.addAll(middlePoints);
        pendingPoints.add(new Tuple<>(end, endDir));

        var pointsWithTangents = new ArrayList<Tuple<Vec3, Vec3>>();
        pointsWithTangents.add(new Tuple<>(start, startDir));

        var currentFrom = start.add(startDir.scale(0.3f));

        while (!pendingPoints.isEmpty()) {
            var pair = pendingPoints.removeFirst();

            if (pair.getA().equals(end)) {
                pointsWithTangents.add(new Tuple<>(end, endDir));
                break;
            }

            var currentTo = pair.getA();
            var distA = currentFrom.distanceTo(pair.getA().add(pair.getB()));
            var distB = currentFrom.distanceTo(pair.getA().subtract(pair.getB()));
            var currentToDir = distA > distB ? pair.getB() : pair.getB().scale(-1);

            currentFrom = currentTo.add(currentToDir.scale(-0.3f));

            pointsWithTangents.add(new Tuple<>(currentTo, currentToDir));
        }

        return pointsWithTangents;

    }

    /**
     * Calculates a point on a cubic Hermite spline.
     *
     * @param pointA   The starting point of the spline (P0).
     * @param tangentA The tangent vector (derivative) at pointA (M0). The curve will start
     *                 moving in this direction with a "velocity" given by its magnitude.
     * @param pointB   The ending point of the spline (P1).
     * @param tangentB The tangent vector (derivative) at pointB (M1). The curve will arrive
     *                 at pointB with this tangent.
     * @param t        The interpolation parameter, ranging from 0.0 (returns pointA) to 1.0 (returns pointB).
     *                 Values outside this range will be clamped.
     * @return A Vec3d representing the point on the Hermite spline at parameter t.
     */
    public static Vec3 getPointOnHermiteSpline(Vec3 pointA, Vec3 tangentA, Vec3 pointB, Vec3 tangentB, double t) {
        // Clamp t to the range [0, 1]
        if (t < 0.0) t = 0.0;
        if (t > 1.0) t = 1.0;

        double t2 = t * t;
        double t3 = t2 * t;

        // Hermite basis functions
        double h00 = 2.0 * t3 - 3.0 * t2 + 1.0;
        double h10 = t3 - 2.0 * t2 + t;
        double h01 = -2.0 * t3 + 3.0 * t2;
        double h11 = t3 - t2;

        // Calculate the point on the spline
        // H(t) = h00(t)*P0 + h10(t)*M0 + h01(t)*P1 + h11(t)*M1
        Vec3 termP0 = pointA.scale(h00);
        Vec3 termM0 = tangentA.scale(h10);
        Vec3 termP1 = pointB.scale(h01);
        Vec3 termM1 = tangentB.scale(h11);

        return termP0.add(termM0).add(termP1).add(termM1);
    }

}
