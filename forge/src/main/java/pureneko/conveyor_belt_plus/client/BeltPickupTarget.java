package pureneko.conveyor_belt_plus.client;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import pureneko.conveyor_belt_plus.network.NetworkChannel;
import pureneko.conveyor_belt_plus.blocks.BeltPickup;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity;
import pureneko.conveyor_belt_plus.network.PickupNetworking;

/** Constant-memory nearest-hit accumulator fed by the existing item-render loop. */
public final class BeltPickupTarget {
    private BeltPickupTarget() {}
    private static ClientWorld frameWorld;
    private static Vec3d eye, end;
    private static BlockPos owner;
    private static Direction port;
    private static long itemId, frameTime;
    private static float progress;
    private static double nearest, centerLimit;
    private static Vec3d hit;
    private static int lastRequest = Integer.MIN_VALUE;
    private static boolean remote;
    private static net.minecraft.item.ItemStack shownStack = net.minecraft.item.ItemStack.EMPTY;
    private static int shownTier;
    public record Target(BlockPos owner, Direction port, long id, float progress, Vec3d hit,
                         net.minecraft.item.ItemStack stack, int tier) {}

    public static Target current() {
        return owner == null || MinecraftClient.getInstance().world != frameWorld || System.nanoTime() - frameTime > 250_000_000L ? null
                : new Target(owner, port, itemId, progress, hit, shownStack.copy(), shownTier);
    }

    public static void clear() {
        frameWorld = null;
        owner = null;
        shownStack = net.minecraft.item.ItemStack.EMPTY;
        eye = end = hit = null;
        lastRequest = Integer.MIN_VALUE;
    }

    public static void beginFrame(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        var client = MinecraftClient.getInstance();
        if (frameWorld != client.world) lastRequest = Integer.MIN_VALUE;
        frameWorld = client.world;
        owner = null;
        eye = null;
        remote = false;
        frameTime = System.nanoTime();
        if (client.player == null) return;
        if (net.minecraftforge.fml.ModList.get().isLoaded("rtsbuilding")) {
            var view = pureneko.conveyor_belt_plus.compat.rts.RtsClient.view();
            if (view != null) {
                remote = true;
                eye = view.origin();
                end = eye.add(view.direction().multiply(128));
                nearest = eye.squaredDistanceTo(end);
                if (view.hit() != null && view.hit().getType() != HitResult.Type.MISS)
                    nearest = Math.min(nearest, eye.squaredDistanceTo(view.hit().getPos()));
                updateCenterLimit();
                return;
            }
        }
        if (client.currentScreen != null || client.getCameraEntity() != client.player
                || !client.player.getMainHandStack().isEmpty() && !net.minecraftforge.fml.ModList.get().isLoaded("jade")) return;
        float delta = event.getPartialTick();
        eye = client.player.getCameraPosVec(delta);
        end = eye.add(client.player.getRotationVec(delta).multiply(client.player.getBlockReach()));
        nearest = eye.squaredDistanceTo(end);
        // A vanilla entity/block in front keeps priority. The server raycasts again on the click.
        if (client.crosshairTarget != null && client.crosshairTarget.getType() != HitResult.Type.MISS)
            nearest = Math.min(nearest, eye.squaredDistanceTo(client.crosshairTarget.getPos()));
        updateCenterLimit();
    }

    private static void updateCenterLimit() {
        // The same half-block envelope works at both vanilla and RTS camera distances.
        double distance = Math.sqrt(nearest) + .5;
        centerLimit = distance * distance;
    }

    public static void consider(BlockEntity entity, Direction output, ChuteBlockEntity.BeltItem packet,
                                float shown, Vec3d point) {
        if (eye == null || entity.getWorld() != frameWorld || point.squaredDistanceTo(eye) > centerLimit) return;
        var intersection = BeltPickup.rayHit(BeltPickup.bounds(point), eye, end);
        if (intersection == null) return;
        double distance = intersection.squaredDistanceTo(eye);
        if (distance >= nearest) return;
        nearest = distance;
        updateCenterLimit();
        owner = entity.getPos();
        port = output;
        itemId = packet.id;
        progress = shown;
        hit = intersection;
        shownStack = packet.stack;
        shownTier = entity instanceof pureneko.conveyor_belt_plus.blocks.ConveyorNode node ? node.outgoingBeltTier(output) : 1;
    }

    public static void interact(InputEvent.InteractionKeyMappingTriggered event) {
        var client = MinecraftClient.getInstance();
        if (!event.isUseItem() || owner == null || client.world != frameWorld || client.player == null
                || client.currentScreen != null || client.player.isSpectator()
                || !client.player.getMainHandStack().isEmpty() || System.nanoTime() - frameTime > 250_000_000L) return;
        if (!BeltPickup.unobstructed(client.player, eye, hit)) return;
        // MinecraftForge fires both hands even after cancellation: consume both, send only once.
        event.setCanceled(true);
        event.setSwingHand(event.getHand() == Hand.MAIN_HAND);
        if (event.getHand() != Hand.MAIN_HAND || lastRequest == client.player.age) return;
        lastRequest = client.player.age;
        NetworkChannel.sendToServer(new PickupNetworking.Request(owner, port, itemId, progress));
    }

    public static boolean takeRemote(Vec3d origin, Vec3d direction) {
        var client = MinecraftClient.getInstance();
        if (!remote || current() == null || client.world != frameWorld || client.player == null
                || client.player.isSpectator() || lastRequest == client.player.age
                || origin.squaredDistanceTo(eye) > .01 || direction.dotProduct(end.subtract(eye).normalize()) < .9999
                || !BeltPickup.unobstructed(client.player, origin, hit)) return false;
        lastRequest = client.player.age;
        NetworkChannel.sendToServer(new pureneko.conveyor_belt_plus.network.RtsNetworking.Take(
                owner, port, itemId, progress, origin, direction));
        return true;
    }
}
