package pureneko.conveyor_belt_plus.compat.rts;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import pureneko.conveyor_belt_plus.registry.ComponentContent;
import pureneko.conveyor_belt_plus.network.RtsNetworking;

import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Endpoint selection belongs to the builder, not a transient stack extracted by RTS. */
public final class RtsBeltDrafts {
    private RtsBeltDrafts() {}
    private record Draft(ResourceLocation dimension, ItemStack stack) {}
    private static final WeakHashMap<Player, Draft> DRAFTS = new WeakHashMap<>();

    public static void copySelection(ItemStack from, ItemStack to) {
        clearSelection(to);
        if (from.has(ComponentContent.BELT_START.get()))
            to.set(ComponentContent.BELT_START.get(), from.get(ComponentContent.BELT_START.get()));
        if (from.has(ComponentContent.BELT_DIR.get()))
            to.set(ComponentContent.BELT_DIR.get(), from.get(ComponentContent.BELT_DIR.get()));
        if (from.has(ComponentContent.MIDPOINTS.get()))
            to.set(ComponentContent.MIDPOINTS.get(), from.get(ComponentContent.MIDPOINTS.get()));
    }
    public static void clearSelection(ItemStack stack) {
        stack.remove(ComponentContent.BELT_START.get());
        stack.remove(ComponentContent.BELT_DIR.get());
        stack.remove(ComponentContent.MIDPOINTS.get());
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
            if (!stack.isEmpty() && stack.has(ComponentContent.BELT_START.get()))
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
        PacketDistributor.sendToPlayer(server, new RtsNetworking.Draft(draft == null ? ItemStack.EMPTY : draft.stack));
    }
}
