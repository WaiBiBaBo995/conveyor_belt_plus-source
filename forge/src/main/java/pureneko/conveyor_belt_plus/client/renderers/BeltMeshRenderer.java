package pureneko.conveyor_belt_plus.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.util.BeltQuad;
import pureneko.conveyor_belt_plus.util.BeltMaterials;
import pureneko.conveyor_belt_plus.util.BeltVertex;

/** Samples a frame from a standalone sprite sheet, never the globally ticking block atlas. */
public final class BeltMeshRenderer {
    private static final ResourceLocation[] TEXTURES = {
            texture(1), texture(2), texture(3)
    };

    private BeltMeshRenderer() {}

    private static ResourceLocation texture(int tier) {
        return ConveyorBeltPlus.id("textures/block/" + BeltMaterials.textureName(tier) + ".png");
    }

    public static void draw(Level world, BeltQuad[] quads, PoseStack matrices,
                            MultiBufferSource consumers, int tier, int frame, int overlay,
                            int color, boolean preview) {
        if (quads == null) return;
        var texture = TEXTURES[net.minecraft.util.Mth.clamp(tier, 1, 3) - 1];
        var consumer = consumers.getBuffer(preview ? RenderType.entityTranslucent(texture)
                : RenderType.entityCutoutNoCull(texture));
        var camera = Minecraft.getInstance().getCameraEntity();
        var cameraPos = camera == null ? null : camera.position();
        matrices.pushPose();
        matrices.translate(0, -2 / 16f + 0.08f, 0);
        var entry = matrices.last();
        for (var quad : quads) {
            if (!preview && cameraPos != null && cameraPos.distanceToSqr(
                    quad.worldPos().getX(), quad.worldPos().getY(), quad.worldPos().getZ()) > 96 * 96) continue;
            int light = preview ? LightTexture.FULL_BRIGHT
                    : ChuteBeltRenderer.lightAt(world, quad.worldPos());
            vertex(consumer, entry, quad.a(), frame, light, overlay, color);
            vertex(consumer, entry, quad.b(), frame, light, overlay, color);
            vertex(consumer, entry, quad.c(), frame, light, overlay, color);
            vertex(consumer, entry, quad.d(), frame, light, overlay, color);
        }
        matrices.popPose();
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose entry,
    BeltVertex vertex, int frame, int light, int overlay, int color) {
        consumer.vertex(entry.pose(), vertex.x(), vertex.y(), vertex.z())
                .color(color).uv(BeltMaterials.u(vertex.u()), BeltMaterials.v(vertex.v(), frame))
                .overlayCoords(overlay).uv2(light).normal(entry.normal(), 0, 1, 0).endVertex();
    }
}
