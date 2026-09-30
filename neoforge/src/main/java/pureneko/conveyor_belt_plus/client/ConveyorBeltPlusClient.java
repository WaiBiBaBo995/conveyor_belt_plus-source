package pureneko.conveyor_belt_plus.client;

import pureneko.conveyor_belt_plus.registry.BlockContent;
import pureneko.conveyor_belt_plus.registry.BlockEntitiesContent;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.registry.ScreenContent;
import pureneko.conveyor_belt_plus.client.renderers.*;
import pureneko.conveyor_belt_plus.client.screens.ChuteScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Block;
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
        modBus.addListener((net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) ->
                event.registerItem(FluidPacketItemRenderer.EXTENSIONS,
                        pureneko.conveyor_belt_plus.registry.ItemContent.FLUID_PACKET.get()));
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
        var client = Minecraft.getInstance();
        BeltRenderClock.tick(client);
        if (net.neoforged.fml.ModList.get().isLoaded("rtsbuilding"))
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
    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ScreenContent.CHUTE.get(), ChuteScreen::new);
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
