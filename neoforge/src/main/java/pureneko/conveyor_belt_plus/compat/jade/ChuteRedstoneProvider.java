package pureneko.conveyor_belt_plus.compat.jade;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Reads the normal block-entity snapshot: no extra server queries or icon elements. */
public enum ChuteRedstoneProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = ConveyorBeltPlus.id("chute_redstone");

    @Override
    public ResourceLocation getUid() { return UID; }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        // A belt packet uses a fake accessor at its owning chute; it is not the chute itself.
        if (!accessor.isFakeBlock() && accessor.getBlockEntity() instanceof ChuteBlockEntity chute) {
            if (chute.getKind() == pureneko.conveyor_belt_plus.blocks.ChuteKind.UNIVERSAL) {
                for (boolean fluid : new boolean[]{false, true})
                    tooltip.add(Component.translatable(fluid ? "screen.conveyor_belt_plus.tab_fluids" : "screen.conveyor_belt_plus.tab_items")
                            .append(": ").append(Component.translatable(chute.getRedstoneMode(fluid).translationKey())));
            } else tooltip.add(Component.translatable(chute.getRedstoneMode().translationKey()));
        }
    }
}
