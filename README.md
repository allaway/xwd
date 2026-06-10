# xwd — Crosswords for Android

A crossword-solving app in the spirit of the NYT and Atlantic crossword apps,
filled with **real, human-constructed puzzles** pulled from openly published
newspaper feeds.

## Puzzle sources

Puzzles are downloaded on demand (never bundled or redistributed) and only
from constructors who themselves publish their puzzles free of charge for
personal solving:

| Source | Schedule | Basis for non-commercial use |
|---|---|---|
| Jonesin' Crosswords (Matt Jones) | weekly (Thu) | Self-syndicated; the author gives the `.puz` away free every week via a public mailing list and long-standing community mirrors |
| BEQ (Brendan Emmett Quigley) | Mon & Thu | The constructor posts free puzzles on his own website, supported by donations |

Commercial syndicated puzzles (NYT, WSJ, LA Times, Universal, Newsday, …)
are deliberately **not** included: they are copyrighted works licensed to
paying newspapers, and being free to *play* on a publisher's website does
not grant a license for reuse in a third-party app. None of the freely
distributed sources above carries a formal license document either — almost
no crossword does — so each source in
[`PuzzleSources.kt`](app/src/main/java/com/allaway/xwd/sources/PuzzleSources.kt)
must record a `licenseBasis` explaining why personal, non-commercial use is
sanctioned by the rights holder (enforced by a unit test). Sources are easy
to extend — both date-patterned feeds and scrape-the-latest-link pages work.

## Features

- **Library** — download the latest puzzle from every source with one tap, or
  pick any past date from the Jonesin' archive picker. Cards show fill
  progress, solve time, and completion.
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

No puzzle content is stored in or distributed with this repository; the app
downloads each puzzle directly from the constructor's free public feed to
the user's device for personal solving. The unit tests synthesize `.puz`
bytes in memory rather than committing real puzzle files. This app is for
non-commercial use; if you redistribute it commercially you must secure
your own puzzle licensing. Locked/scrambled `.puz` files are detected;
check and reveal are disabled for them.
