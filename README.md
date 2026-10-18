# Simple Storage Network Legacy

A maintained recreation/backport of Simple Storage Network for **Minecraft 1.6.4, 1.7.10, and 1.8.9 on Forge**. Each implementation is maintained specifically for its Minecraft version.

Connect existing chests and modded inventories to a Network Master, then search, store, request, and craft items through terminals or bound remotes. Import/export cables automate transfers; processing cables and the Network Controller manage machine batches. Includes facades, filters, priorities, six upgrades, and inventory deduplication for repeated links and double chests.

Install the production JAR matching your Minecraft version on both client and server. Only Minecraft and Forge are required. Files ending in `-dev.jar` are development artifacts. These versions do not convert 1.12.2 worlds or load its capability-based plugins. Optional NEI crafting transfer uses ingredients from the player inventory, including GTNH NEI on 1.7.10.

See [usage and configuration](docs/usage.md) for controls, upgrades, compatibility differences, numeric IDs on 1.6.4, and the legacy inventory-provider API. Forge creates `config/storagenetwork.cfg`; preserve configured numeric IDs when updating existing 1.6.4 saves.

## Supported versions and build requirements

| Minecraft | Forge | Pinned build tooling | Build JDK | Compiler / production bytecode | Game Java |
| --- | --- | --- | --- | --- | --- |
| 1.6.4 | 9.11.1.1345 | Unimined 1.4.1 / Gradle 8.13 | 21 | JDK 17 / Java 7 | 8 |
| 1.7.10 | 10.13.4.1614 | GTNHGradle 2.0.34 / Jabel / Gradle 9.7.1 | 25 | JDK 25 with Jabel / Java 8 | 8 |
| 1.8.9 | 11.15.1.2318 | Unimined 1.4.1 / Gradle 8.13 | 21 | JDK 21 / Java 8 | 8 |

Install Python 3 and the required JDKs. Set `PYTHON`, `JAVA8_HOME`, `JAVA17_HOME`, `JAVA21_HOME`, and `JAVA25_HOME` if automatic discovery cannot find them. The root wrapper runs on JDK 21; native target wrappers select their own build JDKs. Keep the pinned wrappers and stock Forge runtime libraries.

## Build and develop

From the repository root on Windows:

```powershell
.\gradlew.bat testAll
.\gradlew.bat buildAll
.\gradlew.bat build1_6_4
.\gradlew.bat build1_7_10
.\gradlew.bat build1_8_9
.\gradlew.bat runClient1_7_10
.\gradlew.bat runServer1_7_10
```

Use `./gradlew` on Linux/macOS. `buildAll` compiles, checks, and remaps/reobfuscates all three production JARs, then inspects their metadata, assets, bytecode, and remapping symbols. `testAll` runs each target's native `test` and `check` tasks, including the existing deterministic terminal regression checks. Individual checks are available as `test1_6_4`, `test1_7_10`, and `test1_8_9`. Root `build` includes both aggregates; root `check` runs `testAll`. Headless checks do not replace in-game or multiplayer testing.

Production outputs are `versions/<minecraft>/build/libs/SimpleStorageNetworkLegacy-<minecraft>-<version>.jar`. `version.properties` defines the public `mod_version` and the next internal `build_number`. `buildAll` uses one internal number across all targets and advances it only after every production archive passes verification. Rebuilding a public version replaces its filename. `pruneBuilds` optionally removes older production outputs while preserving development/source JARs.

Open the repository root in your IDE. Portable IntelliJ tasks live in `.run/`; each version also has an independent native wrapper. Server launches prompt for development server settings and EULA acceptance as required by the target tooling.

## Repository layout

```text
common/            Existing shared platform-neutral code and resources
versions/1.6.4/    Native Forge 1.6.4 implementation and build
versions/1.7.10/   Native Forge 1.7.10 implementation and build
versions/1.8.9/    Native Forge 1.8.9 implementation and build
build-logic/       Shared build, versioning, and verification rules
scripts/           Wrapper/JDK selection and multiversion build dispatcher
validation/        Existing native regression checks, excluded from production JARs
docs/              Usage, configuration, and plugin API
```

Keep Minecraft and Forge types in their native version directories. `common/` contains only the already shared implementation and assets consumed by the legacy targets.

## Attribution and license

Based on Simple Storage Network 2.0.1 by **MrRiegel** and **Sam Bassett (Lothrazar)**. See the [original project](https://github.com/MrRiegel/Storage-Network), [Lothrazar's continuation](https://github.com/Lothrazar/Storage-Network), and [Simple Storage Network on CurseForge](https://www.curseforge.com/minecraft/mc-mods/simple-storage-network). Legacy recreation maintained by GlowingFederal. Distributed under the [MIT license](LICENSE.txt); original notices and mod credits are preserved.
