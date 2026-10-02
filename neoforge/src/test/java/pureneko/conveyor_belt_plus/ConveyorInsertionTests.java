package pureneko.conveyor_belt_plus;

import java.util.Deque;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pureneko.conveyor_belt_plus.blocks.*;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity.BeltItem;
import pureneko.conveyor_belt_plus.network.InsertionNetworking.Put;
import pureneko.conveyor_belt_plus.network.InsertionNetworking.RemotePut;
import pureneko.conveyor_belt_plus.registry.*;
import pureneko.conveyor_belt_plus.util.*;

@GameTestHolder(ConveyorBeltPlus.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConveyorInsertionTests {
    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void rejectedPlacementKeepsInventory(GameTestHelper ctx) {
        var world = ctx.getLevel();
        for (int existing = 0; existing <= 2; existing++) {
            var start = ctx.absolutePos(new BlockPos(2, 2, 3 + existing * 4));
            var end = start.east(10);
            if (existing >= 1) world.setBlockAndUpdate(start, BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
            if (existing == 2) world.setBlockAndUpdate(end, BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
            var player = ctx.makeMockPlayer(GameType.SURVIVAL);
            for (int slot = 0; slot < player.getInventory().items.size(); slot++) player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
            var belt = new ItemStack(ItemContent.BELT.get());
            belt.set(DataComponents.CUSTOM_NAME, Component.literal("Reserved belt"));
            belt.set(ComponentContent.BELT_START.get(), start);
            belt.set(ComponentContent.BELT_DIR.get(), Direction.WEST);
            player.setItemInHand(InteractionHand.MAIN_HAND, belt);
            var offhand = new ItemStack(ItemContent.FLUID_CHUTE.get(), 2 - existing);
            if (!offhand.isEmpty()) offhand.set(DataComponents.CUSTOM_NAME, Component.literal("Reserved interface"));
            player.setItemInHand(InteractionHand.OFF_HAND, offhand);
            ctx.assertTrue(ChuteBlockEntity.BeltData.create(world, start, Direction.WEST, end, Direction.WEST, java.util.List.of()) == null,
                    "test construction has an invalid sharp reversal");
            var use = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(end.east().getCenter(), Direction.WEST, end.east(), false));
            if (existing == 2) use = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(end.getCenter(), Direction.WEST, end, false));
            ctx.assertFalse(ItemContent.BELT.get().useOn(use).consumesAction(), "invalid construction rejected");
            ctx.assertTrue(player.getMainHandItem() == belt && belt.getCount() == 1 && belt.has(ComponentContent.BELT_START.get())
                    && belt.getHoverName().getString().equals("Reserved belt"), "belt and retry draft remain in the original hand");
            ctx.assertTrue(player.getOffhandItem().getCount() == 2 - existing, "reserved interfaces return to offhand even with full inventory");
            if (existing < 2) ctx.assertTrue(player.getOffhandItem() == offhand && offhand.is(ItemContent.FLUID_CHUTE.get())
                    && offhand.getHoverName().getString().equals("Reserved interface"), "refund retains interface variant and data");
            ctx.assertTrue((existing >= 1 || world.isEmptyBlock(start)) && (existing == 2 || world.isEmptyBlock(end)), "temporary endpoints rolled back");
            ctx.assertTrue(world.getEntitiesOfClass(ItemEntity.class, new AABB(start.getCenter(), end.getCenter()).inflate(1), e -> true).isEmpty(), "failure spawns no construction drops");
            if (existing >= 1) world.setBlockAndUpdate(start, BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
            belt.set(ComponentContent.BELT_DIR.get(), Direction.EAST);
            ctx.assertTrue(ItemContent.BELT.get().useOn(use).consumesAction(), "same held belt can retry successfully");
            ctx.assertTrue(player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty(), "successful retry consumes exactly one belt and required interfaces");
        }
        ctx.succeed();
    }

    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void handInsertionValidationAndConservation(GameTestHelper ctx) {
        var world = ctx.getLevel();
        var start = ctx.absolutePos(new BlockPos(2, 2, 5));
        var end = start.east(10);
        world.setBlockAndUpdate(start, BlockContent.UNIVERSAL_CHUTE.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        world.setBlockAndUpdate(end, BlockContent.UNIVERSAL_CHUTE.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        var chute = (ChuteBlockEntity) world.getBlockEntity(start);
        ctx.assertTrue(chute.connectOutgoing(Direction.EAST, end, Direction.WEST, java.util.List.of(), 1), "empty belt ready");
        var player = ctx.makeMockPlayer(GameType.SURVIVAL);
        var point = SplineUtil.getPositionOnSpline(chute.getBeltData(), .5f);
        var offered = new ItemStack(Items.IRON_INGOT, 37);
        offered.set(DataComponents.CUSTOM_NAME, Component.literal("Inserted contents"));
        player.setItemInHand(InteractionHand.MAIN_HAND, offered);
        lookAt(player, point, 8);
        ctx.assertFalse(BeltInsertion.put(player, start, Direction.EAST), "out of reach rejected");
        lookAt(player, point, 2);
        ctx.assertFalse(BeltInsertion.put(player, start, Direction.WEST), "wrong output rejected");
        ctx.assertFalse(BeltInsertion.put(player, start.offset(100000, 0, 0), Direction.EAST), "unloaded owner rejected");
        var wall = BlockPos.containing(player.getEyePosition().lerp(point, .5));
        world.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        ctx.assertFalse(BeltInsertion.put(player, start, Direction.EAST), "wall blocks insertion");
        world.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock> deny = event -> {
            if (event.getEntity() == player) event.setCanceled(true);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(deny);
        try { ctx.assertFalse(BeltInsertion.put(player, start, Direction.EAST), "protection event can veto insertion"); }
        finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(deny); }
        player.getAbilities().mayBuild = false;
        ctx.assertFalse(BeltInsertion.put(player, start, Direction.EAST), "adventure restrictions respected");
        player.getAbilities().mayBuild = true;
        ctx.assertTrue(offered.getCount() == 37 && chute.pickupAccess(Direction.EAST).items().isEmpty(), "all refusals conserve inventory");
        ctx.assertTrue(BeltInsertion.put(player, start, Direction.EAST), "right click inserts into an empty belt");
        var queue = chute.pickupAccess(Direction.EAST).items();
        var inserted = queue.getFirst();
        ctx.assertTrue(player.getMainHandItem().isEmpty() && inserted.stack.getCount() == 37
                && inserted.stack.getHoverName().getString().equals("Inserted contents") && Math.abs(inserted.progress - .5) < .04,
                "one whole held stack and components enter at the aimed location");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLD_INGOT, 9));
        ctx.assertFalse(BeltInsertion.put(player, start, Direction.EAST), "occupied spacing rejects insertion");
        ctx.assertTrue(player.getMainHandItem().getCount() == 9 && queue.size() == 1, "congestion neither consumes nor duplicates");
        lookAt(player, SplineUtil.getPositionOnSpline(chute.getBeltData(), .25f), 2);
        ctx.assertTrue(BeltInsertion.put(player, start, Direction.EAST), "insert upstream of an existing packet");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 3));
        lookAt(player, SplineUtil.getPositionOnSpline(chute.getBeltData(), .75f), 2);
        ctx.assertTrue(BeltInsertion.put(player, start, Direction.EAST), "insert downstream of an existing packet");
        var fluid = new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 16000);
        var parcel = FluidPackets.create(fluid);
        player.setItemInHand(InteractionHand.MAIN_HAND, parcel);
        lookAt(player, SplineUtil.getPositionOnSpline(chute.getBeltData(), .1f), 2);
        ctx.assertTrue(BeltInsertion.put(player, start, Direction.EAST), "fluid_packet can reenter shared belt transport");
        ctx.assertTrue(player.getMainHandItem().isEmpty() && FluidPackets.amount(queue.getFirst().stack) == 16000
                && queue.getFirst().count() == 16000, "packet retains its fluid transport semantics and exact mB");
        float previous = -1;
        var ids = new java.util.HashSet<Long>();
        for (var packet : queue) {
            ctx.assertTrue(packet.progress > previous && ids.add(packet.id), "packets stay ordered with unique persistent IDs");
            previous = packet.progress;
        }
        var saved = chute.saveWithFullMetadata(world.registryAccess());
        chute.loadWithComponents(saved, world.registryAccess());
        ctx.assertTrue(chute.pickupAccess(Direction.EAST).items().size() == 4
                && FluidPackets.amount(chute.pickupAccess(Direction.EAST).items().getFirst().stack) == 16000, "inserted contents survive save/load");
        ctx.succeed();
    }

    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void splitterInsertionAndPacketBounds(GameTestHelper ctx) {
        var world = ctx.getLevel();
        var start = ctx.absolutePos(new BlockPos(2, 2, 5));
        var end = start.east(10);
        world.setBlockAndUpdate(start, BlockContent.SPLITTER.get().defaultBlockState());
        world.setBlockAndUpdate(end, BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        var splitter = (ConveyorSplitterBlockEntity) world.getBlockEntity(start);
        ctx.assertTrue(splitter.connectOutgoing(Direction.EAST, end, Direction.WEST, java.util.List.of(), 3), "splitter output exists");
        var stack = new ItemStack(Items.REDSTONE, 12);
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -.1f, 1.1f})
            ctx.assertFalse(splitter.insertOnBelt(Direction.EAST, invalid, stack), "invalid progress rejected");
        ctx.assertFalse(splitter.insertOnBelt(Direction.NORTH, .5f, stack), "no route on wrong splitter port");
        ctx.assertTrue(stack.getCount() == 12 && splitter.insertOnBelt(Direction.EAST, .5f, stack)
                && stack.isEmpty() && splitter.pickupAccess(Direction.EAST).items().getFirst().stack.getCount() == 12,
                "splitter insertion conserves offered stack");
        var queue = splitter.pickupAccess(Direction.EAST).items();
        queue.clear();
        for (int i = 0; i < 64; i++) queue.add(new ChuteBlockEntity.BeltItem(1, i, new ItemStack(Items.STICK)));
        ctx.assertFalse(splitter.insertOnBelt(Direction.EAST, 0, new ItemStack(Items.DIAMOND)), "64-packet cap cannot be bypassed");
        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), world.registryAccess());
        try {
            var request = new pureneko.conveyor_belt_plus.network.InsertionNetworking.Put(start, Direction.EAST);
            pureneko.conveyor_belt_plus.network.InsertionNetworking.Put.CODEC.encode(buf, request);
            ctx.assertTrue(buf.readableBytes() <= 10 && pureneko.conveyor_belt_plus.network.InsertionNetworking.Put.CODEC.decode(buf).equals(request), "normal request contains only route identity");
            var packet = FluidPackets.create(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 64000));
            var remote = new pureneko.conveyor_belt_plus.network.InsertionNetworking.RemotePut(start, Direction.EAST, start.getCenter(), new Vec3(1, 0, 0), packet);
            pureneko.conveyor_belt_plus.network.InsertionNetworking.RemotePut.CODEC.encode(buf, remote);
            var decoded = pureneko.conveyor_belt_plus.network.InsertionNetworking.RemotePut.CODEC.decode(buf);
            ctx.assertTrue(decoded.owner().equals(start) && FluidPackets.sameContents(decoded.selected(), packet)
                    && FluidPackets.amount(decoded.selected()) == 64000, "RTS selector codec preserves fluid identity and amount");
        } finally { buf.release(); }
        ctx.succeed();
    }

    private static void lookAt(net.minecraft.world.entity.player.Player player, Vec3 point, double distance) {
        player.setPosRaw(point.x, point.y, point.z + distance);
        var offset = point.subtract(player.getEyePosition());
        player.setYRot((float) Math.toDegrees(Math.atan2(-offset.x, offset.z)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(offset.y, Math.sqrt(offset.x * offset.x + offset.z * offset.z))));
    }
}
