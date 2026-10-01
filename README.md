# Grafting

[![Build](https://github.com/DjonMustard1/trial-dev-grafting-Djon/actions/workflows/build.yml/badge.svg)](https://github.com/DjonMustard1/trial-dev-grafting-Djon/actions/workflows/build.yml)
![Paper](https://img.shields.io/badge/Paper-26.2-blue)
![Java](https://img.shields.io/badge/Java-25-orange)

A small, standalone Paper plugin that brings **Reassembly (Grafting)**, the
Sequence 1 ability of the Attendant of Mysteries (Fool Pathway, *Lord of the
Mysteries*), into Minecraft.

> Grafting joins together things that should never be connected, producing an
> effect that is inconceivable yet completely real.

## Status

Work in progress. The project skeleton builds and loads on Paper 26.2. The
Grafting mechanic is being designed and will be documented below.

## Features

- _Coming soon._

## Usage

| Command / Item | Description |
|----------------|-------------|
| _Coming soon_  |             |

## Requirements

| Component | Version |
|-----------|---------|
| Server    | [Paper](https://papermc.io/downloads/paper) 26.2 |
| Java      | 25 or newer |

No other plugins, databases, or external libraries are required.

## Installation

1. Download the latest `Grafting-x.y.z.jar` from
   [Releases](https://github.com/DjonMustard1/trial-dev-grafting-Djon/releases)
   or the latest [build artifact](https://github.com/DjonMustard1/trial-dev-grafting-Djon/actions).
2. Drop it into your server's `plugins/` folder.
3. Restart the server.

## Building from source

```bash
git clone https://github.com/DjonMustard1/trial-dev-grafting-Djon.git
cd trial-dev-grafting-Djon
./gradlew build        # Windows: gradlew.bat build
```

The plugin jar is written to `build/libs/`.

## Project structure

```
.
├── build.gradle.kts              Build script (Paper API, Java 25)
├── settings.gradle.kts
├── gradlew / gradlew.bat         Gradle wrapper (no local Gradle install needed)
└── src/main
    ├── java/dev/djon/grafting
    │   └── GraftingPlugin.java   Plugin entry point
    └── resources
        └── plugin.yml            Plugin metadata
```

## AI usage disclosure

As permitted by the task, this project was developed with AI assistance using
[Jcode](https://github.com/1jehuang/jcode) with Anthropic Claude and OpenAI
GPT models. The Grafting concept and design decisions are my own. AI was used
for project scaffolding, implementation help, and code review.

## Credits

- *Lord of the Mysteries* by Cuttlefish That Loves Diving. This is an
  unofficial fan project and is not affiliated with the author or publishers.
- Built on the [Paper](https://papermc.io/) API.
