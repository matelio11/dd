# KiteBlocks Custom Enchants (Paper 26.2)

Five independent enchants inspired by the enchant descriptions provided by the server owner:

| Enchant | Max | Items | Effect |
|---|---:|---|---|
| Decapitation | 10 | Sword or axe | Chance to drop a player head after a PvP kill (5%-50%). |
| WellFed | 5 | Sword or axe | Chance to recover hunger/saturation when striking another player (8%-40%). |
| Trickster | 5 | Sword | Chance to shuffle the other player's hotbar items (3%-15%, 12s victim cooldown). |
| Summoner | 3 | Any vanilla weapon | Chance to receive two named Summoning Eyes when defeating an Enderman (10%-30%). |
| Scorch | 5 | Sword | Chance to ignite and slow another player (7%-35%, 5s victim cooldown). |

Summoning Eyes are named vanilla Eyes of Ender, **not** OPBlocks' proprietary item. Effects are approximate original KiteBlocks implementations, not a copy of OPBlocks internals. Chances are configurable in `config.yml`.

## Requirements

- Paper 26.2, Java 25
- No other plugins needed.

## Commands

- `/enchants` opens a menu describing all five enchantments. Players allowed by default.
- `/kbenchant apply decapitation 10` enchants the held sword or axe. OP only.
- `/kbenchant apply wellfed 5`
- `/kbenchant apply trickster 5`
- `/kbenchant apply summoner 3`
- `/kbenchant apply scorch 5`

Custom levels are stored in item PersistentDataContainer, and displayed in item lore. There is no enchanting table integration or enchanted book merging in this first version.

## Build from actual Paper API

Compile on Java 25 with Gradle 8.14+ and an internet connection:

```
gradle clean build
```

The output should be under `build/libs/`. Upload only the resulting KiteBlocksCustomEnchants JAR into `plugins/`, restart, check `/plugins` and `/enchants`. Test on a separate server before production.

This source project is not validated against a live server; item behavior and API compatibility require testing.

## Build using GitHub Actions (no local Java setup)

Create a private GitHub repository, upload the **contents** of this project into its root (including `.github/workflows/build.yml`), and select **Actions -> Build Paper plugin -> Run workflow**. Once finished, download its `KiteBlocksCustomEnchants` artifact. The workflow uses Java 25 and the real Paper API dependency, unlike a stub-based compilation. Inspect the build logs before installing.
