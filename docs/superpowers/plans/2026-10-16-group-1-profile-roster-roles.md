# Group 1: User Profile, Real Team Roster & Ministry Roles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist user profile details to Firestore, dynamically display the real team roster and ministry roles based on teamCode, and connect role assignment and member management to Cloud Firestore.

**Architecture:** Create data models (`TeamMember`, `TeamData`, extended `UserProfile`) and a centralized `TeamRepository` singleton in `com.example.syncedn.data.database`. Wire `AdminActivity` and `MemberActivity` to consume `TeamRepository` rather than hardcoded mock data, maintaining offline/cached resilience.

**Tech Stack:** Kotlin, Cloud Firestore (Firebase Android SDK), Android SharedPreferences, JUnit 4.

**Spec:** [docs/superpowers/specs/2026-10-16-group-1-profile-roster-roles-design.md](file:///c:/Users/RYAN/AndroidStudioProjects/Synced1.0/docs/superpowers/specs/2026-10-16-group-1-profile-roster-roles-design.md)

## Global Constraints
- Do NOT push to remote Git repository until the user confirms. Keep commits local on `Backend`.
- Keep raw Firestore database logic out of `AdminActivity.kt` and `MemberActivity.kt`; all database interactions must pass through `com.example.syncedn.data.*`.
- Retain non-blocking operations and timeout guards to ensure UI never hangs on network latency.
- Preserve backward compatibility with existing `SharedPreferences` keys.

## Review Focus
1. User saves profile with blank full name: validation prevents submission and preserves previous name.
2. Team roster query when user is offline: returns cached members or graceful fallback without crashing.
3. Member role change: updates Firestore and immediately refreshes local in-memory view.
4. Member removal or leaving team: resets user's team state in Firestore and local preferences, routing back to `teamChoice`.
5. Empty team roster or custom roles list: falls back to default role catalog and shows the current user as primary member.

---

### Task 1: Extend `UserProfile` and Create `TeamMember` & `TeamData` Models

**Files:**
- Modify: `app/src/main/java/com/example/syncedn/data/model/UserProfile.kt`
- Create: `app/src/main/java/com/example/syncedn/data/model/TeamMember.kt`
- Create: `app/src/main/java/com/example/syncedn/data/model/TeamData.kt`
- Test: `app/src/test/java/com/example/syncedn/ExampleUnitTest.kt`

**Interfaces:**
- Produces:
  - `UserProfile(fullName, phone, country, language, ministryRole)`
  - `TeamMember(uid, fullName, username, email, role, ministryRole, teamCode, joinedAt)`
  - `TeamData(teamCode, teamName, leaderUid, leaderName, roles, createdAt)`

- [ ] **Step 1: Write the failing unit tests for models**

```kotlin
@Test
fun testUserProfileExtendedFieldsSerialization() {
    val profile = UserProfile(
        uid = "uid123",
        username = "johndoe",
        email = "john@example.com",
        fullName = "John Doe",
        phone = "09123456789",
        country = "Philippines",
        language = "English",
        ministryRole = "Guitarist"
    )
    val map = profile.toMap()
    val restored = UserProfile.fromMap(map)
    assertEquals("John Doe", restored.fullName)
    assertEquals("09123456789", restored.phone)
    assertEquals("Guitarist", restored.ministryRole)
}

@Test
fun testTeamMemberAndTeamDataMapping() {
    val member = TeamMember(
        uid = "u1",
        fullName = "Jane Doe",
        username = "janed",
        email = "jane@example.com",
        role = "Member",
        ministryRole = "Vocalist",
        teamCode = "TEST01",
        joinedAt = 1000L
    )
    val map = member.toMap()
    val restored = TeamMember.fromMap(map)
    assertEquals("Jane Doe", restored.fullName)
    assertEquals("Vocalist", restored.ministryRole)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat test --tests "com.example.syncedn.ExampleUnitTest.testUserProfileExtendedFieldsSerialization"`
Expected: FAIL (unresolved references for new properties)

- [ ] **Step 3: Implement model classes**

Update `UserProfile.kt`, and create `TeamMember.kt` and `TeamData.kt` in `app/src/main/java/com/example/syncedn/data/model/`.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat test --tests "com.example.syncedn.ExampleUnitTest.testUserProfileExtendedFieldsSerialization"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/syncedn/data/model/ app/src/test/java/com/example/syncedn/ExampleUnitTest.kt
git commit -m "feat(model): extend UserProfile and add TeamMember and TeamData models"
```

---

### Task 2: Implement `TeamRepository` Backend Layer

**Files:**
- Create: `app/src/main/java/com/example/syncedn/data/database/TeamRepository.kt`
- Test: `app/src/test/java/com/example/syncedn/ExampleUnitTest.kt`

**Interfaces:**
- Consumes: `UserProfile`, `TeamMember`, `TeamData`
- Produces:
  - `TeamRepository.updateUserProfile(profile, onComplete)`
  - `TeamRepository.getTeamRoster(teamCode, onComplete)`
  - `TeamRepository.updateMemberMinistryRole(memberUid, newRole, onComplete)`
  - `TeamRepository.getTeamCustomRoles(teamCode, onComplete)`
  - `TeamRepository.saveTeamCustomRoles(teamCode, roles, onComplete)`
  - `TeamRepository.removeMemberFromTeam(memberUid, onComplete)`
  - `TeamRepository.leaveTeam(uid, onComplete)`

- [ ] **Step 1: Write unit tests for `TeamRepository`**

```kotlin
@Test
fun testTeamRepositoryRosterAndRoleCaching() {
    TeamRepository.clearCache()
    TeamRepository.cacheMember(TeamMember(uid = "u1", fullName = "Leader Ryan", role = "Leader", ministryRole = "Worship Leader", teamCode = "CODE01"))
    TeamRepository.cacheMember(TeamMember(uid = "u2", fullName = "Member Dave", role = "Member", ministryRole = "Bassist", teamCode = "CODE01"))
    
    val roster = TeamRepository.getCachedRoster("CODE01")
    assertEquals(2, roster.size)
    assertEquals("Leader Ryan", roster[0].fullName)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat test --tests "com.example.syncedn.ExampleUnitTest.testTeamRepositoryRosterAndRoleCaching"`
Expected: FAIL (unresolved `TeamRepository`)

- [ ] **Step 3: Implement `TeamRepository.kt`**

Create `app/src/main/java/com/example/syncedn/data/database/TeamRepository.kt` with Firestore calls and local memory fallback.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat test --tests "com.example.syncedn.ExampleUnitTest.testTeamRepositoryRosterAndRoleCaching"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/syncedn/data/database/TeamRepository.kt app/src/test/java/com/example/syncedn/ExampleUnitTest.kt
git commit -m "feat(backend): add TeamRepository for profile, roster, and role management"
```

---

### Task 3: Profile Screen Integration with Firestore

**Files:**
- Modify: `app/src/main/java/com/example/syncedn/AdminActivity.kt:1378-1422`
- Modify: `app/src/main/java/com/example/syncedn/MemberActivity.kt:1320-1364`
- Test: `app/src/test/java/com/example/syncedn/ExampleUnitTest.kt`

**Interfaces:**
- Consumes: `TeamRepository.updateUserProfile(profile, onComplete)`

- [ ] **Step 1: Write unit test verifying profile update mapping**

```kotlin
@Test
fun testProfileUpdateCreatesValidUserProfile() {
    val uid = "test_user_1"
    val profile = UserProfile(
        uid = uid,
        fullName = "Ryan Lloyd Genturo",
        username = "Yanyan",
        phone = "09123456789",
        country = "Philippines",
        language = "English"
    )
    assertEquals("Ryan Lloyd Genturo", profile.fullName)
    assertEquals("Yanyan", profile.username)
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `.\gradlew.bat test --tests "com.example.syncedn.ExampleUnitTest.testProfileUpdateCreatesValidUserProfile"`
Expected: PASS

- [ ] **Step 3: Update `setupProfile()` in `AdminActivity.kt` and `MemberActivity.kt`**

Connect `saveProfileButton` to call `TeamRepository.updateUserProfile(...)` while updating local `preferences` simultaneously.

- [ ] **Step 4: Verify build and tests pass**

Run: `.\gradlew.bat compileDebugSources test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/syncedn/AdminActivity.kt app/src/main/java/com/example/syncedn/MemberActivity.kt
git commit -m "feat(profile): sync user profile updates to Cloud Firestore via TeamRepository"
```

---

### Task 4: Dynamic Team Roster and Roles Display in Teams Screen

**Files:**
- Modify: `app/src/main/java/com/example/syncedn/AdminActivity.kt:2434-2504`
- Modify: `app/src/main/java/com/example/syncedn/MemberActivity.kt:1910-1972`

**Interfaces:**
- Consumes: `TeamRepository.getTeamRoster(teamCode, onComplete)`

- [ ] **Step 1: Update `teamsView()` to fetch and display dynamic roster**

Replace static `ministryMembers` references with dynamic `teamRoster` loaded via `TeamRepository.getTeamRoster(activeCode)`.
Populate **Members** tab and **Role** tab from the real roster list.

- [ ] **Step 2: Update member count labels in Home and Settings**

Bind the team card subtitle (`"${roster.size} Members"`) to the fetched roster size.

- [ ] **Step 3: Run compilation and tests**

Run: `.\gradlew.bat compileDebugSources test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/syncedn/AdminActivity.kt app/src/main/java/com/example/syncedn/MemberActivity.kt
git commit -m "feat(teams): connect team roster and role groupings to real Firestore members"
```

---

### Task 5: Leader Role Management, Member Removal & Leave Team Integration

**Files:**
- Modify: `app/src/main/java/com/example/syncedn/AdminActivity.kt:1887-1951, 2636-2659`
- Modify: `app/src/main/java/com/example/syncedn/MemberActivity.kt:2074-2082`

**Interfaces:**
- Consumes:
  - `TeamRepository.updateMemberMinistryRole`
  - `TeamRepository.getTeamCustomRoles`
  - `TeamRepository.saveTeamCustomRoles`
  - `TeamRepository.removeMemberFromTeam`
  - `TeamRepository.leaveTeam`

- [ ] **Step 1: Hook up `roleEditView()`, `teamRolesView()`, and `removeTeamMember()` in `AdminActivity.kt`**

Connect role edits, custom roles list, and member removal dialog to `TeamRepository`.

- [ ] **Step 2: Hook up `leaveTeam` in `AdminActivity.kt` and `MemberActivity.kt`**

Call `TeamRepository.leaveTeam()`, clear local team session preferences, and navigate to `teamChoice`.

- [ ] **Step 3: Run compilation and test verification**

Run: `.\gradlew.bat compileDebugSources test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/syncedn/AdminActivity.kt app/src/main/java/com/example/syncedn/MemberActivity.kt
git commit -m "feat(team-mgmt): connect role assignments, custom roles, member removal, and leave team"
```

---

### Task 6: Full Verification & Emulator Verification

**Files:**
- Test: All files modified across Tasks 1-5

- [ ] **Step 1: Execute full test suite**

Run: `.\gradlew.bat test`
Expected: 0 failures, `BUILD SUCCESSFUL`

- [ ] **Step 2: Install debug build to emulator**

Run: `.\gradlew.bat installDebug`
Expected: Installed successfully on `emulator-5554`

- [ ] **Step 3: Capture emulator screenshot and verify**

Take screenshot via `adb` and verify that the app launches and displays cleanly.
