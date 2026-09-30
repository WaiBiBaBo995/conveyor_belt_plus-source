package pureneko.conveyor_belt_plus.registry;

import net.neoforged.bus.api.IEventBus;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pureneko.conveyor_belt_plus.config.ConveyorConfig;
import pureneko.conveyor_belt_plus.neoforge.LegacyRegistryAliases;
import pureneko.conveyor_belt_plus.network.FilterNetworking;

@Mod(ConveyorBeltPlus.MOD_ID)
public final class ConveyorBeltPlus {
    public static final String MOD_ID = "conveyor_belt_plus";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public ConveyorBeltPlus(IEventBus modBus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, ConveyorConfig.SPEC, ConveyorConfig.FILE_NAME);
        BlockContent.BLOCKS.register(modBus);
        ItemContent.ITEMS.register(modBus);
        BlockEntitiesContent.TYPES.register(modBus);
        ComponentContent.COMPONENTS.register(modBus);
        ItemGroupContent.GROUPS.register(modBus);
        ScreenContent.MENUS.register(modBus);
        modBus.addListener(EventPriority.LOWEST, LegacyRegistryAliases::register);
        modBus.addListener(FilterNetworking::register);
        modBus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) ->
                event.registerItem(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM,
                        (stack, context) -> pureneko.conveyor_belt_plus.neoforge.ConveyorFluidApi.packetHandler(stack), ItemContent.FLUID_PACKET.get()));
        modBus.addListener(pureneko.conveyor_belt_plus.network.PickupNetworking::register);
        modBus.addListener(pureneko.conveyor_belt_plus.network.InsertionNetworking::register);
        modBus.addListener(pureneko.conveyor_belt_plus.network.RtsNetworking::register);
    }

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MOD_ID, path); }
}
