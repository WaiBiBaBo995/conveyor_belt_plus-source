package pureneko.conveyor_belt_plus.client.renderers;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Colors;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity;
import pureneko.conveyor_belt_plus.blocks.ConveyorNodeUtil;
import pureneko.conveyor_belt_plus.util.BeltMaterials;
import pureneko.conveyor_belt_plus.client.BeltPickupTarget;
import pureneko.conveyor_belt_plus.util.BeltQuad;
import pureneko.conveyor_belt_plus.util.BeltRenderClock;
import pureneko.conveyor_belt_plus.util.SplineUtil;
import pureneko.conveyor_belt_plus.util.BeltVertex;

import java.util.ArrayList;

public class ChuteBeltRenderer implements BlockEntityRenderer<ChuteBlockEntity> {

    private static final Vec3d UNIT_X = new Vec3d(1, 0, 0);

    private static final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap lightmapCache = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
    private static net.minecraft.world.World lightmapWorld;
    private static long lightmapTick = Long.MIN_VALUE;
    static { lightmapCache.defaultReturnValue(-1); }

    public static void clearLightingCache() {
        lightmapCache.clear();
        lightmapWorld = null;
        lightmapTick = Long.MIN_VALUE;
    }

    /** Shared by belt faces and items; bounded lifetime and no boxed position keys. */
    public static int lightAt(net.minecraft.world.World world, BlockPos pos) {
        if (lightmapWorld != world || lightmapTick != world.getTime()) {
            lightmapWorld = world;
            lightmapTick = world.getTime();
            lightmapCache.clear();
        }
        int light = lightmapCache.get(pos.asLong());
        if (light == -1) {
            light = WorldRenderer.getLightmapCoordinates(world, pos);
            lightmapCache.put(pos.asLong(), light);
        }
        return light;
    }

    @Override
    public void render(ChuteBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {

        if (entity == null || entity.getWorld() == null) return;

        if (entity.getTarget() == null || entity.getTarget().getManhattanDistance(entity.getPos()) < 1) return;

        var targetNode = ConveyorNodeUtil.get(entity.getWorld(), entity.getTarget());
        if (targetNode == null) return;

        var beltData = entity.getBeltData();
        if (beltData == null) return;

        var itemRenderDistSq = 64 * 64;
        var beltRenderDistSq = 96 * 96;

        var state = entity.getVisualState();
        double presentationTick = state.presentationTick(BeltRenderClock.renderTime(entity.getWorld(), tickDelta));
        int frame = BeltMaterials.frame(state.renderedDistance(presentationTick));
        if (entity.renderedModel == null)
            entity.renderedModel = createSplineModel(beltData, entity.getPos());
        BeltMeshRenderer.draw(entity.getWorld(), entity.renderedModel, matrices, vertexConsumers,
                entity.getBeltTier(), frame, overlay, Colors.WHITE, false);

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

        var conveyorStartDir = beltData.allPoints().get(0).getRight();
        var conveyorEndDir = beltData.allPoints().get(beltData.allPoints().size() - 1).getRight();

        var beginRight = conveyorStartDir.crossProduct(new Vec3d(0, 1, 0)).normalize();

        // local space
        var localStart = beltData.allPoints().get(0).getLeft().subtract(Vec3d.of(origin));
        var lastRight = localStart.add(beginRight.multiply(lineWidth));
        var lastLeft = localStart.add(beginRight.multiply(-lineWidth));

        for (int i = 0; i < segmentCount; i++) {

            var last = i == segmentCount - 1;
            var progress = i / (float) segmentCount;
            var nextProgress = (i + 1) / (float) segmentCount;
            var worldPoint = SplineUtil.getPositionOnSpline(beltData, progress);
            var localPoint = worldPoint.subtract(origin.toCenterPos());
            var worldPointNext = SplineUtil.getPositionOnSpline(beltData, nextProgress);
            var localPointNext = worldPointNext.subtract(origin.toCenterPos());

            var worldPos = BlockPos.ofFloored(worldPointNext.add(0.5f, 0, 0.5f));

            var direction = localPointNext.subtract(localPoint);
            var cross = direction.crossProduct(new Vec3d(0, 1, 0)).normalize();
            if (last)
                cross = conveyorEndDir.crossProduct(new Vec3d(0, 1, 0)).normalize();

            var nextRight = localPointNext.add(cross.multiply(lineWidth)).add(0.5f, 0.5f, 0.5f);
            var nextLeft = localPointNext.add(cross.multiply(-lineWidth)).add(0.5f, 0.5f, 0.5f);

            var dirA = lastLeft.subtract(lastRight).normalize();
            var dirB = nextLeft.subtract(nextRight).normalize();
            var curveStrength = 1 - Math.abs(dirA.dotProduct(dirB));

            // split into 2 segments for strong curved segments
            if (curveStrength > 0.025) {
                var midProgress = (i + 0.5f) / (float) segmentCount;
                var worldPointMid = SplineUtil.getPositionOnSpline(beltData, midProgress);
                var localPointMid = worldPointMid.subtract(origin.toCenterPos());

                var directionMid = localPointMid.subtract(localPoint);
                var crossMid = directionMid.crossProduct(new Vec3d(0, 1, 0)).normalize();

                var midRight = localPointMid.add(crossMid.multiply(lineWidth)).add(0.5f, 0.5f, 0.5f);
                var midLeft = localPointMid.add(crossMid.multiply(-lineWidth)).add(0.5f, 0.5f, 0.5f);

                direction = localPointNext.subtract(localPointMid);
                cross = direction.crossProduct(new Vec3d(0, 1, 0)).normalize();
                if (last)
                    cross = conveyorEndDir.crossProduct(new Vec3d(0, 1, 0)).normalize();

                nextRight = localPointNext.add(cross.multiply(lineWidth)).add(0.5f, 0.5f, 0.5f);
                nextLeft = localPointNext.add(cross.multiply(-lineWidth)).add(0.5f, 0.5f, 0.5f);

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

    private static void addSegmentVertices(Vec3d nextRight, Vec3d lastRight, Vec3d nextLeft, Vec3d lastLeft, BlockPos worldPos, ArrayList<BeltQuad> result, float vStart, float vEnd) {

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

    public static void renderItems(BlockEntity entity, Direction output, Iterable<ChuteBlockEntity.BeltItem> renderedItems, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int overlay, ChuteBlockEntity.BeltData beltData, int itemRenderDistSq, double presentationTick) {
        var client = MinecraftClient.getInstance();
        var cam = client.getCameraEntity();
        if (cam == null) return;
        var camPos = cam.getPos();
        var camLookDir = cam.getRotationVector();
        var itemRenderer = client.getItemRenderer();
        var world = entity.getWorld();
        var entityCenter = entity.getPos().toCenterPos();

        for (var itemData : renderedItems) {
            if (!itemData.visible(presentationTick)) continue;
            var renderedStack = itemData.stack;
            var renderedProgress = itemData.renderedProgress(presentationTick);

            var worldPoint = SplineUtil.getPositionOnSpline(beltData, renderedProgress);

            // abort early is camera is too far away
            var camDist = camPos.squaredDistanceTo(worldPoint);
            if (camDist > itemRenderDistSq) continue;

            // abort if item is behind player (very basic frustum culling)
            var itemOffset = worldPoint.subtract(camPos);
            // negative dot product means the item is behind
            if (camDist > 1f && camLookDir.dotProduct(itemOffset) < 0)
                continue;

            BeltPickupTarget.consider(entity, output, itemData, renderedProgress, worldPoint);

            // One lookup resolves both tangent samples; the item position is already known.
            var tangent = SplineUtil.getWindowOnSpline(beltData, renderedProgress - 0.001f, renderedProgress + 0.001f);
            var forward = tangent[1].subtract(tangent[0]);
            var renderPosition = worldPoint.subtract(entityCenter);
            var flatForward = new Vec3d(forward.x, 0, forward.z).normalize();
            var dot = UNIT_X.dotProduct(flatForward);
            var angleRad = Math.acos(net.minecraft.util.math.MathHelper.clamp(dot, -1, 1));
            var angleUp = Math.acos(net.minecraft.util.math.MathHelper.clamp(forward.normalize().dotProduct(flatForward), -1, 1));

            if (forward.y < 0)
                angleUp = -angleUp;

            if (flatForward.z > 0) {
                angleRad = -angleRad;
            }

            matrices.push();
            matrices.translate(renderPosition.x, renderPosition.y, renderPosition.z);
            matrices.translate(0.5f, 0.8f - 3 / 16f, 0.5f);

            var bakedmodel = itemRenderer.getModel(renderedStack, world, null, 0);
            var useItemTransform = !bakedmodel.getQuads(null, null, Random.create(itemData.id)).isEmpty();

            if (useItemTransform) {
                matrices.translate(0, -2 / 16f, 0);
                matrices.scale(0.8f, 0.8f, 0.8f);
            }

            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) Math.toDegrees(angleRad)));
            if (Math.abs(angleUp) > 0.01f)
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.toDegrees(angleUp)));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));

            matrices.scale(0.6f, 0.6f, 0.6f);

            var worldPos = BlockPos.ofFloored(worldPoint);

            var worldLight = lightAt(world, worldPos);

            itemRenderer.renderItem(
              renderedStack,
              ModelTransformationMode.FIXED,
              worldLight,
              overlay,
              matrices,
              vertexConsumers,
              world,
              0
            );

            matrices.pop();
        }

    }

    private void renderBeltFilter(ChuteBlockEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, int itemRenderDistSq) {
        var renderedStack = entity.getFilter(0);
        for (int i = 1; i < entity.getFilterSlotCount() && renderedStack.isEmpty(); i++)
            renderedStack = entity.getFilter(i);
        if (renderedStack.isEmpty()) return;

        var worldPoint = entity.getPos().toCenterPos();
        var cam = MinecraftClient.getInstance().getCameraEntity();

        // abort early is camera is too far away
        var camDist = cam.getPos().squaredDistanceTo(worldPoint);
        if (camDist > itemRenderDistSq) return;

        // abort if item is behind player (very basic frustum culling)
        var camLookDir = cam.getRotationVector();
        var itemOffset = worldPoint.subtract(cam.getPos());
        // negative dot product means the item is behind
        if (camDist > 5f && camLookDir.dotProduct(itemOffset.normalize()) < 0)
            return;

        var ownFacing = entity.getOwnFacing();

        var forwardDir = Vec3d.of(ownFacing.getVector());
        var renderOffset = forwardDir.multiply(-0.43f);

        matrices.push();
        matrices.translate(0.5f, 0.7f, 0.5f);
        matrices.translate(renderOffset.x, renderOffset.y, renderOffset.z);

        if (ownFacing.getAxis().equals(Direction.Axis.X))
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90));

        matrices.scale(0.4f, 0.4f, 0.4f);

        MinecraftClient.getInstance().getItemRenderer().renderItem(
          renderedStack,
          ModelTransformationMode.FIXED,
          light,
          overlay,
          matrices,
          vertexConsumers,
          entity.getWorld(),
          0
        );

        matrices.pop();
    }

    @Override
    public boolean rendersOutsideBoundingBox(ChuteBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getRenderDistance() {
        return 96;
    }

}
