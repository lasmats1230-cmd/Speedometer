# Working on Speedometer

An Android GPS speedometer and trip tracker: Kotlin, Jetpack Compose,
Material 3, Room, no dependency-injection framework, no backend.

## Commands

```bash
./gradlew testDebugUnitTest    # all tests, including the Robolectric ones
./gradlew lintDebug            # lint
./gradlew assembleDebug        # APKs at app/build/outputs/apk/debug/
./gradlew assembleRelease      # the only build that runs R8
```

Needs JDK 17+ and an Android SDK (compileSdk 35, minSdk 26). If `sdk.dir` is
missing, either set `ANDROID_HOME` or write `local.properties` — that file is
deliberately not in version control.

Lint's remaining warnings are all "a newer version of X is available" plus
targetSdk not being the latest; nothing structural. Anything else that appears
is new and yours.

**Run `assembleRelease` when you touch the toolchain.** Debug never runs R8,
and R8 has to be able to read the metadata the Kotlin compiler writes. AGP
8.7 with Kotlin 2.2 logged a parse failure for every class in the app and
nothing but a release build showed it. AGP and Gradle are pinned as a pair in
`gradle/libs.versions.toml` and `gradle/wrapper/`; CI builds release for the
same reason.

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
| Storage | `data/db/`, `data/repo/` — Room: trips, track points, photos, tours, routes, waypoints |
| Preferences | `data/prefs/` — DataStore, one `AppSettings` data class |
| Import/export | `data/io/` — GPX, JSON backup, CSV, `Downloads`, the shared-card renderer |
| Derived figures | `data/repo/TripStatistics.kt`, `Splits.kt`, `RouteTracker.kt`, `TrackSketch.kt` — pure functions |
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

**The recorder is single-threaded, on the main thread.** GNSS fixes arrive on
the main looper and append to a plain list, so the ticker, the persistence
pass and the stop handler all run there too — `TrackingService`'s scope is
`Dispatchers.Main.immediate` on purpose. Reading `recorder.points` or
`recorder.state` from a background thread is a torn read at best and a
`ConcurrentModificationException` mid-ride at worst. Database calls switch to
IO inside the repository, which is where the switch belongs.

**Work that must outlive its caller goes on `SpeedometerApp.applicationScope`.**
Saving a trip is the case that matters: the service launches the write and
then stops itself, and its own scope dies with it. Anything else tied to a
screen or a service is fair game for cancellation and should stay there.

**A write that spans more than one statement is a transaction.** A trip and
its track, a recording being closed, a backup being merged — all of them are
cancellable, and half of one is worse than none. The transactional pieces live
on the DAO (`insertTripWithTrack`, `finishTripWithTrack`, `appendTrack`).

**Shared components, not per-screen ones.** `ui/components/Scaffolding.kt`
holds the spacing scale, tab title, pushed-screen frame, section card, list
card and overflow menu. If a screen needs a card, it uses `SectionCard`.

**Comments say why, not what.** The existing ones explain a decision, a
trade-off or a trap; a comment restating the line below it does not belong.

## Tests

- Pure logic: plain JUnit (`SplitsTest`, `StatsCalculatorTest`, `TrackImporterTest`,
  `BackupFormatTest`, `CsvWriterTest`, `RouteTrackerTest`, `TrackSketchTest`,
  `PhotoGrantsTest`).
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
- Accessibility: `ScreenRenderTest` drives two screens at font scale 2, the
  accessibility maximum. A new screen with fixed heights around text should
  get the same treatment.

Run the whole suite before committing; it takes about a minute after the first
build. A change to the build files or the dependency versions also wants
`assembleRelease`, which takes several more.
