package pureneko.conveyor_belt_plus.network;

import pureneko.conveyor_belt_plus.network.NetworkChannel;
import pureneko.conveyor_belt_plus.network.NetworkChannel.Codec;
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
    public record Edit(int syncId, int slot, String rule) {
        public static final Codec<Edit> CODEC = Codec.ofStatic((buf, value) -> {
            buf.writeVarInt(value.syncId);
            buf.writeVarInt(value.slot);
            buf.writeString(value.rule, FilterRule.MAX_TEXT);
        }, buf -> new Edit(buf.readVarInt(), buf.readVarInt(), buf.readString(FilterRule.MAX_TEXT)));
    }

    public record Snapshot(int syncId, boolean whitelist, List<String> rules, String error) {
        public Snapshot { rules = List.copyOf(rules); }
        public static final Codec<Snapshot> CODEC = Codec.ofStatic((buf, value) -> {
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
    }

    public static void register() {
        // MinecraftForge dispatches these handlers on the main game thread.
        NetworkChannel.server(Edit.class, Edit.CODEC, (message, context) -> {
            var player = context.player();
            if (player.currentScreenHandler instanceof ChuteScreenHandler menu
                    && menu.syncId == message.syncId && menu.canUse(player))
                menu.applyEdit(player, message.slot, message.rule);
        });
        NetworkChannel.client(Snapshot.class, Snapshot.CODEC, (message, context) -> {
            var player = context.player();
            if (player.currentScreenHandler instanceof ChuteScreenHandler menu && menu.syncId == message.syncId)
                menu.applySnapshot(message.whitelist, message.rules, message.error);
        });
    }

    public static void sendEdit(int syncId, int slot, String rule) {
        NetworkChannel.sendToServer(new Edit(syncId, slot, rule));
    }

    public static void sendSnapshot(ServerPlayerEntity player, Snapshot snapshot) {
        NetworkChannel.sendToPlayer(player, snapshot);
    }
}
