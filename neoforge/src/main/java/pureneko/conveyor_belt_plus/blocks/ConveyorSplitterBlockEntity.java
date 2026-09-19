package pureneko.conveyor_belt_plus.blocks;

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
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import pureneko.conveyor_belt_plus.registry.BlockContent;
import pureneko.conveyor_belt_plus.registry.BlockEntitiesContent;
import pureneko.conveyor_belt_plus.registry.ItemContent;
import pureneko.conveyor_belt_plus.util.BeltTiers;
import pureneko.conveyor_belt_plus.util.BeltTransport;
import pureneko.conveyor_belt_plus.util.TransportStacks;
import pureneko.conveyor_belt_plus.util.SplitDistribution;
import pureneko.conveyor_belt_plus.util.BeltVisualState;
import pureneko.conveyor_belt_plus.util.TierUpgrade;
import pureneko.conveyor_belt_plus.util.BeltQuad;
import pureneko.conveyor_belt_plus.util.BeltRenderClock;
import pureneko.conveyor_belt_plus.config.ConveyorConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A four-sided logistics device with one cached item type.
 * Each side becomes an input or output according to how the belt is connected.
 */
public class ConveyorSplitterBlockEntity extends BlockEntity
        implements BlockEntityTicker<ConveyorSplitterBlockEntity>, ConveyorNode {

    private static final int GEOMETRY_RETRY_TICKS = 20;

    private long nextItemId;

    private ItemStack cachedItem = ItemStack.EMPTY;
    private final Deque<Integer> cachedBatches = new ArrayDeque<>();
    private final Map<Direction, BlockPos> incomingSources = new EnumMap<>(Direction.class);
    private final Map<Direction, Route> outgoing = new EnumMap<>(Direction.class);
    private int roundRobinIndex;
    private boolean networkDirty;

    public ConveyorSplitterBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesContent.SPLITTER.get(), pos, state);
    }

    public int getCachedItemCount() {
        return cachedItem.isEmpty() ? 0 : cachedItem.getCount();
    }

    /** A display snapshot, never a mutable reference to the transport buffer. */
    public ItemStack getCachedItemSnapshot() { return cachedItem.copy(); }

    public Iterable<Route> getRoutes() {
        return outgoing.values();
    }

    @Override public BeltPickup.Access pickupAccess(Direction port) {
        var route = outgoing.get(port);
        return route == null ? null : new BeltPickup.Access(route.beltData, route.beltTier, route.movingItems);
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, ConveyorSplitterBlockEntity blockEntity) {
        refreshRouteGeometry(world);

        if (world.isClient) return;

        for (var route : outgoing.values()) {
            if (route.beltData != null)
                moveItemsOnRoute(world, route);
        }
        dispatchCachedItems();

        if (networkDirty && world instanceof ServerWorld serverWorld) {
            serverWorld.getChunkManager().markForUpdate(pos);
            networkDirty = false;
        }
    }

    private void moveItemsOnRoute(World world, Route route) {
        var step = BeltTransport.tickWithMotion(route.movingItems, route.beltData.totalLength(), BeltTiers.speed(route.beltTier),
                packet -> ConveyorNodeUtil.accept(world, route.target, route.targetPort, packet.stack));
        route.visualState.advance(step.distanceMoved(), world.getTime());
        if (step.changed()) {
            // The tick tail broadcasts one update per tick; only the dirty flag is needed here.
            networkDirty = true;
            markDirty();
        }
    }

    /**
     * Rebuilds a route spline only when the blocks shaping it changed, so a stable route does not
     * re-sample the spline every tick. A build that fails (unusable spline) backs off instead of
     * being retried immediately.
     */
    private void refreshRouteGeometry(World world) {
        for (var route : outgoing.values()) {
            if (ConveyorNodeUtil.get(world, route.target) == null) {
                route.beltData = null;
                continue;
            }
            var signature = geometrySignature(world, route);
            if (route.beltData != null && signature == route.geometrySignature) continue;
            if (route.geometryRetryAt > world.getTime()) continue;
            route.geometrySignature = signature;
            route.beltData = createBeltData(route);
            route.geometryRetryAt = route.beltData == null ? world.getTime() + GEOMETRY_RETRY_TICKS : 0;
        }
    }

    /** Order-sensitive hash of the blocks that shape a route spline. */
    private static long geometrySignature(World world, Route route) {
        long signature = world.getBlockState(route.target).getBlock().hashCode();
        for (var point : route.supports) {
            var state = world.getBlockState(point);
            if (!state.isOf(BlockContent.CONVEYOR_SUPPORT_BLOCK.get())) continue;
            signature = signature * 31 + point.asLong();
            signature = signature * 31 + state.get(HorizontalFacingBlock.FACING).ordinal();
        }
        return signature;
    }

    /** Marks the entity dirty and pushes the change to tracking clients. */
    private void markDirtyAndSync() {
        markDirty();
        if (world instanceof ServerWorld serverWorld) serverWorld.getChunkManager().markForUpdate(pos);
    }

    private void dispatchCachedItems() {
        boolean changed = false;
        while (!cachedItem.isEmpty() && !cachedBatches.isEmpty()) {
            var routes = getReadyRoutes();
            if (routes == null) break;

            var batchSize = Math.min(cachedBatches.peekFirst(), cachedItem.getCount());
            var distribution = SplitDistribution.divide(batchSize, routes.size(), roundRobinIndex);

            for (int i = 0; i < routes.size(); i++) {
                var count = distribution.counts()[i];
                if (count == 0) continue;

                var route = routes.get(i);
                route.movingItems.addFirst(new ChuteBlockEntity.BeltItem(
                        nextItemId++,
                        cachedItem.copyWithCount(count)));
                networkDirty = true;
            }

            cachedItem.decrement(batchSize);
            cachedBatches.removeFirst();
            if (cachedItem.isEmpty()) cachedItem = ItemStack.EMPTY;
            roundRobinIndex = distribution.nextIndex();
            changed = true;
        }

        if (cachedItem.isEmpty()) cachedBatches.clear();
        if (changed) markDirty();
    }

    /** Each input batch is split only across outputs whose entrance currently has room. */
    private @Nullable List<Route> getReadyRoutes() {
        if (outgoing.isEmpty()) return null;
        List<Route> ready = null;
        for (var route : outgoing.values()) {
            if (route.beltData == null || !BeltTransport.canLoad(route.movingItems, route.beltData.totalLength())) continue;
            if (ready == null) ready = new ArrayList<>(outgoing.size());
            ready.add(route);
        }
        return ready;
    }

    @Override
    public boolean hasInputPort(Direction port) {
        return port.getAxis().isHorizontal();
    }

    @Override
    public boolean hasOutputPort(Direction port) {
        return port.getAxis().isHorizontal();
    }

    @Override
    public boolean isPortUsed(Direction port) {
        return incomingSources.containsKey(port) || outgoing.containsKey(port);
    }

    @Override public int outgoingBeltTier(Direction port) {
        var route = outgoing.get(port);
        return route == null ? 0 : route.beltTier;
    }

    @Override public boolean upgradeOutgoingBelt(Direction port, int tier) {
        if (world == null || world.isClient || !TierUpgrade.isUpgrade(outgoingBeltTier(port), tier)) return false;
        outgoing.get(port).beltTier = tier;
        networkDirty = true;
        markDirtyAndSync();
        return true;
    }

    @Override
    public void connectIncoming(Direction port, BlockPos source) {
        if (hasInputPort(port) && !isPortUsed(port)) {
            incomingSources.put(port, source);
            networkDirty = true;
            markDirty();
        }
    }

    @Override
    public boolean connectOutgoing(Direction port, BlockPos target, Direction targetPort,
                                   List<BlockPos> supports, int beltTier) {
        if (!hasOutputPort(port) || isPortUsed(port)) return false;
        var route = new Route(port, target, targetPort, List.copyOf(supports), BeltTiers.normalize(beltTier));
        route.beltData = createBeltData(route);
        if (route.beltData == null) return false;
        outgoing.put(port, route);
        networkDirty = true;
        markDirtyAndSync();
        return true;
    }

    @Override
    public boolean acceptFromBelt(ItemStack stack, Direction port) {
        if (world == null || world.isClient || !hasInputPort(port) || !incomingSources.containsKey(port)
                || stack.isEmpty()) return false;
        if (!cachedItem.isEmpty() && !ItemStack.areItemsAndComponentsEqual(cachedItem, stack)) return false;

        var freeSpace = ConveyorConfig.splitterBufferItems() - getCachedItemCount();
        if (stack.getCount() > freeSpace) return false;

        if (cachedItem.isEmpty())
            cachedItem = stack.copy();
        else
            cachedItem.increment(stack.getCount());
        cachedBatches.addLast(stack.getCount());
        markDirty();
        networkDirty = true;
        return true;
    }

    /** Oversized batches wait on the incoming belt and fill the buffer in bounded pieces. */
    @Override
    public boolean acceptPartialFromBelt(ItemStack stack, Direction port) {
        int count = Math.min(stack.getCount(), ConveyorConfig.splitterBufferItems() - getCachedItemCount());
        if (count <= 0 || !acceptFromBelt(stack.copyWithCount(count), port)) return false;
        stack.decrement(count);
        return stack.isEmpty();
    }

    @Override
    public void markTargeted(BlockPos source) {
        // The source belt owns the connection. The stored source positions are used for persistence.
    }

    @Override
    public void disconnectOutgoingTo(BlockPos destination) {
        var removed = outgoing.entrySet().removeIf(entry -> {
            var route = entry.getValue();
            if (!route.target.equals(destination)) return false;
            for (var item : route.movingItems) TransportStacks.drop(world, pos, item.stack);
            TransportStacks.drop(world, pos, ItemContent.beltStackForTier(route.beltTier));
            return true;
        });
        if (removed) {
            networkDirty = true;
            markDirtyAndSync();
        }
    }

    @Override
    public void disconnectIncomingFrom(BlockPos source) {
        var removed = incomingSources.entrySet().removeIf(entry -> entry.getValue().equals(source));
        if (removed) {
            networkDirty = true;
            markDirty();
        }
    }

    private @Nullable ChuteBlockEntity.BeltData createBeltData(Route route) {
        if (world == null || ConveyorNodeUtil.get(world, route.target) == null) return null;
        var midpoints = route.supports.stream()
                .filter(point -> world.getBlockState(point).isOf(BlockContent.CONVEYOR_SUPPORT_BLOCK.get()))
                .map(point -> new Pair<>(point, world.getBlockState(point).get(HorizontalFacingBlock.FACING)))
                .toList();
        return ChuteBlockEntity.BeltData.create(world, pos, route.port, route.target,
                route.targetPort, midpoints);
    }

    public void dropContent(World world, BlockPos pos) {
        for (var sourcePos : new ArrayList<>(incomingSources.values())) {
            var source = ConveyorNodeUtil.get(world, sourcePos);
            if (source != null) source.disconnectOutgoingTo(pos);
        }
        for (var route : outgoing.values()) {
            var target = ConveyorNodeUtil.get(world, route.target);
            if (target != null) target.disconnectIncomingFrom(pos);
        }

        if (!cachedItem.isEmpty()) TransportStacks.drop(world, pos, cachedItem);
        cachedItem = ItemStack.EMPTY;
        cachedBatches.clear();

        for (var route : outgoing.values()) {
            for (var item : route.movingItems)
                TransportStacks.drop(world, pos, item.stack);
            TransportStacks.drop(world, pos, ItemContent.beltStackForTier(route.beltTier));
        }
        outgoing.clear();
        incomingSources.clear();
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        TransportStacks.write(nbt, "cache", cachedItem, registryLookup);
        nbt.putLong("nextItemId", nextItemId);
        nbt.putLong("snapshotTick", world == null ? 0 : world.getTime());
        nbt.putIntArray("cacheBatches", cachedBatches.stream().mapToInt(Integer::intValue).toArray());

        var inputs = new NbtList();
        incomingSources.forEach((port, source) -> {
            var entry = new NbtCompound();
            entry.putByte("port", (byte) port.getId());
            entry.putLong("source", source.asLong());
            inputs.add(entry);
        });
        nbt.put("incoming", inputs);

        var routes = new NbtList();
        outgoing.forEach((port, route) -> {
            var entry = new NbtCompound();
            entry.putByte("port", (byte) port.getId());
            entry.putLong("target", route.target.asLong());
            entry.putByte("targetPort", (byte) route.targetPort.getId());
            entry.putInt("beltTier", route.beltTier);
            entry.putDouble("beltDistance", route.visualState.distance());
            entry.putDouble("beltStep", world == null ? 0 : route.visualState.step(world.getTime()));

            var supports = new NbtList();
            for (var support : route.supports) {
                var supportTag = new NbtCompound();
                supportTag.putLong("pos", support.asLong());
                supports.add(supportTag);
            }
            entry.put("supports", supports);

            var moving = new NbtList();
            for (var item : route.movingItems)
                moving.add(item.write(registryLookup, "progress", "stack"));
            entry.put("moving", moving);
            routes.add(entry);
        });
        nbt.put("outgoing", routes);
        nbt.putInt("roundRobin", roundRobinIndex);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        cachedItem = TransportStacks.read(nbt, "cache", registryLookup);
        nextItemId = nbt.getLong("nextItemId");
        cachedBatches.clear();
        var remainingCache = cachedItem.getCount();
        for (var batch : nbt.getIntArray("cacheBatches")) {
            if (batch <= 0 || remainingCache <= 0) continue;
            var accepted = Math.min(batch, remainingCache);
            cachedBatches.addLast(accepted);
            remainingCache -= accepted;
        }
        if (remainingCache > 0) cachedBatches.addLast(remainingCache);

        var previousRoutes = new EnumMap<>(outgoing);
        incomingSources.clear();
        outgoing.clear();

        for (var entry : nbt.getList("incoming", NbtElement.COMPOUND_TYPE)) {
            var tag = (NbtCompound) entry;
            var port = Direction.byId(tag.getByte("port"));
            if (port != null && hasInputPort(port))
                incomingSources.put(port, BlockPos.fromLong(tag.getLong("source")));
        }

        for (var entry : nbt.getList("outgoing", NbtElement.COMPOUND_TYPE)) {
            var tag = (NbtCompound) entry;
            var port = Direction.byId(tag.getByte("port"));
            var targetPort = Direction.byId(tag.getByte("targetPort"));
            if (port == null || targetPort == null || !hasOutputPort(port)) continue;

            var supports = new ArrayList<BlockPos>();
            for (var support : tag.getList("supports", NbtElement.COMPOUND_TYPE))
                supports.add(BlockPos.fromLong(((NbtCompound) support).getLong("pos")));

            var beltTier = tag.contains("beltTier", NbtElement.INT_TYPE)
                    ? BeltTiers.normalize(tag.getInt("beltTier")) : BeltTiers.STANDARD;
            var target = BlockPos.fromLong(tag.getLong("target"));
            var route = previousRoutes.get(port);
            if (route == null || !route.target.equals(target) || route.targetPort != targetPort
                    || !route.supports.equals(supports))
                route = new Route(port, target, targetPort, supports, beltTier);
            route.beltTier = beltTier; // Keep client packet interpolation and animation during upgrades.
            long snapshotTick = nbt.getLong("snapshotTick");
            if (world != null && world.isClient)
                route.visualState.receive(tag.getDouble("beltDistance"), tag.getDouble("beltStep"),
                        snapshotTick, BeltRenderClock.now(world));
            else route.visualState.restore(tag.getDouble("beltDistance"));
            ChuteBlockEntity.BeltItem.readItems(route.movingItems,
                    tag.getList("moving", NbtElement.COMPOUND_TYPE), registryLookup, world, "progress", "stack", snapshotTick, route.visualState);
            for (var item : route.movingItems) nextItemId = Math.max(nextItemId, item.id + 1);
            outgoing.put(port, route);
        }

        roundRobinIndex = Math.max(0, nbt.getInt("roundRobin"));
        if (world != null) {
            for (var route : outgoing.values())
                if (route.beltData == null) route.beltData = createBeltData(route);
        }
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        var tag = super.toInitialChunkDataNbt(registryLookup);
        writeNbt(tag, registryLookup);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    public static final class Route {
        private final Direction port;
        private final BlockPos target;
        private final Direction targetPort;
        private final List<BlockPos> supports;
        private int beltTier;
        private final Deque<ChuteBlockEntity.BeltItem> movingItems = new ArrayDeque<>();
        private ChuteBlockEntity.BeltData beltData;
        public BeltQuad[] renderedModel;
        private final BeltVisualState visualState = new BeltVisualState();
        /** Inputs that produced {@link #beltData}; a change forces a rebuild. */
        private long geometrySignature;
        /** World time before which a failed geometry build is not retried. */
        private long geometryRetryAt;

        private Route(Direction port, BlockPos target, Direction targetPort,
                      List<BlockPos> supports, int beltTier) {
            this.port = port;
            this.target = target;
            this.targetPort = targetPort;
            this.supports = supports;
            this.beltTier = beltTier;
        }

        public BeltVisualState getVisualState() { return visualState; }
        public Direction getPort() { return port; }
        public int getBeltTier() { return beltTier; }
        public ChuteBlockEntity.BeltData getBeltData() { return beltData; }
        public Iterable<ChuteBlockEntity.BeltItem> getMovingItems() { return movingItems; }
    }
}
