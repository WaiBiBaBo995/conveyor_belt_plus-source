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

    public static boolean canReplace(ChuteBlock previous, ChuteBlock replacement) {
        return previous.getKind() == replacement.getKind()
                ? TierUpgrade.isUpgrade(previous.getTier(), replacement.getTier())
                : replacement.getKind() == ChuteKind.UNIVERSAL && replacement.getTier() >= previous.getTier();
    }

    public static boolean chute(Level world, BlockPos pos, Player player, ItemStack offered, ChuteBlock upgrade) {
        if (world.isClientSide || offered.isEmpty() || !player.isShiftKeyDown() || !player.getAbilities().mayBuild) return false;
        var state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChuteBlock previous)
                || !canReplace(previous, upgrade)
                || !(world.getBlockEntity(pos) instanceof ChuteBlockEntity old)) return false;
        var lookup = world.registryAccess();
        var data = old.saveWithFullMetadata(lookup);
        var next = upgrade.defaultBlockState().setValue(ChuteBlock.FACING, state.getValue(ChuteBlock.FACING));
        try {
            if (!world.setBlock(pos, next, Block.UPDATE_ALL)) return false;
            if (!(world.getBlockEntity(pos) instanceof ChuteBlockEntity replacement))
                throw new IllegalStateException("Missing replacement chute");
            replacement.loadWithComponents(data, lookup);
            replacement.setChanged();
            if (world instanceof ServerLevel serverWorld) serverWorld.getChunkSource().blockChanged(pos);
        } catch (RuntimeException ex) {
            world.setBlock(pos, state, Block.UPDATE_ALL);
            if (world.getBlockEntity(pos) instanceof ChuteBlockEntity restored) {
                restored.loadWithComponents(data, lookup);
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

    /** Authoritative belt-body interaction: one named loaded owner, bounded ray and standard protection event. */
    public static boolean beltAt(Player player, BlockPos pos, Direction port) {
        var eye = player.getEyePosition();
        return beltAlongRay(player, pos, port, eye, eye.add(player.getLookAngle().scale(player.blockInteractionRange())), false);
    }

    public static boolean beltAlongRay(Player player, BlockPos pos, Direction port,
            net.minecraft.world.phys.Vec3 eye, net.minecraft.world.phys.Vec3 end, boolean remote) {
        var world = player.level();
        var offered = player.getMainHandItem();
        if (world.isClientSide || !world.hasChunkAt(pos) || !player.isAlive() || player.isSpectator()
                || !player.isShiftKeyDown() || !player.getAbilities().mayBuild
                || player.containerMenu != player.inventoryMenu
                || !(offered.getItem() instanceof pureneko.conveyor_belt_plus.items.BeltItem item)) return false;
        if (remote && (!pureneko.conveyor_belt_plus.compat.rts.RtsCompat.validRay(player, eye, end.subtract(eye).normalize())
                || eye.distanceToSqr(end) > 128 * 128 + .01
                || !pureneko.conveyor_belt_plus.compat.rts.RtsCompat.canBuild(player, pos, false))) return false;
        var entity = world.getBlockEntity(pos);
        var access = entity instanceof ConveyorNode node ? node.pickupAccess(port) : null;
        if (access == null || access.data() == null || !TierUpgrade.isUpgrade(access.tier(), item.getBeltTier())) return false;
        var hit = access.data().interactionPath().raycast(eye, end);
        if (hit == null || !BeltPickup.unobstructed(player, eye, hit.point())) return false;
        if (remote && !pureneko.conveyor_belt_plus.compat.rts.RtsCompat.canBuild(player, BlockPos.containing(hit.point()), false)) return false;
        var entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, end,
                new net.minecraft.world.phys.AABB(eye, end).inflate(1),
                candidate -> !candidate.isSpectator() && candidate.isPickable(), eye.distanceToSqr(hit.point()));
        if (entityHit != null) return false;
        var destination = new BeltInsertion.Destination(entity, port, hit.point(), hit.progress());
        if (!BeltInsertion.allowed(player, destination, offered, remote)) return false;
        boolean upgraded = belt(world, pos, port, player, offered, item.getBeltTier());
        if (upgraded) BeltInsertion.syncInventory(player);
        return upgraded;
    }

    private static void finish(Level world, BlockPos pos, Player player, ItemStack offered,
                               ItemStack returned, String message) {
        if (!player.isCreative()) {
            // With the last new belt, keep the connection draft on the refunded old belt.
            if (offered.getCount() == 1 && offered.getItem() instanceof pureneko.conveyor_belt_plus.items.BeltItem) {
                var start = offered.get(pureneko.conveyor_belt_plus.registry.ComponentContent.BELT_START.get());
                var direction = offered.get(pureneko.conveyor_belt_plus.registry.ComponentContent.BELT_DIR.get());
                var supports = offered.get(pureneko.conveyor_belt_plus.registry.ComponentContent.MIDPOINTS.get());
                if (start != null) returned.set(pureneko.conveyor_belt_plus.registry.ComponentContent.BELT_START.get(), start);
                if (direction != null) returned.set(pureneko.conveyor_belt_plus.registry.ComponentContent.BELT_DIR.get(), direction);
                if (supports != null) returned.set(pureneko.conveyor_belt_plus.registry.ComponentContent.MIDPOINTS.get(), supports);
            }
            offered.shrink(1);
            if (pureneko.conveyor_belt_plus.compat.rts.RtsCompat.active(player) && player instanceof net.minecraft.server.level.ServerPlayer server)
                pureneko.conveyor_belt_plus.compat.rts.RtsActions.refund(server, returned);
            else if (!player.getInventory().add(returned)) player.drop(returned, false);
            player.getInventory().setChanged();
        }
        world.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5f, 1.5f);
        player.displayClientMessage(Component.translatable("message.conveyor_belt_plus." + message), true);
    }
}
