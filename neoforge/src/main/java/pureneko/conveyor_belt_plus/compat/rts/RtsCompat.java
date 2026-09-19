package pureneko.conveyor_belt_plus.compat.rts;

import com.rtsbuilding.rtsbuilding.api.RtsAPI;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.neoforged.fml.ModList;

/** Optional boundary: never call RTS classes unless its mod is present. */
public final class RtsCompat {
    private RtsCompat() {}
    public static boolean active(PlayerEntity player) {
        return ModList.get().isLoaded("rtsbuilding") && player instanceof ServerPlayerEntity server
                && RtsCameraManager.isActive(server);
    }
    public static boolean canBuild(PlayerEntity player, BlockPos pos, boolean placing) {
        if (!active(player)) return false;
        var server = (ServerPlayerEntity) player;
        return RtsProgressionManager.canUse(server, RtsFeature.REMOTE_PLACE)
                && RtsAPI.get() != null && RtsAPI.get().storage().canAccessTarget(server, pos)
                && (placing ? RtsClaimProtectionService.canPlaceBlock(server, pos)
                : RtsClaimProtectionService.canInteractBlock(server, pos, Direction.UP, Hand.MAIN_HAND, player.getMainHandStack()));
    }
    public static boolean canTake(PlayerEntity player, BlockPos pos) {
        if (!active(player)) return false;
        var server = (ServerPlayerEntity) player;
        return RtsProgressionManager.canUse(server, RtsFeature.INTERACT)
                && RtsAPI.get() != null && RtsAPI.get().storage().canAccessTarget(server, pos)
                && RtsClaimProtectionService.canInteractBlock(server, pos, Direction.UP, Hand.MAIN_HAND, ItemStack.EMPTY);
    }
    public static boolean validRay(PlayerEntity player, Vec3d origin, Vec3d direction) {
        if (!active(player) || !finite(origin) || !finite(direction)
                || Math.abs(direction.lengthSquared() - 1) > .01) return false;
        var camera = RtsCameraManager.getCameraPosition((ServerPlayerEntity) player);
        // Allow bounded client camera interpolation, never an arbitrary remote origin.
        return camera != null && camera.squaredDistanceTo(origin) <= 9;
    }
    private static boolean finite(Vec3d v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }
}
