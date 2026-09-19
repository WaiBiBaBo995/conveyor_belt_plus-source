package pureneko.conveyor_belt_plus.util;

import net.minecraft.block.enums.BlockFace;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import static net.minecraft.util.math.Direction.*;

public final class MathHelpers {

    private MathHelpers() {}

    public static VoxelShape rotateVoxelShape(VoxelShape shape, Direction facing, BlockFace face) {

        if (shape.isEmpty()) return shape;

        var minX = shape.getMin(Axis.X);
        var maxX = shape.getMax(Axis.X);
        var minY = shape.getMin(Axis.Y);
        var maxY = shape.getMax(Axis.Y);
        var minZ = shape.getMin(Axis.Z);
        var maxZ = shape.getMax(Axis.Z);

        if (facing == NORTH) {
            if (face == BlockFace.FLOOR) return shape;
            if (face == BlockFace.WALL)
                return VoxelShapes.cuboid(1 - maxX, 1 - maxZ, 1 - maxY, 1 - minX, 1 - minZ, 1 - minY);
            if (face == BlockFace.CEILING)
                return VoxelShapes.cuboid(minX, 1 - maxY, 1 - maxZ, maxX, 1 - minY, 1 - minZ);
        }

        if (facing == SOUTH) {
            if (face == BlockFace.FLOOR)
                return VoxelShapes.cuboid(1 - maxX, minY, 1 - maxZ, 1 - minX, maxY, 1 - minZ);
            if (face == BlockFace.WALL)
                return VoxelShapes.cuboid(minX, 1 - maxZ, minY, maxX, 1 - minZ, maxY);
            if (face == BlockFace.CEILING)
                return VoxelShapes.cuboid(1 - maxX, 1 - maxY, minZ, 1 - minX, 1 - minY, maxZ);

        }

        if (facing == EAST) {
            if (face == BlockFace.FLOOR)
                return VoxelShapes.cuboid(1 - maxZ, minY, minX, 1 - minZ, maxY, maxX);
            if (face == BlockFace.WALL)
                return VoxelShapes.cuboid(minY, 1 - maxZ, 1 - maxX, maxY, 1 - minZ, 1 - minX);
            if (face == BlockFace.CEILING)
                return VoxelShapes.cuboid(minZ, 1 - maxY, minX, maxZ, 1 - minY, maxX);
        }

        if (facing == WEST) {
            if (face == BlockFace.FLOOR)
                return VoxelShapes.cuboid(minZ, minY, 1 - maxX, maxZ, maxY, 1 - minX);
            if (face == BlockFace.WALL)
                return VoxelShapes.cuboid(1 - maxY, 1 - maxZ, minX, 1 - minY, 1 - minZ, maxX);
            if (face == BlockFace.CEILING)
                return VoxelShapes.cuboid(1 - maxZ, 1 - maxY, 1 - maxX, 1 - minZ, 1 - minY, 1 - minX);
        }

        if (facing == UP) {
            return VoxelShapes.cuboid(minX, 1 - maxZ, minY, maxX, 1 - minZ, maxY);
        }

        if (facing == DOWN) {
            return VoxelShapes.cuboid(minX, minZ, minY, maxX, maxZ, maxY);
        }

        return shape;
    }

}
