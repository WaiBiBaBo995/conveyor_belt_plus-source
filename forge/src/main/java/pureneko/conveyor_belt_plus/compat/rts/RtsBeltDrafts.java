package pureneko.conveyor_belt_plus.compat.rts;

import pureneko.conveyor_belt_plus.network.NetworkChannel;
import pureneko.conveyor_belt_plus.registry.ComponentContent;
import pureneko.conveyor_belt_plus.network.RtsNetworking;

import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Endpoint selection belongs to the builder, not a transient stack extracted by RTS. */
public final class RtsBeltDrafts {
    private RtsBeltDrafts() {}
    private record Draft(ResourceLocation dimension, ItemStack stack) {}
    private static final WeakHashMap<Player, Draft> DRAFTS = new WeakHashMap<>();

    public static void copySelection(ItemStack from, ItemStack to) {
        clearSelection(to);
        if (ComponentContent.BELT_START.contains(from))
            ComponentContent.BELT_START.set(to, ComponentContent.BELT_START.get(from));
        if (ComponentContent.BELT_DIR.contains(from))
            ComponentContent.BELT_DIR.set(to, ComponentContent.BELT_DIR.get(from));
        if (ComponentContent.MIDPOINTS.contains(from))
            ComponentContent.MIDPOINTS.set(to, ComponentContent.MIDPOINTS.get(from));
    }
    public static void clearSelection(ItemStack stack) {
        ComponentContent.BELT_START.remove(stack);
        ComponentContent.BELT_DIR.remove(stack);
        ComponentContent.MIDPOINTS.remove(stack);
    }
    public static InteractionResult use(Player player, ItemStack stack, Supplier<InteractionResult> action) {
        var original = stack.copy();
        var draft = DRAFTS.get(player);
        var dimension = player.level().dimension().location();
        clearSelection(stack);
        if (draft != null && draft.dimension.equals(dimension) && draft.stack.is(stack.getItem()))
            copySelection(draft.stack, stack);
        try {
            action.get();
            // Consume the handled click even on rejection. RTS otherwise retries use/useOn,
            // which can reset the draft or repeat a rejected construction attempt.
            if (!stack.isEmpty() && ComponentContent.BELT_START.contains(stack))
                DRAFTS.put(player, new Draft(dimension, stack.copyWithCount(1)));
            else DRAFTS.remove(player);
            sync(player);
            return InteractionResult.SUCCESS;
        } finally {
            // Keep the real count changes, but never refund RTS storage a polluted prototype.
            copySelection(original, stack);
        }
    }
    public static void reset(Player player) { DRAFTS.remove(player); sync(player); }
    public static void sync(Player player) {
        if (!(player instanceof ServerPlayer server)) return;
        var draft = DRAFTS.get(player);
        if (draft != null && !draft.dimension.equals(player.level().dimension().location())) {
            DRAFTS.remove(player);
            draft = null;
        }
        NetworkChannel.sendToPlayer(server, new RtsNetworking.Draft(draft == null ? ItemStack.EMPTY : draft.stack));
    }
}
