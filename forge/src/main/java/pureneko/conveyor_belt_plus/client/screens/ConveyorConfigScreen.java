package pureneko.conveyor_belt_plus.client.screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import pureneko.conveyor_belt_plus.config.ConveyorConfig;
import java.util.ArrayList;
import java.util.List;

/** Edits the native server config only from its owning integrated server. */
public final class ConveyorConfigScreen extends Screen {
    private final Screen parent;
    private final List<EditBox> fields = new ArrayList<>();
    private final List<ForgeConfigSpec.ConfigValue<?>> values = new ArrayList<>();
    private boolean extractionSides;
    private boolean fluidPage;
    private Component error = Component.empty();

    public ConveyorConfigScreen(Screen parent) {
        super(Component.translatable("config.conveyor_belt_plus.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        fields.clear();
        values.clear();
        boolean editable = minecraft.getSingleplayerServer() != null && ConveyorConfig.SPEC.isLoaded();
        for (int tier = 0; tier < 3; tier++) {
            values.add(ConveyorConfig.SPEEDS[tier]);
            values.add(ConveyorConfig.STACKS[tier]);
            values.add(ConveyorConfig.FILTER_LIMITS[tier]);
        }
        values.add(ConveyorConfig.SPLITTER_BUFFER);
        for (int tier = 0; tier < 3; tier++) {
            values.add(ConveyorConfig.FLUID_AMOUNTS[tier]);
            values.add(ConveyorConfig.FLUID_FILTER_LIMITS[tier]);
        }
        int left = width / 2 - 155;
        addRenderableWidget(Button.builder(pageText(), button -> {
            fluidPage = !fluidPage;
            button.setMessage(pageText());
            updatePage();
        }).bounds(left, 43, 112, 18).build());
        for (int i = 0; i < values.size(); i++) {
            var value = values.get(i);
            int column = i < 10 ? i / 3 : (i - 10) / 2;
            int row = i < 10 ? i % 3 : (i - 10) % 2;
            var field = new EditBox(font, i == 9 ? left + 252 : left + 124 + column * 62, i == 9 ? 143 : 65 + row * 24, 56, 18,
                    Component.translatable("config.conveyor_belt_plus." + String.join(".", value.getPath())));
            String range = i >= 10 ? ((i - 10) % 2 == 0 ? "1–1048576" : "1–54")
                    : i == 9 ? "1–1048576" : i % 3 == 0 ? "0.1–64" : i % 3 == 2 ? "1–54" : "1–64";
            field.setTooltip(net.minecraft.client.gui.components.Tooltip.create(field.getMessage().copy().append(": " + range)));
            field.setMaxLength(20);
            field.setValue(String.valueOf(ConveyorConfig.SPEC.isLoaded() ? value.get() : value.getDefault()));
            field.setEditable(editable);
            fields.add(addRenderableWidget(field));
        }
        updatePage();
        extractionSides = ConveyorConfig.extractionSidesEnabled();
        var toggle = addRenderableWidget(Button.builder(sideText(), button -> {
            extractionSides = !extractionSides;
            button.setMessage(sideText());
        }).bounds(left, 174, 310, 20).build());
        toggle.active = editable;
        var save = addRenderableWidget(Button.builder(Component.translatable("config.conveyor_belt_plus.save"), button -> save())
                .bounds(width / 2 - 155, height - 25, 150, 20).build());
        save.active = editable;
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(width / 2 + 5, height - 25, 150, 20).build());
    }

    private Component sideText() {
        return Component.translatable("config.conveyor_belt_plus.chute.extraction_sides_enabled")
                .append(": ").append(Component.translatable(extractionSides ? "options.on" : "options.off"));
    }
    private Component pageText() { return Component.translatable("screen.conveyor_belt_plus." + (fluidPage ? "tab_fluids" : "tab_items")); }
    private void updatePage() {
        for (int i = 0; i < fields.size(); i++) {
            fields.get(i).visible = i == 9 || (i >= 10) == fluidPage;
            if (!fields.get(i).visible) fields.get(i).setFocused(false);
        }
    }
    private void save() {
        var server = minecraft.getSingleplayerServer();
        if (server == null || !ConveyorConfig.SPEC.isLoaded()) return;
        var parsed = new Number[values.size()];
        try {
            for (int i = 0; i < values.size(); i++) {
                String text = fields.get(i).getValue().trim();
                if (values.get(i) instanceof ForgeConfigSpec.DoubleValue) {
                    double value = Double.parseDouble(text);
                    if (!Double.isFinite(value) || value < .1 || value > 64) throw new IllegalArgumentException();
                    parsed[i] = value;
                } else {
                    int value = Integer.parseInt(text);
                    int max = i >= 10 ? ((i - 10) % 2 == 0 ? 1048576 : ConveyorConfig.MAX_FILTER_RULES)
                            : i == 9 ? ConveyorConfig.MAX_SPLITTER_BUFFER : i % 3 == 2 ? ConveyorConfig.MAX_FILTER_RULES : 64;
                    if (value < 1 || value > max) throw new IllegalArgumentException();
                    parsed[i] = value;
                }
            }
        } catch (IllegalArgumentException ex) {
            error = Component.translatable("config.conveyor_belt_plus.invalid");
            return;
        }
        boolean sides = extractionSides;
        server.execute(() -> {
            for (int i = 0; i < values.size(); i++) {
                if (values.get(i) instanceof ForgeConfigSpec.DoubleValue value) value.set(parsed[i].doubleValue());
                else if (values.get(i) instanceof ForgeConfigSpec.IntValue value) value.set(parsed[i].intValue());
            }
            ConveyorConfig.CHUTE_EXTRACTION_SIDES.set(sides);
            ConveyorConfig.SPEC.save();
        });
        onClose();
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        context.drawCenteredString(font, Component.translatable("config.conveyor_belt_plus.local_only"), width / 2, 26, 0xAAAAAA);
        String[] tiers = {"standard", "advanced", "ultimate"};
        for (int i = 0; i < tiers.length; i++) context.drawCenteredString(font,
                Component.translatable("config.conveyor_belt_plus.tier." + tiers[i]), width / 2 - 3 + i * 62, 49, 0xFFFFFF);
        String[] labels = fluidPage ? new String[]{"fluid_amount", "fluid_filters", "", "buffer"}
                : new String[]{"speed", "stacks", "filters", "buffer"};
        for (int i = 0; i < labels.length; i++) context.drawString(font,
                labels[i].isEmpty() ? Component.empty() : Component.translatable("config.conveyor_belt_plus.field." + labels[i]), width / 2 - 155,
                i == 3 ? 148 : 70 + i * 24, 0xFFFFFF);
        context.drawCenteredString(font, error, width / 2, height - 38, 0xFF7777);
        super.render(context, mouseX, mouseY, delta);
    }
}
