package pureneko.conveyor_belt_plus.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public final class ConveyorNodeUtil {

    private ConveyorNodeUtil() {
    }

    public static @Nullable ConveyorNode get(Level world, BlockPos pos) {
        BlockEntity entity = world.getBlockEntity(pos);
        return entity instanceof ConveyorNode node ? node : null;
    }

    public static boolean accept(Level world, BlockPos pos, Direction port, ItemStack stack) {
        var node = get(world, pos);
        if (node instanceof ConveyorSplitterBlockEntity splitter) return splitter.acceptPartialFromBelt(stack, port);
        return node != null && node.acceptFromBelt(stack, port);
    }
}
