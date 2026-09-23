package pureneko.conveyor_belt_plus.client.screens;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import pureneko.conveyor_belt_plus.util.RedstoneControl;
import java.util.function.Supplier;

/** Vanilla item icons, with the vanilla unlit texture for the no-signal mode. */
public final class RedstoneModeButton extends ButtonWidget {
    private static final Identifier UNLIT_TORCH = new Identifier("textures/block/redstone_torch_off.png");
    private static final ItemStack GUNPOWDER = new ItemStack(Items.GUNPOWDER);
    private static final ItemStack TORCH = new ItemStack(Items.REDSTONE_TORCH);
    private static final ItemStack BARRIER = new ItemStack(Items.BARRIER);
    private static final ItemStack REPEATER = new ItemStack(Items.REPEATER);
    private final Supplier<RedstoneControl.Mode> mode;
    private RedstoneControl.Mode displayed;

    public RedstoneModeButton(int x, int y, Supplier<RedstoneControl.Mode> mode, PressAction action) {
        super(x, y, 20, 20, Text.empty(), action, DEFAULT_NARRATION_SUPPLIER);
        this.mode = mode;
    }

    @Override public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        var current = mode.get();
        if (displayed != current) {
            displayed = current;
            setTooltip(Tooltip.of(Text.translatable(current.translationKey()).append("\n")
                    .append(Text.translatable(current.translationKey() + ".help")).append("\n")
                    .append(Text.translatable("redstone.conveyor_belt_plus.cycle"))));
        }
        super.renderButton(context, mouseX, mouseY, delta);
        if (current == RedstoneControl.Mode.LOW) {
            context.drawTexture(UNLIT_TORCH, getX() + 2, getY() + 2, 0, 0, 16, 16, 16, 16);
        } else {
            var icon = switch (current) {
                case ALWAYS -> GUNPOWDER;
                case HIGH -> TORCH;
                case NEVER -> BARRIER;
                case PULSE -> REPEATER;
                default -> ItemStack.EMPTY;
            };
            context.drawItem(icon, getX() + 2, getY() + 2);
        }
    }

    @Override public net.minecraft.text.MutableText getNarrationMessage() {
        return Text.translatable(mode.get().translationKey());
    }
}
