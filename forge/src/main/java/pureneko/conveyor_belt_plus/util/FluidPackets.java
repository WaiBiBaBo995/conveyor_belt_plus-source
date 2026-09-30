package pureneko.conveyor_belt_plus.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import pureneko.conveyor_belt_plus.registry.ItemContent;

/** An internal carrier reuses the belt's item serialization; its amount is always in mB, never item count. */
public final class FluidPackets {
    private FluidPackets() {}
    public static boolean isPacket(ItemStack stack) { return !stack.isEmpty() && stack.is(ItemContent.FLUID_PACKET.get()); }
    public static FluidStack get(ItemStack stack) {
        var tag = stack.getTagElement("conveyor_belt_plus");
        return isPacket(stack) && tag != null ? FluidStack.loadFluidStackFromNBT(tag.getCompound("fluid_content")) : FluidStack.EMPTY;
    }
    public static ItemStack create(FluidStack fluid) {
        if (fluid.isEmpty()) return ItemStack.EMPTY;
        var stack = new ItemStack(ItemContent.FLUID_PACKET.get());
        set(stack, fluid);
        return stack;
    }
    public static int amount(ItemStack stack) { return get(stack).getAmount(); }
    public static void set(ItemStack stack, FluidStack fluid) {
        if (fluid.isEmpty()) stack.setCount(0);
        else stack.getOrCreateTagElement("conveyor_belt_plus").put("fluid_content", fluid.writeToNBT(new CompoundTag()));
    }
    public static FluidStack marker(ItemStack stack) {
        return isPacket(stack) ? get(stack) : FluidUtil.getFluidContained(stack.copyWithCount(1)).orElse(FluidStack.EMPTY);
    }
    public static ItemStack icon(FluidStack fluid) {
        var bucket = FluidUtil.getFilledBucket(fluid);
        return bucket.isEmpty() ? create(withAmount(fluid, 1000)) : bucket;
    }
    public static boolean sameContents(ItemStack a, ItemStack b) {
        return isPacket(a) && isPacket(b)
                ? FluidPackets.sameFluid(get(a), get(b)) : ItemStack.isSameItemSameTags(a, b);
    }
    public static FluidStack withAmount(FluidStack fluid, int amount) {
        var copy = fluid.copy();
        copy.setAmount(amount);
        return copy;
    }
    public static boolean sameFluid(FluidStack first, FluidStack second) {
        return first.isFluidEqual(second);
    }
}
