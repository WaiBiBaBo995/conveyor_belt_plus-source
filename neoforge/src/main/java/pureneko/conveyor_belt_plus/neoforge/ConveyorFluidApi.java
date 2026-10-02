package pureneko.conveyor_belt_plus.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import pureneko.conveyor_belt_plus.util.FluidPackets;

public final class ConveyorFluidApi {
    private ConveyorFluidApi() {}
    public static IFluidHandler find(Level world, BlockPos pos, Direction side) {
        return world.hasChunkAt(pos) ? world.getCapability(Capabilities.FluidHandler.BLOCK, pos, side) : null;
    }
    /** Drain-only carrier: also lets a recovered parcel be emptied with standard mod fluid APIs. */
    public static IFluidHandlerItem packetHandler(ItemStack packet) {
        return new IFluidHandlerItem() {
            @Override public ItemStack getContainer() { return packet; }
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int tank) { return tank == 0 ? FluidPackets.get(packet) : FluidStack.EMPTY; }
            @Override public int getTankCapacity(int tank) { return tank == 0 ? FluidPackets.amount(packet) : 0; }
            @Override public boolean isFluidValid(int tank, FluidStack stack) { return false; }
            @Override public int fill(FluidStack stack, FluidAction action) { return 0; }
            @Override public FluidStack drain(FluidStack requested, FluidAction action) {
                return FluidStack.isSameFluidSameComponents(requested, FluidPackets.get(packet))
                        ? drain(requested.getAmount(), action) : FluidStack.EMPTY;
            }
            @Override public FluidStack drain(int amount, FluidAction action) {
                var contents = FluidPackets.get(packet);
                if (contents.isEmpty() || amount <= 0) return FluidStack.EMPTY;
                var drained = contents.copyWithAmount(Math.min(amount, contents.getAmount()));
                if (action.execute()) {
                    contents.shrink(drained.getAmount());
                    FluidPackets.set(packet, contents);
                }
                return drained;
            }
        };
    }
}
