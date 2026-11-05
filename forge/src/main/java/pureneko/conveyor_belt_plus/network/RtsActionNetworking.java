package pureneko.conveyor_belt_plus.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;
import java.util.WeakHashMap;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;

/** Explicit RTS Shift intent; the server resolves actual selected materials and validates the camera ray. */
public final class RtsActionNetworking {
    private RtsActionNetworking() {}
    private static final WeakHashMap<Player,Integer> LAST=new WeakHashMap<>();
    public record Action(BlockPos owner, Direction face, Vec3 origin, Vec3 direction, Vec3 hit,
                         ItemStack selected, boolean shift, boolean body, boolean storage)  {

        public static final NetworkChannel.Codec<Action> CODEC=NetworkChannel.Codec.ofStatic((buf,value)->{
            buf.writeBlockPos(value.owner);buf.writeEnum(value.face);
            buf.writeDouble(value.origin.x);buf.writeDouble(value.origin.y);buf.writeDouble(value.origin.z);
            buf.writeDouble(value.direction.x);buf.writeDouble(value.direction.y);buf.writeDouble(value.direction.z);
            buf.writeDouble(value.hit.x);buf.writeDouble(value.hit.y);buf.writeDouble(value.hit.z);
            buf.writeItem(value.selected);
            buf.writeBoolean(value.shift);buf.writeBoolean(value.body);buf.writeBoolean(value.storage);
        },buf->new Action(buf.readBlockPos(),buf.readEnum(Direction.class),
                new Vec3(buf.readDouble(),buf.readDouble(),buf.readDouble()),new Vec3(buf.readDouble(),buf.readDouble(),buf.readDouble()),
                new Vec3(buf.readDouble(),buf.readDouble(),buf.readDouble()),buf.readItem(),buf.readBoolean(),buf.readBoolean(),buf.readBoolean()));

    }
    public static void register() {
        NetworkChannel.server(Action.class,Action.CODEC, (message,context)->{
            var player=context.player();
            if(!net.minecraftforge.fml.ModList.get().isLoaded("rtsbuilding"))return;
            var previous=LAST.put(player,player.tickCount);
            if(previous!=null && previous==player.tickCount)return;
            pureneko.conveyor_belt_plus.compat.rts.RtsActions.use((net.minecraft.server.level.ServerPlayer)player,
                    message.owner,message.face,message.origin,message.direction,message.hit,message.selected,message.shift,message.body,message.storage);
        });
    }
}
