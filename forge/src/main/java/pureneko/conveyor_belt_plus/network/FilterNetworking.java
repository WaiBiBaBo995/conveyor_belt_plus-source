package pureneko.conveyor_belt_plus.network;

import pureneko.conveyor_belt_plus.network.NetworkChannel;
import pureneko.conveyor_belt_plus.network.NetworkChannel.Codec;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.config.ConveyorConfig;
import pureneko.conveyor_belt_plus.filter.FilterRule;
import pureneko.conveyor_belt_plus.screen.ChuteScreenHandler;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Only updates virtual rules in the currently open, distance-checked server menu. */
public final class FilterNetworking {
    private FilterNetworking() {}
    public record Edit(int syncId, boolean fluid, int slot, String rule) {
        public Edit(int syncId, int slot, String rule) { this(syncId, false, slot, rule); }
        public static final Codec<Edit> CODEC = Codec.ofStatic((buf, value) -> {
            buf.writeVarInt(value.syncId);
            buf.writeBoolean(value.fluid);
            buf.writeVarInt(value.slot);
            buf.writeUtf(value.rule, FilterRule.MAX_TEXT);
        }, buf -> new Edit(buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readUtf(FilterRule.MAX_TEXT)));
    }

    public record Snapshot(int syncId, boolean fluid, boolean whitelist, List<String> rules, String error) {
        public Snapshot(int syncId, boolean whitelist, List<String> rules, String error) { this(syncId, false, whitelist, rules, error); }
        public Snapshot { rules = List.copyOf(rules); }
        public static final Codec<Snapshot> CODEC = Codec.ofStatic((buf, value) -> {
            buf.writeVarInt(value.syncId);
            buf.writeBoolean(value.fluid);
            buf.writeBoolean(value.whitelist);
            buf.writeVarInt(value.rules.size());
            for (var rule : value.rules) buf.writeUtf(rule, FilterRule.MAX_TEXT);
            buf.writeUtf(value.error, 256);
        }, buf -> {
            int syncId = buf.readVarInt();
            boolean fluid = buf.readBoolean();
            boolean whitelist = buf.readBoolean();
            int count = buf.readVarInt();
            if (count < 1 || count > ConveyorConfig.MAX_FILTER_RULES) throw new IllegalArgumentException("Invalid filter capacity");
            var rules = new ArrayList<String>(count);
            for (int i = 0; i < count; i++) rules.add(buf.readUtf(FilterRule.MAX_TEXT));
            return new Snapshot(syncId, fluid, whitelist, rules, buf.readUtf(256));
        });
    }

    public static void register() {
        // Forge dispatches these handlers on the main game thread.
        NetworkChannel.server(Edit.class, Edit.CODEC, (message, context) -> {
            var player = context.player();
            if (player.containerMenu instanceof ChuteScreenHandler menu
                    && menu.containerId == message.syncId && menu.stillValid(player))
                menu.applyEdit(player, message.fluid, message.slot, message.rule);
        });
        NetworkChannel.client(Snapshot.class, Snapshot.CODEC, (message, context) -> {
            var player = context.player();
            if (player.containerMenu instanceof ChuteScreenHandler menu && menu.containerId == message.syncId)
                menu.applySnapshot(message.fluid, message.whitelist, message.rules, message.error);
        });
    }

    public static void sendEdit(int syncId, boolean fluid, int slot, String rule) {
        NetworkChannel.sendToServer(new Edit(syncId, fluid, slot, rule));
    }

    public static void sendSnapshot(ServerPlayer player, Snapshot snapshot) {
        NetworkChannel.sendToPlayer(player, snapshot);
    }
}
