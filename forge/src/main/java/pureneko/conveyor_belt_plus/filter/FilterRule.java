package pureneko.conveyor_belt_plus.filter;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

/** Immutable, non-consuming filter rule. Components are Minecraft data components, not item counts. */
public final class FilterRule {
    public enum Kind { EMPTY, ITEM, COMPONENTS, TAG }
    public static final int MAX_TEXT = 4096;
    public static final int MAX_COMPONENT_TEXT = 2048;
    public static final FilterRule EMPTY = new FilterRule(Kind.EMPTY, ItemStack.EMPTY, "");
    private final Kind kind;
    private final ItemStack prototype;
    private final String tagId;

    private FilterRule(Kind kind, ItemStack prototype, String tagId) {
        this.kind = kind;
        this.prototype = prototype.isEmpty() ? ItemStack.EMPTY : prototype.copyWithCount(1);
        this.tagId = tagId;
    }

    public static FilterRule item(ItemStack stack, boolean components) {
        return stack.isEmpty() ? EMPTY : new FilterRule(components ? Kind.COMPONENTS : Kind.ITEM, stack, "");
    }

    public static FilterRule tag(String value) {
        String text = value.trim();
        if (text.startsWith("#")) text = text.substring(1);
        if (text.length() > 256 || !text.contains(":")) throw new IllegalArgumentException("Use namespace:tag");
        var id = Identifier.tryParse(text);
        if (id == null) throw new IllegalArgumentException("Invalid item tag ID");
        return new FilterRule(Kind.TAG, ItemStack.EMPTY, id.toString());
    }

    public Kind kind() { return kind; }
    public String tagId() { return tagId; }
    public boolean isEmpty() { return kind == Kind.EMPTY; }
    public ItemStack prototype() { return prototype.copy(); }

    public ItemStack icon() {
        if (kind != Kind.TAG) return prototype();
        var entries = Registries.ITEM.getEntryList(TagKey.of(RegistryKeys.ITEM, new Identifier(tagId)));
        return entries.isPresent() && entries.get().size() > 0
                ? entries.get().get(0).value().getDefaultStack() : Items.NAME_TAG.getDefaultStack();
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return switch (kind) {
            case EMPTY -> false;
            case ITEM -> stack.isOf(prototype.getItem());
            case COMPONENTS -> ItemStack.canCombine(prototype, stack);
            case TAG -> stack.isIn(TagKey.of(RegistryKeys.ITEM, new Identifier(tagId)));
        };
    }

    public boolean sameRule(FilterRule other) {
        return kind == other.kind && tagId.equals(other.tagId)
                && ItemStack.canCombine(prototype, other.prototype);
    }

    public NbtCompound write(net.minecraft.registry.DynamicRegistryManager lookup) {
        var tag = new NbtCompound();
        tag.putString("kind", kind.name());
        if (!prototype.isEmpty()) tag.put("item", prototype.writeNbt(new NbtCompound()));
        if (kind == Kind.TAG) tag.putString("tag", tagId);
        return tag;
    }

    public static FilterRule read(NbtCompound tag, net.minecraft.registry.DynamicRegistryManager lookup) {
        Kind kind = Kind.valueOf(tag.getString("kind"));
        if (kind == Kind.EMPTY) return EMPTY;
        if (kind == Kind.TAG) return tag(tag.getString("tag"));
        var stack = ItemStack.fromNbt(tag.getCompound("item"));
        if (stack.isEmpty()) throw new IllegalArgumentException("Missing marker item");
        return item(stack, kind == Kind.COMPONENTS);
    }

    public String toText(net.minecraft.registry.DynamicRegistryManager lookup) {
        String text = write(lookup).toString();
        validateText(text, MAX_TEXT);
        return text;
    }

    public static FilterRule fromText(String text, net.minecraft.registry.DynamicRegistryManager lookup) {
        return read(parseCompound(text, MAX_TEXT), lookup);
    }

    /** Editable SNBT is the complete item tag in Minecraft 1.20.1. */
    public static String componentsText(ItemStack stack, net.minecraft.registry.DynamicRegistryManager lookup) {
        return stack.hasNbt() ? stack.getNbt().toString() : "{}";
    }

    public static FilterRule editComponents(ItemStack base, String text, net.minecraft.registry.DynamicRegistryManager lookup) {
        if (base.isEmpty()) throw new IllegalArgumentException("Choose a marker item first");
        var tag = parseCompound(text, MAX_COMPONENT_TEXT);
        if (tag.contains("Damage") && (!tag.contains("Damage", net.minecraft.nbt.NbtElement.NUMBER_TYPE) || tag.getInt("Damage") < 0))
            throw new IllegalArgumentException("Damage must be a nonnegative number");
        var edited = base.copyWithCount(1);
        edited.setNbt(tag.isEmpty() ? null : tag);
        var rule = item(edited, true);
        rule.toText(lookup);
        return rule;
    }

    public static NbtCompound parseCompound(String text, int maxLength) {
        validateText(text, maxLength);
        try { return StringNbtReader.parse(text); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException ex) {
            throw new IllegalArgumentException(ex.getMessage());
        }
    }

    private static void validateText(String text, int maxLength) {
        if (text.length() > maxLength) throw new IllegalArgumentException("Rule text exceeds " + maxLength + " characters");
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quote != 0) {
                if (escaped) escaped = false;
                else if (ch == '\\') escaped = true;
                else if (ch == quote) quote = 0;
            } else if (ch == '\'' || ch == '"') quote = ch;
            else if (ch == '{' || ch == '[') {
                if (++depth > 32) throw new IllegalArgumentException("Component nesting is too deep");
            } else if (ch == '}' || ch == ']') depth--;
        }
    }
}
