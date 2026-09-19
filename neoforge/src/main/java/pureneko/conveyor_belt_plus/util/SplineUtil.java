package pureneko.conveyor_belt_plus.util;

import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity;
import net.minecraft.util.Pair;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public final class SplineUtil {

    private SplineUtil() {}

    private static final int PATH_SAMPLES = 96;
    private static final double MIN_TURN_RADIUS = 1.25;
    private static final double MIN_ENDPOINT_ALIGNMENT = Math.cos(Math.toRadians(60));

    public static Vec3d getPositionOnSpline(ChuteBlockEntity.BeltData data, double t) {
        return data.arcPath().position(t);
    }

    /** Both ends of a narrow progress window, resolved with one arc-length lookup. */
    public static Vec3d[] getWindowOnSpline(ChuteBlockEntity.BeltData data, double from, double to) {
        return data.arcPath().sampleWindow(from, to);
    }

    /** Cached arc-length lookup on the original Hermite geometry; equal progress means equal distance. */
    public static final class ArcLengthPath {
        private final Vec3d[] points;
        private final double[] distances;

        private ArcLengthPath(Vec3d[] points, double[] distances) {
            this.points = points;
            this.distances = distances;
        }

        public static ArcLengthPath create(List<Pair<Vec3d, Vec3d>> controls) {
            return create(controls, getSegmentLengths(controls));
        }

        public static ArcLengthPath create(List<Pair<Vec3d, Vec3d>> controls, double[] lengths) {
            var samples = new ArrayList<Vec3d>();
            var cumulative = new ArrayList<Double>();
            samples.add(controls.getFirst().getLeft());
            cumulative.add(0d);
            double distance = 0;
            for (int segment = 0; segment < controls.size() - 1; segment++) {
                var from = controls.get(segment);
                var to = controls.get(segment + 1);
                double tangentLength = lengths[segment] * 1.5;
                int count = Math.clamp((int) Math.ceil(lengths[segment] * 32), 32, 4096);
                for (int step = 1; step <= count; step++) {
                    var point = getPointOnHermiteSpline(from.getLeft(), from.getRight().multiply(tangentLength),
                            to.getLeft(), to.getRight().multiply(tangentLength), step / (double) count);
                    distance += samples.getLast().distanceTo(point);
                    samples.add(point);
                    cumulative.add(distance);
                }
            }
            return new ArcLengthPath(samples.toArray(Vec3d[]::new),
                    cumulative.stream().mapToDouble(Double::doubleValue).toArray());
        }

        public double length() { return distances[distances.length - 1]; }

        public Vec3d position(double progress) {
            if (progress <= 0 || length() <= 0) return points[0];
            if (progress >= 1) return points[points.length - 1];
            double target = progress * length();
            return sampleAt(java.util.Arrays.binarySearch(distances, target), target);
        }

        /**
         * Samples both ends of a narrow ascending progress window with a single binary search.
         * Rendering uses it to estimate travel direction without resolving the arc length twice.
         */
        public Vec3d[] sampleWindow(double fromProgress, double toProgress) {
            double total = length();
            if (total <= 0) return new Vec3d[] { points[0], points[0] };
            double from = Math.clamp(fromProgress, 0, 1) * total;
            int index = java.util.Arrays.binarySearch(distances, from);
            int cursor = Math.max(0, (index >= 0 ? index : -index - 1) - 1);
            return new Vec3d[] { sampleAt(index, from), sampleAfter(cursor, Math.clamp(toProgress, 0, 1) * total) };
        }

        private Vec3d sampleAt(int index, double target) {
            if (index >= 0) return points[index];
            int next = -index - 1;
            int previous = Math.max(0, next - 1);
            double span = distances[next] - distances[previous];
            return points[previous].lerp(points[next], span > 0 ? (target - distances[previous]) / span : 0);
        }

        /** Walks forward from a known segment; both window samples are close together. */
        private Vec3d sampleAfter(int cursor, double target) {
            while (cursor + 1 < distances.length && distances[cursor + 1] <= target) cursor++;
            if (cursor + 1 >= distances.length || distances[cursor] == target) return points[cursor];
            double span = distances[cursor + 1] - distances[cursor];
            return points[cursor].lerp(points[cursor + 1], span > 0 ? (target - distances[cursor]) / span : 0);
        }
    }

    // approximates segment length by sampling 2 points along the line, and returning the total distance
    public static double getLineLength(Vec3d from, Vec3d fromTangent, Vec3d to, Vec3d toTangent) {

        var approxLength = from.distanceTo(to);
        if (fromTangent.squaredDistanceTo(toTangent) < 0.1)
            approxLength += 1;

        var midPointA = getPointOnHermiteSpline(from, fromTangent.multiply(approxLength), to, toTangent.multiply(approxLength), 0.33f);
        var midPointB = getPointOnHermiteSpline(from, fromTangent.multiply(approxLength), to, toTangent.multiply(approxLength), 0.66f);

        return from.distanceTo(midPointA) + midPointA.distanceTo(midPointB) + midPointB.distanceTo(to);
    }

    /** Validates the same cached path used by transport and rendering. */
    public static boolean isPathUsable(ArcLengthPath arc, Vec3d startDir, Vec3d endDir) {
        var totalLength = arc.length();
        if (!Double.isFinite(totalLength) || totalLength < 0.35) return false;

        var samples = new Vec3d[PATH_SAMPLES + 1];
        for (int i = 0; i <= PATH_SAMPLES; i++)
            samples[i] = arc.position(i / (double) PATH_SAMPLES);

        var startTravel = samples[1].subtract(samples[0]);
        var endTravel = samples[PATH_SAMPLES].subtract(samples[PATH_SAMPLES - 1]);
        if (startTravel.lengthSquared() < 1.0E-8 || endTravel.lengthSquared() < 1.0E-8)
            return false;
        if (startTravel.normalize().dotProduct(startDir.normalize()) < MIN_ENDPOINT_ALIGNMENT
                || endTravel.normalize().dotProduct(endDir.normalize()) < MIN_ENDPOINT_ALIGNMENT)
            return false;

        for (int i = 1; i < PATH_SAMPLES; i++) {
            var incoming = samples[i].subtract(samples[i - 1]);
            var outgoing = samples[i + 1].subtract(samples[i]);
            if (incoming.lengthSquared() < 1.0E-8 || outgoing.lengthSquared() < 1.0E-8) return false;

            var dot = Math.max(-1, Math.min(1,
                    incoming.normalize().dotProduct(outgoing.normalize())));
            var turnAngle = Math.acos(dot);
            if (turnAngle > 0.05) {
                var localArcLength = (incoming.length() + outgoing.length()) * 0.5;
                if (localArcLength / turnAngle < MIN_TURN_RADIUS) return false;
            }
        }
        return true;
    }

    private static double[] getSegmentLengths(List<Pair<Vec3d, Vec3d>> points) {
        var lengths = new double[points.size() - 1];
        for (int i = 0; i < lengths.length; i++) {
            var from = points.get(i);
            var to = points.get(i + 1);
            lengths[i] = getLineLength(from.getLeft(), from.getRight(), to.getLeft(), to.getRight());
        }
        return lengths;
    }

    // calculates the facing of the middle points automatically. Returns a pair for each point with the desired tangent (to the next point)
    public static List<Pair<Vec3d, Vec3d>> getPointPairs(Vec3d start, Vec3d startDir, Vec3d end, Vec3d endDir, List<Pair<Vec3d, Vec3d>> middlePoints) {

        var pendingPoints = new ArrayList<Pair<Vec3d, Vec3d>>();
        pendingPoints.addAll(middlePoints);
        pendingPoints.add(new Pair<>(end, endDir));

        var pointsWithTangents = new ArrayList<Pair<Vec3d, Vec3d>>();
        pointsWithTangents.add(new Pair<>(start, startDir));

        var currentFrom = start.add(startDir.multiply(0.3f));

        while (!pendingPoints.isEmpty()) {
            var pair = pendingPoints.removeFirst();

            if (pair.getLeft().equals(end)) {
                pointsWithTangents.add(new Pair<>(end, endDir));
                break;
            }

            var currentTo = pair.getLeft();
            var distA = currentFrom.distanceTo(pair.getLeft().add(pair.getRight()));
            var distB = currentFrom.distanceTo(pair.getLeft().subtract(pair.getRight()));
            var currentToDir = distA > distB ? pair.getRight() : pair.getRight().multiply(-1);

            currentFrom = currentTo.add(currentToDir.multiply(-0.3f));

            pointsWithTangents.add(new Pair<>(currentTo, currentToDir));
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
    public static Vec3d getPointOnHermiteSpline(Vec3d pointA, Vec3d tangentA, Vec3d pointB, Vec3d tangentB, double t) {
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
        Vec3d termP0 = pointA.multiply(h00);
        Vec3d termM0 = tangentA.multiply(h10);
        Vec3d termP1 = pointB.multiply(h01);
        Vec3d termM1 = tangentB.multiply(h11);

        return termP0.add(termM0).add(termP1).add(termM1);
    }

}
