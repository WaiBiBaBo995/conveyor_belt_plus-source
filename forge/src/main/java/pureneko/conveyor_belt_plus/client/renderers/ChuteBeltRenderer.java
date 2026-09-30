package pureneko.conveyor_belt_plus.client.renderers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.CommonColors;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity.BeltData;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity.BeltItem;
import pureneko.conveyor_belt_plus.blocks.ConveyorNode;
import pureneko.conveyor_belt_plus.blocks.ConveyorNodeUtil;
import pureneko.conveyor_belt_plus.util.BeltMaterials;
import pureneko.conveyor_belt_plus.client.BeltPickupTarget;
import pureneko.conveyor_belt_plus.util.BeltQuad;
import pureneko.conveyor_belt_plus.util.BeltRenderClock;
import pureneko.conveyor_belt_plus.util.SplineUtil;
import pureneko.conveyor_belt_plus.util.BeltVertex;
import pureneko.conveyor_belt_plus.util.BeltVisualState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;

public class ChuteBeltRenderer implements BlockEntityRenderer<ChuteBlockEntity> {

    private static final Vec3 UNIT_X = new Vec3(1, 0, 0);

    private static final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap lightmapCache = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
    private static net.minecraft.world.level.Level lightmapWorld;
    private static long lightmapTick = Long.MIN_VALUE;
    static { lightmapCache.defaultReturnValue(-1); }

    public static void clearLightingCache() {
        lightmapCache.clear();
        lightmapWorld = null;
        lightmapTick = Long.MIN_VALUE;
    }

    /** Shared by belt faces and items; bounded lifetime and no boxed position keys. */
    public static int lightAt(net.minecraft.world.level.Level world, BlockPos pos) {
        if (lightmapWorld != world || lightmapTick != world.getGameTime()) {
            lightmapWorld = world;
            lightmapTick = world.getGameTime();
            lightmapCache.clear();
        }
        int light = lightmapCache.get(pos.asLong());
        if (light == -1) {
            light = LevelRenderer.getLightColor(world, pos);
            lightmapCache.put(pos.asLong(), light);
        }
        return light;
    }

    @Override
    public void render(ChuteBlockEntity entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {

        if (entity == null || entity.getLevel() == null) return;

        if (entity.getTarget() == null || entity.getTarget().distManhattan(entity.getBlockPos()) < 1) return;

        var targetNode = ConveyorNodeUtil.get(entity.getLevel(), entity.getTarget());
        if (targetNode == null) return;

        var beltData = entity.getBeltData();
        if (beltData == null) return;

        var itemRenderDistSq = 64 * 64;
        var beltRenderDistSq = 96 * 96;

        var state = entity.getVisualState();
        double presentationTick = state.presentationTick(BeltRenderClock.renderTime(entity.getLevel(), tickDelta));
        int frame = BeltMaterials.frame(state.renderedDistance(presentationTick));
        if (entity.renderedModel == null)
            entity.renderedModel = createSplineModel(beltData, entity.getBlockPos());
        BeltMeshRenderer.draw(entity.getLevel(), entity.renderedModel, matrices, vertexConsumers,
                entity.getBeltTier(), frame, overlay, CommonColors.WHITE, false);

        // render items
        renderItems(entity, entity.getOwnFacing(), entity.getMovingItems(), matrices, vertexConsumers, overlay, beltData, itemRenderDistSq, presentationTick);

        renderBeltFilter(entity, matrices, vertexConsumers, light, overlay, beltRenderDistSq);

    }

    public static BeltQuad[] createSplineModel(ChuteBlockEntity.BeltData beltData, BlockPos origin) {

        var result = new ArrayList<BeltQuad>();

        if (beltData == null) return null;

        var segmentSize = 0.75f;
        var segmentCount = (int) Math.ceil(beltData.totalLength() / segmentSize);
        var lineWidth = 0.33f;

        var conveyorStartDir = beltData.allPoints().get(0).getB();
        var conveyorEndDir = beltData.allPoints().get(beltData.allPoints().size() - 1).getB();

        var beginRight = conveyorStartDir.cross(new Vec3(0, 1, 0)).normalize();

        // local space
        var localStart = beltData.allPoints().get(0).getA().subtract(Vec3.atLowerCornerOf(origin));
        var lastRight = localStart.add(beginRight.scale(lineWidth));
        var lastLeft = localStart.add(beginRight.scale(-lineWidth));

        for (int i = 0; i < segmentCount; i++) {

            var last = i == segmentCount - 1;
            var progress = i / (float) segmentCount;
            var nextProgress = (i + 1) / (float) segmentCount;
            var worldPoint = SplineUtil.getPositionOnSpline(beltData, progress);
            var localPoint = worldPoint.subtract(origin.getCenter());
            var worldPointNext = SplineUtil.getPositionOnSpline(beltData, nextProgress);
            var localPointNext = worldPointNext.subtract(origin.getCenter());

            var worldPos = BlockPos.containing(worldPointNext.add(0.5f, 0, 0.5f));

            var direction = localPointNext.subtract(localPoint);
            var cross = direction.cross(new Vec3(0, 1, 0)).normalize();
            if (last)
                cross = conveyorEndDir.cross(new Vec3(0, 1, 0)).normalize();

            var nextRight = localPointNext.add(cross.scale(lineWidth)).add(0.5f, 0.5f, 0.5f);
            var nextLeft = localPointNext.add(cross.scale(-lineWidth)).add(0.5f, 0.5f, 0.5f);

            var dirA = lastLeft.subtract(lastRight).normalize();
            var dirB = nextLeft.subtract(nextRight).normalize();
            var curveStrength = 1 - Math.abs(dirA.dot(dirB));

            // split into 2 segments for strong curved segments
            if (curveStrength > 0.025) {
                var midProgress = (i + 0.5f) / (float) segmentCount;
                var worldPointMid = SplineUtil.getPositionOnSpline(beltData, midProgress);
                var localPointMid = worldPointMid.subtract(origin.getCenter());

                var directionMid = localPointMid.subtract(localPoint);
                var crossMid = directionMid.cross(new Vec3(0, 1, 0)).normalize();

                var midRight = localPointMid.add(crossMid.scale(lineWidth)).add(0.5f, 0.5f, 0.5f);
                var midLeft = localPointMid.add(crossMid.scale(-lineWidth)).add(0.5f, 0.5f, 0.5f);

                direction = localPointNext.subtract(localPointMid);
                cross = direction.cross(new Vec3(0, 1, 0)).normalize();
                if (last)
                    cross = conveyorEndDir.cross(new Vec3(0, 1, 0)).normalize();

                nextRight = localPointNext.add(cross.scale(lineWidth)).add(0.5f, 0.5f, 0.5f);
                nextLeft = localPointNext.add(cross.scale(-lineWidth)).add(0.5f, 0.5f, 0.5f);

                addSegmentVertices(midRight, lastRight, midLeft, lastLeft, worldPos, result, 0, 0.5f);
                addSegmentVertices(nextRight, midRight, nextLeft, midLeft, worldPos, result, 0.5f, 1f);
            } else {
                addSegmentVertices(nextRight, lastRight, nextLeft, lastLeft, worldPos, result, 0, 1);
            }
            lastRight = nextRight;
            lastLeft = nextLeft;

            // draw a quad from lastLeft -> nextLeft -> nextRight -> lastRight
        }

        return result.toArray(BeltQuad[]::new);
    }

    private static void addSegmentVertices(Vec3 nextRight, Vec3 lastRight, Vec3 nextLeft, Vec3 lastLeft, BlockPos worldPos, ArrayList<BeltQuad> result, float vStart, float vEnd) {

        var skirtHeight = 0.15f;

        // top quad
        var uMin = 0f;
        var uMax = 1f;
        var vMin = vStart;
        var vMax = vEnd;

        var botRight = BeltVertex.create(lastRight, uMin, vMin);
        var topRight = BeltVertex.create(nextRight, uMin, vMax);
        var topLeft = BeltVertex.create(nextLeft, uMax, vMax);
        var botLeft = BeltVertex.create(lastLeft, uMax, vMin);

        var quad = new BeltQuad(topRight, topLeft, botLeft, botRight, worldPos);
        result.add(quad);

        // right skirt
        uMin = 0f;
        uMax = 2 / 16f;
        vMin = vStart;
        vMax = vEnd;

        topRight = BeltVertex.create(nextRight.add(0, -skirtHeight, 0), uMax, vMax);
        topLeft = BeltVertex.create(nextRight, uMin, vMax);
        botLeft = BeltVertex.create(lastRight, uMin, vMin);
        botRight = BeltVertex.create(lastRight.add(0, -skirtHeight, 0), uMax, vMin);

        quad = new BeltQuad(topRight, topLeft, botLeft, botRight, worldPos);
        result.add(quad);

        // left skirt
        uMin = 0f;
        uMax = 2 / 16f;
        vMin = vStart;
        vMax = vEnd;

        topRight = BeltVertex.create(nextLeft.add(0, -skirtHeight, 0), uMax, vMax);
        topLeft = BeltVertex.create(nextLeft, uMin, vMax);
        botLeft = BeltVertex.create(lastLeft, uMin, vMin);
        botRight = BeltVertex.create(lastLeft.add(0, -skirtHeight, 0), uMax, vMin);

        quad = new BeltQuad(botRight, botLeft, topLeft, topRight, worldPos);
        result.add(quad);

        // bot quad
        uMin = 0f;
        uMax = 1f;
        vMin = vStart;
        vMax = vEnd;

        botRight = BeltVertex.create(lastRight.add(0, -skirtHeight, 0), uMin, vMin);
        topRight = BeltVertex.create(nextRight.add(0, -skirtHeight, 0), uMin, vMax);
        topLeft = BeltVertex.create(nextLeft.add(0, -skirtHeight, 0), uMax, vMax);
        botLeft = BeltVertex.create(lastLeft.add(0, -skirtHeight, 0), uMax, vMin);

        quad = new BeltQuad(botRight, botLeft, topLeft, topRight, worldPos);
        result.add(quad);
    }

    public static void renderItems(BlockEntity entity, Direction output, Iterable<ChuteBlockEntity.BeltItem> renderedItems, PoseStack matrices, MultiBufferSource vertexConsumers, int overlay, ChuteBlockEntity.BeltData beltData, int itemRenderDistSq, double presentationTick) {
        pureneko.conveyor_belt_plus.client.BeltPickupTarget.considerRoute(entity, output, beltData);
        var client = Minecraft.getInstance();
        var cam = client.getCameraEntity();
        if (cam == null) return;
        var camPos = cam.position();
        var camLookDir = cam.getLookAngle();
        var itemRenderer = client.getItemRenderer();
        var world = entity.getLevel();
        var entityCenter = entity.getBlockPos().getCenter();

        for (var itemData : renderedItems) {
            if (!itemData.visible(presentationTick)) continue;
            var renderedStack = itemData.stack;
            var renderedProgress = itemData.renderedProgress(presentationTick);

            var worldPoint = SplineUtil.getPositionOnSpline(beltData, renderedProgress);

            // abort early is camera is too far away
            var camDist = camPos.distanceToSqr(worldPoint);
            if (camDist > itemRenderDistSq) continue;

            // abort if item is behind player (very basic frustum culling)
            var itemOffset = worldPoint.subtract(camPos);
            // negative dot product means the item is behind
            if (camDist > 1f && camLookDir.dot(itemOffset) < 0)
                continue;

            BeltPickupTarget.consider(entity, output, itemData, renderedProgress, worldPoint);

            // One lookup resolves both tangent samples; the item position is already known.
            var tangent = SplineUtil.getWindowOnSpline(beltData, renderedProgress - 0.001f, renderedProgress + 0.001f);
            var forward = tangent[1].subtract(tangent[0]);
            var renderPosition = worldPoint.subtract(entityCenter);
            var flatForward = new Vec3(forward.x, 0, forward.z).normalize();
            var dot = UNIT_X.dot(flatForward);
            var angleRad = Math.acos(net.minecraft.util.Mth.clamp(dot, -1, 1));
            var angleUp = Math.acos(net.minecraft.util.Mth.clamp(forward.normalize().dot(flatForward), -1, 1));

            if (forward.y < 0)
                angleUp = -angleUp;

            if (flatForward.z > 0) {
                angleRad = -angleRad;
            }

            matrices.pushPose();
            matrices.translate(renderPosition.x, renderPosition.y, renderPosition.z);
            matrices.translate(0.5f, 0.8f - 3 / 16f, 0.5f);

            if (pureneko.conveyor_belt_plus.util.FluidPackets.isPacket(renderedStack)) {
                matrices.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(angleRad)));
                FluidPacketRenderer.render(pureneko.conveyor_belt_plus.util.FluidPackets.get(renderedStack), entity.getLevel(),
                        matrices, vertexConsumers, lightAt(entity.getLevel(), BlockPos.containing(worldPoint)), overlay);
                matrices.popPose();
                continue;
            }
            var bakedmodel = Minecraft.getInstance().getItemRenderer().getModel(renderedStack, entity.getLevel(), null, 0);
            var useItemTransform = !bakedmodel.getQuads(null, null, net.minecraft.util.RandomSource.create(itemData.id)).isEmpty();

            if (useItemTransform) {
                matrices.translate(0, -2 / 16f, 0);
                matrices.scale(0.8f, 0.8f, 0.8f);
            }

            matrices.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(angleRad)));
            if (Math.abs(angleUp) > 0.01f)
                matrices.mulPose(Axis.ZP.rotationDegrees((float) Math.toDegrees(angleUp)));
            matrices.mulPose(Axis.XP.rotationDegrees(90));

            matrices.scale(0.6f, 0.6f, 0.6f);

            var worldPos = BlockPos.containing(worldPoint);

            var worldLight = lightAt(world, worldPos);

            itemRenderer.renderStatic(
              renderedStack,
              ItemDisplayContext.FIXED,
              worldLight,
              overlay,
              matrices,
              vertexConsumers,
              world,
              0
            );

            matrices.popPose();
        }

    }

    private void renderBeltFilter(ChuteBlockEntity entity, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay, int itemRenderDistSq) {
        var renderedStack = entity.getFilter(0);
        for (int i = 1; i < entity.getFilterSlotCount() && renderedStack.isEmpty(); i++)
            renderedStack = entity.getFilter(i);
        if (renderedStack.isEmpty()) return;

        var worldPoint = entity.getBlockPos().getCenter();
        var cam = Minecraft.getInstance().getCameraEntity();

        // abort early is camera is too far away
        var camDist = cam.position().distanceToSqr(worldPoint);
        if (camDist > itemRenderDistSq) return;

        // abort if item is behind player (very basic frustum culling)
        var camLookDir = cam.getLookAngle();
        var itemOffset = worldPoint.subtract(cam.position());
        // negative dot product means the item is behind
        if (camDist > 5f && camLookDir.dot(itemOffset.normalize()) < 0)
            return;

        var ownFacing = entity.getOwnFacing();

        var forwardDir = Vec3.atLowerCornerOf(ownFacing.getNormal());
        var renderOffset = forwardDir.scale(-0.43f);

        matrices.pushPose();
        matrices.translate(0.5f, 0.7f, 0.5f);
        matrices.translate(renderOffset.x, renderOffset.y, renderOffset.z);

        if (ownFacing.getAxis().equals(Direction.Axis.X))
            matrices.mulPose(Axis.YP.rotationDegrees(90));

        matrices.scale(0.4f, 0.4f, 0.4f);

        Minecraft.getInstance().getItemRenderer().renderStatic(
          renderedStack,
          ItemDisplayContext.FIXED,
          light,
          overlay,
          matrices,
          vertexConsumers,
          entity.getLevel(),
          0
        );

        matrices.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(ChuteBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

}
