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
    public static void renderPlannedBelt(ClientLevel world, Camera camera, PoseStack matrices,
                                         MultiBufferSource consumers) {
        var client = Minecraft.getInstance();
        var player = client.player;
        if (world == null || player == null || client.hitResult == null
                || client.hitResult.getType() != HitResult.Type.BLOCK) return;
        var stack = player.getMainHandItem();
        renderPlannedBelt(world, camera, matrices, consumers, stack, (BlockHitResult) client.hitResult, player.getDirection());
    }

    public static void renderPlannedBelt(ClientLevel world, Camera camera, PoseStack matrices,
                                         MultiBufferSource consumers, net.minecraft.world.item.ItemStack stack,
                                         BlockHitResult hit, Direction horizontalFacing) {
        var player = Minecraft.getInstance().player;
        if (world == null || player == null) return;
        if (!(stack.getItem() instanceof BeltItem beltItem)) return;
        var clicked = hit.getBlockPos();
        var node = ConveyorNodeUtil.get(world, clicked);
        var port = hit.getDirection().getAxis().isHorizontal()
                ? hit.getDirection() : horizontalFacing.getOpposite();
        if (node instanceof ChuteBlockEntity chute) port = chute.getOwnFacing();
        int availableChutes = player.isCreative() ? Integer.MAX_VALUE
                : ChutePlacementPlan.count(ChutePlacementPlan.orderedInventory(player));

        var start = stack.get(ComponentContent.BELT_START.get());
        var startFacing = stack.get(ComponentContent.BELT_DIR.get());
        if (start == null || startFacing == null) {
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
        boolean isSupport = world.getBlockState(clicked).is(BlockContent.CONVEYOR_SUPPORT_BLOCK.get());
        boolean validEnd;
        if (node != null) {
            end = clicked;
            endPort = port;
            validEnd = node.hasInputPort(port) && !node.isPortUsed(port);
        } else if (isSupport) {
            end = clicked;
            var facing = world.getBlockState(clicked).getValue(HorizontalDirectionalBlock.FACING);
            var last = midpoints.isEmpty() ? start : midpoints.getLast().getA();
            var travel = end.relative(facing.getOpposite()).distSqr(last)
                    < end.relative(facing).distSqr(last) ? facing : facing.getOpposite();
            endPort = travel.getOpposite();
            validEnd = midpoints.stream().noneMatch(point -> point.getA().equals(clicked));
        } else {
            end = clicked.relative(hit.getDirection());
            endPort = hit.getDirection().getAxis().isHorizontal()
                    ? hit.getDirection() : horizontalFacing;
            validEnd = world.getBlockState(end).canBeReplaced();
        }
        if (start.equals(end)) return;
        var startNode = ConveyorNodeUtil.get(world, start);
        boolean validStart = startNode == null ? world.getBlockState(start).canBeReplaced()
                : startNode.hasOutputPort(startFacing) && !startNode.isPortUsed(startFacing);
        int needed = (startNode == null ? 1 : 0) + (node == null && !isSupport ? 1 : 0);
        var validData = ChuteBlockEntity.BeltData.create(world, start, startFacing, end, endPort, midpoints);
        boolean valid = validStart && validEnd && availableChutes >= needed && validData != null;
        var data = validData != null ? validData
                : ChuteBlockEntity.BeltData.create(world, start, startFacing, end, endPort, midpoints, false);
        draw(world, data, start, camera, matrices, consumers, valid, beltItem.getBeltTier());
    }

    private static void draw(ClientLevel world, ChuteBlockEntity.BeltData data, BlockPos origin, Camera camera,
                             PoseStack matrices, MultiBufferSource consumers, boolean valid, int tier) {
        if (data == null || data.totalLength() < 0.001 || !Double.isFinite(data.totalLength())) return;
        var quads = ChuteBeltRenderer.createSplineModel(data, origin);
        if (quads == null) return;
        matrices.pushPose();
        var cameraPos = camera.getPosition();
        matrices.translate(origin.getX() - cameraPos.x, origin.getY() - cameraPos.y,
                origin.getZ() - cameraPos.z);
        BeltMeshRenderer.draw(world, quads, matrices, consumers, tier, 0, OverlayTexture.NO_OVERLAY,
                valid ? 0xA060FFD0 : 0xB0FF6060, true);
        matrices.popPose();
    }
}
