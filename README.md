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
| Club 72 (Tim Croce) | Tue & Fri | Freestyle puzzles the constructor posts free on his own blog as `.puz` downloads |
| Tough as Nails (Stella Zawistowski) | ~weekly | Hard themeless puzzles the constructor posts free on her own site as `.puz` downloads |
| Crosshare Daily Mini | daily | Community constructors publish on Crosshare, a free, open-source, donation-funded platform, expressly for free public solving; the platform provides the `.puz` export API |
| Muller Monthly Music Meta (Pete Muller) | monthly (first Tue) | Award-winning meta contest the constructor publishes free on his own site (pmxwords.com), supported by donations |
| Square Pursuit (Steve Mossberg) | irregular | Cryptics and themed crosswords the constructor posts free on his own blog as `.puz` downloads |
| JKL Crosswords (Jesse Lansner) | irregular | Crosswords the constructor posts free on his own site as `.puz`/`.ipuz` downloads |

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

- **Library** — a browsable catalog of every source's feed: available puzzles
  are *listed* (lazy-loaded as you scroll, all the way back through the
  Jonesin' archive) without downloading anything, and each card downloads on
  tap. Sources can be toggled on and off, the latest from every source still
  comes down with one tap, and any past Jonesin' date can be picked from the
  calendar. Downloaded cards show fill progress, solve time, completion, and
  a grid-size badge (Mini / Midi / Maxi / Supermaxi / Ultramaxi).
- **Solving** — tap to select, tap again to flip Across/Down, built-in
  keyboard, clue bar with prev/next navigation, full clue list sheet, circled
  squares (GEXT) support. Grids too large to fit the screen at a readable
  cell size render full-size inside a two-axis pan that keeps the selected
  square in view.
- **Error highlighting** — an *Autocheck* mode that marks every incorrect
  entry with red text and a slash (NYT-style), plus on-demand
  Check letter / word / puzzle and Reveal letter / word / puzzle.
- **Import from photo** — take a picture or screenshot of any crossword and
  the app reconstructs it: Claude (via the Anthropic API) reads the grid and
  clues from the image, **solves the puzzle behind the scenes**, and the
  result is validated (grid/clue numbering consistency, answer crossings)
  and added to your library so you can solve it against the AI's solution.
  Requires your own Claude API key (stored only on the device); AI-imported
  puzzles are clearly labeled since the reconstructed solution may contain
  errors.
- **Metrics** — per-puzzle solve timer that persists across sessions and
  auto-pauses after 20 seconds of inactivity (resuming on input), and a
  stats screen with: puzzles solved, clean (assistance-free) solves, total /
  average / best solve times, average grid size and time-per-square, 3×3
  heatmaps of where in the grid your solves start and finish, a
  day-of-week solving rhythm chart, and a per-source breakdown.

## Architecture

- Kotlin + Jetpack Compose (Material 3), single-activity, Compose Navigation.
- The grid is a custom `Canvas` composable
  ([`CrosswordGrid.kt`](app/src/main/java/com/allaway/xwd/ui/grid/CrosswordGrid.kt)).
- Parsers for both crossword file formats, with no Android dependencies and
  covered by JVM unit tests: the Across Lite binary format
  ([`PuzParser.kt`](app/src/main/java/com/allaway/xwd/puz/PuzParser.kt),
  tested against `.puz` files synthesized in memory) and the ipuz JSON
  format ([`IpuzParser.kt`](app/src/main/java/com/allaway/xwd/puz/IpuzParser.kt));
  downloads auto-detect the format by content.
- Room database stores each puzzle (as JSON), the solver's grid, elapsed
  time, and assistance metrics; OkHttp handles downloads.
- Photo import uses the official Anthropic Java SDK: one streaming Messages
  API call (`claude-opus-4-8`, adaptive thinking, high effort, vision input,
  structured JSON output) extracts and solves the puzzle; `ImportConverter`
  then re-derives the numbering from the returned grid and cross-checks every
  answer against its crossing letters before accepting it.

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
