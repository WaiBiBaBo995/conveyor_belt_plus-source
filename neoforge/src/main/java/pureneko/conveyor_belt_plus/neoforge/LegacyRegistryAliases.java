package pureneko.conveyor_belt_plus.neoforge;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.IRegistryExtension;
import net.neoforged.neoforge.registries.RegisterEvent;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;

/** Read old names through NeoForge's registry aliases without registering duplicate blocks/items. */
public final class LegacyRegistryAliases {
    private LegacyRegistryAliases() {}
    public static void register(RegisterEvent event) {
        var registry = event.getRegistry();
        int added = 0;
        for (String prefix : java.util.List.of("", "advanced_", "ultimate_")) {
            var current = ConveyorBeltPlus.id(prefix + "item_chute");
            if (!registry.containsKey(current)) continue;
            for (String namespace : java.util.List.of(ConveyorBeltPlus.MOD_ID, "logisticsplus"))
                ((IRegistryExtension<?>) registry).addAlias(ResourceLocation.fromNamespaceAndPath(namespace, prefix + "chute"), current);
        }
        for (var id : java.util.List.copyOf(registry.keySet())) {
            if (!id.getNamespace().equals(ConveyorBeltPlus.MOD_ID)) continue;
            var oldId = ResourceLocation.fromNamespaceAndPath("logisticsplus", id.getPath());
            if (!registry.containsKey(oldId)) {
                ((IRegistryExtension<?>) registry).addAlias(oldId, id);
                if (registry.get(oldId) != registry.get(id)) throw new IllegalStateException("Legacy alias failed: " + oldId);
                added++;
            }
        }
        if (added > 0) ConveyorBeltPlus.LOGGER.info("Verified {} legacy registry aliases in {}", added, event.getRegistryKey().location());
    }
}
