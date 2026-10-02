package pureneko.conveyor_belt_plus.client.screens;

import org.lwjgl.glfw.GLFW;
import pureneko.conveyor_belt_plus.filter.FilterRule;
import pureneko.conveyor_belt_plus.network.FilterNetworking;
import pureneko.conveyor_belt_plus.screen.ChuteScreenHandler;
import pureneko.conveyor_belt_plus.util.ExtractionSide;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

public class ChuteScreen extends AbstractContainerScreen<ChuteScreenHandler> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private Button modeButton, kindButton, editButton, clearButton, tagCycle, tagApply;
    private Button previousPage, nextPage, saveComponents, cancelComponents;
    private RedstoneModeButton redstoneButton;
    private EditBox tagField, componentField;
    private int page, selected;
    private FilterRule.Kind inputKind = FilterRule.Kind.ITEM;
    private boolean editingComponents;
    private String localError = "";
    private long seenRevision = -1;
    private List<String> candidateTags = List.of();
    private static final int SIDE_PANEL_WIDTH = 112, SIDE_PANEL_HEIGHT = 112, SIDE_PANEL_GAP = 4;
    private Button settingsButton;
    private final List<ExtractionSideButton> sideButtons = new ArrayList<>();
    private boolean sidesOpen;
    private boolean seenFluid;
    private Button itemTab, fluidTab;

    public ChuteScreen(ChuteScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        imageWidth = 176;
        imageHeight = handler.getPlayerInventoryY() + 82;
        inventoryLabelY = handler.getPlayerInventoryY() - 12;
        seenFluid = handler.isFluidTab();
        inputKind = seenFluid ? FilterRule.Kind.FLUID : FilterRule.Kind.ITEM;
    }

    private Component tr(String key, Object... args) { return Component.translatable("screen.conveyor_belt_plus." + key, args); }

    @Override protected void init() {
        String oldTag = tagField == null ? "" : tagField.getValue();
        String oldComponents = componentField == null ? "" : componentField.getValue();
        super.init();
        // Keep the original inventory layout; shift the main window only when the left panel needs room.
        if (sidesOpen && canFitSidePanel())
            leftPos = Math.max(leftPos, SIDE_PANEL_WIDTH + SIDE_PANEL_GAP + 4);
        itemTab = addRenderableWidget(Button.builder(tr("tab_items"), button -> switchTab(ChuteScreenHandler.ITEM_TAB))
                .bounds(leftPos + imageWidth + 2, topPos + 20, 42, 22).build());
        fluidTab = addRenderableWidget(Button.builder(tr("tab_fluids"), button -> switchTab(ChuteScreenHandler.FLUID_TAB))
                .bounds(leftPos + imageWidth + 2, topPos + 44, 42, 22).build());
        modeButton = addRenderableWidget(Button.builder(modeText(), button -> {
            if (minecraft != null && minecraft.gameMode != null)
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ChuteScreenHandler.MODE_BUTTON);
        }).bounds(leftPos + 8, topPos + 18, 136, 20).tooltip(Tooltip.create(tr(menu.isFluidTab() ? "fluid_filter_hint" : "filter_hint"))).build());
        redstoneButton = addRenderableWidget(new RedstoneModeButton(leftPos + 148, topPos + 18, menu::getRedstoneMode, button -> {
            if (minecraft != null && minecraft.gameMode != null)
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ChuteScreenHandler.REDSTONE_BUTTON);
        }));
        kindButton = addRenderableWidget(Button.builder(Component.empty(), button -> cycleKind())
                .bounds(leftPos + 8, topPos + 88, 78, 18).build());
        editButton = addRenderableWidget(Button.builder(tr("edit_components"), button -> beginComponents())
                .bounds(leftPos + 88, topPos + 88, 38, 18).tooltip(Tooltip.create(tr("components_help"))).build());
        clearButton = addRenderableWidget(Button.builder(tr("clear_rule"), button -> sendRule(selected, FilterRule.EMPTY))
                .bounds(leftPos + 128, topPos + 88, 40, 18).build());
        tagField = addRenderableWidget(new EditBox(font, leftPos + 8, topPos + 108, 112, 18, tr("tag_id")));
        tagField.setMaxLength(256);
        tagField.setResponder(text -> tagField.setSuggestion(pureneko.conveyor_belt_plus.util.TagInputHint.forText(text)));
        tagField.setValue(oldTag);
        tagField.setSuggestion(pureneko.conveyor_belt_plus.util.TagInputHint.forText(oldTag));
        tagCycle = addRenderableWidget(Button.builder(Component.literal(">"), button -> cycleTag())
                .bounds(leftPos + 124, topPos + 108, 20, 18).tooltip(Tooltip.create(tr("cycle_tag"))).build());
        tagApply = addRenderableWidget(Button.builder(Component.literal("+"), button -> applyTag())
                .bounds(leftPos + 148, topPos + 108, 20, 18).tooltip(Tooltip.create(tr("apply_tag"))).build());
        previousPage = addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-1))
                .bounds(leftPos + 8, topPos + 128, 20, 16).build());
        nextPage = addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(1))
                .bounds(leftPos + 148, topPos + 128, 20, 16).build());
        componentField = addRenderableWidget(new EditBox(font, leftPos + 8, topPos + 108, 160, 18, tr("component_snbt")));
        componentField.setMaxLength(FilterRule.MAX_COMPONENT_TEXT);
        componentField.setTooltip(Tooltip.create(tr("components_help")));
        componentField.setValue(oldComponents);
        saveComponents = addRenderableWidget(Button.builder(tr("save_components"), button -> saveComponents())
                .bounds(leftPos + 8, topPos + 128, 76, 16).build());
        cancelComponents = addRenderableWidget(Button.builder(tr("cancel"), button -> cancelComponents())
                .bounds(leftPos + 92, topPos + 128, 76, 16).build());
        settingsButton = addRenderableWidget(Button.builder(Component.literal("⚙"), button -> {
            sidesOpen = !sidesOpen;
            rebuildWidgets();
        }).bounds(leftPos - 22, topPos + 2, 20, 20)
                .tooltip(Tooltip.create(tr(canFitSidePanel() ? "extraction.settings" : "extraction.small_window"))).build());
        sideButtons.clear();
        for (var side : ExtractionSide.values()) {
            sideButtons.add(addRenderableWidget(new ExtractionSideButton(sidePanelX() + 24 + side.column() * 22,
                    sidePanelY() + 23 + side.row() * 22, side, menu, button -> {
                if (minecraft != null && minecraft.gameMode != null)
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ChuteScreenHandler.EXTRACTION_BUTTON_BASE + side.ordinal());
            })));
        }
        updateWidgets();
    }

    private void switchTab(int tab) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, tab);
    }
    public boolean isFluidTab() { return menu.isFluidTab(); }
    private boolean tagInput() { return inputKind == FilterRule.Kind.TAG || inputKind == FilterRule.Kind.FLUID_TAG; }
    private FilterRule tagRule(String id) { return menu.isFluidTab() ? FilterRule.fluidTag(id) : FilterRule.tag(id); }

    private Component modeText() {
        return tr(menu.isWhitelistMode() ? "filter_whitelist" : "filter_blacklist");
    }

    private Component kindText(FilterRule.Kind kind) {
        return tr("rule_" + (kind == FilterRule.Kind.EMPTY ? "item" : kind.name().toLowerCase(java.util.Locale.ROOT)));
    }

    private void select(int index) {
        selected = index;
        var rule = menu.getRule(index);
        candidateTags = markerTags(rule.icon());
        if (!rule.isEmpty()) inputKind = rule.kind();
        tagField.setValue(rule.isTagRule() ? rule.tagId() : "");
        localError = "";
        updateWidgets();
    }

    private void cycleKind() {
        if (menu.isFluidTab()) {
            inputKind = tagInput() ? FilterRule.Kind.FLUID : FilterRule.Kind.FLUID_TAG;
            var marker = pureneko.conveyor_belt_plus.util.FluidPackets.marker(menu.getRule(selected).prototype());
            if (!tagInput() && !marker.isEmpty()) sendRule(selected, FilterRule.fluid(marker));
            updateWidgets();
            return;
        }
        inputKind = switch (inputKind) {
            case ITEM, EMPTY -> FilterRule.Kind.COMPONENTS;
            case COMPONENTS -> FilterRule.Kind.TAG;
            case TAG, FLUID, FLUID_TAG -> FilterRule.Kind.ITEM;
        };
        var marker = menu.getRule(selected).prototype();
        if (!tagInput() && !marker.isEmpty())
            sendRule(selected, FilterRule.item(marker, inputKind == FilterRule.Kind.COMPONENTS));
        updateWidgets();
    }

    private List<String> markerTags(ItemStack marker) {
        if (marker.isEmpty()) return List.of();
        if (menu.isFluidTab()) {
            var fluid = pureneko.conveyor_belt_plus.util.FluidPackets.marker(marker);
            return fluid.isEmpty() ? List.of() : fluid.getFluid().builtInRegistryHolder().tags()
                    .map(tag -> tag.location().toString()).sorted().toList();
        }
        return marker.getTags().map(tag -> tag.location().toString()).sorted().toList();
    }

    private void cycleTag() {
        var marker = menu.getRule(selected).icon();
        var tags = candidateTags.isEmpty() ? markerTags(marker) : candidateTags;
        if (tags.isEmpty()) { fail(tr("no_tags").getString()); return; }
        int next = (tags.indexOf(tagField.getValue()) + 1) % tags.size();
        tagField.setValue(tags.get(next));
    }

    private void applyTag() {
        try { sendRule(selected, tagRule(tagField.getValue())); }
        catch (IllegalArgumentException ex) { fail(ex.getMessage()); }
    }

    private void beginComponents() {
        if (menu.isFluidTab() || inputKind != FilterRule.Kind.COMPONENTS) return;
        var marker = menu.getRule(selected).prototype();
        if (marker.isEmpty() || minecraft == null || minecraft.level == null) return;
        String text;
        try { text = FilterRule.componentsText(marker, minecraft.level.registryAccess()); }
        catch (IllegalArgumentException ex) { fail(ex.getMessage()); return; }
        if (text.length() > FilterRule.MAX_COMPONENT_TEXT) { fail(tr("components_too_large").getString()); return; }
        componentField.setValue(text);
        editingComponents = true;
        localError = "";
        updateWidgets();
        setFocused(componentField);
        componentField.setFocused(true);
    }

    private void saveComponents() {
        try {
            var rule = FilterRule.editComponents(menu.getRule(selected).prototype(), componentField.getValue(),
                    minecraft.level.registryAccess());
            sendRule(selected, rule);
            editingComponents = false;
            inputKind = FilterRule.Kind.COMPONENTS;
            componentField.setFocused(false);
            updateWidgets();
        } catch (IllegalArgumentException ex) { fail(ex.getMessage()); }
    }

    private void cancelComponents() {
        editingComponents = false;
        componentField.setFocused(false);
        localError = "";
        updateWidgets();
    }

    private void fail(String message) { localError = message == null ? "Invalid rule" : message; }

    private void sendRule(int index, FilterRule rule) {
        if (minecraft == null || minecraft.level == null) return;
        try {
            FilterNetworking.sendEdit(menu.containerId, menu.isFluidTab(), index, rule.toText(minecraft.level.registryAccess()));
            localError = "";
        } catch (IllegalArgumentException ex) { fail(ex.getMessage()); }
    }

    private int pageCount() { return (menu.getFilterSlotCount() + ChuteScreenHandler.PAGE_SIZE - 1) / ChuteScreenHandler.PAGE_SIZE; }
    private int visibleCount() { return Math.min(ChuteScreenHandler.PAGE_SIZE, menu.getFilterSlotCount() - page * ChuteScreenHandler.PAGE_SIZE); }
    private void changePage(int delta) {
        page = Math.clamp(page + delta, 0, pageCount() - 1);
        select(page * ChuteScreenHandler.PAGE_SIZE);
    }

    private void updateWidgets() {
        if (modeButton == null) return;
        itemTab.visible = fluidTab.visible = menu.isUniversal();
        itemTab.active = !editingComponents && menu.isFluidTab();
        fluidTab.active = !editingComponents && !menu.isFluidTab();
        settingsButton.visible = menu.extractionEnabled();
        settingsButton.active = !editingComponents && canFitSidePanel();
        for (var button : sideButtons) {
            button.visible = sidePanelVisible();
            button.active = !editingComponents && menu.extractionEnabled();
        }
        modeButton.setMessage(modeText());
        modeButton.setTooltip(Tooltip.create(tr(menu.isFluidTab() ? "fluid_filter_hint" : "filter_hint")));
        modeButton.active = !editingComponents;
        redstoneButton.active = !editingComponents;
        kindButton.setMessage(kindText(inputKind));
        kindButton.active = !editingComponents;
        editButton.visible = !menu.isFluidTab() && inputKind == FilterRule.Kind.COMPONENTS;
        editButton.active = editButton.visible && !editingComponents && !menu.getRule(selected).prototype().isEmpty();
        clearButton.active = !editingComponents && !menu.getRule(selected).isEmpty();
        boolean tags = !editingComponents && tagInput();
        tagField.visible = tags;
        tagCycle.visible = tags;
        tagApply.visible = tags;
        if (!tags) tagField.setFocused(false);
        componentField.visible = editingComponents;
        saveComponents.visible = editingComponents;
        cancelComponents.visible = editingComponents;
        previousPage.visible = nextPage.visible = !editingComponents && pageCount() > 1;
        previousPage.active = page > 0;
        nextPage.active = page + 1 < pageCount();
    }

    public record GhostTarget(int slot, Rect2i area) {}
    private boolean canFitSidePanel() { return width >= imageWidth + SIDE_PANEL_WIDTH + SIDE_PANEL_GAP + 8 + (menu.isUniversal() ? 46 : 0); }
    private int sidePanelX() { return leftPos - SIDE_PANEL_WIDTH - SIDE_PANEL_GAP; }
    private int sidePanelY() { return topPos + 24; }
    private boolean sidePanelVisible() { return sidesOpen && menu.extractionEnabled() && canFitSidePanel(); }
    /** Let JEI reserve the tab and expanded panel instead of drawing ingredients over them. */
    public List<Rect2i> extraAreas() {
        var areas = new ArrayList<Rect2i>();
        if (menu.isUniversal()) areas.add(new Rect2i(leftPos + imageWidth + 2, topPos + 20, 42, 46));
        if (menu.extractionEnabled()) {
            areas.add(new Rect2i(leftPos - 22, topPos + 2, 20, 20));
            if (sidePanelVisible()) areas.add(new Rect2i(sidePanelX(), sidePanelY(), SIDE_PANEL_WIDTH, SIDE_PANEL_HEIGHT));
        }
        return areas;
    }
    public List<GhostTarget> ghostTargets() {
        if (editingComponents) return List.of();
        var targets = new ArrayList<GhostTarget>();
        for (int i = 0; i < visibleCount(); i++)
            targets.add(new GhostTarget(page * ChuteScreenHandler.PAGE_SIZE + i,
                    new Rect2i(leftPos + 8 + i % 9 * 18, topPos + ChuteScreenHandler.FILTER_Y + i / 9 * 18, 16, 16)));
        return targets;
    }

    /** Used both for real cursor marking and JEI's ghost-ingredient drop. Never changes the cursor stack. */
    public void acceptGhost(int index, ItemStack stack) {
        if (index < 0 || index >= menu.getFilterSlotCount() || stack.isEmpty() || editingComponents) return;
        selected = index;
        if (tagInput()) {
            var tags = markerTags(stack);
            candidateTags = tags;
            if (tags.isEmpty()) { fail(tr("no_tags").getString()); return; }
            String choice = tags.contains(tagField.getValue()) ? tagField.getValue() : tags.getFirst();
            tagField.setValue(choice);
            sendRule(index, tagRule(choice));
        } else if (menu.isFluidTab()) {
            var fluid = pureneko.conveyor_belt_plus.util.FluidPackets.marker(stack);
            if (fluid.isEmpty()) { fail(tr("fluid_marker_required").getString()); return; }
            sendRule(index, FilterRule.fluid(fluid));
        } else sendRule(index, FilterRule.item(stack, inputKind == FilterRule.Kind.COMPONENTS));
        updateWidgets();
    }

    public void acceptFluidGhost(int index, net.neoforged.neoforge.fluids.FluidStack fluid) {
        if (menu.isFluidTab() && !fluid.isEmpty()) acceptGhost(index, pureneko.conveyor_belt_plus.util.FluidPackets.create(fluid));
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (sidePanelVisible() && new Rect2i(sidePanelX(), sidePanelY(), SIDE_PANEL_WIDTH, SIDE_PANEL_HEIGHT)
                .contains((int) mouseX, (int) mouseY)) {
            for (var face : sideButtons) if (face.mouseClicked(mouseX, mouseY, button)) return true;
            return true; // Panel background is part of the menu, never an outside/drop click.
        }
        if (!editingComponents) for (var target : ghostTargets()) {
            if (!target.area.contains((int) mouseX, (int) mouseY)) continue;
            selected = target.slot;
            if (button == 1) sendRule(selected, FilterRule.EMPTY);
            else if (button == 0 && !menu.getCarried().isEmpty()) acceptGhost(selected, menu.getCarried());
            else if (button == 0) select(selected);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        for (var area : extraAreas()) if (area.contains((int) mouseX, (int) mouseY)) return false;
        return super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editingComponents) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) cancelComponents();
            else componentField.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (tagField != null && tagField.visible && tagField.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) tagField.setFocused(false);
            else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) applyTag();
            else tagField.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
        if (sidePanelVisible()) {
            int left = sidePanelX(), top = sidePanelY();
            context.fill(left, top, left + SIDE_PANEL_WIDTH, top + SIDE_PANEL_HEIGHT, 0xFF373737);
            context.fill(left, top, left + SIDE_PANEL_WIDTH - 1, top + SIDE_PANEL_HEIGHT - 1, 0xFFFFFFFF);
            context.fill(left + 2, top + 2, left + SIDE_PANEL_WIDTH - 2, top + SIDE_PANEL_HEIGHT - 2, 0xFFC6C6C6);
            context.drawString(font, tr("extraction.title"), left + 8, top + 7, 0x404040, false);
            context.drawCenteredString(font, tr("extraction.count", Integer.bitCount(menu.getExtractionSides())),
                    left + SIDE_PANEL_WIDTH / 2, top + 98, 0xFFFFFFFF);
        }
        int inventoryTop = menu.getPlayerInventoryY() - 14;
        context.blit(TEXTURE, leftPos, topPos, 0, 0, 176, 17);
        for (int row = 17; row < inventoryTop; row++)
            context.blit(TEXTURE, leftPos, topPos + row, 0, 5, 176, 1);
        context.blit(TEXTURE, leftPos, topPos + inventoryTop, 0, 126, 176, 96);
        for (int i = 0; i < visibleCount(); i++) {
            int xx = leftPos + 8 + i % 9 * 18, yy = topPos + ChuteScreenHandler.FILTER_Y + i / 9 * 18;
            int index = page * ChuteScreenHandler.PAGE_SIZE + i;
            context.blit(TEXTURE, xx - 1, yy - 1, 7, 17, 18, 18);
            if (index == selected) context.renderOutline(xx - 1, yy - 1, 18, 18, 0xFFFFFF80);
            var rule = menu.getRule(index);
            if (!rule.isEmpty()) {
                if (rule.isFluidRule()) drawFluidRule(context, rule, xx, yy);
                else context.renderItem(rule.icon(), xx, yy);
                if (rule.isTagRule() || rule.kind() == FilterRule.Kind.COMPONENTS)
                    context.drawString(font, rule.isTagRule() ? "#" : "C", xx + 10, yy + 8, 0xFFFFFFFF, true);
            }
        }
    }

    @Override protected void renderLabels(GuiGraphics context, int mouseX, int mouseY) {
        context.drawString(font, title, 8, 6, 0x404040, false);
        context.drawString(font, localError.isEmpty() ? tr("rules_count", menu.usedRules(), menu.getFilterSlotCount())
                : tr("invalid_rule_short"), 8, 40, localError.isEmpty() ? 0x404040 : 0xAA0000, false);
        if (!editingComponents && !tagInput())
            context.drawString(font, tr("ghost_help"), 8, 111, 0x404040, false);
        if (!editingComponents)
            context.drawCenteredString(font, tr("selected_page", selected + 1, page + 1, pageCount()),
                    88, 132, 0xFFFFFFFF);
        context.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0x404040, false);
    }

    private void drawFluidRule(GuiGraphics context, FilterRule rule, int xx, int yy) {
        var fluid = rule.fluidIcon();
        if (fluid.isEmpty()) return;
        var extension = net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid.getFluid());
        var texture = extension.getStillTexture(fluid);
        if (texture == null) return;
        var sprite = minecraft.getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(texture);
        int color = extension.getTintColor(fluid);
        context.blit(xx, yy, 0, 16, 16, sprite, ((color >> 16) & 255) / 255f,
                ((color >> 8) & 255) / 255f, (color & 255) / 255f, ((color >>> 24) & 255) / 255f);
    }

    @Override public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (seenFluid != menu.isFluidTab()) {
            seenFluid = menu.isFluidTab();
            page = selected = 0;
            inputKind = seenFluid ? FilterRule.Kind.FLUID : FilterRule.Kind.ITEM;
            editingComponents = false;
            candidateTags = List.of();
            tagField.setValue("");
        }
        if (menu.clientRevision() != seenRevision) {
            seenRevision = menu.clientRevision();
            localError = menu.error();
            if (!editingComponents && !menu.getRule(selected).isEmpty()) {
                inputKind = menu.getRule(selected).kind();
                if (tagInput()) tagField.setValue(menu.getRule(selected).tagId());
            }
        }
        updateWidgets();
        super.render(context, mouseX, mouseY, delta);
        renderTooltip(context, mouseX, mouseY);
        if (!localError.isEmpty() && mouseX >= leftPos + 8 && mouseX < leftPos + 168 && mouseY >= topPos + 39 && mouseY < topPos + 50)
            context.renderTooltip(font, Component.literal(localError), mouseX, mouseY);
        if (!editingComponents) for (var target : ghostTargets()) if (target.area.contains(mouseX, mouseY)) {
            var rule = menu.getRule(target.slot);
            var lines = new ArrayList<Component>();
            lines.add(kindText(rule.kind()));
            if (rule.isTagRule()) lines.add(Component.literal("#" + rule.tagId()));
            else if (rule.isFluidRule() && !rule.fluidIcon().isEmpty()) lines.add(rule.fluidIcon().getHoverName());
            else if (!rule.isEmpty()) lines.add(rule.prototype().getHoverName());
            lines.add(tr("rule_controls"));
            context.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }
}
