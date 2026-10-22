<div align="center">

<img alt="Stride — Material 3 Expressive step counter for Android" src="docs/readme/hero.svg" width="100%">

<br>

**A step counter that respects your attention.**<br>
Material 3 Expressive, written in Kotlin and Jetpack Compose.<br>
No ads, no account, no analytics — your steps never leave your phone.

<br>

### 🎉 Stride is now on Google Play

<a href="https://play.google.com/store/apps/details?id=com.vythera.stride"><img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" height="80" alt="Get it on Google Play"></a>

Bought Premium on Gumroad or Polar? **[Swap your key for a free Play code →](https://stride-move.narzo9990.workers.dev)**<br>
<sub>Coming from the GitHub version? See <a href="#moving-to-google-play">Moving to Google Play</a> so you keep your history.</sub>

<br><br>

<a href="https://github.com/NikhilKain/stride/releases/latest"><img src="docs/readme/badges/download.svg" height="40" alt="Download APK"></a>
<img src="docs/readme/badges/android.svg" height="40" alt="Android 8.0+">
<a href="LICENSE"><img src="docs/readme/badges/license.svg" height="40" alt="License GPLv3"></a>

<br>

<a href="docs/img1.jpg"><img src="docs/img1.jpg" width="19%" alt="Stride screenshot 1"></a>
<a href="docs/img2.jpg"><img src="docs/img2.jpg" width="19%" alt="Stride screenshot 2"></a>
<a href="docs/img3.jpg"><img src="docs/img3.jpg" width="19%" alt="Stride screenshot 3"></a>
<a href="docs/img4.jpg"><img src="docs/img4.jpg" width="19%" alt="Stride screenshot 4"></a>
<a href="docs/img5.jpg"><img src="docs/img5.jpg" width="19%" alt="Stride screenshot 5"></a>

<sub><i>Some shots are from the paid edition — see <a href="#editions">Editions</a>.</i></sub>

<br><br>

[Why](#why-another-step-counter) · [Features](#whats-inside) · [Install](#install) · [Move to Play](#moving-to-google-play) · [Build](#build-it-yourself) · [Under the hood](#under-the-hood) · [Editions](#editions) · [Contributing](#contributing)

</div>

<br>

## Why another step counter?

Most of them want a login, a subscription, and permission to sell your movement
data to whoever asks. Stride wants none of that. It counts your steps, draws them
beautifully, and otherwise leaves you alone.

It reads from **Health Connect** when you allow it and falls back to the phone's
hardware step counter when you don't — so it works on day one whether or not you
use any other fitness app. The two sources are merged by taking whichever saw
more steps, never by adding them, so nothing is ever double-counted.

<img alt="Health Connect and the hardware counter are merged by taking the larger value, never the sum" src="docs/readme/sources.svg" width="100%">

## What's inside

<table>
<tr>
<td width="50%" valign="top">
<img src="docs/readme/icons/counting.svg" width="44" height="44" alt=""><br>
<b>Counting that actually works.</b><br>
A foreground service keeps counting with the screen off and survives a reboot. The hardware counter does the work in silicon, so the battery cost is close to nothing.
</td>
<td width="50%" valign="top">
<img src="docs/readme/icons/dashboard.svg" width="44" height="44" alt=""><br>
<b>A dashboard worth opening.</b><br>
A wavy ring that fills as you walk, an odometer that rolls digit by digit, and distance, calories and active minutes underneath. Distance uses a stride length calibrated from your height, not a guess.
</td>
</tr>
<tr>
<td width="50%" valign="top">
<img src="docs/readme/icons/history.svg" width="44" height="44" alt=""><br>
<b>History you can read at a glance.</b><br>
A weekly bar chart and a monthly calendar heatmap, with any day tappable for the detail.
</td>
<td width="50%" valign="top">
<img src="docs/readme/icons/streaks.svg" width="44" height="44" alt=""><br>
<b>Streaks and 14 achievements</b><br>
each a morphing Material shape rather than another gold star — from <i>First Steps</i> to <i>Ultra Walker</i> at 30,000 in a day.
</td>
</tr>
<tr>
<td width="50%" valign="top">
<img src="docs/readme/icons/live.svg" width="44" height="44" alt=""><br>
<b>Live Updates on Android 16.</b><br>
A promoted ongoing notification puts your progress in the status bar. Support is patchy across manufacturers, so the app tells you honestly whether your ROM renders it instead of quietly doing nothing.
</td>
<td width="50%" valign="top">
<img src="docs/readme/icons/share.svg" width="44" height="44" alt=""><br>
<b>Share cards.</b><br>
Renders your day as a 1080×1350 image, gradient and all, ready for wherever you post things.
</td>
</tr>
<tr>
<td width="50%" valign="top">
<img src="docs/readme/icons/theming.svg" width="44" height="44" alt=""><br>
<b>Theming, seriously.</b><br>
Light, dark, system, or pure-black AMOLED. Material You wallpaper colours. Four palettes, five colour styles, and six bundled variable fonts you can actually tell apart.
</td>
<td width="50%" valign="top">
<img src="docs/readme/icons/backups.svg" width="44" height="44" alt=""><br>
<b>Backups that are just a file.</b><br>
Everything exports to a single JSON you can read, keep, and import on another phone. No cloud in the middle.
</td>
</tr>
<tr>
<td colspan="2" valign="top">
<img src="docs/readme/icons/language.svg" width="44" height="44" alt=""><br>
<b>English and Hindi,</b><br>
with an in-app language picker.
</td>
</tr>
</table>

## Install

The easiest way is **[Google Play](https://play.google.com/store/apps/details?id=com.vythera.stride)**:
updates arrive on their own, and Premium follows your Google account to any new phone.

You can also grab the APK from [Releases](https://github.com/NikhilKain/stride/releases/latest).
Android 8.0 or newer.

> [!NOTE]
> On first launch Stride asks for **Physical activity** — it genuinely cannot count
> steps without it. Notifications are optional, and only used for the ongoing
> counter and goal nudges.

## Moving to Google Play

**Bought Premium on Gumroad or Polar?** You don't pay again. Swap your licence key
for a free Google Play code at **[stride-move.narzo9990.workers.dev](https://stride-move.narzo9990.workers.dev)**.
Each key can be swapped once, and it keeps working in the version you have now.

**Using the APK from GitHub?** The Play version is signed by Google, so it can't
install over this one. To keep your history:

1. In Stride, tap **Settings → Export backup**.
2. Uninstall the GitHub version.
3. Install [Stride from Google Play](https://play.google.com/store/apps/details?id=com.vythera.stride)
   and redeem your Play code, if you have one.
4. Tap **Settings → Import backup**. Premium unlocks by itself, or tap
   **Settings → Premium → Restore purchase**.

Need help? Message [@Vytheraa](https://t.me/Vytheraa) on Telegram, or ask in the
[support group](https://t.me/vythera_apps).

## Build it yourself

You need **JDK 17** and the Android SDK with **API 36**.

```bash
git clone https://github.com/NikhilKain/stride
cd stride
./gradlew assembleDebug
```

The wrapper fetches the right Gradle, so that's the whole setup. Your APK lands in `app/build/outputs/apk/debug/`.

<details>
<summary>Gradle can't find your SDK?</summary>

<br>

Point it there:

```properties
# local.properties
sdk.dir=/path/to/Android/Sdk
```

</details>

## Under the hood

| | |
| :-- | :-- |
| **Language** | Kotlin |
| **UI** | Jetpack Compose, Material 3 Expressive (`1.5.0-alpha17`) |
| **Data** | Health Connect, Room, DataStore |
| **Background** | Foreground service, WorkManager |
| **Min / target SDK** | 26 / 36 |

Material 3 Expressive is pinned to an alpha deliberately — the wavy progress
indicators, shape morphing and `MotionScheme` this UI leans on aren't in the
stable release yet.

## Editions

Stride is developed **open-core**. This repository is the open-source edition: a
complete, genuinely usable step counter with nothing time-limited, nagged, or
switched off to upsell you.

The full edition is **[Stride on Google Play](https://play.google.com/store/apps/details?id=com.vythera.stride)**:
free to install, with an optional one-time Premium purchase that adds a GPS walk
and run tracker with maps, deeper insights, extra themes and a few conveniences on
top. Everything in this repository — tracking, history, achievements, theming,
backups — is free and stays that way.

### ☕ Buy me a coffee

Hey! 👋 I'm Nikhil, an indie Android developer building this project in my free time — making apps from GitHub, GitLab, F-Droid and other developer-first sources easier to discover, install and manage on Android, while keeping everything open, fast and user-friendly.

Every contribution goes directly toward new features, bug fixes, performance improvements and long-term development.

<p align="center">
  <a href="https://buy.polar.sh/polar_cl_9FTHFMbmf680aHGH7j3rq6Ii7XsgDFOgLD0Ah3osilE"><img src="docs/buy-me-a-coffee.svg" alt="Buy me a coffee" width="300"></a>

*Thank you for supporting independent open-source development ❤️*

</p>

## Contributing

Issues and pull requests are welcome. Because a paid edition shares this codebase,
contributions need a short copyright assignment before they can be merged — open
an issue first and we'll sort it out there.

## Credits

The bundled typefaces — Nunito, Inter, Outfit, Lexend, Manrope and Space Grotesk —
are used under the [SIL Open Font License](https://scripts.sil.org/OFL).

## Licence

[GNU General Public License v3.0](LICENSE)

Stride is free software: redistribute and modify it under the terms of the GPL.
It comes with no warranty.
