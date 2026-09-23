package pureneko.conveyor_belt_plus.network;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import pureneko.conveyor_belt_plus.network.NetworkChannel;
import pureneko.conveyor_belt_plus.network.NetworkChannel.Codec;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.blocks.BeltPickup;

import java.util.WeakHashMap;

public final class PickupNetworking {
    private PickupNetworking() {}
    private static final WeakHashMap<PlayerEntity, Integer> LAST_REQUEST = new WeakHashMap<>();
    public record Request(BlockPos owner, Direction port, long itemId, float progress) {
        public static final Codec<Request> CODEC = Codec.ofStatic((buf, value) -> {
            buf.writeBlockPos(value.owner); buf.writeEnumConstant(value.port);
            buf.writeLong(value.itemId); buf.writeFloat(value.progress);
        }, buf -> new Request(buf.readBlockPos(), buf.readEnumConstant(Direction.class), buf.readLong(), buf.readFloat()));
    }
    public record Taken(BlockPos owner, Direction port, long itemId, int remaining) {
        public static final Codec<Taken> CODEC = Codec.ofStatic((buf, value) -> {
            buf.writeBlockPos(value.owner); buf.writeEnumConstant(value.port);
            buf.writeLong(value.itemId); buf.writeVarInt(value.remaining);
        }, buf -> new Taken(buf.readBlockPos(), buf.readEnumConstant(Direction.class), buf.readLong(), buf.readVarInt()));
    }
    public static void register() {
        NetworkChannel.server(Request.class, Request.CODEC, (message, context) -> {
            var player = context.player();
            Integer previous = LAST_REQUEST.put(player, player.age);
            if (previous != null && previous == player.age) return;
            var result = BeltPickup.take(player, message.owner, message.port, message.itemId, message.progress);
            if (result.taken() > 0 && player.getWorld() instanceof ServerWorld world)
                NetworkChannel.sendToPlayersTrackingChunk(world, new ChunkPos(message.owner),
                        new Taken(message.owner, message.port, message.itemId, result.remaining()));
        });
        NetworkChannel.client(Taken.class, Taken.CODEC, (message, context) -> {
            var world = context.player().getWorld();
            if (world.isChunkLoaded(message.owner))
                BeltPickup.applyClient(world.getBlockEntity(message.owner), message.port, message.itemId, message.remaining);
        });
    }
}
