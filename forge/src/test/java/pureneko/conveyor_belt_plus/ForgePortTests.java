package pureneko.conveyor_belt_plus;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import pureneko.conveyor_belt_plus.registry.*;

@GameTestHolder(ConveyorBeltPlus.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForgePortTests {
    @GameTest(templateName = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void recipesLootAndDraftNbt(TestContext context) {
        var world = context.getWorld();
        for (String recipe : new String[]{"belt_item", "belt_item_alt", "advanced_belt", "ultimate_belt",
                "chute_item", "advanced_chute", "ultimate_chute", "support_item", "splitter"}) {
            context.assertTrue(world.getRecipeManager().get(ConveyorBeltPlus.id(recipe)).isPresent(), "recipe loaded: " + recipe);
        }
        var pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
        for (Block block : new Block[]{BlockContent.CHUTE_BLOCK.get(), BlockContent.ADVANCED_CHUTE.get(),
                BlockContent.ULTIMATE_CHUTE.get(), BlockContent.CONVEYOR_SUPPORT_BLOCK.get(), BlockContent.SPLITTER.get()}) {
            world.setBlockState(pos, block.getDefaultState());
            var drops = Block.getDroppedStacks(block.getDefaultState(), world, pos, world.getBlockEntity(pos));
            context.assertTrue(drops.size() == 1 && drops.get(0).isOf(block.asItem()) && drops.get(0).getCount() == 1,
                    "correct block drop: " + block.getTranslationKey());
        }
        var stack = new ItemStack(ItemContent.BELT.get());
        stack.getOrCreateNbt().putString("foreign_data", "preserved");
        ComponentContent.BELT_START.set(stack, pos);
        ComponentContent.BELT_DIR.set(stack, Direction.EAST);
        ComponentContent.MIDPOINTS.set(stack, java.util.List.of(pos.east(2), pos.east(4)));
        var restored = ItemStack.fromNbt(stack.writeNbt(new NbtCompound()));
        context.assertTrue(ComponentContent.BELT_START.get(restored).equals(pos)
                && ComponentContent.BELT_DIR.get(restored) == Direction.EAST
                && ComponentContent.MIDPOINTS.get(restored).equals(java.util.List.of(pos.east(2), pos.east(4))), "draft survives vanilla item save/load");
        pureneko.conveyor_belt_plus.compat.rts.RtsBeltDrafts.clearSelection(restored);
        context.assertTrue(!ComponentContent.BELT_START.contains(restored)
                && restored.getNbt().getString("foreign_data").equals("preserved"), "clearing a draft preserves other item data");
        context.complete();
    }
}
