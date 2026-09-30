package pureneko.conveyor_belt_plus;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.NonNullList;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity.BeltData;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity.BeltItem;
import pureneko.conveyor_belt_plus.util.ChutePlacementPlan;
import pureneko.conveyor_belt_plus.util.SplineUtil.ArcLengthPath;
import pureneko.conveyor_belt_plus.util.TransportStacks;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.List;

public final class TransportNbtTests {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var lookup = VanillaRegistries.createLookup();
        FilterRuleTests.run(lookup);
        for (int count : new int[]{1, 64, 256, 512, 4096}) {
            var tag = new CompoundTag();
            TransportStacks.write(tag, "stack", new ItemStack(Items.COBBLESTONE, count), lookup);
            var restored = TransportStacks.read(tag, "stack", lookup);
            check(restored.is(Items.COBBLESTONE) && restored.getCount() == count, "multi-stack round trip " + count);
        }
        var legacy = new CompoundTag();
        legacy.put("stack", new ItemStack(Items.IRON_INGOT, 64).save(lookup));
        check(TransportStacks.read(legacy, "stack", lookup).getCount() == 64, "legacy NBT");
        var packet = new ChuteBlockEntity.BeltItem(0.25f, 100_000L, new ItemStack(Items.COBBLESTONE, 512));
        var list = new ListTag();
        list.add(packet.write(lookup, "progress", "stack"));
        var packets = new ArrayDeque<ChuteBlockEntity.BeltItem>();
        ChuteBlockEntity.BeltItem.readItems(packets, list, lookup, null, "progress", "stack", 0, new pureneko.conveyor_belt_plus.util.BeltVisualState());
        check(packets.size() == 1 && packets.getFirst().id == 100_000L
                && packets.getFirst().stack.getCount() == 512 && packets.getFirst().progress == 0.25f,
                "packet count, position and non-colliding id survive reload");
        clientSnapshots(lookup);
        chutePlacement();
        arcLength();
        suppliedResources();
        System.out.println("Conveyor Belt Plus: NBT, tiered chute placement, client endpoint lifecycle, constant-speed path and supplied resource checks passed.");
    }

    private static void chutePlacement() {
        // Vanilla registries are frozen in this headless loader. Use three registered horizontal block
        // items to exercise the shared variant-preserving reservation, placement-state and refund code.
        var variants = new net.minecraft.world.item.Item[]{Items.FURNACE, Items.SMOKER, Items.BLAST_FURNACE};
        java.util.function.Predicate<ItemStack> eligible = stack ->
                java.util.Arrays.stream(variants).anyMatch(stack::is);
        var main = net.minecraft.core.NonNullList.withSize(36, ItemStack.EMPTY);
        var fallback = new ItemStack(variants[0]);
        for (int tier = 0; tier < 3; tier++) {
            main.set(18, new ItemStack(variants[tier], 2));
            var inventory = pureneko.conveyor_belt_plus.util.ChutePlacementPlan.orderedInventory(main, ItemStack.EMPTY);
            var plan = pureneko.conveyor_belt_plus.util.ChutePlacementPlan.selectMatching(inventory, 2, false, fallback, eligible);
            check(plan != null && plan.size() == 2, "single-variant inventory accepted");
            check(plan.state(0, net.minecraft.core.Direction.EAST).getBlock()
                    == ((net.minecraft.world.item.BlockItem) variants[tier]).getBlock(), "placed endpoint keeps selected block");
            check(plan.state(1, net.minecraft.core.Direction.WEST)
                    .getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING) == net.minecraft.core.Direction.WEST,
                    "selected endpoint keeps requested facing");
            check(plan.consume(inventory) && main.get(18).isEmpty(), "consume exact reserved main-inventory stack");
            check(!plan.consume(inventory), "plan cannot consume twice");
        }
        main.set(0, new ItemStack(variants[0], 64));
        main.set(3, new ItemStack(variants[1], 2));
        var offhand = new ItemStack(variants[2]);
        offhand.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Offhand interface"));
        var inventory = pureneko.conveyor_belt_plus.util.ChutePlacementPlan.orderedInventory(main, offhand);
        var mixed = pureneko.conveyor_belt_plus.util.ChutePlacementPlan.selectMatching(inventory, 2, false, fallback, eligible);
        check(mixed.item(0).is(variants[2]) && mixed.item(1).is(variants[0]), "offhand first; remainder from first inventory slot");
        check(ItemStack.isSameItemSameComponents(mixed.item(0), offhand), "refund retains offhand item components");
        check(mixed.consume(inventory) && offhand.isEmpty() && main.get(0).getCount() == 63
                && main.get(3).getCount() == 2, "consume offhand then selected inventory slot, not another tier");
        check(mixed.item(0).getCount() == 1 && mixed.item(1).getCount() == 1, "failure drops retain both exact variants");
        offhand = new ItemStack(variants[1], 3);
        inventory = pureneko.conveyor_belt_plus.util.ChutePlacementPlan.orderedInventory(main, offhand);
        var enoughOffhand = pureneko.conveyor_belt_plus.util.ChutePlacementPlan.selectMatching(inventory, 2, false, fallback, eligible);
        check(enoughOffhand.item(0).is(variants[1]) && enoughOffhand.item(1).is(variants[1])
                && enoughOffhand.consume(inventory) && offhand.getCount() == 1 && main.get(0).getCount() == 63,
                "sufficient offhand supplies both endpoints without touching inventory");
        inventory = pureneko.conveyor_belt_plus.util.ChutePlacementPlan.orderedInventory(main, new ItemStack(Items.DIAMOND));
        var ordinaryOrder = pureneko.conveyor_belt_plus.util.ChutePlacementPlan.selectMatching(inventory, 2, false, fallback, eligible);
        check(ordinaryOrder.item(0).is(variants[0]) && ordinaryOrder.item(1).is(variants[0]),
                "non-interface offhand ignored; main inventory order does not depend on held slot");

        // Revalidate the whole reservation before touching any slot.
        offhand = new ItemStack(variants[2], 2);
        inventory = pureneko.conveyor_belt_plus.util.ChutePlacementPlan.orderedInventory(main, offhand);
        var changed = pureneko.conveyor_belt_plus.util.ChutePlacementPlan.selectMatching(inventory, 2, false, fallback, eligible);
        offhand.shrink(1);
        check(!changed.consume(inventory) && offhand.getCount() == 1, "insufficient reserved count causes no partial consumption");
        changed = pureneko.conveyor_belt_plus.util.ChutePlacementPlan.selectMatching(inventory, 2, false, fallback, eligible);
        inventory.set(1, new ItemStack(Items.DIAMOND, 4));
        check(!changed.consume(inventory) && offhand.getCount() == 1, "changed second slot cannot consume offhand reservation");

        for (int slot = 0; slot < main.size(); slot++) main.set(slot, ItemStack.EMPTY);
        main.set(9, new ItemStack(variants[2]));
        main.set(20, new ItemStack(variants[1]));
        inventory = pureneko.conveyor_belt_plus.util.ChutePlacementPlan.orderedInventory(main, ItemStack.EMPTY);
        var rows = pureneko.conveyor_belt_plus.util.ChutePlacementPlan.selectMatching(inventory, 2, false, fallback, eligible);
        check(rows.item(0).is(variants[2]) && rows.item(1).is(variants[1]), "main inventory scanned row by row");
        main.set(20, ItemStack.EMPTY);
        inventory = pureneko.conveyor_belt_plus.util.ChutePlacementPlan.orderedInventory(main, ItemStack.EMPTY);
        check(pureneko.conveyor_belt_plus.util.ChutePlacementPlan.selectMatching(inventory, 2, false, fallback, eligible) == null
                && main.get(9).getCount() == 1, "insufficient total leaves inventory untouched");
        var single = pureneko.conveyor_belt_plus.util.ChutePlacementPlan.selectMatching(inventory, 1, false, fallback, eligible);
        check(single.size() == 1 && single.consume(inventory), "one existing endpoint needs only one interface");
        var none = pureneko.conveyor_belt_plus.util.ChutePlacementPlan.selectMatching(inventory, 0, false, fallback, eligible);
        check(none.size() == 0 && none.consume(inventory), "two existing endpoints need no interfaces");
        check(pureneko.conveyor_belt_plus.util.ChutePlacementPlan.selectMatching(inventory, 1, false, fallback, eligible) == null,
                "empty survival inventory cannot place an endpoint");
        check(!pureneko.conveyor_belt_plus.util.ChutePlacementPlan.isChute(new ItemStack(Items.CHEST)), "unrelated block item not accepted");
        check(pureneko.conveyor_belt_plus.util.ChutePlacementPlan.count(inventory) == 0, "empty preview inventory has no interfaces");
        var creative = pureneko.conveyor_belt_plus.util.ChutePlacementPlan.selectMatching(inventory, 2, true, fallback, eligible);
        check(creative.size() == 2 && creative.item(0).is(variants[0]) && creative.consume(inventory),
                "creative mode without interfaces keeps standard fallback");
        offhand = new ItemStack(variants[1]);
        inventory = pureneko.conveyor_belt_plus.util.ChutePlacementPlan.orderedInventory(main, offhand);
        creative = pureneko.conveyor_belt_plus.util.ChutePlacementPlan.selectMatching(inventory, 2, true, fallback, eligible);
        check(creative.item(0).is(variants[1]) && creative.item(1).is(variants[1])
                && creative.consume(inventory) && offhand.getCount() == 1,
                "creative mode copies offhand variant without consumption");
        check(inventory.size() == 37 && inventory.getFirst() == offhand, "all inventory stacks follow offhand");
        for (int slot = 0; slot < main.size(); slot++)
            check(inventory.get(slot + 1) == main.get(slot), "inventory order uses live stack references");
    }

    private static void clientSnapshots(net.minecraft.core.HolderLookup.Provider lookup) {
        for (String progressKey : new String[]{"a", "progress"}) {
            String stackKey = progressKey.equals("a") ? "b" : "stack";
            var serverItem = new ChuteBlockEntity.BeltItem(42L, new ItemStack(Items.IRON_INGOT, 256));
            var items = new ArrayDeque<ChuteBlockEntity.BeltItem>();
            var snapshot = new ListTag();
            snapshot.add(serverItem.write(lookup, progressKey, stackKey));
            ChuteBlockEntity.BeltItem.applySnapshot(items, snapshot, lookup, progressKey, stackKey, 10, true, 8);
            var renderedItem = items.getFirst();
            check(!renderedItem.visible(9.99) && renderedItem.visible(10), "birth follows buffered timeline");
            check(renderedItem.renderedProgress(10) == 0, "new output starts at exact route origin");

            for (int tick = 11; tick <= 19; tick++) {
                serverItem.progress((tick - 10) / 10f);
                snapshot = new ListTag();
                snapshot.add(serverItem.write(lookup, progressKey, stackKey));
                ChuteBlockEntity.BeltItem.applySnapshot(items, snapshot, lookup, progressKey, stackKey,
                        tick, true, tick - 2);
                check(items.getFirst() == renderedItem, "snapshots retain interpolation object identity");
                for (int subframe = 0; subframe < 6; subframe++) {
                    double time = tick - 1 + subframe / 6d;
                    check(Math.abs(renderedItem.renderedProgress(time) - (time - 10) / 10d) < 0.00001,
                            "client packets interpolate every frame rather than jump every tick");
                }
            }
            check(Math.abs(renderedItem.renderedProgress(50) - 0.9) < 0.00001,
                    "blocked packets never predict through the destination");
            serverItem.stack.setCount(128);
            snapshot = new ListTag();
            snapshot.add(serverItem.write(lookup, progressKey, stackKey));
            ChuteBlockEntity.BeltItem.applySnapshot(items, snapshot, lookup, progressKey, stackKey, 20, true, 18);
            check(items.getFirst() == renderedItem && renderedItem.stack.getCount() == 128,
                    "partial insertion or pickup preserves buffered interpolation");
            ChuteBlockEntity.BeltItem.applySnapshot(items, new ListTag(), lookup, progressKey, stackKey,
                    100, true, 98);
            check(items.size() == 1 && items.getFirst() == renderedItem, "accepted packet retained during render buffer");
            check(Math.abs(renderedItem.renderedProgress(98.5) - 0.9) < 0.00001,
                    "no false drift across long blocked interval before final removal");
            check(Math.abs(renderedItem.renderedProgress(99.5) - 0.95) < 0.00001,
                    "retiring packet smoothly reaches the endpoint");
            check(renderedItem.visible(99.99) && !renderedItem.visible(100), "no rendering after retirement");
            ChuteBlockEntity.BeltItem.applySnapshot(items, new ListTag(), lookup, progressKey, stackKey,
                    101, true, 99);
            check(items.size() == 1 && !renderedItem.visible(100), "later snapshots do not extend retirement");
            ChuteBlockEntity.BeltItem.applySnapshot(items, new ListTag(), lookup, progressKey, stackKey,
                    102, true, 100);
            check(items.isEmpty(), "retired packet cleaned up after buffered endpoint");

            snapshot = new ListTag();
            snapshot.add(serverItem.write(lookup, progressKey, stackKey));
            ChuteBlockEntity.BeltItem.applySnapshot(items, snapshot, lookup, progressKey, stackKey,
                    110, false, 110);
            ChuteBlockEntity.BeltItem.applySnapshot(items, new ListTag(), lookup, progressKey, stackKey,
                    111, false, 111);
            check(items.isEmpty(), "server never keeps render-only packets or duplicates their contents");
        }
    }

    private static void arcLength() {
        var east = new net.minecraft.world.phys.Vec3(1, 0, 0);
        var south = new net.minecraft.world.phys.Vec3(0, 0, 1);
        var start = net.minecraft.world.phys.Vec3.ZERO;
        var end = new net.minecraft.world.phys.Vec3(10, 0, 0);
        var straight = new ChuteBlockEntity.BeltData(java.util.List.of(
                new net.minecraft.util.Tuple<>(start, east), new net.minecraft.util.Tuple<>(end, east)),
                new double[]{10d});
        for (int step = 0; step <= 1000; step++) {
            var point = pureneko.conveyor_belt_plus.util.SplineUtil.getPositionOnSpline(straight, step / 1000d);
            check(Math.abs(point.x - step / 100d) < 0.0001, "no Hermite acceleration on straight belts");
        }
        end = new net.minecraft.world.phys.Vec3(8, 0, 8);
        var curve = new ChuteBlockEntity.BeltData(java.util.List.of(
                new net.minecraft.util.Tuple<>(start, east), new net.minecraft.util.Tuple<>(end, south)),
                new double[]{12d});
        double segmentLength = curve.totalLength() / 400;
        var previous = curve.arcPath().position(0);
        for (int step = 1; step <= 400; step++) {
            var point = curve.arcPath().position(step / 400d);
            check(Math.abs(point.distanceTo(previous) - segmentLength) < 0.001,
                    "constant speed around corners");
            previous = point;
        }
        var validPath = pureneko.conveyor_belt_plus.util.SplineUtil.ArcLengthPath.create(java.util.List.of(
                new net.minecraft.util.Tuple<>(start, east),
                new net.minecraft.util.Tuple<>(new net.minecraft.world.phys.Vec3(10, 0, 0), east)));
        check(pureneko.conveyor_belt_plus.util.SplineUtil.isPathUsable(validPath, east, east),
                "straight path remains valid");
        var reversal = pureneko.conveyor_belt_plus.util.SplineUtil.ArcLengthPath.create(java.util.List.of(
                new net.minecraft.util.Tuple<>(start, east),
                new net.minecraft.util.Tuple<>(new net.minecraft.world.phys.Vec3(1, 0, 0), east.reverse())));
        check(!pureneko.conveyor_belt_plus.util.SplineUtil.isPathUsable(reversal, east, east.reverse()),
                "sharp reversal remains invalid");
    }

    private static void suppliedResources() {
        for (int tier = 1; tier <= 3; tier++) {
            String texture = pureneko.conveyor_belt_plus.util.BeltMaterials.textureName(tier);
            String model = tier == 1 ? "belt" : texture;
            try (var imageStream = TransportNbtTests.class.getResourceAsStream(
                    "/assets/conveyor_belt_plus/textures/block/" + texture + ".png");
                 var modelStream = TransportNbtTests.class.getResourceAsStream(
                    "/assets/conveyor_belt_plus/models/item/" + model + ".json");
                 var metadata = TransportNbtTests.class.getResourceAsStream(
                    "/assets/conveyor_belt_plus/textures/block/" + texture + ".png.mcmeta")) {
                var image = javax.imageio.ImageIO.read(imageStream);
                check(image.getWidth() == 16 && image.getHeight() == 256, "supplied sixteen-frame sheet");
                var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(modelStream))
                        .getAsJsonObject();
                check(json.getAsJsonObject("textures").get("belt").getAsString()
                        .equals("conveyor_belt_plus:block/" + texture), "inventory and world use matching materials");
                check(json.getAsJsonArray("elements").size() == 1, "supplied Blockbench geometry");
                var animation = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(metadata))
                        .getAsJsonObject().getAsJsonObject("animation");
                check(animation.getAsJsonArray("frames").size() == 1, "inventory atlas cannot drive placed belts");
            } catch (java.io.IOException exception) {
                throw new AssertionError(exception);
            }
        }
    }

    private static void check(boolean success, String message) {
        if (!success) throw new AssertionError(message);
    }
}
