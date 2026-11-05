package pureneko.conveyor_belt_plus.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import pureneko.conveyor_belt_plus.blocks.UpgradeInteractions;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import java.util.WeakHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** No client-provided tier, stack or geometry is trusted. */
public final class BeltUpgradeNetworking {
    private BeltUpgradeNetworking() {}
    private static final WeakHashMap<Player, Integer> LAST = new WeakHashMap<>();
    public record Request(BlockPos owner, Direction port) implements CustomPacketPayload {
        public static final Type<Request> ID = new Type<>(ConveyorBeltPlus.id("belt_upgrade"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeBlockPos(value.owner); buf.writeEnum(value.port);
        }, buf -> new Request(buf.readBlockPos(), buf.readEnum(Direction.class)));
        @Override public Type<Request> type() { return ID; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("3.4").playToServer(Request.ID, Request.CODEC, (message, context) -> {
            var player = context.player();
            var last = LAST.put(player, player.tickCount);
            if (last == null || last != player.tickCount) UpgradeInteractions.beltAt(player, message.owner, message.port);
        });
    }
}
