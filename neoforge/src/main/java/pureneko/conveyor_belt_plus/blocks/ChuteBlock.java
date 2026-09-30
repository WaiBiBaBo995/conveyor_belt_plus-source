package pureneko.conveyor_belt_plus.blocks;

import com.mojang.serialization.MapCodec;
import net.neoforged.fml.ModList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import pureneko.conveyor_belt_plus.registry.BlockEntitiesContent;
import pureneko.conveyor_belt_plus.screen.ChuteScreenHandler;
import pureneko.conveyor_belt_plus.util.MathHelpers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ChuteBlock extends HorizontalDirectionalBlock implements EntityBlock {

    private static final Map<Direction, VoxelShape> SHAPES = new HashMap<>();

    private VoxelShape createShapeForDirection(Direction direction) {
        return Shapes.or(
          MathHelpers.rotateVoxelShape(Shapes.box(2 / 16f, 4 / 16f, 14 / 16f, 14 / 16f, 1f, 1f), direction, AttachFace.FLOOR),
          MathHelpers.rotateVoxelShape(Shapes.box(3 / 16f, 5 / 16f, 16 / 16f, 13 / 16f, 15 / 16f, 18 / 16f), direction, AttachFace.FLOOR)
        ).optimize();
    }

    private final int tier;
    private final ChuteKind kind;
    public ChuteKind getKind() { return kind; }

    public int getTier() { return tier; }

    public ChuteBlock(Properties settings) { this(settings, 1); }

    public ChuteBlock(Properties settings, int tier) { this(settings, tier, ChuteKind.ITEM); }

    public ChuteBlock(Properties settings, int tier, ChuteKind kind) {
        super(settings);
        this.kind = kind;
        this.tier = pureneko.conveyor_belt_plus.util.BeltTiers.normalize(tier);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof pureneko.conveyor_belt_plus.items.BeltItem)
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        openMenu(world, pos, player);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        openMenu(world, pos, player);
        return InteractionResult.SUCCESS;
    }

    private static void openMenu(Level world, BlockPos pos, Player player) {
        if (world.isClientSide || !(player instanceof ServerPlayer serverPlayer)) return;
        var candidate = world.getBlockEntity(pos, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (candidate.isEmpty()) return;

        var chute = candidate.get();
        serverPlayer.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable(chute.getBlockState().getBlock().getDescriptionId());
            }

            @Override
            public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int syncId, Inventory inventory,
                                                                  Player player) {
                return new ChuteScreenHandler(syncId, inventory, chute);
            }

        }, data -> {
                data.writeBlockPos(pos);
                data.writeEnum(chute.getKind());
                data.writeVarInt(chute.getFilterSlotCount(false));
                data.writeVarInt(chute.getFilterSlotCount(true));
        });
    }

    @Override
    protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        super.neighborChanged(state, world, pos, sourceBlock, sourcePos, notify);
        if (!world.isClientSide && world.getBlockEntity(pos) instanceof ChuteBlockEntity chute) chute.updateRedstoneSignal();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        var dir = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        return SHAPES.computeIfAbsent(dir, this::createShapeForDirection);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {

        var targetFacing = ctx.getClickedFace();
        if (targetFacing.getAxis().isVertical())
            targetFacing = ctx.getHorizontalDirection().getOpposite();

        return Objects.requireNonNull(super.getStateForPlacement(ctx)).setValue(BlockStateProperties.HORIZONTAL_FACING, targetFacing);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return null;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChuteBlockEntity(pos, state);
    }

    @Override
    protected boolean triggerEvent(BlockState state, Level world, BlockPos pos, int type, int data) {
        super.triggerEvent(state, world, pos, type, data);
        var blockEntity = world.getBlockEntity(pos);
        return blockEntity != null && blockEntity.triggerEvent(type, data);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return ((world1, pos, state1, blockEntity) -> {
            if (blockEntity instanceof ChuteBlockEntity chuteBlockEntity)
                chuteBlockEntity.tick(world1, pos, state1, chuteBlockEntity);
        });
    }

    @Override
    public BlockState playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {

        if (world.isClientSide) return super.playerWillDestroy(world, pos, state, player);

        var chuteEntity = world.getBlockEntity(pos, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (chuteEntity.isEmpty()) return super.playerWillDestroy(world, pos, state, player);

        chuteEntity.get().dropContent(world, pos);

        return super.playerWillDestroy(world, pos, state, player);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag options) {
        String summary = kind == ChuteKind.ITEM ? "chute" : kind == ChuteKind.FLUID ? "fluid_chute" : "universal_chute";
        tooltip.add(Component.translatable("tooltip.conveyor_belt_plus." + summary + ".summary").withStyle(ChatFormatting.GRAY));
        if (kind.supports(false)) tooltip.add(Component.translatable("block.conveyor_belt_plus.chute.capacity",
                pureneko.conveyor_belt_plus.config.ConveyorConfig.chuteStacks(tier),
                pureneko.conveyor_belt_plus.config.ConveyorConfig.chuteFilters(tier)).withStyle(ChatFormatting.AQUA));
        if (kind.supports(true)) tooltip.add(Component.translatable("tooltip.conveyor_belt_plus.fluid_chute.capacity",
                pureneko.conveyor_belt_plus.config.ConveyorConfig.fluidAmount(tier),
                pureneko.conveyor_belt_plus.config.ConveyorConfig.fluidFilters(tier)).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.conveyor_belt_plus.chute.open").withStyle(ChatFormatting.GRAY));
        if (Screen.hasControlDown()) {
            if (kind.supports(false)) tooltip.add(Component.translatable("tooltip.conveyor_belt_plus.chute.filters").withStyle(ChatFormatting.GRAY));
            if (kind.supports(true)) {
                tooltip.add(Component.translatable("tooltip.conveyor_belt_plus.fluid_chute.filters").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("tooltip.conveyor_belt_plus.fluid_chute.controls").withStyle(ChatFormatting.GRAY));
            }
            if (tier > 1) tooltip.add(Component.translatable("tooltip.conveyor_belt_plus.chute.upgrade").withStyle(ChatFormatting.YELLOW));
            if (kind.supports(false) && ModList.get().isLoaded("ftbfiltersystem"))
                tooltip.add(Component.translatable("block.conveyor_belt_plus.chute.tooltip.ftbfilters").withStyle(ChatFormatting.GRAY));
        }
        super.appendHoverText(stack, context, tooltip, options);
    }
}
