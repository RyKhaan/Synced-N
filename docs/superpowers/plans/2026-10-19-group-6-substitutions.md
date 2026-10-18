# Group 6: Service Substitutions & Swap Requests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement full Cloud Firestore-backed service substitution requests and swaps with automatic service schedule roster updates upon acceptance, dynamic team roster selection, offline caching, and real-time status updates in MemberActivity and AdminActivity.

**Architecture:** Layered Android Clean Architecture with an isolated `SubstitutionData` model, `TeamRepository` data access with 15s debounce and local SharedPreferences fallback cache, production-grade `firestore.rules` validators, and UI bindings in `MemberActivity` and `AdminActivity` preserving 100% of existing UI styles and 6dp spacing.

**Tech Stack:** Kotlin, Android SDK 34, Cloud Firestore, Firebase Auth, Google Gson / org.json, SharedPreferences, JUnit4.

**Spec:** docs/superpowers/specs/2026-10-19-group-6-substitutions-design.md

## Global Constraints
- Strict Zero UI Disruption: preserve existing layouts, colors (`navy = Color.rgb(13, 27, 42)`, `purple = Color.rgb(108, 74, 182)`), margins, paddings, and font sizes.
- All Firestore queries and mutations must be encapsulated in `TeamRepository.kt`; UI classes must never execute raw Firestore queries directly.
- Subcollection path: `/teams/{teamCode}/substitutions/{substitutionId}`.
- All operations must support offline graceful fallback using local `SharedPreferences` caching.
- Queries must be capped (`.limit(50)`) and throttled with a 15-second debounce window.

## Review Focus
1. Non-empty substitute selector: ensure the requesting user is excluded from the list of selectable substitutes so members cannot swap with themselves.
2. Character limits and validation: `reason` must be trimmed, non-blank, and capped at 500 characters.
3. Schedule roster update integrity: upon accepting a substitution with a valid `serviceId`, replacing the member in `ServiceData.assignments` must only update the target role and retain all other assignments, rehearsal details, and songs intact.
4. Cold start and offline handling: `announcementsView()` must render cached substitutions immediately from `SharedPreferences` before the async Firestore fetch completes.
5. Role-based action permissions: only the proposed substitute or the Team Leader can accept/decline; only the requester can cancel.

---

### Task 1: Data Model & Serialization Tests (`SubstitutionData.kt`)

**Files:**
- Create: `app/src/main/java/com/example/syncedn/data/model/SubstitutionData.kt`
- Test: `app/src/test/java/com/example/syncedn/ExampleUnitTest.kt`

**Interfaces:**
- Consumes: Kotlin standard types, Java Date / reflection for Firestore Timestamp handling.
- Produces: `SubstitutionData` data class with `toMap(): Map<String, Any>` and `SubstitutionData.fromMap(map: Map<String, Any?>): SubstitutionData`.

- [ ] **Step 1: Write failing unit test in `ExampleUnitTest.kt`**
  Add `testSubstitutionDataSerializationAndDeserialization()` testing serialization to map, deserialization from map, timestamp coercion, and default values.

- [ ] **Step 2: Run test to verify it fails**
  Run: `.\gradlew testDebugUnitTest --tests "com.example.syncedn.ExampleUnitTest.testSubstitutionDataSerializationAndDeserialization"`
  Expected: FAIL with compilation error (Unresolved reference: SubstitutionData).

- [ ] **Step 3: Implement `SubstitutionData.kt` in `app/src/main/java/com/example/syncedn/data/model/SubstitutionData.kt`**
  Implement `SubstitutionData` matching the exact specification in `docs/superpowers/specs/2026-10-19-group-6-substitutions-design.md`.

- [ ] **Step 4: Run test to verify it passes**
  Run: `.\gradlew testDebugUnitTest --tests "com.example.syncedn.ExampleUnitTest.testSubstitutionDataSerializationAndDeserialization"`
  Expected: PASS.

- [ ] **Step 5: Commit**
  ```bash
  git add app/src/main/java/com/example/syncedn/data/model/SubstitutionData.kt app/src/test/java/com/example/syncedn/ExampleUnitTest.kt
  git commit -m "feat(model): add SubstitutionData model and unit tests"
  ```

---

### Task 2: Cloud Firestore Security Rules (`firestore.rules`)

**Files:**
- Modify: `firestore.rules`

**Interfaces:**
- Consumes: Firestore Security Rules v2.
- Produces: `isValidSubstitution()` validator and `match /substitutions/{substitutionId}` subcollection rules under `match /teams/{teamCode}`.

- [ ] **Step 1: Add `isValidSubstitution()` validator and `/substitutions/{substitutionId}` rules to `firestore.rules`**
  Add schema documentation in the header comment, the `isValidSubstitution()` validator in Domain Validators, and the subcollection match block inside `/teams/{teamCode}`.

- [ ] **Step 2: Deploy rules to Firebase**
  Run: `firebase deploy --only firestore:rules`
  Expected: `Deploy complete!`

- [ ] **Step 3: Commit**
  ```bash
  git add firestore.rules
  git commit -m "feat(rules): add isValidSubstitution validator and substitutions subcollection rules"
  ```

---

### Task 3: Repository Caching, Querying & Mutations (`TeamRepository.kt`)

**Files:**
- Modify: `app/src/main/java/com/example/syncedn/data/database/TeamRepository.kt`
- Test: `app/src/test/java/com/example/syncedn/ExampleUnitTest.kt`

**Interfaces:**
- Consumes: `SubstitutionData`, `ServiceData`, `ServiceAssignment`.
- Produces:
  - `saveSubstitutionsLocally(teamCode: String, list: List<SubstitutionData>, preferences: SharedPreferences)`
  - `getCachedSubstitutions(teamCode: String, preferences: SharedPreferences): List<SubstitutionData>`
  - `getSubstitutions(teamCode: String, forceRefresh: Boolean, preferences: SharedPreferences, onComplete: (Result<List<SubstitutionData>>) -> Unit)`
  - `createSubstitutionRequest(sub: SubstitutionData, preferences: SharedPreferences, onComplete: (Result<Unit>) -> Unit)`
  - `updateSubstitutionStatus(teamCode: String, substitutionId: String, newStatus: String, preferences: SharedPreferences, onComplete: (Result<Unit>) -> Unit)`
  - `acceptSubstitutionAndUpdateService(teamCode: String, sub: SubstitutionData, preferences: SharedPreferences, onComplete: (Result<Unit>) -> Unit)`

- [ ] **Step 1: Write failing unit tests in `ExampleUnitTest.kt`**
  Add unit tests:
  - `testTeamRepositorySubstitutionCachingAndFiltering()`
  - `testServiceRosterReplacementOnAccept()`

- [ ] **Step 2: Run test to verify it fails**
  Run: `.\gradlew testDebugUnitTest --tests "com.example.syncedn.ExampleUnitTest.testTeamRepositorySubstitutionCachingAndFiltering"`
  Expected: FAIL with compilation error (Unresolved reference: saveSubstitutionsLocally).

- [ ] **Step 3: Implement repository methods in `TeamRepository.kt`**
  Implement caching, 15-second debounce, query with `.limit(50)`, Firestore write mutations, and service assignment replacement logic.

- [ ] **Step 4: Run unit tests to verify they pass**
  Run: `.\gradlew testDebugUnitTest --tests "com.example.syncedn.ExampleUnitTest.*"`
  Expected: PASS for all tests.

- [ ] **Step 5: Commit**
  ```bash
  git add app/src/main/java/com/example/syncedn/data/database/TeamRepository.kt app/src/test/java/com/example/syncedn/ExampleUnitTest.kt
  git commit -m "feat(repo): add substitution CRUD, offline caching, and service roster sync"
  ```

---

### Task 4: MemberActivity Substitution UI & Dynamic Feed (`MemberActivity.kt`)

**Files:**
- Modify: `app/src/main/java/com/example/syncedn/MemberActivity.kt`

**Interfaces:**
- Consumes: `TeamRepository.getTeamRoster()`, `TeamRepository.createSubstitutionRequest()`, `TeamRepository.getCachedSubstitutions()`, `TeamRepository.getSubstitutions()`, `TeamRepository.acceptSubstitutionAndUpdateService()`, `TeamRepository.updateSubstitutionStatus()`.
- Produces:
  - Dynamic `substitutionView()` with prefilled assignment, real team roster selector (excluding requester), and 500-char input validation.
  - Dynamic `announcementsView()` substitution cards rendering status badges and role-based Accept/Decline/Cancel buttons.

- [ ] **Step 1: Update `substitutionView()` in `MemberActivity.kt`**
  Replace hardcoded demo values with current service assignment details. Dynamically populate the roster selection list using `TeamRepository.getTeamRoster()`, excluding the current user. Hook "Submit Request" to `createSubstitutionRequest()`.

- [ ] **Step 2: Update `announcementsView()` in `MemberActivity.kt`**
  Replace the single hardcoded demo substitution card with dynamic rendering of `TeamRepository.getCachedSubstitutions()`. Add async refresh via `TeamRepository.getSubstitutions()`. Wire Accept, Decline, and Cancel buttons with confirmation dialogs.

- [ ] **Step 3: Verify compilation and run unit tests**
  Run: `.\gradlew testDebugUnitTest`
  Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**
  ```bash
  git add app/src/main/java/com/example/syncedn/MemberActivity.kt
  git commit -m "feat(member): integrate live substitution request form and dynamic announcements feed"
  ```

---

### Task 5: AdminActivity Substitution Oversight UI (`AdminActivity.kt`)

**Files:**
- Modify: `app/src/main/java/com/example/syncedn/AdminActivity.kt`

**Interfaces:**
- Consumes: `TeamRepository.getCachedSubstitutions()`, `TeamRepository.getSubstitutions()`, `TeamRepository.acceptSubstitutionAndUpdateService()`, `TeamRepository.updateSubstitutionStatus()`, `TeamRepository.deleteSubstitution()`.
- Produces: Dynamic leader substitution card list in `AdminActivity.kt:announcementsView()` with leader Accept, Decline, and Delete actions.

- [ ] **Step 1: Update `announcementsView()` in `AdminActivity.kt`**
  Replace the hardcoded demo card with dynamic rendering of `TeamRepository.getCachedSubstitutions()`. Hook up async refresh via `TeamRepository.getSubstitutions()`. Enable leader Accept and Decline actions.

- [ ] **Step 2: Verify compilation and run unit tests**
  Run: `.\gradlew testDebugUnitTest`
  Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**
  ```bash
  git add app/src/main/java/com/example/syncedn/AdminActivity.kt
  git commit -m "feat(admin): integrate live substitutions list and leader controls in AdminActivity"
  ```

---

### Task 6: Live Emulator Verification & E2E Validation

**Files:**
- Test: Live app run on `emulator-5554`
- Create: `C:\Users\RYAN\.gemini\antigravity\brain\4fe9ed8c-3563-46cf-baba-f9edb1581d26\group-6-completion-report.md`

- [ ] **Step 1: Run full unit test suite**
  Run: `.\gradlew testDebugUnitTest`
  Expected: 100% tests pass.

- [ ] **Step 2: Build and install debug APK on `emulator-5554`**
  Run: `.\gradlew installDebug`

- [ ] **Step 3: Perform live end-to-end verification**
  - Verify substitution request creation.
  - Verify real-time rendering in Announcements feed.
  - Verify Accept flow and automatic schedule assignment update.
  - Capture verification screenshots.

- [ ] **Step 4: Create completion report artifact and push to `origin/Backend`**
  Push all commits to `origin/Backend`.
