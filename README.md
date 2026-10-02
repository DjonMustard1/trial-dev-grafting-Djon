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

- **Blocks:** the clicked face becomes a sharp map render of the fanart.
- **Any size, still sharp:** grafts go from 1x1 up to 10x10 blocks. Big art is cut
  into one 128 x 128 map per block, and the grid follows the picture's shape (a wide
  banner at 4x is 4 x 1). If the wall is too small, the largest size that fits is used.
- **Mobs:** billboarded pixel art rides on any mob and always faces the viewer.
- **Projectiles:** with projectile grafting on, everything you shoot or throw
  (arrows, tridents, snowballs, eggs, pearls, potions, wind charges) carries art
  until it lands.
- **Purple ritual:** potion swirls gather on every block (or around the mob) where
  the art is about to appear, then it lands with a purple firework-like burst.
  Breaking the block or killing the mob mid-swirl cancels the graft.
- **Random library:** every graft picks a random image, never the same one twice in a row.
- **Artist credits:** grafting credited art posts the artist and a clickable social in chat.
- **Temporary by design:** graft entities are never saved, so a restart wipes them.
  While the server runs, grafts survive chunk reloads and disappear the moment their
  target is gone (mined, pushed by a piston, washed away, exploded, killed, landed).

## Usage

| Action | Effect |
|--------|--------|
| Shift + right-click a block | Graft fanart onto that face (again to swap it) |
| Shift + right-click a mob | Graft fanart onto the mob (again to swap it) |
| Shift + middle-click | Cycle graft size 1x to 10x. Art you are looking at resizes right away |
| Shift + right-click the air | Toggle projectile grafting (hold any item that is not a bow, trident, or throwable) |

> The Minecraft client does not tell the server about right-clicks on air with an
> empty hand, so hold any normal item, or use `/graft projectiles`.

| Command | Permission | Description |
|---------|------------|-------------|
| `/graft` | | Help |
| `/graft list` | | List the loaded fanart and its artists |
| `/graft size <1-10>` | `grafting.use` | Set the graft size directly |
| `/graft projectiles` | `grafting.use` | Toggle projectile grafting |
| `/graft reload` | `grafting.admin` | Reload images from the fanart folder |
| `/graft clear` | `grafting.admin` | Remove every active graft |

`grafting.use` is granted to everyone by default. `grafting.admin` defaults to ops.

## Adding fanart

1. Start the server once so `plugins/Grafting/fanart/` is created.
2. Drop images into that folder (PNG, JPG, GIF, or BMP, any size). WebP is not
   supported by Java's image reader, so convert those to PNG first.
3. Run `/graft reload`.

### Crediting artists

Credits are optional and live in `plugins/Grafting/fanart/credits.yml`, keyed by file name:

```yaml
"Fors art.jpg":
  artist: Jane Doe
  social: "@janedoe on X"
  link: https://x.com/janedoe   # optional, makes the social clickable
```

Grafting a credited image posts `Art by Jane Doe (@janedoe on X)` in chat. Projectile
credits are limited to one line every 2 seconds per player so rapid fire does not
flood chat. Images without an entry load normally and post nothing.

## Configuration

`plugins/Grafting/config.yml` (restart to apply):

| Key | Default | Description |
|-----|---------|-------------|
| `effects.charge-ticks` | `16` | Potion swirl time before the art appears (20 = 1 second, 0 = instant) |
| `text-art.resolution` | `48` | Longest side of mob and projectile art in pixels, 8 to 64 |
| `text-art.mob-size` | `2.0` | Height of mob art in blocks at 1x |
| `text-art.projectile-size` | `1.0` | Height of projectile art in blocks |
| `text-art.y-stretch` | `1.0` | Vertical stretch if pixels look too tall or flat |

## Requirements

| Component | Version |
|-----------|---------|
| Server    | [Paper](https://papermc.io/downloads/paper) 26.2 or newer |
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

The jar is written to `build/libs/`. `build` also runs the test suite: unit tests for
the image pipeline and credits, plus [MockBukkit](https://github.com/MockBukkit/MockBukkit)
tests that load the real plugin and drive clicks, commands, sizes, and cleanup.
Gradle downloads a Java 25 runtime for the tests automatically if needed.

## How it works

| Target | Display | Why |
|--------|---------|-----|
| Block | Invisible, fixed item frames holding maps, one per block | Maps are the sharpest image Minecraft can show without a resource pack |
| Mob | Billboarded text display riding the mob | Follows the mob smoothly and faces every viewer |
| Projectile | Text display riding the projectile | Follows fast-moving entities without a resource pack |

Text art is built from colored full-block characters. Images are shrunk with area
averaging so detail blends instead of turning into noise, neighboring pixels of the
same color are merged into one text run to keep packets small, and an opaque
background in the art's average color hides the gaps the font leaves between pixels.

## Project structure

```
src/main/java/dev/djon/grafting
├── GraftingPlugin.java     Entry point and wiring
├── GraftManager.java       Applies, resizes, and removes grafts
├── GraftListener.java      Player input, potion charge-up, and cleanup events
├── GraftCommand.java       /graft command
├── GraftEffects.java       Particles and sounds
├── GraftSettings.java      config.yml values
├── Messages.java           Player-facing text
└── art
    ├── FanartLibrary.java  Loads the fanart folder, random picks
    ├── Fanart.java         One preprocessed image
    ├── ArtProcessor.java   Image scaling, tiling, and color reduction
    ├── FanartMaps.java     Reusable map tiles per fanart and size
    ├── PixelArtText.java   Pixel grid to colored text
    ├── CreditsFile.java    Reads credits.yml
    └── Credit.java         Artist name, social, link
src/test/java               Unit and MockBukkit tests
```

## AI usage disclosure

As permitted by the task, this project was developed with AI assistance using
[Jcode](https://github.com/1jehuang/jcode) with Anthropic Claude and OpenAI
GPT models. The Grafting concept and design decisions are my own. AI was used
for project scaffolding, implementation help, and code review.

## Credits

- Fanart is credited to its artists in game (see `credits.yml`).
- *Lord of the Mysteries* by Cuttlefish That Loves Diving. This is an
  unofficial fan project and is not affiliated with the author or publishers.
- Built on the [Paper](https://papermc.io/) API.
