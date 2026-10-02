package pureneko.conveyor_belt_plus.registry;

import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity;
import pureneko.conveyor_belt_plus.blocks.ConveyorSplitterBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public class BlockEntitiesContent {

    public static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ConveyorBeltPlus.MOD_ID);

    public static final Supplier<BlockEntityType<ChuteBlockEntity>> CHUTE_BLOCK = TYPES.register("chute", () -> BlockEntityType.Builder.of(ChuteBlockEntity::new, BlockContent.CHUTE_BLOCK.get(), BlockContent.ADVANCED_CHUTE.get(), BlockContent.ULTIMATE_CHUTE.get(),
            BlockContent.FLUID_CHUTE.get(), BlockContent.ADVANCED_FLUID_CHUTE.get(), BlockContent.ULTIMATE_FLUID_CHUTE.get(),
            BlockContent.UNIVERSAL_CHUTE.get(), BlockContent.ADVANCED_UNIVERSAL_CHUTE.get(), BlockContent.ULTIMATE_UNIVERSAL_CHUTE.get()).build(null));

    public static final Supplier<BlockEntityType<ConveyorSplitterBlockEntity>> SPLITTER = TYPES.register(
            "splitter",
            () -> BlockEntityType.Builder.of(
                    ConveyorSplitterBlockEntity::new,
                    BlockContent.SPLITTER.get()).build(null));

}
