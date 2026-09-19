package pureneko.conveyor_belt_plus;

import com.google.gson.JsonParser;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pureneko.conveyor_belt_plus.blocks.ChuteBlockEntity;
import pureneko.conveyor_belt_plus.compat.jade.ChuteRedstoneProvider;
import pureneko.conveyor_belt_plus.registry.BlockContent;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.util.RedstoneControl.Mode;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;

import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** Enabled only with -PwithJade=true, using the real optional Jade API. */
@GameTestHolder(ConveyorBeltPlus.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConveyorJadeTests {
    @GameTest(templateName = "empty", templateNamespace = ConveyorBeltPlus.MOD_ID)
    public static void redstoneTextAndSnapshot(TestContext context) throws Exception {
        var lines = new ArrayList<Text>();
        var tooltip = (ITooltip) Proxy.newProxyInstance(ITooltip.class.getClassLoader(), new Class<?>[]{ITooltip.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("add") && args.length == 1 && args[0] instanceof Text text) {
                        lines.add(text);
                        return null;
                    }
                    throw new AssertionError("Redstone tooltip must add only plain text: " + method);
                });
        String[] chinese = {"始终工作", "无红石信号工作", "收到红石信号工作", "从不工作", "红石脉冲"};
        try (var stream = ConveyorJadeTests.class.getResourceAsStream("/assets/conveyor_belt_plus/lang/zh_cn.json")) {
            var language = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var block : new net.minecraft.block.Block[]{BlockContent.CHUTE_BLOCK.get(), BlockContent.ADVANCED_CHUTE.get(), BlockContent.ULTIMATE_CHUTE.get()}) {
                var pos = context.getAbsolutePos(new BlockPos(4, 2, 4));
                context.getWorld().setBlockState(pos, block.getDefaultState());
                var chute = (ChuteBlockEntity) context.getWorld().getBlockEntity(pos);
                for (var mode : Mode.values()) {
                    chute.setRedstoneMode(mode);
                    // Jade reads the same BE data that an ordinary chunk update sends to a client.
                    var snapshot = chute.toInitialChunkDataNbt(context.getWorld().getRegistryManager());
                    var restored = new ChuteBlockEntity(pos, block.getDefaultState());
                    restored.read(snapshot, context.getWorld().getRegistryManager());
                    lines.clear();
                    ChuteRedstoneProvider.INSTANCE.appendTooltip(tooltip, accessor(restored, false), null);
                    context.assertTrue(lines.size() == 1, "exactly one text line for every tier/mode");
                    context.assertTrue(lines.getFirst().getContent() instanceof TranslatableTextContent, "uses localized mode name");
                    var key = ((TranslatableTextContent) lines.getFirst().getContent()).getKey();
                    context.assertTrue(key.equals(mode.translationKey()) && language.get(key).getAsString().equals(chinese[mode.ordinal()]),
                            "exact Chinese mode name without prefix or icon");
                    lines.clear();
                    ChuteRedstoneProvider.INSTANCE.appendTooltip(tooltip, accessor(restored, true), null);
                    context.assertTrue(lines.isEmpty(), "aiming at a belt packet must not display the owner's mode");
                }
            }
            lines.clear();
            ChuteRedstoneProvider.INSTANCE.appendTooltip(tooltip, accessor(null, false), null);
            context.assertTrue(lines.isEmpty(), "missing block entity is safe");
        }
        context.complete();
    }

    private static BlockAccessor accessor(BlockEntity entity, boolean fake) {
        return (BlockAccessor) Proxy.newProxyInstance(BlockAccessor.class.getClassLoader(), new Class<?>[]{BlockAccessor.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isFakeBlock" -> fake;
                    case "getBlockEntity" -> entity;
                    default -> throw new AssertionError("Unexpected accessor query: " + method);
                });
    }
}
