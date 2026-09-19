# Conveyor Belt Plus

Conveyor Belt Plus 3.6.0 is a **NeoForge-only** logistics mod for Minecraft 1.21.1 / Java 21. Mod ID: `conveyor_belt_plus`. Java package: `pureneko.conveyor_belt_plus`. Main class: `pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus`.

JEI, Jade and RTS Building are optional. Replace the previous jar rather than installing two versions together, and back up existing worlds before upgrading.

## Features

- Curved conveyors with three speed tiers, matching orange/blue/purple materials and smooth item motion.
- Per-route animation that stops when transport stops; conveyor-shaped placement previews.
- Automatic chute installation using the offhand first, then inventory order, with shortage and sharp-turn checks.
- A four-sided splitter with a configurable 512-item buffer that divides batches evenly between available outputs, automatically skipping blocked entrances; oversized incoming batches enter in bounded parts.
- Three chute tiers, with default rule limits of 5/10/15 and batch limits of 1/4/8 stacks.
- Optional per-chute extraction-side multi-selection in a gear/side-panel UI: query selected faces of the same attached container in round-robin order while obeying sided permissions, filters, redstone and one-batch limits. Defaults to the attached face; insertion is unchanged. Settings persist through saves and upgrades.
- Five per-chute redstone modes: always, no signal, with signal, never, or rising-edge pulse. State survives save/load and upgrades.
- Non-consuming item/JEI markers, exact editable component rules, item tags and blacklist/whitelist modes.
- Sneak + right-click in-place chute upgrades preserve rules, mode, facing, connections and in-flight packets.
- Sneak + right-click in-place belt upgrades preserve route geometry and packet progress; select an individual splitter output face.
- Survival upgrades consume one higher-tier item and return the previous item. Creative upgrades do neither.
- Empty-main-hand right-click pickup of the aimed-at rendered batch, with server-side reach/visibility checks and overflow retained on the belt.
- Player-oriented tooltips: purpose, current statistics, basic controls, and Ctrl for extra help.

- Optional RTS Building 1.1.7 integration: cursor-based belt preview, a per-builder endpoint draft across storage extractions, and remote empty-hand rendered-item pickup with RTS range/claim/capability checks.
- Optional Jade 15.10.6 integration: splitter buffer contents/count, chute filter icons, a text-only chute redstone mode line, and the aimed-at belt packet/count, using existing client synchronization without a new polling channel. Chute redstone mode has its own Jade display toggle.

## Configuration

All settings now use a native NeoForge `ModConfigSpec`, registered as `SERVER` config with the existing filename `conveyor_belt_plus.toml`. NeoForge handles loading, validation, saving, file watching and initial server-to-client synchronization. There is no parallel runtime TOML parser or custom settings cache.

The global file remains `config/conveyor_belt_plus.toml`. NeoForge also supports per-world overrides with the same filename in a world's `serverconfig` directory. Existing TOML keys/values are reused; NeoForge may refresh comments, add defaults or correct values and make its standard backups. The supplied 3.6.0 source no longer includes the legacy `.properties` importer: users of that old format must transfer values to TOML manually (existing TOML files need no migration).

The built-in configuration screen is registered: Mods → Conveyor Belt Plus → Config. SERVER options can be edited while playing a local world, not when connected to someone else's server. File/GUI updates use the native lifecycle. Reopen any chute menu whose rule capacity changed; reconnect remote clients if necessary to refresh displayed settings.

`splitter.buffer_items` still defaults to 512 individual items (range 1..1048576); reducing capacity never deletes existing contents.

Limits remain: belt speeds 0.1..64 blocks/s, chute batches 1..64 stacks, total filter rules 1..54. Rules beyond a reduced capacity remain saved but inactive.

`chute.extraction_sides_enabled` defaults to `true`. Disable it to hide the side-settings UI and restore attached-face-only extraction, without deleting per-chute selections. The server rejects side edits while disabled; open menus synchronize the switch and mask through native menu properties.

## Development

All active source and resources are under `neoforge/src`; Fabric/common projects, Architectury registries/networking/menu helpers, unused access wideners and mixin metadata have been removed.

The Gradle mapping/remapping tool remains Architectury Loom with Yarn mappings. This is **build tooling only**, not an installed-mod dependency. There is no Architectury API in the development runtime or release jar.

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

Add `-PsmokeRun=true` for a separate startup-test directory. Tests use `neoforge/run-gametest-conveyor-belt-plus` and never open the normal development world's save files. GameTest classes/resources are not included in the release jar.

Create a complete source distribution, including the Gradle wrapper, resources, tests and documentation:

```powershell
.\gradlew.bat sourceZip --no-daemon --max-workers=1 --console plain
```

This runs all checks and writes `build/distributions/conveyor_belt_plus-3.6.0-source.zip`.
On Linux/macOS use `./gradlew` in place of `.\gradlew.bat`. Extract the archive, enter its root
directory and run the same build command. Java 21 and internet access to download Gradle and
dependencies are required on a new machine. Build caches, local worlds, IDE settings, reference
images, previous archives and migration backups are excluded. `clean` removes generated build
output, including source distributions.

# 中文说明

Conveyor Belt Plus 3.6.0 是一个**仅支持 NeoForge** 的 Minecraft 1.21.1 / Java 21 物流模组。 Mod ID：`conveyor_belt_plus`。Java 包：`pureneko.conveyor_belt_plus`。主类：`pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus`。

JEI、Jade 和 RTS Building 为可选。请替换之前的 jar，而不是同时安装两个版本，并在升级前备份现有世界。

## 功能

- 曲线传送带，三种速度等级，对应橙色/蓝色/紫色材料，物品运动平滑。
- 每条路线的动画在运输停止时停止；传送带形状的放置预览。
- 自动安装滑槽，优先使用副手，然后按物品栏顺序，并检查短缺和急转弯。
- 四面分流器，可配置 512 物品缓冲，在可用输出之间平均分配批次，自动跳过堵塞入口；过大的输入批次以有界部分进入。
- 三种滑槽等级，默认规则限制为 5/10/15，批次限制为 1/4/8 组。
- 可选的每滑槽抽取面多选，位于齿轮/侧面板 UI 中：以轮询顺序查询同一所附着容器的选定面，同时遵守分面权限、过滤器、红石和单批次限制。默认使用所附着面；插入不变。设置通过存档和升级持久保存。
- 五种每滑槽红石模式：始终、无信号、有信号、从不、上升沿脉冲。状态在保存/加载和升级后保留。
- 非消耗性物品/JEI 标记，精确可编辑组件规则，物品标签以及黑名单/白名单模式。
- 潜行 + 右键原地升级滑槽会保留规则、模式、朝向、连接和飞行中的包裹。
- 潜行 + 右键原地升级传送带会保留路线几何和包裹进度；可选择单个分流器输出面。
- 生存模式升级消耗一个更高等级物品并返还之前的物品。创造模式升级两者都不做。
- 主手为空时右键拾取所瞄准的已渲染批次，带服务端触及/可见性检查，溢出保留在传送带上。
- 面向玩家的工具提示：用途、当前统计、基本控制，按 Ctrl 获取额外帮助。

- 可选 RTS Building 1.1.7 集成：基于光标的传送带预览，跨存储抽取的每建造者端点草稿，以及带 RTS 范围/领地声明/能力检查的远程空手渲染物品拾取。
- 可选 Jade 15.10.6 集成：分流器缓冲内容/数量、滑槽过滤器图标、仅文本的滑槽红石模式行，以及所瞄准传送带的包裹/数量，使用现有客户端同步，无需新的轮询通道。滑槽红石模式有自己的 Jade 显示开关。

## 配置

所有设置现在使用原生 NeoForge `ModConfigSpec`，注册为 `SERVER` 配置，并沿用现有文件名 `conveyor_belt_plus.toml`。NeoForge 处理加载、验证、保存、文件监视以及初始服务器到客户端同步。没有并行的运行时 TOML 解析器或自定义设置缓存。

全局文件仍是 `config/conveyor_belt_plus.toml`。NeoForge 还支持在每个世界的 `serverconfig` 目录中使用相同文件名进行每世界覆盖。现有 TOML 键/值会被复用；NeoForge 可能会刷新注释、添加默认值或更正值，并进行其标准备份。提供的 3.6.0 源码不再包含旧版 `.properties` 导入器：使用该旧格式的用户必须手动将值转移到 TOML（现有 TOML 文件无需迁移）。

内置配置界面已注册：Mods → Conveyor Belt Plus → Config。`SERVER` 选项可在游玩本地世界时编辑，连接到他人服务器时不可编辑。文件/GUI 更新使用原生生命周期。重新打开任何规则容量已更改的滑槽菜单；如有必要，重新连接远程客户端以刷新显示的设置。

`splitter.buffer_items` 仍默认为 512 个单个物品（范围 1..1048576）；减少容量从不会删除现有内容。

限制仍为：传送带速度 0.1..64 方块/秒，滑槽批次 1..64 组，总过滤规则 1..54。超出降低后容量的规则仍会保存但处于非活动状态。

`chute.extraction_sides_enabled` 默认为 `true`。禁用它可隐藏侧面设置 UI，并恢复仅从所附着面抽取，而不会删除每滑槽选择。禁用时服务器拒绝侧面编辑；打开的菜单通过原生菜单属性同步开关和掩码。

## 开发

所有活动源码和资源都位于 `neoforge/src` 下；Fabric/common 项目、Architectury 注册表/网络/菜单辅助、未使用的 access widener 和 mixin 元数据已移除。

Gradle 映射/重映射工具仍是使用 Yarn 映射的 Architectury Loom。这**仅是构建工具**，不是已安装模组依赖。开发运行时或发布 jar 中没有 Architectury API。

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

包含可选集成（RTS 1.1.7 本身需要 NeoForge 21.1.221 或更新）：

```powershell
.\gradlew.bat :neoforge:runClient '-PwithRts=true' '-PwithJade=true' '-PwithJei=true' '-Pneoforge_version=21.1.238'
```

在 `:neoforge:runGameTest` 上使用相同属性还会额外启用 `neoforge/src/compatTest/java` 中的真实 RTS 集成测试，以及 `neoforge/src/jadeTest/java` 中的 Jade 文本/快照测试。在 PowerShell 中为带点属性值加引号。

添加 `-PsmokeRun=true` 以使用单独的启动测试目录。测试使用 `neoforge/run-gametest-conveyor-belt-plus`，且从不打开正常开发世界的存档文件。GameTest 类/资源不包含在发布 jar 中。

创建完整源码分发包，包括 Gradle wrapper、资源、测试和文档：

```powershell
.\gradlew.bat sourceZip --no-daemon --max-workers=1 --console plain
```

这会运行所有检查并写入 `build/distributions/conveyor_belt_plus-3.6.0-source.zip`。
在 Linux/macOS 上使用 `./gradlew` 代替 `.\gradlew.bat`。解压归档，进入其根目录并运行相同构建命令。在新机器上需要 Java 21 和互联网访问以下载 Gradle 和依赖。构建缓存、本地世界、IDE 设置、参考图像、以前的归档和迁移备份均被排除。`clean` 会删除生成的构建输出，包括源码分发包。
