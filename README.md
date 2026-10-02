# Grafting

[![Build](https://github.com/DjonMustard1/trial-dev-grafting-Djon/actions/workflows/build.yml/badge.svg)](https://github.com/DjonMustard1/trial-dev-grafting-Djon/actions/workflows/build.yml)
![Paper](https://img.shields.io/badge/Paper-26.2-blue)
![Java](https://img.shields.io/badge/Java-25-orange)

A small, standalone Paper plugin based on **Reassembly (Grafting)**, the
Sequence 1 ability of the Attendant of Mysteries (Fool Pathway, *Lord of the
Mysteries*).

> Grafting joins together things that should never be connected, producing an
> effect that is inconceivable yet completely real.

## The idea

In the novel, Grafting connects concepts that have no business being connected.
This plugin takes that literally and a little absurdly: it grafts **the concept
of Lord of the Mysteries fanart** onto ordinary Minecraft things. A stone wall,
a zombie, or an arrow in flight suddenly *is* a piece of fanart, picked at random
from a small library. It is purely visual, and the graft lasts until the target
itself stops existing.

## Features

- **Blocks:** the clicked face is overlaid with a sharp 128 x 128 map render of
  the fanart. Break the block and the art goes with it.
- **Mobs:** the art rides on the mob. The plugin first tries a map in an
  invisible item frame (sharp, experimental) and falls back to pixel art built
  from colored text if the server refuses it. Kill the mob and the art vanishes.
- **Projectiles:** toggle projectile grafting and everything you shoot or throw
  (arrows, tridents, snowballs, eggs, pearls, potions, wind charges) carries a
  piece of pixel art until it lands.
- **Random library:** every graft picks a random image, never the same one twice
  in a row.
- **Temporary by design:** graft entities are never saved, so a restart wipes them.
  While the server runs, grafts survive chunk reloads, and block art disappears if
  its block is removed in any way (mined, pushed by a piston, washed away, exploded).
- Subtle gray fog and quiet chimes when a graft takes hold or breaks.

## Usage

| Action | Effect |
|--------|--------|
| Shift + right-click a block | Graft fanart onto that face (again to swap it) |
| Shift + right-click a mob | Graft fanart onto the mob (again to swap it) |
| Shift + right-click the air | Toggle projectile grafting on or off (hold any item that is not a bow, trident, or throwable) |

> The Minecraft client does not tell the server about right-clicks on air with an
> empty hand, so hold any normal item (or use `/graft projectiles`) to toggle.

| Command | Permission | Description |
|---------|------------|-------------|
| `/graft` | | Help |
| `/graft list` | | List the loaded fanart |
| `/graft projectiles` | `grafting.use` | Toggle projectile grafting |
| `/graft reload` | `grafting.admin` | Reload images from the fanart folder |
| `/graft clear` | `grafting.admin` | Remove every active graft |

`grafting.use` is granted to everyone by default. `grafting.admin` defaults to ops.

## Adding fanart

1. Start the server once so `plugins/Grafting/fanart/` is created.
2. Drop images into that folder (PNG, JPG, GIF, or BMP, any size). WebP is not
   supported by Java's image reader, so convert those to PNG first.
3. Run `/graft reload`.

Images are scaled automatically: 128 x 128 for map art, and up to 32 pixels
on the longest side for text art. Both can be tuned in `config.yml`.

## Configuration

`plugins/Grafting/config.yml`:

```yaml
mob-display: map        # map (sharp, experimental) or text (always works)
text-art:
  resolution: 32        # longest side of text art, 8 to 64
  mob-size: 1.5         # art height in blocks
  projectile-size: 1.0
  y-stretch: 1.0        # adjust if text pixels look too tall or flat
```

## Requirements

| Component | Version |
|-----------|---------|
| Server    | [Paper](https://papermc.io/downloads/paper) 26.2 |
| Java      | 25 or newer |

No other plugins, databases, packet libraries, or resource packs are required.

## Installation

1. Download `Grafting-x.y.z.jar` from the latest
   [build artifact](https://github.com/DjonMustard1/trial-dev-grafting-Djon/actions).
2. Drop it into your server's `plugins/` folder.
3. Restart the server and add fanart (see above).

## Building from source

```bash
git clone https://github.com/DjonMustard1/trial-dev-grafting-Djon.git
cd trial-dev-grafting-Djon
./gradlew build        # Windows: gradlew.bat build
```

The plugin jar is written to `build/libs/`. `build` also runs the tests (34: image
pipeline unit tests and simulated-server tests with [MockBukkit](https://github.com/MockBukkit/MockBukkit)).
Gradle downloads a Java 25 runtime for the tests automatically if you do not have one.

## How it works

| Target | Display | Why |
|--------|---------|-----|
| Block | Map in an invisible, fixed item frame | Maps give the sharpest image and item frames attach cleanly to block faces |
| Mob | Item frame riding the mob, text display fallback | Keeps the art attached as the mob moves |
| Projectile | Text display riding the projectile | Text displays follow moving entities smoothly and need no resource pack |

Text art is built from colored full-block characters. Neighboring pixels of the
same color are merged into a single text run to keep packets small.

## Project structure

```
src/main/java/dev/djon/grafting
├── GraftingPlugin.java     Entry point, wiring
├── GraftManager.java       Applies and removes grafts
├── GraftListener.java      Shift + right-click input and cleanup events
├── GraftCommand.java       /graft command
├── GraftEffects.java       Particles and sounds
├── GraftSettings.java      config.yml values
├── Messages.java           Player facing text
└── art
    ├── FanartLibrary.java  Loads the fanart folder, random picks
    ├── Fanart.java         One preprocessed image
    ├── ArtProcessor.java   Image scaling and color reduction
    ├── PixelArtText.java   Pixel grid to colored text
    └── FanartMaps.java     One reusable map per fanart
src/test/java               Unit tests for the image pipeline, plus MockBukkit tests
                            that load the plugin and drive clicks, commands, and cleanup
```

## AI usage disclosure

As permitted by the task, this project was developed with AI assistance using
[Jcode](https://github.com/1jehuang/jcode) with Anthropic Claude and OpenAI
GPT models. The Grafting concept and design decisions are my own. AI was used
for project scaffolding, implementation help, and code review.

## Credits

- Fanart by friends of the author, used with permission.
- *Lord of the Mysteries* by Cuttlefish That Loves Diving. This is an
  unofficial fan project and is not affiliated with the author or publishers.
- Built on the [Paper](https://papermc.io/) API.
