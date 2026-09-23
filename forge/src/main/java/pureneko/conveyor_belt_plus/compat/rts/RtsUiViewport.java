package pureneko.conveyor_belt_plus.compat.rts;

/** Pure coordinate conversion matching RTS 1.1.7's temporary input/render frame. */
public record RtsUiViewport(int width, int height, double mouseX, double mouseY) {
    public static RtsUiViewport from(int windowWidth, int windowHeight, int guiWidth, int guiHeight,
                                     double configuredScale, double rawX, double rawY) {
        if (windowWidth <= 0 || windowHeight <= 0 || guiWidth <= 0 || guiHeight <= 0
                || !Double.isFinite(configuredScale) || configuredScale <= 0
                || !Double.isFinite(rawX) || !Double.isFinite(rawY)) return null;
        // Configured scale is pixels/UI unit, NOT a multiplier of Minecraft's GUI scale.
        double renderScale = configuredScale * guiWidth / windowWidth;
        return new RtsUiViewport(Math.max(1, (int) Math.round(guiWidth / renderScale)),
                Math.max(1, (int) Math.round(guiHeight / renderScale)),
                rawX * guiWidth / windowWidth / renderScale,
                rawY * guiHeight / windowHeight / renderScale);
    }
}
