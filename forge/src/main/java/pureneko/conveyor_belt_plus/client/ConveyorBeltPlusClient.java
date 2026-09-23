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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.*;

public final class ConveyorBeltPlusClient {
    public static void initialize() {
        var modBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(
                net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory((client, parent) ->
                        new pureneko.conveyor_belt_plus.client.screens.ConveyorConfigScreen(parent)));
        modBus.addListener(ConveyorBeltPlusClient::registerScreens);
        modBus.addListener(ConveyorBeltPlusClient::registerRenderers);
        MinecraftForge.EVENT_BUS.addListener(ConveyorBeltPlusClient::tick);
        MinecraftForge.EVENT_BUS.addListener(ConveyorBeltPlusClient::outline);
        MinecraftForge.EVENT_BUS.addListener(BeltPickupTarget::beginFrame);
        MinecraftForge.EVENT_BUS.addListener(BeltPickupTarget::interact);
        if (net.minecraftforge.fml.ModList.get().isLoaded("rtsbuilding")) {
            MinecraftForge.EVENT_BUS.addListener(pureneko.conveyor_belt_plus.compat.rts.RtsClient::render);
            MinecraftForge.EVENT_BUS.addListener(pureneko.conveyor_belt_plus.compat.rts.RtsClient::click);
        }
    }
    private static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.START) return;
        var client = MinecraftClient.getInstance();
        BeltRenderClock.advance(client.world, client.isPaused());
        if (net.minecraftforge.fml.ModList.get().isLoaded("rtsbuilding"))
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
    private static void registerScreens(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> net.minecraft.client.gui.screen.ingame.HandledScreens.register(ScreenContent.CHUTE.get(), ChuteScreen::new));
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
