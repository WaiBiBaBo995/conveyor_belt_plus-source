package pureneko.conveyor_belt_plus.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Typed access to belt drafts in a namespaced 1.20.1 item NBT compound. */
public final class ComponentContent {
    private static final String ROOT = ConveyorBeltPlus.MOD_ID;
    private ComponentContent() {}
    public record Key<T>(String name, Function<CompoundTag, T> reader, BiConsumer<CompoundTag, T> writer) {
        public T get(ItemStack stack) {
            var tag = stack.getTagElement(ROOT);
            return tag == null || !tag.contains(name) ? null : reader.apply(tag);
        }
        public boolean contains(ItemStack stack) { return get(stack) != null; }
        public void set(ItemStack stack, T value) { writer.accept(stack.getOrCreateTagElement(ROOT), value); }
        public void remove(ItemStack stack) {
            var tag = stack.getTagElement(ROOT);
            if (tag == null) return;
            tag.remove(name);
            if (tag.isEmpty()) stack.removeTagKey(ROOT);
        }
    }
    public static final Key<BlockPos> BELT_START = new Key<>("belt_start",
            tag -> BlockPos.of(tag.getLong("belt_start")), (tag, pos) -> tag.putLong("belt_start", pos.asLong()));
    public static final Key<Direction> BELT_DIR = new Key<>("belt_start_dir",
            tag -> Direction.from3DDataValue(tag.getInt("belt_start_dir")), (tag, dir) -> tag.putInt("belt_start_dir", dir.get3DDataValue()));
    public static final Key<List<BlockPos>> MIDPOINTS = new Key<>("belt_midpoints", tag -> {
        var points = new ArrayList<BlockPos>();
        for (long packed : tag.getLongArray("belt_midpoints")) points.add(BlockPos.of(packed));
        return List.copyOf(points);
    }, (tag, points) -> tag.putLongArray("belt_midpoints", points.stream().mapToLong(BlockPos::asLong).toArray()));
}
