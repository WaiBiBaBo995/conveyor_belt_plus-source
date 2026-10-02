package pureneko.conveyor_belt_plus.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/** Six fluid faces inside the vanilla glass model; the fluid's own atlas sprite/tint supports mod fluids. */
public final class FluidPacketRenderer {
    private FluidPacketRenderer() {}
    private static final float[][][] FACES = {
            {{-1,1,-1},{-1,1,1},{1,1,1},{1,1,-1}},
            {{-1,-1,1},{-1,-1,-1},{1,-1,-1},{1,-1,1}},
            {{-1,-1,-1},{-1,1,-1},{1,1,-1},{1,-1,-1}},
            {{1,-1,1},{1,1,1},{-1,1,1},{-1,-1,1}},
            {{-1,-1,1},{-1,1,1},{-1,1,-1},{-1,-1,-1}},
            {{1,-1,-1},{1,1,-1},{1,1,1},{1,-1,1}}
    };
    private static final int[][] NORMALS = {{0,1,0},{0,-1,0},{0,0,-1},{0,0,1},{-1,0,0},{1,0,0}};

    public static void render(FluidStack fluid, Level world, PoseStack matrices,
                              MultiBufferSource consumers, int light, int overlay) {
        if (fluid.isEmpty()) return;
        matrices.pushPose();
        matrices.scale(.36f, .36f, .36f);
        renderContents(fluid, world, matrices, consumers, light, overlay);
        matrices.popPose();
    }

    /** Unit-size glass parcel centred at the origin, shared by belts and recovered items. */
    public static void renderContents(FluidStack fluid, Level world, PoseStack matrices,
                                      MultiBufferSource consumers, int light, int overlay) {
        var client = Minecraft.getInstance();
        var extension = fluid.isEmpty() ? null : IClientFluidTypeExtensions.of(fluid.getFluid());
        var texture = extension == null ? null : extension.getStillTexture(fluid);
        if (texture != null) {
            var sprite = client.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
            int color = extension.getTintColor(fluid);
            var vertices = consumers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
            var entry = matrices.last();
            int fluidLight = Math.max(light & 0xFFFF, fluid.getFluidType().getLightLevel(fluid) << 4) | (light & 0xFFFF0000);
            for (int face = 0; face < FACES.length; face++) for (int i = 0; i < 4; i++) {
                var point = FACES[face][i];
                vertices.addVertex(entry.pose(), point[0] * .43f, point[1] * .43f, point[2] * .43f)
                        .setColor(color).setUv(i < 2 ? sprite.getU0() : sprite.getU1(),
                                i == 0 || i == 3 ? sprite.getV1() : sprite.getV0())
                        .setOverlay(overlay).setLight(fluidLight).setNormal(entry, NORMALS[face][0], NORMALS[face][1], NORMALS[face][2]);
            }
        }
        client.getItemRenderer().renderStatic(new ItemStack(Items.GLASS), ItemDisplayContext.NONE,
                light, overlay, matrices, consumers, world, 0);
    }
}
