package pureneko.conveyor_belt_plus.blocks;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public final class ConveyorNodeUtil {

    private ConveyorNodeUtil() {
    }

    public static @Nullable ConveyorNode get(World world, BlockPos pos) {
        BlockEntity entity = world.getBlockEntity(pos);
        return entity instanceof ConveyorNode node ? node : null;
    }

    public static boolean accept(World world, BlockPos pos, Direction port, ItemStack stack) {
        var node = get(world, pos);
        return node != null && node.acceptPartialFromBelt(stack, port);
    }
}
