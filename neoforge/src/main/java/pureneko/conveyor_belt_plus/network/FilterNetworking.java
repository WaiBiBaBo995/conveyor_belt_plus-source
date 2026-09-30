package pureneko.conveyor_belt_plus.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.config.ConveyorConfig;
import pureneko.conveyor_belt_plus.filter.FilterRule;
import pureneko.conveyor_belt_plus.screen.ChuteScreenHandler;

import java.util.ArrayList;
import java.util.List;

/** Only updates virtual rules in the currently open, distance-checked server menu. */
public final class FilterNetworking {
    private FilterNetworking() {}
    public record Edit(int syncId, boolean fluid, int slot, String rule) implements CustomPacketPayload {
        public Edit(int syncId, int slot, String rule) { this(syncId, false, slot, rule); }
        public static final Type<Edit> ID = new Type<>(ConveyorBeltPlus.id("edit_filter"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Edit> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeVarInt(value.syncId);
            buf.writeBoolean(value.fluid);
            buf.writeVarInt(value.slot);
            buf.writeUtf(value.rule, FilterRule.MAX_TEXT);
        }, buf -> new Edit(buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readUtf(FilterRule.MAX_TEXT)));
        @Override public Type<Edit> type() { return ID; }
    }

    public record Snapshot(int syncId, boolean fluid, boolean whitelist, List<String> rules, String error) implements CustomPacketPayload {
        public Snapshot(int syncId, boolean whitelist, List<String> rules, String error) { this(syncId, false, whitelist, rules, error); }
        public static final Type<Snapshot> ID = new Type<>(ConveyorBeltPlus.id("filter_snapshot"));
        public Snapshot { rules = List.copyOf(rules); }
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of((buf, value) -> {
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
        @Override public Type<Snapshot> type() { return ID; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("3.7");
        // NeoForge dispatches these handlers on the main game thread.
        registrar.playToServer(Edit.ID, Edit.CODEC, (message, context) -> {
            var player = context.player();
            if (player.containerMenu instanceof ChuteScreenHandler menu
                    && menu.containerId == message.syncId && menu.stillValid(player))
                menu.applyEdit(player, message.fluid, message.slot, message.rule);
        });
        registrar.playToClient(Snapshot.ID, Snapshot.CODEC, (message, context) -> {
            var player = context.player();
            if (player.containerMenu instanceof ChuteScreenHandler menu && menu.containerId == message.syncId)
                menu.applySnapshot(message.fluid, message.whitelist, message.rules, message.error);
        });
    }

    public static void sendEdit(int syncId, boolean fluid, int slot, String rule) {
        PacketDistributor.sendToServer(new Edit(syncId, fluid, slot, rule));
    }

    public static void sendSnapshot(ServerPlayer player, Snapshot snapshot) {
        PacketDistributor.sendToPlayer(player, snapshot);
    }
}
