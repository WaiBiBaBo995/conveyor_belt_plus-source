package pureneko.conveyor_belt_plus.util;

import java.util.ArrayDeque;
import java.util.Deque;

/** Linear interpolation between timestamped snapshots, with no prediction or per-packet easing. */
public final class ProgressAnimation {
    private record Sample(double tick, double value) {}
    private final Deque<Sample> samples = new ArrayDeque<>();
    private double lastValue;

    public ProgressAnimation(double value) { lastValue = value; }

    public void reset(double value, double tick) {
        samples.clear();
        samples.add(new Sample(tick, value));
        lastValue = value;
    }

    public void update(double value, double tick) {
        if (!Double.isFinite(value) || !Double.isFinite(tick)) return;
        if (!samples.isEmpty() && tick < samples.getLast().tick) return;
        if (!samples.isEmpty()) value = Math.max(value, samples.getLast().value);
        if (!samples.isEmpty() && tick == samples.getLast().tick) samples.removeLast();
        samples.addLast(new Sample(tick, value));
        while (samples.size() > 64) samples.removeFirst();
    }

    public double value(double tick) {
        if (samples.isEmpty()) return lastValue;
        Sample previous = samples.getFirst();
        double result = previous.value;
        for (Sample next : samples) {
            if (tick < next.tick) {
                double span = next.tick - previous.tick;
                double alpha = span > 0 ? net.minecraft.util.math.MathHelper.clamp((tick - previous.tick) / span, 0, 1) : 0;
                result = previous.value + (next.value - previous.value) * alpha;
                break;
            }
            result = next.value;
            previous = next;
        }
        lastValue = Math.max(lastValue, result);
        return lastValue;
    }
}
