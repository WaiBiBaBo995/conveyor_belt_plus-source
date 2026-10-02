package pureneko.conveyor_belt_plus;

import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.registry.ItemContent;
import pureneko.conveyor_belt_plus.registry.BlockContent;
import pureneko.conveyor_belt_plus.registry.ComponentContent;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.api.RtsAPI;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pureneko.conveyor_belt_plus.blocks.*;
import pureneko.conveyor_belt_plus.blocks.BeltPickup.Result;
import pureneko.conveyor_belt_plus.compat.rts.RtsBeltDrafts;
import pureneko.conveyor_belt_plus.compat.rts.RtsCompat;
import pureneko.conveyor_belt_plus.util.SplineUtil;

/** Compiled/run only with -PwithRts=true: actual RTS 1.1.7, not a simulated API. */
@GameTestHolder(ConveyorBeltPlus.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConveyorRtsIntegrationTests {
    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID, timeoutTicks = 200)
    public static void rtsStorageAndInventoryPlacement(GameTestHelper ctx) {
        var world = ctx.getLevel();
        boolean progression = Config.ENABLE_SURVIVAL_PROGRESSION.get();
        var player = new net.neoforged.neoforge.common.util.FakePlayer(ctx.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ConveyorRTSTest"));
        try {
            Config.ENABLE_SURVIVAL_PROGRESSION.set(false);
            player.setGameMode(GameType.SURVIVAL);
            var playerPos = ctx.absolutePos(new BlockPos(7, 2, 7)).getCenter();
            player.setPosRaw(playerPos.x, playerPos.y, playerPos.z);
            RtsCameraManager.start(player);
            ctx.assertTrue(RtsCompat.active(player), "real RTS camera activated");
            var api = RtsAPI.get();
            api.bindings().setBdNetworkEnabled(player, false);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK, 7));
            player.getInventory().setItem(15, new ItemStack(ItemContent.ADVANCED_BELT.get(), 2));
            for (int line = 0; line < 2; line++) {
                var start = ctx.absolutePos(new BlockPos(2, 2, 3 + line * 6));
                var end = ctx.absolutePos(new BlockPos(12, 2, 3 + line * 6));
                world.setBlockAndUpdate(start, BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
                world.setBlockAndUpdate(end, BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
                ChestBlockEntity chest = null;
                if (line == 1) {
                    var storagePos = ctx.absolutePos(new BlockPos(7, 2, 13));
                    world.setBlockAndUpdate(storagePos, Blocks.CHEST.defaultBlockState());
                    chest = (ChestBlockEntity) world.getBlockEntity(storagePos);
                    chest.setItem(0, new ItemStack(ItemContent.ADVANCED_BELT.get(), 3));
                    api.bindings().linkStorage(player, storagePos, (byte) 0);
                    ctx.assertTrue(api.storage().countItemsMatching(player, s -> s.is(ItemContent.ADVANCED_BELT.get())) >= 3, "warehouse linked through RTS public API");
                }
                int before = player.getInventory().countItem(ItemContent.ADVANCED_BELT.get()) + count(chest);
                click(player, start, Direction.EAST);
                ctx.assertTrue(player.getInventory().countItem(ItemContent.ADVANCED_BELT.get()) + count(chest) == before, "first click does not consume a belt");
                click(player, end, Direction.WEST);
                var chute = (ChuteBlockEntity) world.getBlockEntity(start);
                ctx.assertTrue(end.equals(chute.getTarget()) && chute.getBeltTier() == 2, "RTS two-click placement keeps draft across extracted stacks");
                ctx.assertTrue(player.getInventory().countItem(ItemContent.ADVANCED_BELT.get()) + count(chest) == before - 1, "exactly one belt consumed from selected source");
                ctx.assertTrue(player.getMainHandItem().is(Items.STICK) && player.getMainHandItem().getCount() == 7, "real held item restored");
                if (chest != null) for (int slot = 0; slot < chest.getContainerSize(); slot++)
                    ctx.assertFalse(chest.getItem(slot).has(ComponentContent.BELT_START.get()), "storage has no leaked selection components");
            }
            ctx.succeed();
        } finally {
            RtsBeltDrafts.reset(player);
            RtsCameraManager.stopIfActive(player);
            Config.ENABLE_SURVIVAL_PROGRESSION.set(progression);
        }
    }

    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID, timeoutTicks = 200)
    public static void rtsRemotePickupAuthorization(GameTestHelper ctx) {
        var world = ctx.getLevel();
        boolean progression = Config.ENABLE_SURVIVAL_PROGRESSION.get();
        var player = new net.neoforged.neoforge.common.util.FakePlayer(ctx.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ConveyorRTSTest"));
        try {
            Config.ENABLE_SURVIVAL_PROGRESSION.set(false);
            player.setGameMode(GameType.SURVIVAL);
            var start = ctx.absolutePos(new BlockPos(2, 2, 5));
            var end = ctx.absolutePos(new BlockPos(12, 2, 5));
            var playerPos = ctx.absolutePos(new BlockPos(7, 2, 13)).getCenter();
            player.setPosRaw(playerPos.x, playerPos.y, playerPos.z);
            world.setBlockAndUpdate(start, BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
            world.setBlockAndUpdate(end, BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
            var chute = (ChuteBlockEntity) world.getBlockEntity(start);
            ctx.assertTrue(chute.connectOutgoing(Direction.EAST, end, Direction.WEST, java.util.List.of(), 1), "pickup route");
            var nbt = chute.saveWithFullMetadata(world.registryAccess());
            var moving = new ListTag();
            moving.add(new ChuteBlockEntity.BeltItem(.5f, 456, new ItemStack(Items.DIAMOND, 128)).write(world.registryAccess(), "a", "b"));
            nbt.put("moving", moving); chute.loadWithComponents(nbt, world.registryAccess());
            RtsCameraManager.start(player, true);
            var eye = RtsCameraManager.getCameraPosition(player);
            ctx.assertTrue(eye != null, "server camera exists");
            var point = SplineUtil.getPositionOnSpline(chute.getBeltData(), .5f);
            var ray = point.subtract(eye).normalize();
            ctx.assertTrue(RtsCompat.validRay(player, eye, ray), "camera ray allowed");
            ctx.assertFalse(RtsCompat.validRay(player, eye.add(10, 0, 0), ray), "forged origin rejected");
            ctx.assertFalse(RtsCompat.validRay(player, eye, new Vec3(Double.NaN, 0, 0)), "NaN ray rejected");
            ctx.assertFalse(RtsCompat.canTake(player, start.offset(10000, 0, 0)), "outside action area rejected without loading chunk");
            Config.ENABLE_SURVIVAL_PROGRESSION.set(true);
            ctx.assertFalse(RtsCompat.canTake(player, start), "missing RTS interaction capability rejected");
            Config.ENABLE_SURVIVAL_PROGRESSION.set(false);
            var stop = eye.add(ray.scale(128));
            ctx.assertTrue(RtsCompat.canTake(player, start), "RTS owner allowed");
            var hit = BeltPickup.rayHit(BeltPickup.bounds(point), eye, stop);
            ctx.assertTrue(hit != null && BeltPickup.unobstructed(player, eye, hit), "test ray is clear inside the GameTest barrier enclosure");
            ctx.assertTrue(BeltPickup.takeRemote(player, start, Direction.EAST, 456, Float.NaN, eye, stop).taken() == 0, "invalid progress cannot take");
            java.util.function.Consumer<net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock> deny = event -> {
                if (event.getEntity() == player) event.setCanceled(true);
            };
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(deny);
            try { ctx.assertTrue(BeltPickup.takeRemote(player, start, Direction.EAST, 456, .5f, eye, stop).taken() == 0, "claim event veto respected"); }
            finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(deny); }
            // RTS empty-hand selection need not mutate the player's real held stack.
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            var taken = BeltPickup.takeRemote(player, start, Direction.EAST, 456, .5f, eye, stop);
            ctx.assertTrue(taken.taken() == 128 && player.getInventory().countItem(Items.DIAMOND) == 128, "remote render pickup delivers legal stacks and exact quantity");
            ctx.assertTrue(BeltPickup.takeRemote(player, start, Direction.EAST, 456, .5f, eye, stop).taken() == 0, "replay cannot duplicate items");
            RtsCameraManager.stopIfActive(player);
            ctx.assertFalse(RtsCompat.validRay(player, eye, ray), "closed RTS camera revokes remote access");
            ctx.succeed();
        } finally {
            RtsCameraManager.stopIfActive(player);
            Config.ENABLE_SURVIVAL_PROGRESSION.set(progression);
        }
    }
    private static int count(ChestBlockEntity chest) {
        if (chest == null) return 0;
        int count = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) if (chest.getItem(slot).is(ItemContent.ADVANCED_BELT.get())) count += chest.getItem(slot).getCount();
        return count;
    }
    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID, timeoutTicks = 200)
    public static void rtsFluidContainerPickup(GameTestHelper ctx) {
        boolean progression = Config.ENABLE_SURVIVAL_PROGRESSION.get();
        var player = new net.neoforged.neoforge.common.util.FakePlayer(ctx.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ConveyorFluidRTS"));
        try {
            Config.ENABLE_SURVIVAL_PROGRESSION.set(false);
            player.setGameMode(GameType.SURVIVAL);
            var start = ctx.absolutePos(new BlockPos(2, 2, 5));
            var end = ctx.absolutePos(new BlockPos(12, 2, 5));
            player.setPos(ctx.absolutePos(new BlockPos(7, 2, 13)).getCenter());
            ctx.getLevel().setBlockAndUpdate(start, BlockContent.FLUID_CHUTE.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
            ctx.getLevel().setBlockAndUpdate(end, BlockContent.FLUID_CHUTE.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
            var chute = (ChuteBlockEntity) ctx.getLevel().getBlockEntity(start);
            ctx.assertTrue(chute.connectOutgoing(Direction.EAST, end, Direction.WEST, java.util.List.of(), 1), "RTS fluid route");
            var parcel = pureneko.conveyor_belt_plus.util.FluidPackets.create(
                    new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 16000));
            chute.pickupAccess(Direction.EAST).items().add(new ChuteBlockEntity.BeltItem(.5f, 891, parcel));
            RtsCameraManager.start(player, true);
            var eye = RtsCameraManager.getCameraPosition(player);
            var point = SplineUtil.getPositionOnSpline(chute.getBeltData(), .5f);
            var ray = point.subtract(eye).normalize();
            var stop = eye.add(ray.scale(128));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK, 7));
            ctx.assertTrue(BeltPickup.takeRemote(player, start, Direction.EAST, 891, .5f, eye, stop, "minecraft:bucket").taken() == 0,
                    "client cannot manufacture a selected container");
            var storagePos = ctx.absolutePos(new BlockPos(10, 2, 13));
            ctx.getLevel().setBlockAndUpdate(storagePos, Blocks.CHEST.defaultBlockState());
            var chest = (ChestBlockEntity) ctx.getLevel().getBlockEntity(storagePos);
            chest.setItem(0, new ItemStack(Items.BUCKET, 2));
            RtsAPI.get().bindings().setBdNetworkEnabled(player, false);
            RtsAPI.get().bindings().linkStorage(player, storagePos, (byte) 0);
            var result = BeltPickup.takeRemote(player, start, Direction.EAST, 891, .5f, eye, stop, "minecraft:bucket");
            ctx.assertTrue(result.taken() == 1000 && result.remaining() == 15000, "RTS fills a selected linked bucket with one bucket of fluid");
            int empty = 0, full = 0;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                if (chest.getItem(i).is(Items.BUCKET)) empty += chest.getItem(i).getCount();
                if (chest.getItem(i).is(Items.WATER_BUCKET)) full += chest.getItem(i).getCount();
            }
            ctx.assertTrue(empty == 1 && full == 1 && player.getMainHandItem().is(Items.STICK)
                    && player.getMainHandItem().getCount() == 7, "actual storage container is replaced and held item is preserved");
            Config.ENABLE_SURVIVAL_PROGRESSION.set(true);
            ctx.assertTrue(BeltPickup.takeRemote(player, start, Direction.EAST, 891, .5f, eye, stop, "minecraft:bucket").taken() == 0,
                    "RTS progression also protects fluid pickup");
            ctx.succeed();
        } finally {
            RtsCameraManager.stopIfActive(player);
            Config.ENABLE_SURVIVAL_PROGRESSION.set(progression);
        }
    }

    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID, timeoutTicks = 200)
    public static void rtsBeltInsertionAndRejectedPlacement(GameTestHelper ctx) {
        var world = ctx.getLevel();
        boolean progression = Config.ENABLE_SURVIVAL_PROGRESSION.get();
        var player = new net.neoforged.neoforge.common.util.FakePlayer(world,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ConveyorInsertRTS"));
        try {
            Config.ENABLE_SURVIVAL_PROGRESSION.set(false);
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(ctx.absolutePos(new BlockPos(7, 2, 13)).getCenter());
            var start = ctx.absolutePos(new BlockPos(2, 2, 5));
            var end = start.east(10);
            world.setBlockAndUpdate(start, BlockContent.UNIVERSAL_CHUTE.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
            world.setBlockAndUpdate(end, BlockContent.UNIVERSAL_CHUTE.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
            var chute = (ChuteBlockEntity) world.getBlockEntity(start);
            ctx.assertTrue(chute.connectOutgoing(Direction.EAST, end, Direction.WEST, java.util.List.of(), 1), "RTS insertion route");
            RtsCameraManager.start(player, true);
            var eye = RtsCameraManager.getCameraPosition(player);
            var ray = SplineUtil.getPositionOnSpline(chute.getBeltData(), .5f).subtract(eye).normalize();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK, 7));
            var selected = new ItemStack(Items.IRON_INGOT);
            selected.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("RTS selected variant"));
            ctx.assertFalse(BeltInsertion.putRemote(player, start, Direction.EAST, eye, ray, selected), "client preview cannot manufacture items");
            var storage = ctx.absolutePos(new BlockPos(10, 2, 13));
            world.setBlockAndUpdate(storage, Blocks.CHEST.defaultBlockState());
            var chest = (ChestBlockEntity) world.getBlockEntity(storage);
            chest.setItem(0, new ItemStack(Items.IRON_INGOT, 64));
            chest.setItem(1, selected.copyWithCount(17));
            RtsAPI.get().bindings().setBdNetworkEnabled(player, false);
            RtsAPI.get().bindings().linkStorage(player, storage, (byte) 0);
            ctx.assertFalse(BeltInsertion.putRemote(player, start, Direction.EAST, eye.add(10, 0, 0), ray, selected), "forged remote origin cannot extract storage");
            Config.ENABLE_SURVIVAL_PROGRESSION.set(true);
            ctx.assertFalse(BeltInsertion.putRemote(player, start, Direction.EAST, eye, ray, selected), "RTS progression protects insertion");
            Config.ENABLE_SURVIVAL_PROGRESSION.set(false);
            ctx.assertTrue(BeltInsertion.putRemote(player, start, Direction.EAST, eye, ray, selected), "selected storage variant enters the belt");
            ctx.assertTrue(chest.getItem(0).getCount() == 64 && chest.getItem(1).isEmpty()
                    && chute.pickupAccess(Direction.EAST).items().getFirst().stack.getCount() == 17,
                    "authoritative storage extraction preserves variant and count");
            ctx.assertTrue(player.getMainHandItem().is(Items.STICK) && player.getMainHandItem().getCount() == 7, "remote storage insertion preserves held item");
            var packet = pureneko.conveyor_belt_plus.util.FluidPackets.create(
                    new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 64000));
            chest.setItem(2, packet.copy());
            var fluidRay = SplineUtil.getPositionOnSpline(chute.getBeltData(), .25f).subtract(eye).normalize();
            ctx.assertTrue(BeltInsertion.putRemote(player, start, Direction.EAST, eye, fluidRay, packet), "RTS storage fluid_packet enters fluid transport");
            ctx.assertTrue(chest.getItem(2).isEmpty()
                    && pureneko.conveyor_belt_plus.util.FluidPackets.amount(chute.pickupAccess(Direction.EAST).items().getFirst().stack) == 64000,
                    "RTS fluid packet conserves every mB");
            ctx.assertFalse(BeltInsertion.putRemote(player, start, Direction.EAST, eye, fluidRay, packet), "replay cannot duplicate stored fluid");
            var heldRay = SplineUtil.getPositionOnSpline(chute.getBeltData(), .75f).subtract(eye).normalize();
            ctx.assertTrue(BeltInsertion.putRemote(player, start, Direction.EAST, eye, heldRay, player.getMainHandItem().copy()), "RTS held-stack mode also inserts");
            ctx.assertTrue(player.getMainHandItem().isEmpty() && chute.pickupAccess(Direction.EAST).items().getLast().stack.getCount() == 7, "RTS held stack consumed exactly once");
            var badStart = start.south(4);
            var badEnd = badStart.east(10);
            world.setBlockAndUpdate(badStart, BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
            world.setBlockAndUpdate(badEnd.east(), Blocks.STONE.defaultBlockState());
            chest.setItem(3, new ItemStack(ItemContent.ADVANCED_BELT.get(), 2));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK, 7));
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ItemContent.FLUID_CHUTE.get()));
            click(player, badStart, Direction.WEST);
            click(player, badEnd.east(), Direction.WEST);
            ctx.assertTrue(count(chest) == 2 && player.getOffhandItem().is(ItemContent.FLUID_CHUTE.get())
                    && player.getOffhandItem().getCount() == 1 && world.isEmptyBlock(badEnd), "RTS rejected construction returns belt to storage and interface to offhand");
            ctx.assertTrue(world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(badStart.getCenter(), badEnd.getCenter()).inflate(1), e -> true).isEmpty(), "RTS rejected construction spawns no drops");
            RtsCameraManager.stopIfActive(player);
            ctx.assertFalse(BeltInsertion.putRemote(player, start, Direction.EAST, eye, ray, selected), "inactive RTS session rejected");
            ctx.succeed();
        } finally {
            RtsBeltDrafts.reset(player);
            RtsCameraManager.stopIfActive(player);
            Config.ENABLE_SURVIVAL_PROGRESSION.set(progression);
        }
    }

    private static void click(ServerPlayer player, BlockPos pos, Direction face) {
        var hit = pos.getCenter();
        var eye = RtsCameraManager.getCameraPosition(player);
        var ray = hit.subtract(eye).normalize();
        RtsAPI.get().interaction().interactTarget(player, -1, pos, face, hit.x, hit.y, hit.z,
                com.rtsbuilding.rtsbuilding.network.builder.C2SRtsInteractPayload.SOURCE_PIN_ITEM,
                (byte) 0, "conveyor_belt_plus:advanced_belt", eye.x, eye.y, eye.z, ray.x, ray.y, ray.z);
    }
}
