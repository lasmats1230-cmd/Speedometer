# Speedometer

An Android GPS speedometer and trip tracker, built with Jetpack Compose and
Material 3 (Material You). No account, no network beyond map tiles, no
analytics — every trip stays on the device that recorded it.

## Features

**Live** — a large speed readout with configurable tiles, a trip timer, and a
map that follows you. Before a recording starts it says what today already
amounts to: distance, moving time, the streak they extend and what is left of
the weekly goal. Recording runs in a foreground service, so the trip keeps
going with the screen off, and the notification carries speed, distance and
elapsed time along with pause, stop, and a "mark spot" action that saves a
waypoint where you are without unlocking the phone. Pick what the trip is —
ride, run, walk, hike, drive — before you start; foot activities read in pace
rather than km/h. In landscape the readout stands beside the map instead of
stacking, which is the orientation a phone is in on a car mount.

**Statistics** — totals for the week, month, year or all time, a tappable
trend chart, a per-activity breakdown, personal records that open the trip
that set them, and a lifetime card with the odometer, current streak and
active days. A weekly distance goal draws as a closing ring and says whether
the week is ahead of or behind pace.

**History** — every recording as a card with a sketch of the route, searchable
and sortable, filtered by activity. Trips rename, take notes, change activity,
carry photographs, group into tours, export as GPX, sync to Health Connect,
and share either as a few lines of text or as a rendered picture of the route.
The whole history exports as one spreadsheet. Each trip opens onto a detail
view with the full track, an elevation profile, a speed profile,
per-kilometre splits, and how it compared with your usual pace for that
activity. Beat a personal best and the app says so when the trip is saved,
rather than leaving it to be discovered on a tab.

**Tools** — a free-panning map, saved places, and GPX/TCX route import. An
imported route opens onto its own map and elevation profile, and can be
followed on the live view, which then shows how much is left, when you will
arrive, and warns when you leave it.

**Spoken updates** — distance, time and pace announced every kilometre or
mile, in the app's language, ducking music rather than stopping it. The phone
can stay in a pocket.

**Alerts** — a speed limit turns the readout red and buzzes once as you cross
it, on the full display and the battery-saving one. Saved waypoints buzz and
are named as you come within 60 m.

**Backup** — trips live only on this device, so one JSON file carries
everything — trips, tours, routes and waypoints — into your Downloads folder
and merges back in on restore, skipping what is already there. GPX recordings
from other apps import as trips, with the statistics recomputed from the track.

**Layout** — a dedicated settings page rearranges the live view: minimap size,
which of twelve figures appear as tiles, their order, how many per row,
whether the status chip and timer show, and a head-up display mode that
mirrors the speed for reading in a windscreen reflection.

**Battery saving** — cycling mode drops to speed and distance on black after a
few seconds and stops drawing the map; extreme mode does so immediately and
turns the backlight right down. Both trade pixels, not accuracy: the recording
is identical either way.

**Elsewhere on the phone** — a quick settings tile starts and stops a
recording from the shade, a launcher shortcut does the same from the home
screen, and a widget shows this week against the goal, the current streak and
the lifetime total.

**Accessibility** — every control is named and every figure reads as one
phrase rather than a label and a stray number; colour swatches report which
one is chosen; charts say their range aloud. The layout is driven at the
largest font scale Android offers as part of the test suite.

## Design

The palette comes from the wallpaper via Material You on Android 12 and later.
With that off — or on older releases — a chosen accent seeds a Material 3
scheme built in `ui/theme/`: tonal ramps are derived in HSL rather than HCT,
which keeps every contrast pairing where the spec puts it without pulling in a
colour library. Recorded tracks keep a fixed green so a route reads the same
whatever the accent.

Screens share one set of parts — `ui/components/Scaffolding.kt` holds the
spacing scale, the tab title, the pushed-screen frame, the section card, the
list card and the overflow menu — so History, Statistics, Tools and Settings
start at the same baseline and put their actions in the same place.

## Building

```bash
./gradlew assembleDebug        # APKs at app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit tests
./gradlew lintDebug            # lint
```

```bash
./gradlew assembleRelease      # the minified build, which is where R8 runs
```

Requires JDK 17 or later and the Android SDK (compileSdk 35, minSdk 26). The
toolchain is AGP 8.13 on Gradle 8.14.3, which is what the Kotlin version in
`gradle/libs.versions.toml` needs: R8 rewrites Kotlin metadata as it shrinks,
and an AGP older than the compiler cannot read it. If a release build ever
starts logging "An error occurred when parsing kotlin metadata", that pairing
is what to look at.

MapLibre's renderer is native, so the build splits by ABI. Install
`app-arm64-v8a-debug.apk` on any recent phone, or `app-universal-debug.apk`
if you would rather not check.

## Architecture

Single Activity, Compose throughout, no dependency-injection framework — the
singletons live on `SpeedometerApp` and screens reach them through
`AndroidViewModel`.

| Layer | Location | Notes |
| --- | --- | --- |
| Recording | `tracking/` | `TrackingService` owns the GNSS subscription; `TripRecorder` turns fixes into statistics; `VoiceCoach` speaks them |
| Storage | `data/db/`, `data/repo/` | Room: trips, track points, tours, routes, waypoints |
| Preferences | `data/prefs/` | DataStore |
| Import/export | `data/io/` | GPX writer, GPX/TCX parser, JSON backup, CSV writer, shared-card renderer |
| Statistics | `data/repo/TripStatistics.kt`, `Splits.kt`, `RouteTracker.kt`, `TrackSketch.kt` | Pure functions over entities |
| Health Connect | `health/` | Writes exercise sessions, distance, speed and elevation |
| UI | `ui/` | One package per tab, plus shared components and theme |
| Home screen | `widget/` | App widget; the quick settings tile lives in `tracking/` |
| Localisation | `util/AppLocale.kt` | In-app language, stored where `attachBaseContext` can read it synchronously |

A recording is a database row from its first second, appended every ten
seconds and closed on save, so a service the system kills costs seconds rather
than the whole ride. Rows still open are hidden from history, statistics and
backups; the live view offers an interrupted one back the next time it starts.
Every write that spans more than one statement is a transaction, and the write
that closes a trip runs on a scope the service does not own — it stops itself
the moment it hands the trip over.

Everything that touches the recorder runs on the main thread, because that is
where GNSS fixes are delivered: it holds a plain list and a plain state object,
and a background thread reading them while a fix appends is a torn read.

`TripRecorder` takes a plain `Fix` rather than an `android.location.Location`,
so the filtering rules — accuracy thresholds, jitter rejection, implausible
jumps, elevation smoothing, auto pause — are covered by ordinary JVM tests, as
are the statistics, splits, route following, GPX import and backup format.

Maps are rendered by MapLibre Native from OpenStreetMap vector tiles served by
OpenFreeMap, which needs no API key and no sign-up. The style URLs are named
in `ui/components/MapStyles.kt` — point those at another provider, or at a
style bundled in the APK, to change tile source. Which one is used is a
setting, and "match theme" follows the scheme actually in use rather than the
system's dark mode.

The track, the followed route and the position marker are style sources this
app owns rather than annotation-plugin objects, so a live recording pushes new
GeoJSON each second instead of rebuilding overlays.

## Permissions

Location (fine or coarse) for tracking, notifications for the recording
notification, vibration for the alerts, and the Health Connect write
permissions, which are only requested if you choose to connect it.

Not background location. A foreground service typed `location` keeps fixes
coming with the screen off and the app in your pocket, which is the whole of
what this app does with your position — so it neither asks for "allow all the
time" nor declares the permission.

## Licensing

Settings → About → Legal & licensing lists every dependency with its licence,
and shows the Apache 2.0, BSD 2-Clause and ODbL texts in full from
`res/raw/`. That screen is a compliance obligation, not a courtesy: Apache 2.0
requires the notice to ship with the binary and OpenStreetMap's ODbL requires
visible attribution.

Map data © OpenStreetMap contributors, licensed under ODbL.
