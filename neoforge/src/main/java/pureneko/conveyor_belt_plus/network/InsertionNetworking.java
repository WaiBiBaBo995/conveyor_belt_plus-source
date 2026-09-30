package pureneko.conveyor_belt_plus.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import pureneko.conveyor_belt_plus.blocks.BeltInsertion;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;

import java.util.WeakHashMap;

public final class InsertionNetworking {
    private InsertionNetworking() {}
    private static final WeakHashMap<Player, Integer> LAST_REQUEST = new WeakHashMap<>();
    public record Put(BlockPos owner, Direction port) implements CustomPacketPayload {
        public static final Type<Put> ID = new Type<>(ConveyorBeltPlus.id("belt_put"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Put> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeBlockPos(value.owner); buf.writeEnum(value.port);
        }, buf -> new Put(buf.readBlockPos(), buf.readEnum(Direction.class)));
        @Override public Type<Put> type() { return ID; }
    }
    public record RemotePut(BlockPos owner, Direction port, Vec3 origin, Vec3 direction, ItemStack selected) implements CustomPacketPayload {
        public static final Type<RemotePut> ID = new Type<>(ConveyorBeltPlus.id("rts_belt_put"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RemotePut> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeBlockPos(value.owner); buf.writeEnum(value.port);
            buf.writeDouble(value.origin.x); buf.writeDouble(value.origin.y); buf.writeDouble(value.origin.z);
            buf.writeDouble(value.direction.x); buf.writeDouble(value.direction.y); buf.writeDouble(value.direction.z);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, value.selected.copyWithCount(1));
        }, buf -> new RemotePut(buf.readBlockPos(), buf.readEnum(Direction.class),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));
        @Override public Type<RemotePut> type() { return ID; }
    }
    private static boolean firstRequest(Player player) {
        var previous = LAST_REQUEST.put(player, player.tickCount);
        return previous == null || previous != player.tickCount;
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("3.7-insertion-1");
        registrar.playToServer(Put.ID, Put.CODEC, (message, context) -> {
            if (firstRequest(context.player())) BeltInsertion.put(context.player(), message.owner, message.port);
        });
        registrar.playToServer(RemotePut.ID, RemotePut.CODEC, (message, context) -> {
            if (firstRequest(context.player())) BeltInsertion.putRemote(context.player(), message.owner, message.port,
                    message.origin, message.direction, message.selected);
        });
    }
}
