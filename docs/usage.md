# Using Simple Storage Network Legacy


Place a master, connect cables, attach storage links to inventories, and connect a Storage Inventory or Storage Request Table. A simple link exposes its inventory without configurable filters. Configurable links support priority, whitelist/blacklist, metadata, Ore Dictionary, and NBT matching, plus read-only access. Multiple links to one inventory and links to both halves of a vanilla double chest count that inventory once. Insertion prefers higher priorities; extraction prefers lower priorities.

The request screen supports name search and prefixes `@` (mod name), `#` (tooltip), `$` (Ore Dictionary), and `%` (creative tab). Scroll over the item browser to move by rows, or drag its vertical scrollbar. The single search field keeps its text between screens during the current game session; right-click it to clear, or press Tab/Enter to release keyboard focus. Sorting controls sit beside search, and Clear grid sits in the separate crafting panel. Left-click requests a stack, right-click half a stack, Ctrl-click one item, and Shift-click sends the result into your inventory. Click the list with a carried stack to insert it. Shift-click inventory slots to store their contents. Crafting uses the native recipe and container-item behavior and refills depleted ingredients from the network. Clear grid returns inputs to network storage, then player inventory; any remainder drops at the player when both are full.

Optional NEI crafting transfer is available on 1.6.4 and 1.8.9 with the legacy NEI overlay API, and on 1.7.10 with GTNH NEI. Use NEI's normal crafting-recipe transfer control while a crafting terminal or crafting remote is open. Ingredients come from your physical player inventory; request remote ingredients from storage first. Existing grid contents return to player inventory using ordinary slot clicks. If there is insufficient inventory space or an item on the cursor, transfer stops without dropping items. A bound remote cannot be used as a recipe ingredient while it is holding its own screen open. GTNH NEI's automatic craft loop is disabled for these terminals; fill the grid and craft normally, including server-controlled Shift-click crafting. NEI is optional on clients and is not required on dedicated servers.

Ordinary Storage Link Cables and simple links do not open screens. Import, export, and processing cables retain their configuration screens. Existing saved link filters, priorities, and access settings remain effective. Filter slots are ghost patterns: click with a carried stack to copy its pattern, or click with an empty cursor to clear it. Ctrl-click an existing pattern with an empty cursor increases its quantity; Ctrl-right-click decreases it. The **Import** button copies patterns from the connected inventory. Import/export upgrades have four slots, one upgrade per slot:

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

- Native terminal screens use a bounded virtual browser with row scrolling and a scrollbar. Crafting tables and remotes keep a separate crafting panel; search retention lasts for the current game session. Configurable search-retention options and recipe-viewer search synchronization are not included.
- Recipe substitutions for unavailable ingredients: iron nugget → iron ingot; observer → daylight detector; purpur block → obsidian; magma → netherrack. Other ingredient patterns, result variants, and output counts are retained.
- JEI recipe transfer/search synchronization, Baubles equipment-slot integration, FastWorkbench integration, and Patchouli are not included. Collector remotes operate from the normal inventory. These integrations do not become hard dependencies.
- Compact Machines 3's 1.12 integration/dependency is omitted; its newer dimension bridge has no native implementation in these ports.
- The upstream update checker and 1.12 chunk data fixers are omitted. The ports use their own defensive legacy NBT schema; this is not a 1.12 save migration path.
- The capability-based plugin API is replaced by the native inventory-provider API below. Existing 1.12 plugins require adaptation.
- Rejected transfer remainders are retained in a saved recovery inventory, then returned to the network; overflowing recovery drops the remainder instead of deleting it. Partial collector insertion updates the remaining ground stack.

## Legacy plugin API

Each target exposes `mrriegel.storagenetwork.legacy.api`. Annotate a public, no-argument `LegacyPlugin` implementation with `@StorageNetworkPlugin`. Its `helpersReady(LegacyApi)` callback can register `InventoryProvider` implementations before post-initialization closes registration.

A provider receives a native TileEntity and integer side and returns `InventoryAccess<ItemStack>`, or `null` to fall through to the native inventory adapter. It must honor simulation without mutation, enforce its slot/side limits, return stable canonical identities for aliased inventories, notify its backing inventory on actual changes, and return rejected insertion remainders. Providers run on the owning game thread. `LegacyApi.master(Position)` resolves a loaded master. Network extensions can subclass `TileNode`; the graph discovers native nodes rather than querying capabilities.

Original Simple Storage Network authors: MrRiegel and Lothrazar. See [LICENSE.txt](../LICENSE.txt) for the MIT license.
