# Building

```
./gradlew build     # versions/<v>/build/libs/playerportals-<version>+<v>.jar, one per version
```

The house build is documented once for every first-party mod in the workspace's `docs/STYLE.md`.
This file covers only where this mod departs from it.

- **Multi-version build** (Stonecutter): 26.2 and 26.3, with 26.2 committed. Each version's keys
  are in `versions/<v>/gradle.properties`.
  - 26.2 builds against Polymer 0.17.3 and 26.3 against Polymer 0.18.4, both from Maven.
  - Dev servers are on ports 25624/25626 (26.2) and 25824/25826 (26.3).
  - The bot legs in the workspace's `scripts/loadtest/player-portals.js` take `DEV_MC=26.3` for
    the 26.3 server.
  - The recipe advancement's `recipe_unlocked` condition names the recipe under both `recipe`
    (26.2) and `recipes` (26.3). Each version reads its own key and ignores the other, so keep both.
- **`mavenLocal()` dependency.** kalic0re's `command`, `text`, `item`, `click` and `portal`
  packages are relocated into the jar from `club.rainbowkitty.kalic0re:kalic0re:<kalic0re_version>`
  in `~/.m2`: `0.30.1` for 26.2 and `0.31.0+26.3` for 26.3.
  - A fresh clone first needs `./gradlew publishToMavenLocal` in kalic0re, for each version.
  - A stale copy shows up as a compile error naming a missing kalic0re class or method.
- **The jar to install** is `playerportals-<version>+<v>.jar` for the server's version.
  `-unshaded` and `-sources` beside it are not.
- **No datagen.** Models, lang, recipes and the mixin config are static resources.
