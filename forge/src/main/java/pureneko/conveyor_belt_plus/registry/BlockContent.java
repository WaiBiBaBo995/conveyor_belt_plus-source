package pureneko.conveyor_belt_plus.registry;

import pureneko.conveyor_belt_plus.blocks.ChuteBlock;
import pureneko.conveyor_belt_plus.blocks.ConveyorSupportBlock;
import pureneko.conveyor_belt_plus.blocks.ConveyorSplitterBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import java.util.function.Supplier;

public class BlockContent {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, ConveyorBeltPlus.MOD_ID);

    public static final Supplier<Block> CHUTE_BLOCK = BLOCKS.register("item_chute", () -> new ChuteBlock(BlockBehaviour.Properties.copy(Blocks.GLASS).sound(SoundType.DRIPSTONE_BLOCK).noOcclusion()));
    public static final Supplier<Block> ADVANCED_CHUTE = BLOCKS.register("advanced_item_chute", () -> new ChuteBlock(BlockBehaviour.Properties.copy(Blocks.GLASS).sound(SoundType.DRIPSTONE_BLOCK).noOcclusion(), 2));
    public static final Supplier<Block> ULTIMATE_CHUTE = BLOCKS.register("ultimate_item_chute", () -> new ChuteBlock(BlockBehaviour.Properties.copy(Blocks.GLASS).sound(SoundType.DRIPSTONE_BLOCK).noOcclusion(), 3));
    public static final Supplier<Block> FLUID_CHUTE = BLOCKS.register("fluid_chute", () -> new ChuteBlock(BlockBehaviour.Properties.copy(Blocks.GLASS).sound(SoundType.DRIPSTONE_BLOCK).noOcclusion(), 1, pureneko.conveyor_belt_plus.blocks.ChuteKind.FLUID));
    public static final Supplier<Block> ADVANCED_FLUID_CHUTE = BLOCKS.register("advanced_fluid_chute", () -> new ChuteBlock(BlockBehaviour.Properties.copy(Blocks.GLASS).sound(SoundType.DRIPSTONE_BLOCK).noOcclusion(), 2, pureneko.conveyor_belt_plus.blocks.ChuteKind.FLUID));
    public static final Supplier<Block> ULTIMATE_FLUID_CHUTE = BLOCKS.register("ultimate_fluid_chute", () -> new ChuteBlock(BlockBehaviour.Properties.copy(Blocks.GLASS).sound(SoundType.DRIPSTONE_BLOCK).noOcclusion(), 3, pureneko.conveyor_belt_plus.blocks.ChuteKind.FLUID));
    public static final Supplier<Block> UNIVERSAL_CHUTE = BLOCKS.register("universal_chute", () -> new ChuteBlock(BlockBehaviour.Properties.copy(Blocks.GLASS).sound(SoundType.DRIPSTONE_BLOCK).noOcclusion(), 1, pureneko.conveyor_belt_plus.blocks.ChuteKind.UNIVERSAL));
    public static final Supplier<Block> ADVANCED_UNIVERSAL_CHUTE = BLOCKS.register("advanced_universal_chute", () -> new ChuteBlock(BlockBehaviour.Properties.copy(Blocks.GLASS).sound(SoundType.DRIPSTONE_BLOCK).noOcclusion(), 2, pureneko.conveyor_belt_plus.blocks.ChuteKind.UNIVERSAL));
    public static final Supplier<Block> ULTIMATE_UNIVERSAL_CHUTE = BLOCKS.register("ultimate_universal_chute", () -> new ChuteBlock(BlockBehaviour.Properties.copy(Blocks.GLASS).sound(SoundType.DRIPSTONE_BLOCK).noOcclusion(), 3, pureneko.conveyor_belt_plus.blocks.ChuteKind.UNIVERSAL));
    public static final Supplier<Block> CONVEYOR_SUPPORT_BLOCK = BLOCKS.register("conveyor_support", () -> new ConveyorSupportBlock(BlockBehaviour.Properties.copy(Blocks.GLASS).sound(SoundType.DRIPSTONE_BLOCK).noOcclusion()));
    public static final Supplier<Block> SPLITTER = BLOCKS.register("splitter", () -> new ConveyorSplitterBlock(
            BlockBehaviour.Properties.of().strength(1.0f, 6.0f).sound(SoundType.STONE).noOcclusion()));

}
