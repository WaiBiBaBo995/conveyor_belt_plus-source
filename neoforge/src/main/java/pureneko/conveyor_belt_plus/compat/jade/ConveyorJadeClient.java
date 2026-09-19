package pureneko.conveyor_belt_plus.compat.jade;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.registry.ItemContent;
import pureneko.conveyor_belt_plus.blocks.ChuteBlock;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity;
import pureneko.conveyor_belt_plus.blocks.ConveyorSplitterBlock;
import pureneko.conveyor_belt_plus.blocks.ConveyorSplitterBlockEntity;
import pureneko.conveyor_belt_plus.client.BeltPickupTarget;
import snownee.jade.api.*;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElementHelper;

/** All values reuse the normal BE/render synchronization. No Jade server data provider. */
public final class ConveyorJadeClient {
    private ConveyorJadeClient() {}
    public static final Identifier BELT = ConveyorBeltPlus.id("belt_contents");
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
            if (net.neoforged.fml.ModList.get().isLoaded("rtsbuilding")
                    && pureneko.conveyor_belt_plus.compat.rts.RtsClient.jadeHidden()) return accessor;
            var target = BeltPickupTarget.current();
            var client = MinecraftClient.getInstance();
            if (target == null || client.world == null || client.player == null) return accessor;
            var state = client.world.getBlockState(target.owner());
            if (!(state.getBlock() instanceof ChuteBlock) && !(state.getBlock() instanceof ConveyorSplitterBlock)) return accessor;
            return registration.blockAccessor().level(client.world).player(client.player)
                    .serverData(new net.minecraft.nbt.NbtCompound())
                    .blockState(state).blockEntity(() -> null)
                    .hit(new BlockHitResult(target.hit(), Direction.UP, target.owner(), false))
                    .fakeBlock(new ItemStack(ItemContent.beltForTier(target.tier())))
                    .serverConnected(false).build();
        });
    }

    public enum Contents implements IBlockComponentProvider {
        SPLITTER("splitter_contents"), FILTERS("chute_filters"), BELT_ITEM("belt_contents");
        private final Identifier uid;
        Contents(String path) { uid = ConveyorBeltPlus.id(path); }
        @Override public Identifier getUid() { return uid; }
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
                if (stack.isEmpty()) tooltip.add(Text.translatable("jade.conveyor_belt_plus.empty"));
                else itemLine(tooltip, stack);
            } else if (this == FILTERS && accessor.getBlockEntity() instanceof ChuteBlockEntity chute) {
                tooltip.add(Text.translatable(chute.isWhitelistMode() ? "jade.conveyor_belt_plus.whitelist" : "jade.conveyor_belt_plus.blacklist"));
                int icons = 0;
                for (int slot = 0; slot < chute.getFilterSlotCount(); slot++) {
                    var icon = chute.getFilter(slot);
                    if (icon.isEmpty()) continue;
                    var element = elements.item(icon, .75f, "");
                    if (icons++ % 9 == 0) tooltip.add(element);
                    else tooltip.append(element);
                }
            }
        }
        private static void itemLine(ITooltip tooltip, ItemStack stack) {
            if (stack.isEmpty()) return;
            var elements = IElementHelper.get();
            tooltip.add(elements.item(stack.copyWithCount(1), 0.75f, ""));
            tooltip.append(elements.text(Text.translatable("jade.conveyor_belt_plus.item_count",
                    stack.getName(), stack.getCount())).translate(new Vec2f(0f, 3f)));
        }
    }
}
