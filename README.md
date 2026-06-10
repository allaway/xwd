# xwd — Crosswords for Android

A crossword-solving app in the spirit of the NYT and Atlantic crossword apps,
filled with **real, human-constructed puzzles** pulled from openly published
newspaper feeds.

## Puzzle sources

Puzzles are downloaded on demand (never bundled or redistributed) from feeds
that publish standard Across Lite `.puz` files:

| Source | Schedule | Constructor/editor |
|---|---|---|
| Wall Street Journal | Mon–Sat | edited by Mike Shenk |
| Universal Crossword | daily | edited by David Steinberg |
| Jonesin' Crosswords | weekly (Thu) | Matt Jones |

Sources are defined in
[`PuzzleSources.kt`](app/src/main/java/com/allaway/xwd/sources/PuzzleSources.kt)
and are easy to extend — any feed serving `.puz` files works.

## Features

- **Library** — download the latest puzzle from every source with one tap, or
  pick any past date from the archive picker. Cards show fill progress, solve
  time, and completion.
- **Solving** — tap to select, tap again to flip Across/Down, built-in
  keyboard, clue bar with prev/next navigation, full clue list sheet, circled
  squares (GEXT) support.
- **Error highlighting** — an *Autocheck* mode that marks every incorrect
  entry with red text and a slash (NYT-style), plus on-demand
  Check letter / word / puzzle and Reveal letter / word / puzzle.
- **Metrics** — per-puzzle solve timer that persists across sessions, and a
  stats screen with: puzzles solved, clean (assistance-free) solves, total /
  average / best solve times, current and longest daily solve streaks, and a
  per-source breakdown.

## Architecture

- Kotlin + Jetpack Compose (Material 3), single-activity, Compose Navigation.
- The grid is a custom `Canvas` composable
  ([`CrosswordGrid.kt`](app/src/main/java/com/allaway/xwd/ui/grid/CrosswordGrid.kt)).
- Across Lite binary format parser with no Android dependencies
  ([`PuzParser.kt`](app/src/main/java/com/allaway/xwd/puz/PuzParser.kt)),
  covered by JVM unit tests that synthesize `.puz` files in memory.
- Room database stores each puzzle (as JSON), the solver's grid, elapsed
  time, and assistance metrics; OkHttp handles downloads.

## Building

```sh
./gradlew :app:assembleDebug       # APK at app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest   # parser + stats unit tests
```

Requires JDK 17+ and the Android SDK (platform 35).

## Notes on puzzle copyright

The app downloads puzzles directly from publicly accessible feeds to the
user's device, the same model used by long-standing open-source crossword
apps (Shortyz, Forkyz). No puzzle content is stored in or distributed with
this repository. Locked/scrambled `.puz` files are detected; check and
reveal are disabled for them.
