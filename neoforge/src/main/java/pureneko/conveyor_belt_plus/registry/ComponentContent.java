package pureneko.conveyor_belt_plus.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;
import java.util.List;

public class ComponentContent {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ConveyorBeltPlus.MOD_ID);

    public static final Supplier<DataComponentType<pureneko.conveyor_belt_plus.util.FluidContent>> FLUID_CONTENT = COMPONENTS.register("fluid_content",
      () -> DataComponentType.<pureneko.conveyor_belt_plus.util.FluidContent>builder().persistent(pureneko.conveyor_belt_plus.util.FluidContent.CODEC)
        .networkSynchronized(pureneko.conveyor_belt_plus.util.FluidContent.PACKET_CODEC).build());

    public static final Supplier<DataComponentType<BlockPos>> BELT_START = COMPONENTS.register("belt_start",
      () -> DataComponentType.<BlockPos>builder().persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC).build());
    public static final Supplier<DataComponentType<Direction>> BELT_DIR = COMPONENTS.register("belt_start_dir",
      () -> DataComponentType.<Direction>builder().persistent(Direction.CODEC).networkSynchronized(Direction.STREAM_CODEC).build());

    public static final Supplier<DataComponentType<List<BlockPos>>> MIDPOINTS = COMPONENTS.register("belt_midpoints",
      () -> DataComponentType.<List<BlockPos>>builder().persistent(BlockPos.CODEC.listOf()).networkSynchronized(BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list())).build());

}
