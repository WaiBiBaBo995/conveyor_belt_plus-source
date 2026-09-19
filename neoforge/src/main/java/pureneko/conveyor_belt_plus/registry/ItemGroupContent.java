package pureneko.conveyor_belt_plus.registry;

import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;

public class ItemGroupContent {

    public static final DeferredRegister<ItemGroup> GROUPS = DeferredRegister.create(RegistryKeys.ITEM_GROUP, ConveyorBeltPlus.MOD_ID);

    public static final Supplier<ItemGroup> BELTS_GROUP = GROUPS.register("group",
            () -> ItemGroup.builder()
                    .displayName(Text.translatable("itemgroup.conveyor_belt_plus.items"))
                    .icon(() -> new ItemStack(ItemContent.BELT.get()))
                    .entries((context, entries) -> {
                        // Explicit order, independent of registration order on either loader.
                        for (var item : ItemContent.creativeOrder()) entries.add(item);
                    }).build());
}
