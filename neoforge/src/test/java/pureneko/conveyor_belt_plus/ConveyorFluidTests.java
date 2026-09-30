package pureneko.conveyor_belt_plus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;
import pureneko.conveyor_belt_plus.blocks.*;
import pureneko.conveyor_belt_plus.blocks.BeltPickup.Access;
import pureneko.conveyor_belt_plus.blocks.BeltPickup.Result;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity.BeltItem;
import pureneko.conveyor_belt_plus.blocks.ConveyorSplitterBlockEntity.Route;
import pureneko.conveyor_belt_plus.config.ConveyorConfig;
import pureneko.conveyor_belt_plus.filter.FilterRule;
import pureneko.conveyor_belt_plus.network.FilterNetworking;
import pureneko.conveyor_belt_plus.network.FilterNetworking.Edit;
import pureneko.conveyor_belt_plus.network.FilterNetworking.Snapshot;
import pureneko.conveyor_belt_plus.registry.*;
import pureneko.conveyor_belt_plus.screen.ChuteScreenHandler;
import pureneko.conveyor_belt_plus.util.*;
import pureneko.conveyor_belt_plus.util.BeltTransport.Step;
import java.util.*;

/** Test-only sided tanks and a reusable container exercise the same public capabilities used by other mods. */
@GameTestHolder(ConveyorBeltPlus.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = ConveyorBeltPlus.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ConveyorFluidTests {
    private static final Map<BlockPos, Storage> STORAGE = new HashMap<>();
    private static final class Storage {
        final FluidTank tank;
        final ItemStackHandler items = new ItemStackHandler(4);
        Direction onlySide;
        Storage(int capacity) { tank = new FluidTank(capacity); }
    }
    @SubscribeEvent public static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.FluidHandler.BLOCK, (world, pos, state, be, side) -> {
            var storage = STORAGE.get(pos);
            return storage == null || storage.onlySide != null && storage.onlySide != side ? null : storage.tank;
        }, Blocks.GOLD_BLOCK);
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (world, pos, state, be, side) -> {
            var storage = STORAGE.get(pos);
            return storage == null ? null : storage.items;
        }, Blocks.GOLD_BLOCK);
        event.registerItem(Capabilities.FluidHandler.ITEM, (stack, context) -> new TestContainer(stack), Items.BOWL);
    }
    private static final class TestContainer extends FluidTank implements IFluidHandlerItem {
        private final ItemStack stack;
        TestContainer(ItemStack stack) {
            super(6000);
            this.stack = stack;
            setFluid(stack.getOrDefault(ComponentContent.FLUID_CONTENT.get(), FluidContent.EMPTY).copy());
        }
        @Override public ItemStack getContainer() { return stack; }
        @Override protected void onContentsChanged() {
            if (isEmpty()) stack.remove(ComponentContent.FLUID_CONTENT.get());
            else stack.set(ComponentContent.FLUID_CONTENT.get(), new FluidContent(getFluid()));
        }
    }
    private static Storage storage(GameTestHelper ctx, BlockPos pos, int capacity) {
        ctx.getLevel().setBlockAndUpdate(pos, Blocks.GOLD_BLOCK.defaultBlockState());
        var storage = new Storage(capacity);
        STORAGE.put(pos, storage);
        ctx.getLevel().invalidateCapabilities(pos);
        return storage;
    }
    private static ChuteBlockEntity chute(GameTestHelper ctx, BlockPos pos, Block block, Direction facing) {
        ctx.getLevel().setBlockAndUpdate(pos, block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing));
        return (ChuteBlockEntity) ctx.getLevel().getBlockEntity(pos);
    }
    private static ChuteBlockEntity route(GameTestHelper ctx, Block block, int z) {
        var pos = ctx.absolutePos(new BlockPos(2, 2, z));
        var source = chute(ctx, pos, block, Direction.EAST);
        chute(ctx, pos.east(10), BlockContent.UNIVERSAL_CHUTE.get(), Direction.WEST);
        ctx.assertTrue(source.connectOutgoing(Direction.EAST, pos.east(10), Direction.WEST, List.of(), 1), "shared route connects");
        return source;
    }
    private static void tick(ChuteBlockEntity chute) { chute.tick(chute.getLevel(), chute.getBlockPos(), chute.getBlockState(), chute); }
    private static ItemStack pull(ChuteBlockEntity chute) {
        var queue = chute.pickupAccess(Direction.EAST).items();
        queue.clear();
        tick(chute);
        return queue.isEmpty() ? ItemStack.EMPTY : queue.getFirst().stack.copy();
    }

    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void tierAmountsSidesAndConfig(GameTestHelper ctx) {
        double speed = ConveyorConfig.SPEEDS[0].get();
        int amount = ConveyorConfig.FLUID_AMOUNTS[0].get();
        try {
            ConveyorConfig.SPEEDS[0].set(64.0);
            Block[] blocks = {BlockContent.FLUID_CHUTE.get(), BlockContent.ADVANCED_FLUID_CHUTE.get(), BlockContent.ULTIMATE_FLUID_CHUTE.get()};
            int[] amounts = {1000, 16000, 64000};
            for (int tier = 0; tier < 3; tier++) {
                var chute = route(ctx, blocks[tier], 3 + tier * 4);
                var source = storage(ctx, chute.getBlockPos().west(), 200_000);
                source.tank.setFluid(new FluidStack(Fluids.WATER, 200_000));
                ctx.assertTrue(FluidPackets.amount(pull(chute)) == amounts[tier]
                        && source.tank.getFluidAmount() == 200_000 - amounts[tier], "configured tier extraction conserves mB");
                source.onlySide = Direction.UP;
                ctx.assertTrue(pull(chute).isEmpty(), "attached face cannot bypass a sided tank");
                chute.setExtractionSides(ExtractionSide.TOP.bit() | ExtractionSide.BOTTOM.bit());
                ctx.assertTrue(FluidPackets.amount(pull(chute)) == amounts[tier], "multi-face selection stays one batch");
                chute.setExtractionSides(0);
                ctx.assertTrue(pull(chute).isEmpty(), "empty side selection stops extraction");
                if (tier == 0) {
                    ConveyorConfig.FLUID_AMOUNTS[0].set(1234);
                    chute.setExtractionSides(ExtractionSide.TOP.bit());
                    ctx.assertTrue(FluidPackets.amount(pull(chute)) == 1234, "live batch configuration is used");
                }
                STORAGE.remove(chute.getBlockPos().west());
            }
        } finally { ConveyorConfig.SPEEDS[0].set(speed); ConveyorConfig.FLUID_AMOUNTS[0].set(amount); }
        ctx.succeed();
    }

    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void partialDeliveryBackpressureAndPulse(GameTestHelper ctx) {
        var pos = ctx.absolutePos(new BlockPos(10, 2, 5));
        var receiver = chute(ctx, pos, BlockContent.FLUID_CHUTE.get(), Direction.WEST);
        var target = storage(ctx, pos.east(), 1750);
        var packet = new ChuteBlockEntity.BeltItem(1, 7, FluidPackets.create(new FluidStack(Fluids.WATER, 64000)));
        var queue = new ArrayDeque<ChuteBlockEntity.BeltItem>(); queue.add(packet);
        receiver.setRule(0, FilterRule.fluidTag("minecraft:water"));
        receiver.setWhitelistMode(true);
        ctx.assertFalse(receiver.acceptFromBelt(FluidPackets.create(new FluidStack(Fluids.LAVA, 1000)), Direction.WEST), "water tag refuses lava");
        receiver.setRedstoneMode(RedstoneControl.Mode.NEVER);
        ctx.assertFalse(receiver.acceptFromBelt(packet.stack, Direction.WEST), "fluid redstone never stops insertion");
        receiver.setRedstoneMode(RedstoneControl.Mode.ALWAYS);
        var result = BeltTransport.tickWithMotion(queue, 10, 1, p -> receiver.acceptFromBelt(p.stack, Direction.WEST));
        ctx.assertTrue(result.changed() && queue.size() == 1 && target.tank.getFluidAmount() == 1750
                && FluidPackets.amount(packet.stack) == 62250, "partial fill syncs and keeps every remaining mB on the belt");
        ctx.assertFalse(BeltTransport.tick(queue, 10, 1, p -> receiver.acceptFromBelt(p.stack, Direction.WEST)), "full tank blocks without mutating packet");
        target.tank.drain(1750, IFluidHandler.FluidAction.EXECUTE);
        var saved = receiver.saveWithFullMetadata(ctx.getLevel().registryAccess());
        var profile = saved.getCompound("fluidSettings");
        profile.putInt("redstoneMode", RedstoneControl.Mode.PULSE.ordinal());
        profile.putBoolean("redstonePulsePending", true);
        receiver.loadWithComponents(saved, ctx.getLevel().registryAccess());
        receiver.acceptFromBelt(packet.stack, Direction.WEST);
        ctx.assertTrue(target.tank.getFluidAmount() == 1750 && FluidPackets.amount(packet.stack) == 60500, "pending pulse survives save and permits one partial fill");
        target.tank.drain(1750, IFluidHandler.FluidAction.EXECUTE);
        ctx.assertFalse(receiver.acceptFromBelt(packet.stack, Direction.WEST), "successful partial fill consumes only one pulse");
        STORAGE.remove(pos.east());
        ctx.succeed();
    }

    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void universalMixedTransportAndIndependentSettings(GameTestHelper ctx) {
        double speed = ConveyorConfig.SPEEDS[0].get();
        try {
            ConveyorConfig.SPEEDS[0].set(64.0);
            var sender = route(ctx, BlockContent.UNIVERSAL_CHUTE.get(), 5);
            var source = storage(ctx, sender.getBlockPos().west(), 16000);
            var target = storage(ctx, sender.getBlockPos().east(11), 16000);
            source.tank.setFluid(new FluidStack(Fluids.WATER, 5000));
            source.items.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 100));
            sender.setRule(false, 0, FilterRule.item(new ItemStack(Items.IRON_INGOT), false));
            sender.setRule(true, 0, FilterRule.fluid(new FluidStack(Fluids.WATER, 1000)));
            sender.setWhitelistMode(false, true);
            sender.setWhitelistMode(true, true);
            sender.setRedstoneMode(true, RedstoneControl.Mode.NEVER);
            ctx.assertTrue(pull(sender).is(Items.IRON_INGOT) && source.tank.getFluidAmount() == 5000, "fluid stop does not disable items");
            // Return the deliberately inspected item batch before running end-to-end transport.
            sender.pickupAccess(Direction.EAST).items().clear();
            source.items.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 100));
            sender.setRedstoneMode(true, RedstoneControl.Mode.ALWAYS);
            for (int t = 0; t < 40; t++) tick(sender);
            int received = 0; for (int i = 0; i < target.items.getSlots(); i++) received += target.items.getStackInSlot(i).getCount();
            ctx.assertTrue(received == 100 && target.tank.getFluidAmount() == 5000 && source.tank.isEmpty()
                    && !sender.getMovingItems().iterator().hasNext(), "one belt transports both domains completely");
            var itemOnly = chute(ctx, sender.getBlockPos().south(3), BlockContent.CHUTE_BLOCK.get(), Direction.EAST);
            var fluidOnly = chute(ctx, sender.getBlockPos().south(4), BlockContent.FLUID_CHUTE.get(), Direction.EAST);
            ctx.assertFalse(itemOnly.acceptFromBelt(FluidPackets.create(new FluidStack(Fluids.WATER, 1000)), Direction.EAST), "item interface rejects fluid carriers");
            ctx.assertFalse(fluidOnly.acceptFromBelt(new ItemStack(Items.IRON_INGOT), Direction.EAST), "fluid interface rejects physical items");
            STORAGE.remove(sender.getBlockPos().west()); STORAGE.remove(sender.getBlockPos().east(11));
        } finally { ConveyorConfig.SPEEDS[0].set(speed); }
        ctx.succeed();
    }

    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void bucketTankPickupConservation(GameTestHelper ctx) {
        var player = ctx.makeMockPlayer(GameType.SURVIVAL);
        var packet = FluidPackets.create(new FluidStack(Fluids.WATER, 16000));
        ctx.assertTrue(BeltPickup.transferToInventory(player, packet) == 0 && FluidPackets.amount(packet) == 16000, "empty-hand pickup never gives a virtual carrier");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET, 2));
        ctx.assertTrue(BeltPickup.fillHeldContainer(player, packet) == 1000 && FluidPackets.amount(packet) == 15000
                && player.getMainHandItem().is(Items.BUCKET) && player.getMainHandItem().getCount() == 1
                && player.getInventory().countItem(Items.WATER_BUCKET) == 1, "stacked buckets consume one and keep one filled bucket");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOWL));
        ctx.assertTrue(BeltPickup.fillHeldContainer(player, packet) == 6000 && FluidPackets.amount(packet) == 9000
                && FluidPackets.marker(player.getMainHandItem()).getAmount() == 6000, "arbitrary mod container capacity is honored");
        ctx.assertTrue(BeltPickup.fillHeldContainer(player, packet) == 0 && FluidPackets.amount(packet) == 9000, "full mod container cannot drain fluid");
        var lava = new ItemStack(Items.BOWL); lava.set(ComponentContent.FLUID_CONTENT.get(), new FluidContent(new FluidStack(Fluids.LAVA, 100)));
        player.setItemInHand(InteractionHand.MAIN_HAND, lava);
        ctx.assertTrue(BeltPickup.fillHeldContainer(player, packet) == 0, "incompatible fluid cannot be mixed");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET, 2));
        for (int i = 1; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        ctx.assertTrue(BeltPickup.fillHeldContainer(player, packet) == 0 && FluidPackets.amount(packet) == 9000, "full inventory refuses a stacked-container pickup without deleting anything");
        var small = FluidPackets.create(new FluidStack(Fluids.WATER, 500));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        ctx.assertTrue(BeltPickup.fillHeldContainer(player, small) == 0 && FluidPackets.amount(small) == 500, "bucket cannot take a fraction of a bucket");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOWL));
        ctx.assertTrue(BeltPickup.fillHeldContainer(player, small) == 500 && small.isEmpty(), "mod tank can take the final fraction exactly");
        var creative = ctx.makeMockPlayer(GameType.CREATIVE);
        creative.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        ctx.assertTrue(BeltPickup.fillHeldContainer(creative, packet) == 1000 && creative.getMainHandItem().is(Items.WATER_BUCKET), "creative pickup also conserves fluid");
        ctx.succeed();
    }

    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void fluidPersistenceRulesTabsAndUpgrade(GameTestHelper ctx) {
        var pos = ctx.absolutePos(new BlockPos(5, 2, 5));
        var chute = chute(ctx, pos, BlockContent.UNIVERSAL_CHUTE.get(), Direction.EAST);
        var water = new FluidStack(Fluids.WATER, 64000);
        water.set(DataComponents.CUSTOM_NAME, Component.literal("Component water"));
        var parcel = FluidPackets.create(water);
        var nbt = new CompoundTag(); TransportStacks.write(nbt, "packet", parcel, ctx.getLevel().registryAccess());
        var restored = FluidPackets.get(TransportStacks.read(nbt, "packet", ctx.getLevel().registryAccess()));
        ctx.assertTrue(FluidStack.matches(water, restored), "fluid amount and components survive serialization");
        chute.setRule(false, 0, FilterRule.tag("minecraft:logs"));
        chute.setRule(true, 0, FilterRule.fluid(FluidPackets.marker(new ItemStack(Items.WATER_BUCKET))));
        chute.setRule(true, 4, FilterRule.fluidTag("minecraft:water"));
        chute.setWhitelistMode(true, true);
        chute.setRedstoneMode(true, RedstoneControl.Mode.NEVER);
        chute.setExtractionSides(true, ExtractionSide.TOP.bit());
        ctx.assertTrue(chute.getRedstoneMode(false) == RedstoneControl.Mode.ALWAYS && !chute.isWhitelistMode(false), "profiles have separate redstone and list modes");
        var player = ctx.makeMockPlayer(GameType.SURVIVAL); player.setPos(pos.getCenter());
        var menu = new ChuteScreenHandler(8, player.getInventory(), chute);
        ctx.assertTrue(menu.clickMenuButton(player, ChuteScreenHandler.FLUID_TAB) && menu.isFluidTab(), "right tab selects fluid rules");
        menu.applyEdit(player, false, 0, FilterRule.item(new ItemStack(Items.DIAMOND), false).toText(ctx.getLevel().registryAccess()));
        ctx.assertTrue(chute.getRule(true, 0).isFluidRule(), "late edit from the previous tab is rejected");
        menu.applyEdit(player, true, 1, FilterRule.fluidTag("minecraft:lava").toText(ctx.getLevel().registryAccess()));
        ctx.assertTrue(chute.getRule(true, 1).kind() == FilterRule.Kind.FLUID_TAG && chute.getRule(false, 1).isEmpty(), "tab edit changes only its own domain");
        int oldLimit = ConveyorConfig.FLUID_FILTER_LIMITS[0].get();
        try {
            var saved = chute.saveWithFullMetadata(ctx.getLevel().registryAccess());
            ConveyorConfig.FLUID_FILTER_LIMITS[0].set(1);
            chute.loadWithComponents(saved, ctx.getLevel().registryAccess());
            ctx.assertTrue(chute.getRule(true, 4).isEmpty(), "reduced filter capacity deactivates excess rules");
            ConveyorConfig.FLUID_FILTER_LIMITS[0].set(oldLimit);
            ctx.assertTrue(chute.getRule(true, 4).kind() == FilterRule.Kind.FLUID_TAG, "raising capacity recovers dormant fluid rules");
        } finally { ConveyorConfig.FLUID_FILTER_LIMITS[0].set(oldLimit); }
        player.setShiftKeyDown(true); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemContent.ADVANCED_UNIVERSAL_CHUTE.get()));
        ctx.assertTrue(UpgradeInteractions.chute(ctx.getLevel(), pos, player, player.getMainHandItem(), (ChuteBlock) BlockContent.ADVANCED_UNIVERSAL_CHUTE.get()), "universal tier upgrades in place");
        var upgraded = (ChuteBlockEntity) ctx.getLevel().getBlockEntity(pos);
        ctx.assertTrue(upgraded.getRule(false, 0).kind() == FilterRule.Kind.TAG && upgraded.getRule(true, 0).isFluidRule()
                && upgraded.getRedstoneMode(true) == RedstoneControl.Mode.NEVER && upgraded.getExtractionSides(true) == ExtractionSide.TOP.bit(), "upgrade retains both independent profiles");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemContent.ULTIMATE_FLUID_CHUTE.get()));
        ctx.assertFalse(UpgradeInteractions.chute(ctx.getLevel(), pos, player, player.getMainHandItem(), (ChuteBlock) BlockContent.ULTIMATE_FLUID_CHUTE.get()), "cross-domain replacement cannot discard a profile");
        ctx.succeed();
    }

    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void fluidSplitterAndPartialSnapshot(GameTestHelper ctx) {
        var pos = ctx.absolutePos(new BlockPos(7, 2, 7));
        ctx.getLevel().setBlockAndUpdate(pos, BlockContent.SPLITTER.get().defaultBlockState());
        var splitter = (ConveyorSplitterBlockEntity) ctx.getLevel().getBlockEntity(pos);
        chute(ctx, pos.east(6), BlockContent.FLUID_CHUTE.get(), Direction.WEST);
        chute(ctx, pos.north(6), BlockContent.UNIVERSAL_CHUTE.get(), Direction.SOUTH);
        splitter.connectIncoming(Direction.WEST, pos.west(4));
        ctx.assertTrue(splitter.connectOutgoing(Direction.EAST, pos.east(6), Direction.WEST, List.of(), 1)
                && splitter.connectOutgoing(Direction.NORTH, pos.north(6), Direction.SOUTH, List.of(), 1), "splitter has shared output routes");
        var input = FluidPackets.create(new FluidStack(Fluids.WATER, 16001));
        ctx.assertTrue(splitter.acceptFromBelt(input, Direction.WEST), "splitter accepts a fluid packet");
        ctx.assertFalse(splitter.acceptFromBelt(input, Direction.WEST), "fluid packets cannot be accidentally item-stacked");
        var saved = splitter.saveWithFullMetadata(ctx.getLevel().registryAccess());
        splitter.loadWithComponents(saved, ctx.getLevel().registryAccess());
        splitter.tick(ctx.getLevel(), pos, splitter.getBlockState(), splitter);
        int sum = 0; int min = Integer.MAX_VALUE, max = 0;
        for (var route : splitter.getRoutes()) for (var packet : route.getMovingItems()) {
            int amount = FluidPackets.amount(packet.stack); sum += amount; min = Math.min(min, amount); max = Math.max(max, amount);
        }
        ctx.assertTrue(sum == 16001 && max - min == 1 && splitter.getCachedItemCount() == 0, "odd millibuckets split evenly without loss");
        var access = splitter.pickupAccess(Direction.EAST); var packet = access.items().getFirst();
        BeltPickup.applyClient(splitter, Direction.EAST, packet.id, 7000);
        ctx.assertTrue(FluidPackets.amount(packet.stack) == 7000 && packet.stack.getCount() == 1, "pickup acknowledgement updates mB, not item count");
        var list = new net.minecraft.nbt.ListTag(); list.add(packet.write(ctx.getLevel().registryAccess(), "a", "b"));
        ChuteBlockEntity.BeltItem.applySnapshot(access.items(), list, ctx.getLevel().registryAccess(), "a", "b", 4, true, 2);
        ctx.assertTrue(access.items().getFirst() == packet, "amount changes preserve packet interpolation identity");
        ctx.succeed();
    }

    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void renamedRegistryAndFluidRulePayload(GameTestHelper ctx) {
        for (String prefix : List.of("", "advanced_", "ultimate_")) {
            var current = ConveyorBeltPlus.id(prefix + "item_chute");
            for (String namespace : List.of(ConveyorBeltPlus.MOD_ID, "logisticsplus")) {
                var old = ResourceLocation.fromNamespaceAndPath(namespace, prefix + "chute");
                ctx.assertTrue(BuiltInRegistries.BLOCK.get(old) == BuiltInRegistries.BLOCK.get(current)
                        && BuiltInRegistries.ITEM.get(old) == BuiltInRegistries.ITEM.get(current), "old block and item IDs migrate");
            }
        }
        var lookup = ctx.getLevel().registryAccess();
        var rule = FilterRule.fluidTag("minecraft:water");
        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), lookup);
        try {
            var edit = new FilterNetworking.Edit(9, true, 3, rule.toText(lookup));
            FilterNetworking.Edit.CODEC.encode(buf, edit);
            ctx.assertTrue(FilterNetworking.Edit.CODEC.decode(buf).equals(edit), "edit includes fluid domain");
            var snapshot = new FilterNetworking.Snapshot(9, true, true, Collections.nCopies(5, rule.toText(lookup)), "");
            FilterNetworking.Snapshot.CODEC.encode(buf, snapshot);
            ctx.assertTrue(FilterNetworking.Snapshot.CODEC.decode(buf).equals(snapshot), "snapshot includes selected tab and rules");
        } finally { buf.release(); }
        ctx.succeed();
    }
    @GameTest(template = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void aimedFluidPickupAndImmutableComponents(GameTestHelper ctx) {
        var chute = route(ctx, BlockContent.FLUID_CHUTE.get(), 5);
        var contents = new FluidStack(Fluids.WATER, 16000);
        var packet = FluidPackets.create(contents);
        contents.setAmount(1);
        var snapshot = FluidPackets.get(packet); snapshot.setAmount(2);
        ctx.assertTrue(FluidPackets.amount(packet) == 16000, "caller mutations cannot change immutable parcel components");
        var entry = new ChuteBlockEntity.BeltItem(.5f, 4321, packet);
        chute.pickupAccess(Direction.EAST).items().add(entry);
        var player = ctx.makeMockPlayer(GameType.SURVIVAL);
        var point = SplineUtil.getPositionOnSpline(chute.getBeltData(), .5f).add(0, .06, 0);
        player.setPosRaw(point.x, point.y, point.z + 2);
        var offset = point.subtract(player.getEyePosition());
        player.setYRot((float) Math.toDegrees(Math.atan2(-offset.x, offset.z)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(offset.y, Math.sqrt(offset.x * offset.x + offset.z * offset.z))));
        ctx.assertTrue(BeltPickup.take(player, chute.getBlockPos(), Direction.EAST, 4321, .5f).taken() == 0, "empty hand cannot collect fluid as an inventory item");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        ctx.assertTrue(BeltPickup.take(player, chute.getBlockPos(), Direction.EAST, 4321, Float.NaN).taken() == 0, "invalid fluid pickup ray is rejected");
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock> deny = event -> {
            if (event.getEntity() == player) event.setCanceled(true);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(deny);
        try { ctx.assertTrue(BeltPickup.take(player, chute.getBlockPos(), Direction.EAST, 4321, .5f).taken() == 0, "protection mods can cancel fluid pickup"); }
        finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(deny); }
        var result = BeltPickup.take(player, chute.getBlockPos(), Direction.EAST, 4321, .5f);
        ctx.assertTrue(result.taken() == 1000 && result.remaining() == 15000 && player.getMainHandItem().is(Items.WATER_BUCKET)
                && entry.progress == .5f && chute.pickupAccess(Direction.EAST).items().getFirst() == entry, "aimed bucket pickup updates exact mB and retains packet motion");
        ctx.assertTrue(BeltPickup.take(player, chute.getBlockPos(), Direction.EAST, 4321, .5f).taken() == 0, "a full bucket cannot drain again");
        var encoded = new net.minecraft.nbt.CompoundTag();
        TransportStacks.write(encoded, "recovery", packet, ctx.getLevel().registryAccess());
        var recovery = TransportStacks.read(encoded, "recovery", ctx.getLevel().registryAccess());
        ctx.assertTrue(net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(recovery).orElseThrow().getAmount() == 15000,
                "broken-belt recovery parcels expose real conserved fluid to mod capabilities");
        ctx.succeed();
    }

}
