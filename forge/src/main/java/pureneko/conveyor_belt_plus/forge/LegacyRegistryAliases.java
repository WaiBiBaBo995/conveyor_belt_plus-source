package pureneko.conveyor_belt_plus.forge;

import net.minecraft.util.Identifier;
import net.minecraftforge.registries.MissingMappingsEvent;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;

/** Resolve legacy names when Forge loads an older 1.20.1 registry snapshot. */
public final class LegacyRegistryAliases {
    private LegacyRegistryAliases() {}
    public static void register(MissingMappingsEvent event) {
        remap(event, net.minecraft.registry.RegistryKeys.BLOCK);
        remap(event, net.minecraft.registry.RegistryKeys.ITEM);
        remap(event, net.minecraft.registry.RegistryKeys.BLOCK_ENTITY_TYPE);
    }
    private static <T> void remap(MissingMappingsEvent event, net.minecraft.registry.RegistryKey<net.minecraft.registry.Registry<T>> key) {
        for (var mapping : event.getMappings(key, "logisticsplus")) {
            var id = new Identifier(ConveyorBeltPlus.MOD_ID, mapping.getKey().getPath());
            var registry = net.minecraftforge.registries.RegistryManager.ACTIVE.getRegistry(key);
            if (registry != null && registry.containsKey(id)) mapping.remap(registry.getValue(id));
        }
    }
}
