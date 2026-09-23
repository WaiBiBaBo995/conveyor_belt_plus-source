# Changelog

## 3.6.0 — Minecraft 1.20.1 Forge port

- Based on upstream `1959ac4233cea0a65ae2e6f1f13f898fb72e39b2`.
- Target Minecraft 1.20.1, Forge 47.4.10 and Java 17; retain the `conveyor_belt_plus` mod ID.
- Adapt registries, lifecycle/client events, inventory capabilities, menus and packet channels to Forge.
- Store placement drafts and exact item filters in 1.20.1 item NBT; preserve transport batches larger than vanilla stack counts.
- Backport block/entity serialization, rendering, recipes and loot tables; retain the three belt/chute tiers, splitters, upgrades, pickup, redstone and extraction controls.
- Provide an integrated-world Forge configuration screen and fit the chute menu within a 240-pixel scaled GUI.
- Update optional integrations to JEI 15, Jade 11 and RTS Building 1.1.8-beta for Forge 1.20.1.
- Add resource/recipe/loot/NBT checks and opt-in automated client screenshots, packet round trips and configuration saving.
