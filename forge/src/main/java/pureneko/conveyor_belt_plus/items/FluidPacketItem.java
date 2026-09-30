package pureneko.conveyor_belt_plus.items;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import pureneko.conveyor_belt_plus.forge.ConveyorFluidApi;
import pureneko.conveyor_belt_plus.util.FluidPackets;

/** Not craftable: breaking a loaded belt recovers its fluid without manufacturing glass or buckets. */
public final class FluidPacketItem extends Item {
    public FluidPacketItem(Properties settings) { super(settings); }
    @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(pureneko.conveyor_belt_plus.client.renderers.FluidPacketItemRenderer.EXTENSIONS);
    }
    @Override public net.minecraftforge.common.capabilities.ICapabilityProvider initCapabilities(ItemStack stack, net.minecraft.nbt.CompoundTag nbt) {
        return new net.minecraftforge.common.capabilities.ICapabilityProvider() {
            private final net.minecraftforge.common.util.LazyOptional<net.minecraftforge.fluids.capability.IFluidHandlerItem> handler =
                    net.minecraftforge.common.util.LazyOptional.of(() -> ConveyorFluidApi.packetHandler(stack));
            @Override public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
                    net.minecraftforge.common.capabilities.Capability<T> capability, net.minecraft.core.Direction side) {
                return capability == net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER_ITEM
                        ? handler.cast() : net.minecraftforge.common.util.LazyOptional.empty();
            }
        };
    }
    @Override public Component getName(ItemStack stack) {
        var fluid = FluidPackets.get(stack);
        return fluid.isEmpty() ? super.getName(stack)
                : Component.translatable("item.conveyor_belt_plus.fluid_packet.contents", fluid.getDisplayName(), fluid.getAmount());
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        var world = context.getLevel();
        var fluid = FluidPackets.get(context.getItemInHand());
        if (fluid.isEmpty()) return InteractionResult.PASS;
        var target = ConveyorFluidApi.find(world, context.getClickedPos(), context.getClickedFace());
        if (target == null) return InteractionResult.PASS;
        if (world.isClientSide) return InteractionResult.SUCCESS;
        int filled = target.fill(fluid.copy(), IFluidHandler.FluidAction.EXECUTE);
        if (filled <= 0) return InteractionResult.PASS;
        fluid.shrink(Math.min(filled, fluid.getAmount()));
        FluidPackets.set(context.getItemInHand(), fluid);
        return InteractionResult.SUCCESS;
    }
}
