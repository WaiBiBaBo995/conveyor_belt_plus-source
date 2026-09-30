package pureneko.conveyor_belt_plus.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue;

/** Native NeoForge SERVER configuration: persistence, validation, reload and sync belong to the loader. */
public final class ConveyorConfig {
    public static final String FILE_NAME = "conveyor_belt_plus.toml";
    public static final int MAX_FILTER_RULES = 54;
    public static final int MAX_SPLITTER_BUFFER = 1_048_576;
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue[] SPEEDS = new ModConfigSpec.DoubleValue[3];
    public static final ModConfigSpec.IntValue[] STACKS = new ModConfigSpec.IntValue[3];
    public static final ModConfigSpec.IntValue[] FILTER_LIMITS = new ModConfigSpec.IntValue[3];
    public static final ModConfigSpec.IntValue[] FLUID_AMOUNTS = new ModConfigSpec.IntValue[3];
    public static final ModConfigSpec.IntValue[] FLUID_FILTER_LIMITS = new ModConfigSpec.IntValue[3];
    public static final ModConfigSpec.IntValue SPLITTER_BUFFER;
    public static final ModConfigSpec.BooleanValue CHUTE_EXTRACTION_SIDES;

    static {
        var builder = new ModConfigSpec.Builder();
        String[] names = {"standard", "advanced", "ultimate"};
        String[] chinese = {"普通", "高级", "终极"};
        int[] speed = {1, 2, 4}, stacks = {1, 4, 8}, filters = {5, 10, 15};
        for (int i = 0; i < names.length; i++) {
            FLUID_AMOUNTS[i] = builder.comment(chinese[i] + "流体接口/通用接口每批抽取上限（mB）。", "Fluid and universal interface batch limit in mB.")
                    .translation("config.conveyor_belt_plus.fluid_chute." + names[i] + ".millibuckets")
                    .defineInRange("fluid_chute." + names[i] + ".millibuckets", new int[]{1000, 16000, 64000}[i], 1, 1_048_576);
            FLUID_FILTER_LIMITS[i] = builder.comment(chinese[i] + "流体类型与流体标签规则数量；超出部分保留但不生效。", "Fluid type/tag rule limit; excess saved rules remain inactive.")
                    .translation("config.conveyor_belt_plus.fluid_chute." + names[i] + ".filters")
                    .defineInRange("fluid_chute." + names[i] + ".filters", filters[i], 1, MAX_FILTER_RULES);
            String speedKey = "belt." + names[i] + ".speed";
            String stacksKey = "chute." + names[i] + ".stacks";
            String filtersKey = "chute." + names[i] + ".filters";
            SPEEDS[i] = builder.comment(chinese[i] + "传送带速度（格/秒），范围 0.1～64。",
                            names[i] + " belt speed in blocks/second, 0.1..64.")
                    .translation("config.conveyor_belt_plus." + speedKey).defineInRange(speedKey, (double) speed[i], .1, 64);
            STACKS[i] = builder.comment(chinese[i] + "接口每批最多输出的同种物品组数，按物品自身最大堆叠数计组。",
                            names[i] + " chute maximum batch size in stacks of the same item; uses the item's stack limit.")
                    .translation("config.conveyor_belt_plus." + stacksKey).defineInRange(stacksKey, stacks[i], 1, 64);
            FILTER_LIMITS[i] = builder.comment(chinese[i] + "接口过滤规则上限，物品、标签、组件规则共用；降低上限不会删除已有规则。",
                            names[i] + " chute shared item/tag/component rule limit; excess saved rules remain inactive.")
                    .translation("config.conveyor_belt_plus." + filtersKey).defineInRange(filtersKey, filters[i], 1, MAX_FILTER_RULES);
        }
        SPLITTER_BUFFER = builder.comment("分流器单种物品缓存上限（个），默认 512；降低容量不会删除已有物品。",
                        "Splitter buffer capacity in individual items, default 512; reducing it never deletes stored items.")
                .translation("config.conveyor_belt_plus.splitter.buffer_items").defineInRange("splitter.buffer_items", 512, 1, MAX_SPLITTER_BUFFER);
        CHUTE_EXTRACTION_SIDES = builder.comment("启用接口抽取方向设置（默认开启），允许多选所贴容器的六个面；仍遵守容器该面的抽取限制。",
                        "Enable chute extraction-side selection (default true); selected faces still obey the container's sided extraction rules.",
                        "关闭时隐藏设置面板并恢复只从接口贴着的面抽取；保存的选择不会删除，不影响向容器输入的方向。",
                        "When disabled, hide the panel and use only the attached face; preserve saved selections and leave insertion unchanged.")
                .translation("config.conveyor_belt_plus.chute.extraction_sides_enabled")
                .define("chute.extraction_sides_enabled", true);
        SPEC = builder.build();
    }

    private ConveyorConfig() {}
    private static <T> T value(ModConfigSpec.ConfigValue<T> entry) {
        // Tooltips may render at the title screen, before a SERVER config is available.
        return SPEC.isLoaded() ? entry.get() : entry.getDefault();
    }
    public static float beltSpeed(int tier) {
        var entry = SPEEDS[Math.clamp(tier, 1, 3) - 1];
        double speed = value(entry);
        // NeoForge's numeric range correction leaves TOML nan unchanged; never feed it to transport math.
        return Double.isFinite(speed) ? (float) speed : entry.getDefault().floatValue();
    }
    public static int chuteStacks(int tier) { return value(STACKS[Math.clamp(tier, 1, 3) - 1]); }
    public static int chuteFilters(int tier) { return value(FILTER_LIMITS[Math.clamp(tier, 1, 3) - 1]); }
    public static int fluidAmount(int tier) { return value(FLUID_AMOUNTS[Math.clamp(tier, 1, 3) - 1]); }
    public static int fluidFilters(int tier) { return value(FLUID_FILTER_LIMITS[Math.clamp(tier, 1, 3) - 1]); }
    public static int splitterBufferItems() { return value(SPLITTER_BUFFER); }
    public static boolean extractionSidesEnabled() { return value(CHUTE_EXTRACTION_SIDES); }
}
