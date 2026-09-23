package pureneko.conveyor_belt_plus.network;

import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.ModList;
import pureneko.conveyor_belt_plus.network.NetworkChannel;
import pureneko.conveyor_belt_plus.network.NetworkChannel.Codec;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.blocks.BeltPickup;
import pureneko.conveyor_belt_plus.compat.rts.RtsBeltDrafts;
import pureneko.conveyor_belt_plus.compat.rts.RtsCompat;

import java.util.WeakHashMap;
import net.minecraft.entity.player.PlayerEntity;

/** Click-only traffic. Clients cannot submit inventory contents or endpoint drafts. */
public final class RtsNetworking {
    private RtsNetworking() {}
    private static final WeakHashMap<PlayerEntity, Integer> LAST_PICKUP = new WeakHashMap<>();
    private static final WeakHashMap<PlayerEntity, Integer> LAST_DRAFT = new WeakHashMap<>();
    public record Draft(ItemStack stack) {
        public static final Codec<Draft> CODEC = Codec.ofStatic((buf, value) -> buf.writeItemStack(value.stack), buf -> new Draft(buf.readItemStack()));
    }
    public record DraftRequest(boolean reset) {
        public static final Codec<DraftRequest> CODEC = Codec.ofStatic(
                (buf, value) -> buf.writeBoolean(value.reset), buf -> new DraftRequest(buf.readBoolean()));
    }
    public record Take(BlockPos owner, Direction port, long id, float progress, Vec3d origin, Vec3d direction) {
        public static final Codec<Take> CODEC = Codec.ofStatic((buf, value) -> {
            buf.writeBlockPos(value.owner); buf.writeEnumConstant(value.port); buf.writeLong(value.id); buf.writeFloat(value.progress);
            buf.writeDouble(value.origin.x); buf.writeDouble(value.origin.y); buf.writeDouble(value.origin.z);
            buf.writeDouble(value.direction.x); buf.writeDouble(value.direction.y); buf.writeDouble(value.direction.z);
        }, buf -> new Take(buf.readBlockPos(), buf.readEnumConstant(Direction.class), buf.readLong(), buf.readFloat(),
                new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()), new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble())));
    }
    public static void register() {
        NetworkChannel.client(Draft.class, Draft.CODEC, (message, context) -> {
            if (ModList.get().isLoaded("rtsbuilding"))
                pureneko.conveyor_belt_plus.compat.rts.RtsClient.setDraft(message.stack);
        });
        NetworkChannel.server(DraftRequest.class, DraftRequest.CODEC, (message, context) -> {
            var player = context.player();
            if (!ModList.get().isLoaded("rtsbuilding")) return;
            var previous = LAST_DRAFT.put(player, player.age);
            if (previous != null && previous == player.age) return;
            if (message.reset) RtsBeltDrafts.reset(player);
            else if (RtsCompat.active(player)) RtsBeltDrafts.sync(player);
        });
        NetworkChannel.server(Take.class, Take.CODEC, (message, context) -> {
            var player = context.player();
            var previous = LAST_PICKUP.put(player, player.age);
            if (previous != null && previous == player.age || !RtsCompat.validRay(player, message.origin, message.direction)) return;
            var result = BeltPickup.takeRemote(player, message.owner, message.port, message.id, message.progress,
                    message.origin, message.origin.add(message.direction.multiply(128)));
            if (result.taken() > 0 && player.getWorld() instanceof ServerWorld world)
                NetworkChannel.sendToPlayersTrackingChunk(world, new ChunkPos(message.owner),
                        new PickupNetworking.Taken(message.owner, message.port, message.id, result.remaining()));
        });
    }
}
