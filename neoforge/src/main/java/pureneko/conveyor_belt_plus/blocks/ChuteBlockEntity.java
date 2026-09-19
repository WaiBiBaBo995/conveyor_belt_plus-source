package pureneko.conveyor_belt_plus.blocks;

import net.neoforged.fml.ModList;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import pureneko.conveyor_belt_plus.registry.BlockContent;
import pureneko.conveyor_belt_plus.registry.BlockEntitiesContent;
import pureneko.conveyor_belt_plus.registry.ItemContent;
import pureneko.conveyor_belt_plus.neoforge.ConveyorItemApi;
import pureneko.conveyor_belt_plus.util.BeltTiers;
import pureneko.conveyor_belt_plus.util.BeltTransport;
import pureneko.conveyor_belt_plus.util.ProgressAnimation;
import pureneko.conveyor_belt_plus.util.BeltVisualState;
import pureneko.conveyor_belt_plus.util.BeltQuad;
import pureneko.conveyor_belt_plus.util.BeltRenderClock;
import pureneko.conveyor_belt_plus.util.TransportStacks;
import pureneko.conveyor_belt_plus.config.ConveyorConfig;
import pureneko.conveyor_belt_plus.filter.FilterRule;
import pureneko.conveyor_belt_plus.filter.FilterRules;
import pureneko.conveyor_belt_plus.util.SplineUtil;
import pureneko.conveyor_belt_plus.util.ExtractionSide;
import pureneko.conveyor_belt_plus.util.RedstoneControl;
import pureneko.conveyor_belt_plus.util.TierUpgrade;
import pureneko.conveyor_belt_plus.compat.FtbFilters;

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
    private BlockPos sourceBeltPos = BlockPos.ORIGIN;
    private int beltTier = BeltTiers.STANDARD;

    private long nextItemId;
    private final BeltVisualState visualState = new BeltVisualState();
    private static final boolean DEFAULT_WHITELIST_MODE = false;

    // Filter markers are stored as one-item stacks and never consume the player's cursor stack.
    private final FilterRules filters = new FilterRules();
    private long filterRevision;
    private final RedstoneControl redstone = new RedstoneControl();
    // New interfaces start in blacklist mode so an empty filter accepts every item.
    private boolean whitelistMode = DEFAULT_WHITELIST_MODE;
    private int extractionSides = ExtractionSide.DEFAULT_MASK;
    private int nextExtractionSide;

    // client only data, used for rendering
    public BeltQuad[] renderedModel;

    private boolean networkDirty = false;

    public ChuteBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesContent.CHUTE_BLOCK.get(), pos, state);
    }

    /** Pushes the block-entity state to tracking clients; a no-op on the client or a detached entity. */
    private void syncToClients() {
        if (world instanceof ServerWorld serverWorld) serverWorld.getChunkManager().markForUpdate(pos);
    }

    /** Marks the entity dirty and pushes the change to tracking clients. */
    private void markDirtyAndSync() {
        markDirty();
        syncToClients();
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, ChuteBlockEntity blockEntity) {
        if (world == null) return;
        updateRedstoneSignal(); // Receiving interfaces also need edge detection, before the no-outgoing early return.

        if (target == null || target.equals(BlockPos.ORIGIN)) {
            if (!world.isClient && !movingItems.isEmpty()) {
                dropContent(world, pos);
            }
            return;
        }

        if (beltData == null) {
            beltData = BeltData.create(this);
            syncToClients();
        }

        if (beltData == null) {
            if (!world.isClient) {
                // A route can become invalid after a support changes. Treat it as a broken belt
                // instead of allowing a malformed spline to remain rendered.
                dropContent(world, pos);
            }
            target = null;
            midPoints = new ArrayList<>();
            markDirty();
            return;
        }

        if (world.isClient) return;

        moveItemsOnBelt();
        loadItemsOnBelt();

        // refresh target
        if (world.getTime() % 19 == 0)
            assignTargetState(world);

        if (networkDirty) {
            syncToClients();
            networkDirty = false;
        }

    }

    public void dropContent(World world, BlockPos pos) {
        if (world.isClient) return;
        var oldTarget = target;
        var oldSource = sourceBeltPos;
        // Detach first so reciprocal callbacks cannot drop the same packet or belt twice.
        target = null;
        midPoints = new ArrayList<>();
        beltData = null;
        renderedModel = null;
        sourceBeltPos = BlockPos.ORIGIN;
        lastTargetedTime = 0;
        if (!oldSource.equals(BlockPos.ORIGIN)) {
            var source = ConveyorNodeUtil.get(world, oldSource);
            if (source != null && source != this) source.disconnectOutgoingTo(this.pos);
        }
        if (oldTarget != null && !oldTarget.equals(BlockPos.ORIGIN)) {
            var destination = ConveyorNodeUtil.get(world, oldTarget);
            if (destination != null) destination.disconnectIncomingFrom(this.pos);
        }
        for (var item : movingItems) TransportStacks.drop(world, pos, item.stack);
        if (!movingItems.isEmpty() || (oldTarget != null && !oldTarget.equals(BlockPos.ORIGIN)))
            TransportStacks.drop(world, pos, ItemContent.beltStackForTier(beltTier));
        movingItems.clear();
        networkDirty = true;
        markDirtyAndSync();
    }

    // notifies the belt end entity that the current entity is the sender to it
    private void assignTargetState(World world) {
        var beltTarget = ConveyorNodeUtil.get(world, target);
        if (beltTarget != null) {
            beltTarget.markTargeted(pos);
        } else {
            target = null;
            midPoints = new ArrayList<>();
        }
    }

    private void moveItemsOnBelt() {
        var step = BeltTransport.tickWithMotion(movingItems, beltData.totalLength(), BeltTiers.speed(beltTier),
                packet -> ConveyorNodeUtil.accept(world, target, targetFacing, packet.stack));
        visualState.advance(step.distanceMoved(), world.getTime());
        if (step.changed()) {
            networkDirty = true;
            markDirty();
        }
    }

    @SuppressWarnings("DataFlowIssue")
    private void loadItemsOnBelt() {
        if (!redstone.allowsTransfer()) return;
        var extractionInterval = (int) (20 / (0.8f * BeltTiers.speed(beltTier))) + 1;
        if (!extractionDue(extractionInterval)) return;

        if (!BeltTransport.canLoad(movingItems, beltData.totalLength())) return;

        int mask = getEffectiveExtractionSides();
        if (mask == 0) return;
        var containerPos = pos.offset(getOwnFacing().getOpposite());
        // All queries address the SAME attached block, not its six neighboring blocks.
        // One successful side supplies one normal batch; multi-selection never multiplies throughput.
        for (int attempt = 0; attempt < 6; attempt++) {
            int index = (nextExtractionSide + attempt) % 6;
            var side = ExtractionSide.byId(index);
            if (!side.selected(mask)) continue;
            var source = ConveyorItemApi.find(world, containerPos, side.direction(getOwnFacing()));
            if (source == null) continue;
            // try extracting first stack
            ItemStack extracted = null;
            for (int i = 0; i < source.getSlotCount(); i++) {
                var extractingStack = source.getStackInSlot(i).copy();
                if (extractingStack.isEmpty()) continue;
                if (!allowsItem(extractingStack)) continue;
                extractingStack.setCount(extractingStack.getMaxCount() * ConveyorConfig.chuteStacks(getChuteTier()));
                var extractedAmount = source.extract(extractingStack);
                if (extractedAmount > 0) {
                    extracted = extractingStack.copyWithCount(extractedAmount);
                    break;
                }
            }

            if (extracted != null) {
                nextExtractionSide = (index + 1) % 6;
                var id = nextItemId++;
                movingItems.addFirst(new BeltItem(id, extracted));
                redstone.transferred();
                this.markDirty();
                networkDirty = true;
                return;
            }
        }
    }

    /** One extraction attempt per interval, phased by position so neighbouring chutes do not pull together. */
    private boolean extractionDue(int interval) {
        return Math.floorMod(world.getTime() + pos.asLong(), interval) == 0;
    }

    public int getChuteTier() {
        return getCachedState().getBlock() instanceof ChuteBlock chute ? chute.getTier() : 1;
    }

    public int getFilterSlotCount() { return ConveyorConfig.chuteFilters(getChuteTier()); }

    public ItemStack getFilter(int slot) {
        return getRule(slot).icon();
    }

    public FilterRule getRule(int slot) {
        return slot >= 0 && slot < getFilterSlotCount() ? filters.get(slot) : FilterRule.EMPTY;
    }

    public long getFilterRevision() { return filterRevision; }

    public int getExtractionSides() { return extractionSides; }
    public int getEffectiveExtractionSides() {
        return ConveyorConfig.extractionSidesEnabled() ? extractionSides : ExtractionSide.DEFAULT_MASK;
    }
    public void setExtractionSides(int mask) {
        if (world != null && world.isClient || !ExtractionSide.validMask(mask) || extractionSides == mask) return;
        extractionSides = mask;
        nextExtractionSide = 0;
        markDirtyAndSync();
    }

    public RedstoneControl.Mode getRedstoneMode() { return redstone.mode(); }

    public void setRedstoneMode(RedstoneControl.Mode mode) {
        if (world != null && world.isClient || redstone.mode() == mode) return;
        redstone.setMode(mode, world != null && world.isReceivingRedstonePower(pos));
        markDirtyAndSync();
    }

    /** Called by neighbor notifications as well as the tick/transaction path. */
    public void updateRedstoneSignal() {
        if (world == null || world.isClient) return;
        if (redstone.mode() == RedstoneControl.Mode.ALWAYS
                || redstone.mode() == RedstoneControl.Mode.NEVER) return;
        if (redstone.sample(world.isReceivingRedstonePower(pos))) markDirty();
    }

    public boolean isWhitelistMode() {
        return whitelistMode;
    }

    public void setWhitelistMode(boolean whitelistMode) {
        if (this.whitelistMode == whitelistMode) return;
        this.whitelistMode = whitelistMode;
        filterRevision++;
        networkDirty = true;
        markDirtyAndSync();
    }

    public void setRule(int slot, FilterRule rule) {
        if (!filters.set(slot, getFilterSlotCount(), rule)) return;
        filterRevision++;
        networkDirty = true;
        markDirtyAndSync();
    }

    /** Applies the same filter to both container extraction and belt insertion. */
    public boolean allowsItem(ItemStack stack) {
        var matches = false;
        for (int i = 0; i < getFilterSlotCount(); i++) {
            var filter = filters.get(i);
            if (filter.isEmpty()) continue;
            if (filterMatches(filter, stack)) {
                matches = true;
                break;
            }
        }
        return whitelistMode ? matches : !matches;
    }

    private static boolean filterMatches(FilterRule filter, ItemStack stack) {
        // An explicit COMPONENTS rule must remain exact, even for an FTB filter item.
        if (filter.kind() == FilterRule.Kind.ITEM && ModList.get().isLoaded("ftbfiltersystem")) {
            var result = FtbFilters.matches(filter.prototype(), stack);
            if (result != null) return result;
        }
        return filter.matches(stack);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        if (target != null)
            nbt.putLong("target", target.asLong());
        nbt.putByte("targetFacing", (byte) targetFacing.getId());

        if (!midPoints.isEmpty()) {
            var midpointsArray = midPoints.stream().map(BlockPos::asLong).toList();
            nbt.putLongArray("midpoints", midpointsArray);
        }

        filters.write(nbt, registryLookup);
        nbt.putBoolean("whitelistMode", whitelistMode);
        nbt.putInt("extractionSides", extractionSides);
        nbt.putInt("redstoneMode", redstone.mode().ordinal());
        nbt.putBoolean("redstonePowered", redstone.powered());
        nbt.putBoolean("redstonePulsePending", redstone.pending());
        nbt.putInt("beltTier", beltTier);
        nbt.putLong("sourceBeltPos", sourceBeltPos.asLong());
        nbt.putLong("lastTargetedTime", lastTargetedTime);

        var positionsList = new NbtList();
        for (var item : movingItems) positionsList.add(item.write(registryLookup, "a", "b"));
        nbt.put("moving", positionsList);
        nbt.putLong("nextItemId", nextItemId);
        nbt.putLong("snapshotTick", world == null ? 0 : world.getTime());
        nbt.putDouble("beltDistance", visualState.distance());
        nbt.putDouble("beltStep", world == null ? 0 : visualState.step(world.getTime()));
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        extractionSides = nbt.contains("extractionSides", NbtElement.NUMBER_TYPE)
                ? ExtractionSide.savedMask(nbt.getInt("extractionSides")) : ExtractionSide.DEFAULT_MASK;
        redstone.restore(RedstoneControl.Mode.fromId(nbt.getInt("redstoneMode")),
                nbt.getBoolean("redstonePowered"), nbt.getBoolean("redstonePulsePending"));

        sourceBeltPos = BlockPos.fromLong(nbt.getLong("sourceBeltPos"));
        lastTargetedTime = nbt.getLong("lastTargetedTime");
        var oldTarget = target;
        var oldFacing = targetFacing;
        var oldMidpoints = midPoints;
        target = BlockPos.fromLong(nbt.getLong("target"));
        targetFacing = nbt.contains("targetFacing", NbtElement.BYTE_TYPE)
                ? Direction.byId(nbt.getByte("targetFacing"))
                : getTargetFacing(target);
        if (targetFacing == null) targetFacing = getTargetFacing(target);

        var midPointsList = nbt.getLongArray("midpoints");
        midPoints = Arrays.stream(midPointsList).mapToObj(BlockPos::fromLong).toList();

        filters.read(nbt, registryLookup);
        var filterList = nbt.getList("filters", NbtElement.COMPOUND_TYPE);
        var legacyFilter = !nbt.contains("filterRules") && filterList.isEmpty() && nbt.contains("filter", NbtElement.COMPOUND_TYPE);
        whitelistMode = nbt.contains("whitelistMode", NbtElement.BYTE_TYPE)
                ? nbt.getBoolean("whitelistMode")
                : DEFAULT_WHITELIST_MODE;
        if (legacyFilter) whitelistMode = true;
        beltTier = nbt.contains("beltTier", NbtElement.INT_TYPE)
                ? BeltTiers.normalize(nbt.getInt("beltTier")) : BeltTiers.STANDARD;

        boolean sameRoute = Objects.equals(oldTarget, target) && oldFacing == targetFacing
                && oldMidpoints.equals(midPoints);
        if (!sameRoute) {
            movingItems.clear();
            visualState.restore(0);
        }
        long snapshotTick = nbt.getLong("snapshotTick");
        if (world != null && world.isClient)
            visualState.receive(nbt.getDouble("beltDistance"), nbt.getDouble("beltStep"),
                    snapshotTick, BeltRenderClock.now(world));
        else visualState.restore(nbt.getDouble("beltDistance"));
        BeltItem.readItems(movingItems, nbt.getList("moving", NbtElement.COMPOUND_TYPE),
                registryLookup, world, "a", "b", snapshotTick, visualState);
        nextItemId = nbt.getLong("nextItemId");
        for (var item : movingItems) nextItemId = Math.max(nextItemId, item.id + 1);
        if (!sameRoute) {
            beltData = null;
            renderedModel = null;
        }
        if (world != null && beltData == null) beltData = BeltData.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        var base = super.toInitialChunkDataNbt(registryLookup);
        writeNbt(base, registryLookup);
        return base;
    }

    @Override
    public @Nullable Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
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

    @Override public BeltPickup.Access pickupAccess(Direction port) {
        return outgoingBeltTier(port) == 0 ? null : new BeltPickup.Access(beltData, beltTier, movingItems);
    }

    public Direction getOwnFacing() {
        return getCachedState().get(HorizontalFacingBlock.FACING);
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
        return hasOutputPort(port) && target != null && !target.equals(BlockPos.ORIGIN) ? beltTier : 0;
    }

    @Override public boolean upgradeOutgoingBelt(Direction port, int tier) {
        if (world == null || world.isClient || !TierUpgrade.isUpgrade(outgoingBeltTier(port), tier)) return false;
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
        if (!hasInputPort(port) || stack.isEmpty() || world == null || world.isClient || !allowsItem(stack)) return false;
        updateRedstoneSignal();
        if (!redstone.allowsTransfer()) return false;
        var targetInv = ConveyorItemApi.find(world, pos.offset(port.getOpposite()), port);
        if (targetInv == null) return false;
        if (targetInv.insert(stack, true) != stack.getCount()) return false;
        targetInv.insert(stack, false);
        if (redstone.transferred()) markDirty();
        return true;
    }

    @Override
    public void markTargeted(BlockPos source) {
        if (world != null) {
            lastTargetedTime = world.getTime();
            sourceBeltPos = source;
            // Receiving chutes do not tick an outgoing route; sync occupancy here for the preview.
            markDirtyAndSync();
        }
    }

    @Override
    public void disconnectOutgoingTo(BlockPos destination) {
        if (target == null || !target.equals(destination)) return;

        // dropContent already clears the route, drops every packet and flags the client update.
        if (world != null && !world.isClient) {
            dropContent(world, pos);
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
        sourceBeltPos = BlockPos.ORIGIN;
        lastTargetedTime = 0;
        networkDirty = true;
        markDirtyAndSync();
    }

    public boolean isUsed() {
        var usedAsTarget = world != null && !sourceBeltPos.equals(BlockPos.ORIGIN)
                && world.getTime() >= lastTargetedTime && world.getTime() - lastTargetedTime < 40;
        var usedAsSource = target != null && !target.equals(BlockPos.ORIGIN);
        return usedAsTarget || usedAsSource;
    }

    public boolean assignFromBeltItem(BlockPos target, Direction targetFacing,
                                      List<BlockPos> midpoints, int beltTier) {
        if (world == null || BeltData.create(world, pos, getOwnFacing(), target, targetFacing,
                midpoints.stream().map(point -> new Pair<>(point, world.getBlockState(point).get(HorizontalFacingBlock.FACING))).toList()) == null)
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

    public List<Pair<BlockPos, Direction>> getMidPointsWithTangents() {
        return midPoints.stream()
                 .filter(point -> world.getBlockState(point).getBlock().equals(BlockContent.CONVEYOR_SUPPORT_BLOCK.get()))
                 .map(point -> new Pair<>(point, world.getBlockState(point).get(HorizontalFacingBlock.FACING)))
                 .toList();
    }

    private Direction getTargetFacing(BlockPos target) {
        var node = world == null || target == null ? null : ConveyorNodeUtil.get(world, target);
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
        @Override public int count() { return stack.getCount(); }
        @Override public void progress(float value) { previousProgress = progress; progress = value; }
        public float renderedProgress(double tick) { return (float) animation.value(tick); }
        public boolean visible(double tick) { return tick >= bornTick && tick < retiredTick; }

        public NbtCompound write(RegistryWrapper.WrapperLookup lookup, String progressKey, String stackKey) {
            var tag = new NbtCompound();
            tag.putFloat(progressKey, progress);
            tag.putFloat("previousProgress", previousProgress);
            tag.putLong("id", id);
            TransportStacks.write(tag, stackKey, stack, lookup);
            return tag;
        }

        public static void readItems(Deque<BeltItem> items, NbtList list,
                                     RegistryWrapper.WrapperLookup lookup, World world,
                                     String progressKey, String stackKey, long snapshotTick,
                                     BeltVisualState visualState) {
            boolean client = world != null && world.isClient;
            double renderedTick = client ? visualState.presentationTick(BeltRenderClock.now(world)) : snapshotTick;
            applySnapshot(items, list, lookup, progressKey, stackKey, snapshotTick, client, renderedTick);
        }

        /** Shared snapshot merge, independent of a live world so endpoint transitions can be tested. */
        public static void applySnapshot(Deque<BeltItem> items, NbtList list,
                                          RegistryWrapper.WrapperLookup lookup,
                                          String progressKey, String stackKey, long snapshotTick,
                                          boolean client, double renderedTick) {
            Map<Long, BeltItem> previous = new HashMap<>();
            if (client)
                for (var item : items) previous.put(item.id, item);
            items.clear();
            for (var entry : list) {
                var tag = (NbtCompound) entry;
                var stack = TransportStacks.read(tag, stackKey, lookup);
                if (stack.isEmpty()) continue;
                long id = tag.getLong("id");
                float progress = Math.clamp(tag.getFloat(progressKey), 0, 1);
                var item = previous.remove(id);
                if (item == null || !ItemStack.areItemsAndComponentsEqual(item.stack, stack)) {
                    item = new BeltItem(progress, id, stack);
                    item.animation.reset(progress, snapshotTick);
                    item.bornTick = progress == 0 ? snapshotTick : Double.NEGATIVE_INFINITY;
                }
                item.stack.setCount(stack.getCount()); // Partial insertion/pickup must not reset interpolation.
                item.progress = progress;
                item.previousProgress = tag.contains("previousProgress")
                        ? Math.clamp(tag.getFloat("previousProgress"), 0, progress) : progress;
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
        private final List<Pair<Vec3d, Vec3d>> allPoints;
        private final SplineUtil.ArcLengthPath arcPath;

        public BeltData(List<Pair<Vec3d, Vec3d>> points, double[] lengths) {
            this(points, SplineUtil.ArcLengthPath.create(points, lengths));
        }

        private BeltData(List<Pair<Vec3d, Vec3d>> points, SplineUtil.ArcLengthPath arcPath) {
            this.allPoints = List.copyOf(points);
            this.arcPath = arcPath;
        }

        public List<Pair<Vec3d, Vec3d>> allPoints() { return allPoints; }
        public double totalLength() { return arcPath.length(); }
        public SplineUtil.ArcLengthPath arcPath() { return arcPath; }

        public static @Nullable BeltData create(ChuteBlockEntity entity) {
            if (entity.getWorld() == null || entity.target == null || entity.target.equals(BlockPos.ORIGIN))
                return null;

            var targetNode = ConveyorNodeUtil.get(entity.getWorld(), entity.getTarget());
            if (targetNode == null) return null;
            return create(entity.getWorld(), entity.getPos(), entity.getOwnFacing(), entity.getTarget(),
                    entity.targetFacing, entity.getMidPointsWithTangents());
        }

        public static @Nullable BeltData create(World world, BlockPos start, Direction startFacing,
                                                BlockPos target, Direction targetFacing,
                                                List<Pair<BlockPos, Direction>> midPoints) {
            return create(world, start, startFacing, target, targetFacing, midPoints, true);
        }

        public static Vec3d anchor(World world, BlockPos pos, Direction port) {
            if (world.getBlockEntity(pos) instanceof ConveyorSplitterBlockEntity
                    || world.getBlockState(pos).isOf(BlockContent.CONVEYOR_SUPPORT_BLOCK.get()))
                return pos.toCenterPos();
            return pos.toCenterPos().subtract(Vec3d.of(port.getVector()).multiply(0.5));
        }

        public static @Nullable BeltData create(World world, BlockPos start, Direction startFacing,
                                                BlockPos target, Direction targetFacing,
                                                List<Pair<BlockPos, Direction>> midPoints, boolean validate) {
            var conveyorStartDir = Vec3d.of(startFacing.getVector());
            var conveyorEndDir = Vec3d.of(targetFacing.getOpposite().getVector());
            var conveyorStartPointVisual = anchor(world, start, startFacing);
            var conveyorEndPointVisual = anchor(world, target, targetFacing);

            var transformedMidPoints = midPoints.stream()
                    .map(elem -> new Pair<>(elem.getLeft().toCenterPos(),
                            Vec3d.of(elem.getRight().getVector())))
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
