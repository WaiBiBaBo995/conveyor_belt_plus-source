package pureneko.conveyor_belt_plus.client.screens;

import pureneko.conveyor_belt_plus.util.RedstoneControl;
import pureneko.conveyor_belt_plus.util.RedstoneControl.Mode;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Vanilla item icons, with the vanilla unlit texture for the no-signal mode. */
public final class RedstoneModeButton extends Button {
    private static final ResourceLocation UNLIT_TORCH = ResourceLocation.withDefaultNamespace("textures/block/redstone_torch_off.png");
    private static final ItemStack GUNPOWDER = new ItemStack(Items.GUNPOWDER);
    private static final ItemStack TORCH = new ItemStack(Items.REDSTONE_TORCH);
    private static final ItemStack BARRIER = new ItemStack(Items.BARRIER);
    private static final ItemStack REPEATER = new ItemStack(Items.REPEATER);
    private final Supplier<RedstoneControl.Mode> mode;
    private RedstoneControl.Mode displayed;

    public RedstoneModeButton(int x, int y, Supplier<RedstoneControl.Mode> mode, OnPress action) {
        super(x, y, 20, 20, Component.empty(), action, DEFAULT_NARRATION);
        this.mode = mode;
    }

    @Override protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        var current = mode.get();
        if (displayed != current) {
            displayed = current;
            setTooltip(Tooltip.create(Component.translatable(current.translationKey()).append("\n")
                    .append(Component.translatable(current.translationKey() + ".help")).append("\n")
                    .append(Component.translatable("redstone.conveyor_belt_plus.cycle"))));
        }
        super.renderWidget(context, mouseX, mouseY, delta);
        if (current == RedstoneControl.Mode.LOW) {
            context.blit(UNLIT_TORCH, getX() + 2, getY() + 2, 0, 0, 16, 16, 16, 16);
        } else {
            var icon = switch (current) {
                case ALWAYS -> GUNPOWDER;
                case HIGH -> TORCH;
                case NEVER -> BARRIER;
                case PULSE -> REPEATER;
                default -> ItemStack.EMPTY;
            };
            context.renderItem(icon, getX() + 2, getY() + 2);
        }
    }

    @Override public net.minecraft.network.chat.MutableComponent createNarrationMessage() {
        return Component.translatable(mode.get().translationKey());
    }
}
