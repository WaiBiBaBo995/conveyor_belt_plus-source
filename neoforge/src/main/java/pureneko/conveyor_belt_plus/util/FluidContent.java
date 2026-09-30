package pureneko.conveyor_belt_plus.util;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.fluids.FluidStack;

/** Immutable component value. Mutable NeoForge FluidStacks must never be stored directly in item components. */
public final class FluidContent {
    public static final FluidContent EMPTY = new FluidContent(FluidStack.EMPTY);
    public static final Codec<FluidContent> CODEC = FluidStack.CODEC.xmap(FluidContent::new, FluidContent::copy);
    public static final StreamCodec<RegistryFriendlyByteBuf, FluidContent> PACKET_CODEC =
            FluidStack.STREAM_CODEC.map(FluidContent::new, FluidContent::copy);
    private final FluidStack value;
    public FluidContent(FluidStack value) { this.value = value.copy(); }
    public FluidStack copy() { return value.copy(); }
    @Override public boolean equals(Object other) {
        return other instanceof FluidContent content && FluidStack.matches(value, content.value);
    }
    @Override public int hashCode() { return 31 * FluidStack.hashFluidAndComponents(value) + value.getAmount(); }
}
