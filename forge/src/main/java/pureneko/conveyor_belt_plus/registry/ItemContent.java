package pureneko.conveyor_belt_plus.registry;

import pureneko.conveyor_belt_plus.items.BeltItem;
import pureneko.conveyor_belt_plus.items.TooltipBlockItem;
import net.minecraftforge.registries.DeferredRegister;
import java.util.function.Supplier;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import pureneko.conveyor_belt_plus.util.BeltTiers;

public class ItemContent {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(RegistryKeys.ITEM, ConveyorBeltPlus.MOD_ID);

    public static final Supplier<Item> CHUTE = ITEMS.register("chute", () -> new TooltipBlockItem(BlockContent.CHUTE_BLOCK.get(), new Item.Settings()));
    public static final Supplier<Item> CONVEYOR_SUPPORT = ITEMS.register("conveyor_support", () -> new TooltipBlockItem(BlockContent.CONVEYOR_SUPPORT_BLOCK.get(), new Item.Settings()));
    public static final Supplier<Item> BELT = ITEMS.register("belt", () -> new BeltItem(new Item.Settings()));
    public static final Supplier<Item> ADVANCED_BELT = ITEMS.register("advanced_belt", () -> new BeltItem(new Item.Settings(), BeltTiers.ADVANCED));
    public static final Supplier<Item> ADVANCED_CHUTE = ITEMS.register("advanced_chute", () -> new TooltipBlockItem(BlockContent.ADVANCED_CHUTE.get(), new Item.Settings()));
    public static final Supplier<Item> ULTIMATE_CHUTE = ITEMS.register("ultimate_chute", () -> new TooltipBlockItem(BlockContent.ULTIMATE_CHUTE.get(), new Item.Settings()));
    public static final Supplier<Item> ULTIMATE_BELT = ITEMS.register("ultimate_belt", () -> new BeltItem(new Item.Settings(), BeltTiers.ULTIMATE));

    public static java.util.List<Item> creativeOrder() {
        return java.util.List.of(BELT.get(), ADVANCED_BELT.get(), ULTIMATE_BELT.get(),
                CHUTE.get(), ADVANCED_CHUTE.get(), ULTIMATE_CHUTE.get(),
                CONVEYOR_SUPPORT.get(), SPLITTER.get());
    }

    public static Item beltForTier(int tier) {
        return switch (BeltTiers.normalize(tier)) {
            case BeltTiers.ULTIMATE -> ULTIMATE_BELT.get();
            case BeltTiers.ADVANCED -> ADVANCED_BELT.get();
            default -> BELT.get();
        };
    }

    public static net.minecraft.item.ItemStack beltStackForTier(int tier) {
        return new net.minecraft.item.ItemStack(beltForTier(tier));
    }

    public static final Supplier<Item> SPLITTER = ITEMS.register("splitter", () -> new TooltipBlockItem(BlockContent.SPLITTER.get(), new Item.Settings()));

}
