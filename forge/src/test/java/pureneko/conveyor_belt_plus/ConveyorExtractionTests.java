package pureneko.conveyor_belt_plus;

import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.registry.BlockContent;
import java.util.Deque;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
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

    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void sidedFurnaceAndRoundRobin(GameTestHelper context) {
        double speed = ConveyorConfig.SPEEDS[0].get();
        boolean enabled = ConveyorConfig.CHUTE_EXTRACTION_SIDES.get();
        try {
            ConveyorConfig.SPEEDS[0].set(64.0);
            ConveyorConfig.CHUTE_EXTRACTION_SIDES.set(true);
            var world = context.getLevel();
            var chute = route(context, BlockContent.CHUTE_BLOCK.get(), 5);
            var sourcePos = chute.getBlockPos().west();
            world.setBlockAndUpdate(sourcePos, Blocks.FURNACE.defaultBlockState());
            var furnace = (FurnaceBlockEntity) world.getBlockEntity(sourcePos);
            furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 12));
            context.assertTrue(pull(chute).isEmpty() && furnace.getItem(2).getCount() == 12,
                    "default attached horizontal face cannot access furnace output");
            chute.setExtractionSides(ExtractionSide.BOTTOM.bit());
            context.assertTrue(pull(chute).getCount() == 12 && furnace.getItem(2).isEmpty(), "bottom exposes furnace output");

            furnace.setItem(1, new ItemStack(Items.COAL, 64));
            context.assertTrue(pull(chute).isEmpty() && furnace.getItem(1).getCount() == 64,
                    "bottom cannot extract fuel even if the handler exposes that slot");
            chute.setExtractionSides(TOP_BOTTOM);
            furnace.setItem(0, new ItemStack(Items.RAW_IRON, 64));
            furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 64));
            context.assertTrue(pull(chute).is(Items.RAW_IRON), "first selected usable side");
            furnace.setItem(0, new ItemStack(Items.RAW_IRON, 64));
            context.assertTrue(pull(chute).is(Items.IRON_INGOT) && furnace.getItem(0).getCount() == 64,
                    "round robin serves the next face despite a refilled first face");

            furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 7));
            chute.setRule(0, FilterRule.item(new ItemStack(Items.IRON_INGOT), false));
            chute.setWhitelistMode(true);
            context.assertTrue(pull(chute).is(Items.IRON_INGOT) && furnace.getItem(0).getCount() == 64,
                    "filter-rejected face is skipped, not bypassed");
            chute.setWhitelistMode(false);
            furnace.setItem(0, ItemStack.EMPTY);
            // A selected top face must never be interpreted as the block ABOVE the attached container.
            world.setBlockAndUpdate(sourcePos.above(), Blocks.CHEST.defaultBlockState());
            var neighbor = (ChestBlockEntity) world.getBlockEntity(sourcePos.above());
            neighbor.setItem(0, new ItemStack(Items.DIAMOND, 64));
            chute.setExtractionSides(ExtractionSide.TOP.bit());
            context.assertTrue(pull(chute).isEmpty() && neighbor.getItem(0).getCount() == 64, "never extracts from neighboring blocks");
            chute.setExtractionSides(0);
            context.assertTrue(pull(chute).isEmpty(), "no selected sides stops extraction");
            ConveyorConfig.CHUTE_EXTRACTION_SIDES.set(false);
            context.assertTrue(pull(chute).is(Items.COAL) && chute.getExtractionSides() == 0,
                    "disabled feature uses original attached face and preserves saved empty mask");
            ConveyorConfig.CHUTE_EXTRACTION_SIDES.set(true);
            furnace.setItem(1, new ItemStack(Items.COAL));
            context.assertTrue(pull(chute).isEmpty(), "reenabling restores saved selection");
        } finally {
            ConveyorConfig.SPEEDS[0].set(speed);
            ConveyorConfig.CHUTE_EXTRACTION_SIDES.set(enabled);
        }
        context.succeed();
    }

    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void batchLimitsSharedStorageAndRedstone(GameTestHelper context) {
        double speed = ConveyorConfig.SPEEDS[0].get();
        boolean enabled = ConveyorConfig.CHUTE_EXTRACTION_SIDES.get();
        try {
            ConveyorConfig.SPEEDS[0].set(64.0);
            ConveyorConfig.CHUTE_EXTRACTION_SIDES.set(true);
            Block[] tiers = {BlockContent.CHUTE_BLOCK.get(), BlockContent.ADVANCED_CHUTE.get(), BlockContent.ULTIMATE_CHUTE.get()};
            for (int tier = 0; tier < tiers.length; tier++) {
                var chute = route(context, tiers[tier], 3 + tier * 4);
                var world = context.getLevel();
                world.setBlockAndUpdate(chute.getBlockPos().west(), Blocks.CHEST.defaultBlockState());
                var chest = (ChestBlockEntity) world.getBlockEntity(chute.getBlockPos().west());
                for (int slot = 0; slot < 20; slot++) chest.setItem(slot, new ItemStack(Items.GOLD_INGOT, 64));
                chute.setExtractionSides(ExtractionSide.ALL_MASK);
                int expected = 64 * ConveyorConfig.chuteStacks(tier + 1);
                var batch = pull(chute);
                context.assertTrue(batch.getCount() == expected && total(chest) + batch.getCount() == 1280,
                        "six views of one chest do not multiply throughput or duplicate items, tier " + tier);
                chute.setRedstoneMode(Mode.NEVER);
                context.assertTrue(pull(chute).isEmpty(), "redstone never applies to all selected faces");
                chute.setRedstoneMode(Mode.PULSE);
                chute.setExtractionSides(0);
                world.setBlockAndUpdate(chute.getBlockPos().above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
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
        context.succeed();
    }

    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void selectionPersistenceUpgradeAndMenuSecurity(GameTestHelper context) {
        boolean enabled = ConveyorConfig.CHUTE_EXTRACTION_SIDES.get();
        try {
            ConveyorConfig.CHUTE_EXTRACTION_SIDES.set(true);
            var world = context.getLevel();
            var pos = context.absolutePos(new BlockPos(5, 2, 5));
            world.setBlockAndUpdate(pos, BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
            var chute = (ChuteBlockEntity) world.getBlockEntity(pos);
            var legacy = chute.saveWithFullMetadata();
            legacy.remove("extractionSides");
            chute.load(legacy);
            context.assertTrue(chute.getExtractionSides() == ExtractionSide.DEFAULT_MASK, "legacy save uses original attached face");
            for (int invalid : new int[]{-1, 64, Integer.MAX_VALUE}) {
                legacy.putInt("extractionSides", invalid);
                chute.load(legacy);
                context.assertTrue(chute.getExtractionSides() == ExtractionSide.DEFAULT_MASK, "corrupt saved bitmask defaults safely");
            }
            for (int mask : new int[]{0, TOP_BOTTOM, 63}) {
                chute.setExtractionSides(mask);
                var restored = new ChuteBlockEntity(pos, world.getBlockState(pos));
                restored.load(chute.saveWithFullMetadata());
                context.assertTrue(restored.getExtractionSides() == mask, "saved mask including zero survives reload");
            }
            chute.setExtractionSides(TOP_BOTTOM);
            chute.setRule(0, FilterRule.item(new ItemStack(Items.IRON_INGOT), false));
            var player = context.makeMockSurvivalPlayer();
            player.setPosRaw(pos.getX(), pos.getY(), pos.getZ());
            var menu = new ChuteScreenHandler(51, player.getInventory(), chute);
            menu.setCarried(new ItemStack(Items.DIAMOND, 32));
            int[] properties = new int[4];
            menu.addSlotListener(new ContainerListener() {
                @Override public void slotChanged(AbstractContainerMenu handler, int slot, ItemStack stack) {}
                @Override public void dataChanged(AbstractContainerMenu handler, int id, int value) { properties[id] = value; }
            });
            long filterRevision = chute.getFilterRevision();
            context.assertTrue(menu.clickMenuButton(player, ChuteScreenHandler.EXTRACTION_BUTTON_BASE + ExtractionSide.RIGHT.ordinal()), "menu toggles one face");
            context.assertTrue(properties[1] == (TOP_BOTTOM | ExtractionSide.RIGHT.bit()) && properties[2] == 1
                    && properties[3] == Direction.WEST.get3DDataValue(), "native menu properties synchronize mask, enable switch and orientation");
            context.assertTrue(chute.getFilterRevision() == filterRevision && menu.getCarried().getCount() == 32,
                    "side changes neither resend filter edits nor consume cursor items");
            var intruder = context.makeMockSurvivalPlayer();
            intruder.setPosRaw(pos.getX(), pos.getY(), pos.getZ());
            context.assertFalse(menu.clickMenuButton(intruder, ChuteScreenHandler.EXTRACTION_BUTTON_BASE), "another player cannot use this menu");
            for (int invalid : new int[]{-1, 8, 99, Integer.MIN_VALUE, Integer.MAX_VALUE})
                context.assertFalse(menu.clickMenuButton(player, invalid), "invalid side button refused");
            player.setPosRaw(pos.getX() + 40, pos.getY(), pos.getZ());
            context.assertFalse(menu.clickMenuButton(player, ChuteScreenHandler.EXTRACTION_BUTTON_BASE), "out-of-range edit refused");
            player.setPosRaw(pos.getX(), pos.getY(), pos.getZ());
            int selected = chute.getExtractionSides();
            ConveyorConfig.CHUTE_EXTRACTION_SIDES.set(false);
            menu.broadcastChanges();
            context.assertTrue(properties[2] == 0 && properties[1] == selected, "global switch synchronizes without clearing choices");
            context.assertFalse(menu.clickMenuButton(player, ChuteScreenHandler.EXTRACTION_BUTTON_BASE), "server rejects edits while feature disabled");
            ConveyorConfig.CHUTE_EXTRACTION_SIDES.set(true);
            player.setShiftKeyDown(true);
            for (var upgrade : new Block[]{BlockContent.ADVANCED_CHUTE.get(), BlockContent.ULTIMATE_CHUTE.get()}) {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(upgrade));
                context.assertTrue(UpgradeInteractions.chute(world, pos, player, player.getMainHandItem(), (ChuteBlock) upgrade), "in-place tier upgrade");
                chute = (ChuteBlockEntity) world.getBlockEntity(pos);
                context.assertTrue(chute.getExtractionSides() == selected && chute.getOwnFacing() == Direction.WEST
                        && !chute.getRule(0).isEmpty(), "upgrade retains sides, orientation and filter rules");
            }
            context.assertFalse(menu.clickMenuButton(player, ChuteScreenHandler.EXTRACTION_BUTTON_BASE), "stale menu cannot edit replaced interface");
        } finally { ConveyorConfig.CHUTE_EXTRACTION_SIDES.set(enabled); }
        context.succeed();
    }

    private static ChuteBlockEntity route(GameTestHelper context, Block block, int z) {
        var world = context.getLevel();
        var pos = context.absolutePos(new BlockPos(3, 2, z));
        var target = pos.east(8);
        world.setBlockAndUpdate(pos, block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        world.setBlockAndUpdate(target, BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        var chute = (ChuteBlockEntity) world.getBlockEntity(pos);
        context.assertTrue(chute.connectOutgoing(Direction.EAST, target, Direction.WEST, java.util.List.of(), 1), "test belt created");
        return chute;
    }
    private static ItemStack pull(ChuteBlockEntity chute) {
        chute.pickupAccess(Direction.EAST).items().clear();
        chute.tick(chute.getLevel(), chute.getBlockPos(), chute.getBlockState(), chute);
        var queue = chute.pickupAccess(Direction.EAST).items();
        if (queue.size() > 1) throw new AssertionError("One extraction step spawned multiple batches");
        return queue.isEmpty() ? ItemStack.EMPTY : queue.getFirst().stack.copy();
    }
    private static int total(ChestBlockEntity chest) {
        int total = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) total += chest.getItem(i).getCount();
        return total;
    }
}
