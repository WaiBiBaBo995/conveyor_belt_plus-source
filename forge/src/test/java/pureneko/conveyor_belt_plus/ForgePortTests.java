package pureneko.conveyor_belt_plus;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import pureneko.conveyor_belt_plus.registry.*;

@GameTestHolder(ConveyorBeltPlus.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForgePortTests {
    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void recipesLootAndDraftNbt(GameTestHelper context) {
        var world = context.getLevel();
        for (String recipe : new String[]{"belt_item", "belt_item_alt", "advanced_belt", "ultimate_belt",
                "chute_item", "advanced_chute", "ultimate_chute", "support_item", "splitter"}) {
            context.assertTrue(world.getRecipeManager().byKey(ConveyorBeltPlus.id(recipe)).isPresent(), "recipe loaded: " + recipe);
        }
        var pos = context.absolutePos(new BlockPos(2, 2, 2));
        for (Block block : new Block[]{BlockContent.CHUTE_BLOCK.get(), BlockContent.ADVANCED_CHUTE.get(),
                BlockContent.ULTIMATE_CHUTE.get(), BlockContent.CONVEYOR_SUPPORT_BLOCK.get(), BlockContent.SPLITTER.get()}) {
            world.setBlockAndUpdate(pos, block.defaultBlockState());
            var drops = Block.getDrops(block.defaultBlockState(), world, pos, world.getBlockEntity(pos));
            context.assertTrue(drops.size() == 1 && drops.get(0).is(block.asItem()) && drops.get(0).getCount() == 1,
                    "correct block drop: " + block.getDescriptionId());
        }
        var stack = new ItemStack(ItemContent.BELT.get());
        stack.getOrCreateTag().putString("foreign_data", "preserved");
        ComponentContent.BELT_START.set(stack, pos);
        ComponentContent.BELT_DIR.set(stack, Direction.EAST);
        ComponentContent.MIDPOINTS.set(stack, java.util.List.of(pos.east(2), pos.east(4)));
        var restored = ItemStack.of(stack.save(new CompoundTag()));
        context.assertTrue(ComponentContent.BELT_START.get(restored).equals(pos)
                && ComponentContent.BELT_DIR.get(restored) == Direction.EAST
                && ComponentContent.MIDPOINTS.get(restored).equals(java.util.List.of(pos.east(2), pos.east(4))), "draft survives vanilla item save/load");
        pureneko.conveyor_belt_plus.compat.rts.RtsBeltDrafts.clearSelection(restored);
        context.assertTrue(!ComponentContent.BELT_START.contains(restored)
                && restored.getTag().getString("foreign_data").equals("preserved"), "clearing a draft preserves other item data");
        context.succeed();
    }
}
