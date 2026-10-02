package pureneko.conveyor_belt_plus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import pureneko.conveyor_belt_plus.blocks.BeltPickup;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity;
import pureneko.conveyor_belt_plus.compat.rts.RtsClient.View;
import pureneko.conveyor_belt_plus.network.PickupNetworking;
import pureneko.conveyor_belt_plus.util.BeltHitPath.Hit;

/** Constant-memory nearest-hit accumulator fed by the existing item-render loop. */
public final class BeltPickupTarget {
    private BeltPickupTarget() {}
    private static ClientLevel frameWorld;
    private static Vec3 eye, end;
    private static BlockPos owner;
    private static Direction port;
    private static long itemId, frameTime;
    private static float progress;
    private static double nearest, centerLimit;
    private static Vec3 hit;
    private static int lastRequest = Integer.MIN_VALUE;
    private static boolean remote;
    private static BlockPos insertionOwner;
    private static Direction insertionPort;
    private static Vec3 insertionHit;
    private static double insertionDistance;
    private static net.minecraft.world.item.ItemStack heldForInsertion = net.minecraft.world.item.ItemStack.EMPTY;
    private static net.minecraft.world.item.ItemStack shownStack = net.minecraft.world.item.ItemStack.EMPTY;
    private static int shownTier;
    public record Target(BlockPos owner, Direction port, long id, float progress, Vec3 hit,
                         net.minecraft.world.item.ItemStack stack, int tier) {}

    public static Target current() {
        return owner == null || Minecraft.getInstance().level != frameWorld || System.nanoTime() - frameTime > 250_000_000L ? null
                : new Target(owner, port, itemId, progress, hit, shownStack.copy(), shownTier);
    }

    public static void clear() {
        frameWorld = null;
        owner = null;
        insertionOwner = null;
        shownStack = net.minecraft.world.item.ItemStack.EMPTY;
        eye = end = hit = null;
        lastRequest = Integer.MIN_VALUE;
    }

    public static void beginFrame(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        var client = Minecraft.getInstance();
        if (frameWorld != client.level) lastRequest = Integer.MIN_VALUE;
        frameWorld = client.level;
        owner = null;
        insertionOwner = null;
        eye = null;
        remote = false;
        frameTime = System.nanoTime();
        if (client.player == null) return;
        if (net.neoforged.fml.ModList.get().isLoaded("rtsbuilding")) {
            var view = pureneko.conveyor_belt_plus.compat.rts.RtsClient.view();
            if (view != null) {
                remote = true;
                heldForInsertion = view.stack();
                eye = view.origin();
                end = eye.add(view.direction().scale(128));
                nearest = eye.distanceToSqr(end);
                if (view.hit() != null && view.hit().getType() != HitResult.Type.MISS)
                    nearest = Math.min(nearest, eye.distanceToSqr(view.hit().getLocation()));
                insertionDistance = nearest;
                updateCenterLimit();
                return;
            }
        }
        if (client.screen != null || client.getCameraEntity() != client.player) return;
        heldForInsertion = client.player.getMainHandItem();
        float delta = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        eye = client.player.getEyePosition(delta);
        end = eye.add(client.player.getViewVector(delta).scale(client.player.blockInteractionRange()));
        nearest = eye.distanceToSqr(end);
        // A vanilla entity/block in front keeps priority. The server raycasts again on the click.
        if (client.hitResult != null && client.hitResult.getType() != HitResult.Type.MISS)
            nearest = Math.min(nearest, eye.distanceToSqr(client.hitResult.getLocation()));
        insertionDistance = nearest;
        updateCenterLimit();
    }

    private static void updateCenterLimit() {
        // The same half-block envelope works at both vanilla and RTS camera distances.
        double distance = Math.sqrt(nearest) + .5;
        centerLimit = distance * distance;
    }

    public static void consider(BlockEntity entity, Direction output, ChuteBlockEntity.BeltItem packet,
                                float shown, Vec3 point) {
        if (eye == null || entity.getLevel() != frameWorld || point.distanceToSqr(eye) > centerLimit) return;
        var intersection = BeltPickup.rayHit(BeltPickup.bounds(point), eye, end);
        if (intersection == null) return;
        double distance = intersection.distanceToSqr(eye);
        if (distance >= nearest) return;
        nearest = distance;
        updateCenterLimit();
        owner = entity.getBlockPos();
        port = output;
        itemId = packet.id;
        progress = shown;
        hit = intersection;
        shownStack = packet.stack;
        shownTier = entity instanceof pureneko.conveyor_belt_plus.blocks.ConveyorNode node ? node.outgoingBeltTier(output) : 1;
    }

    public static void considerRoute(BlockEntity entity, Direction port, ChuteBlockEntity.BeltData data) {
        if (eye == null || entity.getLevel() != frameWorld
                || !pureneko.conveyor_belt_plus.blocks.BeltInsertion.validStack(heldForInsertion)) return;
        var target = data.interactionPath().raycast(eye, end);
        if (target == null || eye.distanceToSqr(target.point()) >= insertionDistance) return;
        insertionDistance = eye.distanceToSqr(target.point());
        insertionOwner = entity.getBlockPos();
        insertionPort = port;
        insertionHit = target.point();
    }

    public static void interact(InputEvent.InteractionKeyMappingTriggered event) {
        var client = Minecraft.getInstance();
        if (!event.isUseItem() || client.level != frameWorld || client.player == null || remote
                || client.screen != null || client.player.isSpectator()
                || System.nanoTime() - frameTime > 250_000_000L) return;
        boolean taking = owner != null && canTake(shownStack, client.player.getMainHandItem());
        if (!taking && (insertionOwner == null || !pureneko.conveyor_belt_plus.blocks.BeltInsertion.validStack(client.player.getMainHandItem()))) return;
        if (!BeltPickup.unobstructed(client.player, eye, taking ? hit : insertionHit)) return;
        event.setCanceled(true);
        event.setSwingHand(event.getHand() == InteractionHand.MAIN_HAND);
        if (event.getHand() != InteractionHand.MAIN_HAND || lastRequest == client.player.tickCount) return;
        lastRequest = client.player.tickCount;
        if (taking) PacketDistributor.sendToServer(new PickupNetworking.Request(owner, port, itemId, progress));
        else PacketDistributor.sendToServer(new pureneko.conveyor_belt_plus.network.InsertionNetworking.Put(insertionOwner, insertionPort));
    }

    public static boolean putRemote(Vec3 origin, Vec3 direction, net.minecraft.world.item.ItemStack selected) {
        var client = Minecraft.getInstance();
        if (!remote || insertionOwner == null || client.level != frameWorld || client.player == null
                || client.player.isSpectator() || !pureneko.conveyor_belt_plus.blocks.BeltInsertion.validStack(selected)
                || lastRequest == client.player.tickCount || System.nanoTime() - frameTime > 250_000_000L
                || origin.distanceToSqr(eye) > .01 || direction.dot(end.subtract(eye).normalize()) < .9999
                || !BeltPickup.unobstructed(client.player, origin, insertionHit)) return false;
        lastRequest = client.player.tickCount;
        PacketDistributor.sendToServer(new pureneko.conveyor_belt_plus.network.InsertionNetworking.RemotePut(
                insertionOwner, insertionPort, origin, direction, selected.copyWithCount(1)));
        return true;
    }

    public static boolean canTake(net.minecraft.world.item.ItemStack packet, net.minecraft.world.item.ItemStack held) {
        return !pureneko.conveyor_belt_plus.util.FluidPackets.isPacket(held) && (pureneko.conveyor_belt_plus.util.FluidPackets.isPacket(packet)
                ? net.neoforged.neoforge.fluids.FluidUtil.getFluidHandler(held).isPresent() : held.isEmpty());
    }

    public static boolean takeRemote(Vec3 origin, Vec3 direction) { return takeRemote(origin, direction, net.minecraft.world.item.ItemStack.EMPTY); }
    public static boolean takeRemote(Vec3 origin, Vec3 direction, net.minecraft.world.item.ItemStack selected) {
        var client = Minecraft.getInstance();
        if (!remote || current() == null || client.level != frameWorld || client.player == null
                || client.player.isSpectator() || !canTake(shownStack, selected) || lastRequest == client.player.tickCount
                || origin.distanceToSqr(eye) > .01 || direction.dot(end.subtract(eye).normalize()) < .9999
                || !BeltPickup.unobstructed(client.player, origin, hit)) return false;
        lastRequest = client.player.tickCount;
        PacketDistributor.sendToServer(new pureneko.conveyor_belt_plus.network.RtsNetworking.Take(
                owner, port, itemId, progress, origin, direction, selected.isEmpty() ? "" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(selected.getItem()).toString()));
        return true;
    }
}
