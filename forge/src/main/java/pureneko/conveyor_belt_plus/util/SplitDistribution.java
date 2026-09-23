package pureneko.conveyor_belt_plus.util;

/** Conserves a whole input batch and rotates the remainder between outputs. */
public final class SplitDistribution {
    private SplitDistribution() {}

    public record Result(int[] counts, int nextIndex) {}

    public static Result divide(int total, int outputs, int startIndex) {
        if (total < 0 || outputs < 1 || outputs > 4) throw new IllegalArgumentException();
        int start = Math.floorMod(startIndex, outputs);
        int[] counts = new int[outputs];
        for (int offset = 0; offset < outputs; offset++)
            counts[(start + offset) % outputs] = total / outputs + (offset < total % outputs ? 1 : 0);
        return new Result(counts, (start + total % outputs) % outputs);
    }
}
