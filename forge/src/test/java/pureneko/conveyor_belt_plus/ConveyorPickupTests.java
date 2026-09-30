package pureneko.conveyor_belt_plus;

import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.registry.ItemContent;
import pureneko.conveyor_belt_plus.registry.BlockContent;
import java.util.ArrayDeque;
import java.util.Properties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import pureneko.conveyor_belt_plus.blocks.*;
import pureneko.conveyor_belt_plus.blocks.BeltPickup.Access;
import pureneko.conveyor_belt_plus.blocks.BeltPickup.Result;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity.BeltItem;
import pureneko.conveyor_belt_plus.config.ConveyorConfig;
import pureneko.conveyor_belt_plus.network.PickupNetworking.Request;
import pureneko.conveyor_belt_plus.network.PickupNetworking.Taken;
import pureneko.conveyor_belt_plus.util.BeltTransport;
import pureneko.conveyor_belt_plus.util.BeltTransport.Step;
import pureneko.conveyor_belt_plus.util.SplineUtil;

@GameTestHolder(ConveyorBeltPlus.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConveyorPickupTests {
    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void sneakUpgradeItemEntry(GameTestHelper context) {
        var world = context.getLevel();
        var pos = context.absolutePos(new BlockPos(2, 2, 2));
        world.setBlockAndUpdate(pos, BlockContent.CHUTE_BLOCK.get().defaultBlockState());
        var player = context.makeMockSurvivalPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemContent.ADVANCED_CHUTE.get(), 2));
        context.assertFalse(UpgradeInteractions.chute(world, pos, player, player.getMainHandItem(),
                (ChuteBlock) BlockContent.ADVANCED_CHUTE.get()), "standing player cannot upgrade");
        player.setShiftKeyDown(true);
        var use = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(pos.getCenter(), Direction.NORTH, pos, false));
        context.assertTrue(ItemContent.ADVANCED_CHUTE.get().useOn(use).consumesAction(), "sneaking item path upgrades even when vanilla bypasses block use");
        context.assertTrue(world.getBlockState(pos).is(BlockContent.ADVANCED_CHUTE.get()) && player.getMainHandItem().getCount() == 1, "one upgrade and consumption");
        var target = context.absolutePos(new BlockPos(2, 2, 12));
        world.setBlockAndUpdate(target, BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        // Use a south-facing source for a straight test route.
        world.setBlockAndUpdate(pos, BlockContent.ADVANCED_CHUTE.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        var chute = (ChuteBlockEntity) world.getBlockEntity(pos);
        context.assertTrue(chute.connectOutgoing(Direction.SOUTH, target, Direction.NORTH, java.util.List.of(), 1), "belt exists");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemContent.ULTIMATE_BELT.get(), 2));
        player.setShiftKeyDown(false);
        ItemContent.ULTIMATE_BELT.get().useOn(use);
        context.assertTrue(chute.getBeltTier() == 1 && player.getMainHandItem().getCount() == 2, "ordinary right click does not upgrade belt");
        player.setShiftKeyDown(true);
        context.assertTrue(ItemContent.ULTIMATE_BELT.get().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(pos.getCenter(), Direction.SOUTH, pos, false))).consumesAction(), "sneak right click belt entry");
        context.assertTrue(chute.getBeltTier() == 3 && player.getMainHandItem().getCount() == 1, "belt upgraded once");
        context.succeed();
    }

    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void boundedSplitterBuffer(GameTestHelper context) {
        var world = context.getLevel();
        var pos = context.absolutePos(new BlockPos(5, 2, 5));
        world.setBlockAndUpdate(pos, BlockContent.SPLITTER.get().defaultBlockState());
        var splitter = (ConveyorSplitterBlockEntity) world.getBlockEntity(pos);
        splitter.connectIncoming(Direction.WEST, pos.west(4));
        context.assertTrue(ConveyorConfig.splitterBufferItems() == 512, "default is 512 individual items");
        context.assertFalse(splitter.acceptFromBelt(new ItemStack(Items.IRON_INGOT, 513), Direction.WEST), "atomic insert cannot exceed buffer");
        var packets = new java.util.ArrayDeque<ChuteBlockEntity.BeltItem>();
        var item = new ChuteBlockEntity.BeltItem(1, 1, new ItemStack(Items.IRON_INGOT, 1024));
        packets.add(item);
        var step = BeltTransport.tickWithMotion(packets, 10, 1,
                packet -> ConveyorNodeUtil.accept(world, pos, Direction.WEST, packet.stack));
        context.assertTrue(step.changed() && item.stack.getCount() == 512 && packets.size() == 1
                && splitter.getCachedItemCount() == 512, "large batch partially inserts and dirty state is propagated");
        context.assertFalse(BeltTransport.tick(packets, 10, 1,
                packet -> ConveyorNodeUtil.accept(world, pos, Direction.WEST, packet.stack)), "full buffer does not mutate incoming packet");
        context.assertFalse(splitter.acceptFromBelt(new ItemStack(Items.GOLD_INGOT), Direction.WEST), "different item type refused");
        var data = splitter.saveWithFullMetadata();
        var settings = new java.util.Properties(); settings.setProperty("splitter.buffer_items", "128");
        try {
            ConfigTestSupport.apply(settings, ignored -> {});
            splitter.load(data);
            context.assertTrue(splitter.getCachedItemCount() == 512, "shrinking capacity never deletes saved items");
            context.assertFalse(splitter.acceptFromBelt(new ItemStack(Items.IRON_INGOT), Direction.WEST), "overfull restored buffer refuses more");
        } finally { ConfigTestSupport.apply(new java.util.Properties(), ignored -> {}); }
        context.succeed();
    }

    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void pickupValidationAndConservation(GameTestHelper context) {
        var world = context.getLevel();
        var pos = context.absolutePos(new BlockPos(2, 2, 5));
        var target = context.absolutePos(new BlockPos(12, 2, 5));
        world.setBlockAndUpdate(pos, BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        world.setBlockAndUpdate(target, BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        var chute = (ChuteBlockEntity) world.getBlockEntity(pos);
        context.assertTrue(chute.connectOutgoing(Direction.EAST, target, Direction.WEST, java.util.List.of(), 1), "pickup route exists");
        var original = new ItemStack(Items.IRON_INGOT, 128);
        original.setHoverName(Component.literal("Transport components"));
        var data = chute.saveWithFullMetadata();
        var moving = new ListTag();
        moving.add(new ChuteBlockEntity.BeltItem(.5f, 123L, original).write(world.registryAccess(), "a", "b"));
        data.put("moving", moving); chute.load(data);
        var player = context.makeMockSurvivalPlayer();
        var point = SplineUtil.getPositionOnSpline(chute.getBeltData(), .5f).add(0, .06, 0);
        lookAt(player, point, 2);
        context.assertTrue(BeltPickup.take(player, pos, Direction.EAST, 999, .5f).taken() == 0, "unknown ID refused");
        context.assertTrue(BeltPickup.take(player, pos, Direction.WEST, 123, .5f).taken() == 0, "wrong output refused");
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -.1f, 1.1f, 0})
            context.assertTrue(BeltPickup.take(player, pos, Direction.EAST, 123, invalid).taken() == 0, "invalid/stale progress refused");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        context.assertTrue(BeltPickup.take(player, pos, Direction.EAST, 123, .5f).taken() == 0, "must be empty-handed");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        lookAt(player, point, 8);
        context.assertTrue(BeltPickup.take(player, pos, Direction.EAST, 123, .5f).taken() == 0, "out of reach refused");
        lookAt(player, point, 2);
        player.setYRot(player.getYRot() + 90);
        context.assertTrue(BeltPickup.take(player, pos, Direction.EAST, 123, .5f).taken() == 0, "must aim at the rendered location");
        lookAt(player, point, 2);
        var wall = BlockPos.containing(point.add(0, .5, 1));
        world.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        world.setBlockAndUpdate(wall.above(), Blocks.STONE.defaultBlockState());
        context.assertTrue(BeltPickup.take(player, pos, Direction.EAST, 123, .5f).taken() == 0, "cannot take through walls");
        world.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState()); world.setBlockAndUpdate(wall.above(), Blocks.AIR.defaultBlockState());
        player.getAbilities().mayBuild = false;
        context.assertTrue(BeltPickup.take(player, pos, Direction.EAST, 123, .5f).taken() == 0, "adventure restrictions respected");
        player.getAbilities().mayBuild = true;
        player.containerMenu = new pureneko.conveyor_belt_plus.screen.ChuteScreenHandler(77, player.getInventory(), chute);
        context.assertTrue(BeltPickup.take(player, pos, Direction.EAST, 123, .5f).taken() == 0, "cannot take while a menu is open");
        player.containerMenu = player.inventoryMenu;
        java.util.function.Consumer<net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock> deny = event -> {
            if (event.getEntity() == player) event.setCanceled(true);
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(deny);
        try {
            context.assertTrue(BeltPickup.take(player, pos, Direction.EAST, 123, .5f).taken() == 0, "protection event can cancel pickup");
        } finally { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(deny); }
        // Only the empty selected slot remains available: take 64, leave 64 on the same moving packet.
        for (int slot = 1; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        var packet = chute.getMovingItems().iterator().next();
        var result = BeltPickup.take(player, pos, Direction.EAST, 123, .5f);
        context.assertTrue(result.taken() == 64 && result.remaining() == 64 && packet == chute.getMovingItems().iterator().next()
                && packet.progress == .5f, "partial pickup preserves packet and motion");
        context.assertTrue(ItemStack.isSameItemSameTags(player.getMainHandItem(), original), "components retained");
        player.getInventory().setItem(1, player.getMainHandItem()); player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        context.assertTrue(BeltPickup.take(player, pos, Direction.EAST, 123, .5f).taken() == 64
                && !chute.getMovingItems().iterator().hasNext(), "remaining stack removed exactly once");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        context.assertTrue(BeltPickup.take(player, pos, Direction.EAST, 123, .5f).taken() == 0, "duplicate request never duplicates items");
        var creative = context.makeMockPlayer();
        for (int slot = 0; slot < 36; slot++) creative.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        var leftover = new ItemStack(Items.GOLD_INGOT, 1024);
        context.assertTrue(BeltPickup.transferToInventory(creative, leftover) == 0 && leftover.getCount() == 1024,
                "full creative inventory cannot discard conveyor items");
        context.succeed();
    }

    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void splitterPickupAndClientPackets(GameTestHelper context) {
        var world = context.getLevel();
        var pos = context.absolutePos(new BlockPos(2, 2, 5));
        var target = context.absolutePos(new BlockPos(12, 2, 5));
        world.setBlockAndUpdate(pos, BlockContent.SPLITTER.get().defaultBlockState());
        world.setBlockAndUpdate(target, BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        var splitter = (ConveyorSplitterBlockEntity) world.getBlockEntity(pos);
        splitter.connectIncoming(Direction.WEST, pos.west());
        context.assertTrue(splitter.connectOutgoing(Direction.EAST, target, Direction.WEST, java.util.List.of(), 1), "splitter pickup route");
        splitter.acceptFromBelt(new ItemStack(Items.GOLD_INGOT, 64), Direction.WEST);
        splitter.tick(world, pos, world.getBlockState(pos), splitter);
        var access = splitter.pickupAccess(Direction.EAST);
        var packet = access.items().getFirst(); packet.progress(.5f);
        var player = context.makeMockSurvivalPlayer();
        lookAt(player, SplineUtil.getPositionOnSpline(access.data(), .5f).add(0, .06, 0), 2);
        var saved = splitter.saveWithFullMetadata();
        var clientCopy = new ConveyorSplitterBlockEntity(pos, world.getBlockState(pos));
        clientCopy.load(saved);
        var visualPacket = clientCopy.pickupAccess(Direction.EAST).items().getFirst();
        BeltPickup.applyClient(clientCopy, Direction.EAST, packet.id, 32);
        context.assertTrue(clientCopy.pickupAccess(Direction.EAST).items().getFirst() == visualPacket
                && visualPacket.stack.getCount() == 32 && visualPacket.progress == .5f, "partial pickup keeps visual object and progress");
        BeltPickup.applyClient(clientCopy, Direction.EAST, packet.id, 0);
        context.assertTrue(clientCopy.pickupAccess(Direction.EAST).items().isEmpty(), "picked-up model removed without endpoint animation");
        context.assertTrue(BeltPickup.take(player, pos, Direction.EAST, packet.id, .5f).taken() == 64
                && access.items().isEmpty() && splitter.getCachedItemCount() == 0, "splitter output pickup conserves items");
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            var request = new pureneko.conveyor_belt_plus.network.PickupNetworking.Request(pos, Direction.EAST, 9876, .5f);
            pureneko.conveyor_belt_plus.network.PickupNetworking.Request.CODEC.encode(buf, request);
            context.assertTrue(buf.readableBytes() <= 24, "request is a small fixed-field packet, not an item stack or path");
            context.assertTrue(pureneko.conveyor_belt_plus.network.PickupNetworking.Request.CODEC.decode(buf).equals(request), "request round trip");
            var taken = new pureneko.conveyor_belt_plus.network.PickupNetworking.Taken(pos, Direction.EAST, 9876, 64);
            pureneko.conveyor_belt_plus.network.PickupNetworking.Taken.CODEC.encode(buf, taken);
            context.assertTrue(pureneko.conveyor_belt_plus.network.PickupNetworking.Taken.CODEC.decode(buf).equals(taken), "acknowledgement round trip");
        } finally { buf.release(); }
        context.assertTrue(BeltPickup.validProgress(.45f, .5f, 10, 1), "bounded rendering delay accepted");
        var center = new Vec3(0, 1, 0);
        context.assertTrue(BeltPickup.rayHit(BeltPickup.bounds(center), center, center.add(0, 0, 4)) != null, "eye inside model envelope");
        context.assertTrue(BeltPickup.rayHit(BeltPickup.bounds(center), center.add(0, 0, 2), center.add(0, 0, 4)) == null, "items behind view cannot be hit");
        context.succeed();
    }

    private static void lookAt(net.minecraft.world.entity.player.Player player, Vec3 point, double distance) {
        player.setPosRaw(point.x, point.y, point.z + distance);
        var offset = point.subtract(player.getEyePosition());
        player.setYRot((float) Math.toDegrees(Math.atan2(-offset.x, offset.z)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(offset.y, Math.sqrt(offset.x * offset.x + offset.z * offset.z))));
    }
}
