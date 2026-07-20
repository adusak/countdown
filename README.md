# Countdown

A tiny **Kotlin Multiplatform** desktop app (Compose Multiplatform) that shows a
**floating, always-on-top window** counting down to a target date and time.
Runs on **Windows, macOS and Linux** from a single codebase (JVM desktop target).

## Features

- Frameless, transparent, rounded, **always-on-top** floating window.
- Drag it anywhere by its header.
- Live **days / hours / minutes / seconds** countdown (counts up once the target passes).
- Change the target in-app (gear icon), with the choice **persisted** to
  `~/.countdown.properties`.
- Target can also be supplied via CLI argument or the `COUNTDOWN_TARGET` env var.

## Prerequisites

Tooling is pinned with [`mise`](https://mise.jdx.dev/) in `mise.toml`
(Temurin JDK 21 + Gradle 9.5.0):

```sh
mise install
```

## Run

```sh
# from a desktop session (needs a display)
mise exec -- ./gradlew run                 # macOS / Linux
mise exec -- .\gradlew.bat run             # Windows
```

Pass a target as the first argument (otherwise it defaults to next New Year):

```sh
mise exec -- ./gradlew run --args="2026-12-31T23:59"
```

Accepted formats: `yyyy-MM-ddTHH:mm[:ss]`, `yyyy-MM-dd HH:mm[:ss]`, or `yyyy-MM-dd`.

## Build a native distributable

Produces a self-contained app image / installer for the current OS:

```sh
mise exec -- ./gradlew createDistributable          # app image (no extra tools)
mise exec -- ./gradlew packageDistributionForCurrentOS   # .msi / .dmg / .deb
```

## Project layout

```
build.gradle.kts       Compose Multiplatform desktop build (Kotlin 2.4.10 / Compose 1.11.1)
mise.toml              Pinned JDK + Gradle versions
src/desktopMain/kotlin/com/example/countdown/Main.kt   The whole app
```
