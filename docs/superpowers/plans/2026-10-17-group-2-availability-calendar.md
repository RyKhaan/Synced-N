# Group 2: Member Availability & Calendar Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sync member unavailability date ranges to Cloud Firestore (`/users/{uid}`), allow members to add and delete multiple unavailable date ranges with calendar visual cues, and dynamically categorize team members as Available or Unavailable for Leader service scheduling.

**Architecture:** Create `UnavailableRange` model in `com.example.syncedn.data.model` and extend `UserProfile` and `TeamMember`. Expand `TeamRepository` to manage unavailable date ranges with in-memory and `SharedPreferences` caching. Update `firestore.rules` to permit `unavailableRanges`. Connect `MemberActivity.kt` for managing multiple date ranges and `AdminActivity.kt` for filtering roster availability by service date.

**Tech Stack:** Kotlin, Cloud Firestore (Firebase Android SDK), Android SharedPreferences, Android UI (dynamic layouts), JUnit 4.

**Spec:** [docs/superpowers/specs/2026-10-17-group-2-availability-calendar-design.md](file:///c:/Users/RYAN/AndroidStudioProjects/Synced1.0/docs/superpowers/specs/2026-10-17-group-2-availability-calendar-design.md)

## Global Constraints
- Keep all Git commits local on `Backend`; do NOT push to remote repository until user explicitly instructs to do so.
- Keep raw Firestore operations isolated in `com.example.syncedn.data.*`; `AdminActivity.kt` and `MemberActivity.kt` must interact solely via repository layers.
- Preserve offline resilience: availability operations must update local preferences and in-memory caches immediately so UI never blocks on network latency.
- Preserve backward compatibility with existing single-range `unavailableStart`/`unavailableEnd` preferences.

## Review Focus
1. User enters end date before start date: validation rejects input and shows error message.
2. Member sets unavailability covering a service date: Leader's `memberAvailabilityView()` immediately reflects "Unavailable" with the member's reason.
3. Member removes an unavailable date range: local cache and Firestore update, and calendar indicator clears.
4. Offline member adds unavailable range: saves to local cache without crash and syncs when online.
5. User with 0 unavailable ranges: Leader's view treats member as "Available".

---

### Task 1: Create `UnavailableRange` Model and Extend `UserProfile` & `TeamMember`

**Files:**
- Create: `app/src/main/java/com/example/syncedn/data/model/UnavailableRange.kt`
- Modify: `app/src/main/java/com/example/syncedn/data/model/UserProfile.kt`
- Modify: `app/src/main/java/com/example/syncedn/data/model/TeamMember.kt`
- Test: `app/src/test/java/com/example/syncedn/ExampleUnitTest.kt`

**Interfaces:**
- Produces:
  - `UnavailableRange(id: String, startDate: String, endDate: String, reason: String, createdAt: Long)`
  - `UnavailableRange.includes(date: String): Boolean`
  - `UserProfile(..., unavailableRanges: List<UnavailableRange>)`
  - `TeamMember(..., unavailableRanges: List<UnavailableRange>)`

- [ ] **Step 1: Write failing unit test for `UnavailableRange` and model extensions**

```kotlin
@Test
fun testUnavailableRangeSerializationAndIncludes() {
    val range = UnavailableRange(
        id = "range1",
        startDate = "2026-10-20",
        endDate = "2026-10-25",
        reason = "Out of town",
        createdAt = 1000L
    )
    val map = range.toMap()
    val restored = UnavailableRange.fromMap(map)
    assertEquals("range1", restored.id)
    assertEquals("2026-10-20", restored.startDate)
    assertEquals("2026-10-25", restored.endDate)
    assertEquals("Out of town", restored.reason)

    assertTrue(range.includes("2026-10-20"))
    assertTrue(range.includes("2026-10-22"))
    assertTrue(range.includes("2026-10-25"))
    assertFalse(range.includes("2026-10-19"))
    assertFalse(range.includes("2026-10-26"))
}

@Test
fun testUserProfileAndTeamMemberWithUnavailableRanges() {
    val range = UnavailableRange("r1", "2026-11-01", "2026-11-05", "Medical leave")
    val profile = UserProfile(
        uid = "u1",
        fullName = "Test User",
        unavailableRanges = listOf(range)
    )
    val restoredProfile = UserProfile.fromMap(profile.toMap())
    assertEquals(1, restoredProfile.unavailableRanges.size)
    assertEquals("Medical leave", restoredProfile.unavailableRanges[0].reason)

    val member = TeamMember(
        uid = "u1",
        fullName = "Test Member",
        unavailableRanges = listOf(range)
    )
    val restoredMember = TeamMember.fromMap(member.toMap())
    assertEquals(1, restoredMember.unavailableRanges.size)
    assertEquals("r1", restoredMember.unavailableRanges[0].id)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat test --tests "com.example.syncedn.ExampleUnitTest.testUnavailableRangeSerializationAndIncludes"`
Expected: FAIL (unresolved reference `UnavailableRange`)

- [ ] **Step 3: Implement `UnavailableRange.kt` and update `UserProfile.kt` & `TeamMember.kt`**

Create `UnavailableRange.kt` with `toMap()`, `fromMap()`, and `includes(date: String)`.
Update `UserProfile.kt` and `TeamMember.kt` to include `val unavailableRanges: List<UnavailableRange> = emptyList()`, and support it in `toMap()` and `fromMap()`.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat test --tests "com.example.syncedn.ExampleUnitTest.testUnavailableRangeSerializationAndIncludes" --tests "com.example.syncedn.ExampleUnitTest.testUserProfileAndTeamMemberWithUnavailableRanges"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/syncedn/data/model/ app/src/test/java/com/example/syncedn/ExampleUnitTest.kt
git commit -m "feat(model): add UnavailableRange and extend UserProfile and TeamMember"
```

---

### Task 2: Update Cloud Firestore Security Rules for `unavailableRanges`

**Files:**
- Modify: `firestore.rules`

**Interfaces:**
- Produces: Updated `isValidUser` allowing `'unavailableRanges'` field of type `list` with max size 100.

- [ ] **Step 1: Inspect and update `firestore.rules`**

Add `'unavailableRanges'` to `hasOnly` fields in `isValidUser`:
```javascript
'ministryRole', 'unavailableRanges', 'createdAt'
```
Add rule constraint:
```javascript
(!('unavailableRanges' in data) || (data.unavailableRanges is list && data.unavailableRanges.size() <= 100))
```

- [ ] **Step 2: Verify rules consistency**

Check syntax and ensure `isValidUser` preserves all existing security rules for `users/{userId}`.

- [ ] **Step 3: Commit**

```bash
git add firestore.rules
git commit -m "feat(security): permit unavailableRanges list in firestore user rules"
```

---

### Task 3: Implement `TeamRepository` Availability Methods and Caching

**Files:**
- Modify: `app/src/main/java/com/example/syncedn/data/database/TeamRepository.kt`
- Test: `app/src/test/java/com/example/syncedn/ExampleUnitTest.kt`

**Interfaces:**
- Produces:
  - `TeamRepository.addUnavailableRange(uid: String, range: UnavailableRange, preferences: SharedPreferences?, onComplete: (Result<Unit>) -> Unit)`
  - `TeamRepository.removeUnavailableRange(uid: String, rangeId: String, preferences: SharedPreferences?, onComplete: (Result<Unit>) -> Unit)`
  - `TeamRepository.getMemberUnavailableRanges(uid: String, preferences: SharedPreferences?): List<UnavailableRange>`
  - `TeamRepository.checkTeamAvailabilityForDate(teamCode: String, targetDate: String): Pair<List<TeamMember>, List<Pair<TeamMember, String>>>` (Available members, Unavailable members with reasons)

- [ ] **Step 1: Write failing unit test for `TeamRepository` availability matching**

```kotlin
@Test
fun testTeamAvailabilityCalculationForDate() {
    val range1 = UnavailableRange("r1", "2026-10-20", "2026-10-25", "Family reunion")
    val member1 = TeamMember(uid = "m1", fullName = "Alice", teamCode = "TEAM99", unavailableRanges = listOf(range1))
    val member2 = TeamMember(uid = "m2", fullName = "Bob", teamCode = "TEAM99", unavailableRanges = emptyList())

    TeamRepository.cacheMember(member1)
    TeamRepository.cacheMember(member2)

    val (available, unavailable) = TeamRepository.checkTeamAvailabilityForDate("TEAM99", "2026-10-22")
    assertEquals(1, available.size)
    assertEquals("Bob", available[0].fullName)
    assertEquals(1, unavailable.size)
    assertEquals("Alice", unavailable[0].first.fullName)
    assertEquals("Family reunion", unavailable[0].second)

    val (allAvailable, noneUnavailable) = TeamRepository.checkTeamAvailabilityForDate("TEAM99", "2026-10-30")
    assertEquals(2, allAvailable.size)
    assertEquals(0, noneUnavailable.size)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat test --tests "com.example.syncedn.ExampleUnitTest.testTeamAvailabilityCalculationForDate"`
Expected: FAIL (unresolved reference `checkTeamAvailabilityForDate`)

- [ ] **Step 3: Implement availability functions in `TeamRepository.kt`**

- In `TeamRepository`:
  - Cache `userUnavailableRanges` map keyed by `uid`.
  - Implement `addUnavailableRange`: updates in-memory cache, persists JSON list to `preferences` under `"user_unavailable_ranges_$uid"`, and calls Firestore `update("unavailableRanges", rangesList)`.
  - Implement `removeUnavailableRange`: filters out range by id, updates cache, preferences, and Firestore.
  - Implement `getMemberUnavailableRanges`: retrieves from cache or preferences.
  - Implement `checkTeamAvailabilityForDate`: filters cached roster for `teamCode` against `targetDate`.
  - Update `syncFromPreferences` and roster caching to load and associate `unavailableRanges`.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat test --tests "com.example.syncedn.ExampleUnitTest.testTeamAvailabilityCalculationForDate"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/syncedn/data/database/TeamRepository.kt app/src/test/java/com/example/syncedn/ExampleUnitTest.kt
git commit -m "feat(repo): add unavailable ranges management and availability checking"
```

---

### Task 4: Connect Multiple Date Ranges and Calendar Sync in `MemberActivity.kt`

**Files:**
- Modify: `app/src/main/java/com/example/syncedn/MemberActivity.kt`

**Interfaces:**
- Consumes: `TeamRepository.getMemberUnavailableRanges()`, `TeamRepository.addUnavailableRange()`, `TeamRepository.removeUnavailableRange()`.

- [ ] **Step 1: Update `availabilityView()` in `MemberActivity.kt`**

- Add input length restrictions on Reason using `ValidationUtils.MAX_TITLE_LENGTH` or `ValidationUtils.MAX_NAME_LENGTH`.
- Form section on top:
  - Starts DatePicker, Ends DatePicker, Reason input.
  - Button: **"+ Add Unavailable Dates"**.
  - Validation: Ensure reason is not blank, end date is on or after start date.
- Dynamic list section below:
  - Render each active range from `TeamRepository.getMemberUnavailableRanges()`.
  - Each item displays: `📅 {start} – {end} • {reason}` and a red `✕` button.
  - Clicking `✕` calls `TeamRepository.removeUnavailableRange()`, shows toast, and refreshes view.

- [ ] **Step 2: Update `calendarView()` in `MemberActivity.kt`**

- Fetch all active ranges for the current member.
- In calendar month grid:
  - If a day falls within ANY active range, display a red indicator dot (`•`).
- In daily details card below grid:
  - If selected day has an active unavailable range, display red panel: `"Unavailable: {reason}"` and `"{start} – {end}"`.
  - If not, display `"Available"`.

- [ ] **Step 3: Run unit tests and compilation check**

Run: `.\gradlew.bat compileDebugKotlin`
Expected: SUCCESS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/syncedn/MemberActivity.kt
git commit -m "feat(member): support multiple unavailable date ranges and calendar indicators"
```

---

### Task 5: Connect Leader `memberAvailabilityView()` in `AdminActivity.kt`

**Files:**
- Modify: `app/src/main/java/com/example/syncedn/AdminActivity.kt`

**Interfaces:**
- Consumes: `TeamRepository.checkTeamAvailabilityForDate(teamCode, serviceDate)`.

- [ ] **Step 1: Update `memberAvailabilityView()` in `AdminActivity.kt`**

- Read service date from `leaderDraft.optString("date")`.
- Call `TeamRepository.checkTeamAvailabilityForDate(activeCode, serviceDate)`.
- Render:
  - Service Summary header (Service Title, Date, Venue).
  - Tab / Section counts:
    - **Available ({count})**: Lists available members with their avatar, name, and ministry role.
    - **Unavailable ({count})**: Lists unavailable members with their avatar, name, ministry role, and conflict notice (e.g. `"Unavailable: {reason} ({start} – {end})"`).
- In `serviceAssignmentsView()`, highlight or warn if assigning an unavailable member.

- [ ] **Step 2: Run unit tests and build check**

Run: `.\gradlew.bat compileDebugKotlin`
Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/syncedn/AdminActivity.kt
git commit -m "feat(admin): connect dynamic member availability checking for worship service date"
```

---

### Task 6: Comprehensive Verification & Test Suite

**Files:**
- Modify/Verify: `app/src/test/java/com/example/syncedn/ExampleUnitTest.kt`

- [ ] **Step 1: Run full unit test suite**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: BUILD SUCCESSFUL with all tests passing (25+ tests, 0 failures).

- [ ] **Step 2: Verify git status and commit cleanliness**

Run: `git status`
Expected: Clean working tree on branch `Backend`.
