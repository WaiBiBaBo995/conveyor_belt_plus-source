# Conveyor Belt Plus （English）

Conveyor Belt Plus 3.7.0 provides Minecraft 1.21.1 / NeoForge / Java 21, based on SimpleBelts. The independent Minecraft 1.20.1 / Forge / Java 17 port is maintained in the sibling `1.20.1forge` project. Mod ID: `conveyor_belt_plus`. Java package: `pureneko.conveyor_belt_plus`. Main class: `pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus`.

JEI, Jade and RTS Building are optional. Replace the previous jar rather than installing two versions together, and back up existing worlds before upgrading.


## Features

- Three item, fluid and universal interface tiers, with shared belts and splitters. Item IDs are now `item_chute`, `advanced_item_chute`, and `ultimate_item_chute`; old IDs migrate through registry aliases.
- Fluid interfaces extract up to 1,000 / 16,000 / 64,000 mB per batch (configurable) using sided NeoForge fluid capabilities. Glass fluid parcels render the actual fluid sprite/tint, preserve components, and wait or partially unload when a destination tank fills.
- Universal interfaces alternate items and fluids with independently saved filters, blacklist/whitelist, redstone and extraction sides in right-side UI tabs.
- Mark fluid types/tags with filled buckets or tanks, or drag fluid ingredients from JEI. Fill held buckets/mod tanks directly from aimed belt parcels; Jade shows fluid names and mB. RTS supports held containers and selected containers from linked storage.
- Jade shows native fluid sprites for fluid filter rules, belt parcels and splitter contents. Recovered `fluid_packet` items render the contained fluid inside vanilla glass when dropped, held or viewed in inventory.
- Curved conveyors with three speed tiers, matching orange/blue/purple materials and smooth item motion.
- Per-route animation that stops when transport stops; conveyor-shaped placement previews.
- Automatic chute installation using the offhand first, then inventory order, with shortage and sharp-turn checks.
- A four-sided splitter with a configurable 512-item buffer that divides batches evenly between available outputs, automatically skipping blocked entrances; oversized incoming batches enter in bounded parts.
- Three item-interface tiers, with default rule limits of 5/10/15 and batch limits of 1/4/8 stacks.
- Optional per-chute extraction-side multi-selection in a gear/side-panel UI: query selected faces of the same attached container in round-robin order while obeying sided permissions, filters, redstone and one-batch limits. Defaults to the attached face; insertion is unchanged. Settings persist through saves and upgrades.
- Five per-chute redstone modes: always, no signal, with signal, never, or rising-edge pulse. State survives save/load and upgrades.
- Non-consuming item/JEI markers, exact editable component rules, item tags and blacklist/whitelist modes.
- Sneak + right-click in-place chute upgrades preserve rules, mode, facing, connections and in-flight packets.
- Sneak + right-click in-place belt upgrades preserve route geometry and packet progress; select an individual splitter output face.
- Survival upgrades consume one higher-tier item and return the previous item. Creative upgrades do neither.
- Empty-main-hand right-click pickup of the aimed-at rendered batch, with server-side reach/visibility checks and overflow retained on the belt.
- Right-click any free belt surface with a held stack to insert it at that position, including empty belts and splitter outputs. Recovered fluid parcels reenter fluid transport with their saved contents. Occupied packet spacing refuses insertion without consuming items.
- Failed belt construction restores reserved interfaces to their original inventory/offhand slots and retains the held belt and retry draft, including with a full inventory. RTS supports insertion from held stacks and selected linked-storage items, as well as construction rollback.
- Player-oriented tooltips: purpose, current statistics, basic controls, and Ctrl for extra help.

- Optional RTS Building 1.1.7 integration: cursor-based belt preview, a per-builder endpoint draft across storage extractions, and remote empty-hand rendered-item pickup with RTS range/claim/capability checks.
- Optional Jade 15.10.6 integration: splitter buffer contents/count, chute filter icons, a text-only chute redstone mode line, and the aimed-at belt packet/count, using existing client synchronization without a new polling channel. Chute redstone mode has its own Jade display toggle.

## Configuration

All settings now use a native NeoForge `ModConfigSpec`, registered as `SERVER` config with the existing filename `conveyor_belt_plus.toml`. NeoForge handles loading, validation, saving, file watching and initial server-to-client synchronization. There is no parallel runtime TOML parser or custom settings cache.

The global file remains `config/conveyor_belt_plus.toml`. NeoForge also supports per-world overrides with the same filename in a world's `serverconfig` directory. Existing TOML keys/values are reused; NeoForge may refresh comments, add defaults or correct values and make its standard backups. The supplied 3.6.0 source no longer includes the legacy `.properties` importer: users of that old format must transfer values to TOML manually (existing TOML files need no migration).

The built-in configuration screen is registered: Mods → Conveyor Belt Plus → Config. SERVER options can be edited while playing a local world, not when connected to someone else's server. File/GUI updates use the native lifecycle. Reopen any chute menu whose rule capacity changed; reconnect remote clients if necessary to refresh displayed settings.

`splitter.buffer_items` still defaults to 512 individual items (range 1..1048576); reducing capacity never deletes existing contents.

Limits remain: belt speeds 0.1..64 blocks/s, chute batches 1..64 stacks, total filter rules 1..54. Rules beyond a reduced capacity remain saved but inactive.

`fluid_chute.<standard|advanced|ultimate>.millibuckets` sets the fluid batch size (1..1048576 mB); `.filters` sets each fluid rule limit (1..54). Universal interfaces use the item and fluid settings for the respective tab.

`chute.extraction_sides_enabled` defaults to `true`. Disable it to hide the side-settings UI and restore attached-face-only extraction, without deleting per-chute selections. The server rejects side edits while disabled; open menus synchronize the switch and mask through native menu properties.

## Development

The 1.21.1 source and resources are under `neoforge/src`; the independent 1.20.1 build lives in the sibling `1.20.1forge/forge/src` project.
Build and run the automated regression and NeoForge GameTest suites:

```powershell
.\gradlew.bat build --no-daemon --max-workers=1 --console plain
```

Launch only NeoForge:

```powershell
.\gradlew.bat :neoforge:runClient
```

Include optional JEI in the development client:

```powershell
.\gradlew.bat :neoforge:runClient -PwithJei=true
```

Include the optional integrations (RTS 1.1.7 itself requires NeoForge 21.1.221 or newer):

```powershell
.\gradlew.bat :neoforge:runClient '-PwithRts=true' '-PwithJade=true' '-PwithJei=true' '-Pneoforge_version=21.1.238'
```

The same properties on `:neoforge:runGameTest` additionally enable the real-RTS integration tests in `neoforge/src/compatTest/java` and the Jade text/snapshot tests in `neoforge/src/jadeTest/java`. Quote dotted property values in PowerShell.

Add `-PsmokeRun=true` for a separate startup-test directory. Tests use `neoforge/build/run-gametest-conveyor-belt-plus` and never open the normal development world's save files. GameTest classes/resources are not included in the release jar.

Create a complete source distribution, including the Gradle wrapper, resources, tests and documentation:

```powershell
.\gradlew.bat sourceZip --no-daemon --max-workers=1 --console plain
```

This runs all checks and writes `build/distributions/conveyor_belt_plus-3.7.0-source.zip`.
On Linux/macOS use `./gradlew` in place of `.\gradlew.bat`. Extract the archive, enter its root
directory and run the same build command. Java 21 and internet access to download Gradle and
dependencies are required on a new machine. Build caches, local worlds, IDE settings, reference
images, previous archives and migration backups are excluded. `clean` removes generated build
output, including source distributions.

Earlier notes describe those releases; current loader requirements and config format are those above.

# Conveyor Belt Plus （中文）

Conveyor Belt Plus 3.7.0 提供 Minecraft 1.21.1 / NeoForge / Java 21 支持，基于 SimpleBelts。独立的 Minecraft 1.20.1 / Forge / Java 17 移植版维护在同级 `1.20.1forge` 项目中。模组 ID：`conveyor_belt_plus`。Java 包：`pureneko.conveyor_belt_plus`。主类：`pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus`。

JEI、Jade 和 RTS Building 是可选的。请替换之前的 jar，而不是同时安装两个版本，并在升级前备份现有世界。

## 特性

- 三个物品、流体和通用接口等级，共享传送带和分流器。物品 ID 现在为 `item_chute`、`advanced_item_chute` 和 `ultimate_item_chute`；旧 ID 通过注册表别名迁移。
- 流体接口使用分面 NeoForge 流体能力，每批最多抽取 1,000 / 16,000 / 64,000 mB（可配置）。玻璃流体包裹渲染实际流体精灵/着色，保留组件，并在目标储罐满时等待或部分卸载。
- 通用接口在右侧 UI 标签页中交替处理物品和流体，具有独立保存的过滤器、黑名单/白名单、红石和抽取侧设置。
- 用装满的桶或储罐标记流体类型/标签，或从 JEI 拖拽流体原料。直接从瞄准的传送带包裹填充手持桶/模组储罐；Jade 显示流体名称和 mB。RTS 支持手持容器和来自链接存储的选定容器。
- Jade 为流体过滤规则、传送带包裹和分流器内容显示原生流体精灵。恢复的 `fluid_packet` 物品在掉落、手持或物品栏中查看时，在原生玻璃内渲染所含流体。
- 弯曲传送带，具有三个速度等级，匹配橙色/蓝色/紫色材质和流畅的物品运动。
- 每条路线独立动画，运输停止时动画停止；传送带形状的放置预览。
- 自动安装滑槽，优先使用副手，然后按物品栏顺序，并检查短缺和急转弯。
- 四面分流器，具有可配置的 512 物品缓冲，将批次平均分配到可用输出，自动跳过被阻塞的入口；超大传入批次以有界部分进入。
- 三个物品接口等级，默认规则限制为 5/10/15，批次限制为 1/4/8 堆叠。
- 可选的每个滑槽抽取侧多选，位于齿轮/侧面板 UI 中：以轮询顺序查询同一连接容器的选定面，同时遵守分面权限、过滤器、红石和一批限制。默认为连接面；插入不变。设置通过存档和升级持久化。
- 五种每个滑槽的红石模式：始终、无信号、有信号、从不或上升沿脉冲。状态在保存/加载和升级后保留。
- 非消耗性物品/JEI 标记、精确可编辑组件规则、物品标签和黑名单/白名单模式。
- 潜行 + 右键原地滑槽升级，保留规则、模式、朝向、连接和运输中的包裹。
- 潜行 + 右键原地传送带升级，保留路线几何和包裹进度；选择单个分流器输出面。
- 生存升级消耗一个更高等级物品并返回之前的物品。创造升级两者都不消耗。
- 空主手右键拾取瞄准的已渲染批次，带服务器端触及距离/可见性检查，溢出保留在传送带上。
- 手持堆叠右键任意空闲传送带表面，将其插入该位置，包括空传送带和分流器输出。恢复的流体包裹带着保存的内容重新进入流体运输。被占用的包裹间距会拒绝插入而不消耗物品。
- 失败的传送带建造将预留接口恢复到其原始物品栏/副手槽位，并保留手持传送带和重试草稿，包括在满物品栏时。RTS 支持从手持堆叠和选定链接存储物品插入，以及建造回滚。
- 面向玩家的提示：用途、当前统计、基本控制，按 Ctrl 获取额外帮助。

- 可选 RTS Building 1.1.7 集成：基于光标的传送带预览、跨存储抽取的每个建造者端点草稿，以及远程空手渲染物品拾取，带 RTS 范围/领地/能力检查。
- 可选 Jade 15.10.6 集成：分流器缓冲内容/数量、滑槽过滤图标、仅文本的滑槽红石模式行，以及瞄准的传送带包裹/数量，使用现有客户端同步而不新增轮询通道。滑槽红石模式有自己的 Jade 显示开关。

## 配置

所有设置现在使用原生 NeoForge `ModConfigSpec`，注册为 `SERVER` 配置，使用现有文件名 `conveyor_belt_plus.toml`。NeoForge 处理加载、验证、保存、文件监视和初始服务器到客户端同步。没有并行的运行时 TOML 解析器或自定义设置缓存。

全局文件仍为 `config/conveyor_belt_plus.toml`。NeoForge 还支持世界 `serverconfig` 目录中同名文件的每世界覆盖。现有 TOML 键/值会被复用；NeoForge 可能会刷新注释、添加默认值或修正值，并创建其标准备份。提供的 3.6.0 源码不再包含旧版 `.properties` 导入器：使用该旧格式的用户必须手动将值转移到 TOML（现有 TOML 文件无需迁移）。

内置配置屏幕已注册：模组 → Conveyor Belt Plus → 配置。SERVER 选项可以在游玩本地世界时编辑，连接到他人服务器时不能编辑。文件/GUI 更新使用原生生命周期。重新打开任何规则容量已更改的滑槽菜单；如有必要，重新连接远程客户端以刷新显示的设置。

`splitter.buffer_items` 仍默认为 512 个单个物品（范围 1..1048576）；减少容量绝不会删除现有内容。

限制保持不变：传送带速度 0.1..64 方块/秒，滑槽批次 1..64 堆叠，总过滤规则 1..54。超出减少后容量的规则仍会保存但处于非活动状态。

`fluid_chute.<standard|advanced|ultimate>.millibuckets` 设置流体批次大小（1..1048576 mB）；`.filters` 设置每个流体规则限制（1..54）。通用接口对相应标签页使用物品和流体设置。

`chute.extraction_sides_enabled` 默认为 `true`。禁用它以隐藏侧设置 UI 并恢复仅连接面抽取，而不删除每个滑槽的选择。服务器在禁用时拒绝侧编辑；打开的菜单通过原生菜单属性同步开关和掩码。

## 开发

1.21.1 源码和资源位于 `neoforge/src` 下；独立的 1.20.1 构建位于同级 `1.20.1forge/forge/src` 项目。

构建并运行自动回归和 NeoForge GameTest 套件：

```powershell
.\gradlew.bat build --no-daemon --max-workers=1 --console plain
```

仅启动 NeoForge：

```powershell
.\gradlew.bat :neoforge:runClient
```

在开发客户端中包含可选 JEI：

```powershell
.\gradlew.bat :neoforge:runClient -PwithJei=true
```

包含可选集成（RTS 1.1.7 本身需要 NeoForge 21.1.221 或更新版本）：

```powershell
.\gradlew.bat :neoforge:runClient '-PwithRts=true' '-PwithJade=true' '-PwithJei=true' '-Pneoforge_version=21.1.238'
```

`:neoforge:runGameTest` 上的相同属性还会启用 `neoforge/src/compatTest/java` 中的真实 RTS 集成测试和 `neoforge/src/jadeTest/java` 中的 Jade 文本/快照测试。在 PowerShell 中为带点的属性值加引号。

添加 `-PsmokeRun=true` 以使用单独的启动测试目录。测试使用 `neoforge/build/run-gametest-conveyor-belt-plus`，并且绝不打开正常开发世界的存档文件。GameTest 类/资源不包含在发布 jar 中。

创建完整的源码分发，包括 Gradle wrapper、资源、测试和文档：

```powershell
.\gradlew.bat sourceZip --no-daemon --max-workers=1 --console plain
```

这会运行所有检查并写入 `build/distributions/conveyor_belt_plus-3.7.0-source.zip`。
在 Linux/macOS 上使用 `./gradlew` 代替 `.\gradlew.bat`。解压归档，进入其根目录并运行相同的构建命令。在新机器上需要 Java 21 和互联网访问以下载 Gradle 和依赖。构建缓存、本地世界、IDE 设置、参考图像、之前的归档和迁移备份会被排除。`clean` 删除生成的构建输出，包括源码分发。

早期说明描述了那些版本；当前加载器要求和配置格式以上文为准。