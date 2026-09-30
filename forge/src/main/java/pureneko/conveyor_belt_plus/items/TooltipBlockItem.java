package pureneko.conveyor_belt_plus.items;

import pureneko.conveyor_belt_plus.blocks.ChuteBlock;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public class TooltipBlockItem extends BlockItem {
    public TooltipBlockItem(Block block, Properties settings) { super(block, settings); }
    @Override public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        var player = context.getPlayer();
        var world = context.getLevel();
        if (player != null && player.isShiftKeyDown() && getBlock() instanceof ChuteBlock upgrade
                && world.getBlockState(context.getClickedPos()).getBlock() instanceof ChuteBlock previous
                && previous.getKind() == upgrade.getKind()
                && pureneko.conveyor_belt_plus.util.TierUpgrade.isUpgrade(previous.getTier(), upgrade.getTier())) {
            if (world.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
            return pureneko.conveyor_belt_plus.blocks.UpgradeInteractions.chute(world, context.getClickedPos(),
                    player, context.getItemInHand(), upgrade) ? net.minecraft.world.InteractionResult.SUCCESS : net.minecraft.world.InteractionResult.FAIL;
        }
        return super.useOn(context);
    }
    @Override public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level context, List<Component> tooltip, TooltipFlag type) {
        if (!(getBlock() instanceof ChuteBlock))
            tooltip.add(Component.translatable(getBlock().getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, type);
        if (getBlock() instanceof pureneko.conveyor_belt_plus.blocks.ConveyorSplitterBlock)
            tooltip.add(Component.translatable("tooltip.conveyor_belt_plus.splitter.capacity",
                    pureneko.conveyor_belt_plus.config.ConveyorConfig.splitterBufferItems()).withStyle(ChatFormatting.AQUA));
        if (!Screen.hasControlDown() && getBlock() instanceof ChuteBlock)
            tooltip.add(Component.translatable("message.conveyor_belt_plus.show_extra").withStyle(ChatFormatting.DARK_GRAY));
    }
}
