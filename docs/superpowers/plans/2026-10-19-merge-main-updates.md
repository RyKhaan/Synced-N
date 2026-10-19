# Merge and Integrate Main Updates into Backend Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cleanly merge `origin/main` (containing urmarkiee's PR #2 with Metronome, Auto-Scroll, Font resizer, 12-key Transposition, Paste formatting, and StableScrollView) into the `Backend` branch with zero conflicts, 100% preservation of Groups 1–6 features, passing tests, and live emulator verification.

---

### Task 1: Initiate Git Merge
- [ ] Run `git merge origin/main --no-commit` on `Backend`
- [ ] Verify that all 22 non-conflicting files are staged automatically
- [ ] Confirm the only unmerged files are `AdminActivity.kt` and `MemberActivity.kt`

---

### Task 2: Resolve Conflicts in `AdminActivity.kt`
- [ ] **Lifecycle hooks:** Keep `onPause()` pausing `sheetAutoScroll` and `onStop()` stopping `songMetronome`.
- [ ] **Navigation & Refresh:** Harmonize `navigate(next: String, triggerRefresh: Boolean = true)` with `refreshTeamRoster()` and `refreshTeamServices()`.
- [ ] **Home Hero:** Use `hero.card = card` and `HomeHeroLayout` while preserving `urgentAlertBanner()` and dynamic tile counts.
- [ ] **Roster & Services:** Integrate `RosterRefreshGate` and `updateRosterViews()` to prevent scroll jumps while preserving `refreshTeamServices()`.
- [ ] **Song Edit:** Set `chordSheet = true` on `plainInput` for lyrics/chords so `ChordSheetInput` provides automatic paste formatting, while binding to `currentSong` from Firestore and `TeamRepository.saveSong`.
- [ ] **Song Details:** Combine Firestore `currentSong` resolution with `sheetAutoScroll`, `MetronomeView`, 12-key transpose buttons (`roots.chunked(6)`), and "Original key" reset.

---

### Task 3: Resolve Conflicts in `MemberActivity.kt`
- [ ] **Lifecycle hooks:** Keep `onPause()` pausing `sheetAutoScroll` and `onStop()` stopping `songMetronome`.
- [ ] **Navigation & Refresh:** Keep `triggerRefresh` behavior for roster and services.
- [ ] **Home Hero:** Use `hero.card = card` and `HomeHeroLayout` while preserving `urgentAlertBanner()` and dynamic tile counts.
- [ ] **Roster & Services:** Integrate `RosterRefreshGate` and `updateRosterViews()` to eliminate scroll jump while preserving member services.
- [ ] **Song Details:** Combine Firestore `currentSong` resolution with `sheetAutoScroll`, `MetronomeView`, 12-key transpose buttons, and role-based visibility (`isInstrumentalist` checks).
- [ ] **Schedule View:** Apply `StableScrollView` clipping.

---

### Task 4: Run Complete Unit Test Suite
- [ ] Run `.\gradlew.bat testDebugUnitTest`
- [ ] Ensure all 37+ tests pass (Group 1-6 tests + MetronomePcmTest, SheetPasteFormatterTest, SheetScrollClockTest, RosterRefreshGateTest)
- [ ] Fix any syntax, type, or lint errors if detected.

---

### Task 5: Finalize Git Merge Commit
- [ ] Stage resolved files: `git add app/src/main/java/com/example/syncedn/AdminActivity.kt app/src/main/java/com/example/syncedn/MemberActivity.kt`
- [ ] Verify `git status` shows clean merge state.
- [ ] Commit merge: `git commit -m "merge: integrate collaborator's metronome, auto-scroll, 12-key transpose, and paste formatting from main into Backend"`

---

### Task 6: Deploy & Verify Live on Emulator
- [ ] Install debug APK: `.\gradlew.bat installDebug`
- [ ] Launch `MainActivity` on `emulator-5554`
- [ ] Open a Song Details screen (via Song Library or Worship Service Lineups)
- [ ] Capture screencap confirming Metronome, Auto-Scroll footer, and 12-Key Transpose buttons render crisply without lag or clipping.
