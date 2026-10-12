# Simple Storage Network — legacy ports

Forge ports of Simple Storage Network Legacy **2.0.1**, based on this repository's `trunk/1.12` implementation. The original Minecraft 1.12.2 project remains at the repository root; open this `legacy/` directory as the multiversion project.

Connect chests and modded inventories to a Network Master with cables and storage links. Request terminals list, search, insert, extract, and craft using network contents. Import/export cables automate transfers; processing cables feed ingredients into machines and collect outputs. The Network Controller manages processing batches. Storage, picker, and collector remotes and all six upgrades are included.

| Minecraft | Forge | Build tooling | Build JDK | Mod bytecode / game Java |
| --- | --- | --- | --- | --- |
| 1.6.4 | 9.11.1.1345 | Unimined 1.4.1 / Gradle 8.13 | 21; compiler 17 | Java 7 / Java 8 |
| 1.7.10 | 10.13.4.1614 | GTNHGradle 2.0.34 / Jabel / Gradle 9.7.1 | 25 | Java 8 / Java 8 |
| 1.8.9 | 11.15.1.2318 | Unimined 1.4.1 / Gradle 8.13 | 21 | Java 8 / Java 8 |

Only Minecraft and Forge are required. Install the production JAR matching your Minecraft version on both client and server. Development JARs ending in `-dev.jar` are not distributables. These ports do not convert or downgrade 1.12.2 worlds; use worlds for the matching Minecraft version and back up saves before changing mods.

## Using the network

Place a master, connect cables, attach storage links to inventories, and connect a Storage Inventory or Storage Request Table. A simple link exposes its inventory without configurable filters. Configurable links support priority, whitelist/blacklist, metadata, Ore Dictionary, and NBT matching, plus read-only access. Multiple links to one inventory and links to both halves of a vanilla double chest count that inventory once. Insertion prefers higher priorities; extraction prefers lower priorities.

The request screen supports name search and prefixes `@` (mod name), `#` (tooltip), `$` (Ore Dictionary), and `%` (creative tab). Left-click requests a stack, right-click half a stack, Ctrl-click one item, and Shift-click sends the result into your inventory. Click the list with a carried stack to insert it. Shift-click inventory slots to store their contents. Crafting uses the native recipe and container-item behavior and refills depleted ingredients from the network.

Open a cable to configure it. Filter slots are ghost patterns: click with a carried stack to copy its pattern, or click with an empty cursor to clear it. Ctrl-click an existing pattern with an empty cursor increases its quantity; Ctrl-right-click decreases it. The **Import** button copies patterns from the connected inventory. Import/export upgrades have four slots, one upgrade per slot:

- Speed: reduces the default 30-tick interval to `30 / (speed upgrades + 1)`.
- Slow: increases it to `30 × (slow upgrades + 1)`; Speed overrides Slow.
- Stack: transfers up to 64 rather than four items.
- Single: transfers one item and overrides Stack.
- Stock: export only enough to reach the filter's quantity in the target inventory.
- Operation: runs only when the matching network quantity is at most, or greater than, the configured limit.

For processing, set the first nine ghost slots to ingredients and the second nine to outputs. Choose machine input/output sides, then use Always mode or a finite batch count. The controller selects a processing cable and adjusts its batches. Recipes can be configured manually; recipe-viewer transfer is not required.

Sneak-right-click a cable to cycle its inventory-facing direction. Sneak-left-click with a block to apply its facade, or with an empty hand to remove it. Bind remotes by using them on a master; **V** opens a bound storage remote in your inventory. Limited remotes use the configured range; Unlimited and Simple remotes stay in the same dimension; Dimensional remotes can cross dimensions. Remotes access loaded masters and do not force chunk loading. Picker remotes request the clicked block's dropped item; collector remotes in the player's inventory send pickups to their bound network.

## Configuration and compatibility

Forge writes `config/storagenetwork.cfg`. Applicable upstream options retain their names and defaults: `AutoRefreshTicks=200`, `StorageRemoteRange=128`, `ReloadNetworkWhenUnloadChunk=true`, `facadesUseCollisionBoundingBox=true`, `BlacklistBlocks`, and `BlacklistFacadeCableItems`. `LogSpamAllTheThings` is retained for configuration compatibility but currently has no verbose event instrumentation. Minecraft 1.6.4 also exposes configurable numeric block IDs (2800–2809) and item IDs (18000–18003); resolve pack conflicts before creating a world, and preserve those assignments afterward.

Catalog updates send changed entries in bounded packet fragments; a compressed update is limited to 8 MiB and the client displays up to 100,000 distinct stack identities.

The ports use native `IInventory`/`ISidedInventory`, with simulation, sided restrictions, loaded-only discovery, and inventory identity deduplication. Mod inventories that expose only a custom API need a legacy inventory provider. No Forge capability dependency is present. Native 1.6.4/1.7.10 cable renderers and the 1.8.9 model system share the refreshed cable textures: neutral cables, purple storage links, red imports, green exports, yellow processing cables, and cyan simple links. Facades store block identity plus metadata rather than 1.12 blockstates. Composite facade collision shapes on 1.6.4/1.7.10 currently use the facade's bounding box, so stairs and other multipart shapes may differ.

Intentional differences from the 1.12.2 release:

- The compact native GUI preserves core actions but does not reproduce the original layout, client search-retention options, or recipe-viewer search synchronization.
- Recipe substitutions for unavailable ingredients: iron nugget → iron ingot; observer → daylight detector; purpur block → obsidian; magma → netherrack. Other ingredient patterns, result variants, and output counts are retained.
- JEI recipe transfer/search synchronization, NEI integration, Baubles equipment-slot integration, FastWorkbench integration, and Patchouli are not included. Collector remotes operate from the normal inventory. These integrations do not become hard dependencies.
- Compact Machines 3's 1.12 integration/dependency is omitted; its newer dimension bridge has no native implementation in these ports.
- The upstream update checker and 1.12 chunk data fixers are omitted. The ports use their own defensive legacy NBT schema; this is not a 1.12 save migration path.
- The capability-based plugin API is replaced by the native inventory-provider API below. Existing 1.12 plugins require adaptation.
- Rejected transfer remainders are retained in a saved recovery inventory, then returned to the network; overflowing recovery drops the remainder instead of deleting it. Partial collector insertion updates the remaining ground stack.

## Building and developing

Install the build JDKs listed above and Java 8 for launches. Set `JAVA8_HOME`, `JAVA17_HOME`, `JAVA21_HOME`, and `JAVA25_HOME` if discovery cannot find them. Set `PYTHON` to a Python 3 executable if `python` is not available. Use the pinned wrappers; do not upgrade legacy Forge or replace stock runtime libraries to work around a host problem.

From this directory:

```powershell
.\gradlew.bat buildAll
.\gradlew.bat build1_6_4
.\gradlew.bat build1_7_10
.\gradlew.bat build1_8_9
.\gradlew.bat runClient1_7_10
.\gradlew.bat runServer1_7_10
.\gradlew.bat pruneBuilds
```

The corresponding `./gradlew` tasks work on systems with the required JDKs and Python. Checked-in `.run/` configurations provide portable IntelliJ tasks for all three targets. Native target wrappers are under `versions/<minecraft>/`; each target is an independent Gradle build.

Server launches may ask for online-mode selection and Minecraft EULA acceptance; answer these prompts yourself. The 1.8.9 server task removes the client tweaker attribute from its generated merged development runtime to avoid duplicate Forge bootstrap. Production JARs do not include that runtime. Cable textures are opaque 32×32 pixel art and support the normal 1.8.9 mip level 4 atlas.

`version.properties` defines the shared public version (`mod_version=2.0.1`) and a monotonically increasing internal build number. Production archives use `SimpleStorageNetworkLegacy-<minecraft>-<version>.jar` in each target's `build/libs/`: `SimpleStorageNetworkLegacy-1.6.4-2.0.1.jar`, `SimpleStorageNetworkLegacy-1.7.10-2.0.1.jar`, and `SimpleStorageNetworkLegacy-1.8.9-2.0.1.jar`. Development archives append `-dev` before `.jar`. Mod metadata and `Implementation-Version` use the public version; `Implementation-Build` in the manifest records the internal number. Rebuilding the same public version replaces its existing filename; increment `mod_version` when distributing a distinct public update.

`buildAll` uses one internal build number for all targets, validates every production archive, and advances the number only after all three succeed. Archive checks cover metadata, internal build identity, remapping symbols, bytecode, cable texture dimensions, core classes/resources, licensing, and unexpected bundled dependencies. `pruneBuilds` retains the most recently modified production JAR per target, removes older production files under both the current and former naming schemes, and preserves development/source archives. Pruning is opt-in and does not run during builds.

`common/` contains only platform-neutral positions, inventory/stack interfaces, transfer logic, and upgrade rules, plus upstream assets. `versions/` owns each target's registration, inventory adapter, graph/tiles, GUI, networking, events, recipes, and renderer. Keep Minecraft and Forge types out of common Java sources. Use the original 1.12 source as the behavioral reference when maintaining these ports; successful compilation alone does not establish gameplay parity.

## Legacy plugin API

Each target exposes `mrriegel.storagenetwork.legacy.api`. Annotate a public, no-argument `LegacyPlugin` implementation with `@StorageNetworkPlugin`. Its `helpersReady(LegacyApi)` callback can register `InventoryProvider` implementations before post-initialization closes registration.

A provider receives a native TileEntity and integer side and returns `InventoryAccess<ItemStack>`, or `null` to fall through to the native inventory adapter. It must honor simulation without mutation, enforce its slot/side limits, return stable canonical identities for aliased inventories, notify its backing inventory on actual changes, and return rejected insertion remainders. Providers run on the owning game thread. `LegacyApi.master(Position)` resolves a loaded master. Network extensions can subclass `TileNode`; the graph discovers native nodes rather than querying capabilities.

Original project: [Storage-Network-Legacy](https://github.com/GlowingFederal/Storage-Network-Legacy). Original authors and credits remain in mod metadata and [LICENSE.txt](LICENSE.txt).
