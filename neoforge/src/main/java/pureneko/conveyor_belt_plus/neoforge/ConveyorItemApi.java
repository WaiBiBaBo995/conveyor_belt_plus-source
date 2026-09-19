package pureneko.conveyor_belt_plus.neoforge;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/** The NeoForge inventory operations used by chute extraction and insertion. */
public final class ConveyorItemApi {
    private ConveyorItemApi() {}

    public static @Nullable InventoryStorage find(World world, BlockPos pos, Direction direction) {
        var handler = world.getCapability(Capabilities.ItemHandler.BLOCK, pos, null, null, direction);
        return handler == null ? null : new InventoryStorage(handler);
    }

    public static final class InventoryStorage {
        private final IItemHandler container;

        private InventoryStorage(IItemHandler container) {
            this.container = container;
        }

        /** Returns the number inserted; simulation leaves storage unchanged. */
        public int insert(ItemStack inserted, boolean simulate) {
            return inserted.getCount() - ItemHandlerHelper.insertItem(container, inserted, simulate).getCount();
        }

        /** Extracts only matching items and components, up to the requested count. */
        public int extract(ItemStack extracted) {
            int total = 0;
            for (int slot = 0; slot < container.getSlots() && total < extracted.getCount(); slot++) {
                var available = container.getStackInSlot(slot);
                if (ItemStack.areItemsAndComponentsEqual(available, extracted)) {
                    total += container.extractItem(slot, extracted.getCount() - total, false).getCount();
                }
            }
            return total;
        }

        public ItemStack getStackInSlot(int slot) { return container.getStackInSlot(slot); }
        public int getSlotCount() { return container.getSlots(); }
    }
}
