package pureneko.conveyor_belt_plus.forge;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/** The MinecraftForge inventory operations used by chute extraction and insertion. */
public final class ConveyorItemApi {
    private ConveyorItemApi() {}

    public static @Nullable InventoryStorage find(World world, BlockPos pos, Direction direction) {
        if (!world.isChunkLoaded(pos)) return null;
        var entity = world.getBlockEntity(pos);
        var handler = entity == null ? null : entity.getCapability(ForgeCapabilities.ITEM_HANDLER, direction).orElse(null);
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
                if (ItemStack.canCombine(available, extracted)) {
                    total += container.extractItem(slot, extracted.getCount() - total, false).getCount();
                }
            }
            return total;
        }

        public ItemStack getStackInSlot(int slot) { return container.getStackInSlot(slot); }
        public int getSlotCount() { return container.getSlots(); }
    }
}
