package pureneko.conveyor_belt_plus.compat.rts;

import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import pureneko.conveyor_belt_plus.neoforge.ConveyorFluidApi;
import pureneko.conveyor_belt_plus.util.FluidPackets;

import java.util.ArrayList;
import java.util.List;

/** Resolve a selected container from authoritative RTS storage, then return the filled item to that network. */
public final class RtsFluidPickup {
    private RtsFluidPickup() {}
    public static int fill(ServerPlayer player, ItemStack packet, String selectedId, BlockPos owner, BlockPos hit) {
        if (!RtsCompat.canTake(player, owner) || !RtsCompat.canTake(player, hit)) return 0;
        var id = ResourceLocation.tryParse(selectedId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return 0;
        var selected = BuiltInRegistries.ITEM.get(id);
        if (player.getMainHandItem().is(selected)
                && RtsCompat.canTake(player, owner, player.getMainHandItem()) && RtsCompat.canTake(player, hit, player.getMainHandItem())) {
            int filled = pureneko.conveyor_belt_plus.blocks.BeltPickup.fillHeldContainer(player, packet);
            if (filled > 0) return filled;
        }
        var services = ServiceRegistry.getInstance();
        var session = services.session().getIfPresent(player);
        if (session == null) return 0;
        var linked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        var sources = new ArrayList<IItemHandler>(RtsLinkedStorageResolver.itemHandlersForExtract(linked));
        var destinations = RtsLinkedStorageResolver.itemHandlersForInsert(linked);
        if (RtsStoragePageBuilder.shouldIncludePlayerMainInventoryInStorageView(player, session))
            sources.add(new PlayerMainInvWrapper(player.getInventory()));
        int before = FluidPackets.amount(packet);
        for (var source : sources) for (int slot = 0; slot < source.getSlots(); slot++) {
            var candidate = source.getStackInSlot(slot);
            if (!candidate.is(selected) || !RtsCompat.canTake(player, owner, candidate) || !RtsCompat.canTake(player, hit, candidate)) continue;
            var simulated = source.extractItem(slot, 1, true);
            if (simulated.isEmpty() || !FluidUtil.tryFillContainer(simulated,
                    ConveyorFluidApi.packetHandler(packet), before, null, false).isSuccess()) continue;
            var extracted = source.extractItem(slot, 1, false);
            if (extracted.isEmpty()) continue;
            var result = FluidUtil.tryFillContainer(extracted, ConveyorFluidApi.packetHandler(packet), before, null, true);
            var returned = result.isSuccess() ? result.getResult() : extracted;
            RtsTransferInserter.refundToLinked(destinations, player, returned);
            services.serviceOp().markDirty(player, session);
            return before - FluidPackets.amount(packet);
        }
        return 0;
    }
}
