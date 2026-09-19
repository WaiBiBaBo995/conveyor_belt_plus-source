package pureneko.conveyor_belt_plus.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** A four-sided logistics splitter with a single-item-type cache. */
public class ConveyorSplitterBlock extends HorizontalFacingBlock implements BlockEntityProvider {

    private static final VoxelShape SHAPE = VoxelShapes.union(
            VoxelShapes.cuboid(3 / 16f, 19 / 16f, 3 / 16f, 13 / 16f, 20 / 16f, 13 / 16f),
            VoxelShapes.cuboid(1 / 16f, 0.0, 1 / 16f, 15 / 16f, 19 / 16f, 15 / 16f),
            VoxelShapes.cuboid(2 / 16f, 3 / 16f, 0.0, 14 / 16f, 17 / 16f, 1.0),
            VoxelShapes.cuboid(0.0, 3 / 16f, 2 / 16f, 1.0, 17 / 16f, 14 / 16f),
            VoxelShapes.cuboid(-2 / 16f, 4 / 16f, 2 / 16f, 1 / 16f, 1.0, 14 / 16f),
            VoxelShapes.cuboid(15 / 16f, 4 / 16f, 2 / 16f, 18 / 16f, 1.0, 14 / 16f),
            VoxelShapes.cuboid(2 / 16f, 4 / 16f, -2 / 16f, 14 / 16f, 1.0, 1 / 16f),
            VoxelShapes.cuboid(2 / 16f, 4 / 16f, 15 / 16f, 14 / 16f, 1.0, 18 / 16f)
    ).simplify();

    public ConveyorSplitterBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return null;
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return Objects.requireNonNull(super.getPlacementState(ctx))
                .with(Properties.HORIZONTAL_FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(Properties.HORIZONTAL_FACING);
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ConveyorSplitterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(World world, BlockState state,
                                                                              BlockEntityType<T> type) {
        return (world1, pos, state1, blockEntity) -> {
            if (blockEntity instanceof ConveyorSplitterBlockEntity splitter)
                splitter.tick(world1, pos, state1, splitter);
        };
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient) {
            var entity = world.getBlockEntity(pos);
            if (entity instanceof ConveyorSplitterBlockEntity splitter)
                splitter.dropContent(world, pos);
        }
        return super.onBreak(world, pos, state, player);
    }
}
