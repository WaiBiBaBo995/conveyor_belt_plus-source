package pureneko.conveyor_belt_plus.compat.rts;

import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import pureneko.conveyor_belt_plus.blocks.*;
import pureneko.conveyor_belt_plus.items.BeltItem;
import java.util.ArrayList;

/** RTS modifier and material transaction. Only actual server stacks reach item interaction code. */
public final class RtsActions {
    private RtsActions() {}
    private static final class HandContext {
        ItemStack stack;
        HandContext(ItemStack stack) { this.stack = stack; }
    }
    private static final ThreadLocal<HandContext> REAL_HAND = new ThreadLocal<>();
    public static boolean supported(ItemStack stack) {
        return stack.getItem() instanceof BeltItem || stack.getItem() instanceof BlockItem block && block.getBlock() instanceof ChuteBlock;
    }
    private static boolean matches(ItemStack actual, ItemStack selected) {
        if (actual.isEmpty()) return false;
        actual=actual.copy();selected=selected.copy();
        RtsBeltDrafts.clearSelection(actual);RtsBeltDrafts.clearSelection(selected);
        return ItemStack.isSameItemSameComponents(actual,selected);
    }
    public static void refund(ServerPlayer player, ItemStack returned) {
        var temporary=player.getMainHandItem();
        var real=REAL_HAND.get();
        if(real!=null)player.setItemInHand(InteractionHand.MAIN_HAND,real.stack);
        try {
        var services=ServiceRegistry.getInstance();
        var session=services.session().getIfPresent(player);
        if(session!=null) {
            var linked=RtsLinkedStorageResolver.resolveLinkedHandlers(player,session);
            RtsTransferInserter.refundToLinked(RtsLinkedStorageResolver.itemHandlersForInsert(linked),player,returned);
            services.serviceOp().markDirty(player,session);
        } else if(!player.getInventory().add(returned))player.drop(returned,false);
        } finally {
            if(real!=null) {
                // Inventory insertion can replace an empty hand slot. Preserve that returned stack.
                real.stack=player.getMainHandItem();
                player.setItemInHand(InteractionHand.MAIN_HAND,temporary);
            }
        }
    }
    public static boolean use(ServerPlayer player, BlockPos owner, Direction face, Vec3 origin, Vec3 direction,
            Vec3 hit, ItemStack selected, boolean shift, boolean body, boolean storage) {
        var world=player.serverLevel();
        if(!supported(selected) || !RtsCompat.validRay(player,origin,direction) || !player.isAlive() || player.isSpectator()
                || !player.getAbilities().mayBuild || player.containerMenu!=player.inventoryMenu || !world.hasChunkAt(owner)
                || !RtsCompat.canBuild(player,owner,false))return false;
        if(body && (!shift || !(selected.getItem() instanceof BeltItem)))return false;
        if(!body) {
            if(!Double.isFinite(hit.x) || !Double.isFinite(hit.y) || !Double.isFinite(hit.z)
                    || !new net.minecraft.world.phys.AABB(owner).inflate(.26).contains(hit)
                    || hit.distanceToSqr(origin)>128*128 || hit.subtract(origin).normalize().dot(direction)<.9999
                    || !BeltPickup.unobstructed(player,origin,hit)
                    || !RtsCompat.canBuild(player,owner.relative(face),true))return false;
            var blockRay=world.clip(new net.minecraft.world.level.ClipContext(origin,hit.add(direction.scale(.02)),
                    net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
            if(blockRay.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK || !blockRay.getBlockPos().equals(owner)
                    || blockRay.getLocation().distanceToSqr(hit)>.01 || blockRay.getDirection()!=face)return false;
        }
        var services=ServiceRegistry.getInstance();
        var session=services.session().getIfPresent(player);
        var held=player.getMainHandItem();
        if(!storage && matches(held,selected))return execute(player,held,owner,face,origin,direction,hit,shift,body);
        if(player.isCreative()) {
            var creative=selected.copyWithCount(1);RtsBeltDrafts.clearSelection(creative);
            return execute(player,creative,owner,face,origin,direction,hit,shift,body);
        }
        if(session==null)return false;
        var linked=RtsLinkedStorageResolver.resolveLinkedHandlers(player,session);
        var sources=new ArrayList<IItemHandler>(RtsLinkedStorageResolver.itemHandlersForExtract(linked));
        if(RtsStoragePageBuilder.shouldIncludePlayerMainInventoryInStorageView(player,session))
            sources.add(new PlayerMainInvWrapper(player.getInventory()));
        for(var source:sources)for(int slot=0;slot<source.getSlots();slot++) {
            if(!matches(source.getStackInSlot(slot),selected) || !matches(source.extractItem(slot,1,true),selected))continue;
            var actual=source.extractItem(slot,1,false);
            if(actual.isEmpty())continue;
            try {
                return matches(actual,selected) && execute(player,actual,owner,face,origin,direction,hit,shift,body);
            } finally {
                if(!actual.isEmpty()) {
                    var remainder=source.insertItem(slot,actual,false);
                    if(!remainder.isEmpty())refund(player,remainder);
                }
                services.serviceOp().markDirty(player,session);
                BeltInsertion.syncInventory(player);
            }
        }
        return false;
    }
    private static boolean execute(ServerPlayer player, ItemStack offered, BlockPos pos, Direction face,
            Vec3 origin, Vec3 direction, Vec3 hit, boolean shift, boolean body) {
        var held=player.getMainHandItem();boolean wasShift=player.isShiftKeyDown();float yaw=player.getYRot();
        var context=new HandContext(held);
        REAL_HAND.set(context);
        player.setItemInHand(InteractionHand.MAIN_HAND,offered);player.setShiftKeyDown(shift);
        player.setYRot((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));
        try {
            if(body)return UpgradeInteractions.beltAlongRay(player,pos,face,origin,origin.add(direction.scale(128)),true);
            if(!player.level().mayInteract(player,pos))return false;
            var blockHit=new BlockHitResult(hit,face,pos,false);
            var event=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,blockHit);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
            if(event.isCanceled() || event.getUseBlock() == TriState.FALSE || event.getUseItem() == TriState.FALSE)return false;
            var result=offered.getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,blockHit));
            return result.consumesAction();
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND,context.stack);player.setShiftKeyDown(wasShift);player.setYRot(yaw);
            REAL_HAND.remove();
            BeltInsertion.syncInventory(player);
        }
    }
}
