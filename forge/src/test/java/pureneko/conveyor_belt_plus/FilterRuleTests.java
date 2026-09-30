package pureneko.conveyor_belt_plus;

import io.netty.buffer.Unpooled;
import pureneko.conveyor_belt_plus.filter.FilterRule;
import pureneko.conveyor_belt_plus.filter.FilterRules;
import pureneko.conveyor_belt_plus.network.FilterNetworking;
import pureneko.conveyor_belt_plus.network.FilterNetworking.Edit;
import pureneko.conveyor_belt_plus.network.FilterNetworking.Snapshot;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistryAccess.Frozen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class FilterRuleTests {
    private static int checks;
    public static void run(net.minecraft.core.RegistryAccess lookup) {
        matchingAndEditing(lookup);
        capacitiesAndPersistence(lookup);
        networking(lookup);
        System.out.println("Conveyor Belt Plus: " + checks + " filter and packet checks passed.");
    }

    private static void matchingAndEditing(net.minecraft.core.RegistryAccess lookup) {
        var sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.setDamageValue(3);
        sword.setHoverName(Component.literal("Priority"));
        var differentlyDamaged = sword.copy();
        differentlyDamaged.setDamageValue(7);
        var item = FilterRule.item(sword, false);
        var exact = FilterRule.item(sword, true);
        check(item.matches(differentlyDamaged), "item rule ignores components");
        check(exact.matches(sword) && !exact.matches(differentlyDamaged), "component rule compares exact damage");
        check(!item.matches(new ItemStack(Items.IRON_SWORD)), "item type must match");
        var renamed = sword.copy();
        renamed.setHoverName(Component.literal("Other"));
        check(!exact.matches(renamed), "component rule compares exact names");
        check(exact.matches(sword.copyWithCount(32)), "count is not a component predicate");
        var exported = exact.prototype();
        exported.setDamageValue(99);
        check(exact.matches(sword), "marker access cannot mutate stored rule");
        String initial = FilterRule.componentsText(sword, lookup);
        check(FilterRule.editComponents(sword, initial, lookup).matches(sword), "editable component text round trip");
        var edited = FilterRule.editComponents(sword, "{Damage:7}", lookup);
        check(edited.kind() == FilterRule.Kind.COMPONENTS && edited.prototype().getDamageValue() == 7,
                "edited damage becomes exact filter");
        check(!edited.prototype().hasCustomHoverName(), "omitted patch keys return to default components");
        check(sword.getDamageValue() == 3 && sword.getHoverName().getString().equals("Priority"), "editing never modifies real source item");
        check(FilterRule.editComponents(sword, "{Damage:3,display:{Name:'{\"text\":\"Priority\"}'}}", lookup)
                .matches(sword), "manual NBT preserves the exact serialized name");
        var potion = new ItemStack(Items.POTION);
        var healing = FilterRule.editComponents(potion, "{Potion:\"minecraft:healing\"}", lookup);
        var strongHealing = FilterRule.editComponents(potion, "{Potion:\"minecraft:strong_healing\"}", lookup);
        check(healing.matches(healing.prototype()) && !healing.matches(strongHealing.prototype()), "exact potion contents");
        check(FilterRule.fromText(healing.toText(lookup), lookup).sameRule(healing), "registry-backed potion round-trip");
        var sharpness3 = FilterRule.editComponents(sword, "{Enchantments:[{id:\"minecraft:sharpness\",lvl:3s}]}", lookup);
        var sharpness4 = FilterRule.editComponents(sword, "{Enchantments:[{id:\"minecraft:sharpness\",lvl:4s}]}", lookup);
        check(!sharpness3.matches(sharpness4.prototype()) && sharpness3.matches(sharpness3.prototype()), "exact enchantment levels");
        check(FilterRule.fromText(sharpness3.toText(lookup), lookup).sameRule(sharpness3), "dynamic-registry enchantment round-trip");
        check(FilterRule.editComponents(sword, "{CustomModData:{value:4}}", lookup).prototype().getTagElement("CustomModData").getInt("value") == 4, "mod-defined NBT is preserved");
        rejects(() -> FilterRule.editComponents(sword, "{Damage:\"not_a_number\"}", lookup), "invalid damage tag rejected");
        rejects(() -> FilterRule.editComponents(sword, "{broken", lookup), "malformed SNBT rejected");
        rejects(() -> FilterRule.editComponents(sword, " ".repeat(FilterRule.MAX_COMPONENT_TEXT + 1), lookup), "oversized edit rejected");
        rejects(() -> FilterRule.fromText("{x:".repeat(40) + "0" + "}".repeat(40), lookup), "excessive nesting rejected");
        rejects(() -> FilterRule.fromText("{kind:\"UNKNOWN\"}", lookup), "unknown rule kind rejected");
        rejects(() -> FilterRule.tag("missing_namespace"), "tag namespace is mandatory");
        rejects(() -> FilterRule.tag("minecraft:INVALID"), "invalid tag syntax rejected");

        var tagKey = TagKey.create(Registries.ITEM, new ResourceLocation("conveyor_belt_plus_test", "logs"));
        var allTags = BuiltInRegistries.ITEM.getTags().collect(java.util.stream.Collectors.toMap(
                com.mojang.datafixers.util.Pair::getFirst, pair -> pair.getSecond().stream().toList()));
        allTags.put(tagKey, List.<Holder<net.minecraft.world.item.Item>>of(Items.OAK_LOG.builtInRegistryHolder(), Items.BIRCH_LOG.builtInRegistryHolder()));
        BuiltInRegistries.ITEM.bindTags(allTags);
        var tag = FilterRule.tag("#conveyor_belt_plus_test:logs");
        check(tag.matches(new ItemStack(Items.OAK_LOG)) && tag.matches(new ItemStack(Items.BIRCH_LOG))
                && !tag.matches(new ItemStack(Items.STONE)), "tag matches all member item types");
        check(tag.icon().is(Items.OAK_LOG), "tag has representative icon");
        allTags.put(tagKey, List.<Holder<net.minecraft.world.item.Item>>of(Items.SPRUCE_LOG.builtInRegistryHolder()));
        BuiltInRegistries.ITEM.bindTags(allTags);
        check(!tag.matches(new ItemStack(Items.OAK_LOG)) && tag.matches(new ItemStack(Items.SPRUCE_LOG)),
                "existing rule follows tag reload, not a frozen list of items");
        check(!FilterRule.tag("missing:valid_tag").matches(new ItemStack(Items.OAK_LOG)), "unknown tag matches nothing");
        for (var rule : List.of(FilterRule.EMPTY, item, exact, edited, tag)) {
            var restored = FilterRule.fromText(rule.toText(lookup), lookup);
            check(restored.sameRule(rule), "every rule kind round-trips with exact components");
        }
        check(!FilterRule.EMPTY.matches(sword), "empty rule never matches");
    }

    private static void capacitiesAndPersistence(net.minecraft.core.RegistryAccess lookup) {
        var rules = new FilterRules();
        var iron = new ItemStack(Items.IRON_INGOT);
        check(rules.allows(iron, 5, false) && !rules.allows(iron, 5, true), "empty blacklist allows; empty whitelist blocks");
        for (int i = 0; i < 5; i++) check(rules.set(i, 5, FilterRule.item(new ItemStack(Items.STONE), false)), "fill standard limit");
        check(!rules.set(5, 5, FilterRule.item(iron, false)) && rules.used(5) == 5, "standard limit is enforced");
        check(!rules.set(-1, 5, FilterRule.item(iron, false)), "negative slot rejected");
        check(rules.set(0, 5, FilterRule.item(iron, true)), "replace existing rule at full capacity");
        check(rules.allows(iron, 5, true) && !rules.allows(iron, 5, false), "blacklist and whitelist share matching semantics");
        check(rules.set(7, 10, FilterRule.tag("conveyor_belt_plus_test:logs")), "tag rule uses one advanced slot");
        check(rules.set(14, 15, FilterRule.item(new ItemStack(Items.GOLD_INGOT), true)), "last ultimate slot accepted");
        check(!rules.set(15, 15, FilterRule.item(iron, false)), "ultimate limit is enforced");
        check(rules.set(53, 54, FilterRule.item(new ItemStack(Items.DIAMOND), false)), "configured multi-page limit accepted");
        check(!rules.set(54, 55, FilterRule.item(iron, false)), "hard storage ceiling enforced");
        check(!rules.allows(new ItemStack(Items.DIAMOND), 15, true), "out-of-limit rules are dormant");
        var nbt = new CompoundTag();
        rules.write(nbt, lookup);
        var restored = new FilterRules();
        restored.read(nbt, lookup);
        check(restored.get(53).sameRule(rules.get(53)) && restored.allows(new ItemStack(Items.DIAMOND), 54, true),
                "lowering capacity never deletes dormant rules");
        check(restored.get(7).sameRule(rules.get(7)) && restored.get(14).sameRule(rules.get(14)), "all rule kinds persist");
        var legacy = new CompoundTag();
        var list = new ListTag();
        for (int i = 0; i < 18; i++) list.add(new ItemStack(Items.IRON_INGOT).save(new CompoundTag()));
        legacy.put("filters", list);
        restored.read(legacy, lookup);
        check(restored.get(17).kind() == FilterRule.Kind.COMPONENTS, "legacy eighteenth marker retained with exact semantics");
        check(restored.used(15) == 15 && restored.used(18) == 18, "legacy overflow remains dormant rather than lost");
        check(restored.set(0, 15, FilterRule.EMPTY) && restored.used(15) == 14, "clearing frees a rule slot");
    }

    private static void networking(net.minecraft.core.RegistryAccess lookup) {
        var raw = FilterRule.item(new ItemStack(Items.DIAMOND), true).toText(lookup);
        var registry = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var edit = new FilterNetworking.Edit(7, 14, raw);
            FilterNetworking.Edit.CODEC.encode(buf, edit);
            check(FilterNetworking.Edit.CODEC.decode(buf).equals(edit), "ghost edit packet round-trip");
            buf.clear();
            var state = new FilterNetworking.Snapshot(7, true, java.util.Collections.nCopies(54, raw), "");
            FilterNetworking.Snapshot.CODEC.encode(buf, state);
            check(FilterNetworking.Snapshot.CODEC.decode(buf).equals(state), "maximum-capacity snapshot round-trip");
            buf.clear();
            buf.writeVarInt(1);
            buf.writeBoolean(false); // item tab
            buf.writeBoolean(false); // blacklist
            buf.writeVarInt(55);
            rejects(() -> FilterNetworking.Snapshot.CODEC.decode(buf), "oversized rule array rejected before allocation");
        } finally { buf.release(); }
    }

    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static void rejects(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException exception) { check(true, message); return; }
        throw new AssertionError(message);
    }
}
