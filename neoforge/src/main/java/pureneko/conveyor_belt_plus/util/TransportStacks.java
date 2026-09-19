package pureneko.conveyor_belt_plus.util;

import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Transport packets may contain several stacks; never encode an oversized vanilla ItemStack. */
public final class TransportStacks {
    private TransportStacks() {}

    public static void write(NbtCompound tag, String key, ItemStack stack,
                             RegistryWrapper.WrapperLookup lookup) {
        tag.put(key, stack.copyWithCount(1).encodeAllowEmpty(lookup));
        tag.putInt(key + "Count", stack.getCount());
    }

    public static ItemStack read(NbtCompound tag, String key, RegistryWrapper.WrapperLookup lookup) {
        ItemStack stack = ItemStack.fromNbtOrEmpty(lookup, tag.getCompound(key));
        if (!stack.isEmpty() && tag.contains(key + "Count"))
            stack.setCount(Math.max(0, tag.getInt(key + "Count")));
        return stack;
    }

    public static void drop(World world, BlockPos pos, ItemStack stack) {
        if (world == null || world.isClient || stack.isEmpty()) return;
        var center = pos.toCenterPos();
        int remaining = stack.getCount();
        while (remaining > 0) {
            int count = Math.min(remaining, stack.getMaxCount());
            world.spawnEntity(new ItemEntity(world, center.x, center.y, center.z, stack.copyWithCount(count)));
            remaining -= count;
        }
    }
}
