package pureneko.conveyor_belt_plus.blocks;

import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;
import pureneko.conveyor_belt_plus.registry.BlockContent;
import pureneko.conveyor_belt_plus.registry.BlockEntitiesContent;
import pureneko.conveyor_belt_plus.registry.ItemContent;
import pureneko.conveyor_belt_plus.forge.ConveyorItemApi;
import pureneko.conveyor_belt_plus.forge.ConveyorItemApi.InventoryStorage;
import pureneko.conveyor_belt_plus.util.BeltTiers;
import pureneko.conveyor_belt_plus.util.BeltTransport;
import pureneko.conveyor_belt_plus.util.BeltTransport.Step;
import pureneko.conveyor_belt_plus.util.ProgressAnimation;
import pureneko.conveyor_belt_plus.util.BeltVisualState;
import pureneko.conveyor_belt_plus.util.BeltQuad;
import pureneko.conveyor_belt_plus.util.BeltRenderClock;
import pureneko.conveyor_belt_plus.util.TransportStacks;
import pureneko.conveyor_belt_plus.blocks.BeltPickup.Access;
import pureneko.conveyor_belt_plus.config.ConveyorConfig;
import pureneko.conveyor_belt_plus.filter.FilterRule;
import pureneko.conveyor_belt_plus.filter.FilterRules;
import pureneko.conveyor_belt_plus.util.SplineUtil;
import pureneko.conveyor_belt_plus.util.SplineUtil.ArcLengthPath;
import pureneko.conveyor_belt_plus.util.ExtractionSide;
import pureneko.conveyor_belt_plus.util.FluidPackets;
import pureneko.conveyor_belt_plus.util.TierUpgrade;
import pureneko.conveyor_belt_plus.forge.ConveyorFluidApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import pureneko.conveyor_belt_plus.util.RedstoneControl;
import java.util.*;

public class ChuteBlockEntity extends BlockEntity implements BlockEntityTicker<ChuteBlockEntity>, ConveyorNode {

    // everything in this section is synced to the client
    private BlockPos target;
    private Direction targetFacing = Direction.NORTH;
    private List<BlockPos> midPoints = new ArrayList<>();
    // items that are in transit, and not in the queue yet. Key is the progress [0-1] along the current path;
    // first = at begin of transit path, near extraction point. Last = near target.
    private final Deque<BeltItem> movingItems = new ArrayDeque<>();

    // this is calculated on both the client and server
    private BeltData beltData;

    // used to check if a belt is used as target. Periodically updated on belt ends from the belt starts.
    private long lastTargetedTime;
    private BlockPos sourceBeltPos = BlockPos.ZERO;
    private int beltTier = BeltTiers.STANDARD;

    private long nextItemId;
    private final BeltVisualState visualState = new BeltVisualState();
    private final ChuteSettings itemSettings = new ChuteSettings();
    private final ChuteSettings fluidSettings = new ChuteSettings();
    private boolean nextFluid;
    private long filterRevision;

    public ChuteKind getKind() {
        return getBlockState().getBlock() instanceof ChuteBlock chute ? chute.getKind() : ChuteKind.ITEM;
    }
    public boolean defaultFluid() { return getKind() == ChuteKind.FLUID; }
    private ChuteSettings settings(boolean fluid) { return fluid ? fluidSettings : itemSettings; }

    // client only data, used for rendering
    public BeltQuad[] renderedModel;

    private boolean networkDirty = false;

    public ChuteBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesContent.CHUTE_BLOCK.get(), pos, state);
    }

    /** Pushes the block-entity state to tracking clients; a no-op on the client or a detached entity. */
    private void syncToClients() {
        if (level instanceof ServerLevel serverWorld) serverWorld.getChunkSource().blockChanged(worldPosition);
    }

    /** Marks the entity dirty and pushes the change to tracking clients. */
    private void markDirtyAndSync() {
        setChanged();
        syncToClients();
    }

    @Override
    public void tick(Level world, BlockPos pos, BlockState state, ChuteBlockEntity blockEntity) {
        if (world == null) return;
        updateRedstoneSignal(); // Receiving interfaces also need edge detection, before the no-outgoing early return.

        if (target == null || target.equals(BlockPos.ZERO)) {
            if (!world.isClientSide && !movingItems.isEmpty()) {
                dropContent(world, pos);
            }
            return;
        }

        if (beltData == null) {
            beltData = BeltData.create(this);
            syncToClients();
        }

        if (beltData == null) {
            if (!world.isClientSide) {
                // A route can become invalid after a support changes. Treat it as a broken belt
                // instead of allowing a malformed spline to remain rendered.
                dropContent(world, pos);
            }
            target = null;
            midPoints = new ArrayList<>();
            setChanged();
            return;
        }

        if (world.isClientSide) return;

        moveItemsOnBelt();
        loadItemsOnBelt();

        // refresh target
        if (world.getGameTime() % 19 == 0)
            assignTargetState(world);

        if (networkDirty) {
            syncToClients();
            networkDirty = false;
        }

    }

    public void dropContent(Level world, BlockPos pos) {
        if (world.isClientSide) return;
        var oldTarget = target;
        var oldSource = sourceBeltPos;
        // Detach first so reciprocal callbacks cannot drop the same packet or belt twice.
        target = null;
        midPoints = new ArrayList<>();
        beltData = null;
        renderedModel = null;
        sourceBeltPos = BlockPos.ZERO;
        lastTargetedTime = 0;
        if (!oldSource.equals(BlockPos.ZERO)) {
            var source = ConveyorNodeUtil.get(world, oldSource);
            if (source != null && source != this) source.disconnectOutgoingTo(this.worldPosition);
        }
        if (oldTarget != null && !oldTarget.equals(BlockPos.ZERO)) {
            var destination = ConveyorNodeUtil.get(world, oldTarget);
            if (destination != null) destination.disconnectIncomingFrom(this.worldPosition);
        }
        for (var item : movingItems) TransportStacks.drop(world, pos, item.stack);
        if (!movingItems.isEmpty() || (oldTarget != null && !oldTarget.equals(BlockPos.ZERO)))
            TransportStacks.drop(world, pos, ItemContent.beltStackForTier(beltTier));
        movingItems.clear();
        networkDirty = true;
        markDirtyAndSync();
    }

    // notifies the belt end entity that the current entity is the sender to it
    private void assignTargetState(Level world) {
        var beltTarget = ConveyorNodeUtil.get(world, target);
        if (beltTarget != null) {
            beltTarget.markTargeted(worldPosition);
        } else {
            target = null;
            midPoints = new ArrayList<>();
        }
    }

    private void moveItemsOnBelt() {
        var step = BeltTransport.tickWithMotion(movingItems, beltData.totalLength(), BeltTiers.speed(beltTier),
                packet -> ConveyorNodeUtil.accept(level, target, targetFacing, packet.stack));
        visualState.advance(step.distanceMoved(), level.getGameTime());
        if (step.changed()) {
            networkDirty = true;
            setChanged();
        }
    }

    private void loadItemsOnBelt() {
        int interval = (int) (20 / (0.8f * BeltTiers.speed(beltTier))) + 1;
        if ((level.getGameTime() + worldPosition.asLong()) % interval != 0
                || !BeltTransport.canLoad(movingItems, beltData.totalLength())) return;
        // Universal interfaces alternate after successful transfers so neither domain starves.
        if (tryLoad(nextFluid)) nextFluid = !nextFluid;
        else tryLoad(!nextFluid);
    }

    private boolean tryLoad(boolean fluid) {
        if (!getKind().supports(fluid)) return false;
        var config = settings(fluid);
        if (!config.redstone.allowsTransfer()) return false;
        int mask = getEffectiveExtractionSides(fluid);
        var containerPos = worldPosition.relative(getOwnFacing().getOpposite());
        for (int attempt = 0; attempt < 6; attempt++) {
            int index = (config.nextSide + attempt) % 6;
            var side = ExtractionSide.byId(index);
            if (!side.selected(mask)) continue;
            ItemStack extracted = ItemStack.EMPTY;
            if (fluid) {
                var source = ConveyorFluidApi.find(level, containerPos, side.direction(getOwnFacing()));
                if (source == null) continue;
                for (int tank = 0; tank < source.getTanks(); tank++) {
                    var available = source.getFluidInTank(tank).copy();
                    if (available.isEmpty() || !allowsFluid(available)) continue;
                    available.setAmount(Math.min(available.getAmount(), ConveyorConfig.fluidAmount(getChuteTier())));
                    var simulated = source.drain(available, FluidAction.SIMULATE);
                    if (simulated.isEmpty() || !allowsFluid(simulated)) continue;
                    var drained = source.drain(simulated, FluidAction.EXECUTE);
                    if (!drained.isEmpty()) { extracted = FluidPackets.create(drained); break; }
                }
            } else {
                var source = ConveyorItemApi.find(level, containerPos, side.direction(getOwnFacing()));
                if (source == null) continue;
                for (int slot = 0; slot < source.getSlotCount(); slot++) {
                    var stack = source.getStackInSlot(slot).copy();
                    if (stack.isEmpty() || !allowsItem(stack)) continue;
                    stack.setCount(stack.getMaxStackSize() * ConveyorConfig.chuteStacks(getChuteTier()));
                    int count = source.extract(stack);
                    if (count > 0) { extracted = stack.copyWithCount(count); break; }
                }
            }
            if (!extracted.isEmpty()) {
                config.nextSide = (index + 1) % 6;
                movingItems.addFirst(new BeltItem(nextItemId++, extracted));
                config.redstone.transferred();
                setChanged();
                networkDirty = true;
                return true;
            }
        }
        return false;
    }

    /** One extraction attempt per interval, phased by position so neighbouring chutes do not pull together. */
    private boolean extractionDue(int interval) {
        return Math.floorMod(level.getGameTime() + worldPosition.asLong(), interval) == 0;
    }

    public int getChuteTier() {
        return getBlockState().getBlock() instanceof ChuteBlock chute ? chute.getTier() : 1;
    }

    public int getFilterSlotCount() { return getFilterSlotCount(defaultFluid()); }
    public int getFilterSlotCount(boolean fluid) {
        return fluid ? ConveyorConfig.fluidFilters(getChuteTier()) : ConveyorConfig.chuteFilters(getChuteTier());
    }
    public ItemStack getFilter(int slot) { return getRule(slot).icon(); }
    public FilterRule getRule(int slot) { return getRule(defaultFluid(), slot); }
    public FilterRule getRule(boolean fluid, int slot) {
        return slot >= 0 && slot < getFilterSlotCount(fluid) ? settings(fluid).filters.get(slot) : FilterRule.EMPTY;
    }
    public long getFilterRevision() { return filterRevision; }
    public int getExtractionSides() { return getExtractionSides(defaultFluid()); }
    public int getExtractionSides(boolean fluid) { return settings(fluid).sides; }
    public int getEffectiveExtractionSides() { return getEffectiveExtractionSides(defaultFluid()); }
    public int getEffectiveExtractionSides(boolean fluid) {
        return ConveyorConfig.extractionSidesEnabled() ? settings(fluid).sides : ExtractionSide.DEFAULT_MASK;
    }
    public void setExtractionSides(int mask) { setExtractionSides(defaultFluid(), mask); }
    public void setExtractionSides(boolean fluid, int mask) {
        if (level != null && level.isClientSide || !getKind().supports(fluid) || !ExtractionSide.validMask(mask)) return;
        var config = settings(fluid);
        if (config.sides == mask) return;
        config.sides = mask;
        config.nextSide = 0;
        syncSettings();
    }
    public RedstoneControl.Mode getRedstoneMode() { return getRedstoneMode(defaultFluid()); }
    public RedstoneControl.Mode getRedstoneMode(boolean fluid) { return settings(fluid).redstone.mode(); }
    public void setRedstoneMode(RedstoneControl.Mode mode) { setRedstoneMode(defaultFluid(), mode); }
    public void setRedstoneMode(boolean fluid, RedstoneControl.Mode mode) {
        if (level != null && level.isClientSide || !getKind().supports(fluid) || settings(fluid).redstone.mode() == mode) return;
        settings(fluid).redstone.setMode(mode, level != null && level.hasNeighborSignal(worldPosition));
        syncSettings();
    }
    public void updateRedstoneSignal() {
        if (level == null || level.isClientSide) return;
        boolean powered = level.hasNeighborSignal(worldPosition);
        if (itemSettings.redstone.sample(powered) | fluidSettings.redstone.sample(powered)) setChanged();
    }
    public boolean isWhitelistMode() { return isWhitelistMode(defaultFluid()); }
    public boolean isWhitelistMode(boolean fluid) { return settings(fluid).whitelist; }
    public void setWhitelistMode(boolean whitelist) { setWhitelistMode(defaultFluid(), whitelist); }
    public void setWhitelistMode(boolean fluid, boolean whitelist) {
        if (level != null && level.isClientSide || !getKind().supports(fluid) || settings(fluid).whitelist == whitelist) return;
        settings(fluid).whitelist = whitelist;
        settingsChanged();
    }
    public void setRule(int slot, FilterRule rule) { setRule(defaultFluid(), slot, rule); }
    public void setRule(boolean fluid, int slot, FilterRule rule) {
        if (level != null && level.isClientSide || !getKind().supports(fluid)) return;
        if (!rule.isEmpty() && fluid != rule.isFluidRule()) throw new IllegalArgumentException("Rule belongs to another transport domain");
        if (!settings(fluid).filters.set(slot, getFilterSlotCount(fluid), rule)) return;
        settingsChanged();
    }
    private void settingsChanged() {
        filterRevision++;
        syncSettings();
    }
    private void syncSettings() {
        setChanged();
        networkDirty = true;
        if (level instanceof ServerLevel serverWorld) serverWorld.getChunkSource().blockChanged(worldPosition);
    }
    public boolean allowsFluid(FluidStack stack) {
        if (stack.isEmpty() || !getKind().supports(true)) return false;
        return fluidSettings.filters.allows(FluidPackets.create(stack), getFilterSlotCount(true), fluidSettings.whitelist);
    }
    public boolean allowsItem(ItemStack stack) {
        if (stack.isEmpty() || FluidPackets.isPacket(stack) || !getKind().supports(false)) return false;
        boolean matched = false;
        for (int i = 0; i < getFilterSlotCount(false); i++) {
            var rule = itemSettings.filters.get(i);
            if (rule.isEmpty()) continue;
            Boolean ftb = rule.kind() == FilterRule.Kind.ITEM && ModList.get().isLoaded("ftbfiltersystem")
                    ? pureneko.conveyor_belt_plus.compat.FtbFilters.matches(rule.prototype(), stack) : null;
            if (ftb != null ? ftb : rule.matches(stack)) { matched = true; break; }
        }
        return itemSettings.whitelist == matched;
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        var registryLookup = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.saveAdditional(nbt);
        if (target != null)
            nbt.putLong("target", target.asLong());
        nbt.putByte("targetFacing", (byte) targetFacing.get3DDataValue());

        if (!midPoints.isEmpty()) {
            var midpointsArray = midPoints.stream().map(BlockPos::asLong).toList();
            nbt.putLongArray("midpoints", midpointsArray);
        }

        itemSettings.write(nbt, registryLookup); // Keep legacy item fields at the root.
        var fluidTag = new CompoundTag();
        fluidSettings.write(fluidTag, registryLookup);
        nbt.put("fluidSettings", fluidTag);
        nbt.putBoolean("nextFluid", nextFluid);
        nbt.putInt("beltTier", beltTier);
        nbt.putLong("sourceBeltPos", sourceBeltPos.asLong());
        nbt.putLong("lastTargetedTime", lastTargetedTime);

        var positionsList = new ListTag();
        for (var item : movingItems) positionsList.add(item.write(registryLookup, "a", "b"));
        nbt.put("moving", positionsList);
        nbt.putLong("nextItemId", nextItemId);
        nbt.putLong("snapshotTick", level == null ? 0 : level.getGameTime());
        nbt.putDouble("beltDistance", visualState.distance());
        nbt.putDouble("beltStep", level == null ? 0 : visualState.step(level.getGameTime()));
    }

    @Override
    public void load(CompoundTag nbt) {
        var registryLookup = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.load(nbt);
        itemSettings.read(nbt, registryLookup);
        fluidSettings.read(nbt.getCompound("fluidSettings"), registryLookup);
        nextFluid = nbt.getBoolean("nextFluid");

        sourceBeltPos = BlockPos.of(nbt.getLong("sourceBeltPos"));
        lastTargetedTime = nbt.getLong("lastTargetedTime");
        var oldTarget = target;
        var oldFacing = targetFacing;
        var oldMidpoints = midPoints;
        target = BlockPos.of(nbt.getLong("target"));
        targetFacing = nbt.contains("targetFacing", Tag.TAG_BYTE)
                ? Direction.from3DDataValue(nbt.getByte("targetFacing"))
                : getTargetFacing(target);
        if (targetFacing == null) targetFacing = getTargetFacing(target);

        var midPointsList = nbt.getLongArray("midpoints");
        midPoints = Arrays.stream(midPointsList).mapToObj(BlockPos::of).toList();

        beltTier = nbt.contains("beltTier", Tag.TAG_INT)
                ? BeltTiers.normalize(nbt.getInt("beltTier")) : BeltTiers.STANDARD;

        boolean sameRoute = Objects.equals(oldTarget, target) && oldFacing == targetFacing
                && oldMidpoints.equals(midPoints);
        if (!sameRoute) {
            movingItems.clear();
            visualState.restore(0);
        }
        long snapshotTick = nbt.getLong("snapshotTick");
        if (level != null && level.isClientSide)
            visualState.receive(nbt.getDouble("beltDistance"), nbt.getDouble("beltStep"),
                    snapshotTick, BeltRenderClock.now(level));
        else visualState.restore(nbt.getDouble("beltDistance"));
        BeltItem.readItems(movingItems, nbt.getList("moving", Tag.TAG_COMPOUND),
                registryLookup, level, "a", "b", snapshotTick, visualState);
        nextItemId = nbt.getLong("nextItemId");
        for (var item : movingItems) nextItemId = Math.max(nextItemId, item.id + 1);
        if (!sameRoute) {
            beltData = null;
            renderedModel = null;
        }
        if (level != null && beltData == null) beltData = BeltData.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        var base = super.getUpdateTag();
        saveAdditional(base);
        return base;
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return net.minecraftforge.common.extensions.IForgeBlockEntity.INFINITE_EXTENT_AABB;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public BeltVisualState getVisualState() { return visualState; }

    public Iterable<BeltItem> getMovingItems() {
        return movingItems;
    }

    public BlockPos getTarget() {
        return target;
    }

    public BeltData getBeltData() {
        return beltData;
    }

    @Override public boolean insertOnBelt(Direction port, float progress, ItemStack stack) {
        if (level == null || level.isClientSide || !BeltInsertion.validStack(stack)) return false;
        var access = pickupAccess(port);
        if (access == null || access.data() == null) return false;
        var offered = stack.copyWithCount(Math.min(stack.getCount(), stack.getMaxStackSize()));
        if (!BeltTransport.insertAt(access.items(), access.data().totalLength(),
                new ChuteBlockEntity.BeltItem(progress, nextItemId, offered))) return false;
        nextItemId++;
        stack.shrink(offered.getCount());
        networkDirty = true;
        setChanged();
        if (level instanceof ServerLevel serverWorld) serverWorld.getChunkSource().blockChanged(worldPosition);
        return true;
    }

    @Override public BeltPickup.Access pickupAccess(Direction port) {
        return outgoingBeltTier(port) == 0 ? null : new BeltPickup.Access(beltData, beltTier, movingItems);
    }

    public Direction getOwnFacing() {
        return getBlockState().getValue(HorizontalDirectionalBlock.FACING);
    }

    @Override
    public boolean hasInputPort(Direction port) {
        return port == getOwnFacing();
    }

    @Override
    public boolean hasOutputPort(Direction port) {
        return port == getOwnFacing();
    }

    public int getBeltTier() {
        return beltTier;
    }

    @Override public int outgoingBeltTier(Direction port) {
        return hasOutputPort(port) && target != null && !target.equals(BlockPos.ZERO) ? beltTier : 0;
    }

    @Override public boolean upgradeOutgoingBelt(Direction port, int tier) {
        if (level == null || level.isClientSide || !TierUpgrade.isUpgrade(outgoingBeltTier(port), tier)) return false;
        beltTier = tier;
        networkDirty = true;
        markDirtyAndSync();
        return true;
    }

    @Override
    public boolean isPortUsed(Direction port) {
        return isUsed();
    }

    @Override
    public void connectIncoming(Direction port, BlockPos source) {
        if (hasInputPort(port)) markTargeted(source);
    }

    @Override
    public boolean connectOutgoing(Direction port, BlockPos target, Direction targetPort,
                                   List<BlockPos> supports, int beltTier) {
        return hasOutputPort(port) && assignFromBeltItem(target, targetPort, supports, beltTier);
    }

    @Override
    public boolean acceptFromBelt(ItemStack stack, Direction port) {
        if (!hasInputPort(port) || stack.isEmpty() || level == null || level.isClientSide) return false;
        boolean fluid = FluidPackets.isPacket(stack);
        if (!getKind().supports(fluid)) return false;
        updateRedstoneSignal();
        var redstone = settings(fluid).redstone;
        if (!redstone.allowsTransfer()) return false;
        if (fluid) {
            var contents = FluidPackets.get(stack);
            if (!allowsFluid(contents)) return false;
            var tank = ConveyorFluidApi.find(level, worldPosition.relative(port.getOpposite()), port);
            if (tank == null) return false;
            int accepted = tank.fill(contents.copy(), FluidAction.EXECUTE);
            if (accepted <= 0) return false;
            contents.shrink(Math.min(accepted, contents.getAmount()));
            FluidPackets.set(stack, contents);
            if (redstone.transferred()) setChanged();
            return stack.isEmpty();
        }
        if (!allowsItem(stack)) return false;
        var targetInv = ConveyorItemApi.find(level, worldPosition.relative(port.getOpposite()), port);
        if (targetInv == null || targetInv.insert(stack, true) != stack.getCount()) return false;
        // Preserve a remainder if another mod accepts less than its simulation promised.
        int inserted = targetInv.insert(stack.copy(), false);
        if (inserted <= 0) return false;
        if (redstone.transferred()) setChanged();
        if (inserted >= stack.getCount()) return true;
        stack.shrink(inserted);
        return false;
    }

    @Override
    public void markTargeted(BlockPos source) {
        if (level != null) {
            lastTargetedTime = level.getGameTime();
            sourceBeltPos = source;
            // Receiving chutes do not tick an outgoing route; sync occupancy here for the preview.
            markDirtyAndSync();
        }
    }

    @Override
    public void disconnectOutgoingTo(BlockPos destination) {
        if (target == null || !target.equals(destination)) return;

        // dropContent already clears the route, drops every packet and flags the client update.
        if (level != null && !level.isClientSide) {
            dropContent(level, worldPosition);
        } else {
            target = null;
            midPoints = new ArrayList<>();
            beltData = null;
            renderedModel = null;
        }
        targetFacing = Direction.NORTH;
        networkDirty = true;
        markDirtyAndSync();
    }

    @Override
    public void disconnectIncomingFrom(BlockPos source) {
        if (!source.equals(sourceBeltPos)) return;
        sourceBeltPos = BlockPos.ZERO;
        lastTargetedTime = 0;
        networkDirty = true;
        markDirtyAndSync();
    }

    public boolean isUsed() {
        var usedAsTarget = level != null && !sourceBeltPos.equals(BlockPos.ZERO)
                && level.getGameTime() >= lastTargetedTime && level.getGameTime() - lastTargetedTime < 40;
        var usedAsSource = target != null && !target.equals(BlockPos.ZERO);
        return usedAsTarget || usedAsSource;
    }

    public boolean assignFromBeltItem(BlockPos target, Direction targetFacing,
                                      List<BlockPos> midpoints, int beltTier) {
        if (level == null || BeltData.create(level, worldPosition, getOwnFacing(), target, targetFacing,
                midpoints.stream().map(point -> new Tuple<>(point, level.getBlockState(point).getValue(HorizontalDirectionalBlock.FACING))).toList()) == null)
            return false;
        this.target = target;
        this.targetFacing = targetFacing;
        this.midPoints = midpoints;
        this.beltTier = BeltTiers.normalize(beltTier);
        visualState.restore(0);
        renderedModel = null;
        beltData = BeltData.create(this);
        networkDirty = true;
        markDirtyAndSync();
        return true;
    }

    public List<Tuple<BlockPos, Direction>> getMidPointsWithTangents() {
        return midPoints.stream()
                 .filter(point -> level.getBlockState(point).getBlock().equals(BlockContent.CONVEYOR_SUPPORT_BLOCK.get()))
                 .map(point -> new Tuple<>(point, level.getBlockState(point).getValue(HorizontalDirectionalBlock.FACING)))
                 .toList();
    }

    private Direction getTargetFacing(BlockPos target) {
        var node = level == null || target == null ? null : ConveyorNodeUtil.get(level, target);
        return node instanceof ChuteBlockEntity chute ? chute.getOwnFacing() : Direction.NORTH;
    }

    public static class BeltItem implements BeltTransport.Packet {
        public float progress;
        public final long id;
        public final ItemStack stack;
        private float previousProgress;
        private double bornTick = Double.NEGATIVE_INFINITY;
        private double retiredTick = Double.POSITIVE_INFINITY;
        private final ProgressAnimation animation;

        public BeltItem(long id, ItemStack stack) { this(0, id, stack); }

        public BeltItem(float progress, long id, ItemStack stack) {
            this.id = id;
            this.stack = stack;
            this.progress = this.previousProgress = progress;
            this.animation = new ProgressAnimation(progress);
        }

        @Override public float progress() { return progress; }
        @Override public int count() { return FluidPackets.isPacket(stack) ? FluidPackets.amount(stack) : stack.getCount(); }
        @Override public void progress(float value) { previousProgress = progress; progress = value; }
        public float renderedProgress(double tick) { return (float) animation.value(tick); }
        public boolean visible(double tick) { return tick >= bornTick && tick < retiredTick; }

        public CompoundTag write(net.minecraft.core.RegistryAccess lookup, String progressKey, String stackKey) {
            var tag = new CompoundTag();
            tag.putFloat(progressKey, progress);
            tag.putFloat("previousProgress", previousProgress);
            tag.putLong("id", id);
            TransportStacks.write(tag, stackKey, stack, lookup);
            return tag;
        }

        public static void readItems(Deque<BeltItem> items, ListTag list,
                                     net.minecraft.core.RegistryAccess lookup, Level world,
                                     String progressKey, String stackKey, long snapshotTick,
                                     BeltVisualState visualState) {
            boolean client = world != null && world.isClientSide;
            double renderedTick = client ? visualState.presentationTick(BeltRenderClock.now(world)) : snapshotTick;
            applySnapshot(items, list, lookup, progressKey, stackKey, snapshotTick, client, renderedTick);
        }

        /** Shared snapshot merge, independent of a live world so endpoint transitions can be tested. */
        public static void applySnapshot(Deque<BeltItem> items, ListTag list,
                                          net.minecraft.core.RegistryAccess lookup,
                                          String progressKey, String stackKey, long snapshotTick,
                                          boolean client, double renderedTick) {
            Map<Long, BeltItem> previous = new HashMap<>();
            if (client)
                for (var item : items) previous.put(item.id, item);
            items.clear();
            for (var entry : list) {
                var tag = (CompoundTag) entry;
                var stack = TransportStacks.read(tag, stackKey, lookup);
                if (stack.isEmpty()) continue;
                long id = tag.getLong("id");
                float progress = net.minecraft.util.Mth.clamp(tag.getFloat(progressKey), 0, 1);
                var item = previous.remove(id);
                if (item == null || !FluidPackets.sameContents(item.stack, stack)) {
                    item = new BeltItem(progress, id, stack);
                    item.animation.reset(progress, snapshotTick);
                    item.bornTick = progress == 0 ? snapshotTick : Double.NEGATIVE_INFINITY;
                }
                if (FluidPackets.isPacket(stack)) FluidPackets.set(item.stack, FluidPackets.get(stack));
                item.stack.setCount(stack.getCount()); // Partial insertion/pickup must not reset interpolation.
                item.progress = progress;
                item.previousProgress = tag.contains("previousProgress")
                        ? net.minecraft.util.Mth.clamp(tag.getFloat("previousProgress"), 0, progress) : progress;
                item.animation.update(item.previousProgress, snapshotTick - 1);
                item.animation.update(progress, snapshotTick);
                items.addLast(item);
            }
            // Delay removal by the same presentation buffer so packets visibly reach the endpoint.
            if (client) {
                for (var item : previous.values()) {
                    if (item.retiredTick == Double.POSITIVE_INFINITY) {
                        item.animation.update(item.progress, snapshotTick - 1);
                        item.animation.update(1, snapshotTick);
                        item.retiredTick = snapshotTick;
                    }
                    if (item.retiredTick > renderedTick) items.addLast(item);
                }
            }
        }
    }

    public static final class BeltData {
        private final List<Tuple<Vec3, Vec3>> allPoints;
        private final SplineUtil.ArcLengthPath arcPath;

        public BeltData(List<Tuple<Vec3, Vec3>> points, double[] lengths) {
            this(points, SplineUtil.ArcLengthPath.create(points, lengths));
        }

        private BeltData(List<Tuple<Vec3, Vec3>> points, SplineUtil.ArcLengthPath arcPath) {
            this.allPoints = List.copyOf(points);
            this.arcPath = arcPath;
        }

        public List<Tuple<Vec3, Vec3>> allPoints() { return allPoints; }
        public double totalLength() { return arcPath.length(); }
        private pureneko.conveyor_belt_plus.util.BeltHitPath interactionPath;
        public pureneko.conveyor_belt_plus.util.BeltHitPath interactionPath() {
            if (interactionPath == null) interactionPath = new pureneko.conveyor_belt_plus.util.BeltHitPath(this);
            return interactionPath;
        }
        public SplineUtil.ArcLengthPath arcPath() { return arcPath; }

        public static @Nullable BeltData create(ChuteBlockEntity entity) {
            if (entity.getLevel() == null || entity.target == null || entity.target.equals(BlockPos.ZERO))
                return null;

            var targetNode = ConveyorNodeUtil.get(entity.getLevel(), entity.getTarget());
            if (targetNode == null) return null;
            return create(entity.getLevel(), entity.getBlockPos(), entity.getOwnFacing(), entity.getTarget(),
                    entity.targetFacing, entity.getMidPointsWithTangents());
        }

        public static @Nullable BeltData create(Level world, BlockPos start, Direction startFacing,
                                                BlockPos target, Direction targetFacing,
                                                List<Tuple<BlockPos, Direction>> midPoints) {
            return create(world, start, startFacing, target, targetFacing, midPoints, true);
        }

        public static Vec3 anchor(Level world, BlockPos pos, Direction port) {
            if (world.getBlockEntity(pos) instanceof ConveyorSplitterBlockEntity
                    || world.getBlockState(pos).is(BlockContent.CONVEYOR_SUPPORT_BLOCK.get()))
                return pos.getCenter();
            return pos.getCenter().subtract(Vec3.atLowerCornerOf(port.getNormal()).scale(0.5));
        }

        public static @Nullable BeltData create(Level world, BlockPos start, Direction startFacing,
                                                BlockPos target, Direction targetFacing,
                                                List<Tuple<BlockPos, Direction>> midPoints, boolean validate) {
            var conveyorStartDir = Vec3.atLowerCornerOf(startFacing.getNormal());
            var conveyorEndDir = Vec3.atLowerCornerOf(targetFacing.getOpposite().getNormal());
            var conveyorStartPointVisual = anchor(world, start, startFacing);
            var conveyorEndPointVisual = anchor(world, target, targetFacing);

            var transformedMidPoints = midPoints.stream()
                    .map(elem -> new Tuple<>(elem.getA().getCenter(),
                            Vec3.atLowerCornerOf(elem.getB().getNormal())))
                    .toList();
            var segmentPoints = SplineUtil.getPointPairs(conveyorStartPointVisual, conveyorStartDir,
                    conveyorEndPointVisual, conveyorEndDir, transformedMidPoints);
            var arcPath = SplineUtil.ArcLengthPath.create(segmentPoints);
            if (validate && !SplineUtil.isPathUsable(arcPath, conveyorStartDir, conveyorEndDir))
                return null;
            return new BeltData(segmentPoints, arcPath);
        }

    }
}
