package pureneko.conveyor_belt_plus.util;

import net.minecraft.core.BlockPos;

/** One quad of a belt mesh; see {@link BeltVertex}. */
public record BeltQuad(BeltVertex a, BeltVertex b, BeltVertex c, BeltVertex d, BlockPos worldPos) {
}
