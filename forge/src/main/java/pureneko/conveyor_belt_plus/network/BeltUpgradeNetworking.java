package pureneko.conveyor_belt_plus.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import pureneko.conveyor_belt_plus.blocks.UpgradeInteractions;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import java.util.WeakHashMap;
import pureneko.conveyor_belt_plus.network.NetworkChannel.Codec;

/** No client-provided tier, stack or geometry is trusted. */
public final class BeltUpgradeNetworking {
    private BeltUpgradeNetworking() {}
    private static final WeakHashMap<Player, Integer> LAST = new WeakHashMap<>();
    public record Request(BlockPos owner, Direction port)  {

        public static final Codec<Request> CODEC = Codec.ofStatic((buf, value) -> {
            buf.writeBlockPos(value.owner); buf.writeEnum(value.port);
        }, buf -> new Request(buf.readBlockPos(), buf.readEnum(Direction.class)));

    }
    public static void register() {
        NetworkChannel.server(Request.class, Request.CODEC, (message, context) -> {
            var player = context.player();
            var last = LAST.put(player, player.tickCount);
            if (last == null || last != player.tickCount) UpgradeInteractions.beltAt(player, message.owner, message.port);
        });
    }
}
