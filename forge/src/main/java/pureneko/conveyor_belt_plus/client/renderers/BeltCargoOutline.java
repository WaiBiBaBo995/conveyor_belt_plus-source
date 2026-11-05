package pureneko.conveyor_belt_plus.client.renderers;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import pureneko.conveyor_belt_plus.client.BeltPickupTarget;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;

/** One selected model, private depth-tested mask, alpha silhouette and a small white halo.
 * Never touches vanilla's entity outline buffers. No target means no framebuffer/shader work. */
public final class BeltCargoOutline {
    private BeltCargoOutline() {}
    private static ShaderInstance maskShader, edgeShader;
    private static TextureTarget mask;
    private static MultiBufferSource.BufferSource buffers;
    private static final Map<RenderType, RenderType> TYPES = new IdentityHashMap<>();
    private static long renderedFrames;
    public static long renderedFrames() { return renderedFrames; }

    public static void shaders(RegisterShadersEvent event) {
        try {
        release();
        ChuteBeltRenderer.clearLightingCache();
        BeltOutlineRenderer.clear();
        event.registerShader(new ShaderInstance(event.getResourceProvider(), ConveyorBeltPlus.id("cargo_mask"),
                DefaultVertexFormat.NEW_ENTITY), value -> maskShader = value);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), ConveyorBeltPlus.id("cargo_edge"),
                DefaultVertexFormat.POSITION_TEX), value -> edgeShader = value);
        } catch (IOException error) { throw new java.io.UncheckedIOException("Cannot load conveyor cargo outline shaders", error); }
    }

    public static void release() {
        if (mask != null) { mask.destroyBuffers(); mask = null; }
        TYPES.clear();
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var client = Minecraft.getInstance();
        if (client.level == null) return;
        pureneko.conveyor_belt_plus.compat.rts.RtsClient.View remote = null;
        if (net.minecraftforge.fml.ModList.get().isLoaded("rtsbuilding"))
            remote = pureneko.conveyor_belt_plus.compat.rts.RtsClient.view();
        if (client.screen != null && remote == null) return;
        var projectionBuffers = client.renderBuffers().bufferSource();
        if (remote == null) BeltOutlineRenderer.renderPlannedBelt(client.level, event.getCamera(), event.getPoseStack(), projectionBuffers);
        else BeltOutlineRenderer.renderPlannedBelt(client.level, event.getCamera(), event.getPoseStack(), projectionBuffers,
                remote.stack(), remote.hit(), remote.facing(), net.minecraft.client.gui.screens.Screen.hasShiftDown());
        BeltOutlineRenderer.renderUpgrade(client.level, event.getCamera(), event.getPoseStack(), projectionBuffers);
        projectionBuffers.endBatch();
        var visual = BeltPickupTarget.selectedVisual();
        if (visual == null || !BeltPickupTarget.canHighlight() || maskShader == null || edgeShader == null) return;
        var main = client.getMainRenderTarget();
        if (mask == null || mask.width != main.width || mask.height != main.height) {
            release();
            mask = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            mask.setClearColor(0, 0, 0, 0);
        }
        if (buffers == null) buffers = MultiBufferSource.immediate(new BufferBuilder(4096));
        var previousShader = RenderSystem.getShader();
        boolean wasCulling = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        try {
            mask.clear(Minecraft.ON_OSX);
            mask.copyDepthFrom(main);
            mask.bindWrite(false);
            var pose = event.getPoseStack();
            pose.pushPose();
            try {
                var origin = visual.entity().getBlockPos();
                var camera = event.getCamera().getPosition();
                pose.translate(origin.getX() - camera.x, origin.getY() - camera.y, origin.getZ() - camera.z);
                MultiBufferSource silhouette = type -> buffers.getBuffer(TYPES.computeIfAbsent(type, MaskType::new));
                ChuteBeltRenderer.renderPacket(visual.entity(), visual.packet(), visual.progress(), visual.point(),
                        visual.data(), pose, silhouette, OverlayTexture.NO_OVERLAY, false);
                buffers.endBatch();
            } finally { pose.popPose(); }
            main.bindWrite(false);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(() -> edgeShader);
            edgeShader.setSampler("CargoMask", mask.getColorTextureId());
            edgeShader.setSampler("MaskDepth", mask.getDepthTextureId());
            // Outside the cargo silhouette this private depth texture still contains the copied scene.
            // Never sample main's attached depth while drawing into main (OpenGL feedback is undefined).
            edgeShader.setSampler("SceneDepth", mask.getDepthTextureId());
            edgeShader.safeGetUniform("TexelSize").set(1f / main.width, 1f / main.height);
            // Limit fragment work to the projected cargo envelope, even on a large window.
            scissor(visual, event, main);
            var vertices = Tesselator.getInstance().getBuilder();
            vertices.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            vertices.vertex(-1, -1, 0).uv(0, 0).endVertex();
            vertices.vertex(1, -1, 0).uv(1, 0).endVertex();
            vertices.vertex(1, 1, 0).uv(1, 1).endVertex();
            vertices.vertex(-1, 1, 0).uv(0, 1).endVertex();
            BufferUploader.drawWithShader(vertices.end());
            renderedFrames++;
        } finally {
            if (wasCulling) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.disableScissor();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            RenderSystem.setShader(() -> previousShader);
            main.bindWrite(false);
        }
    }

    private static void scissor(BeltPickupTarget.Visual visual, RenderLevelStageEvent event, RenderTarget main) {
        var camera = event.getCamera().getPosition();
        var centre = visual.point().subtract(camera).add(0, .08, 0);
        var transform = new Matrix4f(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose());
        float minX = 1, maxX = -1, minY = 1, maxY = -1;
        for (int i = 0; i < 8; i++) {
            var corner = new Vector4f((float)centre.x + ((i & 1) == 0 ? -.6f : .6f),
                    (float)centre.y + ((i & 2) == 0 ? -.6f : .6f),
                    (float)centre.z + ((i & 4) == 0 ? -.6f : .6f), 1).mul(transform);
            if (corner.w <= 0) return;
            minX = Math.min(minX, corner.x / corner.w); maxX = Math.max(maxX, corner.x / corner.w);
            minY = Math.min(minY, corner.y / corner.w); maxY = Math.max(maxY, corner.y / corner.w);
        }
        int x0 = Math.max(0, (int)((minX + 1) * .5 * main.width) - 4);
        int y0 = Math.max(0, (int)((minY + 1) * .5 * main.height) - 4);
        int x1 = Math.min(main.width, (int)((maxX + 1) * .5 * main.width) + 5);
        int y1 = Math.min(main.height, (int)((maxY + 1) * .5 * main.height) + 5);
        RenderSystem.enableScissor(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
    }

    private static final class MaskType extends RenderType {
        MaskType(RenderType original) {
            super("conveyor_cargo_mask", original.format(), original.mode(), original.bufferSize(), false, false,
                    () -> {
                        original.setupRenderState();
                        mask.bindWrite(false);
                        RenderSystem.setShader(() -> maskShader);
                        maskShader.safeGetUniform("Skip").set(original.toString().contains("glint") ? 1 : 0);
                        RenderSystem.enableDepthTest();
                        RenderSystem.depthFunc(GL11.GL_LEQUAL);
                        RenderSystem.depthMask(true);
                        RenderSystem.disableBlend();
                        RenderSystem.disableCull();
                        RenderSystem.colorMask(true, true, true, true);
                    }, () -> { original.clearRenderState(); Minecraft.getInstance().getMainRenderTarget().bindWrite(false); });
        }
    }
}
