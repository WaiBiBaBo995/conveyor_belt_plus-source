package pureneko.conveyor_belt_plus;

import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.registry.ItemContent;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import pureneko.conveyor_belt_plus.blocks.*;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity.BeltItem;
import pureneko.conveyor_belt_plus.config.ConveyorConfig;
import pureneko.conveyor_belt_plus.screen.ChuteScreenHandler;
import pureneko.conveyor_belt_plus.util.RedstoneControl.Mode;

@GameTestHolder(ConveyorBeltPlus.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConveyorRedstoneTests {
    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void redstoneInsertionAndPulsePersistence(GameTestHelper context) {
        var world = context.getLevel();
        var pos = context.absolutePos(new BlockPos(5, 2, 5));
        world.setBlockAndUpdate(pos.west(), Blocks.CHEST.defaultBlockState());
        world.setBlockAndUpdate(pos, BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        var chute = (ChuteBlockEntity) world.getBlockEntity(pos);
        var chest = (ChestBlockEntity) world.getBlockEntity(pos.west());
        var batch = new ItemStack(Items.IRON_INGOT, 64);
        context.assertTrue(chute.getRedstoneMode() == Mode.ALWAYS && chute.acceptFromBelt(batch, Direction.EAST), "old/new interfaces default always active");
        for (var mode : Mode.values()) {
            world.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
            chute.setRedstoneMode(mode);
            context.assertTrue(chute.acceptFromBelt(batch, Direction.EAST) == (mode == Mode.ALWAYS || mode == Mode.LOW), "unpowered insertion for " + mode);
            world.setBlockAndUpdate(pos.above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            context.assertTrue(chute.acceptFromBelt(batch, Direction.EAST) == (mode == Mode.ALWAYS || mode == Mode.HIGH || mode == Mode.PULSE), "powered insertion for " + mode);
            if (mode == Mode.PULSE) context.assertFalse(chute.acceptFromBelt(batch, Direction.EAST), "steady high consumes only one incoming batch");
        }
        // Switching to pulse while already powered must not manufacture a rising edge.
        chute.setRedstoneMode(Mode.ALWAYS); chute.setRedstoneMode(Mode.PULSE);
        context.assertFalse(chute.acceptFromBelt(batch, Direction.EAST), "switching while powered does not fire");
        world.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
        world.setBlockAndUpdate(pos.above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        for (int i = 0; i < chest.getContainerSize(); i++) chest.setItem(i, new ItemStack(Items.STONE, 64));
        context.assertFalse(chute.acceptFromBelt(batch, Direction.EAST), "blocked inventory does not consume pulse");
        var saved = chute.saveWithFullMetadata();
        context.assertTrue(saved.getBoolean("redstonePulsePending"), "blocked pulse is saved");
        chute.load(saved);
        chest.setItem(0, ItemStack.EMPTY);
        context.assertTrue(chute.acceptFromBelt(batch, Direction.EAST), "saved pending pulse resumes once");
        chest.setItem(1, ItemStack.EMPTY);
        context.assertFalse(chute.acceptFromBelt(batch, Direction.EAST), "restore does not duplicate the pulse");
        // Upgrade a still-pending pulse and the redstone mode together.
        world.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
        world.setBlockAndUpdate(pos.above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        var player = context.makeMockSurvivalPlayer();
        player.setShiftKeyDown(true); player.setPosRaw(pos.getX(), pos.getY(), pos.getZ());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemContent.ADVANCED_CHUTE.get()));
        context.assertTrue(UpgradeInteractions.chute(world, pos, player, player.getMainHandItem(), (ChuteBlock) BlockContent.ADVANCED_CHUTE.get()), "redstone chute upgrade");
        chute = (ChuteBlockEntity) world.getBlockEntity(pos);
        context.assertTrue(chute.getRedstoneMode() == Mode.PULSE && chute.acceptFromBelt(batch, Direction.EAST), "upgrade retains pending pulse and mode");
        var menu = new ChuteScreenHandler(42, player.getInventory(), chute);
        context.assertTrue(menu.getRedstoneMode() == Mode.PULSE, "menu reads saved mode");
        context.assertTrue(menu.clickMenuButton(player, ChuteScreenHandler.REDSTONE_BUTTON)
                && chute.getRedstoneMode() == Mode.ALWAYS, "menu button cycles modes on server");
        context.assertFalse(menu.clickMenuButton(player, 99), "invalid menu button refused");
        player.setPosRaw(pos.getX() + 40, pos.getY(), pos.getZ());
        context.assertFalse(menu.clickMenuButton(player, ChuteScreenHandler.REDSTONE_BUTTON), "remote player cannot change mode");
        context.succeed();
    }

    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void redstoneExtractionAndBeltMotion(GameTestHelper context) {
        context.assertTrue(ConveyorConfig.SPEC.isLoaded(), "native SERVER configuration loaded by MinecraftForge");
        double speed = ConveyorConfig.SPEEDS[0].get();
        try {
            ConveyorConfig.SPEEDS[0].set(64.0); // One-tick extraction interval for deterministic checks.
            var world = context.getLevel();
            var pos = context.absolutePos(new BlockPos(3, 2, 5));
            var target = pos.east(8);
            world.setBlockAndUpdate(pos.west(), Blocks.CHEST.defaultBlockState());
            var chest = (ChestBlockEntity) world.getBlockEntity(pos.west());
            for (int i = 0; i < 8; i++) chest.setItem(i, new ItemStack(Items.GOLD_INGOT, 64));
            world.setBlockAndUpdate(pos, BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
            world.setBlockAndUpdate(target, BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
            var chute = (ChuteBlockEntity) world.getBlockEntity(pos);
            context.assertTrue(chute.connectOutgoing(Direction.EAST, target, Direction.WEST, java.util.List.of(), 1), "test belt");
            chute.setRedstoneMode(Mode.NEVER);
            chute.tick(world, pos, world.getBlockState(pos), chute);
            context.assertFalse(chute.getMovingItems().iterator().hasNext(), "never mode blocks extraction");
            chute.setRedstoneMode(Mode.PULSE);
            chute.tick(world, pos, world.getBlockState(pos), chute);
            context.assertFalse(chute.getMovingItems().iterator().hasNext(), "pulse mode waits for rising edge");
            world.setBlockAndUpdate(pos.above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            chute.tick(world, pos, world.getBlockState(pos), chute);
            var packet = chute.getMovingItems().iterator().next();
            context.assertTrue(packet.stack.getCount() == 64, "one configured extraction batch per pulse");
            for (int i = 0; i < 8; i++) chute.tick(world, pos, world.getBlockState(pos), chute);
            context.assertTrue(chute.pickupAccess(Direction.EAST).items().size() == 1, "constant high does not repeatedly extract");
            packet.progress(.2f);
            chute.setRedstoneMode(Mode.NEVER);
            chute.tick(world, pos, world.getBlockState(pos), chute);
            context.assertTrue(packet.progress > .2f, "disabling interface does not stop in-flight belt items");
            for (var mode : new Mode[]{Mode.LOW, Mode.HIGH}) {
                for (boolean power : new boolean[]{false, true}) {
                    chute.pickupAccess(Direction.EAST).items().clear();
                    world.setBlockAndUpdate(pos.above(), power ? Blocks.REDSTONE_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    chute.setRedstoneMode(mode);
                    chute.tick(world, pos, world.getBlockState(pos), chute);
                    context.assertTrue(chute.getMovingItems().iterator().hasNext() == (mode == Mode.HIGH ? power : !power), "extraction follows " + mode + "/" + power);
                }
            }
        } finally { ConveyorConfig.SPEEDS[0].set(speed); }
        context.succeed();
    }

    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void splitAroundBlockedOutputs(GameTestHelper context) {
        var world = context.getLevel();
        var pos = context.absolutePos(new BlockPos(7, 2, 7));
        world.setBlockAndUpdate(pos, BlockContent.SPLITTER.get().defaultBlockState());
        var splitter = (ConveyorSplitterBlockEntity) world.getBlockEntity(pos);
        splitter.connectIncoming(Direction.WEST, pos.west(5));
        for (var direction : new Direction[]{Direction.EAST, Direction.NORTH, Direction.SOUTH}) {
            var end = pos.relative(direction, 6);
            world.setBlockAndUpdate(end, BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, direction.getOpposite()));
            context.assertTrue(splitter.connectOutgoing(direction, end, direction.getOpposite(), java.util.List.of(), 1), "splitter route " + direction);
        }
        fill(splitter, Direction.EAST);
        context.assertTrue(splitter.acceptFromBelt(new ItemStack(Items.IRON_INGOT, 64), Direction.WEST), "input batch");
        splitter.tick(world, pos, world.getBlockState(pos), splitter);
        context.assertTrue(head(splitter, Direction.NORTH) == 32 && head(splitter, Direction.SOUTH) == 32
                && splitter.pickupAccess(Direction.EAST).items().size() == 64 && splitter.getCachedItemCount() == 0,
                "one full output: remaining two get half each");
        fill(splitter, Direction.NORTH); splitter.pickupAccess(Direction.SOUTH).items().clear();
        splitter.acceptFromBelt(new ItemStack(Items.IRON_INGOT, 64), Direction.WEST);
        splitter.tick(world, pos, world.getBlockState(pos), splitter);
        context.assertTrue(head(splitter, Direction.SOUTH) == 64, "two full outputs: remaining output gets entire batch");
        fill(splitter, Direction.SOUTH);
        splitter.acceptFromBelt(new ItemStack(Items.IRON_INGOT, 63), Direction.WEST);
        splitter.tick(world, pos, world.getBlockState(pos), splitter);
        context.assertTrue(splitter.getCachedItemCount() == 63, "all full: cache retained");
        for (var direction : new Direction[]{Direction.EAST, Direction.NORTH, Direction.SOUTH}) splitter.pickupAccess(direction).items().clear();
        splitter.tick(world, pos, world.getBlockState(pos), splitter);
        context.assertTrue(head(splitter, Direction.EAST) == 21 && head(splitter, Direction.NORTH) == 21
                && head(splitter, Direction.SOUTH) == 21 && splitter.getCachedItemCount() == 0, "all recovered: automatic three-way even split");
        context.succeed();
    }
    private static void fill(ConveyorSplitterBlockEntity splitter, Direction direction) {
        var queue = splitter.pickupAccess(direction).items(); queue.clear();
        for (int i = 0; i < 64; i++) queue.add(new ChuteBlockEntity.BeltItem(i + 1000, new ItemStack(Items.STONE)));
    }
    private static int head(ConveyorSplitterBlockEntity splitter, Direction direction) {
        return splitter.pickupAccess(direction).items().getFirst().stack.getCount();
    }
}
