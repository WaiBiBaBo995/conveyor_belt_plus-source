package pureneko.conveyor_belt_plus.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import pureneko.conveyor_belt_plus.util.ExtractionSide;
import pureneko.conveyor_belt_plus.registry.ScreenContent;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity;
import pureneko.conveyor_belt_plus.config.ConveyorConfig;
import pureneko.conveyor_belt_plus.filter.FilterRule;
import pureneko.conveyor_belt_plus.network.FilterNetworking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Ghost rules are not inventory slots: they can never be extracted, shift-clicked or duplicated. */
public class ChuteScreenHandler extends ScreenHandler {
    public static final int MODE_BUTTON = 0;
    public static final int REDSTONE_BUTTON = 1;
    public static final int EXTRACTION_BUTTON_BASE = 2;
    public static final int FILTER_Y = 58;
    public static final int PAGE_SIZE = 18;
    private final PlayerEntity owner;
    private final BlockPos blockPos;
    private final ChuteBlockEntity chute;
    private final int limit;
    private final FilterRule[] displayedRules;
    private boolean whitelist;
    private int extractionSides = ExtractionSide.DEFAULT_MASK;
    private boolean extractionEnabled;
    private Direction facing = Direction.NORTH;
    private pureneko.conveyor_belt_plus.util.RedstoneControl.Mode redstoneMode = pureneko.conveyor_belt_plus.util.RedstoneControl.Mode.ALWAYS;
    private long lastSentRevision = Long.MIN_VALUE;
    private long clientRevision;
    private String error = "";

    public ChuteScreenHandler(int syncId, PlayerInventory inventory, ChuteBlockEntity chute) {
        this(syncId, inventory, chute.getPos(), chute.getChuteTier(), chute.getFilterSlotCount());
    }

    public ChuteScreenHandler(int syncId, PlayerInventory inventory, PacketByteBuf data) {
        this(syncId, inventory, data.readBlockPos(), data.readVarInt(), data.readVarInt());
    }

    private ChuteScreenHandler(int syncId, PlayerInventory inventory, BlockPos pos, int tier, int serverLimit) {
        super(ScreenContent.CHUTE.get(), syncId);
        if (serverLimit < 1 || serverLimit > ConveyorConfig.MAX_FILTER_RULES)
            throw new IllegalArgumentException("Invalid server filter limit");
        owner = inventory.player;
        blockPos = pos;
        var entity = owner.getWorld().getBlockEntity(pos);
        chute = entity instanceof ChuteBlockEntity candidate ? candidate : null;
        limit = serverLimit; // Never derive the client's layout from its local config file.
        displayedRules = new FilterRule[limit];
        Arrays.fill(displayedRules, FilterRule.EMPTY);
        if (chute != null) {
            for (int i = 0; i < limit; i++) displayedRules[i] = chute.getRule(i);
            whitelist = chute.isWhitelistMode();
            redstoneMode = chute.getRedstoneMode();
            extractionSides = chute.getExtractionSides();
            facing = chute.getOwnFacing();
        }
        extractionEnabled = ConveyorConfig.extractionSidesEnabled();
        addProperty(new net.minecraft.screen.Property() {
            @Override public int get() { return getRedstoneMode().ordinal(); }
            @Override public void set(int value) { redstoneMode = pureneko.conveyor_belt_plus.util.RedstoneControl.Mode.fromId(value); }
        });
        addProperty(new net.minecraft.screen.Property() {
            @Override public int get() { return getExtractionSides(); }
            @Override public void set(int value) { extractionSides = ExtractionSide.savedMask(value); }
        });
        addProperty(new net.minecraft.screen.Property() {
            @Override public int get() { return extractionEnabled() ? 1 : 0; }
            @Override public void set(int value) { extractionEnabled = value == 1; }
        });
        addProperty(new net.minecraft.screen.Property() {
            @Override public int get() { return getFacing().getId(); }
            @Override public void set(int value) {
                var direction = Direction.byId(value);
                facing = direction.getAxis().isHorizontal() ? direction : Direction.NORTH;
            }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, getPlayerInventoryY() + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col, 8 + col * 18, getPlayerInventoryY() + 58));
    }

    public int getFilterSlotCount() { return limit; }
    public int getExtractionSides() {
        return !owner.getWorld().isClient && chute != null ? chute.getExtractionSides() : extractionSides;
    }
    public boolean extractionEnabled() {
        return !owner.getWorld().isClient ? ConveyorConfig.extractionSidesEnabled() : extractionEnabled;
    }
    public Direction getFacing() { return !owner.getWorld().isClient && chute != null ? chute.getOwnFacing() : facing; }
    public int getPlayerInventoryY() { return 178; }
    public boolean isWhitelistMode() { return whitelist; }
    public pureneko.conveyor_belt_plus.util.RedstoneControl.Mode getRedstoneMode() {
        return !owner.getWorld().isClient && chute != null ? chute.getRedstoneMode() : redstoneMode;
    }
    public long clientRevision() { return clientRevision; }
    public String error() { return error; }
    public FilterRule getRule(int slot) { return slot >= 0 && slot < limit ? displayedRules[slot] : FilterRule.EMPTY; }
    public int usedRules() { return (int) Arrays.stream(displayedRules).filter(rule -> !rule.isEmpty()).count(); }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        var side = ExtractionSide.byId(id - EXTRACTION_BUTTON_BASE);
        if ((id != MODE_BUTTON && id != REDSTONE_BUTTON && side == null) || !canUse(player)) return false;
        if (side != null && !extractionEnabled()) return false;
        if (!player.getWorld().isClient) {
            if (side != null) {
                chute.setExtractionSides(chute.getExtractionSides() ^ side.bit());
                sendContentUpdates();
            } else if (id == REDSTONE_BUTTON) {
                chute.setRedstoneMode(chute.getRedstoneMode().next());
                sendContentUpdates();
            } else {
                chute.setWhitelistMode(!chute.isWhitelistMode());
                sendRules("");
            }
        }
        return true;
    }

    public void applyEdit(PlayerEntity player, int slot, String text) {
        if (player.getWorld().isClient || !canUse(player)) return;
        if (slot < 0 || slot >= limit || slot >= chute.getFilterSlotCount()) {
            sendRules("Filter limit exceeded");
            return;
        }
        try {
            var rule = FilterRule.fromText(text, player.getRegistryManager());
            rule.toText(player.getRegistryManager());
            chute.setRule(slot, rule);
            sendRules("");
        } catch (IllegalArgumentException ex) {
            String message = String.valueOf(ex.getMessage());
            sendRules(message.substring(0, Math.min(256, message.length())));
        }
    }

    public void applySnapshot(boolean whitelist, List<String> rules, String error) {
        if (!owner.getWorld().isClient || rules.size() != limit) return;
        var decoded = new FilterRule[limit];
        for (int i = 0; i < limit; i++) decoded[i] = FilterRule.fromText(rules.get(i), owner.getRegistryManager());
        System.arraycopy(decoded, 0, displayedRules, 0, limit);
        this.whitelist = whitelist;
        this.error = error;
        clientRevision++;
    }

    @Override
    public void sendContentUpdates() {
        super.sendContentUpdates();
        if (owner instanceof ServerPlayerEntity && chute != null && lastSentRevision != chute.getFilterRevision())
            sendRules("");
    }

    private void sendRules(String error) {
        if (!(owner instanceof ServerPlayerEntity player)) return;
        var rules = new ArrayList<String>(limit);
        for (int i = 0; i < limit; i++) {
            try { rules.add(chute.getRule(i).toText(owner.getRegistryManager())); }
            catch (IllegalArgumentException ex) {
                // Keep an oversized legacy rule in the world; never crash menu synchronization.
                rules.add(FilterRule.EMPTY.toText(owner.getRegistryManager()));
                error = "Slot " + (i + 1) + ": saved rule is too large to display; it is preserved. Right-click to clear or replace it.";
            }
        }
        FilterNetworking.sendSnapshot(player, new FilterNetworking.Snapshot(syncId, chute.isWhitelistMode(), rules, error));
        lastSentRevision = chute.getFilterRevision();
    }

    @Override public boolean canUse(PlayerEntity player) {
        return player == owner && chute != null && chute.getWorld() == player.getWorld()
                && chute.getFilterSlotCount() == limit
                && player.getWorld().getBlockEntity(blockPos) == chute
                && blockPos.getSquaredDistance(player.getPos()) <= 64;
    }

    @Override public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
}
