package pureneko.conveyor_belt_plus.compat.rts;

import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import pureneko.conveyor_belt_plus.blocks.BeltInsertion;

import java.util.ArrayList;
import java.util.List;

/** The selected preview is only a filter; the server extracts actual stacks with their saved contents. */
public final class RtsBeltInsertion {
    private RtsBeltInsertion() {}
    private static boolean matches(ItemStack actual, ItemStack selected) {
        if (actual.isEmpty()) return false;
        if (actual.getItem() instanceof pureneko.conveyor_belt_plus.items.BeltItem) {
            actual = actual.copy(); selected = selected.copy();
            RtsBeltDrafts.clearSelection(actual); RtsBeltDrafts.clearSelection(selected);
        }
        return ItemStack.isSameItemSameComponents(actual, selected);
    }
    public static boolean put(ServerPlayer player, BeltInsertion.Destination target, ItemStack selected) {
        var held = player.getMainHandItem();
        if (matches(held, selected)) {
            if (!BeltInsertion.allowed(player, target, held, true) || !target.insert(held)) return false;
            BeltInsertion.syncInventory(player);
            return true;
        }
        var services = ServiceRegistry.getInstance();
        var session = services.session().getIfPresent(player);
        if (session == null) return false;
        var linked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        var sources = new ArrayList<IItemHandler>(RtsLinkedStorageResolver.itemHandlersForExtract(linked));
        var destinations = RtsLinkedStorageResolver.itemHandlersForInsert(linked);
        if (RtsStoragePageBuilder.shouldIncludePlayerMainInventoryInStorageView(player, session))
            sources.add(new PlayerMainInvWrapper(player.getInventory()));
        for (var source : sources) for (int slot = 0; slot < source.getSlots(); slot++) {
            var candidate = source.getStackInSlot(slot);
            if (candidate.isEmpty() || !matches(candidate, selected)) continue;
            if (!BeltInsertion.allowed(player, target, candidate, true) || !target.canInsert()) return false;
            int count = Math.min(candidate.getCount(), candidate.getMaxStackSize());
            var simulated = source.extractItem(slot, count, true);
            if (simulated.isEmpty() || !matches(simulated, selected)) continue;
            var extracted = source.extractItem(slot, Math.min(count, simulated.getCount()), false);
            if (extracted.isEmpty()) continue;
            boolean inserted = matches(extracted, selected) && target.insert(extracted);
            if (!extracted.isEmpty()) {
                var remainder = source.insertItem(slot, extracted, false);
                if (!remainder.isEmpty()) RtsTransferInserter.refundToLinked(destinations, player, remainder);
            }
            services.serviceOp().markDirty(player, session);
            BeltInsertion.syncInventory(player);
            return inserted;
        }
        return false;
    }
}
