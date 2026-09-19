package pureneko.conveyor_belt_plus;

import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.registry.ItemContent;
import pureneko.conveyor_belt_plus.registry.BlockContent;
import pureneko.conveyor_belt_plus.registry.ComponentContent;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.api.RtsAPI;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pureneko.conveyor_belt_plus.blocks.*;
import pureneko.conveyor_belt_plus.compat.rts.RtsBeltDrafts;
import pureneko.conveyor_belt_plus.compat.rts.RtsCompat;
import pureneko.conveyor_belt_plus.util.SplineUtil;

/** Compiled/run only with -PwithRts=true: actual RTS 1.1.7, not a simulated API. */
@GameTestHolder(ConveyorBeltPlus.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConveyorRtsIntegrationTests {
    @GameTest(templateName = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID, tickLimit = 200)
    public static void rtsStorageAndInventoryPlacement(TestContext ctx) {
        var world = ctx.getWorld();
        boolean progression = Config.ENABLE_SURVIVAL_PROGRESSION.get();
        var player = new net.neoforged.neoforge.common.util.FakePlayer(ctx.getWorld(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ConveyorRTSTest"));
        try {
            Config.ENABLE_SURVIVAL_PROGRESSION.set(false);
            player.changeGameMode(GameMode.SURVIVAL);
            var playerPos = ctx.getAbsolutePos(new BlockPos(7, 2, 7)).toCenterPos();
            player.setPos(playerPos.x, playerPos.y, playerPos.z);
            RtsCameraManager.start(player);
            ctx.assertTrue(RtsCompat.active(player), "real RTS camera activated");
            var api = RtsAPI.get();
            api.bindings().setBdNetworkEnabled(player, false);
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK, 7));
            player.getInventory().setStack(15, new ItemStack(ItemContent.ADVANCED_BELT.get(), 2));
            for (int line = 0; line < 2; line++) {
                var start = ctx.getAbsolutePos(new BlockPos(2, 2, 3 + line * 6));
                var end = ctx.getAbsolutePos(new BlockPos(12, 2, 3 + line * 6));
                world.setBlockState(start, BlockContent.CHUTE_BLOCK.get().getDefaultState().with(HorizontalFacingBlock.FACING, Direction.EAST));
                world.setBlockState(end, BlockContent.CHUTE_BLOCK.get().getDefaultState().with(HorizontalFacingBlock.FACING, Direction.WEST));
                ChestBlockEntity chest = null;
                if (line == 1) {
                    var storagePos = ctx.getAbsolutePos(new BlockPos(7, 2, 13));
                    world.setBlockState(storagePos, Blocks.CHEST.getDefaultState());
                    chest = (ChestBlockEntity) world.getBlockEntity(storagePos);
                    chest.setStack(0, new ItemStack(ItemContent.ADVANCED_BELT.get(), 3));
                    api.bindings().linkStorage(player, storagePos, (byte) 0);
                    ctx.assertTrue(api.storage().countItemsMatching(player, s -> s.isOf(ItemContent.ADVANCED_BELT.get())) >= 3, "warehouse linked through RTS public API");
                }
                int before = player.getInventory().count(ItemContent.ADVANCED_BELT.get()) + count(chest);
                click(player, start, Direction.EAST);
                ctx.assertTrue(player.getInventory().count(ItemContent.ADVANCED_BELT.get()) + count(chest) == before, "first click does not consume a belt");
                click(player, end, Direction.WEST);
                var chute = (ChuteBlockEntity) world.getBlockEntity(start);
                ctx.assertTrue(end.equals(chute.getTarget()) && chute.getBeltTier() == 2, "RTS two-click placement keeps draft across extracted stacks");
                ctx.assertTrue(player.getInventory().count(ItemContent.ADVANCED_BELT.get()) + count(chest) == before - 1, "exactly one belt consumed from selected source");
                ctx.assertTrue(player.getMainHandStack().isOf(Items.STICK) && player.getMainHandStack().getCount() == 7, "real held item restored");
                if (chest != null) for (int slot = 0; slot < chest.size(); slot++)
                    ctx.assertFalse(chest.getStack(slot).contains(ComponentContent.BELT_START.get()), "storage has no leaked selection components");
            }
            ctx.complete();
        } finally {
            RtsBeltDrafts.reset(player);
            RtsCameraManager.stopIfActive(player);
            Config.ENABLE_SURVIVAL_PROGRESSION.set(progression);
        }
    }

    @GameTest(templateName = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID, tickLimit = 200)
    public static void rtsRemotePickupAuthorization(TestContext ctx) {
        var world = ctx.getWorld();
        boolean progression = Config.ENABLE_SURVIVAL_PROGRESSION.get();
        var player = new net.neoforged.neoforge.common.util.FakePlayer(ctx.getWorld(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ConveyorRTSTest"));
        try {
            Config.ENABLE_SURVIVAL_PROGRESSION.set(false);
            player.changeGameMode(GameMode.SURVIVAL);
            var start = ctx.getAbsolutePos(new BlockPos(2, 2, 5));
            var end = ctx.getAbsolutePos(new BlockPos(12, 2, 5));
            var playerPos = ctx.getAbsolutePos(new BlockPos(7, 2, 13)).toCenterPos();
            player.setPos(playerPos.x, playerPos.y, playerPos.z);
            world.setBlockState(start, BlockContent.CHUTE_BLOCK.get().getDefaultState().with(HorizontalFacingBlock.FACING, Direction.EAST));
            world.setBlockState(end, BlockContent.CHUTE_BLOCK.get().getDefaultState().with(HorizontalFacingBlock.FACING, Direction.WEST));
            var chute = (ChuteBlockEntity) world.getBlockEntity(start);
            ctx.assertTrue(chute.connectOutgoing(Direction.EAST, end, Direction.WEST, java.util.List.of(), 1), "pickup route");
            var nbt = chute.createNbtWithIdentifyingData(world.getRegistryManager());
            var moving = new NbtList();
            moving.add(new ChuteBlockEntity.BeltItem(.5f, 456, new ItemStack(Items.DIAMOND, 128)).write(world.getRegistryManager(), "a", "b"));
            nbt.put("moving", moving); chute.read(nbt, world.getRegistryManager());
            RtsCameraManager.start(player, true);
            var eye = RtsCameraManager.getCameraPosition(player);
            ctx.assertTrue(eye != null, "server camera exists");
            var point = SplineUtil.getPositionOnSpline(chute.getBeltData(), .5f);
            var ray = point.subtract(eye).normalize();
            ctx.assertTrue(RtsCompat.validRay(player, eye, ray), "camera ray allowed");
            ctx.assertFalse(RtsCompat.validRay(player, eye.add(10, 0, 0), ray), "forged origin rejected");
            ctx.assertFalse(RtsCompat.validRay(player, eye, new Vec3d(Double.NaN, 0, 0)), "NaN ray rejected");
            ctx.assertFalse(RtsCompat.canTake(player, start.add(10000, 0, 0)), "outside action area rejected without loading chunk");
            Config.ENABLE_SURVIVAL_PROGRESSION.set(true);
            ctx.assertFalse(RtsCompat.canTake(player, start), "missing RTS interaction capability rejected");
            Config.ENABLE_SURVIVAL_PROGRESSION.set(false);
            var stop = eye.add(ray.multiply(128));
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
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
            var taken = BeltPickup.takeRemote(player, start, Direction.EAST, 456, .5f, eye, stop);
            ctx.assertTrue(taken.taken() == 128 && player.getInventory().count(Items.DIAMOND) == 128, "remote render pickup delivers legal stacks and exact quantity");
            ctx.assertTrue(BeltPickup.takeRemote(player, start, Direction.EAST, 456, .5f, eye, stop).taken() == 0, "replay cannot duplicate items");
            RtsCameraManager.stopIfActive(player);
            ctx.assertFalse(RtsCompat.validRay(player, eye, ray), "closed RTS camera revokes remote access");
            ctx.complete();
        } finally {
            RtsCameraManager.stopIfActive(player);
            Config.ENABLE_SURVIVAL_PROGRESSION.set(progression);
        }
    }
    private static int count(ChestBlockEntity chest) {
        if (chest == null) return 0;
        int count = 0;
        for (int slot = 0; slot < chest.size(); slot++) if (chest.getStack(slot).isOf(ItemContent.ADVANCED_BELT.get())) count += chest.getStack(slot).getCount();
        return count;
    }
    private static void click(ServerPlayerEntity player, BlockPos pos, Direction face) {
        var hit = pos.toCenterPos();
        var eye = RtsCameraManager.getCameraPosition(player);
        var ray = hit.subtract(eye).normalize();
        RtsAPI.get().interaction().interactTarget(player, -1, pos, face, hit.x, hit.y, hit.z,
                com.rtsbuilding.rtsbuilding.network.builder.C2SRtsInteractPayload.SOURCE_PIN_ITEM,
                (byte) 0, "conveyor_belt_plus:advanced_belt", eye.x, eye.y, eye.z, ray.x, ray.y, ray.z);
    }
}
