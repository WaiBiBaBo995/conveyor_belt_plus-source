package pureneko.conveyor_belt_plus.filter;

import net.minecraft.component.ComponentChanges;
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
        var entries = Registries.ITEM.getEntryList(TagKey.of(RegistryKeys.ITEM, Identifier.of(tagId)));
        return entries.isPresent() && entries.get().size() > 0
                ? entries.get().get(0).value().getDefaultStack() : Items.NAME_TAG.getDefaultStack();
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return switch (kind) {
            case EMPTY -> false;
            case ITEM -> stack.isOf(prototype.getItem());
            case COMPONENTS -> ItemStack.areItemsAndComponentsEqual(prototype, stack);
            case TAG -> stack.isIn(TagKey.of(RegistryKeys.ITEM, Identifier.of(tagId)));
        };
    }

    public boolean sameRule(FilterRule other) {
        return kind == other.kind && tagId.equals(other.tagId)
                && ItemStack.areItemsAndComponentsEqual(prototype, other.prototype);
    }

    public NbtCompound write(RegistryWrapper.WrapperLookup lookup) {
        var tag = new NbtCompound();
        tag.putString("kind", kind.name());
        if (!prototype.isEmpty()) tag.put("item", prototype.encode(lookup));
        if (kind == Kind.TAG) tag.putString("tag", tagId);
        return tag;
    }

    public static FilterRule read(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        Kind kind = Kind.valueOf(tag.getString("kind"));
        if (kind == Kind.EMPTY) return EMPTY;
        if (kind == Kind.TAG) return tag(tag.getString("tag"));
        var stack = ItemStack.VALIDATED_CODEC.parse(RegistryOps.of(NbtOps.INSTANCE, lookup), tag.getCompound("item"))
                .getOrThrow(error -> new IllegalArgumentException(error));
        if (stack.isEmpty()) throw new IllegalArgumentException("Missing marker item");
        return item(stack, kind == Kind.COMPONENTS);
    }

    public String toText(RegistryWrapper.WrapperLookup lookup) {
        String text = write(lookup).toString();
        validateText(text, MAX_TEXT);
        return text;
    }

    public static FilterRule fromText(String text, RegistryWrapper.WrapperLookup lookup) {
        return read(parseCompound(text, MAX_TEXT), lookup);
    }

    /** Editable SNBT contains the item's component patch; omitted keys use the item's defaults. */
    public static String componentsText(ItemStack stack, RegistryWrapper.WrapperLookup lookup) {
        return ComponentChanges.CODEC.encodeStart(RegistryOps.of(NbtOps.INSTANCE, lookup), stack.getComponentChanges())
                .getOrThrow(error -> new IllegalArgumentException(error)).toString();
    }

    public static FilterRule editComponents(ItemStack base, String text, RegistryWrapper.WrapperLookup lookup) {
        if (base.isEmpty()) throw new IllegalArgumentException("Choose a marker item first");
        var changes = ComponentChanges.CODEC.parse(RegistryOps.of(NbtOps.INSTANCE, lookup),
                parseCompound(text, MAX_COMPONENT_TEXT)).getOrThrow(error -> new IllegalArgumentException(error));
        var edited = new ItemStack(base.getRegistryEntry(), 1, changes);
        ItemStack.validateComponents(edited.getComponents()).getOrThrow(error -> new IllegalArgumentException(error));
        var rule = item(edited, true);
        rule.toText(lookup); // Validate packet/save limits before accepting an edit.
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
