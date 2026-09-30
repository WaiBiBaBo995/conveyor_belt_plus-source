package pureneko.conveyor_belt_plus.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.registry.ItemContent;
import pureneko.conveyor_belt_plus.util.TierUpgrade;

/** Server-thread transactions: transfer state first, consume/refund only after success. */
public final class UpgradeInteractions {
    private UpgradeInteractions() {}

    public static boolean chute(Level world, BlockPos pos, Player player, ItemStack offered, ChuteBlock upgrade) {
        if (world.isClientSide || offered.isEmpty() || !player.isShiftKeyDown() || !player.getAbilities().mayBuild) return false;
        var state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChuteBlock previous)
                || previous.getKind() != upgrade.getKind()
                || !TierUpgrade.isUpgrade(previous.getTier(), upgrade.getTier())
                || !(world.getBlockEntity(pos) instanceof ChuteBlockEntity old)) return false;
        var lookup = world.registryAccess();
        var data = old.saveWithFullMetadata();
        var next = upgrade.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, state.getValue(HorizontalDirectionalBlock.FACING));
        try {
            if (!world.setBlock(pos, next, Block.UPDATE_ALL)) return false;
            if (!(world.getBlockEntity(pos) instanceof ChuteBlockEntity replacement))
                throw new IllegalStateException("Missing replacement chute");
            replacement.load(data);
            replacement.setChanged();
            if (world instanceof ServerLevel serverWorld) serverWorld.getChunkSource().blockChanged(pos);
        } catch (RuntimeException ex) {
            world.setBlock(pos, state, Block.UPDATE_ALL);
            if (world.getBlockEntity(pos) instanceof ChuteBlockEntity restored) {
                restored.load(data);
                restored.setChanged();
                if (world instanceof ServerLevel serverWorld) serverWorld.getChunkSource().blockChanged(pos);
            }
            ConveyorBeltPlus.LOGGER.error("Chute upgrade rolled back at {}", pos, ex);
            return false;
        }
        finish(world, pos, player, offered, new ItemStack(state.getBlock()), "chute_upgraded");
        return true;
    }

    public static boolean belt(Level world, BlockPos pos, Direction port, Player player,
                               ItemStack offered, int tier) {
        if (world.isClientSide || offered.isEmpty() || !player.isShiftKeyDown() || !player.getAbilities().mayBuild) return false;
        var node = ConveyorNodeUtil.get(world, pos);
        int previous = node == null ? 0 : node.outgoingBeltTier(port);
        if (!TierUpgrade.isUpgrade(previous, tier) || !node.upgradeOutgoingBelt(port, tier)) return false;
        finish(world, pos, player, offered, new ItemStack(ItemContent.beltForTier(previous)), "belt_upgraded");
        return true;
    }

    private static void finish(Level world, BlockPos pos, Player player, ItemStack offered,
                               ItemStack returned, String message) {
        if (!player.isCreative()) {
            offered.shrink(1);
            if (!player.getInventory().add(returned)) player.drop(returned, false);
            player.getInventory().setChanged();
        }
        world.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5f, 1.5f);
        player.displayClientMessage(Component.translatable("message.conveyor_belt_plus." + message), true);
    }
}
