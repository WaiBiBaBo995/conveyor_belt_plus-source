package pureneko.conveyor_belt_plus.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import pureneko.conveyor_belt_plus.util.FluidPackets;

/** Uses the parcel's saved contents for GUI, hand, frame and dropped-item rendering. */
public final class FluidPacketItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() { return Instance.RENDERER; }
    };
    private static final class Instance {
        private static final FluidPacketItemRenderer RENDERER = new FluidPacketItemRenderer();
    }
    private FluidPacketItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }
    @Override public void renderByItem(ItemStack stack, ItemDisplayContext mode, PoseStack matrices,
                                 MultiBufferSource consumers, int light, int overlay) {
        matrices.pushPose();
        // The item pipeline already applies the JSON display transform and translates by -0.5.
        matrices.translate(.5, .5, .5);
        FluidPacketRenderer.renderContents(FluidPackets.get(stack), Minecraft.getInstance().level,
                matrices, consumers, light, overlay);
        matrices.popPose();
    }
}
