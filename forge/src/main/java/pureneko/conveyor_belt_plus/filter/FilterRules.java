package pureneko.conveyor_belt_plus.filter;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.config.ConveyorConfig;

import java.util.Arrays;

/** Keeps out-of-limit rules dormant when a server lowers the configured capacity. */
public final class FilterRules {
    private final FilterRule[] rules = new FilterRule[ConveyorConfig.MAX_FILTER_RULES];
    public FilterRules() { Arrays.fill(rules, FilterRule.EMPTY); }
    public FilterRule get(int slot) { return slot >= 0 && slot < rules.length ? rules[slot] : FilterRule.EMPTY; }
    public boolean set(int slot, int limit, FilterRule rule) {
        if (slot < 0 || slot >= Math.min(limit, rules.length) || rules[slot].sameRule(rule)) return false;
        rules[slot] = rule;
        return true;
    }

    public boolean allows(ItemStack stack, int limit, boolean whitelist) {
        boolean matched = false;
        for (int i = 0; i < Math.min(limit, rules.length); i++)
            if (rules[i].matches(stack)) { matched = true; break; }
        return whitelist == matched;
    }

    public int used(int limit) {
        int count = 0;
        for (int i = 0; i < Math.min(limit, rules.length); i++) if (!rules[i].isEmpty()) count++;
        return count;
    }

    public void write(NbtCompound nbt, net.minecraft.registry.DynamicRegistryManager lookup) {
        var list = new NbtList();
        for (int i = 0; i < rules.length; i++) {
            if (rules[i].isEmpty()) continue;
            var tag = rules[i].write(lookup);
            tag.putInt("slot", i);
            list.add(tag);
        }
        nbt.put("filterRules", list);
    }

    public void read(NbtCompound nbt, net.minecraft.registry.DynamicRegistryManager lookup) {
        Arrays.fill(rules, FilterRule.EMPTY);
        if (nbt.contains("filterRules", NbtElement.LIST_TYPE)) {
            for (var element : nbt.getList("filterRules", NbtElement.COMPOUND_TYPE)) {
                var tag = (NbtCompound) element;
                int slot = tag.getInt("slot");
                if (slot < 0 || slot >= rules.length) continue;
                try { rules[slot] = FilterRule.read(tag, lookup); }
                catch (IllegalArgumentException ex) { ConveyorBeltPlus.LOGGER.warn("Invalid saved filter slot {}: {}", slot, ex.getMessage()); }
            }
        } else {
            // Older markers used exact component matching. Keep that behavior when reading old NBT.
            var list = nbt.getList("filters", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < Math.min(list.size(), rules.length); i++)
                rules[i] = FilterRule.item(ItemStack.fromNbt(list.getCompound(i)), true);
            if (list.isEmpty() && nbt.contains("filter", NbtElement.COMPOUND_TYPE))
                rules[0] = FilterRule.item(ItemStack.fromNbt(nbt.getCompound("filter")), true);
        }
    }
}
