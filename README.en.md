English | [简体中文](README.md)

# ConstructionWand-Plus

A modded fork of [ConstructionWandLegacy](https://github.com/xinyihl/ConstructionWandLegacy) (itself a backport of Construction Wand features for Minecraft 1.12.2 / Forge), adding new cores, passive upgrades and quality-of-life enhancements on top of the original feature set.

## Implemented Features

### Items and Core Modules

- 4 wand types: Stone / Iron / Diamond / Infinity
- 4 supported cores: Angel / Destruction / AE / ProjectE. Angel and Destruction are always available; AE and ProjectE are registered only when their corresponding mods are loaded.
- Core overlay model and tinting (wand appearance changes after installing a core)

### Placement and Destruction Logic

- Construction mode
- Angel mode: supports mid-air placement
- Destruction mode
- Optional Baubles compatibility: container items equipped in Baubles slots can supply building blocks
- Optional AE2 compatibility: bind a wand with the AE core selected to an AE2 Controller to draw materials from that network
- Optional ProjectE compatibility: a wand with the ProjectE core selected can consume EMC for learned blocks
- Uses an interaction flow close to the original implementation:
  - Placement goes through `ItemBlock.placeBlockAt`
  - Breaking goes through `removedByPlayer` + `onPlayerDestroy`
  - Integrated with Forge Place/Break events

### Upgrades and Options

- Core installation upgrade (combine wand + core in the crafting grid)
- Toggleable options (lock/direction/replace/match/random/core)
- Wand GUI (open with key combo while right-clicking in air)

### Undo and Preview

- Undo history
- Undo preview sync (triggered by key query)
- Automatic preview refresh after undo
- Preview colors:
  - Destruction core: red
  - Undo preview: green
  - Angel core supports air-target preview

### Assets and Localization

- Complete item models and textures
- `en_us.lang` / `zh_cn.lang`

## Default Controls

Current default interactions:

- `Shift + Ctrl + Mouse Wheel`: toggle lock mode
- `Shift + Ctrl + Left Click` (swing in air): switch core
- `Shift + Ctrl + Right Click` (in air): open wand config GUI
- Hold `Shift + Ctrl`: show undo preview
- `Shift + Ctrl` + the undo key (default `Z`, rebindable in Controls): perform undo

> Note: The GUI only opens when right-clicking in air, so a right click on a block stays available for building.

## Configuration File

A config file is generated after first launch at `config/ConstructionWand-Plus.cfg`.

### Configurable Options

- `wandLimits.stoneWandMaxBlocks`: default max placement count for Stone Wand
- `wandLimits.ironWandMaxBlocks`: default max placement count for Iron Wand
- `wandLimits.diamondWandMaxBlocks`: default max placement count for Diamond Wand
- `wandLimits.infinityWandMaxBlocks`: default max placement count for Infinity Wand
- `placement.allowTileEntityPlacement`: whether wand placement of TileEntity blocks is allowed
- `placement.blockWhitelist`: placement whitelist (empty means whitelist disabled)
- `placement.blockBlacklist`: placement blacklist
- `placement.propertyCopyWhitelist`: keyword whitelist of property names allowed to copy in `TARGET` mode (e.g. `facing`, `axis`)
- `performance.deferredLightingUpdates`: experimental coalescing of per-block chunk lighting updates during wand execution (disabled by default; requires a full restart)
- `matching.similarBlocks`: groups of blocks treated as equivalent in `SIMILAR` mode

Whitelist/blacklist entry formats:

- `modid:block` (matches all variants of the block)
- `modid:block@meta` (matches only the specific meta)

Each `matching.similarBlocks` entry is one group of registry names separated by `;`, for example `minecraft:dirt;minecraft:grass`.

When Forge emits a config-changed event, both the placement rules and the similar-block index are rebuilt from the new values. Restart the game or server after editing the file directly so Forge reloads it.

Example:

```cfg
placement {
  B:allowTileEntityPlacement=true
  S:propertyCopyWhitelist <
    facing
    axis
    rotation
    half
    hinge
    shape
    part
    face
   >
  S:blockWhitelist <
    minecraft:stone
    minecraft:stained_hardened_clay@14
   >
  S:blockBlacklist <
    minecraft:chest
    minecraft:mob_spawner
   >
}

matching {
  S:similarBlocks <
    minecraft:dirt;minecraft:grass
   >
}

performance {
  B:deferredLightingUpdates=false
}

wandLimits {
  I:stoneWandMaxBlocks=9
  I:ironWandMaxBlocks=27
  I:diamondWandMaxBlocks=81
  I:infinityWandMaxBlocks=256
}
```

## Optional Compatibility and API

ConstructionWand-Plus can run without AE2, ProjectE, or Baubles. Optional core items, models, and recipes are registered only when the corresponding mod is loaded.

- AE compatibility targets the `appliedenergistics2` mod ID and is built against AE2 Extended Life.
- ProjectE compatibility targets the `projecte` mod ID.
- Baubles compatibility targets the `baubles` mod ID and only adds an extra material source; it does not add a core.

There is currently no stable public third-party API. Packages such as `compat`, `material`, and `wand` are internal implementation details and may change between releases.

Existing item, core, and recipe registry names remain compatibility data. Existing wand option and binding NBT, including `wand_options`, `cores`, `cores_sel`, `bound_container_pos`, `bound_container_dim`, `ae_bound_pos`, and `ae_bound_dim`, remains readable without migration. The configuration filename remains `ConstructionWand-Plus.cfg`.

## Development Build

### Requirements

- Use JDK 17 to run Gradle (the project uses Java Toolchain to compile to a Java 8 target)
- Use `gradlew.bat` on Windows, and `./gradlew` on Linux/macOS

### Common Commands

```bash
# Compile source code
./gradlew compileJava

# Process resources
./gradlew processResources

# Run unit tests
./gradlew test

# Build artifacts
./gradlew build

# Run development client
./gradlew runClient
```
