package pureneko.conveyor_belt_plus.blocks;

import com.mojang.serialization.MapCodec;
import pureneko.conveyor_belt_plus.util.DirectionalShapes;
import pureneko.conveyor_belt_plus.util.MathHelpers;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class ConveyorSupportBlock extends HorizontalFacingBlock {

    private static final DirectionalShapes SHAPES = new DirectionalShapes(ConveyorSupportBlock::createShape);

    private static VoxelShape createShape(Direction direction) {
        return VoxelShapes.union(
                MathHelpers.rotateVoxelShape(VoxelShapes.cuboid(
                        7.0 / 16.0, 1.0 / 16.0, 7.0 / 16.0,
                        9.0 / 16.0, 4.0 / 16.0, 9.0 / 16.0
                ), direction, BlockFace.FLOOR),
                MathHelpers.rotateVoxelShape(VoxelShapes.cuboid(
                        2.0 / 16.0, 4.0 / 16.0, 6.0 / 16.0,
                        14.0 / 16.0, 6.0 / 16.0, 10.0 / 16.0
                ), direction, BlockFace.FLOOR),
                MathHelpers.rotateVoxelShape(VoxelShapes.cuboid(
                        2.0 / 16.0, 6.0 / 16.0, 6.0 / 16.0,
                        3.0 / 16.0, 7.0 / 16.0, 10.0 / 16.0
                ), direction, BlockFace.FLOOR),
                MathHelpers.rotateVoxelShape(VoxelShapes.cuboid(
                        13.0 / 16.0, 6.0 / 16.0, 6.0 / 16.0,
                        14.0 / 16.0, 7.0 / 16.0, 10.0 / 16.0
                ), direction, BlockFace.FLOOR),
                MathHelpers.rotateVoxelShape(VoxelShapes.cuboid(
                        4.0 / 16.0, 0.0 / 16.0, 4.0 / 16.0,
                        12.0 / 16.0, 1.0 / 16.0, 12.0 / 16.0
                ), direction, BlockFace.FLOOR)
        ).simplify();
    }

    public ConveyorSupportBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(Properties.HORIZONTAL_FACING);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        var dir = state.get(Properties.HORIZONTAL_FACING);
        if (dir == Direction.SOUTH) dir = Direction.NORTH;
        if (dir == Direction.EAST) dir = Direction.WEST;
        return SHAPES.get(dir);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return Objects.requireNonNull(super.getPlacementState(ctx)).with(Properties.HORIZONTAL_FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return null;
    }
}
