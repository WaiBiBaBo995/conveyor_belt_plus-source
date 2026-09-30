package pureneko.conveyor_belt_plus;

import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.registry.ItemContent;
import pureneko.conveyor_belt_plus.registry.BlockContent;
import pureneko.conveyor_belt_plus.registry.ComponentContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import pureneko.conveyor_belt_plus.blocks.*;
import pureneko.conveyor_belt_plus.compat.rts.RtsBeltDrafts;
import pureneko.conveyor_belt_plus.network.RtsNetworking;
import pureneko.conveyor_belt_plus.network.RtsNetworking.Take;

@GameTestHolder(ConveyorBeltPlus.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConveyorCompatTests {
    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void detachedDraftConservation(GameTestHelper ctx) {
        var world = ctx.getLevel();
        var player = ctx.makeMockSurvivalPlayer();
        var start = ctx.absolutePos(new BlockPos(2, 2, 5));
        var end = ctx.absolutePos(new BlockPos(12, 2, 5));
        world.setBlockAndUpdate(start, BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        world.setBlockAndUpdate(end, BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        var first = new ItemStack(ItemContent.ADVANCED_BELT.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, first);
        var begin = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(start.getCenter(), Direction.EAST, start, false));
        RtsBeltDrafts.use(player, first, () -> first.getItem().useOn(begin));
        ctx.assertTrue(first.getCount() == 1 && !ComponentContent.BELT_START.contains(first), "selection is not consumed or leaked into storage");
        var second = new ItemStack(ItemContent.ADVANCED_BELT.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, second);
        var finish = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(end.getCenter(), Direction.WEST, end, false));
        RtsBeltDrafts.use(player, second, () -> second.getItem().useOn(finish));
        var chute = (ChuteBlockEntity) world.getBlockEntity(start);
        ctx.assertTrue(chute.getTarget().equals(end) && chute.getBeltTier() == 2 && second.isEmpty(), "a fresh storage stack finishes the draft, consuming exactly one belt");
        var third = new ItemStack(ItemContent.ADVANCED_BELT.get());
        RtsBeltDrafts.use(player, third, () -> {
            ctx.assertFalse(ComponentContent.BELT_START.contains(third), "completed draft cleared");
            return InteractionResult.FAIL;
        });
        RtsBeltDrafts.reset(player);
        ctx.succeed();
    }

    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void draftIsolationAndCancellation(GameTestHelper ctx) {
        var player = ctx.makeMockSurvivalPlayer();
        var other = ctx.makeMockSurvivalPlayer();
        var start = ctx.absolutePos(new BlockPos(2, 2, 2));
        var stack = new ItemStack(ItemContent.BELT.get(), 4);
        RtsBeltDrafts.use(player, stack, () -> {
            ComponentContent.BELT_START.set(stack, start);
            ComponentContent.BELT_DIR.set(stack, Direction.EAST);
            return InteractionResult.SUCCESS;
        });
        RtsBeltDrafts.use(other, stack, () -> {
            ctx.assertFalse(ComponentContent.BELT_START.contains(stack), "players do not share drafts");
            return InteractionResult.FAIL;
        });
        var higher = new ItemStack(ItemContent.ULTIMATE_BELT.get());
        RtsBeltDrafts.use(player, higher, () -> {
            ctx.assertFalse(ComponentContent.BELT_START.contains(higher), "different tiers do not reuse endpoints");
            ComponentContent.BELT_START.set(higher, start);
            return InteractionResult.SUCCESS;
        });
        RtsBeltDrafts.reset(player);
        RtsBeltDrafts.use(player, higher, () -> {
            ctx.assertFalse(ComponentContent.BELT_START.contains(higher), "explicit cancel removes draft");
            return InteractionResult.FAIL;
        });
        // Existing ordinary hand selection must survive a temporary RTS invocation.
        ComponentContent.BELT_START.set(stack, start);
        try { RtsBeltDrafts.use(player, stack, () -> { stack.shrink(1); throw new IllegalStateException("test"); }); }
        catch (IllegalStateException expected) { }
        ctx.assertTrue(stack.getCount() == 3 && start.equals(ComponentContent.BELT_START.get(stack)), "finally restores only selection, never restores consumed count");
        RtsBeltDrafts.reset(player); RtsBeltDrafts.reset(other);
        ctx.succeed();
    }

    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void compatPayloadAndDisplaySnapshots(GameTestHelper ctx) {
        var buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            var request = new RtsNetworking.Take(BlockPos.ZERO, Direction.EAST, 789, .5f, new Vec3(0, 8, 0), new Vec3(0, -1, 0));
            RtsNetworking.Take.CODEC.encode(buf, request);
            ctx.assertTrue(buf.readableBytes() <= 72, "remote pickup is bounded fixed fields, no item NBT or paths");
            ctx.assertTrue(RtsNetworking.Take.CODEC.decode(buf).equals(request), "RTS pickup round trip");
            var stack = new ItemStack(ItemContent.BELT.get());
            ComponentContent.BELT_START.set(stack, new BlockPos(1, 2, 3));
            ComponentContent.BELT_DIR.set(stack, Direction.EAST);
            RtsNetworking.Draft.CODEC.encode(buf, new RtsNetworking.Draft(stack));
            ctx.assertTrue(ItemStack.matches(stack, RtsNetworking.Draft.CODEC.decode(buf).stack()), "draft reply preserves endpoints");
        } finally { buf.release(); }
        var pos = ctx.absolutePos(new BlockPos(5, 2, 5));
        ctx.getLevel().setBlockAndUpdate(pos, BlockContent.SPLITTER.get().defaultBlockState());
        var splitter = (ConveyorSplitterBlockEntity) ctx.getLevel().getBlockEntity(pos);
        splitter.connectIncoming(Direction.WEST, pos.west());
        ctx.assertTrue(splitter.acceptFromBelt(new ItemStack(Items.DIAMOND, 512), Direction.WEST), "buffer filled");
        var display = splitter.getCachedItemSnapshot();
        ctx.assertTrue(display.getCount() == 512 && display.is(Items.DIAMOND), "Jade snapshot retains counts above vanilla stack size");
        display.setCount(1);
        ctx.assertTrue(splitter.getCachedItemCount() == 512, "tooltip cannot mutate buffer");
        var player = ctx.makeMockSurvivalPlayer();
        ctx.assertTrue(BeltPickup.takeRemote(player, pos, Direction.WEST, 1, .5f, Vec3.ZERO, new Vec3(0, 1, 0)).taken() == 0,
                "remote pickup rejects players without a server RTS camera session");
        ctx.succeed();
    }
}
