package pureneko.conveyor_belt_plus.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public class ItemGroupContent {

    public static final DeferredRegister<CreativeModeTab> GROUPS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ConveyorBeltPlus.MOD_ID);

    public static final Supplier<CreativeModeTab> BELTS_GROUP = GROUPS.register("group",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemgroup.conveyor_belt_plus.items"))
                    .icon(() -> new ItemStack(ItemContent.BELT.get()))
                    .displayItems((context, entries) -> {
                        // Explicit order, independent of registration order on either loader.
                        for (var item : ItemContent.creativeOrder()) entries.accept(item);
                    }).build());
}
