package pureneko.conveyor_belt_plus;

import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.registry.BlockContent;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerListener;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pureneko.conveyor_belt_plus.blocks.ChuteBlock;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity;
import pureneko.conveyor_belt_plus.blocks.UpgradeInteractions;
import pureneko.conveyor_belt_plus.config.ConveyorConfig;
import pureneko.conveyor_belt_plus.filter.FilterRule;
import pureneko.conveyor_belt_plus.screen.ChuteScreenHandler;
import pureneko.conveyor_belt_plus.util.ExtractionSide;
import pureneko.conveyor_belt_plus.util.RedstoneControl.Mode;

@GameTestHolder(ConveyorBeltPlus.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConveyorExtractionTests {
    private static final int TOP_BOTTOM = ExtractionSide.TOP.bit() | ExtractionSide.BOTTOM.bit();

    @GameTest(templateName = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void sidedFurnaceAndRoundRobin(TestContext context) {
        double speed = ConveyorConfig.SPEEDS[0].get();
        boolean enabled = ConveyorConfig.CHUTE_EXTRACTION_SIDES.get();
        try {
            ConveyorConfig.SPEEDS[0].set(64.0);
            ConveyorConfig.CHUTE_EXTRACTION_SIDES.set(true);
            var world = context.getWorld();
            var chute = route(context, BlockContent.CHUTE_BLOCK.get(), 5);
            var sourcePos = chute.getPos().west();
            world.setBlockState(sourcePos, Blocks.FURNACE.getDefaultState());
            var furnace = (FurnaceBlockEntity) world.getBlockEntity(sourcePos);
            furnace.setStack(2, new ItemStack(Items.IRON_INGOT, 12));
            context.assertTrue(pull(chute).isEmpty() && furnace.getStack(2).getCount() == 12,
                    "default attached horizontal face cannot access furnace output");
            chute.setExtractionSides(ExtractionSide.BOTTOM.bit());
            context.assertTrue(pull(chute).getCount() == 12 && furnace.getStack(2).isEmpty(), "bottom exposes furnace output");

            furnace.setStack(1, new ItemStack(Items.COAL, 64));
            context.assertTrue(pull(chute).isEmpty() && furnace.getStack(1).getCount() == 64,
                    "bottom cannot extract fuel even if the handler exposes that slot");
            chute.setExtractionSides(TOP_BOTTOM);
            furnace.setStack(0, new ItemStack(Items.RAW_IRON, 64));
            furnace.setStack(2, new ItemStack(Items.IRON_INGOT, 64));
            context.assertTrue(pull(chute).isOf(Items.RAW_IRON), "first selected usable side");
            furnace.setStack(0, new ItemStack(Items.RAW_IRON, 64));
            context.assertTrue(pull(chute).isOf(Items.IRON_INGOT) && furnace.getStack(0).getCount() == 64,
                    "round robin serves the next face despite a refilled first face");

            furnace.setStack(2, new ItemStack(Items.IRON_INGOT, 7));
            chute.setRule(0, FilterRule.item(new ItemStack(Items.IRON_INGOT), false));
            chute.setWhitelistMode(true);
            context.assertTrue(pull(chute).isOf(Items.IRON_INGOT) && furnace.getStack(0).getCount() == 64,
                    "filter-rejected face is skipped, not bypassed");
            chute.setWhitelistMode(false);
            furnace.setStack(0, ItemStack.EMPTY);
            // A selected top face must never be interpreted as the block ABOVE the attached container.
            world.setBlockState(sourcePos.up(), Blocks.CHEST.getDefaultState());
            var neighbor = (ChestBlockEntity) world.getBlockEntity(sourcePos.up());
            neighbor.setStack(0, new ItemStack(Items.DIAMOND, 64));
            chute.setExtractionSides(ExtractionSide.TOP.bit());
            context.assertTrue(pull(chute).isEmpty() && neighbor.getStack(0).getCount() == 64, "never extracts from neighboring blocks");
            chute.setExtractionSides(0);
            context.assertTrue(pull(chute).isEmpty(), "no selected sides stops extraction");
            ConveyorConfig.CHUTE_EXTRACTION_SIDES.set(false);
            context.assertTrue(pull(chute).isOf(Items.COAL) && chute.getExtractionSides() == 0,
                    "disabled feature uses original attached face and preserves saved empty mask");
            ConveyorConfig.CHUTE_EXTRACTION_SIDES.set(true);
            furnace.setStack(1, new ItemStack(Items.COAL));
            context.assertTrue(pull(chute).isEmpty(), "reenabling restores saved selection");
        } finally {
            ConveyorConfig.SPEEDS[0].set(speed);
            ConveyorConfig.CHUTE_EXTRACTION_SIDES.set(enabled);
        }
        context.complete();
    }

    @GameTest(templateName = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void batchLimitsSharedStorageAndRedstone(TestContext context) {
        double speed = ConveyorConfig.SPEEDS[0].get();
        boolean enabled = ConveyorConfig.CHUTE_EXTRACTION_SIDES.get();
        try {
            ConveyorConfig.SPEEDS[0].set(64.0);
            ConveyorConfig.CHUTE_EXTRACTION_SIDES.set(true);
            Block[] tiers = {BlockContent.CHUTE_BLOCK.get(), BlockContent.ADVANCED_CHUTE.get(), BlockContent.ULTIMATE_CHUTE.get()};
            for (int tier = 0; tier < tiers.length; tier++) {
                var chute = route(context, tiers[tier], 3 + tier * 4);
                var world = context.getWorld();
                world.setBlockState(chute.getPos().west(), Blocks.CHEST.getDefaultState());
                var chest = (ChestBlockEntity) world.getBlockEntity(chute.getPos().west());
                for (int slot = 0; slot < 20; slot++) chest.setStack(slot, new ItemStack(Items.GOLD_INGOT, 64));
                chute.setExtractionSides(ExtractionSide.ALL_MASK);
                int expected = 64 * ConveyorConfig.chuteStacks(tier + 1);
                var batch = pull(chute);
                context.assertTrue(batch.getCount() == expected && total(chest) + batch.getCount() == 1280,
                        "six views of one chest do not multiply throughput or duplicate items, tier " + tier);
                chute.setRedstoneMode(Mode.NEVER);
                context.assertTrue(pull(chute).isEmpty(), "redstone never applies to all selected faces");
                chute.setRedstoneMode(Mode.PULSE);
                chute.setExtractionSides(0);
                world.setBlockState(chute.getPos().up(), Blocks.REDSTONE_BLOCK.getDefaultState());
                context.assertTrue(pull(chute).isEmpty(), "no selected faces retains pending pulse");
                chute.setExtractionSides(ExtractionSide.ALL_MASK);
                context.assertTrue(pull(chute).getCount() == expected && pull(chute).isEmpty(), "one pulse remains exactly one batch across all faces");
                chute.setRedstoneMode(Mode.ALWAYS);
                chute.setExtractionSides(0);
                int remaining = total(chest);
                context.assertTrue(chute.acceptFromBelt(new ItemStack(Items.GOLD_INGOT, 3), Direction.EAST)
                        && total(chest) == remaining + 3, "empty extraction selection does not affect insertion");
                int oldStacks = ConveyorConfig.STACKS[tier].get();
                try {
                    ConveyorConfig.STACKS[tier].set(2);
                    chute.setExtractionSides(ExtractionSide.ALL_MASK);
                    context.assertTrue(pull(chute).getCount() == 128, "custom batch limit remains shared by all selected faces");
                } finally { ConveyorConfig.STACKS[tier].set(oldStacks); }
            }
        } finally {
            ConveyorConfig.SPEEDS[0].set(speed);
            ConveyorConfig.CHUTE_EXTRACTION_SIDES.set(enabled);
        }
        context.complete();
    }

    @GameTest(templateName = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void selectionPersistenceUpgradeAndMenuSecurity(TestContext context) {
        boolean enabled = ConveyorConfig.CHUTE_EXTRACTION_SIDES.get();
        try {
            ConveyorConfig.CHUTE_EXTRACTION_SIDES.set(true);
            var world = context.getWorld();
            var pos = context.getAbsolutePos(new BlockPos(5, 2, 5));
            world.setBlockState(pos, BlockContent.CHUTE_BLOCK.get().getDefaultState().with(HorizontalFacingBlock.FACING, Direction.WEST));
            var chute = (ChuteBlockEntity) world.getBlockEntity(pos);
            var legacy = chute.createNbtWithIdentifyingData(world.getRegistryManager());
            legacy.remove("extractionSides");
            chute.read(legacy, world.getRegistryManager());
            context.assertTrue(chute.getExtractionSides() == ExtractionSide.DEFAULT_MASK, "legacy save uses original attached face");
            for (int invalid : new int[]{-1, 64, Integer.MAX_VALUE}) {
                legacy.putInt("extractionSides", invalid);
                chute.read(legacy, world.getRegistryManager());
                context.assertTrue(chute.getExtractionSides() == ExtractionSide.DEFAULT_MASK, "corrupt saved bitmask defaults safely");
            }
            for (int mask : new int[]{0, TOP_BOTTOM, 63}) {
                chute.setExtractionSides(mask);
                var restored = new ChuteBlockEntity(pos, world.getBlockState(pos));
                restored.read(chute.createNbtWithIdentifyingData(world.getRegistryManager()), world.getRegistryManager());
                context.assertTrue(restored.getExtractionSides() == mask, "saved mask including zero survives reload");
            }
            chute.setExtractionSides(TOP_BOTTOM);
            chute.setRule(0, FilterRule.item(new ItemStack(Items.IRON_INGOT), false));
            var player = context.createMockPlayer(GameMode.SURVIVAL);
            player.setPos(pos.getX(), pos.getY(), pos.getZ());
            var menu = new ChuteScreenHandler(51, player.getInventory(), chute);
            menu.setCursorStack(new ItemStack(Items.DIAMOND, 32));
            int[] properties = new int[4];
            menu.addListener(new ScreenHandlerListener() {
                @Override public void onSlotUpdate(ScreenHandler handler, int slot, ItemStack stack) {}
                @Override public void onPropertyUpdate(ScreenHandler handler, int id, int value) { properties[id] = value; }
            });
            long filterRevision = chute.getFilterRevision();
            context.assertTrue(menu.onButtonClick(player, ChuteScreenHandler.EXTRACTION_BUTTON_BASE + ExtractionSide.RIGHT.ordinal()), "menu toggles one face");
            context.assertTrue(properties[1] == (TOP_BOTTOM | ExtractionSide.RIGHT.bit()) && properties[2] == 1
                    && properties[3] == Direction.WEST.getId(), "native menu properties synchronize mask, enable switch and orientation");
            context.assertTrue(chute.getFilterRevision() == filterRevision && menu.getCursorStack().getCount() == 32,
                    "side changes neither resend filter edits nor consume cursor items");
            var intruder = context.createMockPlayer(GameMode.SURVIVAL);
            intruder.setPos(pos.getX(), pos.getY(), pos.getZ());
            context.assertFalse(menu.onButtonClick(intruder, ChuteScreenHandler.EXTRACTION_BUTTON_BASE), "another player cannot use this menu");
            for (int invalid : new int[]{-1, 8, 99, Integer.MIN_VALUE, Integer.MAX_VALUE})
                context.assertFalse(menu.onButtonClick(player, invalid), "invalid side button refused");
            player.setPos(pos.getX() + 40, pos.getY(), pos.getZ());
            context.assertFalse(menu.onButtonClick(player, ChuteScreenHandler.EXTRACTION_BUTTON_BASE), "out-of-range edit refused");
            player.setPos(pos.getX(), pos.getY(), pos.getZ());
            int selected = chute.getExtractionSides();
            ConveyorConfig.CHUTE_EXTRACTION_SIDES.set(false);
            menu.sendContentUpdates();
            context.assertTrue(properties[2] == 0 && properties[1] == selected, "global switch synchronizes without clearing choices");
            context.assertFalse(menu.onButtonClick(player, ChuteScreenHandler.EXTRACTION_BUTTON_BASE), "server rejects edits while feature disabled");
            ConveyorConfig.CHUTE_EXTRACTION_SIDES.set(true);
            player.setSneaking(true);
            for (var upgrade : new Block[]{BlockContent.ADVANCED_CHUTE.get(), BlockContent.ULTIMATE_CHUTE.get()}) {
                player.setStackInHand(Hand.MAIN_HAND, new ItemStack(upgrade));
                context.assertTrue(UpgradeInteractions.chute(world, pos, player, player.getMainHandStack(), (ChuteBlock) upgrade), "in-place tier upgrade");
                chute = (ChuteBlockEntity) world.getBlockEntity(pos);
                context.assertTrue(chute.getExtractionSides() == selected && chute.getOwnFacing() == Direction.WEST
                        && !chute.getRule(0).isEmpty(), "upgrade retains sides, orientation and filter rules");
            }
            context.assertFalse(menu.onButtonClick(player, ChuteScreenHandler.EXTRACTION_BUTTON_BASE), "stale menu cannot edit replaced interface");
        } finally { ConveyorConfig.CHUTE_EXTRACTION_SIDES.set(enabled); }
        context.complete();
    }

    private static ChuteBlockEntity route(TestContext context, Block block, int z) {
        var world = context.getWorld();
        var pos = context.getAbsolutePos(new BlockPos(3, 2, z));
        var target = pos.east(8);
        world.setBlockState(pos, block.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.EAST));
        world.setBlockState(target, BlockContent.CHUTE_BLOCK.get().getDefaultState().with(HorizontalFacingBlock.FACING, Direction.WEST));
        var chute = (ChuteBlockEntity) world.getBlockEntity(pos);
        context.assertTrue(chute.connectOutgoing(Direction.EAST, target, Direction.WEST, java.util.List.of(), 1), "test belt created");
        return chute;
    }
    private static ItemStack pull(ChuteBlockEntity chute) {
        chute.pickupAccess(Direction.EAST).items().clear();
        chute.tick(chute.getWorld(), chute.getPos(), chute.getCachedState(), chute);
        var queue = chute.pickupAccess(Direction.EAST).items();
        if (queue.size() > 1) throw new AssertionError("One extraction step spawned multiple batches");
        return queue.isEmpty() ? ItemStack.EMPTY : queue.getFirst().stack.copy();
    }
    private static int total(ChestBlockEntity chest) {
        int total = 0;
        for (int i = 0; i < chest.size(); i++) total += chest.getStack(i).getCount();
        return total;
    }
}
