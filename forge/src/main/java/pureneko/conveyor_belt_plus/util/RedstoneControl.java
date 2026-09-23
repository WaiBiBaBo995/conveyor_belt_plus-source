package pureneko.conveyor_belt_plus.util;

/** Edge latch shared by extraction and insertion. One pending pulse, no accumulating backlog. */
public final class RedstoneControl {
    public enum Mode {
        ALWAYS("always"), LOW("low"), HIGH("high"), NEVER("never"), PULSE("pulse");
        private final String key;
        Mode(String key) { this.key = key; }
        public String translationKey() { return "redstone.conveyor_belt_plus." + key; }
        public Mode next() { return fromId(ordinal() + 1 == values().length ? 0 : ordinal() + 1); }
        public static Mode fromId(int id) { return id >= 0 && id < values().length ? values()[id] : ALWAYS; }
    }
    private Mode mode = Mode.ALWAYS;
    private boolean powered, pending, initialized;
    public Mode mode() { return mode; }
    public boolean powered() { return powered; }
    public boolean pending() { return pending; }

    /** A mode switch establishes a baseline; switching while powered is not a new pulse. */
    public void setMode(Mode mode, boolean power) {
        this.mode = mode;
        powered = power;
        initialized = true;
        pending = false;
    }

    /** A save/load is not a redstone edge, but an unconsumed pulse remains valid. */
    public void restore(Mode mode, boolean power, boolean pulse) {
        this.mode = mode;
        powered = power;
        pending = mode == Mode.PULSE && pulse;
        initialized = false;
    }

    public boolean sample(boolean power) {
        boolean changed = power != powered;
        if (initialized && mode == Mode.PULSE && !powered && power && !pending) {
            pending = true;
            changed = true;
        }
        powered = power;
        initialized = true;
        return changed;
    }

    public boolean allowsTransfer() {
        return switch (mode) {
            case ALWAYS -> true;
            case LOW -> !powered;
            case HIGH -> powered;
            case NEVER -> false;
            case PULSE -> pending;
        };
    }

    /** Only a successful container transaction consumes the pulse. */
    public boolean transferred() {
        if (mode != Mode.PULSE || !pending) return false;
        pending = false;
        return true;
    }
}
