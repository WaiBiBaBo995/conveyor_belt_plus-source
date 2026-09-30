package pureneko.conveyor_belt_plus.compat.jade;

import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.registry.ItemContent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidStack;
import pureneko.conveyor_belt_plus.blocks.ChuteBlock;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity;
import pureneko.conveyor_belt_plus.blocks.ConveyorSplitterBlock;
import pureneko.conveyor_belt_plus.blocks.ConveyorSplitterBlockEntity;
import pureneko.conveyor_belt_plus.client.BeltPickupTarget;
import pureneko.conveyor_belt_plus.client.BeltPickupTarget.Target;
import pureneko.conveyor_belt_plus.filter.FilterRule;
import snownee.jade.api.*;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElementHelper;

/** All values reuse the normal BE/render synchronization. No Jade server data provider. */
public final class ConveyorJadeClient {
    private ConveyorJadeClient() {}
    public static final ResourceLocation BELT = ConveyorBeltPlus.id("belt_contents");
    public static void register(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Contents.SPLITTER, ConveyorSplitterBlock.class);
        registration.registerBlockComponent(Contents.FILTERS, ChuteBlock.class);
        registration.registerBlockComponent(ChuteRedstoneProvider.INSTANCE, ChuteBlock.class);
        registration.markAsClientFeature(ChuteRedstoneProvider.INSTANCE.getUid());
        registration.registerBlockComponent(Contents.BELT_ITEM, ChuteBlock.class);
        registration.registerBlockComponent(Contents.BELT_ITEM, ConveyorSplitterBlock.class);
        for (var provider : Contents.values()) registration.markAsClientFeature(provider.getUid());
        // RTS uses 1000; use a distinct later priority, independent of plugin load order.
        registration.addRayTraceCallback(1001, (hit, accessor, original) -> {
            if (!snownee.jade.api.config.IWailaConfig.get().getPlugin().get(BELT)) return accessor;
            if (net.minecraftforge.fml.ModList.get().isLoaded("rtsbuilding")
                    && pureneko.conveyor_belt_plus.compat.rts.RtsClient.jadeHidden()) return accessor;
            var target = BeltPickupTarget.current();
            var client = Minecraft.getInstance();
            if (target == null || client.level == null || client.player == null) return accessor;
            var state = client.level.getBlockState(target.owner());
            if (!(state.getBlock() instanceof ChuteBlock) && !(state.getBlock() instanceof ConveyorSplitterBlock)) return accessor;
            return registration.blockAccessor().level(client.level).player(client.player)
                    .serverData(new net.minecraft.nbt.CompoundTag())
                    .blockState(state).blockEntity(() -> null)
                    .hit(new BlockHitResult(target.hit(), Direction.UP, target.owner(), false))
                    .fakeBlock(new ItemStack(ItemContent.beltForTier(target.tier())))
                    .serverConnected(false).build();
        });
    }

    public enum Contents implements IBlockComponentProvider {
        SPLITTER("splitter_contents"), FILTERS("chute_filters"), BELT_ITEM("belt_contents");
        private final ResourceLocation uid;
        Contents(String path) { uid = ConveyorBeltPlus.id(path); }
        @Override public ResourceLocation getUid() { return uid; }
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            var elements = IElementHelper.get();
            if (accessor.isFakeBlock()) {
                if (this != BELT_ITEM) return;
                var target = BeltPickupTarget.current();
                if (target != null && target.owner().equals(accessor.getPosition())) itemLine(tooltip, target.stack());
                return;
            }
            if (this == SPLITTER && accessor.getBlockEntity() instanceof ConveyorSplitterBlockEntity splitter) {
                var stack = splitter.getCachedItemSnapshot();
                if (stack.isEmpty()) tooltip.add(Component.translatable("jade.conveyor_belt_plus.empty"));
                else itemLine(tooltip, stack);
            } else if (this == FILTERS && accessor.getBlockEntity() instanceof ChuteBlockEntity chute) {
                for (boolean fluid : new boolean[]{false, true}) {
                    if (!chute.getKind().supports(fluid)) continue;
                    tooltip.add(Component.translatable(fluid ? "screen.conveyor_belt_plus.tab_fluids" : "screen.conveyor_belt_plus.tab_items")
                            .append(": ").append(Component.translatable(chute.isWhitelistMode(fluid)
                                    ? "jade.conveyor_belt_plus.whitelist" : "jade.conveyor_belt_plus.blacklist")));
                    int icons = 0;
                    for (int slot = 0; slot < chute.getFilterSlotCount(fluid); slot++) {
                        var rule = chute.getRule(fluid, slot);
                        if (rule.isEmpty()) continue;
                        snownee.jade.api.ui.IElement element;
                        if (fluid) {
                            var preview = rule.fluidIcon();
                            if (preview.isEmpty()) continue;
                            element = fluidIcon(preview);
                        } else element = elements.item(rule.icon(), .75f, "");
                        if (icons++ % 9 == 0) tooltip.add(element);
                        else tooltip.append(element);
                    }
                }
            }
        }
        /** Full fluid swatch, independent of bucket availability and transported amount. */
        private static snownee.jade.api.ui.IElement fluidIcon(net.minecraftforge.fluids.FluidStack fluid) {
            return IElementHelper.get().fluid(snownee.jade.api.fluid.JadeFluidObject.of(fluid.getFluid(),
                    snownee.jade.api.fluid.JadeFluidObject.bucketVolume(), fluid.getTag() == null ? null : fluid.getTag().copy()))
                    .size(new net.minecraft.world.phys.Vec2(12, 12));
        }
        private static void itemLine(ITooltip tooltip, ItemStack stack) {
            if (stack.isEmpty()) return;
            if (pureneko.conveyor_belt_plus.util.FluidPackets.isPacket(stack)) {
                var fluid = pureneko.conveyor_belt_plus.util.FluidPackets.get(stack);
                tooltip.add(fluidIcon(fluid));
                tooltip.append(Component.translatable("jade.conveyor_belt_plus.fluid_amount", fluid.getDisplayName(), fluid.getAmount()));
                return;
            }
            tooltip.add(IElementHelper.get().item(stack.copyWithCount(1), .75f, ""));
            tooltip.append(Component.translatable("jade.conveyor_belt_plus.item_count", stack.getHoverName(), stack.getCount()));
        }
    }
}
