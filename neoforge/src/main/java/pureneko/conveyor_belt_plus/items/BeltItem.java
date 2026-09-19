package pureneko.conveyor_belt_plus.items;

import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Pair;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import pureneko.conveyor_belt_plus.registry.BlockContent;
import pureneko.conveyor_belt_plus.registry.ComponentContent;
import pureneko.conveyor_belt_plus.registry.ItemContent;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity;
import pureneko.conveyor_belt_plus.blocks.ConveyorNodeUtil;
import pureneko.conveyor_belt_plus.util.BeltTiers;
import pureneko.conveyor_belt_plus.util.ChutePlacementPlan;

import java.util.ArrayList;
import java.util.List;

/** Selects endpoints and commits a complete belt only after all checks pass. */
public class BeltItem extends Item {

    private final int beltTier;

    public BeltItem(Settings settings) {
        this(settings, BeltTiers.STANDARD);
    }

    public BeltItem(Settings settings, int beltTier) {
        super(settings);
        this.beltTier = BeltTiers.normalize(beltTier);
    }

    public int getBeltTier() {
        return beltTier;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, net.minecraft.util.Hand hand) {
        if (!world.isClient && user.isSneaking()) {
            var stack = user.getStackInHand(hand);
            stack.remove(ComponentContent.MIDPOINTS.get());
            stack.remove(ComponentContent.BELT_START.get());
            stack.remove(ComponentContent.BELT_DIR.get());
            user.sendMessage(Text.translatable("message.conveyor_belt_plus.reset"), true);
        }
        return super.use(world, user, hand);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        var world = context.getWorld();
        var stack = context.getStack();
        var player = context.getPlayer();
        if (player == null || !player.getAbilities().allowModifyWorld) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;

        if (pureneko.conveyor_belt_plus.compat.rts.RtsCompat.active(player))
            return pureneko.conveyor_belt_plus.compat.rts.RtsBeltDrafts.use(player, stack, () -> useEndpoint(context));
        return useEndpoint(context);
    }

    private ActionResult useEndpoint(ItemUsageContext context) {
        var world = context.getWorld();
        var stack = context.getStack();
        var player = context.getPlayer();

        var clickedPos = context.getBlockPos();
        var hasStart = stack.contains(ComponentContent.BELT_START.get())
                && stack.contains(ComponentContent.BELT_DIR.get());
        var clickedNode = ConveyorNodeUtil.get(world, clickedPos);

        if (clickedNode != null) {
            var port = interactionPort(context);
            var upgradePort = clickedNode instanceof ChuteBlockEntity chute ? chute.getOwnFacing() : port;
            if (player.isSneaking() && !hasStart && clickedNode.outgoingBeltTier(upgradePort) > 0) {
                if (pureneko.conveyor_belt_plus.blocks.UpgradeInteractions.belt(world, clickedPos, upgradePort, player, stack, beltTier))
                    return ActionResult.SUCCESS;
                player.sendMessage(Text.translatable("message.conveyor_belt_plus.upgrade_requires_higher"), true);
                return ActionResult.FAIL;
            }
            if (clickedNode instanceof ChuteBlockEntity chute) {
                if (chute.isUsed()) {
                    player.sendMessage(Text.translatable("message.conveyor_belt_plus.chute_used"), true);
                    return ActionResult.FAIL;
                }
                if (hasStart) {
                    return createBelt(stack.get(ComponentContent.BELT_START.get()),
                            stack.get(ComponentContent.BELT_DIR.get()), getStoredMidpoints(stack, world),
                            clickedPos, chute.getOwnFacing(), world, stack, player)
                            ? ActionResult.SUCCESS : ActionResult.FAIL;
                }
                stack.set(ComponentContent.BELT_START.get(), clickedPos);
                stack.set(ComponentContent.BELT_DIR.get(), chute.getOwnFacing());
                player.sendMessage(Text.translatable("message.conveyor_belt_plus.started"), true);
                return ActionResult.SUCCESS;
            }

            if (hasStart) {
                if (!clickedNode.hasInputPort(port) || clickedNode.isPortUsed(port)) {
                    player.sendMessage(Text.translatable("message.conveyor_belt_plus.port_used"), true);
                    return ActionResult.FAIL;
                }
                return createBelt(stack.get(ComponentContent.BELT_START.get()),
                        stack.get(ComponentContent.BELT_DIR.get()), getStoredMidpoints(stack, world),
                        clickedPos, port, world, stack, player)
                        ? ActionResult.SUCCESS : ActionResult.FAIL;
            }

            if (!clickedNode.hasOutputPort(port) || clickedNode.isPortUsed(port)) {
                player.sendMessage(Text.translatable("message.conveyor_belt_plus.port_used"), true);
                return ActionResult.FAIL;
            }
            stack.set(ComponentContent.BELT_START.get(), clickedPos);
            stack.set(ComponentContent.BELT_DIR.get(), port);
            player.sendMessage(Text.translatable("message.conveyor_belt_plus.started"), true);
            return ActionResult.SUCCESS;
        }

        BlockState clickedState = world.getBlockState(clickedPos);
        if (hasStart && clickedState.isOf(BlockContent.CONVEYOR_SUPPORT_BLOCK.get())) {
            var points = new ArrayList<BlockPos>();
            getStoredMidpoints(stack, world).stream().map(Pair::getLeft).forEach(points::add);
            if (points.contains(clickedPos)) {
                player.sendMessage(Text.translatable("message.conveyor_belt_plus.midpoint_duplicate"), true);
                return ActionResult.FAIL;
            }
            points.add(clickedPos);
            if (points.size() > 64 && pureneko.conveyor_belt_plus.compat.rts.RtsCompat.active(player)) return ActionResult.FAIL;
            stack.set(ComponentContent.MIDPOINTS.get(), points);
            player.sendMessage(Text.translatable("message.conveyor_belt_plus.midpoint_added"), true);
            return ActionResult.SUCCESS;
        }

        var endPos = clickedPos.add(context.getSide().getVector());
        var endDir = context.getSide();
        if (endDir.getAxis() == Direction.Axis.Y)
            endDir = context.getHorizontalPlayerFacing();

        var candidateState = world.getBlockState(endPos);
        if (candidateState.isReplaceable() || candidateState.isAir()) {
            if (hasStart) {
                return createBelt(stack.get(ComponentContent.BELT_START.get()),
                        stack.get(ComponentContent.BELT_DIR.get()), getStoredMidpoints(stack, world),
                        endPos, endDir, world, stack, player)
                        ? ActionResult.SUCCESS : ActionResult.FAIL;
            }

            if (context.getSide().getAxis() != Direction.Axis.Y)
                endDir = endDir.getOpposite();
            stack.set(ComponentContent.BELT_START.get(), endPos);
            stack.set(ComponentContent.BELT_DIR.get(), endDir.getOpposite());
            player.sendMessage(Text.translatable("message.conveyor_belt_plus.started"), true);
        }
        return ActionResult.SUCCESS;
    }

    private static Direction interactionPort(ItemUsageContext context) {
        return context.getSide().getAxis().isHorizontal()
                ? context.getSide() : context.getHorizontalPlayerFacing().getOpposite();
    }

    public static List<Pair<BlockPos, Direction>> getStoredMidpoints(ItemStack stack, World world) {
        var result = new ArrayList<Pair<BlockPos, Direction>>();
        if (!stack.contains(ComponentContent.MIDPOINTS.get())) return result;
        for (var point : stack.get(ComponentContent.MIDPOINTS.get())) {
            var state = world.getBlockState(point);
            if (state.isOf(BlockContent.CONVEYOR_SUPPORT_BLOCK.get()))
                result.add(new Pair<>(point, state.get(HorizontalFacingBlock.FACING)));
        }
        return result;
    }

    private boolean createBelt(BlockPos start, Direction startDir, List<Pair<BlockPos, Direction>> supports,
                               BlockPos end, Direction endDir, World world, ItemStack beltStack,
                               PlayerEntity player) {
        if (start == null || end == null || start.equals(end)) {
            player.sendMessage(Text.translatable("message.conveyor_belt_plus.invalid_path"), true);
            return false;
        }

        var startNode = ConveyorNodeUtil.get(world, start);
        var endNode = ConveyorNodeUtil.get(world, end);
        if (startNode != null && (!startNode.hasOutputPort(startDir) || startNode.isPortUsed(startDir))) {
            player.sendMessage(Text.translatable("message.conveyor_belt_plus.port_used"), true);
            return false;
        }
        if (endNode != null && (!endNode.hasInputPort(endDir) || endNode.isPortUsed(endDir))) {
            player.sendMessage(Text.translatable("message.conveyor_belt_plus.port_used"), true);
            return false;
        }

        if ((startNode == null && !canPlaceChute(world, start))
                || (endNode == null && !canPlaceChute(world, end))) {
            player.sendMessage(Text.translatable("message.conveyor_belt_plus.chute_position_blocked"), true);
            return false;
        }

        var chutesNeeded = (startNode == null ? 1 : 0) + (endNode == null ? 1 : 0);
        if (pureneko.conveyor_belt_plus.compat.rts.RtsCompat.active(player)) {
            if (!pureneko.conveyor_belt_plus.compat.rts.RtsCompat.canBuild(player, start, startNode == null)
                    || !pureneko.conveyor_belt_plus.compat.rts.RtsCompat.canBuild(player, end, endNode == null)
                    || supports.stream().anyMatch(point -> !pureneko.conveyor_belt_plus.compat.rts.RtsCompat.canBuild(player, point.getLeft(), false))) {
                player.sendMessage(Text.translatable("message.conveyor_belt_plus.rts_forbidden"), true);
                return false;
            }
        }
        var inventory = ChutePlacementPlan.orderedInventory(player);
        var plan = ChutePlacementPlan.select(inventory,
                chutesNeeded, player.isCreative(), new ItemStack(ItemContent.CHUTE.get()));
        if (plan == null || !plan.consume(inventory)) {
            player.sendMessage(Text.translatable("message.conveyor_belt_plus.not_enough_chutes"), true);
            return false;
        }
        if (!player.isCreative()) player.getInventory().markDirty();

        int startChoice = startNode == null ? 0 : -1;
        int endChoice = endNode == null ? (startNode == null ? 1 : 0) : -1;
        var oldStart = world.getBlockState(start);
        var oldEnd = world.getBlockState(end);
        boolean placedStart = false;
        boolean placedEnd = false;
        if (startChoice >= 0) {
            placedStart = world.setBlockState(start, plan.state(startChoice, startDir));
        }
        if (endChoice >= 0 && (startChoice < 0 || placedStart)) {
            placedEnd = world.setBlockState(end, plan.state(endChoice, endDir));
        }

        startNode = ConveyorNodeUtil.get(world, start);
        endNode = ConveyorNodeUtil.get(world, end);
        if ((startChoice >= 0 && !placedStart) || (endChoice >= 0 && !placedEnd)
                || startNode == null || endNode == null) {
            // A failed block placement must leave neither a free endpoint nor a missing inventory item.
            if (placedStart) world.setBlockState(start, oldStart);
            if (placedEnd) world.setBlockState(end, oldEnd);
            if (!player.isCreative()) {
                for (int i = 0; i < plan.size(); i++) {
                    var refund = plan.item(i);
                    player.getInventory().insertStack(refund);
                    if (!refund.isEmpty()) spawnDrop(world, start, refund);
                }
                player.getInventory().markDirty();
            }
            player.sendMessage(Text.translatable("message.conveyor_belt_plus.invalid_path"), true);
            return false;
        }
        if (!startNode.connectOutgoing(startDir, end, endDir,
                supports.stream().map(Pair::getLeft).toList(), beltTier)) {
            dropRejectedConstruction(world, player, beltStack, start, end, plan, startChoice, endChoice,
                    oldStart, oldEnd);
            player.sendMessage(Text.translatable("message.conveyor_belt_plus.turn_too_sharp"), true);
            return false;
        }
        endNode.connectIncoming(endDir, start);

        beltStack.remove(ComponentContent.MIDPOINTS.get());
        beltStack.remove(ComponentContent.BELT_START.get());
        beltStack.remove(ComponentContent.BELT_DIR.get());
        if (!player.isCreative()) {
            beltStack.decrement(1);
            player.getInventory().markDirty();
        }

        player.sendMessage(Text.translatable("message.conveyor_belt_plus.belt_created"), true);
        var playFrom = start.getSquaredDistance(player.getPos()) < end.getSquaredDistance(player.getPos()) ? start : end;
        world.playSound(null, playFrom, SoundEvents.ENTITY_BREEZE_WIND_BURST.value(),
                SoundCategory.PLAYERS, 1f, 0.5f);
        return true;
    }

    private static boolean canPlaceChute(World world, BlockPos pos) {
        var state = world.getBlockState(pos);
        return state.isReplaceable() || state.isAir();
    }

    /** The route is formed only after the final path check. Invalid formation drops everything back into the world. */
    private static void dropRejectedConstruction(World world, PlayerEntity player, ItemStack beltStack,
                                                  BlockPos start, BlockPos end,
                                                  ChutePlacementPlan plan, int startChoice, int endChoice,
                                                  BlockState oldStart, BlockState oldEnd) {
        if (startChoice >= 0) world.setBlockState(start, oldStart);
        if (endChoice >= 0) world.setBlockState(end, oldEnd);
        if (player.isCreative()) {
            return;
        }

        var beltItem = beltStack.getItem();
        beltStack.decrement(1);
        spawnDrop(world, start, new ItemStack(beltItem));
        if (startChoice >= 0) spawnDrop(world, start, plan.item(startChoice));
        if (endChoice >= 0) spawnDrop(world, end, plan.item(endChoice));
        player.getInventory().markDirty();
    }

    private static void spawnDrop(World world, BlockPos pos, ItemStack stack) {
        var center = pos.toCenterPos();
        world.spawnEntity(new ItemEntity(world, center.x, center.y, center.z, stack));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        var gray = net.minecraft.util.Formatting.GRAY;
        tooltip.add(Text.translatable("tooltip.conveyor_belt_plus.belt.summary").formatted(gray));
        tooltip.add(Text.translatable("item.conveyor_belt_plus.belt.speed", BeltTiers.speed(beltTier))
                .formatted(net.minecraft.util.Formatting.AQUA));
        tooltip.add(Text.translatable("tooltip.conveyor_belt_plus.belt.connect").formatted(gray));
        if (stack.contains(ComponentContent.BELT_START.get()))
            tooltip.add(Text.translatable("tooltip.conveyor_belt_plus.belt.start",
                    stack.get(ComponentContent.BELT_START.get()).toShortString()).formatted(net.minecraft.util.Formatting.YELLOW));
        if (stack.contains(ComponentContent.MIDPOINTS.get()))
            tooltip.add(Text.translatable("tooltip.conveyor_belt_plus.belt.supports",
                    stack.get(ComponentContent.MIDPOINTS.get()).size()).formatted(gray));
        if (Screen.hasControlDown()) {
            tooltip.add(Text.translatable("tooltip.conveyor_belt_plus.belt.interfaces").formatted(gray));
            tooltip.add(Text.translatable("tooltip.conveyor_belt_plus.belt.reset").formatted(gray));
            tooltip.add(Text.translatable("tooltip.conveyor_belt_plus.belt.pickup").formatted(gray));
            if (beltTier > 1) tooltip.add(Text.translatable("tooltip.conveyor_belt_plus.belt.upgrade")
                    .formatted(net.minecraft.util.Formatting.YELLOW));
        } else tooltip.add(Text.translatable("message.conveyor_belt_plus.show_extra")
                .formatted(net.minecraft.util.Formatting.DARK_GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }

}
