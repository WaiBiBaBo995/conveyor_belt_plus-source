package pureneko.conveyor_belt_plus;

import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.registry.ItemContent;
import pureneko.conveyor_belt_plus.registry.BlockContent;

import pureneko.conveyor_belt_plus.blocks.*;
import pureneko.conveyor_belt_plus.filter.FilterRule;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ConveyorBeltPlus.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConveyorGameTests {
    @GameTest(templateName = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID, tickLimit = 200)
    public static void regressions(TestContext context) {
        var originalTags = Registries.ITEM.streamTagsAndEntries().collect(java.util.stream.Collectors.toMap(
                com.mojang.datafixers.util.Pair::getFirst, pair -> pair.getSecond().stream().toList()));
        try { ConveyorRegressionTests.nativeConfigurationTests(); TransportNbtTests.main(new String[0]); }
        finally {
            originalTags.putIfAbsent(net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.ITEM,
                    new net.minecraft.util.Identifier("conveyor_belt_plus_test", "logs")), java.util.List.of());
            Registries.ITEM.populateTags(originalTags);
        }
        context.complete();
    }

    @GameTest(templateName = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID, tickLimit = 200)
    public static void upgradeChuteAndBelt(TestContext context) {
        var world = context.getWorld();
        var source = context.getAbsolutePos(new BlockPos(2, 2, 2));
        var target = context.getAbsolutePos(new BlockPos(12, 2, 2));
        world.setBlockState(source, BlockContent.CHUTE_BLOCK.get().getDefaultState().with(HorizontalFacingBlock.FACING, Direction.EAST));
        world.setBlockState(target, BlockContent.CHUTE_BLOCK.get().getDefaultState().with(HorizontalFacingBlock.FACING, Direction.WEST));
        var chute = (ChuteBlockEntity) world.getBlockEntity(source);
        chute.setRule(0, FilterRule.tag("minecraft:logs"));
        chute.setRule(1, FilterRule.item(new ItemStack(Items.DIAMOND_SWORD), true));
        chute.setWhitelistMode(true);
        context.assertTrue(chute.connectOutgoing(Direction.EAST, target, Direction.WEST, java.util.List.of(), 1), "test route created");
        ((ChuteBlockEntity) world.getBlockEntity(target)).connectIncoming(Direction.WEST, source);
        var data = chute.createNbtWithIdentifyingData();
        var moving = new NbtList();
        moving.add(new ChuteBlockEntity.BeltItem(0.375f, 42L, new ItemStack(Items.OAK_LOG, 128))
                .write(world.getRegistryManager(), "a", "b"));
        data.put("moving", moving);
        chute.readNbt(data);
        var player = context.createMockSurvivalPlayer();
        player.setSneaking(true);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ItemContent.ADVANCED_CHUTE.get(), 2));
        context.assertTrue(UpgradeInteractions.chute(world, source, player, player.getMainHandStack(),
                (ChuteBlock) BlockContent.ADVANCED_CHUTE.get()), "chute upgraded");
        var upgraded = (ChuteBlockEntity) world.getBlockEntity(source);
        context.assertTrue(upgraded.getChuteTier() == 2 && upgraded.getOwnFacing() == Direction.EAST, "tier and facing retained");
        context.assertTrue(upgraded.isWhitelistMode() && upgraded.getRule(0).sameRule(chute.getRule(0))
                && upgraded.getRule(1).sameRule(chute.getRule(1)), "all filter kinds and mode retained");
        context.assertTrue(upgraded.getTarget().equals(target), "connection retained");
        var packet = upgraded.getMovingItems().iterator().next();
        context.assertTrue(packet.id == 42L && packet.progress == 0.375f && packet.stack.getCount() == 128, "in-flight packet retained");
        context.assertTrue(player.getMainHandStack().getCount() == 1, "one new chute consumed");
        context.assertTrue(player.getInventory().count(ItemContent.CHUTE.get()) == 1, "old chute returned once");
        context.assertFalse(UpgradeInteractions.chute(world, source, player, player.getMainHandStack(),
                (ChuteBlock) BlockContent.ADVANCED_CHUTE.get()), "same-tier chute does not consume items");
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ItemContent.ULTIMATE_BELT.get(), 2));
        context.assertTrue(UpgradeInteractions.belt(world, source, Direction.EAST, player, player.getMainHandStack(), 3), "belt upgraded");
        context.assertTrue(upgraded.getMovingItems().iterator().next() == packet && upgraded.getBeltTier() == 3, "belt upgrade preserves packet identity and progress");
        context.assertTrue(player.getMainHandStack().getCount() == 1 && player.getInventory().count(ItemContent.BELT.get()) == 1, "belt exchange conserved items");
        var menu = new pureneko.conveyor_belt_plus.screen.ChuteScreenHandler(1, player.getInventory(), upgraded);
        context.assertTrue(menu.getFilterSlotCount() == 10, "upgraded chute exposes larger menu capacity");
        context.complete();
    }

    @GameTest(templateName = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID, tickLimit = 200)
    public static void upgradeSplitterOutputs(TestContext context) {
        var world = context.getWorld();
        var source = context.getAbsolutePos(new BlockPos(7, 2, 7));
        var east = context.getAbsolutePos(new BlockPos(14, 2, 7));
        var north = context.getAbsolutePos(new BlockPos(7, 2, 1));
        world.setBlockState(source, BlockContent.SPLITTER.get().getDefaultState());
        world.setBlockState(east, BlockContent.CHUTE_BLOCK.get().getDefaultState().with(HorizontalFacingBlock.FACING, Direction.WEST));
        world.setBlockState(north, BlockContent.CHUTE_BLOCK.get().getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH));
        var splitter = (ConveyorSplitterBlockEntity) world.getBlockEntity(source);
        splitter.connectIncoming(Direction.WEST, source.west(5));
        context.assertTrue(splitter.connectOutgoing(Direction.EAST, east, Direction.WEST, java.util.List.of(), 1), "east route");
        context.assertTrue(splitter.connectOutgoing(Direction.NORTH, north, Direction.SOUTH, java.util.List.of(), 2), "north route");
        context.assertTrue(splitter.acceptFromBelt(new ItemStack(Items.IRON_INGOT, 64), Direction.WEST), "test batch accepted");
        splitter.tick(world, source, world.getBlockState(source), splitter);
        var eastRoute = java.util.stream.StreamSupport.stream(splitter.getRoutes().spliterator(), false)
                .filter(route -> route.getPort() == Direction.EAST).findFirst().orElseThrow();
        var packet = eastRoute.getMovingItems().iterator().next();
        var player = context.createMockSurvivalPlayer();
        player.setSneaking(true);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ItemContent.ULTIMATE_BELT.get(), 2));
        context.assertTrue(UpgradeInteractions.belt(world, source, Direction.EAST, player, player.getMainHandStack(), 3), "selected splitter output upgraded");
        context.assertTrue(eastRoute.getBeltTier() == 3 && packet == eastRoute.getMovingItems().iterator().next()
                && packet.stack.getCount() == 32 && packet.progress == 0, "split output retains packet and route objects");
        context.assertTrue(splitter.outgoingBeltTier(Direction.NORTH) == 2, "other output unchanged");
        context.assertFalse(UpgradeInteractions.belt(world, source, Direction.WEST, player, player.getMainHandStack(), 3), "input face cannot upgrade output");
        context.assertTrue(player.getMainHandStack().getCount() == 1, "invalid face consumed nothing");
        var creative = context.createMockCreativePlayer();
        creative.setSneaking(true);
        creative.setStackInHand(Hand.MAIN_HAND, new ItemStack(ItemContent.ULTIMATE_BELT.get()));
        context.assertTrue(UpgradeInteractions.belt(world, source, Direction.NORTH, creative, creative.getMainHandStack(), 3), "creative upgrade");
        context.assertTrue(creative.getMainHandStack().getCount() == 1 && creative.getInventory().count(ItemContent.ADVANCED_BELT.get()) == 0,
                "creative upgrade neither consumes nor duplicates refunds");
        var saved = splitter.createNbtWithIdentifyingData();
        var restored = new ConveyorSplitterBlockEntity(source, world.getBlockState(source));
        restored.readNbt(saved);
        context.assertTrue(restored.outgoingBeltTier(Direction.EAST) == 3 && restored.outgoingBeltTier(Direction.NORTH) == 3,
                "upgraded output tiers persist");
        context.complete();
    }
}
