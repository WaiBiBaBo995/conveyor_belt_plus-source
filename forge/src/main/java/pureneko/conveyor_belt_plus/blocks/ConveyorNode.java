package pureneko.conveyor_belt_plus.blocks;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

/** Common connection contract for chutes and multi-port logistics blocks. */
public interface ConveyorNode {

    /** Only requested on a click/update, not every frame or tick. */
    default BeltPickup.Access pickupAccess(Direction port) { return null; }

    /** Consume the offered stack only if a packet can be inserted at this route position. */
    default boolean insertOnBelt(Direction port, float progress, ItemStack stack) { return false; }

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
