package pureneko.conveyor_belt_plus.network;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.config.ConveyorConfig;
import pureneko.conveyor_belt_plus.filter.FilterRule;
import pureneko.conveyor_belt_plus.screen.ChuteScreenHandler;

import java.util.ArrayList;
import java.util.List;

/** Only updates virtual rules in the currently open, distance-checked server menu. */
public final class FilterNetworking {
    private FilterNetworking() {}
    public record Edit(int syncId, int slot, String rule) implements CustomPayload {
        public static final Id<Edit> ID = new Id<>(ConveyorBeltPlus.id("edit_filter"));
        public static final PacketCodec<RegistryByteBuf, Edit> CODEC = PacketCodec.ofStatic((buf, value) -> {
            buf.writeVarInt(value.syncId);
            buf.writeVarInt(value.slot);
            buf.writeString(value.rule, FilterRule.MAX_TEXT);
        }, buf -> new Edit(buf.readVarInt(), buf.readVarInt(), buf.readString(FilterRule.MAX_TEXT)));
        @Override public Id<Edit> getId() { return ID; }
    }

    public record Snapshot(int syncId, boolean whitelist, List<String> rules, String error) implements CustomPayload {
        public static final Id<Snapshot> ID = new Id<>(ConveyorBeltPlus.id("filter_snapshot"));
        public Snapshot { rules = List.copyOf(rules); }
        public static final PacketCodec<RegistryByteBuf, Snapshot> CODEC = PacketCodec.ofStatic((buf, value) -> {
            buf.writeVarInt(value.syncId);
            buf.writeBoolean(value.whitelist);
            buf.writeVarInt(value.rules.size());
            for (var rule : value.rules) buf.writeString(rule, FilterRule.MAX_TEXT);
            buf.writeString(value.error, 256);
        }, buf -> {
            int syncId = buf.readVarInt();
            boolean whitelist = buf.readBoolean();
            int count = buf.readVarInt();
            if (count < 1 || count > ConveyorConfig.MAX_FILTER_RULES) throw new IllegalArgumentException("Invalid filter capacity");
            var rules = new ArrayList<String>(count);
            for (int i = 0; i < count; i++) rules.add(buf.readString(FilterRule.MAX_TEXT));
            return new Snapshot(syncId, whitelist, rules, buf.readString(256));
        });
        @Override public Id<Snapshot> getId() { return ID; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("3.5");
        // NeoForge dispatches these handlers on the main game thread.
        registrar.playToServer(Edit.ID, Edit.CODEC, (message, context) -> {
            var player = context.player();
            if (player.currentScreenHandler instanceof ChuteScreenHandler menu
                    && menu.syncId == message.syncId && menu.canUse(player))
                menu.applyEdit(player, message.slot, message.rule);
        });
        registrar.playToClient(Snapshot.ID, Snapshot.CODEC, (message, context) -> {
            var player = context.player();
            if (player.currentScreenHandler instanceof ChuteScreenHandler menu && menu.syncId == message.syncId)
                menu.applySnapshot(message.whitelist, message.rules, message.error);
        });
    }

    public static void sendEdit(int syncId, int slot, String rule) {
        PacketDistributor.sendToServer(new Edit(syncId, slot, rule));
    }

    public static void sendSnapshot(ServerPlayerEntity player, Snapshot snapshot) {
        PacketDistributor.sendToPlayer(player, snapshot);
    }
}
