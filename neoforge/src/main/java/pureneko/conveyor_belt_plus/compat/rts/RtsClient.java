package pureneko.conveyor_belt_plus.compat.rts;

import com.mojang.blaze3d.platform.Window;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import pureneko.conveyor_belt_plus.client.BeltPickupTarget;
import pureneko.conveyor_belt_plus.client.renderers.BeltOutlineRenderer;
import pureneko.conveyor_belt_plus.items.BeltItem;
import pureneko.conveyor_belt_plus.network.RtsNetworking;

/** Uses RTS's public cursor/selection queries; no reflection or screen mixins. */
public final class RtsClient {
    private RtsClient() {}
    private static ItemStack draft = ItemStack.EMPTY;
    private static boolean wasBuilder;
    public record View(ItemStack stack, BlockHitResult hit, Vec3 origin, Vec3 direction, Direction facing, boolean emptyHand) {}
    public static void setDraft(ItemStack stack) { draft = stack.copy(); }
    public static boolean jadeHidden() {
        return Minecraft.getInstance().screen instanceof BuilderScreen
                && com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore.isJadePanelHidden();
    }

    public static void tick() {
        var client = Minecraft.getInstance();
        boolean builder = client.screen instanceof BuilderScreen;
        if (client.level == null) { draft = ItemStack.EMPTY; wasBuilder = false; return; }
        if (builder != wasBuilder) {
            draft = ItemStack.EMPTY;
            PacketDistributor.sendToServer(new RtsNetworking.DraftRequest(!builder));
        }
        wasBuilder = builder;
    }
    public static View view() {
        var client = Minecraft.getInstance();
        if (!(client.screen instanceof BuilderScreen screen) || client.player == null) return null;
        if (screen.isGuideOpen() || screen.isGearMenuOpen() || screen.isCraftQuantityDialogOpen()
                || screen.isRangeCullingManagementActive() || screen.getPendingGuiBindSlot() >= 0
                || screen.isBlueprintPlacementModeLocked()) return null;
        var window = client.getWindow();
        var viewport = RtsUiViewport.from(window.getScreenWidth(), window.getScreenHeight(), window.getGuiScaledWidth(),
                window.getGuiScaledHeight(), screen.getRtsGuiScale(), client.mouseHandler.xpos(), client.mouseHandler.ypos());
        if (viewport == null) return null;
        int previousWidth = screen.width, previousHeight = screen.height;
        try {
            // These public queries recompute the bottom panel from screen dimensions.
            // Mirror RTS's scoped virtual viewport; never resize/init the actual screen.
            screen.width = viewport.width();
            screen.height = viewport.height();
            if (!screen.isWorldArea(viewport.mouseX(), viewport.mouseY())
                    || screen.isMouseOverFloatingWindow(viewport.mouseX(), viewport.mouseY())) return null;
        } finally {
            screen.width = previousWidth;
            screen.height = previousHeight;
        }
        var controller = screen.uiController();
        String mode = controller.getMode().name();
        if (!mode.equals("INTERACT")) return null;
        if (controller.hasSelectedFluid()) return null;
        var selected = controller.hasSelectedItem() ? controller.getSelectedItemPreview()
                : controller.isEmptyHandSelected() ? ItemStack.EMPTY : client.player.getMainHandItem();
        var stack = selected.copy();
        if (stack.getItem() instanceof BeltItem) {
            RtsBeltDrafts.clearSelection(stack);
            if (draft.is(stack.getItem())) RtsBeltDrafts.copySelection(draft, stack);
        }
        var direction = screen.computeCursorRayDirection().normalize();
        var facing = Direction.fromYRot(Math.toDegrees(Math.atan2(-direction.x, direction.z)));
        return new View(stack, screen.pickBlockHit(), screen.currentRayOrigin(), direction, facing, selected.isEmpty());
    }
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var view = view();
        var client = Minecraft.getInstance();
        if (view == null || !(view.stack.getItem() instanceof BeltItem) || view.hit == null
                || view.hit.getType() != HitResult.Type.BLOCK) return;
        var consumers = client.renderBuffers().bufferSource();
        BeltOutlineRenderer.renderPlannedBelt(client.level, event.getCamera(), event.getPoseStack(), consumers,
                view.stack, view.hit, view.facing);
        consumers.endBatch();
    }
    public static void click(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 1) return;
        var view = view();
        if (view == null) return;
        if (Screen.hasShiftDown() && view.stack.getItem() instanceof BeltItem
                && (view.hit == null || view.hit.getType() == HitResult.Type.MISS)) {
            PacketDistributor.sendToServer(new RtsNetworking.DraftRequest(true));
            event.setCanceled(true);
        } else if (BeltPickupTarget.takeRemote(view.origin, view.direction, view.stack)
                || BeltPickupTarget.putRemote(view.origin, view.direction, view.stack)) {
            event.setCanceled(true);
        }
    }
}
