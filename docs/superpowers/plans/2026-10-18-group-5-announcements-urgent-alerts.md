# Group 5: Team Announcements & Urgent Alerts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Gawing live sa Cloud Firestore ang pamamahala at pagbabasa ng announcements ng ministry para sa mga pinuno at miyembro, kasama ang Urgent Alert Banner sa Home dashboard at full Leader lifecycle (post, edit, delete).

**Architecture:** Ang mga anunsyo ay nakalagay sa subcollection `/teams/{teamCode}/announcements/{announcementId}`. Isang `AnnouncementData` Kotlin model ang humahawak sa serialization/deserialization. Ang `TeamRepository` ay may in-memory at SharedPreferences caching na may 15-segundong debounce laban sa redundant reads. Ang UI sa `AdminActivity` at `MemberActivity` ay magre-render ng dynamic announcements at magpapakita ng sleek Urgent Alert Banner sa Home dashboard kapag may active alert.

**Tech Stack:** Android Kotlin, Cloud Firestore (Android SDK), Firebase Security Rules, SharedPreferences Caching, JUnit 4.

**Spec:** [docs/superpowers/specs/2026-10-18-group-5-announcements-urgent-alerts-design.md](file:///c:/Users/RYAN/AndroidStudioProjects/Synced1.0/docs/superpowers/specs/2026-10-18-group-5-announcements-urgent-alerts-design.md)

## Global Constraints

- Lahat ng existing layout, fonts, colors (navy `#0D1B2A`, purple `#6C4AB6`), 6dp button margins, at UI widget structures ay 100% pananatilihin nang walang regression (Strict Zero UI Disruption Rule).
- Lahat ng Firestore queries para sa announcements ay dapat ma-isolate sa `TeamRepository.kt`; bawal magkaroon ng raw Firestore collection calls sa UI activities.
- Lahat ng data operations ay may local cache at fallback upang laging gumana ang app kahit offline o mabagal ang internet.

## Review Focus

1. **Empty / missing author or timestamps on Firestore read:** Dapat may default fallback sa `System.currentTimeMillis()` at `""` upang hindi mag-crash ang JSON o Map parser.
2. **Leader delete confirmation:** Kapag pinindot ang Delete sa anunsyo, kailangang magpakita ng confirmation dialog bago burahin sa Firestore upang maiwasan ang accidental deletion.
3. **Active Urgent Banner condition:** Ang Urgent Banner sa Home dashboard ay dapat lumitaw LAMANG kapag mayroong kahit isang announcement na may `urgent == true`. Kung walang urgent announcement, hindi magpapakita ng blankong space.
4. **Offline fallback on cold start:** Kung walang internet pagkabukas ng app, dapat agad na i-render ang dating naka-cache na announcements mula sa SharedPreferences nang walang loading stall.
5. **Role-based security enforcement:** Tanging ang Leader ng team (`isLeaderOfTeam(teamCode)`) ang makakapag-create, update, at delete; ang mga miyembro ay read-only.

---

### Task 1: AnnouncementData Model & Unit Tests

**Files:**
- Create: `app/src/main/java/com/example/syncedn/data/model/AnnouncementData.kt`
- Modify: `app/src/test/java/com/example/syncedn/ExampleUnitTest.kt`

**Interfaces:**
- Produces: `AnnouncementData(id, teamCode, title, message, urgent, author, createdBy, createdAt, updatedAt)`
- Methods: `toMap(): Map<String, Any>`, `AnnouncementData.fromMap(map: Map<String, Any?>): AnnouncementData`

- [ ] **Step 1: Write the failing unit test in `ExampleUnitTest.kt`**
```kotlin
@Test
fun testAnnouncementDataSerializationAndDeserialization() {
    val announcement = com.example.syncedn.data.model.AnnouncementData(
        id = "ann_123456",
        teamCode = "NGK8VN",
        title = "Rehearsal Rescheduled",
        message = "Please be informed that Saturday rehearsal is moved to 4 PM.",
        urgent = true,
        author = "RYAN LLOYD",
        createdBy = "h9yRCswmV1f0Pnia2EnxDv4x4X43",
        createdAt = 1792330000000L,
        updatedAt = 1792330000000L
    )
    val map = announcement.toMap()
    assertEquals("ann_123456", map["id"])
    assertEquals("NGK8VN", map["teamCode"])
    assertEquals("Rehearsal Rescheduled", map["title"])
    assertEquals(true, map["urgent"])

    val restored = com.example.syncedn.data.model.AnnouncementData.fromMap(map)
    assertEquals(announcement.id, restored.id)
    assertEquals(announcement.teamCode, restored.teamCode)
    assertEquals(announcement.title, restored.title)
    assertEquals(announcement.message, restored.message)
    assertEquals(announcement.urgent, restored.urgent)
    assertEquals(announcement.author, restored.author)
    assertEquals(announcement.createdBy, restored.createdBy)
    assertEquals(announcement.createdAt, restored.createdAt)
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `.\gradlew.bat testDebugUnitTest --tests com.example.syncedn.ExampleUnitTest.testAnnouncementDataSerializationAndDeserialization`
Expected: FAIL (unresolved reference: AnnouncementData).

- [ ] **Step 3: Implement `AnnouncementData.kt` in `app/src/main/java/com/example/syncedn/data/model/AnnouncementData.kt`**
Create data class with `toMap()` and `fromMap(map: Map<String, Any?>)` with timestamp coercion.

- [ ] **Step 4: Run test to verify it passes**
Run: `.\gradlew.bat testDebugUnitTest --tests com.example.syncedn.ExampleUnitTest.testAnnouncementDataSerializationAndDeserialization`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/example/syncedn/data/model/AnnouncementData.kt app/src/test/java/com/example/syncedn/ExampleUnitTest.kt
git commit -m "feat(model): add AnnouncementData model and serialization tests"
```

---

### Task 2: Cloud Firestore Security Rules for Announcements Subcollection

**Files:**
- Modify: `firestore.rules`

**Interfaces:**
- Consumes: `AnnouncementData` schema
- Produces: `isValidAnnouncement()` validator and `match /teams/{teamCode}/announcements/{announcementId}` rules

- [ ] **Step 1: Add `isValidAnnouncement` helper function in `firestore.rules`**
Define field constraints:
  - allowed keys: `['id', 'teamCode', 'title', 'message', 'urgent', 'author', 'createdBy', 'createdAt', 'updatedAt']`
  - required keys: `['id', 'teamCode', 'title', 'message', 'createdBy']`
  - title max size 120, message max size 2000, urgent is bool, createdBy is string.

- [ ] **Step 2: Add subcollection match rule under `/teams/{teamCode}` in `firestore.rules`**
```javascript
match /announcements/{announcementId} {
  allow read: if isAuthenticated() && isSameTeam(teamCode);
  allow create: if isLeaderOfTeam(teamCode) &&
                   isValidAnnouncement(request.resource.data, announcementId, teamCode) &&
                   request.resource.data.createdBy == request.auth.uid;
  allow update: if isLeaderOfTeam(teamCode) &&
                   isValidAnnouncement(request.resource.data, announcementId, teamCode) &&
                   areImmutableFieldsUnchanged(['id', 'teamCode', 'createdBy', 'createdAt']);
  allow delete: if isLeaderOfTeam(teamCode);
}
```

- [ ] **Step 3: Validate and deploy rules using Firebase CLI**
Run: `firebase deploy --only firestore:rules`
Expected: Output showing `rules file firestore.rules compiled successfully` and `released rules firestore.rules to cloud.firestore`.

- [ ] **Step 4: Commit**
```bash
git add firestore.rules
git commit -m "feat(security): add firestore security rules for team announcements subcollection"
```

---

### Task 3: TeamRepository Announcements Caching & Firestore Sync

**Files:**
- Modify: `app/src/main/java/com/example/syncedn/data/database/TeamRepository.kt`
- Modify: `app/src/test/java/com/example/syncedn/ExampleUnitTest.kt`

**Interfaces:**
- Produces:
  - `getCachedAnnouncements(teamCode: String, preferences: SharedPreferences?): List<AnnouncementData>`
  - `saveAnnouncementsLocally(teamCode: String, list: List<AnnouncementData>, preferences: SharedPreferences?)`
  - `getAnnouncements(teamCode: String, forceRefresh: Boolean, preferences: SharedPreferences?, onResult: (Result<List<AnnouncementData>>) -> Unit)`
  - `saveAnnouncement(teamCode: String, announcement: AnnouncementData, preferences: SharedPreferences?, onComplete: ((Result<AnnouncementData>) -> Unit)?)`
  - `deleteAnnouncement(teamCode: String, announcementId: String, preferences: SharedPreferences?, onComplete: ((Result<Boolean>) -> Unit)?)`

- [ ] **Step 1: Write unit test in `ExampleUnitTest.kt`**
Test `getCachedAnnouncements`, `saveAnnouncementsLocally`, optimistic sorting by `createdAt` descending.

- [ ] **Step 2: Run test to verify it fails**
Run: `.\gradlew.bat testDebugUnitTest --tests com.example.syncedn.ExampleUnitTest.testTeamRepositoryAnnouncementCachingAndSorting`
Expected: FAIL (unresolved references in TeamRepository).

- [ ] **Step 3: Implement announcement repository methods in `TeamRepository.kt`**
Implement `getCachedAnnouncements`, `saveAnnouncementsLocally`, `getAnnouncements` (with 15s debounce), `saveAnnouncement`, and `deleteAnnouncement`.

- [ ] **Step 4: Run test to verify it passes**
Run: `.\gradlew.bat testDebugUnitTest --tests com.example.syncedn.ExampleUnitTest.testTeamRepositoryAnnouncementCachingAndSorting`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/example/syncedn/data/database/TeamRepository.kt app/src/test/java/com/example/syncedn/ExampleUnitTest.kt
git commit -m "feat(repo): add announcements caching, firestore sync, and debounce in TeamRepository"
```

---

### Task 4: AdminActivity Live Announcements, Post/Edit Form, and Delete Dialog

**Files:**
- Modify: `app/src/main/java/com/example/syncedn/AdminActivity.kt`

**Interfaces:**
- Consumes: `TeamRepository.getAnnouncements`, `TeamRepository.saveAnnouncement`, `TeamRepository.deleteAnnouncement`
- Produces:
  - Dynamic `announcementsView()` rendering live cards with `⋮` menu (Edit, Delete).
  - Dynamic `announcementEditView()` pre-filled on edit mode and saving to Firestore.
  - Delete confirmation dialog with instant UI removal.
  - `announcementHomeTile()` displaying live count.
  - `urgentAlertBanner()` on `homeView()` rendering alert banner if any announcement has `urgent == true`.

- [ ] **Step 1: Wire `announcementsView()` in `AdminActivity.kt` to `TeamRepository.getAnnouncements`**
Replace `preferences.getString("leaderPosts")` with dynamic `TeamRepository.getAnnouncements(activeCode)`.
Add `⋮` popup menu on each announcement card with "Edit" and "Delete".

- [ ] **Step 2: Connect `announcementEditView()` to `TeamRepository.saveAnnouncement`**
Support `editingAnnouncement: AnnouncementData?` state.
When clicking "Publish", call `TeamRepository.saveAnnouncement()`, show success toast, and navigate back to `announcements`.

- [ ] **Step 3: Connect Delete action to `TeamRepository.deleteAnnouncement` with confirmation dialog**
Prompt: "Are you sure you want to delete this announcement?" with "Delete" and "Cancel" buttons.
Call `TeamRepository.deleteAnnouncement()`, update local list, and refresh view.

- [ ] **Step 4: Add `urgentAlertBanner()` to `homeView()` and update `announcementHomeTile()`**
Show banner card at top of Home dashboard when an urgent announcement exists. Clicking the banner opens `announcements`.

- [ ] **Step 5: Verify build passes**
Run: `.\gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**
```bash
git add app/src/main/java/com/example/syncedn/AdminActivity.kt
git commit -m "feat(admin): connect announcements management, edit/delete actions, and urgent home banner"
```

---

### Task 5: MemberActivity Live Announcements & Home Alert Banner

**Files:**
- Modify: `app/src/main/java/com/example/syncedn/MemberActivity.kt`

**Interfaces:**
- Consumes: `TeamRepository.getAnnouncements`, `TeamRepository.getCachedAnnouncements`
- Produces:
  - Dynamic read-only `announcementsView()` in `MemberActivity.kt`.
  - Prominent `🚨 URGENT ALERT` badge on urgent cards.
  - Dynamic `announcementHomeTile()` and `urgentAlertBanner()` on Member's `homeView()`.

- [ ] **Step 1: Wire `announcementsView()` in `MemberActivity.kt` to `TeamRepository.getAnnouncements`**
Replace `preferences.getString("leaderPosts")` with live announcements from `TeamRepository.getAnnouncements(activeCode)`.

- [ ] **Step 2: Add `urgentAlertBanner()` to Member's `homeView()` and update `announcementHomeTile()`**
Check if any announcement has `urgent == true`. If present, render the compact banner at the top of the dashboard.

- [ ] **Step 3: Verify build passes**
Run: `.\gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/com/example/syncedn/MemberActivity.kt
git commit -m "feat(member): render live announcements and home urgent alert banner in MemberActivity"
```

---

### Task 6: End-to-End Verification on Android Emulator & Git Integration

**Files:**
- Test files and activities on live emulator.

- [ ] **Step 1: Run all unit tests**
Run: `.\gradlew.bat testDebugUnitTest`
Expected: 100% tests passing (all 47+ tests).

- [ ] **Step 2: Build and install debug APK onto emulator**
Run: `.\gradlew.bat installDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Live Emulator Verification**
1. Open AdminActivity as Leader (`ryanlloydgenturo@gmail.com`).
2. Go to Announcements tab -> Tap `+ Post Announcement`.
3. Enter Title: "Special Ministry Meeting", Message: "All musicians and vocalists please attend at 3 PM.", check "Mark as Urgent Alert".
4. Tap Publish -> Verify announcement is listed with `🚨 URGENT ALERT` tag.
5. Go back to Home -> Verify the **🚨 Urgent Alert Banner** appears at the top of the dashboard.
6. Tap the banner -> Verify it navigates directly to Announcements.
7. Test Edit: Change message to "...at 4 PM.", tap Publish, verify update.
8. Test Delete: Tap `⋮` -> "Delete" -> Confirm. Verify announcement and banner disappear.

- [ ] **Step 4: Final Git Push**
Run: `git push origin Backend`
Expected: Successfully pushed to GitHub.
