package pureneko.conveyor_belt_plus.client.renderers;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Colors;
import pureneko.conveyor_belt_plus.blocks.ConveyorSplitterBlockEntity;
import pureneko.conveyor_belt_plus.util.BeltRenderClock;
import pureneko.conveyor_belt_plus.util.BeltMaterials;

/** All splitter routes share the same geometry/material/animation pipeline as ordinary belts. */
public class SplitterBeltRenderer implements BlockEntityRenderer<ConveyorSplitterBlockEntity> {
    @Override
    public void render(ConveyorSplitterBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider consumers, int light, int overlay) {
        if (entity == null || entity.getWorld() == null) return;
        double clientTime = BeltRenderClock.renderTime(entity.getWorld(), tickDelta);
        for (var route : entity.getRoutes()) {
            var data = route.getBeltData();
            if (data == null) continue;
            var state = route.getVisualState();
            double presentation = state.presentationTick(clientTime);
            int frame = BeltMaterials.frame(state.renderedDistance(presentation));
            if (route.renderedModel == null)
                route.renderedModel = ChuteBeltRenderer.createSplineModel(data, entity.getPos());
            BeltMeshRenderer.draw(entity.getWorld(), route.renderedModel, matrices, consumers,
                    route.getBeltTier(), frame, overlay, Colors.WHITE, false);
            ChuteBeltRenderer.renderItems(entity, route.getPort(), route.getMovingItems(), matrices, consumers, overlay,
                    data, 64 * 64, presentation);
        }
    }

    @Override
    public boolean rendersOutsideBoundingBox(ConveyorSplitterBlockEntity entity) { return true; }

    @Override
    public int getRenderDistance() { return 96; }

}
