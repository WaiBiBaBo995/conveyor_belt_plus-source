package pureneko.conveyor_belt_plus.filter;

import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.config.ConveyorConfig;

import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

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

    public void write(CompoundTag nbt, net.minecraft.core.RegistryAccess lookup) {
        var list = new ListTag();
        for (int i = 0; i < rules.length; i++) {
            if (rules[i].isEmpty()) continue;
            var tag = rules[i].write(lookup);
            tag.putInt("slot", i);
            list.add(tag);
        }
        nbt.put("filterRules", list);
    }

    public void read(CompoundTag nbt, net.minecraft.core.RegistryAccess lookup) {
        Arrays.fill(rules, FilterRule.EMPTY);
        if (nbt.contains("filterRules", Tag.TAG_LIST)) {
            for (var element : nbt.getList("filterRules", Tag.TAG_COMPOUND)) {
                var tag = (CompoundTag) element;
                int slot = tag.getInt("slot");
                if (slot < 0 || slot >= rules.length) continue;
                try { rules[slot] = FilterRule.read(tag, lookup); }
                catch (IllegalArgumentException ex) { ConveyorBeltPlus.LOGGER.warn("Invalid saved filter slot {}: {}", slot, ex.getMessage()); }
            }
        } else {
            // Older markers used exact component matching. Keep that behavior when reading old NBT.
            var list = nbt.getList("filters", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(list.size(), rules.length); i++)
                rules[i] = FilterRule.item(ItemStack.of(list.getCompound(i)), true);
            if (list.isEmpty() && nbt.contains("filter", Tag.TAG_COMPOUND))
                rules[0] = FilterRule.item(ItemStack.of(nbt.getCompound("filter")), true);
        }
    }
}
