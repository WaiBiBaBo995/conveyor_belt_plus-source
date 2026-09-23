package pureneko.conveyor_belt_plus;

import pureneko.conveyor_belt_plus.config.ConveyorConfig;
import pureneko.conveyor_belt_plus.util.BeltTiers;
import pureneko.conveyor_belt_plus.util.BeltTransport;
import pureneko.conveyor_belt_plus.util.ProgressAnimation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Properties;

/** No client, running world or additional test framework required. */
public final class ConveyorRegressionTests {
    private static int checks;

    public static void main(String[] args) {
        redstone();
        check(pureneko.conveyor_belt_plus.util.TagInputHint.forText("").equals("#namespace:tag"), "empty tag field shows placeholder");
        for (String text : new String[]{"m", "minecraft:", "minecraft:logs", " "})
            check(pureneko.conveyor_belt_plus.util.TagInputHint.forText(text) == null, "typing removes placeholder rather than appending it");
        animation();
        transport();
        distribution();
        rtsViewport();
        extractionSides();
        System.out.println("Conveyor Belt Plus: " + checks + " regression checks passed.");
    }

    private static void rtsViewport() {
        for (int minecraftScale : new int[]{1, 2, 3, 4}) for (double rtsScale : new double[]{1, 1.5, 2, 3, 4}) {
            var frame = pureneko.conveyor_belt_plus.compat.rts.RtsUiViewport.from(1920, 1080,
                    1920 / minecraftScale, 1080 / minecraftScale, rtsScale, 960, 900);
            check(frame.width() == Math.round(1920 / rtsScale)
                    && frame.height() == Math.round(1080 / rtsScale), "RTS virtual panel bounds ignore vanilla GUI scale");
            near(frame.mouseX(), 960 / rtsScale, "RTS horizontal cursor");
            near(frame.mouseY(), 900 / rtsScale, "RTS vertical cursor over bottom panel");
        }
        var odd = pureneko.conveyor_belt_plus.compat.rts.RtsUiViewport.from(1919, 1079, 640, 360, 2, 1918, 1078);
        double renderScale = 2 / (1919.0 / 640);
        check(odd.width() == Math.round(640 / renderScale) && odd.height() == Math.round(360 / renderScale),
                "non-divisible window uses RTS rounded viewport");
        near(odd.mouseY(), 1078 * 360.0 / 1079 / renderScale, "odd window follows vanilla input rounding");
        for (double scale : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            check(pureneko.conveyor_belt_plus.compat.rts.RtsUiViewport.from(1920, 1080, 640, 360, scale, 20, 30) == null,
                    "invalid scale cannot intercept world input");
        check(pureneko.conveyor_belt_plus.compat.rts.RtsUiViewport.from(0, 0, 0, 0, 2, 0, 0) == null,
                "minimized window cannot intercept world input");
    }

    private static void extractionSides() {
        var faces = pureneko.conveyor_belt_plus.util.ExtractionSide.values();
        for (var front : new net.minecraft.util.math.Direction[]{net.minecraft.util.math.Direction.NORTH,
                net.minecraft.util.math.Direction.SOUTH, net.minecraft.util.math.Direction.EAST, net.minecraft.util.math.Direction.WEST}) {
            var directions = java.util.EnumSet.noneOf(net.minecraft.util.math.Direction.class);
            var cells = new java.util.HashSet<String>();
            for (var face : faces) {
                check(directions.add(face.direction(front)), "each selector maps to a unique face");
                check(cells.add(face.column() + ":" + face.row()), "face buttons never overlap");
            }
            check(pureneko.conveyor_belt_plus.util.ExtractionSide.FRONT.direction(front) == front, "default means attached container face");
            check(pureneko.conveyor_belt_plus.util.ExtractionSide.LEFT.direction(front)
                    == pureneko.conveyor_belt_plus.util.ExtractionSide.RIGHT.direction(front).getOpposite(), "left/right remain relative on all orientations");
        }
        for (int mask = 0; mask <= 63; mask++) {
            int count = 0;
            for (var face : faces) {
                if (face.selected(mask)) count++;
                int toggled = mask ^ face.bit();
                check(face.selected(toggled) != face.selected(mask), "one click toggles precisely one face");
                check((toggled ^ face.bit()) == mask, "second click restores multi-selection");
                check(pureneko.conveyor_belt_plus.util.ExtractionSide.validMask(toggled), "click result stays bounded to six bits");
            }
            check(count == Integer.bitCount(mask), "displayed selection count");
            check(pureneko.conveyor_belt_plus.util.ExtractionSide.savedMask(mask) == mask, "all valid saved masks including zero preserved");
        }
        for (int bad : new int[]{-1, 64, 256, Integer.MIN_VALUE, Integer.MAX_VALUE})
            check(pureneko.conveyor_belt_plus.util.ExtractionSide.savedMask(bad) == 1, "bad mask safely uses original behavior");
    }

    private static void configuration() {
        ConfigTestSupport.apply(new Properties(), ignored -> {});
        check(ConveyorConfig.chuteFilters(1) == 5, "ordinary chute has five rules");
        check(ConveyorConfig.chuteFilters(2) == 10, "advanced has ten rules");
        check(ConveyorConfig.chuteFilters(3) == 15, "ultimate has fifteen rules");
        check(ConveyorConfig.chuteStacks(2) == 4 && ConveyorConfig.chuteStacks(3) == 8, "batch defaults");
        check(BeltTiers.speed(3) == 4 && BeltTiers.normalize(2) == 2, "existing tiers remain compatible");
        var properties = new Properties();
        properties.setProperty("belt.ultimate.speed", "12.5");
        properties.setProperty("chute.ultimate.stacks", "32");
        ConfigTestSupport.apply(properties, ignored -> {});
        check(BeltTiers.speed(3) == 12.5f && ConveyorConfig.chuteStacks(3) == 32, "custom configuration");
        properties.setProperty("belt.ultimate.speed", "NaN");
        properties.setProperty("belt.advanced.speed", "-1");
        properties.setProperty("chute.ultimate.stacks", "2147483647");
        properties.setProperty("chute.advanced.stacks", "0");
        var warnings = new ArrayList<String>();
        ConfigTestSupport.apply(properties, warnings::add);
        check(warnings.size() == 4, "invalid settings are diagnosed");
        check(BeltTiers.speed(3) == 4 && BeltTiers.speed(2) == .1f && ConveyorConfig.chuteStacks(2) == 1
                && ConveyorConfig.chuteStacks(3) == 64, "native ranges clamp outliers; non-finite speed remains safe");
        var limits = new Properties();
        limits.setProperty("chute.standard.filters", "1");
        limits.setProperty("chute.advanced.filters", "54");
        limits.setProperty("chute.ultimate.filters", "32");
        ConfigTestSupport.apply(limits, ignored -> {});
        check(ConveyorConfig.chuteFilters(1) == 1 && ConveyorConfig.chuteFilters(2) == 54 && ConveyorConfig.chuteFilters(3) == 32,
                "filter capacities use server configuration, including multi-page limits");
        limits.setProperty("chute.standard.filters", "0");
        limits.setProperty("chute.advanced.filters", "55");
        limits.setProperty("chute.ultimate.filters", "NaN");
        warnings.clear();
        ConfigTestSupport.apply(limits, warnings::add);
        check(warnings.size() == 3 && ConveyorConfig.chuteFilters(1) == 1 && ConveyorConfig.chuteFilters(2) == 54
                && ConveyorConfig.chuteFilters(3) == 15, "native filter ranges clamp numbers and default invalid types");
        ConfigTestSupport.apply(new Properties(), ignored -> {});
    }

    public static void nativeConfigurationTests() {
        try {
            configuration();
            splitterConfiguration();
            check(ConveyorConfig.extractionSidesEnabled(), "side configuration defaults enabled");
            ConveyorConfig.CHUTE_EXTRACTION_SIDES.set(false);
            check(!ConveyorConfig.extractionSidesEnabled(), "native toggle is read immediately without a second config cache");
            ConveyorConfig.CHUTE_EXTRACTION_SIDES.set(true);
            var defaults = com.electronwill.nightconfig.core.CommentedConfig.inMemory();
            ConveyorConfig.SPEC.correct(defaults);
            check(ConveyorConfig.SPEC.isCorrect(defaults), "native schema corrects missing values and comments");
            defaults.set("belt.ultimate.speed", 12.5);
            defaults.set("chute.advanced.stacks", 7);
            var writer = new java.io.StringWriter();
            new com.electronwill.nightconfig.toml.TomlWriter().write(defaults, writer);
            var restored = new com.electronwill.nightconfig.toml.TomlParser()
                    .parse(new java.io.StringReader(writer.toString()));
            check(ConveyorConfig.SPEC.isCorrect(restored), "native TOML roundtrip preserves schema");
            ConfigTestSupport.apply(restored);
            check(ConveyorConfig.beltSpeed(3) == 12.5f && ConveyorConfig.chuteStacks(2) == 7,
                    "native TOML preserves configured values");
            check(restored.getComment("splitter.buffer_items").contains("分流器")
                    && restored.getComment("splitter.buffer_items").contains("Splitter"), "native bilingual comments");
            ConveyorConfig.SPEEDS[2].set(6.0);
            check(ConveyorConfig.beltSpeed(3) == 6, "native value change visible without custom reload/cache");
            for (int old = 0; old <= 4; old++) for (int offered = 0; offered <= 4; offered++)
                check(pureneko.conveyor_belt_plus.util.TierUpgrade.isUpgrade(old, offered)
                        == (old >= 1 && offered <= 3 && offered > old), "tier upgrade boundary");
        } finally {
            ConfigTestSupport.apply(new Properties(), ignored -> {});
        }
        System.out.println("Conveyor Belt Plus: native config schema, bounds, TOML roundtrip and reload checks passed.");
    }

    private static void splitterConfiguration() {
        var properties = new Properties();
        for (int value : new int[]{1, 512, 1024, ConveyorConfig.MAX_SPLITTER_BUFFER}) {
            properties.setProperty("splitter.buffer_items", "" + value);
            ConfigTestSupport.apply(properties, ignored -> {});
            check(ConveyorConfig.splitterBufferItems() == value, "custom buffer capacity");
        }
        for (String value : new String[]{"0", "-1", "1048577", "NaN", "2147483648"}) {
            properties.setProperty("splitter.buffer_items", value);
            var warnings = new ArrayList<String>();
            ConfigTestSupport.apply(properties, warnings::add);
            int expected = value.equals("0") || value.equals("-1") ? 1 : value.equals("1048577") ? ConveyorConfig.MAX_SPLITTER_BUFFER : 512;
            check(ConveyorConfig.splitterBufferItems() == expected && warnings.size() == 1, "native buffer bounds correction");
        }
        ConfigTestSupport.apply(new Properties(), ignored -> {});
    }

    private static void redstone() {
        var control = new pureneko.conveyor_belt_plus.util.RedstoneControl();
        var modes = pureneko.conveyor_belt_plus.util.RedstoneControl.Mode.values();
        for (var mode : modes) for (boolean power : new boolean[]{false, true}) {
            control.setMode(mode, power);
            check(control.allowsTransfer() == (mode == modes[0] || mode == modes[1] && !power || mode == modes[2] && power),
                    "five redstone modes, switching while powered is not a pulse");
        }
        control.setMode(modes[4], false);
        control.sample(true);
        check(control.allowsTransfer(), "rising edge grants one batch");
        for (int tick = 0; tick < 50; tick++) control.sample(true);
        check(control.transferred() && !control.allowsTransfer(), "steady high never retriggers");
        control.sample(false); control.sample(true);
        control.sample(false); control.sample(true);
        check(control.transferred() && !control.transferred(), "blocked pulse latch does not accumulate a backlog");
        control.restore(modes[4], false, false); control.sample(true);
        check(!control.allowsTransfer(), "loading a powered chunk does not invent a pulse");
        control.restore(modes[4], true, true); control.sample(true);
        check(control.transferred() && !control.transferred(), "saved pending pulse survives load once");
        control.setMode(modes[4], false); control.sample(true);
        control.setMode(modes[3], true); control.setMode(modes[4], true);
        check(!control.allowsTransfer(), "switching modes clears pending pulse");
        check(pureneko.conveyor_belt_plus.util.RedstoneControl.Mode.fromId(-1) == modes[0]
                && pureneko.conveyor_belt_plus.util.RedstoneControl.Mode.fromId(99) == modes[0], "bad saved modes fall back safely");
    }

    private static void animation() {
        var animation = new ProgressAnimation(0);
        animation.reset(0, 10);
        animation.update(0.1, 11);
        near(animation.value(10), 0, "new output begins at zero");
        near(animation.value(10.5), 0.05, "linear interpolation between server ticks");
        animation.update(0.2, 12);
        near(animation.value(11.5), 0.15, "packets do not restart an easing curve");
        near(animation.value(12), 0.2, "reaches latest snapshot");
        for (int tick = 13; tick < 100; tick++) {
            animation.update(0.2, tick);
            near(animation.value(tick + 0.9), 0.2, "blocked belt must not oscillate");
        }
        animation.update(0.1, 20);
        near(animation.value(101), 0.2, "out-of-order snapshot cannot rewind");
        animation.update(0.2, 102);
        animation.update(0.3, 103);
        near(animation.value(102.5), 0.25, "resume from a stationary plateau");
        near(animation.value(200), 0.3, "never extrapolate beyond server position");

        var route = new pureneko.conveyor_belt_plus.util.BeltVisualState();
        route.receive(0, 0, 0, 0);
        var smooth = new ProgressAnimation(0);
        smooth.reset(0, 0);
        int nextPacket = 1;
        // TCP delivery jitter: alternating 0 / 1-tick latency, with coalesced packets in order.
        for (int frame = 0; frame < 900; frame++) {
            double clientTime = frame / 6.0;
            while (nextPacket + (nextPacket % 2) <= clientTime) {
                double value = nextPacket * 0.003;
                smooth.update(value - 0.003, nextPacket - 1);
                smooth.update(value, nextPacket);
                route.receive(nextPacket * 0.05, 0.05, nextPacket, clientTime);
                nextPacket++;
            }
            double presentation = route.presentationTick(clientTime);
            if (presentation >= 1)
                near(smooth.value(presentation), presentation * 0.003, "120 FPS motion survives packet jitter");
        }

        var moving = new pureneko.conveyor_belt_plus.util.BeltVisualState();
        var stopped = new pureneko.conveyor_belt_plus.util.BeltVisualState();
        moving.receive(0, 0, 0, 0);
        stopped.receive(0, 0, 0, 0);
        for (int tick = 1; tick <= 20; tick++) moving.receive(tick * 0.05, 0.05, tick, tick);
        double travelled = moving.renderedDistance(20);
        for (int tick = 21; tick < 100; tick++) {
            near(moving.renderedDistance(tick), travelled, "stopped texture retains its exact frame");
            near(stopped.renderedDistance(tick), 0, "neighboring idle belt never animates");
        }
        moving.receive(1.05, 0.05, 100, 100);
        near(moving.renderedDistance(99.5), 1.025, "texture resumes without interpolating across idle time");
        check(!pureneko.conveyor_belt_plus.util.BeltMaterials.textureName(1)
                .equals(pureneko.conveyor_belt_plus.util.BeltMaterials.textureName(2)), "tiers have different materials");
        check(pureneko.conveyor_belt_plus.util.BeltMaterials.textureName(3).equals("ultimate_belt"), "ultimate model material");
        check(pureneko.conveyor_belt_plus.util.BeltMaterials.frame(0) == 0
                && pureneko.conveyor_belt_plus.util.BeltMaterials.frame(0.75) == 0, "16-frame loop wraps");
        for (int frame = 0; frame < 16; frame++) {
            check(pureneko.conveyor_belt_plus.util.BeltMaterials.v(0, frame) > frame / 16.0,
                    "frame start inset");
            check(pureneko.conveyor_belt_plus.util.BeltMaterials.v(1, frame) < (frame + 1) / 16.0,
                    "frame end inset");
        }
    }

    private static void transport() {
        var items = new ArrayDeque<Packet>();
        items.addFirst(new Packet(0.98f, 64));
        items.addFirst(new Packet(0.96f, 64));
        var delivered = new int[]{0};
        BeltTransport.tick(items, 1, 64, packet -> { delivered[0] += packet.count; return true; });
        check(delivered[0] == 128 && items.isEmpty(), "high speed removes each accepted packet exactly once");

        items.addFirst(new Packet(0, 512));
        check(!BeltTransport.canLoad(items, 4), "do not spawn overlapping packets");
        for (int tick = 0; tick < 100; tick++) BeltTransport.tick(items, 4, 1, packet -> false);
        near(items.getLast().progress, 1, "stopped packet reaches exact endpoint");
        items.addFirst(new Packet(0, 256));
        for (int tick = 0; tick < 100; tick++) BeltTransport.tick(items, 4, 1, packet -> false);
        near(items.getFirst().progress, 0.8f, "queue uses physical spacing");
        float stopped = items.getFirst().progress;
        check(!BeltTransport.tick(items, 4, 1, packet -> false), "stalled queues emit no fake movement");
        near(items.getFirst().progress, stopped, "stopped queue remains stable");
        delivered[0] = 0;
        for (int tick = 0; tick < 100; tick++)
            BeltTransport.tick(items, 4, 1, packet -> { delivered[0] += packet.count; return true; });
        check(items.isEmpty() && delivered[0] == 768, "multi-stack batches preserve item counts");
        check(!BeltTransport.canLoad(items, 0), "zero-length routes cannot load");
        check(BeltTransport.tickWithMotion(items, 10, 1, packet -> false).distanceMoved() == 0,
                "empty belt has no texture travel");
        items.addFirst(new Packet(0, 64));
        near(BeltTransport.tickWithMotion(items, 10, 4, packet -> false).distanceMoved(), 0.2,
                "texture follows actual belt speed");
        items.clear();
        items.addFirst(new Packet(1, 64));
        check(BeltTransport.tickWithMotion(items, 10, 4, packet -> false).distanceMoved() == 0,
                "blocked belt has no texture travel");
        check(BeltTransport.tickWithMotion(items, 10, 4, packet -> true).distanceMoved() == 0,
                "in-place unload does not fake belt movement");

        for (float speed : new float[]{1, 2, 4, 12.5f, 64}) {
            var random = new java.util.Random(42);
            int loaded = 0;
            delivered[0] = 0;
            for (int tick = 0; tick < 2000; tick++) {
                boolean open = tick % 113 > 30;
                BeltTransport.tick(items, 5.5, speed, packet -> {
                    if (open) delivered[0] += packet.count;
                    return open;
                });
                if (BeltTransport.canLoad(items, 5.5)) {
                    int count = random.nextInt(512) + 1;
                    loaded += count;
                    items.addFirst(new Packet(0, count));
                }
                check(items.stream().allMatch(packet -> packet.progress >= 0 && packet.progress <= 1), "bounded movement");
            }
            int inTransit = items.stream().mapToInt(packet -> packet.count).sum();
            check(loaded == delivered[0] + inTransit, "conservation through repeated congestion at speed " + speed);
            items.clear();
        }
    }

    private static void distribution() {
        for (int outputs = 1; outputs <= 4; outputs++) {
            for (int count : new int[]{1, 2, 64, 256, 512, 4096}) {
                int next = 0;
                int[] totals = new int[outputs];
                for (int batch = 0; batch < outputs; batch++) {
                    var result = pureneko.conveyor_belt_plus.util.SplitDistribution.divide(count, outputs, next);
                    check(java.util.Arrays.stream(result.counts()).sum() == count, "split conserves whole batch");
                    int min = java.util.Arrays.stream(result.counts()).min().orElseThrow();
                    int max = java.util.Arrays.stream(result.counts()).max().orElseThrow();
                    check(max - min <= 1, "same batch shared evenly between every output");
                    for (int i = 0; i < outputs; i++) totals[i] += result.counts()[i];
                    next = result.nextIndex();
                }
                for (int total : totals) check(total == count, "remainders rotate fairly");
            }
        }
    }

    private static class Packet implements BeltTransport.Packet {
        private float progress;
        private final int count;
        Packet(float progress, int count) { this.progress = progress; this.count = count; }
        public float progress() { return progress; }
        public void progress(float value) { progress = value; }
    }

    private static void near(double actual, double expected, String name) {
        check(Math.abs(actual - expected) < 0.0001f, name + ": " + actual + " != " + expected);
    }

    private static void check(boolean success, String name) {
        checks++;
        if (!success) throw new AssertionError(name);
    }
}
