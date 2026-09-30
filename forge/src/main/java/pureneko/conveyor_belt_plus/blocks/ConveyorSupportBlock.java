package pureneko.conveyor_belt_plus.blocks;

import com.mojang.serialization.MapCodec;
import pureneko.conveyor_belt_plus.util.DirectionalShapes;
import pureneko.conveyor_belt_plus.util.MathHelpers;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ConveyorSupportBlock extends HorizontalDirectionalBlock {

    private static final DirectionalShapes SHAPES = new DirectionalShapes(ConveyorSupportBlock::createShape);

    private static VoxelShape createShape(Direction direction) {
        return Shapes.or(
                MathHelpers.rotateVoxelShape(Shapes.box(
                        7.0 / 16.0, 1.0 / 16.0, 7.0 / 16.0,
                        9.0 / 16.0, 4.0 / 16.0, 9.0 / 16.0
                ), direction, AttachFace.FLOOR),
                MathHelpers.rotateVoxelShape(Shapes.box(
                        2.0 / 16.0, 4.0 / 16.0, 6.0 / 16.0,
                        14.0 / 16.0, 6.0 / 16.0, 10.0 / 16.0
                ), direction, AttachFace.FLOOR),
                MathHelpers.rotateVoxelShape(Shapes.box(
                        2.0 / 16.0, 6.0 / 16.0, 6.0 / 16.0,
                        3.0 / 16.0, 7.0 / 16.0, 10.0 / 16.0
                ), direction, AttachFace.FLOOR),
                MathHelpers.rotateVoxelShape(Shapes.box(
                        13.0 / 16.0, 6.0 / 16.0, 6.0 / 16.0,
                        14.0 / 16.0, 7.0 / 16.0, 10.0 / 16.0
                ), direction, AttachFace.FLOOR),
                MathHelpers.rotateVoxelShape(Shapes.box(
                        4.0 / 16.0, 0.0 / 16.0, 4.0 / 16.0,
                        12.0 / 16.0, 1.0 / 16.0, 12.0 / 16.0
                ), direction, AttachFace.FLOOR)
        ).optimize();
    }

    public ConveyorSupportBlock(Properties settings) {
        super(settings);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        var dir = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        if (dir == Direction.SOUTH) dir = Direction.NORTH;
        if (dir == Direction.EAST) dir = Direction.WEST;
        return SHAPES.get(dir);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return Objects.requireNonNull(super.getStateForPlacement(ctx)).setValue(BlockStateProperties.HORIZONTAL_FACING, ctx.getHorizontalDirection().getOpposite());
    }

}
