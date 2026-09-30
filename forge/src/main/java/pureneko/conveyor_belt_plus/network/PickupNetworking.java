package pureneko.conveyor_belt_plus.network;

import pureneko.conveyor_belt_plus.network.NetworkChannel;
import pureneko.conveyor_belt_plus.network.NetworkChannel.Codec;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.blocks.BeltPickup;
import pureneko.conveyor_belt_plus.blocks.BeltPickup.Result;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public final class PickupNetworking {
    private PickupNetworking() {}
    private static final WeakHashMap<Player, Integer> LAST_REQUEST = new WeakHashMap<>();
    public record Request(BlockPos owner, Direction port, long itemId, float progress) {
        public static final Codec<Request> CODEC = Codec.ofStatic((buf, value) -> {
            buf.writeBlockPos(value.owner); buf.writeEnum(value.port);
            buf.writeLong(value.itemId); buf.writeFloat(value.progress);
        }, buf -> new Request(buf.readBlockPos(), buf.readEnum(Direction.class), buf.readLong(), buf.readFloat()));
    }
    public record Taken(BlockPos owner, Direction port, long itemId, int remaining) {
        public static final Codec<Taken> CODEC = Codec.ofStatic((buf, value) -> {
            buf.writeBlockPos(value.owner); buf.writeEnum(value.port);
            buf.writeLong(value.itemId); buf.writeVarInt(value.remaining);
        }, buf -> new Taken(buf.readBlockPos(), buf.readEnum(Direction.class), buf.readLong(), buf.readVarInt()));
    }
    public static void register() {
        NetworkChannel.server(Request.class, Request.CODEC, (message, context) -> {
            var player = context.player();
            Integer previous = LAST_REQUEST.put(player, player.tickCount);
            if (previous != null && previous == player.tickCount) return;
            var result = BeltPickup.take(player, message.owner, message.port, message.itemId, message.progress);
            if (result.taken() > 0 && player.level() instanceof ServerLevel world)
                NetworkChannel.sendToPlayersTrackingChunk(world, new ChunkPos(message.owner),
                        new Taken(message.owner, message.port, message.itemId, result.remaining()));
        });
        NetworkChannel.client(Taken.class, Taken.CODEC, (message, context) -> {
            var world = context.player().level();
            if (world.hasChunkAt(message.owner))
                BeltPickup.applyClient(world.getBlockEntity(message.owner), message.port, message.itemId, message.remaining);
        });
    }
}
