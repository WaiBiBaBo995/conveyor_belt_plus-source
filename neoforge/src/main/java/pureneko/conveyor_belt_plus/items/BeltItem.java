package pureneko.conveyor_belt_plus.items;

import pureneko.conveyor_belt_plus.registry.BlockContent;
import pureneko.conveyor_belt_plus.registry.ComponentContent;
import pureneko.conveyor_belt_plus.registry.ItemContent;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity;
import pureneko.conveyor_belt_plus.blocks.ConveyorNode;
import pureneko.conveyor_belt_plus.blocks.ConveyorNodeUtil;
import pureneko.conveyor_belt_plus.util.BeltTiers;
import pureneko.conveyor_belt_plus.util.ChutePlacementPlan;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Tuple;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Selects endpoints and commits a complete belt only after all checks pass. */
public class BeltItem extends Item {

    private final int beltTier;

    public BeltItem(Properties settings) {
        this(settings, BeltTiers.STANDARD);
    }

    public BeltItem(Properties settings, int beltTier) {
        super(settings);
        this.beltTier = BeltTiers.normalize(beltTier);
    }

    public int getBeltTier() {
        return beltTier;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, net.minecraft.world.InteractionHand hand) {
        if (!world.isClientSide && user.isShiftKeyDown()) {
            var stack = user.getItemInHand(hand);
            stack.remove(ComponentContent.MIDPOINTS.get());
            stack.remove(ComponentContent.BELT_START.get());
            stack.remove(ComponentContent.BELT_DIR.get());
            user.displayClientMessage(Component.translatable("message.conveyor_belt_plus.reset"), true);
        }
        return super.use(world, user, hand);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var world = context.getLevel();
        var stack = context.getItemInHand();
        var player = context.getPlayer();
        if (player == null || !player.getAbilities().mayBuild) return InteractionResult.PASS;
        if (world.isClientSide) return InteractionResult.SUCCESS;

        if (pureneko.conveyor_belt_plus.compat.rts.RtsCompat.active(player))
            return pureneko.conveyor_belt_plus.compat.rts.RtsBeltDrafts.use(player, stack, () -> useEndpoint(context));
        return useEndpoint(context);
    }

    private InteractionResult useEndpoint(UseOnContext context) {
        var world = context.getLevel();
        var stack = context.getItemInHand();
        var player = context.getPlayer();

        var clickedPos = context.getClickedPos();
        var hasStart = stack.has(ComponentContent.BELT_START.get())
                && stack.has(ComponentContent.BELT_DIR.get());
        var clickedNode = ConveyorNodeUtil.get(world, clickedPos);

        if (clickedNode != null) {
            var port = interactionPort(context);
            var upgradePort = clickedNode instanceof ChuteBlockEntity chute ? chute.getOwnFacing() : port;
            if (player.isShiftKeyDown() && !hasStart && clickedNode.outgoingBeltTier(upgradePort) > 0) {
                if (pureneko.conveyor_belt_plus.blocks.UpgradeInteractions.belt(world, clickedPos, upgradePort, player, stack, beltTier))
                    return InteractionResult.SUCCESS;
                player.displayClientMessage(Component.translatable("message.conveyor_belt_plus.upgrade_requires_higher"), true);
                return InteractionResult.FAIL;
            }
            if (clickedNode instanceof ChuteBlockEntity chute) {
                if (chute.isUsed()) {
                    player.displayClientMessage(Component.translatable("message.conveyor_belt_plus.chute_used"), true);
                    return InteractionResult.FAIL;
                }
                if (hasStart) {
                    return createBelt(stack.get(ComponentContent.BELT_START.get()),
                            stack.get(ComponentContent.BELT_DIR.get()), getStoredMidpoints(stack, world),
                            clickedPos, chute.getOwnFacing(), world, stack, player)
                            ? InteractionResult.SUCCESS : InteractionResult.FAIL;
                }
                stack.set(ComponentContent.BELT_START.get(), clickedPos);
                stack.set(ComponentContent.BELT_DIR.get(), chute.getOwnFacing());
                player.displayClientMessage(Component.translatable("message.conveyor_belt_plus.started"), true);
                return InteractionResult.SUCCESS;
            }

            if (hasStart) {
                if (!clickedNode.hasInputPort(port) || clickedNode.isPortUsed(port)) {
                    player.displayClientMessage(Component.translatable("message.conveyor_belt_plus.port_used"), true);
                    return InteractionResult.FAIL;
                }
                return createBelt(stack.get(ComponentContent.BELT_START.get()),
                        stack.get(ComponentContent.BELT_DIR.get()), getStoredMidpoints(stack, world),
                        clickedPos, port, world, stack, player)
                        ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            }

            if (!clickedNode.hasOutputPort(port) || clickedNode.isPortUsed(port)) {
                player.displayClientMessage(Component.translatable("message.conveyor_belt_plus.port_used"), true);
                return InteractionResult.FAIL;
            }
            stack.set(ComponentContent.BELT_START.get(), clickedPos);
            stack.set(ComponentContent.BELT_DIR.get(), port);
            player.displayClientMessage(Component.translatable("message.conveyor_belt_plus.started"), true);
            return InteractionResult.SUCCESS;
        }

        BlockState clickedState = world.getBlockState(clickedPos);
        if (hasStart && clickedState.is(BlockContent.CONVEYOR_SUPPORT_BLOCK.get())) {
            var points = new ArrayList<BlockPos>();
            getStoredMidpoints(stack, world).stream().map(Tuple::getA).forEach(points::add);
            if (points.contains(clickedPos)) {
                player.displayClientMessage(Component.translatable("message.conveyor_belt_plus.midpoint_duplicate"), true);
                return InteractionResult.FAIL;
            }
            points.add(clickedPos);
            if (points.size() > 64 && pureneko.conveyor_belt_plus.compat.rts.RtsCompat.active(player)) return InteractionResult.FAIL;
            stack.set(ComponentContent.MIDPOINTS.get(), points);
            player.displayClientMessage(Component.translatable("message.conveyor_belt_plus.midpoint_added"), true);
            return InteractionResult.SUCCESS;
        }

        var endPos = clickedPos.offset(context.getClickedFace().getNormal());
        var endDir = context.getClickedFace();
        if (endDir.getAxis() == Direction.Axis.Y)
            endDir = context.getHorizontalDirection();

        var candidateState = world.getBlockState(endPos);
        if (candidateState.canBeReplaced() || candidateState.isAir()) {
            if (hasStart) {
                return createBelt(stack.get(ComponentContent.BELT_START.get()),
                        stack.get(ComponentContent.BELT_DIR.get()), getStoredMidpoints(stack, world),
                        endPos, endDir, world, stack, player)
                        ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            }

            if (context.getClickedFace().getAxis() != Direction.Axis.Y)
                endDir = endDir.getOpposite();
            stack.set(ComponentContent.BELT_START.get(), endPos);
            stack.set(ComponentContent.BELT_DIR.get(), endDir.getOpposite());
            player.displayClientMessage(Component.translatable("message.conveyor_belt_plus.started"), true);
        }
        return InteractionResult.SUCCESS;
    }

    private static Direction interactionPort(UseOnContext context) {
        return context.getClickedFace().getAxis().isHorizontal()
                ? context.getClickedFace() : context.getHorizontalDirection().getOpposite();
    }

    public static List<Tuple<BlockPos, Direction>> getStoredMidpoints(ItemStack stack, Level world) {
        var result = new ArrayList<Tuple<BlockPos, Direction>>();
        if (!stack.has(ComponentContent.MIDPOINTS.get())) return result;
        for (var point : stack.get(ComponentContent.MIDPOINTS.get())) {
            var state = world.getBlockState(point);
            if (state.is(BlockContent.CONVEYOR_SUPPORT_BLOCK.get()))
                result.add(new Tuple<>(point, state.getValue(HorizontalDirectionalBlock.FACING)));
        }
        return result;
    }

    private boolean createBelt(BlockPos start, Direction startDir, List<Tuple<BlockPos, Direction>> supports,
                               BlockPos end, Direction endDir, Level world, ItemStack beltStack,
                               Player player) {
        if (start == null || end == null || start.equals(end)) {
            player.displayClientMessage(Component.translatable("message.conveyor_belt_plus.invalid_path"), true);
            return false;
        }

        var startNode = ConveyorNodeUtil.get(world, start);
        var endNode = ConveyorNodeUtil.get(world, end);
        if (startNode != null && (!startNode.hasOutputPort(startDir) || startNode.isPortUsed(startDir))) {
            player.displayClientMessage(Component.translatable("message.conveyor_belt_plus.port_used"), true);
            return false;
        }
        if (endNode != null && (!endNode.hasInputPort(endDir) || endNode.isPortUsed(endDir))) {
            player.displayClientMessage(Component.translatable("message.conveyor_belt_plus.port_used"), true);
            return false;
        }

        if ((startNode == null && !canPlaceChute(world, start))
                || (endNode == null && !canPlaceChute(world, end))) {
            player.displayClientMessage(Component.translatable("message.conveyor_belt_plus.chute_position_blocked"), true);
            return false;
        }

        var chutesNeeded = (startNode == null ? 1 : 0) + (endNode == null ? 1 : 0);
        if (pureneko.conveyor_belt_plus.compat.rts.RtsCompat.active(player)) {
            if (!pureneko.conveyor_belt_plus.compat.rts.RtsCompat.canBuild(player, start, startNode == null)
                    || !pureneko.conveyor_belt_plus.compat.rts.RtsCompat.canBuild(player, end, endNode == null)
                    || supports.stream().anyMatch(point -> !pureneko.conveyor_belt_plus.compat.rts.RtsCompat.canBuild(player, point.getA(), false))) {
                player.displayClientMessage(Component.translatable("message.conveyor_belt_plus.rts_forbidden"), true);
                return false;
            }
        }
        var inventory = ChutePlacementPlan.orderedInventory(player);
        var plan = ChutePlacementPlan.select(inventory,
                chutesNeeded, player.isCreative(), new ItemStack(ItemContent.CHUTE.get()));
        if (plan == null || !plan.consume(inventory)) {
            player.displayClientMessage(Component.translatable("message.conveyor_belt_plus.not_enough_chutes"), true);
            return false;
        }
        if (!player.isCreative()) player.getInventory().setChanged();

        int startChoice = startNode == null ? 0 : -1;
        int endChoice = endNode == null ? (startNode == null ? 1 : 0) : -1;
        var oldStart = world.getBlockState(start);
        var oldEnd = world.getBlockState(end);
        boolean placedStart = false;
        boolean placedEnd = false;
        if (startChoice >= 0) {
            placedStart = world.setBlockAndUpdate(start, plan.state(startChoice, startDir));
        }
        if (endChoice >= 0 && (startChoice < 0 || placedStart)) {
            placedEnd = world.setBlockAndUpdate(end, plan.state(endChoice, endDir));
        }

        startNode = ConveyorNodeUtil.get(world, start);
        endNode = ConveyorNodeUtil.get(world, end);
        if ((startChoice >= 0 && !placedStart) || (endChoice >= 0 && !placedEnd)
                || startNode == null || endNode == null) {
            // A failed block placement must leave neither a free endpoint nor a missing inventory item.
            if (placedStart) world.setBlockAndUpdate(start, oldStart);
            if (placedEnd) world.setBlockAndUpdate(end, oldEnd);
            if (!player.isCreative()) {
                plan.refund(inventory);
                player.getInventory().setChanged();
            }
            player.displayClientMessage(Component.translatable("message.conveyor_belt_plus.invalid_path"), true);
            return false;
        }
        if (!startNode.connectOutgoing(startDir, end, endDir,
                supports.stream().map(Tuple::getA).toList(), beltTier)) {
            restoreRejectedConstruction(world, player, inventory, start, end, plan, startChoice, endChoice,
                    oldStart, oldEnd);
            player.displayClientMessage(Component.translatable("message.conveyor_belt_plus.turn_too_sharp"), true);
            return false;
        }
        endNode.connectIncoming(endDir, start);

        beltStack.remove(ComponentContent.MIDPOINTS.get());
        beltStack.remove(ComponentContent.BELT_START.get());
        beltStack.remove(ComponentContent.BELT_DIR.get());
        if (!player.isCreative()) {
            beltStack.shrink(1);
            player.getInventory().setChanged();
        }

        player.displayClientMessage(Component.translatable("message.conveyor_belt_plus.belt_created"), true);
        var playFrom = start.distToCenterSqr(player.position()) < end.distToCenterSqr(player.position()) ? start : end;
        world.playSound(null, playFrom, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(),
                SoundSource.PLAYERS, 1f, 0.5f);
        return true;
    }

    private static boolean canPlaceChute(Level world, BlockPos pos) {
        var state = world.getBlockState(pos);
        return state.canBeReplaced() || state.isAir();
    }

    /** Roll back endpoint placement and restore reserved inventory slots without spawning drops. */
    private static void restoreRejectedConstruction(Level world, Player player, List<ItemStack> inventory,
                                                     BlockPos start, BlockPos end,
                                                     ChutePlacementPlan plan, int startChoice, int endChoice,
                                                     BlockState oldStart, BlockState oldEnd) {
        if (startChoice >= 0) world.setBlockAndUpdate(start, oldStart);
        if (endChoice >= 0) world.setBlockAndUpdate(end, oldEnd);
        plan.refund(inventory);
        player.getInventory().setChanged();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        var gray = net.minecraft.ChatFormatting.GRAY;
        tooltip.add(Component.translatable("tooltip.conveyor_belt_plus.belt.summary").withStyle(gray));
        tooltip.add(Component.translatable("item.conveyor_belt_plus.belt.speed", BeltTiers.speed(beltTier))
                .withStyle(net.minecraft.ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.conveyor_belt_plus.belt.connect").withStyle(gray));
        if (stack.has(ComponentContent.BELT_START.get()))
            tooltip.add(Component.translatable("tooltip.conveyor_belt_plus.belt.start",
                    stack.get(ComponentContent.BELT_START.get()).toShortString()).withStyle(net.minecraft.ChatFormatting.YELLOW));
        if (stack.has(ComponentContent.MIDPOINTS.get()))
            tooltip.add(Component.translatable("tooltip.conveyor_belt_plus.belt.supports",
                    stack.get(ComponentContent.MIDPOINTS.get()).size()).withStyle(gray));
        if (Screen.hasControlDown()) {
            tooltip.add(Component.translatable("tooltip.conveyor_belt_plus.belt.interfaces").withStyle(gray));
            tooltip.add(Component.translatable("tooltip.conveyor_belt_plus.belt.reset").withStyle(gray));
            tooltip.add(Component.translatable("tooltip.conveyor_belt_plus.belt.pickup").withStyle(gray));
            tooltip.add(Component.translatable("tooltip.conveyor_belt_plus.belt.insert").withStyle(gray));
            if (beltTier > 1) tooltip.add(Component.translatable("tooltip.conveyor_belt_plus.belt.upgrade")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW));
        } else tooltip.add(Component.translatable("message.conveyor_belt_plus.show_extra")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltip, type);
    }

}
