package pureneko.conveyor_belt_plus.client.screens;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.Rect2i;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import pureneko.conveyor_belt_plus.filter.FilterRule;
import pureneko.conveyor_belt_plus.network.FilterNetworking;
import pureneko.conveyor_belt_plus.screen.ChuteScreenHandler;
import pureneko.conveyor_belt_plus.util.ExtractionSide;

import java.util.ArrayList;
import java.util.List;

public class ChuteScreen extends HandledScreen<ChuteScreenHandler> {
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/gui/container/generic_54.png");
    private ButtonWidget modeButton, kindButton, editButton, clearButton, tagCycle, tagApply;
    private ButtonWidget previousPage, nextPage, saveComponents, cancelComponents;
    private RedstoneModeButton redstoneButton;
    private TextFieldWidget tagField, componentField;
    private int page, selected;
    private FilterRule.Kind inputKind = FilterRule.Kind.ITEM;
    private boolean editingComponents;
    private String localError = "";
    private long seenRevision = -1;
    private List<String> candidateTags = List.of();
    private static final int SIDE_PANEL_WIDTH = 112, SIDE_PANEL_HEIGHT = 112, SIDE_PANEL_GAP = 4;
    private ButtonWidget settingsButton;
    private final List<ExtractionSideButton> sideButtons = new ArrayList<>();
    private boolean sidesOpen;

    public ChuteScreen(ChuteScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 176;
        backgroundHeight = handler.getPlayerInventoryY() + 82;
        playerInventoryTitleY = handler.getPlayerInventoryY() - 12;
    }

    private Text tr(String key, Object... args) { return Text.translatable("screen.conveyor_belt_plus." + key, args); }

    @Override protected void init() {
        String oldTag = tagField == null ? "" : tagField.getText();
        String oldComponents = componentField == null ? "" : componentField.getText();
        super.init();
        // Keep the original inventory layout; shift the main window only when the left panel needs room.
        if (sidesOpen && canFitSidePanel())
            x = Math.max(x, SIDE_PANEL_WIDTH + SIDE_PANEL_GAP + 4);
        modeButton = addDrawableChild(ButtonWidget.builder(modeText(), button -> {
            if (client != null && client.interactionManager != null)
                client.interactionManager.clickButton(handler.syncId, ChuteScreenHandler.MODE_BUTTON);
        }).dimensions(x + 8, y + 20, 136, 20).tooltip(Tooltip.of(tr("filter_hint"))).build());
        redstoneButton = addDrawableChild(new RedstoneModeButton(x + 148, y + 20, handler::getRedstoneMode, button -> {
            if (client != null && client.interactionManager != null)
                client.interactionManager.clickButton(handler.syncId, ChuteScreenHandler.REDSTONE_BUTTON);
        }));
        kindButton = addDrawableChild(ButtonWidget.builder(Text.empty(), button -> cycleKind())
                .dimensions(x + 8, y + 100, 78, 18).build());
        editButton = addDrawableChild(ButtonWidget.builder(tr("edit_components"), button -> beginComponents())
                .dimensions(x + 88, y + 100, 38, 18).tooltip(Tooltip.of(tr("components_help"))).build());
        clearButton = addDrawableChild(ButtonWidget.builder(tr("clear_rule"), button -> sendRule(selected, FilterRule.EMPTY))
                .dimensions(x + 128, y + 100, 40, 18).build());
        tagField = addDrawableChild(new TextFieldWidget(textRenderer, x + 8, y + 122, 112, 18, tr("tag_id")));
        tagField.setMaxLength(256);
        tagField.setChangedListener(text -> tagField.setSuggestion(pureneko.conveyor_belt_plus.util.TagInputHint.forText(text)));
        tagField.setText(oldTag);
        tagField.setSuggestion(pureneko.conveyor_belt_plus.util.TagInputHint.forText(oldTag));
        tagCycle = addDrawableChild(ButtonWidget.builder(Text.literal(">"), button -> cycleTag())
                .dimensions(x + 124, y + 122, 20, 18).tooltip(Tooltip.of(tr("cycle_tag"))).build());
        tagApply = addDrawableChild(ButtonWidget.builder(Text.literal("+"), button -> applyTag())
                .dimensions(x + 148, y + 122, 20, 18).tooltip(Tooltip.of(tr("apply_tag"))).build());
        previousPage = addDrawableChild(ButtonWidget.builder(Text.literal("<"), button -> changePage(-1))
                .dimensions(x + 8, y + 144, 20, 16).build());
        nextPage = addDrawableChild(ButtonWidget.builder(Text.literal(">"), button -> changePage(1))
                .dimensions(x + 148, y + 144, 20, 16).build());
        componentField = addDrawableChild(new TextFieldWidget(textRenderer, x + 8, y + 122, 160, 18, tr("component_snbt")));
        componentField.setMaxLength(FilterRule.MAX_COMPONENT_TEXT);
        componentField.setTooltip(Tooltip.of(tr("components_help")));
        componentField.setText(oldComponents);
        saveComponents = addDrawableChild(ButtonWidget.builder(tr("save_components"), button -> saveComponents())
                .dimensions(x + 8, y + 144, 76, 18).build());
        cancelComponents = addDrawableChild(ButtonWidget.builder(tr("cancel"), button -> cancelComponents())
                .dimensions(x + 92, y + 144, 76, 18).build());
        settingsButton = addDrawableChild(ButtonWidget.builder(Text.literal("⚙"), button -> {
            sidesOpen = !sidesOpen;
            clearAndInit();
        }).dimensions(x - 22, y + 2, 20, 20)
                .tooltip(Tooltip.of(tr(canFitSidePanel() ? "extraction.settings" : "extraction.small_window"))).build());
        sideButtons.clear();
        for (var side : ExtractionSide.values()) {
            sideButtons.add(addDrawableChild(new ExtractionSideButton(sidePanelX() + 24 + side.column() * 22,
                    sidePanelY() + 23 + side.row() * 22, side, handler, button -> {
                if (client != null && client.interactionManager != null)
                    client.interactionManager.clickButton(handler.syncId, ChuteScreenHandler.EXTRACTION_BUTTON_BASE + side.ordinal());
            })));
        }
        updateWidgets();
    }

    private Text modeText() {
        return tr(handler.isWhitelistMode() ? "filter_whitelist" : "filter_blacklist");
    }

    private Text kindText(FilterRule.Kind kind) {
        return tr("rule_" + (kind == FilterRule.Kind.EMPTY ? "item" : kind.name().toLowerCase(java.util.Locale.ROOT)));
    }

    private void select(int index) {
        selected = index;
        var rule = handler.getRule(index);
        candidateTags = markerTags(rule.icon());
        if (!rule.isEmpty()) inputKind = rule.kind();
        tagField.setText(rule.kind() == FilterRule.Kind.TAG ? rule.tagId() : "");
        localError = "";
        updateWidgets();
    }

    private void cycleKind() {
        inputKind = switch (inputKind) {
            case ITEM, EMPTY -> FilterRule.Kind.COMPONENTS;
            case COMPONENTS -> FilterRule.Kind.TAG;
            case TAG -> FilterRule.Kind.ITEM;
        };
        var marker = handler.getRule(selected).prototype();
        if (inputKind != FilterRule.Kind.TAG && !marker.isEmpty())
            sendRule(selected, FilterRule.item(marker, inputKind == FilterRule.Kind.COMPONENTS));
        updateWidgets();
    }

    private List<String> markerTags(ItemStack marker) {
        if (marker.isEmpty()) return List.of();
        return marker.streamTags().map(tag -> tag.id().toString()).sorted().toList();
    }

    private void cycleTag() {
        var marker = handler.getRule(selected).icon();
        var tags = candidateTags.isEmpty() ? markerTags(marker) : candidateTags;
        if (tags.isEmpty()) { fail(tr("no_tags").getString()); return; }
        int next = (tags.indexOf(tagField.getText()) + 1) % tags.size();
        tagField.setText(tags.get(next));
    }

    private void applyTag() {
        try { sendRule(selected, FilterRule.tag(tagField.getText())); }
        catch (IllegalArgumentException ex) { fail(ex.getMessage()); }
    }

    private void beginComponents() {
        var marker = handler.getRule(selected).prototype();
        if (marker.isEmpty() || client == null || client.world == null) return;
        String text;
        try { text = FilterRule.componentsText(marker, client.world.getRegistryManager()); }
        catch (IllegalArgumentException ex) { fail(ex.getMessage()); return; }
        if (text.length() > FilterRule.MAX_COMPONENT_TEXT) { fail(tr("components_too_large").getString()); return; }
        componentField.setText(text);
        editingComponents = true;
        localError = "";
        updateWidgets();
        setFocused(componentField);
        componentField.setFocused(true);
    }

    private void saveComponents() {
        try {
            var rule = FilterRule.editComponents(handler.getRule(selected).prototype(), componentField.getText(),
                    client.world.getRegistryManager());
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
        if (client == null || client.world == null) return;
        try {
            FilterNetworking.sendEdit(handler.syncId, index, rule.toText(client.world.getRegistryManager()));
            localError = "";
        } catch (IllegalArgumentException ex) { fail(ex.getMessage()); }
    }

    private int pageCount() { return (handler.getFilterSlotCount() + ChuteScreenHandler.PAGE_SIZE - 1) / ChuteScreenHandler.PAGE_SIZE; }
    private int visibleCount() { return Math.min(ChuteScreenHandler.PAGE_SIZE, handler.getFilterSlotCount() - page * ChuteScreenHandler.PAGE_SIZE); }
    private void changePage(int delta) {
        page = Math.clamp(page + delta, 0, pageCount() - 1);
        select(page * ChuteScreenHandler.PAGE_SIZE);
    }

    private void updateWidgets() {
        if (modeButton == null) return;
        settingsButton.visible = handler.extractionEnabled();
        settingsButton.active = !editingComponents && canFitSidePanel();
        for (var button : sideButtons) {
            button.visible = sidePanelVisible();
            button.active = !editingComponents && handler.extractionEnabled();
        }
        modeButton.setMessage(modeText());
        modeButton.active = !editingComponents;
        redstoneButton.active = !editingComponents;
        kindButton.setMessage(kindText(inputKind));
        kindButton.active = !editingComponents;
        editButton.active = !editingComponents && !handler.getRule(selected).prototype().isEmpty();
        clearButton.active = !editingComponents && !handler.getRule(selected).isEmpty();
        boolean tags = !editingComponents && inputKind == FilterRule.Kind.TAG;
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
    private boolean canFitSidePanel() { return width >= backgroundWidth + SIDE_PANEL_WIDTH + SIDE_PANEL_GAP + 8; }
    private int sidePanelX() { return x - SIDE_PANEL_WIDTH - SIDE_PANEL_GAP; }
    private int sidePanelY() { return y + 24; }
    private boolean sidePanelVisible() { return sidesOpen && handler.extractionEnabled() && canFitSidePanel(); }
    /** Let JEI reserve the tab and expanded panel instead of drawing ingredients over them. */
    public List<Rect2i> extraAreas() {
        if (!handler.extractionEnabled()) return List.of();
        var gear = new Rect2i(x - 22, y + 2, 20, 20);
        return sidePanelVisible() ? List.of(gear, new Rect2i(sidePanelX(), sidePanelY(), SIDE_PANEL_WIDTH, SIDE_PANEL_HEIGHT))
                : List.of(gear);
    }
    public List<GhostTarget> ghostTargets() {
        if (editingComponents) return List.of();
        var targets = new ArrayList<GhostTarget>();
        for (int i = 0; i < visibleCount(); i++)
            targets.add(new GhostTarget(page * ChuteScreenHandler.PAGE_SIZE + i,
                    new Rect2i(x + 8 + i % 9 * 18, y + ChuteScreenHandler.FILTER_Y + i / 9 * 18, 16, 16)));
        return targets;
    }

    /** Used both for real cursor marking and JEI's ghost-ingredient drop. Never changes the cursor stack. */
    public void acceptGhost(int index, ItemStack stack) {
        if (index < 0 || index >= handler.getFilterSlotCount() || stack.isEmpty() || editingComponents) return;
        selected = index;
        if (inputKind == FilterRule.Kind.TAG) {
            var tags = markerTags(stack);
            candidateTags = tags;
            if (tags.isEmpty()) { fail(tr("no_tags").getString()); return; }
            String choice = tags.contains(tagField.getText()) ? tagField.getText() : tags.getFirst();
            tagField.setText(choice);
            sendRule(index, FilterRule.tag(choice));
        } else sendRule(index, FilterRule.item(stack, inputKind == FilterRule.Kind.COMPONENTS));
        updateWidgets();
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
            else if (button == 0 && !handler.getCursorStack().isEmpty()) acceptGhost(selected, handler.getCursorStack());
            else if (button == 0) select(selected);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override protected boolean isClickOutsideBounds(double mouseX, double mouseY, int left, int top, int button) {
        for (var area : extraAreas()) if (area.contains((int) mouseX, (int) mouseY)) return false;
        return super.isClickOutsideBounds(mouseX, mouseY, left, top, button);
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

    @Override protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        if (sidePanelVisible()) {
            int left = sidePanelX(), top = sidePanelY();
            context.fill(left, top, left + SIDE_PANEL_WIDTH, top + SIDE_PANEL_HEIGHT, 0xFF373737);
            context.fill(left, top, left + SIDE_PANEL_WIDTH - 1, top + SIDE_PANEL_HEIGHT - 1, 0xFFFFFFFF);
            context.fill(left + 2, top + 2, left + SIDE_PANEL_WIDTH - 2, top + SIDE_PANEL_HEIGHT - 2, 0xFFC6C6C6);
            context.drawText(textRenderer, tr("extraction.title"), left + 8, top + 7, 0x404040, false);
            context.drawCenteredTextWithShadow(textRenderer, tr("extraction.count", Integer.bitCount(handler.getExtractionSides())),
                    left + SIDE_PANEL_WIDTH / 2, top + 98, 0xFFFFFFFF);
        }
        int inventoryTop = handler.getPlayerInventoryY() - 14;
        context.drawTexture(TEXTURE, x, y, 0, 0, 176, 17);
        for (int row = 17; row < inventoryTop; row++)
            context.drawTexture(TEXTURE, x, y + row, 0, 5, 176, 1);
        context.drawTexture(TEXTURE, x, y + inventoryTop, 0, 126, 176, 96);
        for (int i = 0; i < visibleCount(); i++) {
            int xx = x + 8 + i % 9 * 18, yy = y + ChuteScreenHandler.FILTER_Y + i / 9 * 18;
            int index = page * ChuteScreenHandler.PAGE_SIZE + i;
            context.drawTexture(TEXTURE, xx - 1, yy - 1, 7, 17, 18, 18);
            if (index == selected) context.drawBorder(xx - 1, yy - 1, 18, 18, 0xFFFFFF80);
            var rule = handler.getRule(index);
            if (!rule.isEmpty()) {
                context.drawItem(rule.icon(), xx, yy);
                if (rule.kind() == FilterRule.Kind.TAG || rule.kind() == FilterRule.Kind.COMPONENTS)
                    context.drawText(textRenderer, rule.kind() == FilterRule.Kind.TAG ? "#" : "C", xx + 10, yy + 8, 0xFFFFFFFF, true);
            }
        }
    }

    @Override protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, title, 8, 6, 0x404040, false);
        context.drawText(textRenderer, localError.isEmpty() ? tr("rules_count", handler.usedRules(), handler.getFilterSlotCount())
                : tr("invalid_rule_short"), 8, 45, localError.isEmpty() ? 0x404040 : 0xAA0000, false);
        if (!editingComponents && inputKind != FilterRule.Kind.TAG)
            context.drawText(textRenderer, tr("ghost_help"), 8, 126, 0x404040, false);
        if (!editingComponents)
            context.drawCenteredTextWithShadow(textRenderer, tr("selected_page", selected + 1, page + 1, pageCount()),
                    88, 148, 0xFFFFFFFF);
        context.drawText(textRenderer, playerInventoryTitle, 8, playerInventoryTitleY, 0x404040, false);
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (handler.clientRevision() != seenRevision) {
            seenRevision = handler.clientRevision();
            localError = handler.error();
            if (!editingComponents && !handler.getRule(selected).isEmpty()) {
                inputKind = handler.getRule(selected).kind();
                if (inputKind == FilterRule.Kind.TAG) tagField.setText(handler.getRule(selected).tagId());
            }
        }
        updateWidgets();
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
        if (!localError.isEmpty() && mouseX >= x + 8 && mouseX < x + 168 && mouseY >= y + 44 && mouseY < y + 55)
            context.drawTooltip(textRenderer, Text.literal(localError), mouseX, mouseY);
        if (!editingComponents) for (var target : ghostTargets()) if (target.area.contains(mouseX, mouseY)) {
            var rule = handler.getRule(target.slot);
            var lines = new ArrayList<Text>();
            lines.add(kindText(rule.kind()));
            if (rule.kind() == FilterRule.Kind.TAG) lines.add(Text.literal("#" + rule.tagId()));
            else if (!rule.isEmpty()) lines.add(rule.prototype().getName());
            lines.add(tr("rule_controls"));
            context.drawTooltip(textRenderer, lines, mouseX, mouseY);
        }
    }
}
