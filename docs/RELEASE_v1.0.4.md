# Stride v1.0.4 — “Elevate”

Draft. Not published.

---

Two devices rarely agree about how far you walked. A phone left on a desk
under-reports; a phone with Google Fit writing beside it can report the same walk
twice. Until now Stride resolved that disagreement on your behalf, and got it
wrong for anyone whose steps came from a wrist.

This release raises the floor: a count that can't go backwards, a source you
choose rather than one imposed on you, steps that flow out to your other health
apps, and a widget worth putting on a home screen.

## Step source

`Settings → Health Connect → Step source`

| Mode | Behaviour |
|---|---|
| **Automatic** (default) | Health Connect when a watch, band, ring or chest strap contributed today's steps, or when Health Connect holds a day this phone never observed. Otherwise the phone. |
| **This phone** | Only what this device's pedometer recorded. |
| **Health Connect** | Whatever your watch and other apps report. |

The picker shows today's phone and Health Connect totals side by side, so the
choice is made between two real numbers rather than two descriptions. Changing it
re-syncs immediately.

Automatic resolves once per day and only ever moves toward Health Connect, never
away from it. The decision is persisted, so it survives the process being killed.

## Health Connect write-back

Opt-in, off by default. Stride can now publish the steps, distance and active
calories it counts, making them available to your other health apps.

- Calories are written as **active**, never total — filing movement-only
  estimates as a total would inflate every other app's basal-inclusive figures.
- Re-syncing a day replaces that day's records rather than appending, so totals
  cannot drift upward.
- Turning it off asks whether to leave the shared data in place or remove it.
  Nothing already shared is deleted without being asked.

## Home-screen widget

New to the open-source edition.

- Four layouts, chosen from the cell you actually give it: a two-cell strip, a
  three-cell row with distance and calories, a square with a goal ring, and a
  larger tile that adds the last seven days as bars.
- Takes its colours from your palette and your light/dark setting.
- Opens the app when tapped.

## Fixes

- **Daily totals can no longer decrease.** The phone's pedometer only counts from
  the moment Stride starts listening; it knows nothing about steps taken before
  installation, before permission, or while the process was dead. It was being
  written in as a correction rather than a floor, which could reset a day's total
  and start it climbing again.
- **Totals are no longer a maximum of two sources.** `max(healthConnect, sensor)`
  selected inflated aggregates whenever two applications logged the same walk.
- **Sensor deltas are correct across reboots and across midnight.** The hardware
  counter resets to zero on boot, and the post-boot cumulative value was
  previously applied as a single delta.
- **Calories are consistent.** Health Connect's total-calorie records include
  basal metabolism, roughly 1,500 kcal of simply being alive, which does not
  belong beside a step count.
- **Days written to Health Connect are no longer one step short**, and reading
  back Stride's own writes no longer compounds them.

## Notes

Requires Android 8.0 or newer. Health Connect is optional; the phone's pedometer
works on its own.

This is the open-source edition under GPLv3. The paid edition adds a GPS walk
tracker, insights, a share studio, streak freezes, coaching, a weekly recap, a
floating step island and two extra skins — none of which are in this repository,
and none of which are needed to count steps.
