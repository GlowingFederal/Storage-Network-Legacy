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

2026-10-13 14:45 — Fix full-network shift-click crashes

- Fix terminal shift-clicks crashing when storage is full, disconnected, or rejects the item in Minecraft 1.6.4, 1.7.10, and 1.8.9. Failed inserts leave the source stack untouched; partial inserts retain the remainder, and successful inserts keep their normal behavior.

2026-10-17 02:31 — Rebuild legacy terminal layout and browser state

- Replace the conflicting full-screen inventory artwork with distinct vanilla-style storage, crafting, and player-inventory panels across 1.6.4, 1.7.10, and 1.8.9. Use shared named layout regions for permanent slot positions, rendering, browser hit detection, and controls. Draw empty crafting and result slot recesses explicitly.
- Replace item pages and navigation buttons with a bounded virtual browser, row-based mouse-wheel scrolling over the browser, and a draggable vertical scrollbar. Preserve offsets during catalog updates, clamp after removals, and reset when search text changes. Retain one focused search field between screens during the current game session, with right-click clearing and Tab/Enter focus release.
- Keep client crafting contents independent of tile-description NBT and synchronize them through native container slots. Refresh results when another viewer changes the saved server matrix; batch Clear grid recipe updates and preserve transfer remainders through the existing inventory/drop fallback.
- Establish native inventory item lighting, white color, and depth state around virtual item rendering; restore render state afterward and use untinted recessed item backgrounds. Fit aggregate quantity labels within their item cells and include exact counts in tooltips.
- Disconnect ordinary Storage Link Cables from activation and both GUI handlers, and remove their access control/action. Preserve saved link settings and network functionality; import/export/processing configuration remains available.
- Add deterministic per-target terminal regression checks to the existing build verification lifecycle, covering large catalogs, fixed real slots, client matrix isolation, viewport limits, native snapshot counts, and full/partial transfer remainders.

2026-10-17 07:44 — Promote the maintained legacy multiversion project

- Move the maintained 1.6.4, 1.7.10, and 1.8.9 builds, existing common code/resources, build logic, regression checks, and portable IDE tasks to the repository root without changing their implementations.
- Remove the obsolete 1.12.2 source/resources, ForgeGradle 2.3 build and wrapper, dependencies, release/setup scripts, update manifest, sample configuration, and fork landing page. Preserve upstream MIT notices and attribution.
- Replace the README with legacy-specific installation, support, build, and layout instructions; retain detailed controls, configuration, compatibility, and plugin API documentation in docs/usage.md.
- Add aggregate and per-target verification tasks that include the existing native regression checks, wire root build/check to the maintained targets, and update CI and ignore rules for the multiversion layout. Preserve pinned tooling, production names, and coordinated version allocation.


2026-10-18 13:41 � Synchronize crafting results and add optional NEI transfer

- Send the server-computed crafting output through native result-slot packets on 1.6.4, 1.7.10, and 1.8.9. Vanilla player listeners exclude SlotCrafting from incremental updates, so server-only recipe calculation previously left the client preview empty. Preserve native crafting, remaining items, shared table matrices, and the existing virtual browser/layout.
- Register optional client-side NEI crafting overlays with the actual nine input coordinates, including GTNH NEI on 1.7.10. Delegate ingredient selection and filling to NEI's native transfer handler after returning old inputs to physical player inventory through ordinary server-validated window clicks; do not extract remote catalog ingredients. Reject noncrafting screens, off-grid recipes, occupied cursors, and recipes that could consume the bound remote. Stop safely when the player inventory cannot accept old inputs.
- Keep NEI absent-server support and avoid compile/runtime dependencies. Disable GTNH NEI's separate automatic craft loop for these server-controlled crafting containers; normal recipe filling and manual/Shift-click crafting remain available.
- Extend the existing native terminal regressions with actual result-packet capture/replay, shaped/shapeless preview invalidation and consumption, and all nine NEI destination mappings.


2026-10-18 14:23 � Correct crafting ownership, shift crafting, and slot synchronization

- Give every Request Table and crafting remote container its own transient native crafting matrix. Closing returns inputs to player inventory, merges existing stacks before empty slots, drops exact overflow server-side, and clears the grid and output. Retain old saved table inputs for one-time recovery by the first server-side opener; unopened tables still drop those saved items when broken.
- Repeat shift crafting through the native result-slot lifecycle until the selected output changes, inputs run out, or player inventory space cannot accept another complete output. Remove the one-output-stack cap and automatic ingredient extraction from remote storage; preserve native crafting hooks and version-specific remaining items.
- Send changed real container slots through native slot packets even during vanilla's predicted-click listener suppression. Batch crafting mutations and publish the completed input, result, remainder, and player-inventory state together, independently of catalog refresh timing. Preserve the visible authoritative result and optional NEI filling integration.
- Extend the existing native regressions with full crafting lifecycles, multi-output and inventory-capacity cases, bucket remainders, close/drop/reopen accounting, isolated simultaneous viewers, one-time saved-input recovery, and ingredient packet replay under native listener suppression.


2026-10-18 22:40 — Restore native crafting click transactions

- Remove the manual all-slot packet broadcaster from the 1.6.4, 1.7.10, and 1.8.9 crafting containers. Restore native listener snapshots and accepted-click suppression so delayed slot echoes cannot overwrite later client predictions or disrupt physical-inventory recipe filling.
- Predict recipe previews, native ingredient/remainder consumption, and the bounded repeated Shift-click craft on both sides, with independent server recipe resolution and click validation. Preserve per-container transient matrices, close/overflow returns, legacy saved-input recovery, physical-inventory-only NEI filling, and the disabled GTNH automatic craft loop. No custom crafting slot packets remain.
- Extend the existing native regression suite with consecutive pickup/place/split/return operations and NEI-style filling clicks through the actual server click packet handler, delayed responses, per-operation slot and cursor comparisons, normal/repeated crafting, and close/reopen accounting. Replace server-only packet replay assumptions with native listener and client preview checks.


2026-10-19 16:54 — Preserve request containers across NEI recipe screens

- Keep client crafting grids, result previews, cursors, and click handling alive when NEI temporarily replaces the request screen with a recipe page and later reuses that screen for Shift-click + recipe transfer. Client screen-close callbacks no longer permanently retire the container; genuine server window closure still returns ingredients once and drops overflow. Apply the lifecycle correction to 1.6.4, 1.7.10, and 1.8.9.
- Preserve optional physical-inventory-only recipe filling, repeated manual Shift-click crafting, and the disabled automatic craft loop. Keep NEI's held-item deletion protocol and permission handling unchanged.
- Extend the existing native regression suite with repeated recipe-screen navigation, retained inputs/results/cursors, deletion followed by validated clicks, and optional execution of real NEI server inventory mutation methods against supplied development JARs.
