package pureneko.conveyor_belt_plus.client.renderers;

import pureneko.conveyor_belt_plus.registry.BlockContent;
import pureneko.conveyor_belt_plus.registry.ComponentContent;
import pureneko.conveyor_belt_plus.util.ChutePlacementPlan;
import pureneko.conveyor_belt_plus.items.BeltItem;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity.BeltData;
import pureneko.conveyor_belt_plus.blocks.ConveyorNode;
import pureneko.conveyor_belt_plus.blocks.ConveyorNodeUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A translucent projection of the very same mesh used by a constructed belt. */
public class BeltOutlineRenderer {
    private record PreviewKey(ClientLevel world, long tick, BlockPos clicked, Direction face, Direction look,
                              BlockPos start, Direction startFacing, Object item, Object midpoints, int chutes, boolean sneak, boolean miss) {}
    private static long previewFrames;
    public static long previewFrames() { return previewFrames; }
    public static boolean previewValid() { return plannedValid; }
    private static long upgradeFrames;
    public static long upgradeFrames() { return upgradeFrames; }
    private static PreviewKey previewKey;
    private static ChuteBlockEntity.BeltData plannedData;
    private static BlockPos plannedOrigin;
    private static pureneko.conveyor_belt_plus.util.BeltQuad[] plannedMesh;
    private static boolean plannedValid;
    public static void clear() {
        previewKey = null; plannedData = cachedData = null;
        plannedMesh = cachedMesh = null; plannedOrigin = cachedOrigin = null;
    }
    private static ChuteBlockEntity.BeltData cachedData;
    private static BlockPos cachedOrigin;
    private static pureneko.conveyor_belt_plus.util.BeltQuad[] cachedMesh;
    public static void renderUpgrade(ClientLevel world, Camera camera, PoseStack matrices, MultiBufferSource consumers) {
        var target = pureneko.conveyor_belt_plus.client.BeltPickupTarget.currentUpgrade();
        if (target == null) {
            var client = Minecraft.getInstance();
            var player = client.player;
            var remote = net.minecraftforge.fml.ModList.get().isLoaded("rtsbuilding") ? pureneko.conveyor_belt_plus.compat.rts.RtsClient.view() : null;
            var selected = remote == null ? (player == null ? ItemStack.EMPTY : player.getMainHandItem()) : remote.stack();
            var cursor = remote == null ? client.hitResult : remote.hit();
            if (player == null || !(remote == null ? player.isShiftKeyDown() : net.minecraft.client.gui.screens.Screen.hasShiftDown()) || player.isSpectator() || !player.getAbilities().mayBuild
                    || !(selected.getItem() instanceof BeltItem held)
                    || !(cursor instanceof BlockHitResult hit)) return;
            var node = ConveyorNodeUtil.get(world, hit.getBlockPos());
            var port = node instanceof ChuteBlockEntity chute ? chute.getOwnFacing()
                    : hit.getDirection().getAxis().isHorizontal() ? hit.getDirection() : player.getDirection().getOpposite();
            var access = node == null ? null : node.pickupAccess(port);
            if (access == null || access.data() == null || !pureneko.conveyor_belt_plus.util.TierUpgrade.isUpgrade(access.tier(), held.getBeltTier())) return;
            target = new pureneko.conveyor_belt_plus.client.BeltPickupTarget.UpgradeTarget(hit.getBlockPos(), port, access.data(), access.tier(), held.getBeltTier());
        }
        if (cachedData != target.data() || !target.owner().equals(cachedOrigin)) {
            cachedData = target.data();
            cachedOrigin = target.owner();
            cachedMesh = ChuteBeltRenderer.createSplineModel(cachedData, cachedOrigin);
        }
        upgradeFrames++;
        float pulse = .5f + .5f * (float)Math.sin(pureneko.conveyor_belt_plus.util.BeltRenderClock.now(world) * .65);
        int alpha = (int)(65 + 125 * pulse);
        matrices.pushPose();
        var cameraPos = camera.getPosition();
        matrices.translate(cachedOrigin.getX() - cameraPos.x, cachedOrigin.getY() - cameraPos.y + (target.data().isVertical() ? 0 : .003), cachedOrigin.getZ() - cameraPos.z + (target.data().isVertical() ? .003 : 0));
        BeltMeshRenderer.draw(world, cachedMesh, matrices, consumers, target.tier(), 0, OverlayTexture.NO_OVERLAY,
                alpha << 24 | 0xFFFFFF, true, target.data().isVertical());
        matrices.popPose();
    }

    public static void renderPlannedBelt(ClientLevel world, Camera camera, PoseStack matrices,
                                         MultiBufferSource consumers) {
        var client = Minecraft.getInstance();
        var player = client.player;
        if (world == null || player == null || client.hitResult == null) return;
        var endpoint = player.getEyePosition().add(player.getLookAngle().scale(player.getBlockReach()));
        var hit = client.hitResult instanceof BlockHitResult block ? block
                : BlockHitResult.miss(endpoint, player.getDirection().getOpposite(), BlockPos.containing(endpoint));
        var stack = player.getMainHandItem();
        renderPlannedBelt(world, camera, matrices, consumers, stack, hit, player.getDirection(), player.isShiftKeyDown());
    }

    public static void renderPlannedBelt(ClientLevel world, Camera camera, PoseStack matrices,
                                         MultiBufferSource consumers, net.minecraft.world.item.ItemStack stack,
                                         BlockHitResult hit, Direction horizontalFacing) {
        renderPlannedBelt(world, camera, matrices, consumers, stack, hit, horizontalFacing, Minecraft.getInstance().player != null && Minecraft.getInstance().player.isShiftKeyDown());
    }

    public static void renderPlannedBelt(ClientLevel world, Camera camera, PoseStack matrices,
            MultiBufferSource consumers, ItemStack stack, BlockHitResult hit, Direction horizontalFacing, boolean sneak) {
        var player = Minecraft.getInstance().player;
        if (world == null || player == null || hit == null || !(stack.getItem() instanceof BeltItem beltItem)) return;
        boolean miss = hit.getType() == HitResult.Type.MISS;
        boolean hasStart = ComponentContent.BELT_START.get(stack) != null;
        var clicked = hit.getBlockPos();
        var node = miss ? null : ConveyorNodeUtil.get(world, clicked);
        var port = node != null && hit.getDirection().getAxis().isVertical()
                ? horizontalFacing.getOpposite() : hit.getDirection();
        if (node instanceof ChuteBlockEntity chute) port = chute.getOwnFacing();
        else if (node == null) port = ChutePlacementPlan.facing(hit.getDirection(), horizontalFacing, sneak, !hasStart);
        if (sneak && node != null && node.outgoingBeltTier(port) > 0) return;
        int availableChutes = player.isCreative() ? Integer.MAX_VALUE
                : ChutePlacementPlan.count(ChutePlacementPlan.orderedInventory(player));

        var start = ComponentContent.BELT_START.get(stack);
        var startFacing = ComponentContent.BELT_DIR.get(stack);
        var key = new PreviewKey(world, world.getGameTime(), clicked, hit.getDirection(), horizontalFacing,
                start, startFacing, stack.getItem(), ComponentContent.MIDPOINTS.get(stack), availableChutes, sneak, miss);
        if (key.equals(previewKey)) {
            draw(world, plannedData, plannedOrigin, camera, matrices, consumers, plannedValid, beltItem.getBeltTier());
            return;
        }
        previewKey = key;
        plannedData = null;
        plannedMesh = null;
        if (start == null || startFacing == null) {
            if (miss) return;
            var position = node != null ? clicked : clicked.relative(hit.getDirection());
            boolean valid = node != null ? node.hasOutputPort(port) && !node.isPortUsed(port)
                    : world.getBlockState(position).canBeReplaced() && availableChutes >= 1;
            var from = ChuteBlockEntity.BeltData.anchor(world, position, port);
            var tangent = Vec3.atLowerCornerOf(port.getNormal());
            var data = new ChuteBlockEntity.BeltData(
                    List.of(new Tuple<>(from, tangent), new Tuple<>(from.add(tangent.scale(0.8)), tangent)),
                    new double[]{0.8});
            draw(world, data, position, camera, matrices, consumers, valid, beltItem.getBeltTier());
            return;
        }

        var midpoints = BeltItem.getStoredMidpoints(stack, world);
        BlockPos end;
        Direction endPort;
        boolean isSupport = !miss && world.getBlockState(clicked).is(BlockContent.CONVEYOR_SUPPORT_BLOCK.get());
        boolean validEnd;
        if (node != null) {
            end = clicked;
            endPort = port;
            validEnd = node.hasInputPort(port) && !node.isPortUsed(port);
        } else if (isSupport) {
            end = clicked;
            var facing = world.getBlockState(clicked).getValue(HorizontalDirectionalBlock.FACING);
            var last = midpoints.isEmpty() ? start : midpoints.get(midpoints.size() - 1).getA();
            var travel = end.relative(facing.getOpposite()).distSqr(last)
                    < end.relative(facing).distSqr(last) ? facing : facing.getOpposite();
            endPort = travel.getOpposite();
            validEnd = midpoints.stream().noneMatch(point -> point.getA().equals(clicked));
        } else {
            end = miss ? clicked : clicked.relative(hit.getDirection());
            endPort = port;
            validEnd = world.getBlockState(end).canBeReplaced();
        }
        if (start.equals(end)) return;
        var startNode = ConveyorNodeUtil.get(world, start);
        boolean validStart = startNode == null ? world.getBlockState(start).canBeReplaced()
                : startNode.hasOutputPort(startFacing) && !startNode.isPortUsed(startFacing);
        int needed = (startNode == null ? 1 : 0) + (node == null && !isSupport ? 1 : 0);
        var validData = ChuteBlockEntity.BeltData.create(world, start, startFacing, end, endPort, midpoints,
                true, pureneko.conveyor_belt_plus.config.ConveyorConfig.minimumBeltTurnRadius(),
                pureneko.conveyor_belt_plus.config.ConveyorConfig.minimumBeltAngle());
        boolean valid = !miss && validStart && validEnd && availableChutes >= needed && validData != null;
        var data = miss ? null : validData != null ? validData
                : ChuteBlockEntity.BeltData.create(world, start, startFacing, end, endPort, midpoints, false);
        if (data == null) {
            // Invalid temporary geometry is a red guide, never a constructible route.
            var a = ChuteBlockEntity.BeltData.anchor(world, start, startFacing);
            var b = miss ? hit.getLocation() : ChuteBlockEntity.BeltData.anchor(world, end, endPort);
            if (a.distanceToSqr(b) < .0001) b = a.add(Vec3.atLowerCornerOf(startFacing.getNormal()).scale(.8));
            var tangent = b.subtract(a).normalize();
            data = new BeltData(List.of(new Tuple<>(a,tangent),new Tuple<>(b,tangent)),new double[]{a.distanceTo(b)});
        }
        draw(world, data, start, camera, matrices, consumers, valid, beltItem.getBeltTier());
    }

    private static void draw(ClientLevel world, ChuteBlockEntity.BeltData data, BlockPos origin, Camera camera,
                             PoseStack matrices, MultiBufferSource consumers, boolean valid, int tier) {
        if (data == null || data.totalLength() < 0.001 || !Double.isFinite(data.totalLength())) return;
        if (plannedData != data || !origin.equals(plannedOrigin)) {
            plannedData = data;
            plannedOrigin = origin;
            plannedValid = valid;
            plannedMesh = ChuteBeltRenderer.createSplineModel(data, origin);
        }
        var quads = plannedMesh;
        if (quads == null) return;
        previewFrames++;
        matrices.pushPose();
        var cameraPos = camera.getPosition();
        matrices.translate(origin.getX() - cameraPos.x, origin.getY() - cameraPos.y,
                origin.getZ() - cameraPos.z);
        BeltMeshRenderer.draw(world, quads, matrices, consumers, tier, 0, OverlayTexture.NO_OVERLAY,
                valid ? 0xA060FFD0 : 0xB0FF6060, true, data.isVertical());
        matrices.popPose();
    }
}
