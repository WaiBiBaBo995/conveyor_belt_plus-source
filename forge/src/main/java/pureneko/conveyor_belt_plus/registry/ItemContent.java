package pureneko.conveyor_belt_plus.registry;

import pureneko.conveyor_belt_plus.items.BeltItem;
import pureneko.conveyor_belt_plus.items.TooltipBlockItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import java.util.function.Supplier;
import pureneko.conveyor_belt_plus.util.BeltTiers;

public class ItemContent {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, ConveyorBeltPlus.MOD_ID);

    public static final Supplier<Item> CHUTE = ITEMS.register("item_chute", () -> new TooltipBlockItem(BlockContent.CHUTE_BLOCK.get(), new Item.Properties()));
    public static final Supplier<Item> CONVEYOR_SUPPORT = ITEMS.register("conveyor_support", () -> new TooltipBlockItem(BlockContent.CONVEYOR_SUPPORT_BLOCK.get(), new Item.Properties()));
    public static final Supplier<Item> BELT = ITEMS.register("belt", () -> new BeltItem(new Item.Properties()));
    public static final Supplier<Item> ADVANCED_BELT = ITEMS.register("advanced_belt", () -> new BeltItem(new Item.Properties(), BeltTiers.ADVANCED));
    public static final Supplier<Item> ADVANCED_CHUTE = ITEMS.register("advanced_item_chute", () -> new TooltipBlockItem(BlockContent.ADVANCED_CHUTE.get(), new Item.Properties()));
    public static final Supplier<Item> ULTIMATE_CHUTE = ITEMS.register("ultimate_item_chute", () -> new TooltipBlockItem(BlockContent.ULTIMATE_CHUTE.get(), new Item.Properties()));
    public static final Supplier<Item> ULTIMATE_BELT = ITEMS.register("ultimate_belt", () -> new BeltItem(new Item.Properties(), BeltTiers.ULTIMATE));

    public static final Supplier<Item> FLUID_CHUTE = ITEMS.register("fluid_chute", () -> new TooltipBlockItem(BlockContent.FLUID_CHUTE.get(), new Item.Properties()));
    public static final Supplier<Item> ADVANCED_FLUID_CHUTE = ITEMS.register("advanced_fluid_chute", () -> new TooltipBlockItem(BlockContent.ADVANCED_FLUID_CHUTE.get(), new Item.Properties()));
    public static final Supplier<Item> ULTIMATE_FLUID_CHUTE = ITEMS.register("ultimate_fluid_chute", () -> new TooltipBlockItem(BlockContent.ULTIMATE_FLUID_CHUTE.get(), new Item.Properties()));
    public static final Supplier<Item> UNIVERSAL_CHUTE = ITEMS.register("universal_chute", () -> new TooltipBlockItem(BlockContent.UNIVERSAL_CHUTE.get(), new Item.Properties()));
    public static final Supplier<Item> ADVANCED_UNIVERSAL_CHUTE = ITEMS.register("advanced_universal_chute", () -> new TooltipBlockItem(BlockContent.ADVANCED_UNIVERSAL_CHUTE.get(), new Item.Properties()));
    public static final Supplier<Item> ULTIMATE_UNIVERSAL_CHUTE = ITEMS.register("ultimate_universal_chute", () -> new TooltipBlockItem(BlockContent.ULTIMATE_UNIVERSAL_CHUTE.get(), new Item.Properties()));
    public static final Supplier<Item> FLUID_PACKET = ITEMS.register("fluid_packet", () -> new pureneko.conveyor_belt_plus.items.FluidPacketItem(new Item.Properties().stacksTo(1)));

    public static java.util.List<Item> creativeOrder() {
        return java.util.List.of(BELT.get(), ADVANCED_BELT.get(), ULTIMATE_BELT.get(),
                CHUTE.get(), ADVANCED_CHUTE.get(), ULTIMATE_CHUTE.get(),
                FLUID_CHUTE.get(), ADVANCED_FLUID_CHUTE.get(), ULTIMATE_FLUID_CHUTE.get(),
                UNIVERSAL_CHUTE.get(), ADVANCED_UNIVERSAL_CHUTE.get(), ULTIMATE_UNIVERSAL_CHUTE.get(),
                CONVEYOR_SUPPORT.get(), SPLITTER.get());
    }

    public static Item beltForTier(int tier) {
        return switch (BeltTiers.normalize(tier)) {
            case BeltTiers.ULTIMATE -> ULTIMATE_BELT.get();
            case BeltTiers.ADVANCED -> ADVANCED_BELT.get();
            default -> BELT.get();
        };
    }

    public static net.minecraft.world.item.ItemStack beltStackForTier(int tier) {
        return new net.minecraft.world.item.ItemStack(beltForTier(tier));
    }

    public static final Supplier<Item> SPLITTER = ITEMS.register("splitter", () -> new TooltipBlockItem(BlockContent.SPLITTER.get(), new Item.Properties()));

}
