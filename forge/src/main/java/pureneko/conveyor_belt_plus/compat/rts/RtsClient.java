package pureneko.conveyor_belt_plus.compat.rts;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ScreenEvent;
import pureneko.conveyor_belt_plus.network.NetworkChannel;
import pureneko.conveyor_belt_plus.client.BeltPickupTarget;
import pureneko.conveyor_belt_plus.items.BeltItem;
import pureneko.conveyor_belt_plus.network.RtsNetworking;

/** Uses RTS cursor queries and its native right-button gesture for completing a belt draft. */
public final class RtsClient {
    private RtsClient() {}
    private static ItemStack draft = ItemStack.EMPTY;
    private static boolean wasBuilder;
    private record PendingEndpoint(BuilderScreen screen, net.minecraft.client.multiplayer.ClientLevel world, ItemStack stack) {}
    private static PendingEndpoint pendingEndpoint;
    // RTS exposes the gesture API but no getter for the per-screen handler. Resolve once, only when needed.
    private static final class GestureAccess {
        static final java.lang.reflect.Field FIELD = find();
        private static java.lang.reflect.Field find() {
            try {
                var field = BuilderScreen.class.getSuperclass().getDeclaredField("cameraInput");
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException | RuntimeException error) {
                pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus.LOGGER.warn("RTS camera gesture access unavailable; keeping immediate endpoint interaction", error);
                return null;
            }
        }
    }
    public record View(ItemStack stack, BlockHitResult hit, Vec3 origin, Vec3 direction, Direction facing, boolean emptyHand, boolean storage) {}
    public static void setDraft(ItemStack stack) { draft = stack.copy(); }
    public static boolean jadeHidden() {
        return Minecraft.getInstance().screen instanceof BuilderScreen
                && com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore.isJadePanelHidden();
    }

    public static void tick() {
        var client = Minecraft.getInstance();
        boolean builder = client.screen instanceof BuilderScreen;
        if (pendingEndpoint != null && (client.screen != pendingEndpoint.screen || client.level != pendingEndpoint.world))
            pendingEndpoint = null;
        if (client.level == null) { draft = ItemStack.EMPTY; wasBuilder = false; return; }
        if (builder != wasBuilder) {
            draft = ItemStack.EMPTY;
            NetworkChannel.sendToServer(new RtsNetworking.DraftRequest(!builder));
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
        var hit = screen.pickBlockHit();
        if(hit == null) {
            var point = screen.currentRayOrigin().add(direction.scale(128));
            hit = BlockHitResult.miss(point,facing.getOpposite(),net.minecraft.core.BlockPos.containing(point));
        }
        return new View(stack, hit, screen.currentRayOrigin(), direction, facing, selected.isEmpty(), controller.hasSelectedItem());
    }
    public static void click(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 1) return;
        var view = view();
        if (view == null) return;
        pendingEndpoint = null;
        if (secondEndpoint(view) && GestureAccess.FIELD != null) {
            pendingEndpoint = new PendingEndpoint((BuilderScreen) event.getScreen(), Minecraft.getInstance().level, view.stack.copy());
            return; // Native RTS must see the press and all drag events to move the camera.
        }
        if (act(view)) event.setCanceled(true);
    }

    private static boolean secondEndpoint(View view) {
        if (!(view.stack.getItem() instanceof BeltItem)
                || pureneko.conveyor_belt_plus.registry.ComponentContent.BELT_START.get(view.stack) == null
                || pureneko.conveyor_belt_plus.registry.ComponentContent.BELT_DIR.get(view.stack) == null) return false;
        if (Screen.hasShiftDown() && BeltPickupTarget.currentUpgrade() != null) return false;
        if (view.hit.getType() != HitResult.Type.BLOCK) return !Screen.hasShiftDown();
        var world = Minecraft.getInstance().level;
        var pos = view.hit.getBlockPos();
        if (world.getBlockState(pos).is(pureneko.conveyor_belt_plus.registry.BlockContent.CONVEYOR_SUPPORT_BLOCK.get())) return false;
        var node = pureneko.conveyor_belt_plus.blocks.ConveyorNodeUtil.get(world, pos);
        var port = node instanceof pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity chute ? chute.getOwnFacing()
                : view.hit.getDirection().getAxis().isVertical() ? view.facing.getOpposite() : view.hit.getDirection();
        return !Screen.hasShiftDown() || node == null || node.outgoingBeltTier(port) <= 0;
    }

    public static void release(ScreenEvent.MouseButtonReleased.Pre event) {
        if (event.getButton() != 1 || pendingEndpoint == null) return;
        var pending = pendingEndpoint;
        pendingEndpoint = null;
        if (event.getScreen() != pending.screen || Minecraft.getInstance().level != pending.world) return;
        var client = Minecraft.getInstance();
        var window = client.getWindow();
        var viewport = RtsUiViewport.from(window.getScreenWidth(), window.getScreenHeight(), window.getGuiScaledWidth(),
                window.getGuiScaledHeight(), pending.screen.getRtsGuiScale(), client.mouseHandler.xpos(), client.mouseHandler.ypos());
        int width = pending.screen.width, height = pending.screen.height;
        boolean clicked;
        try {
            var camera = (com.rtsbuilding.rtsbuilding.client.screen.input.CameraInputHandler) GestureAccess.FIELD.get(pending.screen);
            if (viewport != null) { pending.screen.width = viewport.width(); pending.screen.height = viewport.height(); }
            clicked = camera.endRightPress(viewport == null ? -1 : viewport.mouseX(), viewport == null ? -1 : viewport.mouseY(), 1);
        } catch (IllegalAccessException error) {
            pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus.LOGGER.error("Cannot finish RTS endpoint gesture", error);
            return;
        } finally { pending.screen.width = width; pending.screen.height = height; }
        // Leave release uncancelled: RTS still performs its other release cleanup, but its primary gesture is now ended.
        var view = clicked ? view() : null;
        if (view != null && secondEndpoint(view) && view.hit.getType() == HitResult.Type.BLOCK
                && ItemStack.isSameItemSameTags(pending.stack, view.stack)) act(view);
    }

    private static boolean act(View view) {
        var upgrade = BeltPickupTarget.currentUpgrade();
        if (Screen.hasShiftDown() && upgrade != null) {
            NetworkChannel.sendToServer(new pureneko.conveyor_belt_plus.network.RtsActionNetworking.Action(upgrade.owner(),upgrade.port(),
                    view.origin,view.direction,Vec3.ZERO,view.stack.copyWithCount(1),true,true,view.storage));
            return true;
        } else if (pureneko.conveyor_belt_plus.compat.rts.RtsActions.supported(view.stack)
                && view.hit != null && view.hit.getType() == HitResult.Type.BLOCK) {
            NetworkChannel.sendToServer(new pureneko.conveyor_belt_plus.network.RtsActionNetworking.Action(view.hit.getBlockPos(),view.hit.getDirection(),
                    view.origin,view.direction,view.hit.getLocation(),view.stack.copyWithCount(1),Screen.hasShiftDown(),false,view.storage));
            return true;
        } else if (Screen.hasShiftDown() && view.stack.getItem() instanceof BeltItem
                && (view.hit == null || view.hit.getType() == HitResult.Type.MISS)) {
            NetworkChannel.sendToServer(new RtsNetworking.DraftRequest(true));
            return true;
        } else if (BeltPickupTarget.takeRemote(view.origin, view.direction, view.stack)
                || BeltPickupTarget.putRemote(view.origin, view.direction, view.stack)) {
            return true;
        }
        return false;
    }
}
