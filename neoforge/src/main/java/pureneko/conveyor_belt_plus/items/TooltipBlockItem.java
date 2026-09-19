package pureneko.conveyor_belt_plus.items;

import pureneko.conveyor_belt_plus.blocks.ChuteBlock;
import net.minecraft.block.Block;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.List;

public class TooltipBlockItem extends BlockItem {
    public TooltipBlockItem(Block block, Settings settings) { super(block, settings); }
    @Override public net.minecraft.util.ActionResult useOnBlock(net.minecraft.item.ItemUsageContext context) {
        var player = context.getPlayer();
        var world = context.getWorld();
        if (player != null && player.isSneaking() && getBlock() instanceof ChuteBlock upgrade
                && world.getBlockState(context.getBlockPos()).getBlock() instanceof ChuteBlock previous
                && pureneko.conveyor_belt_plus.util.TierUpgrade.isUpgrade(previous.getTier(), upgrade.getTier())) {
            if (world.isClient) return net.minecraft.util.ActionResult.SUCCESS;
            return pureneko.conveyor_belt_plus.blocks.UpgradeInteractions.chute(world, context.getBlockPos(),
                    player, context.getStack(), upgrade) ? net.minecraft.util.ActionResult.SUCCESS : net.minecraft.util.ActionResult.FAIL;
        }
        return super.useOnBlock(context);
    }
    @Override public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        if (!(getBlock() instanceof ChuteBlock))
            tooltip.add(Text.translatable(getBlock().getTranslationKey() + ".tooltip").formatted(Formatting.GRAY));
        super.appendTooltip(stack, context, tooltip, type);
        if (getBlock() instanceof pureneko.conveyor_belt_plus.blocks.ConveyorSplitterBlock)
            tooltip.add(Text.translatable("tooltip.conveyor_belt_plus.splitter.capacity",
                    pureneko.conveyor_belt_plus.config.ConveyorConfig.splitterBufferItems()).formatted(Formatting.AQUA));
        if (!Screen.hasControlDown() && getBlock() instanceof ChuteBlock)
            tooltip.add(Text.translatable("message.conveyor_belt_plus.show_extra").formatted(Formatting.DARK_GRAY));
    }
}
