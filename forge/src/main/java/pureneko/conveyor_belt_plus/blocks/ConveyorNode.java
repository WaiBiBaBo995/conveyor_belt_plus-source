package pureneko.conveyor_belt_plus.blocks;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.List;

/** Common connection contract for chutes and multi-port logistics blocks. */
public interface ConveyorNode {

    /** Only requested on a click/update, not every frame or tick. */
    default BeltPickup.Access pickupAccess(Direction port) { return null; }

    boolean hasInputPort(Direction port);

    boolean hasOutputPort(Direction port);

    boolean isPortUsed(Direction port);

    /** Zero means that this face owns no outgoing belt. */
    default int outgoingBeltTier(Direction port) { return 0; }

    /** Changes only the route tier; endpoints, packets and progress stay intact. */
    default boolean upgradeOutgoingBelt(Direction port, int tier) { return false; }

    void connectIncoming(Direction port, BlockPos source);

    boolean connectOutgoing(Direction port, BlockPos target, Direction targetPort,
                            List<BlockPos> supports, int beltTier);

    /** Accepts one complete stack and owns it when returning true. */
    boolean acceptFromBelt(ItemStack stack, Direction port);

    /** Admits as much of the stack as fits and shrinks the remainder; defaults to all-or-nothing. */
    default boolean acceptPartialFromBelt(ItemStack stack, Direction port) {
        return acceptFromBelt(stack, port);
    }

    void markTargeted(BlockPos source);

    /** Clears a route owned by this node when its destination is removed. */
    default void disconnectOutgoingTo(BlockPos target) {
    }

    /** Clears the input marker owned by this node when its source is removed. */
    default void disconnectIncomingFrom(BlockPos source) {
    }
}
