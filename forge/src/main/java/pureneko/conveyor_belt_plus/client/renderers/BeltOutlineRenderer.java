package pureneko.conveyor_belt_plus.client.renderers;

import pureneko.conveyor_belt_plus.registry.BlockContent;
import pureneko.conveyor_belt_plus.registry.ComponentContent;
import pureneko.conveyor_belt_plus.util.ChutePlacementPlan;
import pureneko.conveyor_belt_plus.items.BeltItem;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity;
import pureneko.conveyor_belt_plus.blocks.ConveyorNodeUtil;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Pair;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/** A translucent projection of the very same mesh used by a constructed belt. */
public class BeltOutlineRenderer {
    public static void renderPlannedBelt(ClientWorld world, Camera camera, MatrixStack matrices,
                                         VertexConsumerProvider consumers) {
        var client = MinecraftClient.getInstance();
        var player = client.player;
        if (world == null || player == null || client.crosshairTarget == null
                || client.crosshairTarget.getType() != HitResult.Type.BLOCK) return;
        var stack = player.getMainHandStack();
        renderPlannedBelt(world, camera, matrices, consumers, stack, (BlockHitResult) client.crosshairTarget, player.getHorizontalFacing());
    }

    public static void renderPlannedBelt(ClientWorld world, Camera camera, MatrixStack matrices,
                                         VertexConsumerProvider consumers, net.minecraft.item.ItemStack stack,
                                         BlockHitResult hit, Direction horizontalFacing) {
        var player = MinecraftClient.getInstance().player;
        if (world == null || player == null) return;
        if (!(stack.getItem() instanceof BeltItem beltItem)) return;
        var clicked = hit.getBlockPos();
        var node = ConveyorNodeUtil.get(world, clicked);
        var port = hit.getSide().getAxis().isHorizontal()
                ? hit.getSide() : horizontalFacing.getOpposite();
        if (node instanceof ChuteBlockEntity chute) port = chute.getOwnFacing();
        int availableChutes = player.isCreative() ? Integer.MAX_VALUE
                : ChutePlacementPlan.count(ChutePlacementPlan.orderedInventory(player));

        var start = ComponentContent.BELT_START.get(stack);
        var startFacing = ComponentContent.BELT_DIR.get(stack);
        if (start == null || startFacing == null) {
            var position = node != null ? clicked : clicked.offset(hit.getSide());
            boolean valid = node != null ? node.hasOutputPort(port) && !node.isPortUsed(port)
                    : world.getBlockState(position).isReplaceable() && availableChutes >= 1;
            var from = ChuteBlockEntity.BeltData.anchor(world, position, port);
            var tangent = Vec3d.of(port.getVector());
            var data = new ChuteBlockEntity.BeltData(
                    List.of(new Pair<>(from, tangent), new Pair<>(from.add(tangent.multiply(0.8)), tangent)),
                    new double[]{0.8});
            draw(world, data, position, camera, matrices, consumers, valid, beltItem.getBeltTier());
            return;
        }

        var midpoints = BeltItem.getStoredMidpoints(stack, world);
        BlockPos end;
        Direction endPort;
        boolean isSupport = world.getBlockState(clicked).isOf(BlockContent.CONVEYOR_SUPPORT_BLOCK.get());
        boolean validEnd;
        if (node != null) {
            end = clicked;
            endPort = port;
            validEnd = node.hasInputPort(port) && !node.isPortUsed(port);
        } else if (isSupport) {
            end = clicked;
            var facing = world.getBlockState(clicked).get(HorizontalFacingBlock.FACING);
            var last = midpoints.isEmpty() ? start : midpoints.get(midpoints.size() - 1).getLeft();
            var travel = end.offset(facing.getOpposite()).getSquaredDistance(last)
                    < end.offset(facing).getSquaredDistance(last) ? facing : facing.getOpposite();
            endPort = travel.getOpposite();
            validEnd = midpoints.stream().noneMatch(point -> point.getLeft().equals(clicked));
        } else {
            end = clicked.offset(hit.getSide());
            endPort = hit.getSide().getAxis().isHorizontal()
                    ? hit.getSide() : horizontalFacing;
            validEnd = world.getBlockState(end).isReplaceable();
        }
        if (start.equals(end)) return;
        var startNode = ConveyorNodeUtil.get(world, start);
        boolean validStart = startNode == null ? world.getBlockState(start).isReplaceable()
                : startNode.hasOutputPort(startFacing) && !startNode.isPortUsed(startFacing);
        int needed = (startNode == null ? 1 : 0) + (node == null && !isSupport ? 1 : 0);
        var validData = ChuteBlockEntity.BeltData.create(world, start, startFacing, end, endPort, midpoints);
        boolean valid = validStart && validEnd && availableChutes >= needed && validData != null;
        var data = validData != null ? validData
                : ChuteBlockEntity.BeltData.create(world, start, startFacing, end, endPort, midpoints, false);
        draw(world, data, start, camera, matrices, consumers, valid, beltItem.getBeltTier());
    }

    private static void draw(ClientWorld world, ChuteBlockEntity.BeltData data, BlockPos origin, Camera camera,
                             MatrixStack matrices, VertexConsumerProvider consumers, boolean valid, int tier) {
        if (data == null || data.totalLength() < 0.001 || !Double.isFinite(data.totalLength())) return;
        var quads = ChuteBeltRenderer.createSplineModel(data, origin);
        if (quads == null) return;
        matrices.push();
        var cameraPos = camera.getPos();
        matrices.translate(origin.getX() - cameraPos.x, origin.getY() - cameraPos.y,
                origin.getZ() - cameraPos.z);
        BeltMeshRenderer.draw(world, quads, matrices, consumers, tier, 0, OverlayTexture.DEFAULT_UV,
                valid ? 0xA060FFD0 : 0xB0FF6060, true);
        matrices.pop();
    }
}
