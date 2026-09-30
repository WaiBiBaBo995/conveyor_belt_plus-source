package pureneko.conveyor_belt_plus.util;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Transport packets may contain several stacks; never encode an oversized vanilla ItemStack. */
public final class TransportStacks {
    private TransportStacks() {}

    public static void write(CompoundTag tag, String key, ItemStack stack,
                             net.minecraft.core.RegistryAccess lookup) {
        tag.put(key, stack.copyWithCount(1).save(new CompoundTag()));
        tag.putInt(key + "Count", stack.getCount());
    }

    public static ItemStack read(CompoundTag tag, String key, net.minecraft.core.RegistryAccess lookup) {
        ItemStack stack = ItemStack.of(tag.getCompound(key));
        if (!stack.isEmpty() && tag.contains(key + "Count"))
            stack.setCount(Math.max(0, tag.getInt(key + "Count")));
        return stack;
    }

    public static void drop(Level world, BlockPos pos, ItemStack stack) {
        if (world == null || world.isClientSide || stack.isEmpty()) return;
        var center = pos.getCenter();
        int remaining = stack.getCount();
        while (remaining > 0) {
            int count = Math.min(remaining, stack.getMaxStackSize());
            world.addFreshEntity(new ItemEntity(world, center.x, center.y, center.z, stack.copyWithCount(count)));
            remaining -= count;
        }
    }
}
