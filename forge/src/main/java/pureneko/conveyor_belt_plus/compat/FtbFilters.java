package pureneko.conveyor_belt_plus.compat;

import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import org.jetbrains.annotations.Nullable;
import java.lang.reflect.Method;
import net.minecraft.world.item.ItemStack;

/** Optional integration; no FTB or Architectury classes are linked by this mod. */
public final class FtbFilters {
    private static boolean attempted;
    private static Object api;
    private static Method isFilter, matches;
    private FtbFilters() {}
    public static @Nullable Boolean matches(ItemStack marker, ItemStack candidate) {
        try {
            if (!attempted) {
                attempted = true;
                var type = Class.forName("dev.ftb.mods.ftbfiltersystem.api.FTBFilterSystemAPI");
                api = type.getMethod("api").invoke(null);
                isFilter = type.getMethod("isFilterItem", ItemStack.class);
                matches = type.getMethod("doesFilterMatch", ItemStack.class, ItemStack.class);
            }
            if (api != null && (boolean) isFilter.invoke(api, marker))
                return (boolean) matches.invoke(api, marker, candidate);
        } catch (ReflectiveOperationException | LinkageError ex) {
            api = null;
            ConveyorBeltPlus.LOGGER.warn("Optional FTB Filters integration unavailable", ex);
        }
        return null;
    }
}
