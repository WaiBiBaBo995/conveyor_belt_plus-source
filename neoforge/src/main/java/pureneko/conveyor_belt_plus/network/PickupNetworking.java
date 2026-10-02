package pureneko.conveyor_belt_plus.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.blocks.BeltPickup;
import pureneko.conveyor_belt_plus.blocks.BeltPickup.Result;
import java.util.WeakHashMap;

public final class PickupNetworking {
    private PickupNetworking() {}
    private static final WeakHashMap<Player, Integer> LAST_REQUEST = new WeakHashMap<>();
    public record Request(BlockPos owner, Direction port, long itemId, float progress) implements CustomPacketPayload {
        public static final Type<Request> ID = new Type<>(ConveyorBeltPlus.id("pickup"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeBlockPos(value.owner); buf.writeEnum(value.port);
            buf.writeLong(value.itemId); buf.writeFloat(value.progress);
        }, buf -> new Request(buf.readBlockPos(), buf.readEnum(Direction.class), buf.readLong(), buf.readFloat()));
        @Override public Type<Request> type() { return ID; }
    }
    public record Taken(BlockPos owner, Direction port, long itemId, int remaining) implements CustomPacketPayload {
        public static final Type<Taken> ID = new Type<>(ConveyorBeltPlus.id("picked_up"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Taken> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeBlockPos(value.owner); buf.writeEnum(value.port);
            buf.writeLong(value.itemId); buf.writeVarInt(value.remaining);
        }, buf -> new Taken(buf.readBlockPos(), buf.readEnum(Direction.class), buf.readLong(), buf.readVarInt()));
        @Override public Type<Taken> type() { return ID; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("3.4");
        registrar.playToServer(Request.ID, Request.CODEC, (message, context) -> {
            var player = context.player();
            Integer previous = LAST_REQUEST.put(player, player.tickCount);
            if (previous != null && previous == player.tickCount) return;
            var result = BeltPickup.take(player, message.owner, message.port, message.itemId, message.progress);
            if (result.taken() > 0 && player.level() instanceof ServerLevel world)
                PacketDistributor.sendToPlayersTrackingChunk(world, new ChunkPos(message.owner),
                        new Taken(message.owner, message.port, message.itemId, result.remaining()));
        });
        registrar.playToClient(Taken.ID, Taken.CODEC, (message, context) -> {
            var world = context.player().level();
            if (world.hasChunkAt(message.owner))
                BeltPickup.applyClient(world.getBlockEntity(message.owner), message.port, message.itemId, message.remaining);
        });
    }
}
