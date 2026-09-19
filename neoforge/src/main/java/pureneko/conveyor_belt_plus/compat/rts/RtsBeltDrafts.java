package pureneko.conveyor_belt_plus.compat.rts;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.network.ServerPlayerEntity;
import pureneko.conveyor_belt_plus.registry.ComponentContent;
import pureneko.conveyor_belt_plus.network.RtsNetworking;

import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Endpoint selection belongs to the builder, not a transient stack extracted by RTS. */
public final class RtsBeltDrafts {
    private RtsBeltDrafts() {}
    private record Draft(Identifier dimension, ItemStack stack) {}
    private static final WeakHashMap<PlayerEntity, Draft> DRAFTS = new WeakHashMap<>();

    public static void copySelection(ItemStack from, ItemStack to) {
        clearSelection(to);
        if (from.contains(ComponentContent.BELT_START.get()))
            to.set(ComponentContent.BELT_START.get(), from.get(ComponentContent.BELT_START.get()));
        if (from.contains(ComponentContent.BELT_DIR.get()))
            to.set(ComponentContent.BELT_DIR.get(), from.get(ComponentContent.BELT_DIR.get()));
        if (from.contains(ComponentContent.MIDPOINTS.get()))
            to.set(ComponentContent.MIDPOINTS.get(), from.get(ComponentContent.MIDPOINTS.get()));
    }
    public static void clearSelection(ItemStack stack) {
        stack.remove(ComponentContent.BELT_START.get());
        stack.remove(ComponentContent.BELT_DIR.get());
        stack.remove(ComponentContent.MIDPOINTS.get());
    }
    public static ActionResult use(PlayerEntity player, ItemStack stack, Supplier<ActionResult> action) {
        var original = stack.copy();
        var draft = DRAFTS.get(player);
        var dimension = player.getWorld().getRegistryKey().getValue();
        clearSelection(stack);
        if (draft != null && draft.dimension.equals(dimension) && draft.stack.isOf(stack.getItem()))
            copySelection(draft.stack, stack);
        try {
            action.get();
            // Consume the handled click even on rejection. RTS otherwise retries use/useOn,
            // which can reset the draft or resurrect an already dropped construction item.
            if (!stack.isEmpty() && stack.contains(ComponentContent.BELT_START.get()))
                DRAFTS.put(player, new Draft(dimension, stack.copyWithCount(1)));
            else DRAFTS.remove(player);
            sync(player);
            return ActionResult.SUCCESS;
        } finally {
            // Keep the real count changes, but never refund RTS storage a polluted prototype.
            copySelection(original, stack);
        }
    }
    public static void reset(PlayerEntity player) { DRAFTS.remove(player); sync(player); }
    public static void sync(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity server)) return;
        var draft = DRAFTS.get(player);
        if (draft != null && !draft.dimension.equals(player.getWorld().getRegistryKey().getValue())) {
            DRAFTS.remove(player);
            draft = null;
        }
        PacketDistributor.sendToPlayer(server, new RtsNetworking.Draft(draft == null ? ItemStack.EMPTY : draft.stack));
    }
}
