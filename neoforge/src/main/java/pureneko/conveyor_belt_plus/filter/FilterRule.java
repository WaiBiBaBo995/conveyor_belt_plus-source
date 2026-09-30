package pureneko.conveyor_belt_plus.filter;

import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;

/** Immutable, non-consuming filter rule. Components are Minecraft data components, not item counts. */
public final class FilterRule {
    public enum Kind { EMPTY, ITEM, COMPONENTS, TAG, FLUID, FLUID_TAG }
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
        var id = ResourceLocation.tryParse(text);
        if (id == null) throw new IllegalArgumentException("Invalid item tag ID");
        return new FilterRule(Kind.TAG, ItemStack.EMPTY, id.toString());
    }

    public static FilterRule fluid(net.neoforged.neoforge.fluids.FluidStack stack) {
        return stack.isEmpty() ? EMPTY : new FilterRule(Kind.FLUID,
                pureneko.conveyor_belt_plus.util.FluidPackets.create(stack.copyWithAmount(1000)), "");
    }
    public static FilterRule fluidTag(String value) {
        return new FilterRule(Kind.FLUID_TAG, ItemStack.EMPTY, tag(value).tagId);
    }
    public boolean isFluidRule() { return kind == Kind.FLUID || kind == Kind.FLUID_TAG; }
    public boolean isTagRule() { return kind == Kind.TAG || kind == Kind.FLUID_TAG; }

    public Kind kind() { return kind; }
    public String tagId() { return tagId; }
    public boolean isEmpty() { return kind == Kind.EMPTY; }
    public ItemStack prototype() { return prototype.copy(); }

    /** A fluid preview independent of whether this fluid has a bucket item. */
    public net.neoforged.neoforge.fluids.FluidStack fluidIcon() {
        if (kind == Kind.FLUID) return pureneko.conveyor_belt_plus.util.FluidPackets.get(prototype);
        if (kind == Kind.FLUID_TAG) {
            var fluids = BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID, ResourceLocation.parse(tagId)));
            if (fluids.isPresent() && fluids.get().size() > 0)
                return new net.neoforged.neoforge.fluids.FluidStack(fluids.get().get(0), 1000);
        }
        return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
    }

    public ItemStack icon() {
        if (kind == Kind.FLUID) return pureneko.conveyor_belt_plus.util.FluidPackets.icon(
                pureneko.conveyor_belt_plus.util.FluidPackets.get(prototype));
        if (kind == Kind.FLUID_TAG) {
            var fluids = BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID, ResourceLocation.parse(tagId)));
            return fluids.isPresent() && fluids.get().size() > 0
                    ? pureneko.conveyor_belt_plus.util.FluidPackets.icon(new net.neoforged.neoforge.fluids.FluidStack(fluids.get().get(0), 1000))
                    : Items.NAME_TAG.getDefaultInstance();
        }
        if (kind != Kind.TAG) return prototype();
        var entries = BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, ResourceLocation.parse(tagId)));
        return entries.isPresent() && entries.get().size() > 0
                ? entries.get().get(0).value().getDefaultInstance() : Items.NAME_TAG.getDefaultInstance();
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return switch (kind) {
            case FLUID -> net.neoforged.neoforge.fluids.FluidStack.isSameFluid(
                    pureneko.conveyor_belt_plus.util.FluidPackets.get(prototype), pureneko.conveyor_belt_plus.util.FluidPackets.get(stack));
            case FLUID_TAG -> pureneko.conveyor_belt_plus.util.FluidPackets.get(stack).is(TagKey.create(Registries.FLUID, ResourceLocation.parse(tagId)));
            case EMPTY -> false;
            case ITEM -> stack.is(prototype.getItem());
            case COMPONENTS -> ItemStack.isSameItemSameComponents(prototype, stack);
            case TAG -> stack.is(TagKey.create(Registries.ITEM, ResourceLocation.parse(tagId)));
        };
    }

    public boolean sameRule(FilterRule other) {
        return kind == other.kind && tagId.equals(other.tagId)
                && ItemStack.isSameItemSameComponents(prototype, other.prototype);
    }

    public CompoundTag write(HolderLookup.Provider lookup) {
        var tag = new CompoundTag();
        tag.putString("kind", kind.name());
        if (!prototype.isEmpty()) tag.put("item", prototype.save(lookup));
        if (isTagRule()) tag.putString("tag", tagId);
        return tag;
    }

    public static FilterRule read(CompoundTag tag, HolderLookup.Provider lookup) {
        Kind kind = Kind.valueOf(tag.getString("kind"));
        if (kind == Kind.EMPTY) return EMPTY;
        if (kind == Kind.TAG) return tag(tag.getString("tag"));
        if (kind == Kind.FLUID_TAG) return fluidTag(tag.getString("tag"));
        var stack = ItemStack.STRICT_CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, lookup), tag.getCompound("item"))
                .getOrThrow(error -> new IllegalArgumentException(error));
        if (stack.isEmpty()) throw new IllegalArgumentException("Missing marker item");
        if (kind == Kind.FLUID) {
            var fluid = pureneko.conveyor_belt_plus.util.FluidPackets.get(stack);
            if (fluid.isEmpty()) throw new IllegalArgumentException("Missing fluid marker");
            return fluid(fluid);
        }
        return item(stack, kind == Kind.COMPONENTS);
    }

    public String toText(HolderLookup.Provider lookup) {
        String text = write(lookup).toString();
        validateText(text, MAX_TEXT);
        return text;
    }

    public static FilterRule fromText(String text, HolderLookup.Provider lookup) {
        return read(parseCompound(text, MAX_TEXT), lookup);
    }

    /** Editable SNBT contains the item's component patch; omitted keys use the item's defaults. */
    public static String componentsText(ItemStack stack, HolderLookup.Provider lookup) {
        return DataComponentPatch.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, lookup), stack.getComponentsPatch())
                .getOrThrow(error -> new IllegalArgumentException(error)).toString();
    }

    public static FilterRule editComponents(ItemStack base, String text, HolderLookup.Provider lookup) {
        if (base.isEmpty()) throw new IllegalArgumentException("Choose a marker item first");
        var changes = DataComponentPatch.CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, lookup),
                parseCompound(text, MAX_COMPONENT_TEXT)).getOrThrow(error -> new IllegalArgumentException(error));
        var edited = new ItemStack(base.getItemHolder(), 1, changes);
        ItemStack.validateComponents(edited.getComponents()).getOrThrow(error -> new IllegalArgumentException(error));
        var rule = item(edited, true);
        rule.toText(lookup); // Validate packet/save limits before accepting an edit.
        return rule;
    }

    public static CompoundTag parseCompound(String text, int maxLength) {
        validateText(text, maxLength);
        try { return TagParser.parseTag(text); }
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
