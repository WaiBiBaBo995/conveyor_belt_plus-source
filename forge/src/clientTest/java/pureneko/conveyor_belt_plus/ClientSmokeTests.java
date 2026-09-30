package pureneko.conveyor_belt_plus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pureneko.conveyor_belt_plus.blocks.ChuteBlock;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity;
import pureneko.conveyor_belt_plus.client.screens.ChuteScreen;
import pureneko.conveyor_belt_plus.client.screens.ConveyorConfigScreen;
import pureneko.conveyor_belt_plus.registry.BlockContent;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.config.ConveyorConfig;
import pureneko.conveyor_belt_plus.screen.ChuteScreenHandler;
import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Opt-in isolated client fixture; never compiled into the release mod. */
@Mod.EventBusSubscriber(modid = ConveyorBeltPlus.MOD_ID, value = Dist.CLIENT)
public final class ClientSmokeTests {
    private static int phase, ticks, totalTicks;
    private static volatile boolean ready;
    private static boolean expectedWhitelist;
    private static final BlockPos SOURCE = new BlockPos(0, 80, 0);

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        if (++totalTicks > 3600) throw new AssertionError("Client smoke test timed out in phase " + phase);
        ticks++;
        client.getToasts().clear();
        client.gui.getChat().clearMessages(false);
        if (totalTicks == 1) ConveyorBeltPlus.LOGGER.info("Client smoke fixture started: {}", client.screen);
        if (phase == 0 && ticks == 20) client.setScreen(new TitleScreen());
        try {
            if (phase == 0 && client.screen instanceof TitleScreen && ticks > 30) {
                screenshot(client, "01-title.png");
                client.createWorldOpenFlows().loadLevel(new TitleScreen(), "ForgePortSmoke");
                advance();
            } else if (phase == 1 && client.level != null && client.player != null) {
                client.getSingleplayerServer().execute(() -> {
                    var world = client.getSingleplayerServer().overworld();
                    var player = client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
                    player.setGameMode(GameType.CREATIVE);
                    for (int x = -3; x <= 14; x++) for (int z = -3; z <= 9; z++) {
                        world.setBlockAndUpdate(new BlockPos(x, 79, z), Blocks.SMOOTH_STONE.defaultBlockState());
                        for (int y = 80; y < 86; y++) world.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                    for (int tier = 0; tier < 3; tier++) {
                        var source = SOURCE.south(tier * 3);
                        var target = source.east(10);
                        var block = switch (tier) {
                            case 1 -> BlockContent.ADVANCED_CHUTE.get();
                            case 2 -> BlockContent.ULTIMATE_CHUTE.get();
                            default -> BlockContent.CHUTE_BLOCK.get();
                        };
                        world.setBlockAndUpdate(source.west(), Blocks.CHEST.defaultBlockState());
                        world.setBlockAndUpdate(target.east(), Blocks.CHEST.defaultBlockState());
                        world.setBlockAndUpdate(source, block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
                        world.setBlockAndUpdate(target, block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
                        ((ChestBlockEntity) world.getBlockEntity(source.west())).setItem(0, new ItemStack(Items.DIAMOND, 64));
                        if (!((ChuteBlockEntity) world.getBlockEntity(source)).connectOutgoing(Direction.EAST, target,
                                Direction.WEST, java.util.List.of(), tier + 1)) throw new AssertionError("Smoke route failed");
                    }
                    world.setDayTime(6000);
                    player.teleportTo(world, 5, 84, 12, 180, 30);
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    ready = true;
                });
                advance();
            } else if (phase == 2 && ready && ticks > 70) {
                if (client.level.getBlockEntity(SOURCE) == null) throw new AssertionError("Client block entity did not synchronize");
                screenshot(client, "02-conveyors.png");
                client.getSingleplayerServer().execute(() -> {
                    var world = client.getSingleplayerServer().overworld();
                    var player = client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
                    player.teleportTo(world, 1.5, 81, 2.5, 180, 20);
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    var state = world.getBlockState(SOURCE);
                    ((ChuteBlock) state.getBlock()).use(state, world, SOURCE, player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(SOURCE.getCenter(), Direction.EAST, SOURCE, false));
                });
                advance();
            } else if (phase == 3 && ticks > 20) {
                if (!(client.screen instanceof ChuteScreen screen)) throw new AssertionError("Network menu did not open");
                screenshot(client, "03-chute.png");
                expectedWhitelist = !screen.getMenu().isWhitelistMode();
                screen.acceptGhost(0, new ItemStack(Items.DIAMOND));
                client.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, ChuteScreenHandler.MODE_BUTTON);
                advance();
            } else if (phase == 4 && ticks > 20) {
                if (!(client.screen instanceof ChuteScreen screen)
                        || !screen.getMenu().getRule(0).prototype().is(Items.DIAMOND)
                        || screen.getMenu().isWhitelistMode() != expectedWhitelist)
                    throw new AssertionError("Filter edit / mode packets did not round trip");
                screenshot(client, "04-filter-sync.png");
                client.player.closeContainer();
                client.setScreen(new ConveyorConfigScreen(new TitleScreen()));
                advance();
            } else if (phase == 5 && ticks > 10) {
                if (!(client.screen instanceof ConveyorConfigScreen)) throw new AssertionError("Config screen missing");
                screenshot(client, "05-config.png");
                ((EditBox) client.screen.children().stream().filter(EditBox.class::isInstance)
                        .findFirst().orElseThrow()).setValue("3.25");
                String save = net.minecraft.network.chat.Component.translatable("config.conveyor_belt_plus.save").getString();
                ((Button) client.screen.children().stream().filter(child -> child instanceof Button button
                        && button.getMessage().getString().equals(save)).findFirst().orElseThrow()).onPress();
                // Resume the integrated server so its queued config write is executed.
                client.setScreen(null);
                advance();
            } else if (phase == 6 && ticks > 20) {
                if (ConveyorConfig.SPEEDS[0].get() != 3.25) throw new AssertionError("Config did not save on server thread");
                ready = false;
                client.getSingleplayerServer().execute(() -> {
                    var world = client.getSingleplayerServer().overworld();
                    var player = client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
                    for (int i = 1; i <= 2; i++) {
                        var pos = SOURCE.south(i * 3);
                        var block = i == 1 ? BlockContent.FLUID_CHUTE.get() : BlockContent.UNIVERSAL_CHUTE.get();
                        world.setBlockAndUpdate(pos, block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
                        world.setBlockAndUpdate(pos.east(10), block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
                        var chute = (ChuteBlockEntity) world.getBlockEntity(pos);
                        chute.connectOutgoing(Direction.EAST, pos.east(10), Direction.WEST, java.util.List.of(), i);
                        chute.pickupAccess(Direction.EAST).items().add(new ChuteBlockEntity.BeltItem(.4f, 8765 + i,
                                pureneko.conveyor_belt_plus.util.FluidPackets.create(new net.minecraftforge.fluids.FluidStack(
                                        i == 1 ? net.minecraft.world.level.material.Fluids.WATER : net.minecraft.world.level.material.Fluids.LAVA, 16000))));
                        chute.setChanged();
                        world.getChunkSource().blockChanged(pos);
                    }
                    player.teleportTo(world, 5, 84, 12, 180, 30);
                    ready = true;
                });
                advance();
            } else if (phase == 7 && ready && ticks > 50) {
                screenshot(client, "06-fluid-belts.png");
                openChute(client, SOURCE.south(3));
                advance();
            } else if (phase == 8 && ticks > 20) {
                var screen = (ChuteScreen) client.screen;
                if (!screen.isFluidTab()) throw new AssertionError("Fluid chute opened item rules");
                screen.acceptGhost(0, new ItemStack(Items.WATER_BUCKET));
                screen.acceptFluidGhost(1, new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 1000));
                advance();
            } else if (phase == 9 && ticks > 20) {
                var screen = (ChuteScreen) client.screen;
                if (screen.getMenu().getRule(0).fluidIcon().getFluid() != net.minecraft.world.level.material.Fluids.WATER
                        || screen.getMenu().getRule(1).fluidIcon().getFluid() != net.minecraft.world.level.material.Fluids.LAVA)
                    throw new AssertionError("Bucket and direct fluid markers did not synchronize as fluids");
                assertEditVisible(screen, false);
                screenshot(client, "07-fluid-rules.png");
                client.player.closeContainer();
                openChute(client, SOURCE.south(6));
                advance();
            } else if (phase == 10 && ticks > 20) {
                var screen = (ChuteScreen) client.screen;
                assertEditVisible(screen, false);
                screen.acceptGhost(0, new ItemStack(Items.DIAMOND));
                client.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, ChuteScreenHandler.FLUID_TAB);
                advance();
            } else if (phase == 11 && ticks > 20) {
                var screen = (ChuteScreen) client.screen;
                if (!screen.isFluidTab()) throw new AssertionError("Universal fluid tab did not synchronize");
                screen.acceptGhost(0, new ItemStack(Items.WATER_BUCKET));
                advance();
            } else if (phase == 12 && ticks > 20) {
                var screen = (ChuteScreen) client.screen;
                assertEditVisible(screen, false);
                if (screen.getMenu().getRule(0).fluidIcon().getFluid() != net.minecraft.world.level.material.Fluids.WATER)
                    throw new AssertionError("Universal fluid rule did not retain fluid icon");
                screenshot(client, "08-universal-fluid.png");
                client.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, ChuteScreenHandler.ITEM_TAB);
                advance();
            } else if (phase == 13 && ticks > 20) {
                var screen = (ChuteScreen) client.screen;
                if (!screen.getMenu().getRule(0).prototype().is(Items.DIAMOND))
                    throw new AssertionError("Universal fluid edit replaced item rule");
                assertEditVisible(screen, false);
                screenshot(client, "09-universal-items.png");
                button(screen, "screen.conveyor_belt_plus.rule_item").onPress();
                advance();
            } else if (phase == 14 && ticks > 20) {
                var screen = (ChuteScreen) client.screen;
                assertEditVisible(screen, true);
                screenshot(client, "10-nbt-edit.png");
                client.player.closeContainer();
                client.setScreen(new ConveyorConfigScreen(new TitleScreen()));
                button(client.screen, "screen.conveyor_belt_plus.tab_items").onPress();
                advance();
            } else if (phase == 15 && ticks > 10) {
                screenshot(client, "11-fluid-config.png");
                Files.writeString(client.gameDirectory.toPath().resolve("smoke-success.txt"),
                        "Java 17 Forge client: conveyor/fluid rendering, fluid icons, item edit visibility, universal tabs, filter packets and both config pages passed.\n");
                advance();
            } else if (phase == 16 && ticks > 10) {
                client.stop();
                phase = 17;
            }
        } catch (Exception error) {
            throw new RuntimeException("Client smoke test failed in phase " + phase, error);
        }
    }
    private static Button button(net.minecraft.client.gui.screens.Screen screen, String key) {
        String label = net.minecraft.network.chat.Component.translatable(key).getString();
        return (Button) screen.children().stream().filter(child -> child instanceof Button button
                && button.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }
    private static void assertEditVisible(ChuteScreen screen, boolean expected) {
        if (button(screen, "screen.conveyor_belt_plus.edit_components").visible != expected)
            throw new AssertionError("Incorrect edit button visibility");
    }
    private static void openChute(Minecraft client, BlockPos pos) {
        client.getSingleplayerServer().execute(() -> {
            var world = client.getSingleplayerServer().overworld();
            var player = client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
            player.teleportTo(world, pos.getX() + 1.5, pos.getY() + 1, pos.getZ() + 2.5, 180, 20);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            var state = world.getBlockState(pos);
            ((ChuteBlock) state.getBlock()).use(state, world, pos, player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(pos.getCenter(), Direction.EAST, pos, false));
        });
    }
    private static void advance() { phase++; ticks = 0; }
    private static void screenshot(Minecraft client, String name) throws java.io.IOException {
        var path = client.gameDirectory.toPath().resolve("screenshots");
        Files.createDirectories(path);
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) { image.writeToFile(path.resolve(name)); }
    }
}
