package pureneko.conveyor_belt_plus.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.util.CommonColors;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity.BeltData;
import pureneko.conveyor_belt_plus.blocks.ConveyorSplitterBlockEntity;
import pureneko.conveyor_belt_plus.blocks.ConveyorSplitterBlockEntity.Route;
import pureneko.conveyor_belt_plus.util.BeltRenderClock;
import pureneko.conveyor_belt_plus.util.BeltVisualState;
import pureneko.conveyor_belt_plus.util.BeltMaterials;

/** All splitter routes share the same geometry/material/animation pipeline as ordinary belts. */
public class SplitterBeltRenderer implements BlockEntityRenderer<ConveyorSplitterBlockEntity> {
    @Override
    public void render(ConveyorSplitterBlockEntity entity, float tickDelta, PoseStack matrices,
                       MultiBufferSource consumers, int light, int overlay) {
        if (entity == null || entity.getLevel() == null) return;
        double clientTime = BeltRenderClock.renderTime(entity.getLevel(), tickDelta);
        for (var route : entity.getRoutes()) {
            var data = route.getBeltData();
            if (data == null) continue;
            var state = route.getVisualState();
            double presentation = state.presentationTick(clientTime);
            int frame = BeltMaterials.frame(state.renderedDistance(presentation));
            if (route.renderedModel == null)
                route.renderedModel = ChuteBeltRenderer.createSplineModel(data, entity.getBlockPos());
            BeltMeshRenderer.draw(entity.getLevel(), route.renderedModel, matrices, consumers,
                    route.getBeltTier(), frame, overlay, CommonColors.WHITE, false);
            ChuteBeltRenderer.renderItems(entity, route.getPort(), route.getMovingItems(), matrices, consumers, overlay,
                    data, 64 * 64, presentation);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(ConveyorSplitterBlockEntity entity) { return true; }

    @Override
    public int getViewDistance() { return 96; }

}
