package pureneko.conveyor_belt_plus.util;

/** A per-route travel odometer and a two-tick buffered server presentation clock. */
public final class BeltVisualState {
    public static final double BUFFER_TICKS = 2;
    private double distance;
    private double step;
    private long movementTick = Long.MIN_VALUE;
    private ProgressAnimation animation = new ProgressAnimation(0);
    private boolean initialized;
    private double clockOffset;
    private double lastPresentation = Double.NEGATIVE_INFINITY;

    public void advance(double distanceMoved, long serverTick) {
        step = Math.max(0, distanceMoved);
        distance += step;
        movementTick = serverTick;
    }

    public double distance() { return distance; }
    public double step(long serverTick) { return movementTick == serverTick ? step : 0; }

    public void restore(double savedDistance) {
        distance = Math.max(0, savedDistance);
        step = 0;
        movementTick = Long.MIN_VALUE;
        initialized = false;
        lastPresentation = Double.NEGATIVE_INFINITY;
        animation = new ProgressAnimation(distance);
    }

    public void receive(double value, double lastStep, long serverTick, double clientTick) {
        if (!initialized) {
            clockOffset = serverTick - clientTick;
            initialized = true;
            animation.reset(Math.max(0, value - lastStep), serverTick - 1);
        } else if (Math.abs(serverTick - (clientTick + clockOffset)) > 5) {
            // Recover after a long server stall without ever running the presentation backwards.
            clockOffset = serverTick - clientTick;
        }
        // A stationary period is a plateau, not one slow interpolation across the entire idle gap.
        animation.update(Math.max(0, value - lastStep), serverTick - 1);
        animation.update(value, serverTick);
        distance = value;
    }

    public double presentationTick(double clientTick) {
        if (!initialized) return clientTick;
        lastPresentation = Math.max(lastPresentation, clientTick + clockOffset - BUFFER_TICKS);
        return lastPresentation;
    }

    public double renderedDistance(double presentationTick) { return animation.value(presentationTick); }
}
