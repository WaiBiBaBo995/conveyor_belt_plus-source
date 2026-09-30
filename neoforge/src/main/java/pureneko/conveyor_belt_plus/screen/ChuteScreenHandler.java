package pureneko.conveyor_belt_plus.screen;

import pureneko.conveyor_belt_plus.util.ExtractionSide;
import pureneko.conveyor_belt_plus.registry.ScreenContent;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity;
import pureneko.conveyor_belt_plus.config.ConveyorConfig;
import pureneko.conveyor_belt_plus.filter.FilterRule;
import pureneko.conveyor_belt_plus.network.FilterNetworking;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Ghost rules are not inventory slots: they can never be extracted, shift-clicked or duplicated. */
public class ChuteScreenHandler extends AbstractContainerMenu {
    public static final int MODE_BUTTON = 0;
    public static final int REDSTONE_BUTTON = 1;
    public static final int EXTRACTION_BUTTON_BASE = 2;
    public static final int ITEM_TAB = 20, FLUID_TAB = 21;
    public static final int FILTER_Y = 50;
    public static final int PAGE_SIZE = 18;
    private final Player owner;
    private final BlockPos blockPos;
    private final ChuteBlockEntity chute;
    private final int itemLimit, fluidLimit;
    private final pureneko.conveyor_belt_plus.blocks.ChuteKind kind;
    private boolean fluid;
    private final FilterRule[] displayedRules;
    private boolean whitelist;
    private int extractionSides = ExtractionSide.DEFAULT_MASK;
    private boolean extractionEnabled;
    private Direction facing = Direction.NORTH;
    private pureneko.conveyor_belt_plus.util.RedstoneControl.Mode redstoneMode = pureneko.conveyor_belt_plus.util.RedstoneControl.Mode.ALWAYS;
    private long lastSentRevision = Long.MIN_VALUE;
    private long clientRevision;
    private String error = "";

    public ChuteScreenHandler(int syncId, Inventory inventory, ChuteBlockEntity chute) {
        this(syncId, inventory, chute.getBlockPos(), chute.getKind(), chute.getFilterSlotCount(false), chute.getFilterSlotCount(true));
    }

    public ChuteScreenHandler(int syncId, Inventory inventory, FriendlyByteBuf data) {
        this(syncId, inventory, data.readBlockPos(), data.readEnum(pureneko.conveyor_belt_plus.blocks.ChuteKind.class), data.readVarInt(), data.readVarInt());
    }

    private ChuteScreenHandler(int syncId, Inventory inventory, BlockPos pos, pureneko.conveyor_belt_plus.blocks.ChuteKind kind, int serverItemLimit, int serverFluidLimit) {
        super(ScreenContent.CHUTE.get(), syncId);
        if (serverItemLimit < 1 || serverItemLimit > ConveyorConfig.MAX_FILTER_RULES
                || serverFluidLimit < 1 || serverFluidLimit > ConveyorConfig.MAX_FILTER_RULES)
            throw new IllegalArgumentException("Invalid server filter limit");
        owner = inventory.player;
        blockPos = pos;
        var entity = owner.level().getBlockEntity(pos);
        chute = entity instanceof ChuteBlockEntity candidate ? candidate : null;
        this.kind = kind;
        fluid = kind == pureneko.conveyor_belt_plus.blocks.ChuteKind.FLUID;
        itemLimit = serverItemLimit;
        fluidLimit = serverFluidLimit;
        displayedRules = new FilterRule[Math.max(itemLimit, fluidLimit)];
        Arrays.fill(displayedRules, FilterRule.EMPTY);
        if (chute != null) {
            for (int i = 0; i < getFilterSlotCount(); i++) displayedRules[i] = chute.getRule(fluid, i);
            whitelist = chute.isWhitelistMode(fluid);
            redstoneMode = chute.getRedstoneMode(fluid);
            extractionSides = chute.getExtractionSides(fluid);
            facing = chute.getOwnFacing();
        }
        extractionEnabled = ConveyorConfig.extractionSidesEnabled();
        addDataSlot(new net.minecraft.world.inventory.DataSlot() {
            @Override public int get() { return getRedstoneMode().ordinal(); }
            @Override public void set(int value) { redstoneMode = pureneko.conveyor_belt_plus.util.RedstoneControl.Mode.fromId(value); }
        });
        addDataSlot(new net.minecraft.world.inventory.DataSlot() {
            @Override public int get() { return getExtractionSides(); }
            @Override public void set(int value) { extractionSides = ExtractionSide.savedMask(value); }
        });
        addDataSlot(new net.minecraft.world.inventory.DataSlot() {
            @Override public int get() { return extractionEnabled() ? 1 : 0; }
            @Override public void set(int value) { extractionEnabled = value == 1; }
        });
        addDataSlot(new net.minecraft.world.inventory.DataSlot() {
            @Override public int get() { return getFacing().get3DDataValue(); }
            @Override public void set(int value) {
                var direction = Direction.from3DDataValue(value);
                facing = direction.getAxis().isHorizontal() ? direction : Direction.NORTH;
            }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, getPlayerInventoryY() + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col, 8 + col * 18, getPlayerInventoryY() + 58));
    }

    public int getFilterSlotCount() { return fluid ? fluidLimit : itemLimit; }
    public boolean isFluidTab() { return fluid; }
    public boolean isUniversal() { return kind == pureneko.conveyor_belt_plus.blocks.ChuteKind.UNIVERSAL; }
    public int getExtractionSides() {
        return !owner.level().isClientSide && chute != null ? chute.getExtractionSides(fluid) : extractionSides;
    }
    public boolean extractionEnabled() {
        return !owner.level().isClientSide ? ConveyorConfig.extractionSidesEnabled() : extractionEnabled;
    }
    public Direction getFacing() { return !owner.level().isClientSide && chute != null ? chute.getOwnFacing() : facing; }
    public int getPlayerInventoryY() { return 158; }
    public boolean isWhitelistMode() { return whitelist; }
    public pureneko.conveyor_belt_plus.util.RedstoneControl.Mode getRedstoneMode() {
        return !owner.level().isClientSide && chute != null ? chute.getRedstoneMode(fluid) : redstoneMode;
    }
    public long clientRevision() { return clientRevision; }
    public String error() { return error; }
    public FilterRule getRule(int slot) { return slot >= 0 && slot < getFilterSlotCount() ? displayedRules[slot] : FilterRule.EMPTY; }
    public int usedRules() { return (int) Arrays.stream(displayedRules, 0, getFilterSlotCount()).filter(rule -> !rule.isEmpty()).count(); }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == ITEM_TAB || id == FLUID_TAB) {
            if (!isUniversal() || !stillValid(player)) return false;
            if (!player.level().isClientSide) {
                fluid = id == FLUID_TAB;
                lastSentRevision = Long.MIN_VALUE;
                broadcastChanges();
            }
            return true;
        }
        var side = ExtractionSide.byId(id - EXTRACTION_BUTTON_BASE);
        if ((id != MODE_BUTTON && id != REDSTONE_BUTTON && side == null) || !stillValid(player)) return false;
        if (side != null && !extractionEnabled()) return false;
        if (!player.level().isClientSide) {
            if (side != null) {
                chute.setExtractionSides(fluid, chute.getExtractionSides(fluid) ^ side.bit());
                broadcastChanges();
            } else if (id == REDSTONE_BUTTON) {
                chute.setRedstoneMode(fluid, chute.getRedstoneMode(fluid).next());
                broadcastChanges();
            } else {
                chute.setWhitelistMode(fluid, !chute.isWhitelistMode(fluid));
                sendRules("");
            }
        }
        return true;
    }

    public void applyEdit(Player player, int slot, String text) { applyEdit(player, fluid, slot, text); }
    public void applyEdit(Player player, boolean fluidTab, int slot, String text) {
        if (player.level().isClientSide || !stillValid(player) || fluidTab != fluid) return;
        if (slot < 0 || slot >= getFilterSlotCount() || slot >= chute.getFilterSlotCount(fluid)) {
            sendRules("Filter limit exceeded");
            return;
        }
        try {
            var rule = FilterRule.fromText(text, player.registryAccess());
            rule.toText(player.registryAccess());
            chute.setRule(fluid, slot, rule);
            sendRules("");
        } catch (IllegalArgumentException ex) {
            String message = String.valueOf(ex.getMessage());
            sendRules(message.substring(0, Math.min(256, message.length())));
        }
    }

    public void applySnapshot(boolean fluidTab, boolean whitelist, List<String> rules, String error) {
        if (!owner.level().isClientSide || !kind.supports(fluidTab) || rules.size() != (fluidTab ? fluidLimit : itemLimit)) return;
        fluid = fluidTab;
        var decoded = new FilterRule[getFilterSlotCount()];
        for (int i = 0; i < getFilterSlotCount(); i++) decoded[i] = FilterRule.fromText(rules.get(i), owner.registryAccess());
        Arrays.fill(displayedRules, FilterRule.EMPTY);
        System.arraycopy(decoded, 0, displayedRules, 0, decoded.length);
        this.whitelist = whitelist;
        this.error = error;
        clientRevision++;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (owner instanceof ServerPlayer && chute != null && lastSentRevision != chute.getFilterRevision())
            sendRules("");
    }

    private void sendRules(String error) {
        if (!(owner instanceof ServerPlayer player)) return;
        var rules = new ArrayList<String>(getFilterSlotCount());
        for (int i = 0; i < getFilterSlotCount(); i++) {
            try { rules.add(chute.getRule(fluid, i).toText(owner.registryAccess())); }
            catch (IllegalArgumentException ex) {
                // Keep an oversized legacy rule in the world; never crash menu synchronization.
                rules.add(FilterRule.EMPTY.toText(owner.registryAccess()));
                error = "Slot " + (i + 1) + ": saved rule is too large to display; it is preserved. Right-click to clear or replace it.";
            }
        }
        FilterNetworking.sendSnapshot(player, new FilterNetworking.Snapshot(containerId, fluid, chute.isWhitelistMode(fluid), rules, error));
        lastSentRevision = chute.getFilterRevision();
    }

    @Override public boolean stillValid(Player player) {
        return player == owner && chute != null && chute.getLevel() == player.level()
                && chute.getFilterSlotCount(false) == itemLimit && chute.getFilterSlotCount(true) == fluidLimit
                && player.level().getBlockEntity(blockPos) == chute
                && blockPos.distToCenterSqr(player.position()) <= 64;
    }

    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
