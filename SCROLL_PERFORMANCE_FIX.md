# Scrolling and response fixes

This update builds on SyncedN-Compact-Metronome.zip. The metronome, twelve transpose keys, chord sheets, login/team features, images, colors, and page layouts are retained.

## Main issue found

Both activities called refreshTeamRoster when rendering Home or Teams. The roster callback called navigate(page), which rebuilt the entire screen and requested the roster again. With a nonempty roster this continued indefinitely. It also reset scroll position and repeatedly allocated new views.

## Changes

- Separate navigation/rendering from roster fetching. Refresh results never start another refresh.
- Update only the member-count label on Home, keeping the existing screen and scroll gesture alive.
- Rebuild Teams rows only for changed results, outside Team Chat, after a drag/fling settles. Preserve the scroll offset. Chat inputs are not replaced by background roster responses.
- Deduplicate in-flight refreshes for the current team; reject stale callbacks after switching teams or destroying the activity.
- Import roster preferences on startup and explicit refresh, rather than rescanning every preference for every member count or row read.
- StableScrollView keeps Android's native fling behavior, disables edge stretching/glow, and hides the vertical scrollbar. No artificial animation, sleeps, or modified fling speeds.
- HomeHeroLayout calculates the same hero-card dimensions during measurement. The previous post-layout listener resized itself and the card, causing extra layout work and initial jumps.

## Files changed

MemberActivity.kt and AdminActivity.kt contain the refresh/render changes. New shared files: StableScrollView.kt, HomeHeroLayout.kt, RosterRefreshGate.java. New regression tests: RosterRefreshGateTest.java.

## Run

1. Extract the ZIP and open Synced-N in Android Studio.
2. Keep your computer's SDK path in local.properties, then sync Gradle and Run.
3. Scroll and fling Home while the roster loads: the page should stay at the current position and taps should remain responsive.
4. Open Teams > Members or Role and scroll while loading. Updated rows should keep your position after scrolling settles. Draft text in Team Chat should remain intact.
5. Navigate quickly between Home and Teams, then switch teams or log out while a request is pending. Old responses must not rebuild another screen.
6. Check portrait/landscape, screen edges, and the metronome/chord sheets.

## Validation and limits

Standalone JVM tests cover request deduplication, stale callbacks, invalid requests, chord transposition/identity, and PCM timing. Source/package checks confirm refresh callbacks cannot enter the navigation/fetch loop. A full Android build, visual device verification, frame-rate profiling, and Firebase network behavior have not been run in this environment. Network and authentication delays can still depend on connection and Firebase availability; this update does not claim a measured frame rate or eliminate all possible latency.
