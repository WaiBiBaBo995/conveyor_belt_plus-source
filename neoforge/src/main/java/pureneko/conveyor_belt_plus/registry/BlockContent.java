package pureneko.conveyor_belt_plus.registry;

import pureneko.conveyor_belt_plus.blocks.ChuteBlock;
import pureneko.conveyor_belt_plus.blocks.ConveyorSupportBlock;
import pureneko.conveyor_belt_plus.blocks.ConveyorSplitterBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;

public class BlockContent {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(RegistryKeys.BLOCK, ConveyorBeltPlus.MOD_ID);

    public static final Supplier<Block> CHUTE_BLOCK = BLOCKS.register("chute", () -> new ChuteBlock(AbstractBlock.Settings.copy(Blocks.GLASS).sounds(BlockSoundGroup.DRIPSTONE_BLOCK).nonOpaque()));
    public static final Supplier<Block> ADVANCED_CHUTE = BLOCKS.register("advanced_chute", () -> new ChuteBlock(AbstractBlock.Settings.copy(Blocks.GLASS).sounds(BlockSoundGroup.DRIPSTONE_BLOCK).nonOpaque(), 2));
    public static final Supplier<Block> ULTIMATE_CHUTE = BLOCKS.register("ultimate_chute", () -> new ChuteBlock(AbstractBlock.Settings.copy(Blocks.GLASS).sounds(BlockSoundGroup.DRIPSTONE_BLOCK).nonOpaque(), 3));
    public static final Supplier<Block> CONVEYOR_SUPPORT_BLOCK = BLOCKS.register("conveyor_support", () -> new ConveyorSupportBlock(AbstractBlock.Settings.copy(Blocks.GLASS).sounds(BlockSoundGroup.DRIPSTONE_BLOCK).nonOpaque()));
    public static final Supplier<Block> SPLITTER = BLOCKS.register("splitter", () -> new ConveyorSplitterBlock(
            AbstractBlock.Settings.create().strength(1.0f, 6.0f).sounds(BlockSoundGroup.STONE).nonOpaque()));

}
