# Group 4: Songs Library & Chord/Lyrics Sheets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement full Cloud Firestore integration, multi-tier caching, and role-adaptive UI for the Church Ministry Songs Library and Chord/Lyrics Sheets (`/teams/{teamCode}/songs/{songId}`).

**Architecture:** A standalone `SongData` model with full serialization/deserialization, integrated with `TeamRepository` for offline-first caching (`team_songs_<teamCode>`), 15s debouncing, and default seed worship catalog fallback. Wired to `AdminActivity` for CRUD operations (catalog, search, add/edit, delete) and `MemberActivity` for search, role-adaptive sheet viewing (instrumentalists vs. vocalists), and 12-key transposing.

**Tech Stack:** Kotlin, Android Jetpack, Cloud Firestore (`FirebaseFirestore`), SharedPreferences, JUnit4.

**Spec:** [docs/superpowers/specs/2026-10-18-group-4-songs-library-design.md](file:///c:/Users/RYAN/AndroidStudioProjects/Synced1.0/docs/superpowers/specs/2026-10-18-group-4-songs-library-design.md)

## Global Constraints

- **Package:** Code lives in `com.example.syncedn.data.model`, `com.example.syncedn.data.database`, and `com.example.syncedn`.
- **Target Branch:** `Backend`.
- **Zero UI Disruption:** 100% preservation of existing layout hierarchies, color palettes (`purple`, `navy`, `#E4DCEB`), typography, and padding. Preserve the fixed 6dp margin between transpose preview chord buttons and 8dp margin between schedule tabs.
- **Offline Resiliency:** App must remain responsive and functional offline, utilizing local `SharedPreferences` cache and built-in seed catalog.
- **Firestore Rules Transparency:** Output full updated `firestore.rules` directly in the chat whenever security rules are modified.

## Review Focus

1. **BPM & Pitch Type Coercion:** Numeric or string BPM representations (e.g., `"74"` vs `74`) must deserialize gracefully without throwing `ClassCastException`.
2. **Missing/Empty Sheet Fallback:** A song created without a custom sheet must gracefully fall back to formatted lyrics or standard bracketed template without crashing `ChordSheet.render()`.
3. **Optimistic Local Cache Synchronization:** When a song is saved or deleted by the Leader, the local cache must update immediately so the catalog reflects changes with zero perceptible UI latency.
4. **Vocalist vs. Instrumentalist Visibility:** A member with vocal role (or non-instrumentalist) must only see clean lyrics without exposed chord tokens or transposing UI clutter.
5. **Cold-Start Seed Independence:** A brand new team with zero documents in Firestore `/teams/{teamCode}/songs` must cleanly render the default seed songs catalog rather than an empty, broken view.

---

### Task 1: Data Model (`SongData.kt`) & Firestore Security Rules (`firestore.rules`)

**Files:**
- Create: `app/src/main/java/com/example/syncedn/data/model/SongData.kt`
- Modify: `firestore.rules:360-405`
- Test: `app/src/test/java/com/example/syncedn/ExampleUnitTest.kt`

**Interfaces:**
- Produces: `SongData(id, teamCode, title, artist, key, bpm, sheet, lyrics, referenceUrl, createdBy, createdAt, updatedAt)`, `toMap(): Map<String, Any>`, and `SongData.fromMap(Map<String, Any?>): SongData`.

- [ ] **Step 1: Write the failing unit tests for `SongData` in `app/src/test/java/com/example/syncedn/ExampleUnitTest.kt`**

```kotlin
@Test
fun testSongDataSerializationAndDeserialization() {
    val song = SongData(
        id = "song_123",
        teamCode = "UPJAT7",
        title = "Praise",
        artist = "Elevation Worship",
        key = "C",
        bpm = 74,
        sheet = "[Verse]\n[C]Praise the Lord\n[Chorus]\n[Am]Hallelujah [F][G]",
        lyrics = "Praise the Lord\nHallelujah",
        referenceUrl = "https://youtube.com/watch?v=123",
        createdBy = "user_1",
        createdAt = 1000L,
        updatedAt = 2000L
    )
    val map = song.toMap()
    assertEquals("song_123", map["id"])
    assertEquals("UPJAT7", map["teamCode"])
    assertEquals("Praise", map["title"])
    assertEquals(74, map["bpm"])

    val fromMap = SongData.fromMap(map)
    assertEquals(song.id, fromMap.id)
    assertEquals(song.teamCode, fromMap.teamCode)
    assertEquals(song.title, fromMap.title)
    assertEquals(song.artist, fromMap.artist)
    assertEquals(song.key, fromMap.key)
    assertEquals(song.bpm, fromMap.bpm)
    assertEquals(song.sheet, fromMap.sheet)
    assertEquals(song.lyrics, fromMap.lyrics)
    assertEquals(song.referenceUrl, fromMap.referenceUrl)
    assertEquals(song.createdBy, fromMap.createdBy)
    assertEquals(song.createdAt, fromMap.createdAt)
    assertEquals(song.updatedAt, fromMap.updatedAt)

    // Coercion test: string BPM to Int
    val mapWithStringBpm = map.toMutableMap().apply { put("bpm", "82") }
    val coerced = SongData.fromMap(mapWithStringBpm)
    assertEquals(82, coerced.bpm)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest`  
Expected: Compilation failure because `SongData` does not exist yet.

- [ ] **Step 3: Implement `SongData.kt` in `app/src/main/java/com/example/syncedn/data/model/SongData.kt`**

Create `SongData` data class with full fields, `toMap()`, and companion `fromMap()` adhering to spec.

- [ ] **Step 4: Update `firestore.rules` to include `isValidSong()` and subcollection `match /songs/{songId}`**

Add `isValidSong()` validator function and `match /songs/{songId}` rules within `match /teams/{teamCode}`.

- [ ] **Step 5: Run tests and compile verification**

Run: `.\gradlew.bat testDebugUnitTest`  
Expected: PASS (0 errors).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/syncedn/data/model/SongData.kt firestore.rules app/src/test/java/com/example/syncedn/ExampleUnitTest.kt
git commit -m "feat(songs): add SongData model and firestore security rules for team songs"
```

---

### Task 2: Repository Layer (`TeamRepository.kt`) — Caching, Fetching, & Seed Catalog Fallback

**Files:**
- Modify: `app/src/main/java/com/example/syncedn/data/database/TeamRepository.kt`
- Test: `app/src/test/java/com/example/syncedn/ExampleUnitTest.kt`

**Interfaces:**
- Consumes: `SongData` from Task 1.
- Produces:
  - `TeamRepository.getSongs(teamCode: String, forceRefresh: Boolean = false, onResult: (List<SongData>) -> Unit)`
  - `TeamRepository.saveSong(teamCode: String, song: SongData, onComplete: (Boolean, String?) -> Unit)`
  - `TeamRepository.deleteSong(teamCode: String, songId: String, onComplete: (Boolean, String?) -> Unit)`
  - `TeamRepository.getCachedSongs(teamCode: String): List<SongData>`
  - `TeamRepository.getDefaultSeedSongs(teamCode: String): List<SongData>`

- [ ] **Step 1: Write unit tests for `TeamRepository` song caching & seed fallback in `ExampleUnitTest.kt`**

```kotlin
@Test
fun testTeamRepositorySongCachingAndSeedFallback() {
    val seed = TeamRepository.getDefaultSeedSongs("TEST01")
    assertTrue(seed.isNotEmpty())
    assertTrue(seed.any { it.title == "Praise" })
    assertTrue(seed.any { it.title == "Way Maker" })

    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = context.getSharedPreferences("synced_profile", Context.MODE_PRIVATE)
    
    // Save locally and verify retrieval
    TeamRepository.saveSongsLocally("TEST01", seed, prefs)
    val cached = TeamRepository.getCachedSongs("TEST01", prefs)
    assertEquals(seed.size, cached.size)
    assertEquals(seed.first().title, cached.first().title)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest`  
Expected: FAIL (`getDefaultSeedSongs` or `saveSongsLocally` not yet implemented).

- [ ] **Step 3: Implement song methods and caching in `TeamRepository.kt`**

Implement:
- `getDefaultSeedSongs(teamCode: String): List<SongData>` with Praise, Trust in God, Way Maker, Gratitude, Goodness of God, Build My Life, Amazing Grace.
- `getCachedSongs(teamCode: String, customPrefs: SharedPreferences? = null): List<SongData>`
- `saveSongsLocally(teamCode: String, songs: List<SongData>, customPrefs: SharedPreferences? = null)`
- `getSongs(teamCode, forceRefresh, onResult)` with 15s debounce cooldown, local cache delivery, and Firestore query `/teams/{teamCode}/songs`.
- `saveSong(teamCode, song, onComplete)` with optimistic local cache update and Firestore `set(..., SetOptions.merge())`.
- `deleteSong(teamCode, songId, onComplete)` with optimistic local cache removal and Firestore `delete()`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat testDebugUnitTest`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/syncedn/data/database/TeamRepository.kt app/src/test/java/com/example/syncedn/ExampleUnitTest.kt
git commit -m "feat(repo): add songs catalog management, caching, and seed fallback to TeamRepository"
```

---

### Task 3: Admin Activity Integration (`AdminActivity.kt`) — Songs Catalog, Add/Edit Song, & Delete

**Files:**
- Modify: `app/src/main/java/com/example/syncedn/AdminActivity.kt:2850-2960`

**Interfaces:**
- Consumes: `TeamRepository.getSongs()`, `saveSong()`, `deleteSong()`, `SongData`.

- [ ] **Step 1: Wire `songsView()` in `AdminActivity.kt` to load dynamic songs from `TeamRepository`**

Replace hardcoded list in `songsView()` with `TeamRepository.getSongs(teamCode)`. Add real-time text filter search bar for Title and Artist. Add "+ Add Song" header button to navigate to `songEdit`.

- [ ] **Step 2: Wire `songEditView()` in `AdminActivity.kt` to save and delete songs**

Provide Title, Artist, Key picker/field, BPM, Reference Link, and Lyrics/Chords sheet inputs. Connect "Save Song" action to `TeamRepository.saveSong(teamCode, song)` and navigate back to `songs`. Connect "Delete Song" to `TeamRepository.deleteSong(teamCode, songId)`.

- [ ] **Step 3: Wire `songDetailsView()` in `AdminActivity.kt` to display dynamic `SongData`**

Load selected song from `TeamRepository.getCachedSongs(teamCode)` (or fallback seed). Render metadata, YouTube action button, "Edit Song" button, and monospace transpose preview with 6dp margins.

- [ ] **Step 4: Verify compilation & run tests**

Run: `.\gradlew.bat compileDebugSources`  
Run: `.\gradlew.bat testDebugUnitTest`  
Expected: 0 errors, all unit tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/syncedn/AdminActivity.kt
git commit -m "feat(admin): connect songs catalog, add/edit form, and details to TeamRepository"
```

---

### Task 4: Member Activity Integration (`MemberActivity.kt`) — Dynamic Songs, Role-Adaptive Sheets, & Lineup Linking

**Files:**
- Modify: `app/src/main/java/com/example/syncedn/MemberActivity.kt:2140-2330`

**Interfaces:**
- Consumes: `TeamRepository.getSongs()`, `SongData`, `ChordSheet.render()`.

- [ ] **Step 1: Wire `songsView()` in `MemberActivity.kt` to dynamic songs from `TeamRepository`**

Replace hardcoded song list with `TeamRepository.getSongs(teamCode)` and real-time title/artist search filtering.

- [ ] **Step 2: Connect `songDetailsView()` in `MemberActivity.kt` to dynamic `SongData` and role-based visibility**

Load song from repository. If member is an instrumentalist (`ChordSheet.isInstrumentalist(...)`), render monospace lyrics & chords sheet with 6dp spaced transpose buttons. If vocalist or general member, render clean lyrics only.

- [ ] **Step 3: Connect `lineupView()` in `MemberActivity.kt` to open `songDetailsView`**

Allow tapping any song in `lineupView()` to open `songDetailsView` with the song's assigned key.

- [ ] **Step 4: Verify compilation & run tests**

Run: `.\gradlew.bat compileDebugSources`  
Run: `.\gradlew.bat testDebugUnitTest`  
Expected: 0 errors, all unit tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/syncedn/MemberActivity.kt
git commit -m "feat(member): render dynamic songs catalog, role-adaptive chord sheets, and lineup linking"
```

---

### Task 5: End-to-End Build & Visual Verification

**Files:**
- Emulator live test and screencaps: `screen_songs_catalog.png`, `screen_song_edit.png`, `screen_song_transpose.png`.

- [ ] **Step 1: Build debug APK and install onto emulator**

Run: `.\gradlew.bat installDebug`  
Expected: BUILD SUCCESSFUL, APK installed on `emulator-5554`.

- [ ] **Step 2: Visual verification of Songs Catalog and Search**

Run ADB commands to navigate to Songs screen, enter search text, take screenshot, and verify dynamic song cards.

- [ ] **Step 3: Visual verification of Song Details & Transpose Preview**

Open song details, verify 6dp margin between buttons, tap transpose chord button (e.g., `D` or `Eb`), verify active state highlight and monospaced chord alignment.

- [ ] **Step 4: Visual verification of Leader Song Creation/Edit**

Create a new test song ("Living Hope", Key D, 72 BPM) in `AdminActivity`, verify it saves to cache and appears in the Songs catalog.

- [ ] **Step 5: Run full test suite and confirm 0 regressions**

Run: `.\gradlew.bat testDebugUnitTest`  
Expected: All tests pass.

- [ ] **Step 6: Final Commit & Branch Readiness**

```bash
git status
git log -n 5 --oneline
```
