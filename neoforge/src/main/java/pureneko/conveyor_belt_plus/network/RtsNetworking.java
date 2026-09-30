package pureneko.conveyor_belt_plus.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.blocks.BeltPickup;
import pureneko.conveyor_belt_plus.blocks.BeltPickup.Result;
import pureneko.conveyor_belt_plus.compat.rts.RtsBeltDrafts;
import pureneko.conveyor_belt_plus.compat.rts.RtsCompat;

import java.util.WeakHashMap;

/** Click-only traffic. Clients cannot submit inventory contents or endpoint drafts. */
public final class RtsNetworking {
    private RtsNetworking() {}
    private static final WeakHashMap<Player, Integer> LAST_PICKUP = new WeakHashMap<>();
    private static final WeakHashMap<Player, Integer> LAST_DRAFT = new WeakHashMap<>();
    public record Draft(ItemStack stack) implements CustomPacketPayload {
        public static final Type<Draft> ID = new Type<>(ConveyorBeltPlus.id("rts_draft"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Draft> CODEC = ItemStack.OPTIONAL_STREAM_CODEC.map(Draft::new, Draft::stack);
        @Override public Type<Draft> type() { return ID; }
    }
    public record DraftRequest(boolean reset) implements CustomPacketPayload {
        public static final Type<DraftRequest> ID = new Type<>(ConveyorBeltPlus.id("rts_draft_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DraftRequest> CODEC = StreamCodec.of(
                (buf, value) -> buf.writeBoolean(value.reset), buf -> new DraftRequest(buf.readBoolean()));
        @Override public Type<DraftRequest> type() { return ID; }
    }
    public record Take(BlockPos owner, Direction port, long id, float progress, Vec3 origin, Vec3 direction, String containerId) implements CustomPacketPayload {
        public Take(BlockPos owner, Direction port, long id, float progress, Vec3 origin, Vec3 direction) {
            this(owner, port, id, progress, origin, direction, "");
        }
        public static final Type<Take> ID = new Type<>(ConveyorBeltPlus.id("rts_take"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Take> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeBlockPos(value.owner); buf.writeEnum(value.port); buf.writeLong(value.id); buf.writeFloat(value.progress);
            buf.writeDouble(value.origin.x); buf.writeDouble(value.origin.y); buf.writeDouble(value.origin.z);
            buf.writeDouble(value.direction.x); buf.writeDouble(value.direction.y); buf.writeDouble(value.direction.z);
            buf.writeUtf(value.containerId, 256);
        }, buf -> new Take(buf.readBlockPos(), buf.readEnum(Direction.class), buf.readLong(), buf.readFloat(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readUtf(256)));
        @Override public Type<Take> type() { return ID; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("3.7");
        registrar.playToClient(Draft.ID, Draft.CODEC, (message, context) -> {
            if (ModList.get().isLoaded("rtsbuilding"))
                pureneko.conveyor_belt_plus.compat.rts.RtsClient.setDraft(message.stack);
        });
        registrar.playToServer(DraftRequest.ID, DraftRequest.CODEC, (message, context) -> {
            var player = context.player();
            if (!ModList.get().isLoaded("rtsbuilding")) return;
            var previous = LAST_DRAFT.put(player, player.tickCount);
            if (previous != null && previous == player.tickCount) return;
            if (message.reset) RtsBeltDrafts.reset(player);
            else if (RtsCompat.active(player)) RtsBeltDrafts.sync(player);
        });
        registrar.playToServer(Take.ID, Take.CODEC, (message, context) -> {
            var player = context.player();
            var previous = LAST_PICKUP.put(player, player.tickCount);
            if (previous != null && previous == player.tickCount || !RtsCompat.validRay(player, message.origin, message.direction)) return;
            var result = BeltPickup.takeRemote(player, message.owner, message.port, message.id, message.progress,
                    message.origin, message.origin.add(message.direction.scale(128)), message.containerId);
            if (result.taken() > 0 && player.level() instanceof ServerLevel world)
                PacketDistributor.sendToPlayersTrackingChunk(world, new ChunkPos(message.owner),
                        new PickupNetworking.Taken(message.owner, message.port, message.id, result.remaining()));
        });
    }
}
