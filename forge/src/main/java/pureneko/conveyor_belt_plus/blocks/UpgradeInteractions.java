package pureneko.conveyor_belt_plus.blocks;

import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.registry.ItemContent;
import pureneko.conveyor_belt_plus.util.TierUpgrade;
import net.minecraft.block.Block;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/** Server-thread transactions: transfer state first, consume/refund only after success. */
public final class UpgradeInteractions {
    private UpgradeInteractions() {}

    public static boolean chute(World world, BlockPos pos, PlayerEntity player, ItemStack offered, ChuteBlock upgrade) {
        if (world.isClient || offered.isEmpty() || !player.isSneaking() || !player.getAbilities().allowModifyWorld) return false;
        var state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChuteBlock previous)
                || !TierUpgrade.isUpgrade(previous.getTier(), upgrade.getTier())
                || !(world.getBlockEntity(pos) instanceof ChuteBlockEntity old)) return false;
        var lookup = world.getRegistryManager();
        var data = old.createNbtWithIdentifyingData();
        var next = upgrade.getDefaultState().with(HorizontalFacingBlock.FACING, state.get(HorizontalFacingBlock.FACING));
        try {
            if (!world.setBlockState(pos, next, Block.NOTIFY_ALL)) return false;
            if (!(world.getBlockEntity(pos) instanceof ChuteBlockEntity replacement))
                throw new IllegalStateException("Missing replacement chute");
            replacement.readNbt(data);
            replacement.markDirty();
            if (world instanceof ServerWorld serverWorld) serverWorld.getChunkManager().markForUpdate(pos);
        } catch (RuntimeException ex) {
            world.setBlockState(pos, state, Block.NOTIFY_ALL);
            if (world.getBlockEntity(pos) instanceof ChuteBlockEntity restored) {
                restored.readNbt(data);
                restored.markDirty();
                if (world instanceof ServerWorld serverWorld) serverWorld.getChunkManager().markForUpdate(pos);
            }
            ConveyorBeltPlus.LOGGER.error("Chute upgrade rolled back at {}", pos, ex);
            return false;
        }
        finish(world, pos, player, offered, new ItemStack(state.getBlock()), "chute_upgraded");
        return true;
    }

    public static boolean belt(World world, BlockPos pos, Direction port, PlayerEntity player,
                               ItemStack offered, int tier) {
        if (world.isClient || offered.isEmpty() || !player.isSneaking() || !player.getAbilities().allowModifyWorld) return false;
        var node = ConveyorNodeUtil.get(world, pos);
        int previous = node == null ? 0 : node.outgoingBeltTier(port);
        if (!TierUpgrade.isUpgrade(previous, tier) || !node.upgradeOutgoingBelt(port, tier)) return false;
        finish(world, pos, player, offered, new ItemStack(ItemContent.beltForTier(previous)), "belt_upgraded");
        return true;
    }

    private static void finish(World world, BlockPos pos, PlayerEntity player, ItemStack offered,
                               ItemStack returned, String message) {
        if (!player.isCreative()) {
            offered.decrement(1);
            if (!player.getInventory().insertStack(returned)) player.dropItem(returned, false);
            player.getInventory().markDirty();
        }
        world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.5f, 1.5f);
        player.sendMessage(Text.translatable("message.conveyor_belt_plus." + message), true);
    }
}
