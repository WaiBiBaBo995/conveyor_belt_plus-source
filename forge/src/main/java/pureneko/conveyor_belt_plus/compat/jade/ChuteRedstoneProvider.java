package pureneko.conveyor_belt_plus.compat.jade;

import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Reads the normal block-entity snapshot: no extra server queries or icon elements. */
public enum ChuteRedstoneProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final Identifier UID = ConveyorBeltPlus.id("chute_redstone");

    @Override
    public Identifier getUid() { return UID; }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        // A belt packet uses a fake accessor at its owning chute; it is not the chute itself.
        if (!accessor.isFakeBlock() && accessor.getBlockEntity() instanceof ChuteBlockEntity chute)
            tooltip.add(Text.translatable(chute.getRedstoneMode().translationKey()));
    }
}
