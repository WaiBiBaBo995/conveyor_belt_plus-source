package pureneko.conveyor_belt_plus.util;

import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.function.Predicate;

/** Shared server simulation; the deque's tail is the leading packet. */
public final class BeltTransport {
    public static final float SPACING = 0.8f;
    public interface Packet {
        float progress();
        void progress(float progress);
        default int count() { return 0; }
    }
    private BeltTransport() {}

    public static <T extends Packet> boolean canLoad(Deque<T> items, double length) {
        return length > 0 && items.size() < 64
                && (items.isEmpty() || items.getFirst().progress() >= SPACING / length);
    }

    public static <T extends Packet> boolean canInsertAt(Deque<T> items, double length, float progress) {
        if (!Float.isFinite(progress) || progress < 0 || progress > 1 || !Double.isFinite(length)
                || length <= 0 || items.size() >= 64) return false;
        for (var item : items) if (Math.abs(item.progress() - progress) * length < SPACING) return false;
        return true;
    }

    /** Preserve ascending progress so the tail remains the leading packet. */
    public static <T extends Packet> boolean insertAt(Deque<T> items, double length, T packet) {
        if (!canInsertAt(items, length, packet.progress())) return false;
        var ordered = new java.util.ArrayList<T>(items.size() + 1);
        boolean inserted = false;
        for (var item : items) {
            if (!inserted && packet.progress() < item.progress()) { ordered.add(packet); inserted = true; }
            ordered.add(item);
        }
        if (!inserted) ordered.add(packet);
        items.clear();
        items.addAll(ordered);
        return true;
    }

    public record Step(boolean changed, double distanceMoved) {}

    public static <T extends Packet> boolean tick(Deque<T> items, double length,
                                                  float speed, Predicate<T> accept) {
        return tickWithMotion(items, length, speed, accept).changed();
    }

    public static <T extends Packet> Step tickWithMotion(Deque<T> items, double length,
                                                        float speed, Predicate<T> accept) {
        if (length <= 0 || speed <= 0) return new Step(false, 0);
        float delta = (float) (speed / length / 20);
        float limit = 1;
        boolean changed = false;
        double distanceMoved = 0;
        var iterator = items.descendingIterator();
        while (iterator.hasNext()) {
            T item = iterator.next();
            float old = item.progress();
            // The max preserves legacy packets already overlapping when an old world is loaded.
            float next = Math.max(old, Math.min(limit, Math.min(1, old + delta)));
            item.progress(next);
            distanceMoved = Math.max(distanceMoved, (next - old) * length);
            changed |= next != old;
            if (next >= 1 && limit == 1) {
                int previousCount = item.count();
                if (accept.test(item)) {
                    iterator.remove(); // Remove exactly the packet accepted, including at high speeds.
                    changed = true;
                    continue;
                }
                changed |= item.count() != previousCount;
            }
            limit = Math.max(0, next - (float) (SPACING / length));
        }
        return new Step(changed, distanceMoved);
    }
}
