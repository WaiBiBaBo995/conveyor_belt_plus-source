package pureneko.conveyor_belt_plus;

import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
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

import java.nio.file.Files;

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
        var client = MinecraftClient.getInstance();
        if (++totalTicks > 3600) throw new AssertionError("Client smoke test timed out in phase " + phase);
        ticks++;
        if (totalTicks == 1) ConveyorBeltPlus.LOGGER.info("Client smoke fixture started: {}", client.currentScreen);
        if (phase == 0 && ticks == 20) client.setScreen(new TitleScreen());
        try {
            if (phase == 0 && client.currentScreen instanceof TitleScreen && ticks > 30) {
                screenshot(client, "01-title.png");
                client.createIntegratedServerLoader().start(new TitleScreen(), "ForgePortSmoke");
                advance();
            } else if (phase == 1 && client.world != null && client.player != null) {
                client.getServer().execute(() -> {
                    var world = client.getServer().getOverworld();
                    var player = client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                    player.changeGameMode(GameMode.CREATIVE);
                    for (int x = -3; x <= 14; x++) for (int z = -3; z <= 9; z++) {
                        world.setBlockState(new BlockPos(x, 79, z), Blocks.SMOOTH_STONE.getDefaultState());
                        for (int y = 80; y < 86; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());
                    }
                    for (int tier = 0; tier < 3; tier++) {
                        var source = SOURCE.south(tier * 3);
                        var target = source.east(10);
                        var block = switch (tier) {
                            case 1 -> BlockContent.ADVANCED_CHUTE.get();
                            case 2 -> BlockContent.ULTIMATE_CHUTE.get();
                            default -> BlockContent.CHUTE_BLOCK.get();
                        };
                        world.setBlockState(source.west(), Blocks.CHEST.getDefaultState());
                        world.setBlockState(target.east(), Blocks.CHEST.getDefaultState());
                        world.setBlockState(source, block.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.EAST));
                        world.setBlockState(target, block.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.WEST));
                        ((ChestBlockEntity) world.getBlockEntity(source.west())).setStack(0, new ItemStack(Items.DIAMOND, 64));
                        if (!((ChuteBlockEntity) world.getBlockEntity(source)).connectOutgoing(Direction.EAST, target,
                                Direction.WEST, java.util.List.of(), tier + 1)) throw new AssertionError("Smoke route failed");
                    }
                    world.setTimeOfDay(6000);
                    player.teleport(world, 5, 84, 12, 180, 30);
                    player.getAbilities().flying = true;
                    player.sendAbilitiesUpdate();
                    ready = true;
                });
                advance();
            } else if (phase == 2 && ready && ticks > 70) {
                if (client.world.getBlockEntity(SOURCE) == null) throw new AssertionError("Client block entity did not synchronize");
                screenshot(client, "02-conveyors.png");
                client.getServer().execute(() -> {
                    var world = client.getServer().getOverworld();
                    var player = client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                    player.teleport(world, 1.5, 81, 2.5, 180, 20);
                    player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
                    var state = world.getBlockState(SOURCE);
                    ((ChuteBlock) state.getBlock()).onUse(state, world, SOURCE, player, Hand.MAIN_HAND,
                            new BlockHitResult(SOURCE.toCenterPos(), Direction.EAST, SOURCE, false));
                });
                advance();
            } else if (phase == 3 && ticks > 20) {
                if (!(client.currentScreen instanceof ChuteScreen screen)) throw new AssertionError("Network menu did not open");
                screenshot(client, "03-chute.png");
                expectedWhitelist = !screen.getScreenHandler().isWhitelistMode();
                screen.acceptGhost(0, new ItemStack(Items.DIAMOND));
                client.interactionManager.clickButton(screen.getScreenHandler().syncId, ChuteScreenHandler.MODE_BUTTON);
                advance();
            } else if (phase == 4 && ticks > 20) {
                if (!(client.currentScreen instanceof ChuteScreen screen)
                        || !screen.getScreenHandler().getRule(0).prototype().isOf(Items.DIAMOND)
                        || screen.getScreenHandler().isWhitelistMode() != expectedWhitelist)
                    throw new AssertionError("Filter edit / mode packets did not round trip");
                screenshot(client, "04-filter-sync.png");
                client.player.closeHandledScreen();
                client.setScreen(new ConveyorConfigScreen(new TitleScreen()));
                advance();
            } else if (phase == 5 && ticks > 10) {
                if (!(client.currentScreen instanceof ConveyorConfigScreen)) throw new AssertionError("Config screen missing");
                screenshot(client, "05-config.png");
                ((TextFieldWidget) client.currentScreen.children().stream().filter(TextFieldWidget.class::isInstance)
                        .findFirst().orElseThrow()).setText("3.25");
                String save = net.minecraft.text.Text.translatable("config.conveyor_belt_plus.save").getString();
                ((ButtonWidget) client.currentScreen.children().stream().filter(child -> child instanceof ButtonWidget button
                        && button.getMessage().getString().equals(save)).findFirst().orElseThrow()).onPress();
                // Resume the integrated server so its queued config write is executed.
                client.setScreen(null);
                advance();
            } else if (phase == 6 && ticks > 20) {
                if (ConveyorConfig.SPEEDS[0].get() != 3.25) throw new AssertionError("Config did not save on server thread");
                Files.writeString(client.runDirectory.toPath().resolve("smoke-success.txt"),
                        "Java 17 Forge client: title, synchronized conveyor rendering, chute menu, bidirectional filter/mode packets and config save passed.\n");
                advance();
            } else if (phase == 7 && ticks > 10) {
                client.scheduleStop();
                phase = 8;
            }
        } catch (Exception error) {
            throw new RuntimeException("Client smoke test failed in phase " + phase, error);
        }
    }
    private static void advance() { phase++; ticks = 0; }
    private static void screenshot(MinecraftClient client, String name) throws java.io.IOException {
        var path = client.runDirectory.toPath().resolve("screenshots");
        Files.createDirectories(path);
        try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) { image.writeTo(path.resolve(name)); }
    }
}
