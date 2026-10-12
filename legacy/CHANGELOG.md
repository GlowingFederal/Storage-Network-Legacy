# Changelog

2026-10-09 12:41 — Add native Forge legacy ports of Simple Storage Network 2.0.1

- Add independent Minecraft 1.6.4, 1.7.10, and 1.8.9 projects using the established Unimined and GTNHGradle/Jabel tooling, coordinated build numbering, production remapping/archive checks, and portable launch tasks.
- Port all ten blocks, remote types, six upgrades, request/crafting interfaces, configurable links, import/export, processing batches, controller actions, cable topology, saved configuration, and facades using native APIs and upstream assets.
- Replace capability inventories with a small simulation-aware inventory interface and native sided adapters; deduplicate repeated links and both halves of vanilla double chests by backing identity.
- Keep GUI and remote actions server-authoritative, dispatch incoming actions on the server tick, send changed catalog entries in bounded native packet fragments, and retain rejected transfer remainders instead of losing items.
- Add target-native rendering, a legacy inventory-provider plugin API, defensive NBT defaults, and substitutions for recipe ingredients unavailable in these Minecraft versions. Optional 1.12 integrations and save migration are excluded as documented in the README.
- Preserve the original 1.12.2 implementation and upstream licensing alongside the new `legacy/` project.
- Resolve recipes against registered objects during Forge 1.6.4 initialization, include legacy resource-pack metadata, and supply valid 1.8.9 cable models. Prepare the 1.8.9 development server runtime without the merged client's duplicate tweaker attribute.

2026-10-10 03:09 — Polish public archive naming and cable presentation

- Name production archives `SimpleStorageNetworkLegacy-<minecraft>-<version>.jar` across all three legacy targets; retain an explicit `-dev` suffix for development artifacts. Use `2.0.1` as the public version and record coordinated internal build numbers separately in the manifest.
- Update archive verification and opt-in pruning for the new filenames, including removal of obsolete production archives using the former naming scheme. Select the latest production output by modification time because rebuilding a public version replaces its existing file.
- Refresh the shared cable textures with crisp steel edging, restrained contrasting variant colors, and an opaque 32×32 layout fitted to the existing native UVs. Preserve cable connections, inventory-facing collars, facade rendering, and the original variant palette; remove the 6×6 cable textures' mip-level restriction.
- Correct cable lighting initialization after the native block constructor caches a full cube before the cable kind is assigned. Cables now use nonopaque lighting and native neighbor brightness across 1.6.4, 1.7.10, and 1.8.9.

2026-10-10 03:18 — Fix 1.8.9 cable model texture bindings

- Replace direct face texture paths with declared texture aliases in all cable centers, connection arms, inventory-facing collars, and the empty facade model. Minecraft 1.8.9 resolves face textures through aliases, so the former paths produced the missing-texture checkerboard despite correctly packaged PNGs.
- Extend production archive validation to check explicit 1.8.9 block-model face bindings and their packaged texture assets. Preserve the shared artwork, geometry, blockstates, and older targets' renderer paths.

2026-10-10 03:25 — Remove overlapping legacy cable junction faces

- Trim 1.6.4 and 1.7.10 connection arms to meet the center cube instead of including it; trim inventory-facing stems to meet their collars. This removes coplanar overlapping exterior faces that cause depth fighting at corners and branches.
- Preserve the combined outer shape, connection rules, UV mapping, native lighting, inventory rendering, and facade path. The 1.8.9 model path is unchanged.
