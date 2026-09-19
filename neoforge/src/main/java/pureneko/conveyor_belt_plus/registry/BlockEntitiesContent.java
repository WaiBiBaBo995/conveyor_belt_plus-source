package pureneko.conveyor_belt_plus.registry;

import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity;
import pureneko.conveyor_belt_plus.blocks.ConveyorSplitterBlockEntity;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.RegistryKeys;

public class BlockEntitiesContent {

    public static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(RegistryKeys.BLOCK_ENTITY_TYPE, ConveyorBeltPlus.MOD_ID);

    public static final Supplier<BlockEntityType<ChuteBlockEntity>> CHUTE_BLOCK = TYPES.register("chute", () -> BlockEntityType.Builder.create(ChuteBlockEntity::new, BlockContent.CHUTE_BLOCK.get(), BlockContent.ADVANCED_CHUTE.get(), BlockContent.ULTIMATE_CHUTE.get()).build(null));

    public static final Supplier<BlockEntityType<ConveyorSplitterBlockEntity>> SPLITTER = TYPES.register(
            "splitter",
            () -> BlockEntityType.Builder.create(
                    ConveyorSplitterBlockEntity::new,
                    BlockContent.SPLITTER.get()).build(null));

}
