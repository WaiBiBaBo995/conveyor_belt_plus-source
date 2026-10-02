package pureneko.conveyor_belt_plus.util;

import static net.minecraft.core.Direction.*;

import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class MathHelpers {

    private MathHelpers() {}

    public static VoxelShape rotateVoxelShape(VoxelShape shape, Direction facing, AttachFace face) {

        if (shape.isEmpty()) return shape;

        var minX = shape.min(Axis.X);
        var maxX = shape.max(Axis.X);
        var minY = shape.min(Axis.Y);
        var maxY = shape.max(Axis.Y);
        var minZ = shape.min(Axis.Z);
        var maxZ = shape.max(Axis.Z);

        if (facing == NORTH) {
            if (face == AttachFace.FLOOR) return shape;
            if (face == AttachFace.WALL)
                return Shapes.box(1 - maxX, 1 - maxZ, 1 - maxY, 1 - minX, 1 - minZ, 1 - minY);
            if (face == AttachFace.CEILING)
                return Shapes.box(minX, 1 - maxY, 1 - maxZ, maxX, 1 - minY, 1 - minZ);
        }

        if (facing == SOUTH) {
            if (face == AttachFace.FLOOR)
                return Shapes.box(1 - maxX, minY, 1 - maxZ, 1 - minX, maxY, 1 - minZ);
            if (face == AttachFace.WALL)
                return Shapes.box(minX, 1 - maxZ, minY, maxX, 1 - minZ, maxY);
            if (face == AttachFace.CEILING)
                return Shapes.box(1 - maxX, 1 - maxY, minZ, 1 - minX, 1 - minY, maxZ);

        }

        if (facing == EAST) {
            if (face == AttachFace.FLOOR)
                return Shapes.box(1 - maxZ, minY, minX, 1 - minZ, maxY, maxX);
            if (face == AttachFace.WALL)
                return Shapes.box(minY, 1 - maxZ, 1 - maxX, maxY, 1 - minZ, 1 - minX);
            if (face == AttachFace.CEILING)
                return Shapes.box(minZ, 1 - maxY, minX, maxZ, 1 - minY, maxX);
        }

        if (facing == WEST) {
            if (face == AttachFace.FLOOR)
                return Shapes.box(minZ, minY, 1 - maxX, maxZ, maxY, 1 - minX);
            if (face == AttachFace.WALL)
                return Shapes.box(1 - maxY, 1 - maxZ, minX, 1 - minY, 1 - minZ, maxX);
            if (face == AttachFace.CEILING)
                return Shapes.box(1 - maxZ, 1 - maxY, 1 - maxX, 1 - minZ, 1 - minY, 1 - minX);
        }

        if (facing == UP) {
            return Shapes.box(minX, 1 - maxZ, minY, maxX, 1 - minZ, maxY);
        }

        if (facing == DOWN) {
            return Shapes.box(minX, minZ, minY, maxX, maxZ, maxY);
        }

        return shape;
    }

}
