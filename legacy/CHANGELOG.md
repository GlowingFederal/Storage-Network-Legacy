# Changelog

2026-10-09 12:41 — Add native Forge legacy ports of Simple Storage Network 2.0.1

- Add independent Minecraft 1.6.4, 1.7.10, and 1.8.9 projects using the established Unimined and GTNHGradle/Jabel tooling, coordinated build numbering, production remapping/archive checks, and portable launch tasks.
- Port all ten blocks, remote types, six upgrades, request/crafting interfaces, configurable links, import/export, processing batches, controller actions, cable topology, saved configuration, and facades using native APIs and upstream assets.
- Replace capability inventories with a small simulation-aware inventory interface and native sided adapters; deduplicate repeated links and both halves of vanilla double chests by backing identity.
- Keep GUI and remote actions server-authoritative, dispatch incoming actions on the server tick, send changed catalog entries in bounded native packet fragments, and retain rejected transfer remainders instead of losing items.
- Add target-native rendering, a legacy inventory-provider plugin API, defensive NBT defaults, and substitutions for recipe ingredients unavailable in these Minecraft versions. Optional 1.12 integrations and save migration are excluded as documented in the README.
- Preserve the original 1.12.2 implementation and upstream licensing alongside the new `legacy/` project.
- Resolve recipes against registered objects during Forge 1.6.4 initialization, include legacy resource-pack metadata, and supply valid 1.8.9 cable models. Prepare the 1.8.9 development server runtime without the merged client's duplicate tweaker attribute.
