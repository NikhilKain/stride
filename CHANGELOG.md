# Changelog

All notable changes to the open-source edition of Stride are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and
this project adheres to [Semantic Versioning](https://semver.org).

## [1.0.4] — 2026-09-05

### Added
- Step source selection (`Settings → Health Connect → Step source`) with three
  modes: `AUTO`, `PHONE`, `HEALTH_CONNECT`. The picker reports today's phone and
  Health Connect totals side by side.
- `AUTO` resolution: prefers Health Connect when a wearable data origin
  (`Device.TYPE_WATCH`, `TYPE_FITNESS_BAND`, `TYPE_RING`, `TYPE_CHEST_STRAP`)
  contributed steps for the current day, or when Health Connect holds at least
  500 steps the local sensor has not observed. The result is one-way for the
  remainder of the day and is persisted, so it survives process death.
- Health Connect write-back, opt-in and off by default. Publishes daily steps,
  distance and active calories; requests write permissions separately; backfills
  30 days on activation; offers deletion of previously written records on
  deactivation.
- Write-back is idempotent — records for a day are deleted before insertion — and
  throttled to a 50-step delta or a five-minute interval.
- `HealthExporter` abstraction over write sinks.
- Home-screen widget (`androidx.glance`), previously absent from this edition.
  Four responsive layouts selected from the measured cell size via
  `SizeMode.Exact`; renders steps, goal progress, distance, calories and a
  seven-day bar chart. Resolves colours from the active palette and light/dark
  mode. Declares a static `previewLayout` and launches the app on tap.
- Dashboard control to start all-day tracking, with runtime permission handling
  for `ACTIVITY_RECOGNITION` and `POST_NOTIFICATIONS`.

### Fixed
- Daily totals could regress. The hardware pedometer was written to the daily row
  as an absolute value, overwriting larger Health Connect totals recorded before
  the sensor began observing. It is now treated as a lower bound except under an
  explicit `PHONE` selection.
- Step totals were computed as `max(healthConnect, sensor)`, which selected
  inflated aggregates when multiple applications wrote overlapping records to
  Health Connect. Source selection now yields exactly one value.
- Sensor deltas were miscomputed across reboots (`TYPE_STEP_COUNTER` resets to
  zero, and the post-boot cumulative value was applied as a delta) and across
  midnight (the previous day's baseline was carried forward). Both cases now
  re-baseline without accruing steps.
- Calorie figures no longer originate from Health Connect's
  `TotalCaloriesBurnedRecord`, which includes basal metabolism. Movement-only
  estimates are used throughout for internal consistency.
- Records written to Health Connect terminated exactly at midnight and were
  apportioned by the provider when read back against a `LocalTime.MAX` window,
  losing one step per day. Records now end 1 ms earlier.
- Reading back own writes no longer compounds. Health Connect exposes no
  exclusion filter, so own contributions are subtracted via a second aggregate
  restricted to this package.

### Changed
- `versionCode` 5, `versionName` 1.0.4.
- Added `androidx.glance:glance-appwidget:1.1.1`.
- Manifest declares `health.WRITE_STEPS`, `health.WRITE_DISTANCE` and
  `health.WRITE_ACTIVE_CALORIES_BURNED`.

## [1.0.0] — 2026-07-22

Initial public release of the open-source edition.

- Material 3 Expressive step counter (`com.vythera.stride`).
- Hardware pedometer with Health Connect as a supplementary source.
- Foreground tracking service, ongoing progress notification and Android 16 Live
  Updates, daily and weekly goals, streaks, 14 achievements, weekly and monthly
  history views, JSON backup import and export.
- Four palettes, five colour styles, six bundled variable typefaces, AMOLED
  black, Material You dynamic colour.
- English and Hindi localisation.

[1.0.4]: https://github.com/NikhilKain/stride/releases/tag/v1.0.4
[1.0.0]: https://github.com/NikhilKain/stride/releases/tag/v1.0.0
