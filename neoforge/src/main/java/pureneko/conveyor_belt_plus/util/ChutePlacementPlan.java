package pureneko.conveyor_belt_plus.util;

import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import pureneko.conveyor_belt_plus.blocks.ChuteBlock;

import java.util.ArrayList;
import java.util.List;

/** Reserves exact inventory slots and chute variants before placing either endpoint. */
public final class ChutePlacementPlan {
    private record Choice(int slot, ItemStack stack) {}
    private final List<Choice> choices;
    private final boolean creative;
    private boolean consumed;

    private ChutePlacementPlan(List<Choice> choices, boolean creative) {
        this.choices = List.copyOf(choices);
        this.creative = creative;
    }

    public static boolean isChute(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem item
                && item.getBlock() instanceof ChuteBlock;
    }

    public static int count(List<ItemStack> inventory) {
        return inventory.stream().filter(ChutePlacementPlan::isChute).mapToInt(ItemStack::getCount).sum();
    }

    /** Live stack references: offhand first, then hotbar 0..8 and main inventory 9..35. */
    public static List<ItemStack> orderedInventory(PlayerEntity player) {
        return orderedInventory(player.getInventory().main, player.getOffHandStack());
    }

    public static List<ItemStack> orderedInventory(List<ItemStack> main, ItemStack offhand) {
        var inventory = new ArrayList<ItemStack>(main.size() + 1);
        inventory.add(offhand);
        inventory.addAll(main);
        return inventory;
    }

    public static @Nullable ChutePlacementPlan select(List<ItemStack> inventory,
                                                       int needed, boolean creative, ItemStack fallback) {
        return selectMatching(inventory, needed, creative, fallback, ChutePlacementPlan::isChute);
    }

    /** Selection/consumption is separate from block eligibility, so it can be tested without mod registries. */
    public static @Nullable ChutePlacementPlan selectMatching(List<ItemStack> inventory,
            int needed, boolean creative, ItemStack fallback, java.util.function.Predicate<ItemStack> eligible) {
        if (needed < 0 || needed > 2) throw new IllegalArgumentException("A belt has at most two endpoints");
        var choices = new ArrayList<Choice>();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (choices.size() == needed) break;
            var stack = inventory.get(slot);
            if (stack.isEmpty() || !eligible.test(stack)) continue;
            int available = creative ? needed : stack.getCount();
            for (int i = 0; i < available && choices.size() < needed; i++)
                choices.add(new Choice(slot, stack.copyWithCount(1)));
        }
        if (creative && !fallback.isEmpty() && eligible.test(fallback))
            while (choices.size() < needed) choices.add(new Choice(-1, fallback.copyWithCount(1)));
        return choices.size() == needed ? new ChutePlacementPlan(choices, creative) : null;
    }

    public BlockState state(int endpoint, Direction facing) {
        return ((BlockItem) choices.get(endpoint).stack.getItem()).getBlock().getDefaultState()
                .with(HorizontalFacingBlock.FACING, facing);
    }

    /** Also used for failure refunds, retaining the chosen tier and item components. */
    public ItemStack item(int endpoint) { return choices.get(endpoint).stack.copy(); }
    public int size() { return choices.size(); }

    /** Validate every reservation first, then consume exactly those slots, never a different tier. */
    public boolean consume(List<ItemStack> inventory) {
        if (consumed) return false;
        if (!creative) {
            var needed = new int[inventory.size()];
            for (var choice : choices) {
                if (choice.slot < 0 || choice.slot >= inventory.size()) return false;
                var current = inventory.get(choice.slot);
                if (!ItemStack.areItemsAndComponentsEqual(current, choice.stack)
                        || ++needed[choice.slot] > current.getCount()) return false;
            }
            for (int slot = 0; slot < needed.length; slot++)
                if (needed[slot] > 0) inventory.get(slot).decrement(needed[slot]);
        }
        consumed = true;
        return true;
    }
}
