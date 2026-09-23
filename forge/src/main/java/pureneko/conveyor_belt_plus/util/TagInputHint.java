package pureneko.conveyor_belt_plus.util;

/** A placeholder, not an autocomplete suffix. Nonempty input must never display it. */
public final class TagInputHint {
    private TagInputHint() {}
    public static String forText(String text) { return text.isEmpty() ? "#namespace:tag" : null; }
}
