package pureneko.conveyor_belt_plus.client;

import pureneko.conveyor_belt_plus.registry.BlockContent;
import pureneko.conveyor_belt_plus.registry.BlockEntitiesContent;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.registry.ScreenContent;
import pureneko.conveyor_belt_plus.client.renderers.*;
import pureneko.conveyor_belt_plus.client.screens.ChuteScreen;
import pureneko.conveyor_belt_plus.util.BeltRenderClock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Block;
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
        var client = Minecraft.getInstance();
        BeltRenderClock.advance(client.level, client.isPaused());
        if (net.minecraftforge.fml.ModList.get().isLoaded("rtsbuilding"))
            pureneko.conveyor_belt_plus.compat.rts.RtsClient.tick();
        if (client.level == null) {
            BeltPickupTarget.clear();
            ChuteBeltRenderer.clearLightingCache();
        }
    }
    private static void outline(RenderHighlightEvent.Block event) {
        if (Minecraft.getInstance().screen != null) return;
        BeltOutlineRenderer.renderPlannedBelt(Minecraft.getInstance().level, event.getCamera(),
                event.getPoseStack(), event.getMultiBufferSource());
    }
    private static void registerScreens(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> net.minecraft.client.gui.screens.MenuScreens.register(ScreenContent.CHUTE.get(), ChuteScreen::new));
    }
    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BlockEntitiesContent.CHUTE_BLOCK.get(), ctx -> new ChuteBeltRenderer());
        event.registerBlockEntityRenderer(BlockEntitiesContent.SPLITTER.get(), ctx -> new SplitterBeltRenderer());
        ItemBlockRenderTypes.setRenderLayer(BlockContent.CHUTE_BLOCK.get(), RenderType.translucent());
        ItemBlockRenderTypes.setRenderLayer(BlockContent.ADVANCED_CHUTE.get(), RenderType.translucent());
        ItemBlockRenderTypes.setRenderLayer(BlockContent.ULTIMATE_CHUTE.get(), RenderType.translucent());
        for (var block : java.util.List.of(BlockContent.FLUID_CHUTE.get(), BlockContent.ADVANCED_FLUID_CHUTE.get(),
                BlockContent.ULTIMATE_FLUID_CHUTE.get(), BlockContent.UNIVERSAL_CHUTE.get(),
                BlockContent.ADVANCED_UNIVERSAL_CHUTE.get(), BlockContent.ULTIMATE_UNIVERSAL_CHUTE.get()))
            ItemBlockRenderTypes.setRenderLayer(block, RenderType.translucent());
        ItemBlockRenderTypes.setRenderLayer(BlockContent.SPLITTER.get(), RenderType.translucent());
    }
}
