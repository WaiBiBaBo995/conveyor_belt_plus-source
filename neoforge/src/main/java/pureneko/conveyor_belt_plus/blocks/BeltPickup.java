package pureneko.conveyor_belt_plus.blocks;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.common.util.TriState;
import pureneko.conveyor_belt_plus.util.BeltTiers;
import pureneko.conveyor_belt_plus.util.SplineUtil;

import java.util.Deque;

/** One loaded owner/one route per click; never searches chunks or spawns pickup entities. */
public final class BeltPickup {
    private BeltPickup() {}
    public record Access(ChuteBlockEntity.BeltData data, int tier, Deque<ChuteBlockEntity.BeltItem> items) {}
    public record Result(int taken, int remaining) {}
    private static final Result NONE = new Result(0, 0);

    /** Envelope around the small rendered model; shared by client targeting and server validation. */
    public static Box bounds(Vec3d pathPoint) {
        return new Box(pathPoint.x - .25, pathPoint.y - .17, pathPoint.z - .25,
                pathPoint.x + .25, pathPoint.y + .29, pathPoint.z + .25);
    }

    public static boolean validProgress(float shown, float current, double length, float speed) {
        if (!Float.isFinite(shown) || shown < 0 || shown > 1 || length <= 0) return false;
        // Rendering is buffered. Allow up to one second of bounded network/render delay, not arbitrary coordinates.
        double behind = (current - shown) * length;
        return behind >= -.15 && behind <= speed + .15;
    }

    public static Vec3d rayHit(Box bounds, Vec3d eye, Vec3d end) {
        return bounds.contains(eye) ? eye : bounds.raycast(eye, end).orElse(null);
    }

    public static boolean unobstructed(PlayerEntity player, Vec3d eye, Vec3d hit) {
        var obstruction = player.getWorld().raycast(new RaycastContext(eye, hit,
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
        return obstruction.getType() == HitResult.Type.MISS
                || eye.squaredDistanceTo(obstruction.getPos()) + .001 >= eye.squaredDistanceTo(hit);
    }

    /** Runs only on the server thread; the client supplies an ID and progress, never an ItemStack. */
    public static Result take(PlayerEntity player, BlockPos ownerPos, Direction port, long id, float shown) {
        var eye = player.getEyePos();
        return takeAlongRay(player, ownerPos, port, id, shown, eye,
                eye.add(player.getRotationVector().multiply(player.getBlockInteractionRange())), false);
    }

    public static Result takeRemote(PlayerEntity player, BlockPos ownerPos, Direction port, long id, float shown,
                                    Vec3d eye, Vec3d end) {
        if (!pureneko.conveyor_belt_plus.compat.rts.RtsCompat.validRay(player, eye, end.subtract(eye).normalize())
                || eye.squaredDistanceTo(end) > 128 * 128 + .01) return NONE;
        return takeAlongRay(player, ownerPos, port, id, shown, eye, end, true);
    }

    private static Result takeAlongRay(PlayerEntity player, BlockPos ownerPos, Direction port, long id, float shown,
                                       Vec3d eye, Vec3d end, boolean remote) {
        if (!(player.getWorld() instanceof ServerWorld world) || !player.isAlive() || player.isSpectator()
                || !player.getAbilities().allowModifyWorld || !remote && !player.getMainHandStack().isEmpty()
                || player.currentScreenHandler != player.playerScreenHandler || !world.isChunkLoaded(ownerPos)) return NONE;
        if (remote && !pureneko.conveyor_belt_plus.compat.rts.RtsCompat.canTake(player, ownerPos)) return NONE;
        var owner = world.getBlockEntity(ownerPos);
        var access = owner instanceof ConveyorNode node ? node.pickupAccess(port) : null;
        if (access == null || access.data == null) return NONE;
        var iterator = access.items.iterator();
        while (iterator.hasNext()) {
            var item = iterator.next();
            if (item.id != id) continue;
            if (!validProgress(shown, item.progress, access.data.totalLength(), BeltTiers.speed(access.tier))) return NONE;
            var point = SplineUtil.getPositionOnSpline(access.data, shown);
            var hit = rayHit(bounds(point), eye, end);
            if (hit == null || !unobstructed(player, eye, hit)) return NONE;
            var hitPos = BlockPos.ofFloored(hit);
            if (!world.canPlayerModifyAt(player, hitPos) || !world.canPlayerModifyAt(player, ownerPos)) return NONE;
            if (remote && !pureneko.conveyor_belt_plus.compat.rts.RtsCompat.canTake(player, hitPos)) return NONE;
            // Give protection mods a standard cancellable interaction at the pickup location.
            var event = NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(player, Hand.MAIN_HAND,
                    hitPos, new BlockHitResult(hit, Direction.UP, hitPos, false)));
            if (event.isCanceled() || event.getUseBlock() == TriState.FALSE || event.getUseItem() == TriState.FALSE) return NONE;
            int taken = transferToInventory(player, item.stack);
            if (taken == 0) return NONE;
            int remaining = item.stack.getCount();
            if (remaining == 0) iterator.remove();
            owner.markDirty();
            world.getChunkManager().markForUpdate(ownerPos);
            player.getInventory().markDirty();
            player.playerScreenHandler.sendContentUpdates();
            return new Result(taken, remaining);
        }
        return NONE;
    }

    /** Insert in legal vanilla stack sizes, including creative mode; never discard overflow. */
    public static int transferToInventory(PlayerEntity player, ItemStack source) {
        int before = source.getCount();
        var inventory = player.getInventory();
        for (int pass = 0; pass < 2 && !source.isEmpty(); pass++) {
            for (int slot = 0; slot < inventory.main.size() && !source.isEmpty(); slot++) {
                var existing = inventory.getStack(slot);
                if (pass == 0 && !existing.isEmpty() && ItemStack.areItemsAndComponentsEqual(existing, source)) {
                    int count = Math.min(source.getCount(), Math.max(0, Math.min(existing.getMaxCount(), inventory.getMaxCountPerStack()) - existing.getCount()));
                    existing.increment(count);
                    source.decrement(count);
                } else if (pass == 1 && existing.isEmpty()) {
                    int count = Math.min(source.getCount(), Math.min(source.getMaxCount(), inventory.getMaxCountPerStack()));
                    inventory.setStack(slot, source.split(count));
                }
            }
        }
        return before - source.getCount();
    }

    /** Remove a picked-up model immediately instead of animating it to the destination. */
    public static void applyClient(BlockEntity owner, Direction port, long id, int remaining) {
        var access = owner instanceof ConveyorNode node ? node.pickupAccess(port) : null;
        if (access == null || remaining < 0) return;
        var iterator = access.items.iterator();
        while (iterator.hasNext()) {
            var item = iterator.next();
            if (item.id != id) continue;
            if (remaining == 0) iterator.remove();
            else item.stack.setCount(remaining);
            return;
        }
    }
}
