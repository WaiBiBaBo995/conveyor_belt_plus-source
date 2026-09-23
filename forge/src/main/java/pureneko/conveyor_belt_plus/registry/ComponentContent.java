package pureneko.conveyor_belt_plus.registry;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Typed access to belt drafts in a namespaced 1.20.1 item NBT compound. */
public final class ComponentContent {
    private static final String ROOT = ConveyorBeltPlus.MOD_ID;
    private ComponentContent() {}
    public record Key<T>(String name, Function<NbtCompound, T> reader, BiConsumer<NbtCompound, T> writer) {
        public T get(ItemStack stack) {
            var tag = stack.getSubNbt(ROOT);
            return tag == null || !tag.contains(name) ? null : reader.apply(tag);
        }
        public boolean contains(ItemStack stack) { return get(stack) != null; }
        public void set(ItemStack stack, T value) { writer.accept(stack.getOrCreateSubNbt(ROOT), value); }
        public void remove(ItemStack stack) {
            var tag = stack.getSubNbt(ROOT);
            if (tag == null) return;
            tag.remove(name);
            if (tag.isEmpty()) stack.removeSubNbt(ROOT);
        }
    }
    public static final Key<BlockPos> BELT_START = new Key<>("belt_start",
            tag -> BlockPos.fromLong(tag.getLong("belt_start")), (tag, pos) -> tag.putLong("belt_start", pos.asLong()));
    public static final Key<Direction> BELT_DIR = new Key<>("belt_start_dir",
            tag -> Direction.byId(tag.getInt("belt_start_dir")), (tag, dir) -> tag.putInt("belt_start_dir", dir.getId()));
    public static final Key<List<BlockPos>> MIDPOINTS = new Key<>("belt_midpoints", tag -> {
        var points = new ArrayList<BlockPos>();
        for (long packed : tag.getLongArray("belt_midpoints")) points.add(BlockPos.fromLong(packed));
        return List.copyOf(points);
    }, (tag, points) -> tag.putLongArray("belt_midpoints", points.stream().mapToLong(BlockPos::asLong).toArray()));
}
