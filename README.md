# Speedometer

An Android GPS speedometer and trip tracker, built with Jetpack Compose and
Material 3 (Material You).

## Features

**Live** — a large speed readout with max, average and distance tiles, a trip
timer, and a map that follows you. Recording runs in a foreground service, so
the trip keeps going with the screen off, and the notification carries speed,
distance and elapsed time along with pause and stop actions.

**History** — every recording as a card with a sketch of the route, distance,
average and max speed, duration and elevation change. Each trip opens onto a
detail view with the full track on a map, an elevation profile and a speed
profile. Trips export as GPX, sync to Health Connect, and group into tours.

**Tools** — a magnetometer compass, a free-panning map, and GPX/TCX route
import. An imported route can be followed on the live map.

**Settings** — units, language (English or German, independent of the system
setting), theme, Material You or a chosen accent colour, pure-black backgrounds,
battery saving, auto pause, a GPS accuracy threshold, and a choice between the
GNSS chip's Doppler speed or one computed from successive fixes.

**Layout** — a dedicated settings page rearranges the live view: minimap size
(off, small, medium, large), which of twelve figures appear as tiles, their
order, how many per row, and whether the status chip and timer show at all.

**Battery saving** — cycling mode drops to speed and distance on black after a
few seconds and stops drawing the map; extreme mode does so immediately and
turns the backlight right down. Both trade pixels, not accuracy: the recording
is identical either way.

**Waypoints** — long-press the map to drop a labelled, coloured marker. Tap one
to rename, recolour, annotate or delete it. Waypoints are not tied to a trip, so
a water tap noted today is still there next ride.

## Design

The palette comes from the wallpaper via Material You on Android 12 and later.
With that off — or on older releases — a chosen accent seeds a Material 3 scheme
built in `ui/theme/`: tonal ramps are derived in HSL rather than HCT, which
keeps every contrast pairing where the spec puts it without pulling in a colour
library. Recorded tracks keep a fixed green so a route reads the same whatever
the accent.

## Building

```bash
./gradlew assembleDebug        # APKs at app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit tests
./gradlew lintDebug            # lint
```

Requires JDK 17 or later and the Android SDK (compileSdk 35, minSdk 26).

MapLibre's renderer is native, so the build splits by ABI. Install
`app-arm64-v8a-debug.apk` on any recent phone, or `app-universal-debug.apk`
if you would rather not check.

## Architecture

Single Activity, Compose throughout, no dependency-injection framework — the
singletons live on `SpeedometerApp` and screens reach them through
`AndroidViewModel`.

| Layer | Location | Notes |
| --- | --- | --- |
| Recording | `tracking/` | `TrackingService` owns the GNSS subscription; `TripRecorder` turns fixes into statistics |
| Storage | `data/db/`, `data/repo/` | Room: trips, track points, tours, routes |
| Preferences | `data/prefs/` | DataStore |
| Import/export | `data/io/` | GPX writer, GPX/TCX parser |
| Health Connect | `health/` | Writes exercise sessions, distance, speed and elevation |
| UI | `ui/` | One package per tab, plus shared components and theme |
| Localisation | `util/AppLocale.kt` | In-app language, stored where `attachBaseContext` can read it synchronously |

`TripRecorder` takes a plain `Fix` rather than an `android.location.Location`,
so the filtering rules — accuracy thresholds, jitter rejection, implausible
jumps, elevation smoothing, auto pause — are covered by ordinary JVM tests.

Maps are rendered by MapLibre Native from OpenStreetMap vector tiles served by
OpenFreeMap, which needs no API key and no sign-up. The dark and light basemap
styles are named in `ui/components/MapStyles.kt` — point those at another
provider, or at a style bundled in the APK, to change tile source.

The track, the followed route and the position marker are style sources this
app owns rather than annotation-plugin objects, so a live recording pushes new
GeoJSON each second instead of rebuilding overlays.

## Permissions

Location (fine and background) for tracking, notifications for the recording
notification, and the Health Connect write permissions, which are only
requested if you choose to connect it.

Unrestricted background usage is asked for when the app opens without it,
through Android's own dialog rather than a settings row that would only hand
you off to the device settings. Doze is what puts gaps in a track recorded
with the screen off, so the exemption is what keeps extreme mode honest. The
dialog stops appearing as soon as the exemption is granted; declining it
leaves the app working, with that risk.

## Licensing

Settings → About → Legal & licensing lists every dependency with its licence,
and shows the Apache 2.0, BSD 2-Clause and ODbL texts in full from
`res/raw/`. That screen is a compliance obligation, not a courtesy: Apache 2.0
requires the notice to ship with the binary and OpenStreetMap's ODbL requires
visible attribution.

Map data © OpenStreetMap contributors, licensed under ODbL.
