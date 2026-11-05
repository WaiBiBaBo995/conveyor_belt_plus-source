package pureneko.conveyor_belt_plus.client.renderers;

import net.minecraft.client.Minecraft;
import pureneko.conveyor_belt_plus.client.BeltPickupTarget;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.CommonColors;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity.BeltData;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity.BeltItem;
import pureneko.conveyor_belt_plus.blocks.ConveyorNode;
import pureneko.conveyor_belt_plus.blocks.ConveyorNodeUtil;
import pureneko.conveyor_belt_plus.util.BeltMaterials;
import pureneko.conveyor_belt_plus.util.BeltVisualState;
import pureneko.conveyor_belt_plus.client.BeltRenderClock;
import pureneko.conveyor_belt_plus.util.SplineUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;

public class ChuteBeltRenderer implements BlockEntityRenderer<ChuteBlockEntity> {

    private static final Vec3 UNIT_X = new Vec3(1, 0, 0);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final net.minecraft.world.level.block.state.BlockState TRAY = net.minecraft.world.level.block.Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
    private static final java.util.Map<BakedModel, Boolean> FLAT_MODELS = new java.util.IdentityHashMap<>();

    /** A vertical tangent has no cross product with world-up; use a stable east/west width. */
    private static Vec3 right(Vec3 direction) {
        var result = direction.cross(UP);
        return result.lengthSqr() < 1e-10 ? UNIT_X : result.normalize();
    }

    private static final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap lightmapCache = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
    private static net.minecraft.world.level.Level lightmapWorld;
    private static long lightmapTick = Long.MIN_VALUE;
    static { lightmapCache.defaultReturnValue(-1); }

    public static void clearLightingCache() {
        lightmapCache.clear();
        FLAT_MODELS.clear();
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

    public record Vertex(float x, float y, float z, float u, float v) {
        public static Vertex create(Vec3 pos, float u, float v) {
            return new Vertex((float) pos.x, (float) pos.y, (float) pos.z, u, v);
        }
    }

    public record Quad(Vertex a, Vertex b, Vertex c, Vertex d, BlockPos worldPos) {
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
                entity.getBeltTier(), frame, overlay, CommonColors.WHITE, false, beltData.isVertical());

        // render items
        renderItems(entity, entity.getOwnFacing(), entity.getMovingItems(), matrices, vertexConsumers, overlay, beltData, itemRenderDistSq, presentationTick);

        renderBeltFilter(entity, matrices, vertexConsumers, light, overlay, beltRenderDistSq);

    }

    public static Quad[] createSplineModel(ChuteBlockEntity.BeltData beltData, BlockPos origin) {

        var result = new ArrayList<Quad>();

        if (beltData == null) return null;

        var segmentSize = 0.75f;
        var segmentCount = (int) Math.ceil(beltData.totalLength() / segmentSize);
        var lineWidth = 0.33f;

        var conveyorStartDir = beltData.allPoints().getFirst().getB();
        var conveyorEndDir = beltData.allPoints().getLast().getB();

        var beginRight = right(conveyorStartDir);

        // local space
        var localStart = beltData.allPoints().getFirst().getA().subtract(Vec3.atLowerCornerOf(origin));
        localStart = pureneko.conveyor_belt_plus.util.BeltGeometry.surfacePoint(beltData, localStart);
        var lastRight = localStart.add(beginRight.scale(lineWidth));
        var lastLeft = localStart.add(beginRight.scale(-lineWidth));

        for (int i = 0; i < segmentCount; i++) {

            var last = i == segmentCount - 1;
            var progress = i / (float) segmentCount;
            var nextProgress = (i + 1) / (float) segmentCount;
            var worldPoint = SplineUtil.getPositionOnSpline(beltData, progress);
            var localPoint = worldPoint.subtract(origin.getCenter());
            localPoint = pureneko.conveyor_belt_plus.util.BeltGeometry.surfacePoint(beltData, localPoint);
            var worldPointNext = SplineUtil.getPositionOnSpline(beltData, nextProgress);
            var localPointNext = worldPointNext.subtract(origin.getCenter());
            localPointNext = pureneko.conveyor_belt_plus.util.BeltGeometry.surfacePoint(beltData, localPointNext);

            var worldPos = BlockPos.containing(worldPointNext.add(0.5f, 0, 0.5f));

            var direction = localPointNext.subtract(localPoint);
            var cross = right(direction);
            if (last)
                cross = right(conveyorEndDir);

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
                var crossMid = right(directionMid);

                var midRight = localPointMid.add(crossMid.scale(lineWidth)).add(0.5f, 0.5f, 0.5f);
                var midLeft = localPointMid.add(crossMid.scale(-lineWidth)).add(0.5f, 0.5f, 0.5f);

                direction = localPointNext.subtract(localPointMid);
                cross = right(direction);
                if (last)
                    cross = right(conveyorEndDir);

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

        return result.toArray(Quad[]::new);
    }

    private static void addSegmentVertices(Vec3 nextRight, Vec3 lastRight, Vec3 nextLeft, Vec3 lastLeft, BlockPos worldPos, ArrayList<Quad> result, float vStart, float vEnd) {

        var skirtHeight = 0.15f;
        var tangent = nextRight.subtract(lastRight);
        var normal = tangent.cross(lastLeft.subtract(lastRight)).normalize();
        if (Math.abs(tangent.normalize().y) > .999) normal = new Vec3(0, 0, 1);
        var skirt = normal.scale(-skirtHeight);

        // top quad
        var uMin = 0f;
        var uMax = 1f;
        var vMin = vStart;
        var vMax = vEnd;

        var botRight = Vertex.create(lastRight, uMin, vMin);
        var topRight = Vertex.create(nextRight, uMin, vMax);
        var topLeft = Vertex.create(nextLeft, uMax, vMax);
        var botLeft = Vertex.create(lastLeft, uMax, vMin);

        var quad = new Quad(topRight, topLeft, botLeft, botRight, worldPos);
        result.add(quad);

        // right skirt
        uMin = 0f;
        uMax = 2 / 16f;
        vMin = vStart;
        vMax = vEnd;

        topRight = Vertex.create(nextRight.add(skirt), uMax, vMax);
        topLeft = Vertex.create(nextRight, uMin, vMax);
        botLeft = Vertex.create(lastRight, uMin, vMin);
        botRight = Vertex.create(lastRight.add(skirt), uMax, vMin);

        quad = new Quad(topRight, topLeft, botLeft, botRight, worldPos);
        result.add(quad);

        // left skirt
        uMin = 0f;
        uMax = 2 / 16f;
        vMin = vStart;
        vMax = vEnd;

        topRight = Vertex.create(nextLeft.add(skirt), uMax, vMax);
        topLeft = Vertex.create(nextLeft, uMin, vMax);
        botLeft = Vertex.create(lastLeft, uMin, vMin);
        botRight = Vertex.create(lastLeft.add(skirt), uMax, vMin);

        quad = new Quad(botRight, botLeft, topLeft, topRight, worldPos);
        result.add(quad);

        // bot quad
        uMin = 0f;
        uMax = 1f;
        vMin = vStart;
        vMax = vEnd;

        botRight = Vertex.create(lastRight.add(skirt), uMin, vMin);
        topRight = Vertex.create(nextRight.add(skirt), uMin, vMax);
        topLeft = Vertex.create(nextLeft.add(skirt), uMax, vMax);
        botLeft = Vertex.create(lastLeft.add(skirt), uMax, vMin);

        quad = new Quad(botRight, botLeft, topLeft, topRight, worldPos);
        result.add(quad);
    }

    public static void renderItems(BlockEntity entity, Direction output, Iterable<ChuteBlockEntity.BeltItem> items,
                                   PoseStack matrices, MultiBufferSource consumers, int overlay, BeltData data,
                                   int maxDistance, double presentationTick) {
        BeltPickupTarget.considerRoute(entity, output, data);
        var client = Minecraft.getInstance();
        var camera = client.getCameraEntity();
        if (camera == null) return;
        var position = camera.position();
        var look = camera.getLookAngle();
        for (var packet : items) {
            if (!packet.visible(presentationTick)) continue;
            float progress = packet.renderedProgress(presentationTick);
            var point = pureneko.conveyor_belt_plus.util.BeltGeometry.cargoPoint(data, SplineUtil.getPositionOnSpline(data, progress));
            double distance = position.distanceToSqr(point);
            if (distance > maxDistance || distance > 1 && look.dot(point.subtract(position)) < 0) continue;
            BeltPickupTarget.consider(entity, output, packet, progress, point, data);
            renderPacket(entity, packet, progress, point, data, matrices, consumers, overlay, true);
        }
    }

    /** Shared normal/mask pass, so outlines use the exact same model, pose and fluid geometry. */
    public static void renderPacket(BlockEntity entity, ChuteBlockEntity.BeltItem packet, float progress, Vec3 point,
                                    BeltData data, PoseStack matrices, MultiBufferSource consumers, int overlay, boolean tray) {
        var client = Minecraft.getInstance();
        var renderer = client.getItemRenderer();
        var world = entity.getLevel();
        if (world == null) return;
        boolean vertical = data.isVertical();
        double yaw = 0, slope = 0;
        if (!vertical) {
            var window = SplineUtil.getWindowOnSpline(data, progress - .001, progress + .001);
            var forward = window[1].subtract(window[0]);
            yaw = -Math.atan2(forward.z, forward.x);
            slope = Math.atan2(forward.y, Math.sqrt(forward.x * forward.x + forward.z * forward.z));
        }
        var position = point.subtract(Vec3.atLowerCornerOf(entity.getBlockPos()));
        int light = lightAt(world, BlockPos.containing(point));
        if (vertical && tray) {
            matrices.pushPose();
            matrices.translate(position.x - .30, position.y - .12, position.z - .30);
            matrices.scale(.60f, .24f, .60f);
            client.getBlockRenderer().renderSingleBlock(TRAY, matrices, consumers, light, overlay);
            matrices.popPose();
        }
        matrices.pushPose();
        matrices.translate(position.x, position.y + (vertical ? .18 : .1125), position.z);
        matrices.mulPose(Axis.YP.rotation((float) yaw));
        if (pureneko.conveyor_belt_plus.util.FluidPackets.isPacket(packet.stack)) {
            FluidPacketRenderer.render(pureneko.conveyor_belt_plus.util.FluidPackets.get(packet.stack), world,
                    matrices, consumers, light, overlay);
        } else {
            var model = renderer.getModel(packet.stack, world, null, 0);
            if (FLAT_MODELS.size() > 512) FLAT_MODELS.clear();
            boolean flat = FLAT_MODELS.computeIfAbsent(model,
                    candidate -> !candidate.getQuads(null, null, net.minecraft.util.RandomSource.create(0)).isEmpty());
            if (flat) {
                matrices.translate(0, -.125, 0);
                matrices.scale(.8f, .8f, .8f);
            }
            // No pitch on a lift: the item stays flat on its carrier in either travel direction.
            if (!vertical && Math.abs(slope) > .0001) matrices.mulPose(Axis.ZP.rotation((float) slope));
            matrices.mulPose(Axis.XP.rotationDegrees(90));
            matrices.scale(.6f, .6f, .6f);
            renderer.render(packet.stack, ItemDisplayContext.FIXED, false, matrices, consumers, light, overlay, model);
        }
        matrices.popPose();
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

    // overrides NF mixin
    @Override
    public AABB getRenderBoundingBox(ChuteBlockEntity blockEntity) {
        return new AABB(
          Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY
        );
    }
}
