package pureneko.conveyor_belt_plus.blocks;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import pureneko.conveyor_belt_plus.filter.FilterRules;
import pureneko.conveyor_belt_plus.util.ExtractionSide;
import pureneko.conveyor_belt_plus.util.RedstoneControl;

/** One independent set per transport domain, including pulse state and round-robin cursor. */
final class ChuteSettings {
    final FilterRules filters = new FilterRules();
    final RedstoneControl redstone = new RedstoneControl();
    boolean whitelist;
    int sides = ExtractionSide.DEFAULT_MASK;
    int nextSide;

    void write(CompoundTag tag, HolderLookup.Provider lookup) {
        filters.write(tag, lookup);
        tag.putBoolean("whitelistMode", whitelist);
        tag.putInt("extractionSides", sides);
        tag.putInt("nextExtractionSide", nextSide);
        tag.putInt("redstoneMode", redstone.mode().ordinal());
        tag.putBoolean("redstonePowered", redstone.powered());
        tag.putBoolean("redstonePulsePending", redstone.pending());
    }

    void read(CompoundTag tag, HolderLookup.Provider lookup) {
        filters.read(tag, lookup);
        whitelist = tag.getBoolean("whitelistMode");
        if (!tag.contains("filterRules") && tag.contains("filter") && !tag.contains("whitelistMode")) whitelist = true;
        sides = tag.contains("extractionSides") ? ExtractionSide.savedMask(tag.getInt("extractionSides")) : ExtractionSide.DEFAULT_MASK;
        nextSide = Math.floorMod(tag.getInt("nextExtractionSide"), 6);
        redstone.restore(RedstoneControl.Mode.fromId(tag.getInt("redstoneMode")),
                tag.getBoolean("redstonePowered"), tag.getBoolean("redstonePulsePending"));
    }
}
