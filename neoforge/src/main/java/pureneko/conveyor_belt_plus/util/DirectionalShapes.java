package pureneko.conveyor_belt_plus.util;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Lazily builds and caches one voxel shape per facing; shapes are shared by every block instance. */
public final class DirectionalShapes {
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
    private final Function<Direction, VoxelShape> factory;

    public DirectionalShapes(Function<Direction, VoxelShape> factory) {
        this.factory = factory;
    }

    public VoxelShape get(Direction direction) {
        return shapes.computeIfAbsent(direction, factory);
    }
}
