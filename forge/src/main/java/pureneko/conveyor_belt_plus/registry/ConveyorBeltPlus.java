package pureneko.conveyor_belt_plus.registry;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pureneko.conveyor_belt_plus.config.ConveyorConfig;
import pureneko.conveyor_belt_plus.forge.LegacyRegistryAliases;
import pureneko.conveyor_belt_plus.network.FilterNetworking;

@Mod(ConveyorBeltPlus.MOD_ID)
public final class ConveyorBeltPlus {
    public static final String MOD_ID = "conveyor_belt_plus";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public ConveyorBeltPlus() {
        var modBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER, ConveyorConfig.SPEC, ConveyorConfig.FILE_NAME);
        BlockContent.BLOCKS.register(modBus);
        ItemContent.ITEMS.register(modBus);
        BlockEntitiesContent.TYPES.register(modBus);
        ItemGroupContent.GROUPS.register(modBus);
        ScreenContent.MENUS.register(modBus);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(LegacyRegistryAliases::register);
        FilterNetworking.register();
        pureneko.conveyor_belt_plus.network.PickupNetworking.register();
        pureneko.conveyor_belt_plus.network.RtsNetworking.register();
        pureneko.conveyor_belt_plus.network.InsertionNetworking.register();
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> pureneko.conveyor_belt_plus.client.ConveyorBeltPlusClient::initialize);
    }

    public static ResourceLocation id(String path) { return new ResourceLocation(MOD_ID, path); }
}
