package pureneko.conveyor_belt_plus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;

import java.util.function.BiConsumer;
import java.util.function.Function;

/** Forge 1.20.1 messages are direction checked and dispatched on the game thread. */
public final class NetworkChannel {
    private static final String PROTOCOL = "3.7-forge-2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ConveyorBeltPlus.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static int nextId;

    private NetworkChannel() {}

    public record Codec<T>(BiConsumer<FriendlyByteBuf, T> encoder, Function<FriendlyByteBuf, T> decoder) {
        public void encode(FriendlyByteBuf buf, T message) { encoder.accept(buf, message); }
        public T decode(FriendlyByteBuf buf) { return decoder.apply(buf); }
        public static <T> Codec<T> ofStatic(BiConsumer<FriendlyByteBuf, T> encoder, Function<FriendlyByteBuf, T> decoder) {
            return new Codec<>(encoder, decoder);
        }
    }
    public record Context(Player player) {}

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
                    Player player = direction == NetworkDirection.PLAY_TO_SERVER
                            ? context.get().getSender() : ClientContext.player();
                    if (player != null) handler.accept(message, new Context(player));
                }).add();
    }
    public static void sendToServer(Object message) { CHANNEL.sendToServer(message); }
    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
    public static void sendToPlayersTrackingChunk(ServerLevel world, ChunkPos pos, Object message) {
        // Never force a chunk load in response to an incoming packet.
        var chunk = world.getChunkSource().getChunkNow(pos.x, pos.z);
        if (chunk != null) CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk), message);
    }
    private static final class ClientContext {
        private static Player player() { return net.minecraft.client.Minecraft.getInstance().player; }
    }
}
