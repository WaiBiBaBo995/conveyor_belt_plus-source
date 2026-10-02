# Changelog （English）

## 3.7.1 — Support-aware belt validation and performance improvements (2026-10-02)

- Reset `belt.minimum_angle` and `belt.minimum_turn_radius` at each explicitly selected conveyor support, so every endpoint-to-support section is validated independently instead of combining both sides of a support.
- Keep the client projection and server placement checks consistent with the support-aware validation, while retaining basic path geometry and endpoint direction checks.
- Optimize spline validation by caching segment boundaries and total path length, avoiding repeated list-front removal, and skipping full curvature sampling when the turn-radius limit is disabled.
- Add regression coverage for supported multi-section slopes and document the new configuration behavior.

## 3.7.0 — Hand insertion and construction rollback (2026-09-30)

- Keep the held belt and restore reserved interface items to their original slots when construction fails, including sharp turns and full inventories; do not spawn construction refunds as drops.
- Insert a held stack at the aimed belt position, including empty belts and splitter outputs. Preserve item data and fluid_packet contents, packet order, spacing and route capacity.
- Support RTS held-item and selected linked-storage insertion using authoritative server inventory, with camera, range, claim and interaction-permission checks.

## 3.7.0 — Fluid interfaces, Forge parity and rendering update (2026-09-29)

- Use Jade's native fluid icons for interface fluid whitelist/blacklist rules, belt parcels and splitter contents, preserving fluid tint and custom data without requiring a bucket item.
- Render recovered `fluid_packet` items with their saved fluid inside vanilla glass in dropped, inventory, hand and frame views, sharing the belt fluid renderer.
- Set both loader builds to the requested 3.7.0 version, retaining the fixes previously documented under the 3.7.1 development label.
- Isolate Forge GameTest configuration mutations from its autosaving file to prevent file-watcher races; restore the loaded world configuration after each batch.
- Draw fluid filter markers using the fluid sprite and tint, including universal-interface fluid tabs; show fluid names instead of bucket names.
- Show the edit button only for exact component/NBT rules. Item-type and tag rules have no edit button.
- Port fluid/universal interfaces, shared transport, container pickup, independent profiles and JEI/Jade/RTS support to Forge 1.20.1 using native NBT and sided fluid capabilities.
- Add Forge fluid configuration pages and preserve old chute IDs through missing-mapping migration.

- Rename chute block/item IDs to `item_chute`, `advanced_item_chute`, and `ultimate_item_chute`, retaining aliases for existing worlds.
- Add three fluid-interface tiers (1,000 / 16,000 / 64,000 mB, configurable) and three universal-interface tiers.
- Share belts and splitters across item and fluid transport; conserve fluid components and amounts through partial insertion, pickup, splitting, save/load and route teardown.
- Add separate item/fluid tabs, type/tag ghost rules, fluid JEI dragging, independent whitelist, redstone and extraction-side settings.
- Add bucket/mod-tank pickup, fluid Jade display and RTS selected-storage-container pickup with existing reach/claim checks.
- Expand GameTests for fluid capabilities, persistence, conservation, immutable components, tab security and optional RTS/Jade integration.

# 更新日志 (中文)

## 3.7.1 — 支架感知的传送带校验与性能优化（2026-10-02）

- 在每个手动选择的传送带支架处重新计算 `belt.minimum_angle` 和 `belt.minimum_turn_radius`，使端点到支架之间的每个区段独立判断，不再合并支架两侧的路径。
- 保持客户端传送带投影与服务器实际放置判断一致，同时继续保留基础路径几何和端点方向检查。
- 优化曲线校验：缓存区段边界和路径总长度，避免反复删除列表头部元素；关闭最小转弯半径限制时跳过完整曲率采样。
- 增加带支架多区段坡度的回归测试，并补充相关配置行为说明。

## 3.7.0 — 手持插入与建造回滚（2026-09-30）

- 当建造失败时，保留手持的传送带，并将预留的接口物品恢复到原始槽位，包括急转弯和满物品栏的情况；不要将建造退款生成为掉落物。
- 在瞄准的传送带位置插入手持堆叠，包括空传送带和分流器输出。保留物品数据和 `fluid_packet` 内容、包顺序、间距和路线容量。
- 使用权威服务器物品栏支持 RTS 手持物品和选定链接存储插入，并进行相机、范围、领地声明和交互权限检查。

## 3.7.0 — 流体接口、Forge 对等与渲染更新（2026-09-29）

- 使用 Jade 的原生流体图标来显示接口流体白名单/黑名单规则、传送带包裹和分流器内容，保留流体着色和自定义数据，无需桶物品。
- 在掉落、物品栏、手部和物品展示框视图中，将恢复的 `fluid_packet` 物品及其保存的流体渲染在原版玻璃内，共用传送带流体渲染器。
- 将两个加载器构建设置为请求的 3.7.0 版本，保留此前在 3.7.1 开发标签下记录的修复。
- 将 Forge GameTest 配置变更与其自动保存文件隔离，以防止文件监视器竞争；每批之后恢复已加载的世界配置。
- 使用流体精灵和着色绘制流体过滤器标记，包括通用接口流体标签页；显示流体名称而不是桶名称。
- 仅为精确组件/NBT 规则显示编辑按钮。物品类型和标签规则没有编辑按钮。
- 将流体/通用接口、共享运输、容器拾取、独立配置文件和 JEI/Jade/RTS 支持移植到 Forge 1.20.1，使用原生 NBT 和分面流体能力。
- 添加 Forge 流体配置页面，并通过缺失映射迁移保留旧滑槽 ID。

- 将滑槽方块/物品 ID 重命名为 `item_chute`、`advanced_item_chute` 和 `ultimate_item_chute`，为现有世界保留别名。
- 添加三个流体接口等级（1,000 / 16,000 / 64,000 mB，可配置）和三个通用接口等级。
- 在物品和流体运输之间共享传送带和分流器；在部分插入、拾取、分流、保存/加载和路线拆除过程中保持流体组件和数量。
- 添加独立的物品/流体标签页、类型/标签幽灵规则、流体 JEI 拖拽、独立白名单、红石和抽取侧设置。
- 添加桶/模组储罐拾取、流体 Jade 显示和 RTS 选定存储容器拾取，并沿用现有触及距离/领地声明检查。
- 扩展 GameTest，以覆盖流体能力、持久化、守恒、不可变组件、标签页安全和可选 RTS/Jade 集成。
