package pureneko.conveyor_belt_plus.network;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;

import java.util.function.BiConsumer;
import java.util.function.Function;

/** Forge 1.20.1 messages are direction checked and dispatched on the game thread. */
public final class NetworkChannel {
    private static final String PROTOCOL = "3.6-forge-1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ConveyorBeltPlus.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static int nextId;

    private NetworkChannel() {}

    public record Codec<T>(BiConsumer<PacketByteBuf, T> encoder, Function<PacketByteBuf, T> decoder) {
        public void encode(PacketByteBuf buf, T message) { encoder.accept(buf, message); }
        public T decode(PacketByteBuf buf) { return decoder.apply(buf); }
        public static <T> Codec<T> ofStatic(BiConsumer<PacketByteBuf, T> encoder, Function<PacketByteBuf, T> decoder) {
            return new Codec<>(encoder, decoder);
        }
    }
    public record Context(PlayerEntity player) {}

    public static <T> void server(Class<T> type, Codec<T> codec, BiConsumer<T, Context> handler) {
        register(type, codec, NetworkDirection.PLAY_TO_SERVER, handler);
    }
    public static <T> void client(Class<T> type, Codec<T> codec, BiConsumer<T, Context> handler) {
        register(type, codec, NetworkDirection.PLAY_TO_CLIENT, handler);
    }
    private static <T> void register(Class<T> type, Codec<T> codec, NetworkDirection direction,
                                     BiConsumer<T, Context> handler) {
        CHANNEL.messageBuilder(type, nextId++, direction)
                .encoder((message, buf) -> codec.encoder.accept(buf, message))
                .decoder(codec.decoder)
                .consumerMainThread((message, context) -> {
                    PlayerEntity player = direction == NetworkDirection.PLAY_TO_SERVER
                            ? context.get().getSender() : ClientContext.player();
                    if (player != null) handler.accept(message, new Context(player));
                }).add();
    }
    public static void sendToServer(Object message) { CHANNEL.sendToServer(message); }
    public static void sendToPlayer(ServerPlayerEntity player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
    public static void sendToPlayersTrackingChunk(ServerWorld world, ChunkPos pos, Object message) {
        // Never force a chunk load in response to an incoming packet.
        var chunk = world.getChunkManager().getWorldChunk(pos.x, pos.z);
        if (chunk != null) CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk), message);
    }
    private static final class ClientContext {
        private static PlayerEntity player() { return net.minecraft.client.MinecraftClient.getInstance().player; }
    }
}
