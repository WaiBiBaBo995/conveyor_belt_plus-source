package pureneko.conveyor_belt_plus.client;

import pureneko.conveyor_belt_plus.registry.BlockContent;
import pureneko.conveyor_belt_plus.registry.BlockEntitiesContent;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.registry.ScreenContent;
import pureneko.conveyor_belt_plus.client.renderers.*;
import pureneko.conveyor_belt_plus.client.screens.ChuteScreen;
import pureneko.conveyor_belt_plus.util.BeltRenderClock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;

@Mod(value = ConveyorBeltPlus.MOD_ID, dist = Dist.CLIENT)
public final class ConveyorBeltPlusClient {
    public ConveyorBeltPlusClient(IEventBus modBus, net.neoforged.fml.ModContainer container) {
        container.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                net.neoforged.neoforge.client.gui.ConfigurationScreen::new);
        modBus.addListener(ConveyorBeltPlusClient::registerScreens);
        modBus.addListener(ConveyorBeltPlusClient::registerRenderers);
        NeoForge.EVENT_BUS.addListener(ConveyorBeltPlusClient::tick);
        NeoForge.EVENT_BUS.addListener(ConveyorBeltPlusClient::outline);
        NeoForge.EVENT_BUS.addListener(BeltPickupTarget::beginFrame);
        NeoForge.EVENT_BUS.addListener(BeltPickupTarget::interact);
        if (net.neoforged.fml.ModList.get().isLoaded("rtsbuilding")) {
            NeoForge.EVENT_BUS.addListener(pureneko.conveyor_belt_plus.compat.rts.RtsClient::render);
            NeoForge.EVENT_BUS.addListener(pureneko.conveyor_belt_plus.compat.rts.RtsClient::click);
        }
    }
    private static void tick(ClientTickEvent.Pre event) {
        var client = MinecraftClient.getInstance();
        BeltRenderClock.advance(client.world, client.isPaused());
        if (net.neoforged.fml.ModList.get().isLoaded("rtsbuilding"))
            pureneko.conveyor_belt_plus.compat.rts.RtsClient.tick();
        if (client.world == null) {
            BeltPickupTarget.clear();
            ChuteBeltRenderer.clearLightingCache();
        }
    }
    private static void outline(RenderHighlightEvent.Block event) {
        if (MinecraftClient.getInstance().currentScreen != null) return;
        BeltOutlineRenderer.renderPlannedBelt(MinecraftClient.getInstance().world, event.getCamera(),
                event.getPoseStack(), event.getMultiBufferSource());
    }
    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ScreenContent.CHUTE.get(), ChuteScreen::new);
    }
    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BlockEntitiesContent.CHUTE_BLOCK.get(), ctx -> new ChuteBeltRenderer());
        event.registerBlockEntityRenderer(BlockEntitiesContent.SPLITTER.get(), ctx -> new SplitterBeltRenderer());
        RenderLayers.setRenderLayer(BlockContent.CHUTE_BLOCK.get(), RenderLayer.getTranslucent());
        RenderLayers.setRenderLayer(BlockContent.ADVANCED_CHUTE.get(), RenderLayer.getTranslucent());
        RenderLayers.setRenderLayer(BlockContent.ULTIMATE_CHUTE.get(), RenderLayer.getTranslucent());
        RenderLayers.setRenderLayer(BlockContent.SPLITTER.get(), RenderLayer.getTranslucent());
    }
}
