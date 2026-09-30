package pureneko.conveyor_belt_plus;

import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.registry.ItemContent;
import pureneko.conveyor_belt_plus.screen.ChuteScreenHandler;
import pureneko.conveyor_belt_plus.registry.BlockContent;

import pureneko.conveyor_belt_plus.blocks.*;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity.BeltItem;
import pureneko.conveyor_belt_plus.blocks.ConveyorSplitterBlockEntity.Route;
import pureneko.conveyor_belt_plus.filter.FilterRule;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ConveyorBeltPlus.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConveyorGameTests {
    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID, timeoutTicks = 200)
    public static void regressions(GameTestHelper context) {
        var originalTags = BuiltInRegistries.ITEM.getTags().collect(java.util.stream.Collectors.toMap(
                com.mojang.datafixers.util.Pair::getFirst, pair -> pair.getSecond().stream().toList()));
        try { ConveyorRegressionTests.nativeConfigurationTests(); TransportNbtTests.main(new String[0]); }
        finally {
            originalTags.putIfAbsent(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("conveyor_belt_plus_test", "logs")), java.util.List.of());
            BuiltInRegistries.ITEM.bindTags(originalTags);
        }
        context.succeed();
    }

    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID, timeoutTicks = 200)
    public static void upgradeChuteAndBelt(GameTestHelper context) {
        var world = context.getLevel();
        var source = context.absolutePos(new BlockPos(2, 2, 2));
        var target = context.absolutePos(new BlockPos(12, 2, 2));
        world.setBlockAndUpdate(source, BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        world.setBlockAndUpdate(target, BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        var chute = (ChuteBlockEntity) world.getBlockEntity(source);
        chute.setRule(0, FilterRule.tag("minecraft:logs"));
        chute.setRule(1, FilterRule.item(new ItemStack(Items.DIAMOND_SWORD), true));
        chute.setWhitelistMode(true);
        context.assertTrue(chute.connectOutgoing(Direction.EAST, target, Direction.WEST, java.util.List.of(), 1), "test route created");
        ((ChuteBlockEntity) world.getBlockEntity(target)).connectIncoming(Direction.WEST, source);
        var data = chute.saveWithFullMetadata(world.registryAccess());
        var moving = new ListTag();
        moving.add(new ChuteBlockEntity.BeltItem(0.375f, 42L, new ItemStack(Items.OAK_LOG, 128))
                .write(world.registryAccess(), "a", "b"));
        data.put("moving", moving);
        chute.loadWithComponents(data, world.registryAccess());
        var player = context.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemContent.ADVANCED_CHUTE.get(), 2));
        context.assertTrue(UpgradeInteractions.chute(world, source, player, player.getMainHandItem(),
                (ChuteBlock) BlockContent.ADVANCED_CHUTE.get()), "chute upgraded");
        var upgraded = (ChuteBlockEntity) world.getBlockEntity(source);
        context.assertTrue(upgraded.getChuteTier() == 2 && upgraded.getOwnFacing() == Direction.EAST, "tier and facing retained");
        context.assertTrue(upgraded.isWhitelistMode() && upgraded.getRule(0).sameRule(chute.getRule(0))
                && upgraded.getRule(1).sameRule(chute.getRule(1)), "all filter kinds and mode retained");
        context.assertTrue(upgraded.getTarget().equals(target), "connection retained");
        var packet = upgraded.getMovingItems().iterator().next();
        context.assertTrue(packet.id == 42L && packet.progress == 0.375f && packet.stack.getCount() == 128, "in-flight packet retained");
        context.assertTrue(player.getMainHandItem().getCount() == 1, "one new chute consumed");
        context.assertTrue(player.getInventory().countItem(ItemContent.CHUTE.get()) == 1, "old chute returned once");
        context.assertFalse(UpgradeInteractions.chute(world, source, player, player.getMainHandItem(),
                (ChuteBlock) BlockContent.ADVANCED_CHUTE.get()), "same-tier chute does not consume items");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemContent.ULTIMATE_BELT.get(), 2));
        context.assertTrue(UpgradeInteractions.belt(world, source, Direction.EAST, player, player.getMainHandItem(), 3), "belt upgraded");
        context.assertTrue(upgraded.getMovingItems().iterator().next() == packet && upgraded.getBeltTier() == 3, "belt upgrade preserves packet identity and progress");
        context.assertTrue(player.getMainHandItem().getCount() == 1 && player.getInventory().countItem(ItemContent.BELT.get()) == 1, "belt exchange conserved items");
        var menu = new pureneko.conveyor_belt_plus.screen.ChuteScreenHandler(1, player.getInventory(), upgraded);
        context.assertTrue(menu.getFilterSlotCount() == 10, "upgraded chute exposes larger menu capacity");
        context.succeed();
    }

    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID, timeoutTicks = 200)
    public static void upgradeSplitterOutputs(GameTestHelper context) {
        var world = context.getLevel();
        var source = context.absolutePos(new BlockPos(7, 2, 7));
        var east = context.absolutePos(new BlockPos(14, 2, 7));
        var north = context.absolutePos(new BlockPos(7, 2, 1));
        world.setBlockAndUpdate(source, BlockContent.SPLITTER.get().defaultBlockState());
        world.setBlockAndUpdate(east, BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        world.setBlockAndUpdate(north, BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        var splitter = (ConveyorSplitterBlockEntity) world.getBlockEntity(source);
        splitter.connectIncoming(Direction.WEST, source.west(5));
        context.assertTrue(splitter.connectOutgoing(Direction.EAST, east, Direction.WEST, java.util.List.of(), 1), "east route");
        context.assertTrue(splitter.connectOutgoing(Direction.NORTH, north, Direction.SOUTH, java.util.List.of(), 2), "north route");
        context.assertTrue(splitter.acceptFromBelt(new ItemStack(Items.IRON_INGOT, 64), Direction.WEST), "test batch accepted");
        splitter.tick(world, source, world.getBlockState(source), splitter);
        var eastRoute = java.util.stream.StreamSupport.stream(splitter.getRoutes().spliterator(), false)
                .filter(route -> route.getPort() == Direction.EAST).findFirst().orElseThrow();
        var packet = eastRoute.getMovingItems().iterator().next();
        var player = context.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemContent.ULTIMATE_BELT.get(), 2));
        context.assertTrue(UpgradeInteractions.belt(world, source, Direction.EAST, player, player.getMainHandItem(), 3), "selected splitter output upgraded");
        context.assertTrue(eastRoute.getBeltTier() == 3 && packet == eastRoute.getMovingItems().iterator().next()
                && packet.stack.getCount() == 32 && packet.progress == 0, "split output retains packet and route objects");
        context.assertTrue(splitter.outgoingBeltTier(Direction.NORTH) == 2, "other output unchanged");
        context.assertFalse(UpgradeInteractions.belt(world, source, Direction.WEST, player, player.getMainHandItem(), 3), "input face cannot upgrade output");
        context.assertTrue(player.getMainHandItem().getCount() == 1, "invalid face consumed nothing");
        var creative = context.makeMockPlayer(GameType.CREATIVE);
        creative.setShiftKeyDown(true);
        creative.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemContent.ULTIMATE_BELT.get()));
        context.assertTrue(UpgradeInteractions.belt(world, source, Direction.NORTH, creative, creative.getMainHandItem(), 3), "creative upgrade");
        context.assertTrue(creative.getMainHandItem().getCount() == 1 && creative.getInventory().countItem(ItemContent.ADVANCED_BELT.get()) == 0,
                "creative upgrade neither consumes nor duplicates refunds");
        var saved = splitter.saveWithFullMetadata(world.registryAccess());
        var restored = new ConveyorSplitterBlockEntity(source, world.getBlockState(source));
        restored.loadWithComponents(saved, world.registryAccess());
        context.assertTrue(restored.outgoingBeltTier(Direction.EAST) == 3 && restored.outgoingBeltTier(Direction.NORTH) == 3,
                "upgraded output tiers persist");
        context.succeed();
    }
}
