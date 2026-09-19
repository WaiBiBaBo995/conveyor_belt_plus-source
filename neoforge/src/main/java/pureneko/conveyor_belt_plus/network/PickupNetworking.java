package pureneko.conveyor_belt_plus.network;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.blocks.BeltPickup;

import java.util.WeakHashMap;

public final class PickupNetworking {
    private PickupNetworking() {}
    private static final WeakHashMap<PlayerEntity, Integer> LAST_REQUEST = new WeakHashMap<>();
    public record Request(BlockPos owner, Direction port, long itemId, float progress) implements CustomPayload {
        public static final Id<Request> ID = new Id<>(ConveyorBeltPlus.id("pickup"));
        public static final PacketCodec<RegistryByteBuf, Request> CODEC = PacketCodec.ofStatic((buf, value) -> {
            buf.writeBlockPos(value.owner); buf.writeEnumConstant(value.port);
            buf.writeLong(value.itemId); buf.writeFloat(value.progress);
        }, buf -> new Request(buf.readBlockPos(), buf.readEnumConstant(Direction.class), buf.readLong(), buf.readFloat()));
        @Override public Id<Request> getId() { return ID; }
    }
    public record Taken(BlockPos owner, Direction port, long itemId, int remaining) implements CustomPayload {
        public static final Id<Taken> ID = new Id<>(ConveyorBeltPlus.id("picked_up"));
        public static final PacketCodec<RegistryByteBuf, Taken> CODEC = PacketCodec.ofStatic((buf, value) -> {
            buf.writeBlockPos(value.owner); buf.writeEnumConstant(value.port);
            buf.writeLong(value.itemId); buf.writeVarInt(value.remaining);
        }, buf -> new Taken(buf.readBlockPos(), buf.readEnumConstant(Direction.class), buf.readLong(), buf.readVarInt()));
        @Override public Id<Taken> getId() { return ID; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("3.4");
        registrar.playToServer(Request.ID, Request.CODEC, (message, context) -> {
            var player = context.player();
            Integer previous = LAST_REQUEST.put(player, player.age);
            if (previous != null && previous == player.age) return;
            var result = BeltPickup.take(player, message.owner, message.port, message.itemId, message.progress);
            if (result.taken() > 0 && player.getWorld() instanceof ServerWorld world)
                PacketDistributor.sendToPlayersTrackingChunk(world, new ChunkPos(message.owner),
                        new Taken(message.owner, message.port, message.itemId, result.remaining()));
        });
        registrar.playToClient(Taken.ID, Taken.CODEC, (message, context) -> {
            var world = context.player().getWorld();
            if (world.isChunkLoaded(message.owner))
                BeltPickup.applyClient(world.getBlockEntity(message.owner), message.port, message.itemId, message.remaining);
        });
    }
}
