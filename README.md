# Conveyor Belt Plus — Minecraft 1.20.1 Forge

基于上游 3.6.0 / `1959ac4233cea0a65ae2e6f1f13f898fb72e39b2` 的 Forge 移植分支。

## 安装

- Minecraft **1.20.1**，Forge **47.4.10 或更新的 47.x**，Java **17**。
- 将 `conveyor_belt_plus-forge-1.20.1-3.6.0.jar` 放入客户端和服务端的 `mods` 目录。
- 模组 ID 仍为 `conveyor_belt_plus`，不需要 Fabric、NeoForge 或 Architectury API。
- 可选联动版本：JEI **15.20.0.106**、Jade **11.13.3+forge**、RTS Building **1.1.8-beta（Forge 1.20.1）**。这些模组不包含在发布 JAR 中。
- 本分支面向 1.20.1 世界；不能直接载入或降级 1.21.1 世界。请使用对应 Minecraft 版本的模组文件。

## 功能

- 三档曲线传送带、平滑物品运动、随运输停止的带面动画、放置预览。
- 三档滑槽，默认过滤上限 5/10/15，默认每批上限 1/4/8 组。
- 默认 512 个物品的分流器缓存；均分批次、跳过堵塞输出、完整保留超大批次数量。
- 物品、物品标签、精确 NBT 过滤；支持黑白名单、不消耗物品的标记和 JEI 拖放。
- 多面抽取选择、轮询、公平批次及 Forge 容器分面权限检查。
- 五种红石模式：始终、无信号、有信号、禁用、上升沿脉冲。
- 潜行右键原地升级滑槽或传送带，保留线路、过滤、红石状态及途中物品。
- 空主手右键拿取带上物品，服务端验证距离、遮挡及领地交互事件，背包溢出留在带上。
- 自动从副手及背包选择滑槽；RTS 跨仓储堆叠的布置草稿和远程拾取；Jade 缓存、过滤、红石和带上物品显示。

## 1.20.1 数据差异与配置

1.20.1 使用物品 NBT，精确过滤编辑框输入整个物品 `tag` 的 SNBT，例如 `{Damage:7}`、`{Potion:"minecraft:healing"}`。NBT 规则按原始内容精确比较；物品数量不参与匹配。传送带布置草稿存放在物品 NBT 的 `conveyor_belt_plus` 子项中。

使用原生 Forge `SERVER` 配置：`<世界>/serverconfig/conveyor_belt_plus.toml`。新世界默认值可放入 `defaultconfigs/conveyor_belt_plus.toml`。1.21.1 版全局配置可手动复制到对应的 1.20.1 世界配置位置，已有键名和数值仍适用。

游戏内进入单人世界后，在 Mods → Conveyor Belt Plus → Config 编辑；远程服务器由服主编辑文件。速度范围 0.1–64 格/秒，批次范围 1–64 组，过滤上限 1–54，缓存上限 1–1048576。降低规则或缓存上限不会删除已有数据。更改规则容量后重新打开滑槽界面；文件更新后的远程客户端显示可通过重新连接刷新。

## 构建与测试

源码位于 `forge/src`。Architectury Loom / Yarn 只用于构建和映射。Gradle 会选用 Java 17 编译并运行测试；支持在较新的 JDK 中启动 Gradle。

```powershell
.\gradlew.bat build --no-daemon --max-workers=2 --console plain
.\gradlew.bat :forge:runGameTest -PwithRts=true -PwithJade=true -PwithJei=true --no-daemon --max-workers=2 --console plain
.\gradlew.bat :forge:runClient -PsmokeRun=true
.\gradlew.bat :forge:runClient -PsmokeRun=true -PwithRts=true -PwithJade=true -PwithJei=true
.\gradlew.bat :forge:runClient -PclientSmokeTests=true --no-daemon --max-workers=2 --console plain
.\gradlew.bat sourceZip --no-daemon --max-workers=2 --console plain
```

Linux/macOS 使用 `./gradlew`。新环境需要联网下载依赖。

`build` 包含 12,797 项算法回归、资源完整性检查和 17 项真正的 Forge 服务端 GameTest（其中包含 57 项过滤/数据包检查）。同时开启 RTS、Jade 和 JEI 后运行 20 项 GameTest。基础测试和联动测试分别使用 `forge/run-gametest-forge-1.20.1`、`forge/run-gametest-integrations-forge-1.20.1`。构建任务要求出现成功完成的 GameTest 记录，避免 Forge 启动失败但返回 0 时误报成功。

`smokeRun` 在独立目录手动启动客户端。`clientSmokeTests` 则复制对应的 GameTest 世界并自动验证三档传送带渲染、滑槽界面、过滤/模式数据包往返和配置保存，截图保存在 `forge/run-smoke-conveyor-belt-plus/screenshots`；需先运行相同联动参数的 GameTest。测试代码和测试结构不进入发布 JAR。

RTS 1.1.8-beta 部分 Mixin 注解及 Jade 11.13.3 的一个字段别名使用固定的 SRG 名称，Loom 的 Yarn 开发环境需要额外映射。`gradle/integration-development.gradle` 仅在开启 `withRts` / `withJade` 的开发启动任务中生成临时适配副本，不修改依赖缓存，也不将联动模组或该适配打入发布 JAR。

JAR 位于 `forge/build/libs`，完整可构建源码 ZIP 位于 `build/distributions`。`-sources.jar` 是开发用源码附件，不能放入 `mods`。

## English

This branch ports upstream Conveyor Belt Plus 3.6.0 (commit `1959ac4`) to **Minecraft 1.20.1 / Forge 47.4.10 / Java 17**. Install the release JAR on both client and server. JEI 15.20.0.106, Jade 11.13.3+forge and RTS Building 1.1.8-beta for Forge are optional. No Architectury API runtime dependency is required.

All logistics features are retained, with exact item filters and belt drafts adapted to 1.20.1 NBT. The SNBT editor edits the item's complete `tag`, for example `{Damage:7}` or `{Potion:"minecraft:healing"}`. This is a mod port, not a 1.21.1 world downgrade tool.

Native Forge SERVER settings live in `<world>/serverconfig/conveyor_belt_plus.toml`; `defaultconfigs` provides defaults for new worlds. The built-in Config screen can edit settings while playing an integrated local world. Server owners edit remote server settings. Reopen chute menus after changing rule limits; reconnect remote clients to refresh file-based configuration changes.

Use the commands above to build, run isolated GameTests, launch the client, or create the complete source ZIP. The `build` task requires successful algorithm, resource, and in-game tests. Optional integration tests use the actual RTS/Jade libraries. Test classes and structures are excluded from release JARs.
