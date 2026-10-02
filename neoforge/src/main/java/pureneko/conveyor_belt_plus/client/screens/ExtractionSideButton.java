package pureneko.conveyor_belt_plus.client.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import pureneko.conveyor_belt_plus.screen.ChuteScreenHandler;
import pureneko.conveyor_belt_plus.util.ExtractionSide;

/** Small bevelled face selector; no item slots, item consumption or extra texture files. */
final class ExtractionSideButton extends Button {
    private final ExtractionSide side;
    private final ChuteScreenHandler handler;
    private int tooltipState = -1;

    ExtractionSideButton(int x, int y, ExtractionSide side, ChuteScreenHandler handler, OnPress action) {
        super(x, y, 20, 20, Component.translatable(side.translationKey() + ".short"), action, DEFAULT_NARRATION);
        this.side = side;
        this.handler = handler;
    }

    private boolean checked() { return side.selected(handler.getExtractionSides()); }
    private Component stateText() {
        return Component.translatable("screen.conveyor_belt_plus.extraction." + (checked() ? "on" : "off"));
    }
    private net.minecraft.network.chat.MutableComponent faceText() {
        return Component.translatable("screen.conveyor_belt_plus.extraction.face", Component.translatable(side.translationKey()),
                Component.translatable("screen.conveyor_belt_plus.extraction.world." + side.direction(handler.getFacing()).getName()));
    }
    @Override protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        int state = handler.getFacing().get3DDataValue() * 2 + (checked() ? 1 : 0);
        if (tooltipState != state) {
            tooltipState = state;
            setTooltip(Tooltip.create(faceText().append("\n").append(stateText()).append("\n")
                    .append(Component.translatable("screen.conveyor_belt_plus.extraction.orientation"))));
        }
        boolean highlight = active && (isHovered() || isFocused());
        int fill = checked() ? (highlight ? 0xFF29B982 : 0xFF285F35) : (highlight ? 0xFF888888 : 0xFF555555);
        int xx = getX(), yy = getY();
        context.fill(xx, yy, xx + width, yy + height, 0xFF101010);
        context.fill(xx + 1, yy + 1, xx + width - 1, yy + height - 1, checked() ? 0xFF58B886 : 0xFFAAAAAA);
        context.fill(xx + 2, yy + 2, xx + width - 2, yy + height - 2, fill);
        context.fill(xx + 1, yy + height - 2, xx + width - 1, yy + height - 1, 0xFF303030);
        var font = Minecraft.getInstance().font;
        context.drawCenteredString(font, getMessage(), xx + width / 2, yy + 6, active ? 0xFFFFFFFF : 0xFF888888);
        if (checked()) context.fill(xx + 15, yy + 3, xx + 17, yy + 5, 0xFFFFFFFF);
    }

    @Override public net.minecraft.network.chat.MutableComponent createNarrationMessage() {
        return faceText().append(". ").append(stateText());
    }
}
