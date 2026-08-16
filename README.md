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

**Settings** — metric or imperial units, light/dark/system theme, Material You
colours, auto pause, a GPS accuracy threshold, and a choice between the GNSS
chip's Doppler speed or one computed from successive fixes.

## Design

The palette comes from the wallpaper via Material You on Android 12 and later,
with a hand-tuned green scheme as the fallback. Recorded tracks keep a fixed
green so a route reads the same regardless of the accent colour in play.

## Building

```bash
./gradlew assembleDebug        # APK at app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit tests
./gradlew lintDebug            # lint
```

Requires JDK 17 or later and the Android SDK (compileSdk 35, minSdk 26).

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

`TripRecorder` takes a plain `Fix` rather than an `android.location.Location`,
so the filtering rules — accuracy thresholds, jitter rejection, implausible
jumps, elevation smoothing, auto pause — are covered by ordinary JVM tests.

Maps are OpenStreetMap raster tiles via osmdroid, colour-inverted in dark mode.
No API key needed.

## Permissions

Location (fine and background) for tracking, notifications for the recording
notification, and the Health Connect write permissions, which are only
requested if you choose to connect it.

Map data © OpenStreetMap contributors.
