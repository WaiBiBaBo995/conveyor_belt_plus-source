package pureneko.conveyor_belt_plus.network;

import pureneko.conveyor_belt_plus.network.NetworkChannel.Codec;
import pureneko.conveyor_belt_plus.blocks.BeltInsertion;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;

import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class InsertionNetworking {
    private InsertionNetworking() {}
    private static final WeakHashMap<Player, Integer> LAST_REQUEST = new WeakHashMap<>();
    public record Put(BlockPos owner, Direction port) {
        public static final Codec<Put> CODEC = Codec.ofStatic((buf, value) -> {
            buf.writeBlockPos(value.owner); buf.writeEnum(value.port);
        }, buf -> new Put(buf.readBlockPos(), buf.readEnum(Direction.class)));
    }
    public record RemotePut(BlockPos owner, Direction port, Vec3 origin, Vec3 direction, ItemStack selected) {
        public static final Codec<RemotePut> CODEC = Codec.ofStatic((buf, value) -> {
            buf.writeBlockPos(value.owner); buf.writeEnum(value.port);
            buf.writeDouble(value.origin.x); buf.writeDouble(value.origin.y); buf.writeDouble(value.origin.z);
            buf.writeDouble(value.direction.x); buf.writeDouble(value.direction.y); buf.writeDouble(value.direction.z);
            buf.writeItem(value.selected.copyWithCount(1));
        }, buf -> new RemotePut(buf.readBlockPos(), buf.readEnum(Direction.class),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readItem()));
    }
    private static boolean firstRequest(Player player) {
        var previous = LAST_REQUEST.put(player, player.tickCount);
        return previous == null || previous != player.tickCount;
    }
    public static void register() {
        NetworkChannel.server(Put.class, Put.CODEC, (message, context) -> {
            if (firstRequest(context.player())) BeltInsertion.put(context.player(), message.owner, message.port);
        });
        NetworkChannel.server(RemotePut.class, RemotePut.CODEC, (message, context) -> {
            if (firstRequest(context.player())) BeltInsertion.putRemote(context.player(), message.owner, message.port,
                    message.origin, message.direction, message.selected);
        });
    }
}
