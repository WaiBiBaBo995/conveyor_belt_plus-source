package pureneko.conveyor_belt_plus.blocks;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A four-sided logistics splitter with a single-item-type cache. */
public class ConveyorSplitterBlock extends HorizontalDirectionalBlock implements EntityBlock {

    private static final VoxelShape SHAPE = Shapes.or(
            Shapes.box(3 / 16f, 19 / 16f, 3 / 16f, 13 / 16f, 20 / 16f, 13 / 16f),
            Shapes.box(1 / 16f, 0.0, 1 / 16f, 15 / 16f, 19 / 16f, 15 / 16f),
            Shapes.box(2 / 16f, 3 / 16f, 0.0, 14 / 16f, 17 / 16f, 1.0),
            Shapes.box(0.0, 3 / 16f, 2 / 16f, 1.0, 17 / 16f, 14 / 16f),
            Shapes.box(-2 / 16f, 4 / 16f, 2 / 16f, 1 / 16f, 1.0, 14 / 16f),
            Shapes.box(15 / 16f, 4 / 16f, 2 / 16f, 18 / 16f, 1.0, 14 / 16f),
            Shapes.box(2 / 16f, 4 / 16f, -2 / 16f, 14 / 16f, 1.0, 1 / 16f),
            Shapes.box(2 / 16f, 4 / 16f, 15 / 16f, 14 / 16f, 1.0, 18 / 16f)
    ).optimize();

    public ConveyorSplitterBlock(Properties settings) {
        super(settings);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }


    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return Objects.requireNonNull(super.getStateForPlacement(ctx))
                .setValue(BlockStateProperties.HORIZONTAL_FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConveyorSplitterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level world, BlockState state,
                                                                              BlockEntityType<T> type) {
        return (world1, pos, state1, blockEntity) -> {
            if (blockEntity instanceof ConveyorSplitterBlockEntity splitter)
                splitter.tick(world1, pos, state1, splitter);
        };
    }

    @Override
    public void playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        if (!world.isClientSide) {
            var entity = world.getBlockEntity(pos);
            if (entity instanceof ConveyorSplitterBlockEntity splitter)
                splitter.dropContent(world, pos);
        }
        super.playerWillDestroy(world, pos, state, player);
    }
}
