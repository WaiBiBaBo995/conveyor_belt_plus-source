package pureneko.conveyor_belt_plus.client.screens;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraftforge.common.ForgeConfigSpec;
import pureneko.conveyor_belt_plus.config.ConveyorConfig;

import java.util.ArrayList;
import java.util.List;

/** Edits the native server config only from its owning integrated server. */
public final class ConveyorConfigScreen extends Screen {
    private final Screen parent;
    private final List<TextFieldWidget> fields = new ArrayList<>();
    private final List<ForgeConfigSpec.ConfigValue<?>> values = new ArrayList<>();
    private boolean extractionSides;
    private Text error = Text.empty();

    public ConveyorConfigScreen(Screen parent) {
        super(Text.translatable("config.conveyor_belt_plus.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        fields.clear();
        values.clear();
        boolean editable = client.getServer() != null && ConveyorConfig.SPEC.isLoaded();
        for (int tier = 0; tier < 3; tier++) {
            values.add(ConveyorConfig.SPEEDS[tier]);
            values.add(ConveyorConfig.STACKS[tier]);
            values.add(ConveyorConfig.FILTER_LIMITS[tier]);
        }
        values.add(ConveyorConfig.SPLITTER_BUFFER);
        int left = width / 2 - 155;
        for (int i = 0; i < values.size(); i++) {
            var value = values.get(i);
            var field = new TextFieldWidget(textRenderer, i == 9 ? left + 252 : left + 124 + (i / 3) * 62, i == 9 ? 143 : 65 + (i % 3) * 24, 56, 18,
                    Text.translatable("config.conveyor_belt_plus." + String.join(".", value.getPath())));
            String range = i == 9 ? "1–1048576" : i % 3 == 0 ? "0.1–64" : i % 3 == 2 ? "1–54" : "1–64";
            field.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(field.getMessage().copy().append(": " + range)));
            field.setMaxLength(20);
            field.setText(String.valueOf(ConveyorConfig.SPEC.isLoaded() ? value.get() : value.getDefault()));
            field.setEditable(editable);
            fields.add(addDrawableChild(field));
        }
        extractionSides = ConveyorConfig.extractionSidesEnabled();
        var toggle = addDrawableChild(ButtonWidget.builder(sideText(), button -> {
            extractionSides = !extractionSides;
            button.setMessage(sideText());
        }).dimensions(left, 174, 310, 20).build());
        toggle.active = editable;
        var save = addDrawableChild(ButtonWidget.builder(Text.translatable("config.conveyor_belt_plus.save"), button -> save())
                .dimensions(width / 2 - 155, height - 25, 150, 20).build());
        save.active = editable;
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> close())
                .dimensions(width / 2 + 5, height - 25, 150, 20).build());
    }

    private Text sideText() {
        return Text.translatable("config.conveyor_belt_plus.chute.extraction_sides_enabled")
                .append(": ").append(Text.translatable(extractionSides ? "options.on" : "options.off"));
    }
    private void save() {
        var server = client.getServer();
        if (server == null || !ConveyorConfig.SPEC.isLoaded()) return;
        var parsed = new Number[values.size()];
        try {
            for (int i = 0; i < values.size(); i++) {
                String text = fields.get(i).getText().trim();
                if (values.get(i) instanceof ForgeConfigSpec.DoubleValue) {
                    double value = Double.parseDouble(text);
                    if (!Double.isFinite(value) || value < .1 || value > 64) throw new IllegalArgumentException();
                    parsed[i] = value;
                } else {
                    int value = Integer.parseInt(text);
                    int max = i == 9 ? ConveyorConfig.MAX_SPLITTER_BUFFER : i % 3 == 2 ? ConveyorConfig.MAX_FILTER_RULES : 64;
                    if (value < 1 || value > max) throw new IllegalArgumentException();
                    parsed[i] = value;
                }
            }
        } catch (IllegalArgumentException ex) {
            error = Text.translatable("config.conveyor_belt_plus.invalid");
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
        close();
    }
    @Override public void close() { client.setScreen(parent); }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("config.conveyor_belt_plus.local_only"), width / 2, 26, 0xAAAAAA);
        String[] tiers = {"standard", "advanced", "ultimate"};
        for (int i = 0; i < tiers.length; i++) context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("config.conveyor_belt_plus.tier." + tiers[i]), width / 2 - 3 + i * 62, 49, 0xFFFFFF);
        String[] labels = {"speed", "stacks", "filters", "buffer"};
        for (int i = 0; i < labels.length; i++) context.drawTextWithShadow(textRenderer,
                Text.translatable("config.conveyor_belt_plus.field." + labels[i]), width / 2 - 155,
                i == 3 ? 148 : 70 + i * 24, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, error, width / 2, height - 38, 0xFF7777);
        super.render(context, mouseX, mouseY, delta);
    }
}
