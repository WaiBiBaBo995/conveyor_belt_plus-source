package pureneko.conveyor_belt_plus;

import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.registry.ItemContent;
import pureneko.conveyor_belt_plus.registry.BlockContent;

import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtList;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pureneko.conveyor_belt_plus.blocks.*;
import pureneko.conveyor_belt_plus.config.ConveyorConfig;
import pureneko.conveyor_belt_plus.util.BeltTransport;
import pureneko.conveyor_belt_plus.util.SplineUtil;

@GameTestHolder(ConveyorBeltPlus.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConveyorPickupTests {
    @GameTest(templateName = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void sneakUpgradeItemEntry(TestContext context) {
        var world = context.getWorld();
        var pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
        world.setBlockState(pos, BlockContent.CHUTE_BLOCK.get().getDefaultState());
        var player = context.createMockPlayer(GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ItemContent.ADVANCED_CHUTE.get(), 2));
        context.assertFalse(UpgradeInteractions.chute(world, pos, player, player.getMainHandStack(),
                (ChuteBlock) BlockContent.ADVANCED_CHUTE.get()), "standing player cannot upgrade");
        player.setSneaking(true);
        var use = new ItemUsageContext(player, Hand.MAIN_HAND, new BlockHitResult(pos.toCenterPos(), Direction.NORTH, pos, false));
        context.assertTrue(ItemContent.ADVANCED_CHUTE.get().useOnBlock(use).isAccepted(), "sneaking item path upgrades even when vanilla bypasses block use");
        context.assertTrue(world.getBlockState(pos).isOf(BlockContent.ADVANCED_CHUTE.get()) && player.getMainHandStack().getCount() == 1, "one upgrade and consumption");
        var target = context.getAbsolutePos(new BlockPos(2, 2, 12));
        world.setBlockState(target, BlockContent.CHUTE_BLOCK.get().getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH));
        // Use a south-facing source for a straight test route.
        world.setBlockState(pos, BlockContent.ADVANCED_CHUTE.get().getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH));
        var chute = (ChuteBlockEntity) world.getBlockEntity(pos);
        context.assertTrue(chute.connectOutgoing(Direction.SOUTH, target, Direction.NORTH, java.util.List.of(), 1), "belt exists");
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ItemContent.ULTIMATE_BELT.get(), 2));
        player.setSneaking(false);
        ItemContent.ULTIMATE_BELT.get().useOnBlock(use);
        context.assertTrue(chute.getBeltTier() == 1 && player.getMainHandStack().getCount() == 2, "ordinary right click does not upgrade belt");
        player.setSneaking(true);
        context.assertTrue(ItemContent.ULTIMATE_BELT.get().useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND,
                new BlockHitResult(pos.toCenterPos(), Direction.SOUTH, pos, false))).isAccepted(), "sneak right click belt entry");
        context.assertTrue(chute.getBeltTier() == 3 && player.getMainHandStack().getCount() == 1, "belt upgraded once");
        context.complete();
    }

    @GameTest(templateName = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void boundedSplitterBuffer(TestContext context) {
        var world = context.getWorld();
        var pos = context.getAbsolutePos(new BlockPos(5, 2, 5));
        world.setBlockState(pos, BlockContent.SPLITTER.get().getDefaultState());
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
        var data = splitter.createNbtWithIdentifyingData(world.getRegistryManager());
        var settings = new java.util.Properties(); settings.setProperty("splitter.buffer_items", "128");
        try {
            ConfigTestSupport.apply(settings, ignored -> {});
            splitter.read(data, world.getRegistryManager());
            context.assertTrue(splitter.getCachedItemCount() == 512, "shrinking capacity never deletes saved items");
            context.assertFalse(splitter.acceptFromBelt(new ItemStack(Items.IRON_INGOT), Direction.WEST), "overfull restored buffer refuses more");
        } finally { ConfigTestSupport.apply(new java.util.Properties(), ignored -> {}); }
        context.complete();
    }

    @GameTest(templateName = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void pickupValidationAndConservation(TestContext context) {
        var world = context.getWorld();
        var pos = context.getAbsolutePos(new BlockPos(2, 2, 5));
        var target = context.getAbsolutePos(new BlockPos(12, 2, 5));
        world.setBlockState(pos, BlockContent.CHUTE_BLOCK.get().getDefaultState().with(HorizontalFacingBlock.FACING, Direction.EAST));
        world.setBlockState(target, BlockContent.CHUTE_BLOCK.get().getDefaultState().with(HorizontalFacingBlock.FACING, Direction.WEST));
        var chute = (ChuteBlockEntity) world.getBlockEntity(pos);
        context.assertTrue(chute.connectOutgoing(Direction.EAST, target, Direction.WEST, java.util.List.of(), 1), "pickup route exists");
        var original = new ItemStack(Items.IRON_INGOT, 128);
        original.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Transport components"));
        var data = chute.createNbtWithIdentifyingData(world.getRegistryManager());
        var moving = new NbtList();
        moving.add(new ChuteBlockEntity.BeltItem(.5f, 123L, original).write(world.getRegistryManager(), "a", "b"));
        data.put("moving", moving); chute.read(data, world.getRegistryManager());
        var player = context.createMockPlayer(GameMode.SURVIVAL);
        var point = SplineUtil.getPositionOnSpline(chute.getBeltData(), .5f).add(0, .06, 0);
        lookAt(player, point, 2);
        context.assertTrue(BeltPickup.take(player, pos, Direction.EAST, 999, .5f).taken() == 0, "unknown ID refused");
        context.assertTrue(BeltPickup.take(player, pos, Direction.WEST, 123, .5f).taken() == 0, "wrong output refused");
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -.1f, 1.1f, 0})
            context.assertTrue(BeltPickup.take(player, pos, Direction.EAST, 123, invalid).taken() == 0, "invalid/stale progress refused");
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
        context.assertTrue(BeltPickup.take(player, pos, Direction.EAST, 123, .5f).taken() == 0, "must be empty-handed");
        player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        lookAt(player, point, 8);
        context.assertTrue(BeltPickup.take(player, pos, Direction.EAST, 123, .5f).taken() == 0, "out of reach refused");
        lookAt(player, point, 2);
        player.setYaw(player.getYaw() + 90);
        context.assertTrue(BeltPickup.take(player, pos, Direction.EAST, 123, .5f).taken() == 0, "must aim at the rendered location");
        lookAt(player, point, 2);
        var wall = BlockPos.ofFloored(point.add(0, .5, 1));
        world.setBlockState(wall, Blocks.STONE.getDefaultState());
        world.setBlockState(wall.up(), Blocks.STONE.getDefaultState());
        context.assertTrue(BeltPickup.take(player, pos, Direction.EAST, 123, .5f).taken() == 0, "cannot take through walls");
        world.setBlockState(wall, Blocks.AIR.getDefaultState()); world.setBlockState(wall.up(), Blocks.AIR.getDefaultState());
        player.getAbilities().allowModifyWorld = false;
        context.assertTrue(BeltPickup.take(player, pos, Direction.EAST, 123, .5f).taken() == 0, "adventure restrictions respected");
        player.getAbilities().allowModifyWorld = true;
        player.currentScreenHandler = new pureneko.conveyor_belt_plus.screen.ChuteScreenHandler(77, player.getInventory(), chute);
        context.assertTrue(BeltPickup.take(player, pos, Direction.EAST, 123, .5f).taken() == 0, "cannot take while a menu is open");
        player.currentScreenHandler = player.playerScreenHandler;
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock> deny = event -> {
            if (event.getEntity() == player) event.setCanceled(true);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(deny);
        try {
            context.assertTrue(BeltPickup.take(player, pos, Direction.EAST, 123, .5f).taken() == 0, "protection event can cancel pickup");
        } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(deny); }
        // Only the empty selected slot remains available: take 64, leave 64 on the same moving packet.
        for (int slot = 1; slot < 36; slot++) player.getInventory().setStack(slot, new ItemStack(Items.STONE, 64));
        var packet = chute.getMovingItems().iterator().next();
        var result = BeltPickup.take(player, pos, Direction.EAST, 123, .5f);
        context.assertTrue(result.taken() == 64 && result.remaining() == 64 && packet == chute.getMovingItems().iterator().next()
                && packet.progress == .5f, "partial pickup preserves packet and motion");
        context.assertTrue(ItemStack.areItemsAndComponentsEqual(player.getMainHandStack(), original), "components retained");
        player.getInventory().setStack(1, player.getMainHandStack()); player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        context.assertTrue(BeltPickup.take(player, pos, Direction.EAST, 123, .5f).taken() == 64
                && !chute.getMovingItems().iterator().hasNext(), "remaining stack removed exactly once");
        player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        context.assertTrue(BeltPickup.take(player, pos, Direction.EAST, 123, .5f).taken() == 0, "duplicate request never duplicates items");
        var creative = context.createMockPlayer(GameMode.CREATIVE);
        for (int slot = 0; slot < 36; slot++) creative.getInventory().setStack(slot, new ItemStack(Items.STONE, 64));
        var leftover = new ItemStack(Items.GOLD_INGOT, 1024);
        context.assertTrue(BeltPickup.transferToInventory(creative, leftover) == 0 && leftover.getCount() == 1024,
                "full creative inventory cannot discard conveyor items");
        context.complete();
    }

    @GameTest(templateName = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void splitterPickupAndClientPackets(TestContext context) {
        var world = context.getWorld();
        var pos = context.getAbsolutePos(new BlockPos(2, 2, 5));
        var target = context.getAbsolutePos(new BlockPos(12, 2, 5));
        world.setBlockState(pos, BlockContent.SPLITTER.get().getDefaultState());
        world.setBlockState(target, BlockContent.CHUTE_BLOCK.get().getDefaultState().with(HorizontalFacingBlock.FACING, Direction.WEST));
        var splitter = (ConveyorSplitterBlockEntity) world.getBlockEntity(pos);
        splitter.connectIncoming(Direction.WEST, pos.west());
        context.assertTrue(splitter.connectOutgoing(Direction.EAST, target, Direction.WEST, java.util.List.of(), 1), "splitter pickup route");
        splitter.acceptFromBelt(new ItemStack(Items.GOLD_INGOT, 64), Direction.WEST);
        splitter.tick(world, pos, world.getBlockState(pos), splitter);
        var access = splitter.pickupAccess(Direction.EAST);
        var packet = access.items().getFirst(); packet.progress(.5f);
        var player = context.createMockPlayer(GameMode.SURVIVAL);
        lookAt(player, SplineUtil.getPositionOnSpline(access.data(), .5f).add(0, .06, 0), 2);
        var saved = splitter.createNbtWithIdentifyingData(world.getRegistryManager());
        var clientCopy = new ConveyorSplitterBlockEntity(pos, world.getBlockState(pos));
        clientCopy.read(saved, world.getRegistryManager());
        var visualPacket = clientCopy.pickupAccess(Direction.EAST).items().getFirst();
        BeltPickup.applyClient(clientCopy, Direction.EAST, packet.id, 32);
        context.assertTrue(clientCopy.pickupAccess(Direction.EAST).items().getFirst() == visualPacket
                && visualPacket.stack.getCount() == 32 && visualPacket.progress == .5f, "partial pickup keeps visual object and progress");
        BeltPickup.applyClient(clientCopy, Direction.EAST, packet.id, 0);
        context.assertTrue(clientCopy.pickupAccess(Direction.EAST).items().isEmpty(), "picked-up model removed without endpoint animation");
        context.assertTrue(BeltPickup.take(player, pos, Direction.EAST, packet.id, .5f).taken() == 64
                && access.items().isEmpty() && splitter.getCachedItemCount() == 0, "splitter output pickup conserves items");
        var buf = new net.minecraft.network.RegistryByteBuf(io.netty.buffer.Unpooled.buffer(), world.getRegistryManager());
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
        var center = new Vec3d(0, 1, 0);
        context.assertTrue(BeltPickup.rayHit(BeltPickup.bounds(center), center, center.add(0, 0, 4)) != null, "eye inside model envelope");
        context.assertTrue(BeltPickup.rayHit(BeltPickup.bounds(center), center.add(0, 0, 2), center.add(0, 0, 4)) == null, "items behind view cannot be hit");
        context.complete();
    }

    private static void lookAt(net.minecraft.entity.player.PlayerEntity player, Vec3d point, double distance) {
        player.setPos(point.x, point.y, point.z + distance);
        var offset = point.subtract(player.getEyePos());
        player.setYaw((float) Math.toDegrees(Math.atan2(-offset.x, offset.z)));
        player.setPitch((float) -Math.toDegrees(Math.atan2(offset.y, Math.sqrt(offset.x * offset.x + offset.z * offset.z))));
    }
}
