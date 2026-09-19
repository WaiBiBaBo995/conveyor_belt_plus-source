package pureneko.conveyor_belt_plus;

import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.registry.ItemContent;
import pureneko.conveyor_belt_plus.registry.BlockContent;
import pureneko.conveyor_belt_plus.registry.ComponentContent;

import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pureneko.conveyor_belt_plus.blocks.*;
import pureneko.conveyor_belt_plus.compat.rts.RtsBeltDrafts;
import pureneko.conveyor_belt_plus.network.RtsNetworking;

@GameTestHolder(ConveyorBeltPlus.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConveyorCompatTests {
    @GameTest(templateName = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void detachedDraftConservation(TestContext ctx) {
        var world = ctx.getWorld();
        var player = ctx.createMockPlayer(GameMode.SURVIVAL);
        var start = ctx.getAbsolutePos(new BlockPos(2, 2, 5));
        var end = ctx.getAbsolutePos(new BlockPos(12, 2, 5));
        world.setBlockState(start, BlockContent.CHUTE_BLOCK.get().getDefaultState().with(HorizontalFacingBlock.FACING, Direction.EAST));
        world.setBlockState(end, BlockContent.CHUTE_BLOCK.get().getDefaultState().with(HorizontalFacingBlock.FACING, Direction.WEST));
        var first = new ItemStack(ItemContent.ADVANCED_BELT.get());
        player.setStackInHand(Hand.MAIN_HAND, first);
        var begin = new ItemUsageContext(player, Hand.MAIN_HAND, new BlockHitResult(start.toCenterPos(), Direction.EAST, start, false));
        RtsBeltDrafts.use(player, first, () -> first.getItem().useOnBlock(begin));
        ctx.assertTrue(first.getCount() == 1 && !first.contains(ComponentContent.BELT_START.get()), "selection is not consumed or leaked into storage");
        var second = new ItemStack(ItemContent.ADVANCED_BELT.get());
        player.setStackInHand(Hand.MAIN_HAND, second);
        var finish = new ItemUsageContext(player, Hand.MAIN_HAND, new BlockHitResult(end.toCenterPos(), Direction.WEST, end, false));
        RtsBeltDrafts.use(player, second, () -> second.getItem().useOnBlock(finish));
        var chute = (ChuteBlockEntity) world.getBlockEntity(start);
        ctx.assertTrue(chute.getTarget().equals(end) && chute.getBeltTier() == 2 && second.isEmpty(), "a fresh storage stack finishes the draft, consuming exactly one belt");
        var third = new ItemStack(ItemContent.ADVANCED_BELT.get());
        RtsBeltDrafts.use(player, third, () -> {
            ctx.assertFalse(third.contains(ComponentContent.BELT_START.get()), "completed draft cleared");
            return ActionResult.FAIL;
        });
        RtsBeltDrafts.reset(player);
        ctx.complete();
    }

    @GameTest(templateName = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void draftIsolationAndCancellation(TestContext ctx) {
        var player = ctx.createMockPlayer(GameMode.SURVIVAL);
        var other = ctx.createMockPlayer(GameMode.SURVIVAL);
        var start = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        var stack = new ItemStack(ItemContent.BELT.get(), 4);
        RtsBeltDrafts.use(player, stack, () -> {
            stack.set(ComponentContent.BELT_START.get(), start);
            stack.set(ComponentContent.BELT_DIR.get(), Direction.EAST);
            return ActionResult.SUCCESS;
        });
        RtsBeltDrafts.use(other, stack, () -> {
            ctx.assertFalse(stack.contains(ComponentContent.BELT_START.get()), "players do not share drafts");
            return ActionResult.FAIL;
        });
        var higher = new ItemStack(ItemContent.ULTIMATE_BELT.get());
        RtsBeltDrafts.use(player, higher, () -> {
            ctx.assertFalse(higher.contains(ComponentContent.BELT_START.get()), "different tiers do not reuse endpoints");
            higher.set(ComponentContent.BELT_START.get(), start);
            return ActionResult.SUCCESS;
        });
        RtsBeltDrafts.reset(player);
        RtsBeltDrafts.use(player, higher, () -> {
            ctx.assertFalse(higher.contains(ComponentContent.BELT_START.get()), "explicit cancel removes draft");
            return ActionResult.FAIL;
        });
        // Existing ordinary hand selection must survive a temporary RTS invocation.
        stack.set(ComponentContent.BELT_START.get(), start);
        try { RtsBeltDrafts.use(player, stack, () -> { stack.decrement(1); throw new IllegalStateException("test"); }); }
        catch (IllegalStateException expected) { }
        ctx.assertTrue(stack.getCount() == 3 && start.equals(stack.get(ComponentContent.BELT_START.get())), "finally restores only selection, never restores consumed count");
        RtsBeltDrafts.reset(player); RtsBeltDrafts.reset(other);
        ctx.complete();
    }

    @GameTest(templateName = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void compatPayloadAndDisplaySnapshots(TestContext ctx) {
        var buf = new RegistryByteBuf(io.netty.buffer.Unpooled.buffer(), ctx.getWorld().getRegistryManager());
        try {
            var request = new RtsNetworking.Take(BlockPos.ORIGIN, Direction.EAST, 789, .5f, new Vec3d(0, 8, 0), new Vec3d(0, -1, 0));
            RtsNetworking.Take.CODEC.encode(buf, request);
            ctx.assertTrue(buf.readableBytes() <= 72, "remote pickup is bounded fixed fields, no item NBT or paths");
            ctx.assertTrue(RtsNetworking.Take.CODEC.decode(buf).equals(request), "RTS pickup round trip");
            var stack = new ItemStack(ItemContent.BELT.get());
            stack.set(ComponentContent.BELT_START.get(), new BlockPos(1, 2, 3));
            stack.set(ComponentContent.BELT_DIR.get(), Direction.EAST);
            RtsNetworking.Draft.CODEC.encode(buf, new RtsNetworking.Draft(stack));
            ctx.assertTrue(ItemStack.areEqual(stack, RtsNetworking.Draft.CODEC.decode(buf).stack()), "draft reply preserves endpoints");
        } finally { buf.release(); }
        var pos = ctx.getAbsolutePos(new BlockPos(5, 2, 5));
        ctx.getWorld().setBlockState(pos, BlockContent.SPLITTER.get().getDefaultState());
        var splitter = (ConveyorSplitterBlockEntity) ctx.getWorld().getBlockEntity(pos);
        splitter.connectIncoming(Direction.WEST, pos.west());
        ctx.assertTrue(splitter.acceptFromBelt(new ItemStack(Items.DIAMOND, 512), Direction.WEST), "buffer filled");
        var display = splitter.getCachedItemSnapshot();
        ctx.assertTrue(display.getCount() == 512 && display.isOf(Items.DIAMOND), "Jade snapshot retains counts above vanilla stack size");
        display.setCount(1);
        ctx.assertTrue(splitter.getCachedItemCount() == 512, "tooltip cannot mutate buffer");
        var player = ctx.createMockPlayer(GameMode.SURVIVAL);
        ctx.assertTrue(BeltPickup.takeRemote(player, pos, Direction.WEST, 1, .5f, Vec3d.ZERO, new Vec3d(0, 1, 0)).taken() == 0,
                "remote pickup rejects players without a server RTS camera session");
        ctx.complete();
    }
}
