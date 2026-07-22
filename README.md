<h1 align="center">Stride</h1>

<p align="center">
  A Material 3 Expressive step counter for Android — built with Kotlin and Jetpack Compose.
</p>

<p align="center">
  <a href="https://github.com/NikhilKain/stride/releases"><img src="https://img.shields.io/github/v/release/NikhilKain/stride?label=Download&style=for-the-badge&color=00A88E&logo=android&logoColor=white"></a>
</p>

---

## About

Stride counts your steps and gets out of the way. It reads from **Health Connect**
when you allow it, and falls back to the phone's hardware step counter otherwise —
so it works whether or not you use other fitness apps. Nothing leaves your device.

The interface is built on **Material 3 Expressive**: wavy progress indicators,
shape-morphing badges, spring-driven motion and full Material You theming.

## Features

- **Live step tracking** from Health Connect, with a hardware-sensor fallback
- **Animated dashboard** — wavy goal ring, odometer step counter, distance,
  calories and active minutes
- **History** — weekly bar chart and a monthly calendar heatmap, with per-day detail
- **Streaks and achievements** — 14 badges in morphing Material shapes
- **Goals** — daily and weekly targets, plus stride-length calibration from your height
- **Share cards** — render your day as an image for social apps
- **Background tracking** with an ongoing notification, and **Android 16 Live Updates**
  (a promoted progress notification) where the system supports it
- **Theming** — light/dark/system, Material You wallpaper colours, AMOLED black,
  four palettes, five colour styles and seven bundled fonts
- **Backup** — export and import everything as a single JSON file
- **Localised** in English and Hindi

## Build

Requirements: JDK 17, Android SDK with API 36, Gradle 8.13+.

```bash
git clone https://github.com/NikhilKain/stride
cd stride
./gradlew assembleDebug
```

Create a `local.properties` with your SDK location if Gradle doesn't find it:

```properties
sdk.dir=/path/to/Android/Sdk
```

The APK lands in `app/build/outputs/apk/debug/`.

## Tech

| | |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 Expressive (`1.5.0-alpha17`) |
| Data | Health Connect, Room, DataStore |
| Background | WorkManager, foreground service |
| Min / target SDK | 26 / 36 |

## Editions

Stride is developed open-core. This repository holds the open-source edition,
which is a complete and fully usable step counter. A separate paid edition adds
extras on top — a GPS walk tracker with maps, deeper insights, a home-screen
widget and some convenience features. The tracking, history, achievements and
backup you see here are not crippled or time-limited in any way.

## Fonts

The bundled typefaces — Nunito, Inter, Outfit, Lexend, Manrope and Space Grotesk —
are used under the [SIL Open Font License](https://scripts.sil.org/OFL).

## Licence

[GNU General Public License v3.0](LICENSE)

Stride is free software: you may redistribute and modify it under the terms of
the GPL. It comes with no warranty.
