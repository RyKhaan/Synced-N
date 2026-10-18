# Group 3: Worship Services, Schedules, Role Assignments & Rehearsals Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Synchronize worship services, schedules, role assignments, rehearsal details, and song lineups between Team Leaders and Members in Cloud Firestore under `/teams/{teamCode}/services/{serviceId}` with offline caching, 15-second query throttling, and 100% UI preservation.

**Architecture:** A clean three-layer architecture where `ServiceData` models are managed via `TeamRepository` with `SharedPreferences` instant caching and in-flight request deduplication. `AdminActivity` and `MemberActivity` consume repository methods directly without altering existing UI widgets, styles, or navigation flows.

**Tech Stack:** Kotlin, Android SDK, Cloud Firestore, Firebase Authentication, SharedPreferences, JUnit 4.

**Spec:** [docs/superpowers/specs/2026-10-18-group-3-worship-services-schedules-design.md](file:///c:/Users/RYAN/AndroidStudioProjects/Synced1.0/docs/superpowers/specs/2026-10-18-group-3-worship-services-schedules-design.md)

## Global Constraints

- Android SDK minSdk 26, targetSdk 35.
- Package namespace `com.example.syncedn`.
- Strictly zero UI alterations: do NOT modify widget layouts, padding, colors, fonts, or navigation flows.
- Firestore subcollection path `/teams/{teamCode}/services/{serviceId}`.
- Non-leader team members may only read services where `status == "Published"`.
- Team Leaders can create, edit, publish, and delete both `"Draft"` and `"Published"` services.
- Multi-level offline resilience: all services cached in `preferences` under `team_services_<teamCode>`.
- 15-second debounce cooldown and in-flight locks to prevent re-render thrashing or frame drops.

## Review Focus

- Malformed or partial service JSON maps from Firestore must deserialize safely with defaults without crashing.
- Draft services must never leak to regular members in `MemberActivity.kt`.
- Deleting or updating a service must immediately synchronize both local cache and Firestore.
- An empty service lineup or empty assignments list must render clean fallback messages rather than null pointer exceptions.
- Rapid tab switching between Schedule and Home must not trigger redundant Firestore query loops.

---

### Task 1: Data Models (`ServiceData.kt`, `ServiceAssignment`, `RehearsalData`, `ServiceSong`)

**Files:**
- Create: `app/src/main/java/com/example/syncedn/data/model/ServiceData.kt`
- Test: `app/src/test/java/com/example/syncedn/ExampleUnitTest.kt`

**Interfaces:**
- Produces:
  - `ServiceData(id: String, teamCode: String, title: String, date: String, start: String, end: String, venue: String, status: String, assignments: List<ServiceAssignment>, rehearsal: RehearsalData, songs: List<ServiceSong>, createdBy: String, createdAt: Long, updatedAt: Long)`
  - `ServiceAssignment(uid: String, email: String, name: String, role: String)`
  - `RehearsalData(date: String, start: String, end: String, venue: String, notes: String)`
  - `ServiceSong(title: String, artist: String, key: String, bpm: Int)`
  - `toMap(): Map<String, Any>` and `fromMap(Map<String, Any?>)` companion methods for all four classes.

- [ ] **Step 1: Write the failing unit tests for `ServiceData` serialization and deserialization**

```kotlin
@Test
fun testServiceDataSerializationAndDeserialization() {
    val assignment = ServiceAssignment(
        uid = "user_123",
        email = "member@example.com",
        name = "John Doe",
        role = "Guitarist"
    )
    val rehearsal = RehearsalData(
        date = "2026-10-24",
        start = "18:00",
        end = "19:30",
        venue = "Fellowship Hall",
        notes = "Review tempo for Song 2"
    )
    val song = ServiceSong(
        title = "Way Maker",
        artist = "Sinach",
        key = "E",
        bpm = 68
    )
    val service = ServiceData(
        id = "service_001",
        teamCode = "TEST01",
        title = "Sunday Celebration",
        date = "2026-10-25",
        start = "09:00",
        end = "10:30",
        venue = "Main Sanctuary",
        status = "Published",
        assignments = listOf(assignment),
        rehearsal = rehearsal,
        songs = listOf(song),
        createdBy = "leader_uid_999"
    )

    val map = service.toMap()
    assertEquals("service_001", map["id"])
    assertEquals("TEST01", map["teamCode"])
    assertEquals("Published", map["status"])

    val restored = ServiceData.fromMap(map)
    assertEquals(service.id, restored.id)
    assertEquals(service.title, restored.title)
    assertEquals(1, restored.assignments.size)
    assertEquals("Guitarist", restored.assignments[0].role)
    assertEquals("Fellowship Hall", restored.rehearsal.venue)
    assertEquals(1, restored.songs.size)
    assertEquals(68, restored.songs[0].bpm)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests com.example.syncedn.ExampleUnitTest.testServiceDataSerializationAndDeserialization`
Expected: Compilation failure (Unresolved reference: ServiceData)

- [ ] **Step 3: Implement `ServiceData.kt` in `app/src/main/java/com/example/syncedn/data/model/ServiceData.kt`**

Implement `ServiceData`, `ServiceAssignment`, `RehearsalData`, and `ServiceSong` data classes with full `toMap()` and `fromMap()` implementations as specified in the spec.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests com.example.syncedn.ExampleUnitTest.testServiceDataSerializationAndDeserialization`
Expected: BUILD SUCCESSFUL with 1/1 passed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/syncedn/data/model/ServiceData.kt app/src/test/java/com/example/syncedn/ExampleUnitTest.kt
git commit -m "feat(model): add ServiceData, ServiceAssignment, RehearsalData, and ServiceSong models"
```

---

### Task 2: Firestore Security Rules (`firestore.rules`)

**Files:**
- Modify: `firestore.rules:340-360`

**Interfaces:**
- Consumes: `isAuthenticated()`, `isLeaderOfTeam(teamCode)`, `isSameTeam(teamCode)`, `areImmutableFieldsUnchanged()`
- Produces: Subcollection rules for `/teams/{teamCode}/services/{serviceId}` and `isValidService(data, serviceId, teamCode)` function.

- [ ] **Step 1: Add subcollection match and validator in `firestore.rules`**

Add `isValidService(data, serviceId, teamCode)` in Domain Validators:
```javascript
function isValidService(data, serviceId, teamCode) {
  return hasOnlyAllowedFields(data, [
    'id', 'teamCode', 'title', 'date', 'start', 'end', 'venue',
    'status', 'assignments', 'rehearsal', 'songs', 'createdBy',
    'createdAt', 'updatedAt'
  ]) &&
  hasRequiredFields(data, ['id', 'teamCode', 'title', 'date', 'start', 'end', 'venue', 'status', 'createdBy']) &&
  data.id is string && data.id == serviceId &&
  data.teamCode is string && data.teamCode == teamCode &&
  data.title is string && data.title.size() > 0 && data.title.size() <= 100 &&
  data.date is string && data.date.size() <= 20 &&
  data.start is string && data.start.size() <= 10 &&
  data.end is string && data.end.size() <= 10 &&
  data.venue is string && data.venue.size() > 0 && data.venue.size() <= 100 &&
  data.status is string && data.status in ['Draft', 'Published'] &&
  (!('assignments' in data) || (data.assignments is list && data.assignments.size() <= 50)) &&
  (!('rehearsal' in data) || data.rehearsal is map) &&
  (!('songs' in data) || (data.songs is list && data.songs.size() <= 50)) &&
  data.createdBy is string && data.createdBy.size() > 0 &&
  (!('createdAt' in data) || (data.createdAt is number && data.createdAt >= 0) || (data.createdAt is timestamp)) &&
  (!('updatedAt' in data) || (data.updatedAt is number && data.updatedAt >= 0) || (data.updatedAt is timestamp));
}
```

Add subcollection rule inside `match /teams/{teamCode}`:
```javascript
match /services/{serviceId} {
  allow read: if isAuthenticated() && isSameTeam(teamCode) && (
    isLeaderOfTeam(teamCode) ||
    (resource != null && resource.data.status == 'Published')
  );

  allow create: if isLeaderOfTeam(teamCode) &&
                   isValidService(request.resource.data, serviceId, teamCode) &&
                   request.resource.data.createdBy == request.auth.uid;

  allow update: if isLeaderOfTeam(teamCode) &&
                   isValidService(request.resource.data, serviceId, teamCode) &&
                   areImmutableFieldsUnchanged(['id', 'teamCode', 'createdBy', 'createdAt']);

  allow delete: if isLeaderOfTeam(teamCode);
}
```

- [ ] **Step 2: Commit**

```bash
git add firestore.rules
git commit -m "feat(security): add firestore security rules for team services subcollection"
```

---

### Task 3: Repository Service Methods & Caching (`TeamRepository.kt`)

**Files:**
- Modify: `app/src/main/java/com/example/syncedn/data/database/TeamRepository.kt`
- Test: `app/src/test/java/com/example/syncedn/ExampleUnitTest.kt`

**Interfaces:**
- Consumes: `ServiceData`, `SharedPreferences`, `FirebaseFirestore`
- Produces:
  - `TeamRepository.getServices(teamCode: String, isLeader: Boolean, preferences: SharedPreferences, onComplete: (Result<List<ServiceData>>) -> Unit)`
  - `TeamRepository.saveService(teamCode: String, service: ServiceData, preferences: SharedPreferences, onComplete: ((Result<ServiceData>) -> Unit)? = null)`
  - `TeamRepository.deleteService(teamCode: String, serviceId: String, preferences: SharedPreferences, onComplete: ((Result<Boolean>) -> Unit)? = null)`
  - `TeamRepository.getCachedServices(teamCode: String, preferences: SharedPreferences): List<ServiceData>`

- [ ] **Step 1: Write unit tests for Service caching, throttling, and member filtering in `ExampleUnitTest.kt`**

```kotlin
@Test
fun testTeamRepositoryServiceCachingAndFiltering() {
    val prefs = MockSharedPreferences()
    val draftService = ServiceData(
        id = "srv_draft",
        teamCode = "ALPHA1",
        title = "Midweek Service",
        status = "Draft"
    )
    val pubService = ServiceData(
        id = "srv_pub",
        teamCode = "ALPHA1",
        title = "Sunday Service",
        status = "Published"
    )

    TeamRepository.saveServiceLocally("ALPHA1", draftService, prefs)
    TeamRepository.saveServiceLocally("ALPHA1", pubService, prefs)

    val cached = TeamRepository.getCachedServices("ALPHA1", prefs)
    assertEquals(2, cached.size)

    val forMembers = cached.filter { it.status == "Published" }
    assertEquals(1, forMembers.size)
    assertEquals("Sunday Service", forMembers[0].title)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests com.example.syncedn.ExampleUnitTest.testTeamRepositoryServiceCachingAndFiltering`
Expected: Compilation failure (Unresolved reference: saveServiceLocally / getCachedServices)

- [ ] **Step 3: Implement methods in `TeamRepository.kt`**

Add `servicesInFlight` lock, `lastServicesFetchTimes` map (15s cooldown), `getCachedServices`, `saveServiceLocally`, `getServices`, `saveService`, and `deleteService` with SharedPreferences JSON synchronization and Firestore background write.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests com.example.syncedn.ExampleUnitTest.testTeamRepositoryServiceCachingAndFiltering`
Expected: BUILD SUCCESSFUL with all tests passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/syncedn/data/database/TeamRepository.kt app/src/test/java/com/example/syncedn/ExampleUnitTest.kt
git commit -m "feat(repo): add worship service management, caching, and throttling to TeamRepository"
```

---

### Task 4: Connect Leader Service Management & Schedule (`AdminActivity.kt`)

**Files:**
- Modify: `app/src/main/java/com/example/syncedn/AdminActivity.kt:1980-2045`

**Interfaces:**
- Consumes: `TeamRepository.saveService()`, `TeamRepository.getServices()`, `leaderDraft`
- Produces: Dynamic Firestore synchronization on `storeService()` and live `scheduleView()` hydration while preserving 100% of UI layout.

- [ ] **Step 1: Update `storeService(status)` in `AdminActivity.kt`**

Construct `ServiceData` from `leaderDraft` (including id generation if blank, title, date, start, end, venue, status, assignments, rehearsal, and songs).
Call `TeamRepository.saveService(currentTeamCode, serviceData, preferences)`.
Keep all existing local UI updates, toast, and `navigate("schedule")`.

- [ ] **Step 2: Update `scheduleView()` in `AdminActivity.kt`**

Hydrate `localServices` from `TeamRepository.getCachedServices(currentTeamCode, preferences)` and trigger non-blocking `TeamRepository.getServices(currentTeamCode, isLeader = true, preferences)`.
Preserve all buttons, filters ("Upcoming", "Past"), cards, and click handlers.

- [ ] **Step 3: Verify build compiles cleanly**

Run: `.\gradlew.bat compileDebugSources`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/syncedn/AdminActivity.kt
git commit -m "feat(admin): connect worship service creation, publishing, and schedule view to Firestore"
```

---

### Task 5: Connect Member Schedule View & Service Details (`MemberActivity.kt`)

**Files:**
- Modify: `app/src/main/java/com/example/syncedn/MemberActivity.kt:1049-1065,1667-1725,1896-1915`

**Interfaces:**
- Consumes: `TeamRepository.getServices(teamCode, isLeader = false)`, `selectedSheetService`, `lineupSongs`
- Produces: Real published service cards in `scheduleView()`, dynamic service header in `serviceView()`, and real assigned members list while preserving 100% of UI layout.

- [ ] **Step 1: Update `storedServices()` in `MemberActivity.kt`**

Replace static reading of `leaderServices` with `TeamRepository.getCachedServices(teamCode, preferences).filter { it.status == "Published" }`.
Trigger background `TeamRepository.getServices(teamCode, isLeader = false, preferences) { refreshIfChanged() }`.

- [ ] **Step 2: Update `scheduleView()` in `MemberActivity.kt`**

When published services exist, render cards for each service dynamically using the exact existing layout:
- Purple gradient date badge (`60x66 dp`, text size 15, bold, white).
- Title (size 13, bold, navy), time (size 11, navy), venue (size 11, navy).
- Arrow symbol.
- On click, populate `selectedSheetService`, `selectedService`, `serviceDate`, `serviceTime`, `serviceVenue`, and navigate to `"service"`.

- [ ] **Step 3: Update `serviceView()` in `MemberActivity.kt`**

Bind the light blue header card to `selectedService`, `serviceDate`, `serviceTime`, `serviceVenue`.
Under *"Assigned Members and their Roles"*, iterate over `selectedSheetService.optJSONArray("assignments")` (or `selectedServiceData.assignments`) and call `memberRow(name, role, badge)` instead of hardcoded `ministryMembers.take(4)`.

- [ ] **Step 4: Verify build compiles cleanly**

Run: `.\gradlew.bat compileDebugSources`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/syncedn/MemberActivity.kt
git commit -m "feat(member): render dynamic published services, role assignments, and lineup"
```

---

### Task 6: Automated Testing & Live Verification on Emulator

**Files:**
- Modify: `app/src/test/java/com/example/syncedn/ExampleUnitTest.kt`

- [ ] **Step 1: Run complete unit test suite**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: 33+ tests pass with 0 failures (`BUILD SUCCESSFUL`).

- [ ] **Step 2: Build and deploy debug APK to emulator**

Run: `.\gradlew.bat installDebug`
Expected: Installed successfully on `emulator-5554`.

- [ ] **Step 3: Live Verification - Leader Workflow**
1. Launch app as Leader (`ryanlloydgenturo@gmail.com`).
2. Navigate to Schedule ➔ New Service.
3. Enter title: "Sunday Celebration Service", date: "2026-10-25", time: "09:00 - 10:30", venue: "Main Sanctuary".
4. Check Member Availability (Available / Unavailable counts).
5. Assign available members to roles (e.g. RL Gents - Bassist).
6. Select songs for lineup and enter rehearsal details.
7. Click "Publish Schedule".
8. Verify service card appears on Leader Schedule screen under "Upcoming".

- [ ] **Step 4: Live Verification - Member Workflow**
1. Switch/Log in as Member (`rlgenturo@gmail.com`).
2. Navigate to Schedule screen.
3. Verify "Sunday Celebration Service" card appears with correct date, time, and venue.
4. Click service card to open `serviceView()`.
5. Verify assigned members show "RL Gents - Bassist" (not hardcoded demo names).
6. Verify "Song Lineups" shows the songs chosen by the Leader.

- [ ] **Step 5: Commit & Final Push**

```bash
git add .
git commit -m "test: verify worship service scheduling, published filtering, and live roster synchronization"
git push origin Backend
```
