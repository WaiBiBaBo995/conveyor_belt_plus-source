package pureneko.conveyor_belt_plus.registry;

import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.List;

public class ComponentContent {

    public static final DeferredRegister<ComponentType<?>> COMPONENTS = DeferredRegister.create(RegistryKeys.DATA_COMPONENT_TYPE, ConveyorBeltPlus.MOD_ID);

    public static final Supplier<ComponentType<BlockPos>> BELT_START = COMPONENTS.register("belt_start",
      () -> ComponentType.<BlockPos>builder().codec(BlockPos.CODEC).packetCodec(BlockPos.PACKET_CODEC).build());
    public static final Supplier<ComponentType<Direction>> BELT_DIR = COMPONENTS.register("belt_start_dir",
      () -> ComponentType.<Direction>builder().codec(Direction.CODEC).packetCodec(Direction.PACKET_CODEC).build());

    public static final Supplier<ComponentType<List<BlockPos>>> MIDPOINTS = COMPONENTS.register("belt_midpoints",
      () -> ComponentType.<List<BlockPos>>builder().codec(BlockPos.CODEC.listOf()).packetCodec(BlockPos.PACKET_CODEC.collect(PacketCodecs.toList())).build());

}
