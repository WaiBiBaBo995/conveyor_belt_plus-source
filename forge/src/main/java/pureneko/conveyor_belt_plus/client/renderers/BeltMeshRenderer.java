package pureneko.conveyor_belt_plus.client.renderers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.util.BeltQuad;
import pureneko.conveyor_belt_plus.util.BeltMaterials;
import pureneko.conveyor_belt_plus.util.BeltVertex;

/** Samples a frame from a standalone sprite sheet, never the globally ticking block atlas. */
public final class BeltMeshRenderer {
    private static final Identifier[] TEXTURES = {
            texture(1), texture(2), texture(3)
    };

    private BeltMeshRenderer() {}

    private static Identifier texture(int tier) {
        return ConveyorBeltPlus.id("textures/block/" + BeltMaterials.textureName(tier) + ".png");
    }

    public static void draw(World world, BeltQuad[] quads, MatrixStack matrices,
                            VertexConsumerProvider consumers, int tier, int frame, int overlay,
                            int color, boolean preview) {
        if (quads == null) return;
        var texture = TEXTURES[net.minecraft.util.math.MathHelper.clamp(tier, 1, 3) - 1];
        var consumer = consumers.getBuffer(preview ? RenderLayer.getEntityTranslucent(texture)
                : RenderLayer.getEntityCutoutNoCull(texture));
        var camera = MinecraftClient.getInstance().getCameraEntity();
        var cameraPos = camera == null ? null : camera.getPos();
        matrices.push();
        matrices.translate(0, -2 / 16f + 0.08f, 0);
        var entry = matrices.peek();
        for (var quad : quads) {
            if (!preview && cameraPos != null && cameraPos.squaredDistanceTo(
                    quad.worldPos().getX(), quad.worldPos().getY(), quad.worldPos().getZ()) > 96 * 96) continue;
            int light = preview ? LightmapTextureManager.MAX_LIGHT_COORDINATE
                    : ChuteBeltRenderer.lightAt(world, quad.worldPos());
            vertex(consumer, entry, quad.a(), frame, light, overlay, color);
            vertex(consumer, entry, quad.b(), frame, light, overlay, color);
            vertex(consumer, entry, quad.c(), frame, light, overlay, color);
            vertex(consumer, entry, quad.d(), frame, light, overlay, color);
        }
        matrices.pop();
    }

    private static void vertex(VertexConsumer consumer, MatrixStack.Entry entry,
    BeltVertex vertex, int frame, int light, int overlay, int color) {
        consumer.vertex(entry.getPositionMatrix(), vertex.x(), vertex.y(), vertex.z())
                .color(color).texture(BeltMaterials.u(vertex.u()), BeltMaterials.v(vertex.v(), frame))
                .overlay(overlay).light(light).normal(entry.getNormalMatrix(), 0, 1, 0).next();
    }
}
