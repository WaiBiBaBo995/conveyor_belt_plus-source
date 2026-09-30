package pureneko.conveyor_belt_plus.forge;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraftforge.registries.MissingMappingsEvent.Mapping;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;

/** Resolve legacy names when Forge loads an older 1.20.1 registry snapshot. */
public final class LegacyRegistryAliases {
    private LegacyRegistryAliases() {}
    public static void register(MissingMappingsEvent event) {
        remap(event, net.minecraft.core.registries.Registries.BLOCK);
        remap(event, net.minecraft.core.registries.Registries.ITEM);
        remap(event, net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE);
    }
    public static ResourceLocation currentId(ResourceLocation old, net.minecraft.resources.ResourceKey<?> key) {
        if (!old.getNamespace().equals("logisticsplus") && !old.getNamespace().equals(ConveyorBeltPlus.MOD_ID)) return old;
        String path = old.getPath();
        if (key.equals(net.minecraft.core.registries.Registries.BLOCK) || key.equals(net.minecraft.core.registries.Registries.ITEM))
            path = switch (path) {
                case "chute" -> "item_chute";
                case "advanced_chute" -> "advanced_item_chute";
                case "ultimate_chute" -> "ultimate_item_chute";
                default -> path;
            };
        return new ResourceLocation(ConveyorBeltPlus.MOD_ID, path);
    }
    private static <T> void remap(MissingMappingsEvent event, net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<T>> key) {
        for (String namespace : java.util.List.of("logisticsplus", ConveyorBeltPlus.MOD_ID))
        for (var mapping : event.getMappings(key, namespace)) {
            var id = currentId(mapping.getKey(), key);
            var registry = net.minecraftforge.registries.RegistryManager.ACTIVE.getRegistry(key);
            if (registry != null && registry.containsKey(id)) mapping.remap(registry.getValue(id));
        }
    }
}
