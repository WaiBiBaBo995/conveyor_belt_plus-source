package pureneko.conveyor_belt_plus;

import com.electronwill.nightconfig.core.CommentedConfig;
import pureneko.conveyor_belt_plus.config.ConveyorConfig;
import java.util.Properties;
import java.util.function.Consumer;

/** Test-only native-spec mutation, detached from Forge's autosaving file for each GameTest batch. */
final class ConfigTestSupport {
    private ConfigTestSupport() {}
    private static CommentedConfig originalConfig;
    static void beginBatch() {
        if (!ConveyorConfig.SPEC.isLoaded()) throw new AssertionError("Tests require the loaded Forge SERVER config");
        originalConfig = net.minecraftforge.fml.config.ConfigTracker.INSTANCE.configSets()
                .get(net.minecraftforge.fml.config.ModConfig.Type.SERVER).stream()
                .filter(config -> config.getSpec() == ConveyorConfig.SPEC).findFirst().orElseThrow().getConfigData();
        var temporary = CommentedConfig.inMemory();
        ConveyorConfig.SPEC.correct(temporary);
        // Forge ConfigValue.set writes through to AutosaveCommentedFileConfig, unlike NeoForge.
        // Keep native schema/get/set behavior without racing the test world's file watcher.
        ConveyorConfig.SPEC.acceptConfig(temporary);
    }
    static void endBatch() {
        ConveyorConfig.SPEC.acceptConfig(originalConfig);
        originalConfig = null;
    }
    static void apply(Properties properties, Consumer<String> warn) {
        var config = CommentedConfig.inMemory();
        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key);
            try {
                if (key.equals("chute.extraction_sides_enabled")) config.set(key,
                        value.equals("true") ? Boolean.TRUE : value.equals("false") ? Boolean.FALSE : value);
                else if (key.endsWith(".speed") || key.equals("belt.minimum_angle") || key.equals("belt.minimum_turn_radius"))
                    config.set(key, Double.parseDouble(value));
                else config.set(key, Integer.parseInt(value));
            } catch (NumberFormatException ex) { config.set(key, value); }
        }
        ConveyorConfig.SPEC.correct(config, (action, path, oldValue, newValue) -> {
            if (oldValue != null) warn.accept("Corrected " + String.join(".", path));
        });
        apply(config);
    }
    static void apply(CommentedConfig config) {
        if (!ConveyorConfig.SPEC.isLoaded()) throw new AssertionError("Tests require the loaded Forge SERVER config");
        for (int i = 0; i < 3; i++) {
            ConveyorConfig.SPEEDS[i].set(config.<Number>get(ConveyorConfig.SPEEDS[i].getPath()).doubleValue());
            ConveyorConfig.STACKS[i].set(config.<Number>get(ConveyorConfig.STACKS[i].getPath()).intValue());
            ConveyorConfig.FILTER_LIMITS[i].set(config.<Number>get(ConveyorConfig.FILTER_LIMITS[i].getPath()).intValue());
        }
        ConveyorConfig.SPLITTER_BUFFER.set(config.<Number>get("splitter.buffer_items").intValue());
        ConveyorConfig.CHUTE_EXTRACTION_SIDES.set(config.<Boolean>get("chute.extraction_sides_enabled"));
        ConveyorConfig.MINIMUM_BELT_ANGLE.set(config.<Number>get("belt.minimum_angle").doubleValue());
        ConveyorConfig.MINIMUM_BELT_TURN_RADIUS.set(config.<Number>get("belt.minimum_turn_radius").doubleValue());
    }
}
