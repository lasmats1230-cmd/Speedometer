# Working on Speedometer

An Android GPS speedometer and trip tracker: Kotlin, Jetpack Compose,
Material 3, Room, no dependency-injection framework, no backend.

## Commands

```bash
./gradlew testDebugUnitTest    # all tests, including the Robolectric ones
./gradlew lintDebug            # lint — currently zero warnings; keep it there
./gradlew assembleDebug        # APKs at app/build/outputs/apk/debug/
```

Needs JDK 17+ and an Android SDK (compileSdk 35, minSdk 26). If `sdk.dir` is
missing, either set `ANDROID_HOME` or write `local.properties` — that file is
deliberately not in version control.

There is no emulator in most sandboxes, and MapLibre's renderer is native, so
`connectedAndroidTest` is usually not an option. Everything below is arranged
so that the JVM tests can carry the load instead.

## Architecture

Single Activity, Compose throughout. The singletons live on `SpeedometerApp`
and screens reach them through `AndroidViewModel` — adding Hilt would cost
more than it saves at this size.

| Layer | Location |
| --- | --- |
| Recording | `tracking/` — `TrackingService` owns the GNSS subscription, `TripRecorder` turns fixes into statistics, `VoiceCoach` speaks them |
| Storage | `data/db/`, `data/repo/` — Room: trips, track points, tours, routes, waypoints |
| Preferences | `data/prefs/` — DataStore, one `AppSettings` data class |
| Import/export | `data/io/` — GPX, JSON backup, CSV, the shared-card renderer |
| Derived figures | `data/repo/TripStatistics.kt`, `Splits.kt`, `RouteTracker.kt` — pure functions |
| UI | `ui/` — one package per tab, plus `ui/components` and `ui/theme` |
| Home screen | `widget/`; the quick settings tile is in `tracking/` |

## Rules this code follows

**Keep the logic out of Android types.** `TripRecorder` takes a plain `Fix`,
not a `Location`; the statistics take entities and a clock, not a database.
That is what makes them testable, and every non-trivial calculation here has
tests. New logic goes the same way: a pure object in `data/repo` or `util`,
with the resources and formatting resolved by the caller.

**Interface text is resolved in the composition.** Renderers, writers and
services take already-resolved strings (see `CsvHeadings`, `TripCardText`).
The one exception is `TrackingService`, which wraps its context with
`AppLocale` so the notification and the spoken updates follow the in-app
language.

**Every string exists in both languages.** `values/strings.xml` and
`values-de/strings.xml` must hold the same keys with the same placeholders —
a `%1$s` present in one and not the other crashes on that locale. Only
`translatable="false"` strings may be missing.

**Migrations are written by hand and tested.** Never
`fallbackToDestructiveMigration`: it would take every recorded trip with it.
Add the column, add the `Migration`, and extend both `MigrationSchemaTest`
(statement matches the exported schema) and `MigrationTest` (a real database
migrates and keeps its rows).

**A recording is a database row from its first second.** `TrackingService`
opens it on start, appends every ten seconds, closes it on save. Rows with
`inProgress = 1` are hidden from history, statistics and backups. Anything
that queries trips must respect that.

**Shared components, not per-screen ones.** `ui/components/Scaffolding.kt`
holds the spacing scale, tab title, pushed-screen frame, section card, list
card and overflow menu. If a screen needs a card, it uses `SectionCard`.

**Comments say why, not what.** The existing ones explain a decision, a
trade-off or a trap; a comment restating the line below it does not belong.

## Tests

- Pure logic: plain JUnit (`SplitsTest`, `StatsCalculatorTest`, `TrackImporterTest`,
  `BackupFormatTest`, `CsvWriterTest`, `RouteTrackerTest`).
- Composables: Robolectric plus `compose-ui-test` (`ComponentRenderTest`,
  `ScreenRenderTest`). Whole screens render with their real view models and
  database. Remember that a `LazyColumn` does not compose what is off screen —
  scroll with `performScrollToNode` rather than asserting on nodes that cannot
  exist yet, and that `StatTile` merges its label and value into one
  `contentDescription`.
- Migrations: `MigrationTest` with `MigrationTestHelper`. The exported schemas
  are on the debug variant's asset path so it can read them.
- Drawing: `TripCardRenderTest` runs in `GraphicsMode.NATIVE` and compares
  pixels.

Run the whole suite before committing; it takes well under a minute after the
first build.
