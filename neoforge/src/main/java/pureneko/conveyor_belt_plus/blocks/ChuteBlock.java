package pureneko.conveyor_belt_plus.blocks;

import com.mojang.serialization.MapCodec;
import net.neoforged.fml.ModList;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import pureneko.conveyor_belt_plus.registry.BlockEntitiesContent;
import pureneko.conveyor_belt_plus.screen.ChuteScreenHandler;
import pureneko.conveyor_belt_plus.util.DirectionalShapes;
import pureneko.conveyor_belt_plus.util.MathHelpers;

import java.util.List;
import java.util.Objects;

public class ChuteBlock extends HorizontalFacingBlock implements BlockEntityProvider {

    private static final DirectionalShapes SHAPES = new DirectionalShapes(ChuteBlock::createShape);

    private static VoxelShape createShape(Direction direction) {
        return VoxelShapes.union(
          MathHelpers.rotateVoxelShape(VoxelShapes.cuboid(2 / 16f, 4 / 16f, 14 / 16f, 14 / 16f, 1f, 1f), direction, BlockFace.FLOOR),
          MathHelpers.rotateVoxelShape(VoxelShapes.cuboid(3 / 16f, 5 / 16f, 16 / 16f, 13 / 16f, 15 / 16f, 18 / 16f), direction, BlockFace.FLOOR)
        ).simplify();
    }

    private final int tier;

    public int getTier() { return tier; }

    public ChuteBlock(Settings settings) { this(settings, 1); }

    public ChuteBlock(Settings settings, int tier) {
        super(settings);
        this.tier = pureneko.conveyor_belt_plus.util.BeltTiers.normalize(tier);
        setDefaultState(getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof pureneko.conveyor_belt_plus.items.BeltItem)
            return ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        openMenu(world, pos, player);
        return ItemActionResult.SUCCESS;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        openMenu(world, pos, player);
        return ActionResult.SUCCESS;
    }

    private static void openMenu(World world, BlockPos pos, PlayerEntity player) {
        if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer)) return;
        var candidate = world.getBlockEntity(pos, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (candidate.isEmpty()) return;

        var chute = candidate.get();
        serverPlayer.openMenu(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.translatable(chute.getCachedState().getBlock().getTranslationKey());
            }

            @Override
            public net.minecraft.screen.ScreenHandler createMenu(int syncId, PlayerInventory inventory,
                                                                  PlayerEntity player) {
                return new ChuteScreenHandler(syncId, inventory, chute);
            }

        }, data -> {
                data.writeBlockPos(pos);
                data.writeVarInt(chute.getChuteTier());
                data.writeVarInt(chute.getFilterSlotCount());
        });
    }

    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);
        if (!world.isClient && world.getBlockEntity(pos) instanceof ChuteBlockEntity chute) chute.updateRedstoneSignal();
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES.get(state.get(Properties.HORIZONTAL_FACING));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(Properties.HORIZONTAL_FACING);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {

        var targetFacing = ctx.getSide();
        if (targetFacing.getAxis().isVertical())
            targetFacing = ctx.getHorizontalPlayerFacing().getOpposite();

        return Objects.requireNonNull(super.getPlacementState(ctx)).with(Properties.HORIZONTAL_FACING, targetFacing);
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return null;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ChuteBlockEntity(pos, state);
    }

    @Override
    protected boolean onSyncedBlockEvent(BlockState state, World world, BlockPos pos, int type, int data) {
        super.onSyncedBlockEvent(state, world, pos, type, data);
        var blockEntity = world.getBlockEntity(pos);
        return blockEntity != null && blockEntity.onSyncedBlockEvent(type, data);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return ((world1, pos, state1, blockEntity) -> {
            if (blockEntity instanceof ChuteBlockEntity chuteBlockEntity)
                chuteBlockEntity.tick(world1, pos, state1, chuteBlockEntity);
        });
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {

        if (world.isClient) return super.onBreak(world, pos, state, player);

        var chuteEntity = world.getBlockEntity(pos, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (chuteEntity.isEmpty()) return super.onBreak(world, pos, state, player);

        chuteEntity.get().dropContent(world, pos);

        return super.onBreak(world, pos, state, player);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
        tooltip.add(Text.translatable("tooltip.conveyor_belt_plus.chute.summary").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("block.conveyor_belt_plus.chute.capacity",
                pureneko.conveyor_belt_plus.config.ConveyorConfig.chuteStacks(tier),
                pureneko.conveyor_belt_plus.config.ConveyorConfig.chuteFilters(tier)).formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("tooltip.conveyor_belt_plus.chute.open").formatted(Formatting.GRAY));
        if (Screen.hasControlDown()) {
            tooltip.add(Text.translatable("tooltip.conveyor_belt_plus.chute.filters").formatted(Formatting.GRAY));
            if (tier > 1) tooltip.add(Text.translatable("tooltip.conveyor_belt_plus.chute.upgrade").formatted(Formatting.YELLOW));
            if (ModList.get().isLoaded("ftbfiltersystem"))
                tooltip.add(Text.translatable("block.conveyor_belt_plus.chute.tooltip.ftbfilters").formatted(Formatting.GRAY));
        }
        super.appendTooltip(stack, context, tooltip, options);
    }
}
