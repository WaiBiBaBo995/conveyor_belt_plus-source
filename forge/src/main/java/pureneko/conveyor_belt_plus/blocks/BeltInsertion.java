package pureneko.conveyor_belt_plus.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import pureneko.conveyor_belt_plus.blocks.BeltPickup.Access;
import pureneko.conveyor_belt_plus.compat.rts.RtsCompat;
import pureneko.conveyor_belt_plus.util.BeltHitPath.Hit;
import pureneko.conveyor_belt_plus.util.BeltTransport;
import pureneko.conveyor_belt_plus.util.FluidPackets;

/** Click-only insertion. Never accepts client-supplied contents as a source of items. */
public final class BeltInsertion {
    private BeltInsertion() {}
    public static boolean validStack(ItemStack stack) {
        return !stack.isEmpty() && (!FluidPackets.isPacket(stack) || !FluidPackets.get(stack).isEmpty());
    }
    public record Destination(BlockEntity owner, Direction port, Vec3 hit, float progress) {
        public boolean canInsert() {
            if (owner.isRemoved() || owner.getLevel() == null || owner.getLevel().getBlockEntity(owner.getBlockPos()) != owner) return false;
            var access = ((ConveyorNode) owner).pickupAccess(port);
            return access != null && access.data() != null
                    && BeltTransport.canInsertAt(access.items(), access.data().totalLength(), progress);
        }
        public boolean insert(ItemStack stack) {
            return canInsert() && ((ConveyorNode) owner).insertOnBelt(port, progress, stack);
        }
    }
    public static Destination locate(Player player, BlockPos pos, Direction port, Vec3 eye, Vec3 end) {
        if (!(player.level() instanceof ServerLevel world) || !player.isAlive() || player.isSpectator()
                || !player.getAbilities().mayBuild || player.containerMenu != player.inventoryMenu
                || !world.hasChunkAt(pos)) return null;
        var owner = world.getBlockEntity(pos);
        var access = owner instanceof ConveyorNode node ? node.pickupAccess(port) : null;
        if (access == null || access.data() == null) return null;
        var hit = access.data().interactionPath().raycast(eye, end);
        if (hit == null || !BeltPickup.unobstructed(player, eye, hit.point())) return null;
        var result = new Destination(owner, port, hit.point(), hit.progress());
        return result.canInsert() ? result : null;
    }
    public static boolean allowed(Player player, Destination target, ItemStack used, boolean remote) {
        var pos = target.owner.getBlockPos();
        var hitPos = BlockPos.containing(target.hit);
        var world = player.level();
        if (!validStack(used) || !world.mayInteract(player, pos) || !world.mayInteract(player, hitPos)
                || remote && (!RtsCompat.canTake(player, pos, used) || !RtsCompat.canTake(player, hitPos, used))) return false;
        var event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND,
                hitPos, new BlockHitResult(target.hit, Direction.UP, hitPos, false));
        MinecraftForge.EVENT_BUS.post(event);
        return !event.isCanceled() && event.getUseBlock() != Event.Result.DENY && event.getUseItem() != Event.Result.DENY;
    }
    public static boolean put(Player player, BlockPos owner, Direction port) {
        var eye = player.getEyePosition();
        var target = locate(player, owner, port, eye, eye.add(player.getLookAngle().scale(player.getBlockReach())));
        var held = player.getMainHandItem();
        if (target == null || !allowed(player, target, held, false) || !target.insert(held)) return false;
        syncInventory(player);
        return true;
    }
    public static boolean putRemote(Player player, BlockPos owner, Direction port, Vec3 eye, Vec3 direction, ItemStack selected) {
        if (!(player instanceof ServerPlayer server) || !validStack(selected) || !RtsCompat.validRay(player, eye, direction)) return false;
        var target = locate(player, owner, port, eye, eye.add(direction.scale(128)));
        if (target == null) return false;
        return pureneko.conveyor_belt_plus.compat.rts.RtsBeltInsertion.put(server, target, selected);
    }
    public static void syncInventory(Player player) {
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }
}
