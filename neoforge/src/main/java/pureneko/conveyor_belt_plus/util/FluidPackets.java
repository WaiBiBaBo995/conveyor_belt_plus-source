package pureneko.conveyor_belt_plus.util;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import pureneko.conveyor_belt_plus.registry.ComponentContent;
import pureneko.conveyor_belt_plus.registry.ItemContent;

/** An internal carrier reuses the belt's item serialization; its amount is always in mB, never item count. */
public final class FluidPackets {
    private FluidPackets() {}
    public static boolean isPacket(ItemStack stack) { return !stack.isEmpty() && stack.is(ItemContent.FLUID_PACKET.get()); }
    public static FluidStack get(ItemStack stack) {
        return isPacket(stack) ? stack.getOrDefault(ComponentContent.FLUID_CONTENT.get(), FluidContent.EMPTY).copy() : FluidStack.EMPTY;
    }
    public static ItemStack create(FluidStack fluid) {
        if (fluid.isEmpty()) return ItemStack.EMPTY;
        var stack = new ItemStack(ItemContent.FLUID_PACKET.get());
        stack.set(ComponentContent.FLUID_CONTENT.get(), new FluidContent(fluid));
        return stack;
    }
    public static int amount(ItemStack stack) { return get(stack).getAmount(); }
    public static void set(ItemStack stack, FluidStack fluid) {
        if (fluid.isEmpty()) stack.setCount(0);
        else stack.set(ComponentContent.FLUID_CONTENT.get(), new FluidContent(fluid));
    }
    public static FluidStack marker(ItemStack stack) {
        return isPacket(stack) ? get(stack) : FluidUtil.getFluidContained(stack.copyWithCount(1)).orElse(FluidStack.EMPTY);
    }
    public static ItemStack icon(FluidStack fluid) {
        var bucket = FluidUtil.getFilledBucket(fluid);
        return bucket.isEmpty() ? create(fluid.copyWithAmount(1000)) : bucket;
    }
    public static boolean sameContents(ItemStack a, ItemStack b) {
        return isPacket(a) && isPacket(b)
                ? FluidStack.isSameFluidSameComponents(get(a), get(b)) : ItemStack.isSameItemSameComponents(a, b);
    }
}
