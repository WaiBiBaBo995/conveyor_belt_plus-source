package pureneko.conveyor_belt_plus.compat.rts;

import com.rtsbuilding.rtsbuilding.api.RtsAPI;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/** Optional boundary: never call RTS classes unless its mod is present. */
public final class RtsCompat {
    private RtsCompat() {}
    public static boolean active(Player player) {
        return ModList.get().isLoaded("rtsbuilding") && player instanceof ServerPlayer server
                && RtsCameraManager.isActive(server);
    }
    public static boolean canBuild(Player player, BlockPos pos, boolean placing) {
        if (!active(player)) return false;
        var server = (ServerPlayer) player;
        return RtsProgressionManager.canUse(server, RtsFeature.REMOTE_PLACE)
                && RtsAPI.get() != null && RtsAPI.get().storage().canAccessTarget(server, pos)
                && (placing ? RtsClaimProtectionService.canPlaceBlock(server, pos)
                : RtsClaimProtectionService.canInteractBlock(server, pos, Direction.UP, InteractionHand.MAIN_HAND, player.getMainHandItem()));
    }
    public static boolean canTake(Player player, BlockPos pos) { return canTake(player, pos, ItemStack.EMPTY); }
    public static boolean canTake(Player player, BlockPos pos, ItemStack used) {
        if (!active(player)) return false;
        var server = (ServerPlayer) player;
        return RtsProgressionManager.canUse(server, RtsFeature.INTERACT)
                && RtsAPI.get() != null && RtsAPI.get().storage().canAccessTarget(server, pos)
                && RtsClaimProtectionService.canInteractBlock(server, pos, Direction.UP, InteractionHand.MAIN_HAND, used);
    }
    public static boolean validRay(Player player, Vec3 origin, Vec3 direction) {
        if (!active(player) || !finite(origin) || !finite(direction)
                || Math.abs(direction.lengthSqr() - 1) > .01) return false;
        var camera = RtsCameraManager.getCameraPosition((ServerPlayer) player);
        // Allow bounded client camera interpolation, never an arbitrary remote origin.
        return camera != null && camera.distanceToSqr(origin) <= 9;
    }
    private static boolean finite(Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }
}
