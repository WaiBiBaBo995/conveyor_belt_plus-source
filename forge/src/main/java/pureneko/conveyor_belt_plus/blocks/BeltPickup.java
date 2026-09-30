package pureneko.conveyor_belt_plus.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.FluidStack;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity.BeltItem;
import pureneko.conveyor_belt_plus.util.BeltTiers;
import pureneko.conveyor_belt_plus.util.SplineUtil;

import java.util.Deque;
import java.util.Iterator;

/** One loaded owner/one route per click; never searches chunks or spawns pickup entities. */
public final class BeltPickup {
    private BeltPickup() {}
    public record Access(ChuteBlockEntity.BeltData data, int tier, Deque<ChuteBlockEntity.BeltItem> items) {}
    public record Result(int taken, int remaining) {}
    private static final Result NONE = new Result(0, 0);

    /** Envelope around the small rendered model; shared by client targeting and server validation. */
    public static AABB bounds(Vec3 pathPoint) {
        return new AABB(pathPoint.x - .25, pathPoint.y - .17, pathPoint.z - .25,
                pathPoint.x + .25, pathPoint.y + .29, pathPoint.z + .25);
    }

    public static boolean validProgress(float shown, float current, double length, float speed) {
        if (!Float.isFinite(shown) || shown < 0 || shown > 1 || length <= 0) return false;
        // Rendering is buffered. Allow up to one second of bounded network/render delay, not arbitrary coordinates.
        double behind = (current - shown) * length;
        return behind >= -.15 && behind <= speed + .15;
    }

    public static Vec3 rayHit(AABB bounds, Vec3 eye, Vec3 end) {
        return bounds.contains(eye) ? eye : bounds.clip(eye, end).orElse(null);
    }

    public static boolean unobstructed(Player player, Vec3 eye, Vec3 hit) {
        var obstruction = player.level().clip(new ClipContext(eye, hit,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return obstruction.getType() == HitResult.Type.MISS
                || eye.distanceToSqr(obstruction.getLocation()) + .001 >= eye.distanceToSqr(hit);
    }

    /** Runs only on the server thread; the client supplies an ID and progress, never an ItemStack. */
    public static Result take(Player player, BlockPos ownerPos, Direction port, long id, float shown) {
        var eye = player.getEyePosition();
        return takeAlongRay(player, ownerPos, port, id, shown, eye,
                eye.add(player.getLookAngle().scale(player.getBlockReach())), false, "");
    }

    public static Result takeRemote(Player player, BlockPos ownerPos, Direction port, long id, float shown,
                                    Vec3 eye, Vec3 end) {
        return takeRemote(player, ownerPos, port, id, shown, eye, end, "");
    }
    public static Result takeRemote(Player player, BlockPos ownerPos, Direction port, long id, float shown,
                                    Vec3 eye, Vec3 end, String containerId) {
        if (!pureneko.conveyor_belt_plus.compat.rts.RtsCompat.validRay(player, eye, end.subtract(eye).normalize())
                || eye.distanceToSqr(end) > 128 * 128 + .01) return NONE;
        return takeAlongRay(player, ownerPos, port, id, shown, eye, end, true, containerId);
    }

    private static Result takeAlongRay(Player player, BlockPos ownerPos, Direction port, long id, float shown,
                                       Vec3 eye, Vec3 end, boolean remote, String containerId) {
        if (!(player.level() instanceof ServerLevel world) || !player.isAlive() || player.isSpectator()
                || !player.getAbilities().mayBuild
                || player.containerMenu != player.inventoryMenu || !world.hasChunkAt(ownerPos)) return NONE;
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
            var hitPos = BlockPos.containing(hit);
            if (!world.mayInteract(player, hitPos) || !world.mayInteract(player, ownerPos)) return NONE;
            if (remote && !pureneko.conveyor_belt_plus.compat.rts.RtsCompat.canTake(player, hitPos)) return NONE;
            // Give protection mods a standard cancellable interaction at the pickup location.
            var event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND,
                    hitPos, new BlockHitResult(hit, Direction.UP, hitPos, false));
            MinecraftForge.EVENT_BUS.post(event);
            if (event.isCanceled() || event.getUseBlock() == Event.Result.DENY || event.getUseItem() == Event.Result.DENY) return NONE;
            boolean fluid = pureneko.conveyor_belt_plus.util.FluidPackets.isPacket(item.stack);
            if (!fluid && (!containerId.isEmpty() || !remote && !player.getMainHandItem().isEmpty())) return NONE;
            int taken;
            if (!fluid) taken = transferToInventory(player, item.stack);
            else if (remote && !containerId.isEmpty() && player instanceof net.minecraft.server.level.ServerPlayer server)
                taken = pureneko.conveyor_belt_plus.compat.rts.RtsFluidPickup.fill(server, item.stack, containerId, ownerPos, hitPos);
            else taken = fillHeldContainer(player, item.stack);
            if (taken == 0) return NONE;
            int remaining = fluid ? pureneko.conveyor_belt_plus.util.FluidPackets.amount(item.stack) : item.stack.getCount();
            if (remaining == 0) iterator.remove();
            owner.setChanged();
            world.getChunkSource().blockChanged(ownerPos);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            return new Result(taken, remaining);
        }
        return NONE;
    }

    public static int fillHeldContainer(Player player, ItemStack packet) {
        int before = pureneko.conveyor_belt_plus.util.FluidPackets.amount(packet);
        if (before <= 0 || player.getMainHandItem().isEmpty()) return 0;
        // A null player gives the same conserving semantics in creative and survival, and refuses overflow.
        var result = net.minecraftforge.fluids.FluidUtil.tryFillContainerAndStow(player.getMainHandItem(),
                pureneko.conveyor_belt_plus.forge.ConveyorFluidApi.packetHandler(packet),
                new net.minecraftforge.items.wrapper.PlayerMainInvWrapper(player.getInventory()), before, null, true);
        if (!result.isSuccess()) return 0;
        player.setItemInHand(InteractionHand.MAIN_HAND, result.getResult());
        return before - pureneko.conveyor_belt_plus.util.FluidPackets.amount(packet);
    }

    /** Insert in legal vanilla stack sizes, including creative mode; never discard overflow. */
    public static int transferToInventory(Player player, ItemStack source) {
        if (pureneko.conveyor_belt_plus.util.FluidPackets.isPacket(source)) return 0;
        int before = source.getCount();
        var inventory = player.getInventory();
        for (int pass = 0; pass < 2 && !source.isEmpty(); pass++) {
            for (int slot = 0; slot < inventory.items.size() && !source.isEmpty(); slot++) {
                var existing = inventory.getItem(slot);
                if (pass == 0 && !existing.isEmpty() && ItemStack.isSameItemSameTags(existing, source)) {
                    int count = Math.min(source.getCount(), Math.max(0, Math.min(existing.getMaxStackSize(), inventory.getMaxStackSize()) - existing.getCount()));
                    existing.grow(count);
                    source.shrink(count);
                } else if (pass == 1 && existing.isEmpty()) {
                    int count = Math.min(source.getCount(), Math.min(source.getMaxStackSize(), inventory.getMaxStackSize()));
                    inventory.setItem(slot, source.split(count));
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
            else if (pureneko.conveyor_belt_plus.util.FluidPackets.isPacket(item.stack)) {
                var fluid = pureneko.conveyor_belt_plus.util.FluidPackets.get(item.stack);
                fluid.setAmount(remaining);
                pureneko.conveyor_belt_plus.util.FluidPackets.set(item.stack, fluid);
            } else item.stack.setCount(remaining);
            return;
        }
    }
}
