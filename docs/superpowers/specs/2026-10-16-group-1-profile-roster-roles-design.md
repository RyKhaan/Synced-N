# Design Spec: Group 1 — User Profile, Real Team Roster & Ministry Roles

**Date:** 2026-10-16  
**Status:** In Review / Ready for Implementation  
**Package:** `com.example.syncedn.data` and `com.example.syncedn`

---

## 1. Overview & Objective
Currently in `AdminActivity.kt` and `MemberActivity.kt`:
- User profile details (`fullName`, `phone`, `country`, `language`) are saved only locally in `SharedPreferences`.
- The team roster displayed in the **Teams** screen (Members tab, Role tab) and Home/Settings count ("10 Members" / "15 members") is hardcoded to static mock data (`ministryMembers`).
- Assigning ministry roles (`roleEditView()`), modifying team roles (`teamRolesView()`), removing members, and leaving a team only update local variables.

**Objective:**
Introduce a dedicated **Group 1 backend data layer** (`TeamRepository.kt`, `TeamMember.kt`, `TeamData.kt`) and integrate it into `AdminActivity.kt` and `MemberActivity.kt` so that:
1. User profile updates persist to Cloud Firestore under `/users/{uid}`.
2. The team roster dynamically queries Firestore for users who joined using the active `teamCode`.
3. Ministry roles (e.g. *Worship Leader*, *Vocalist*, *Guitarist*, *Keyboardist*, etc.) are stored in Firestore per user.
4. Leaders can update member roles, customize team roles, and remove members in Firestore.
5. Members can leave the team, which cleans up their team association in Firestore.

---

## 2. Data Models (`com.example.syncedn.data.model`)

### 2.1 Extended `UserProfile.kt`
Add personal profile attributes:
- `fullName: String = ""`
- `phone: String = ""`
- `country: String = "Philippines"`
- `language: String = "English"`
- `ministryRole: String = ""`

### 2.2 `TeamMember.kt`
Represents a team member in the roster:
```kotlin
data class TeamMember(
    val uid: String = "",
    val fullName: String = "",
    val username: String = "",
    val email: String = "",
    val role: String = "Member",       // "Leader" or "Member"
    val ministryRole: String = "",     // e.g. "Vocalist", "Guitarist"
    val teamCode: String = "",
    val joinedAt: Long = 0L
)
```

### 2.3 `TeamData.kt`
Represents team metadata in `/teams/{teamCode}`:
```kotlin
data class TeamData(
    val teamCode: String = "",
    val teamName: String = "",
    val leaderUid: String = "",
    val leaderName: String = "",
    val roles: List<String> = defaultRoles,
    val createdAt: Long = 0L
)
```
Default roles:
`listOf("Worship Leader", "Substitute Leader", "Vocalist", "Guitarist", "Keyboardist", "Bassist", "Drummer", "Sound Operator")`

---

## 3. Backend Repository Layer (`com.example.syncedn.data.database.TeamRepository`)

Singleton / Object providing clean Firestore calls with local fallback:
1. `updateUserProfile(profile: UserProfile, onComplete: (Result<Unit>) -> Unit)`
   - Updates `/users/{uid}` with `fullName`, `username`, `phone`, `country`, `language`, `ministryRole`.
2. `getTeamRoster(teamCode: String, onComplete: (Result<List<TeamMember>>) -> Unit)`
   - Queries `/users` collection where `teamCode == teamCode`.
   - Returns a list of `TeamMember` instances sorted by Leader first, then name.
3. `updateMemberMinistryRole(memberUid: String, newMinistryRole: String, onComplete: (Result<Unit>) -> Unit)`
   - Updates `/users/{memberUid}` field `ministryRole`.
4. `getTeamCustomRoles(teamCode: String, onComplete: (Result<List<String>>) -> Unit)`
   - Reads `roles` list from `/teams/{teamCode}` document; returns default roles if empty.
5. `saveTeamCustomRoles(teamCode: String, roles: List<String>, onComplete: (Result<Unit>) -> Unit)`
   - Updates `/teams/{teamCode}` field `roles`.
6. `removeMemberFromTeam(memberUid: String, onComplete: (Result<Unit>) -> Unit)`
   - Clears `teamCode`, `teamName`, `ministryRole`, sets `role = "Member"` on `/users/{memberUid}`.
7. `leaveTeam(uid: String, onComplete: (Result<Unit>) -> Unit)`
   - Resets user document team fields on `/users/{uid}`.

---

## 4. Frontend / UI Integration

### 4.1 Profile Page (`setupProfile()` in `AdminActivity` & `MemberActivity`)
- On "Save Profile Changes":
  - Save to local `preferences` for instant UI feedback.
  - Call `TeamRepository.updateUserProfile(...)` to sync to Firestore in background.

### 4.2 Teams Screen (`teamsView()`)
- **Members Tab:**
  - Call `TeamRepository.getTeamRoster(teamCode)`.
  - Render actual members from Firestore. Leader has "Admin" badge, members have their actual `ministryRole`.
  - In Leader view, clicking a member opens `roleEditView()`.
- **Role Tab:**
  - Group fetched team members by `ministryRole`.
  - Display actual member counts per role.
- **Home & Settings Member Counts:**
  - Display the real size of `teamRoster` (e.g. `"${roster.size} Members"`).

### 4.3 Leader Role Management & Member Removal
- `roleEditView()`: Fetch available roles via `TeamRepository.getTeamCustomRoles()`, and save selected role to `/users/{memberUid}`.
- `teamRolesView()`: Load existing roles from `/teams/{teamCode}`, allow adding/deleting, save via `saveTeamCustomRoles()`.
- `removeTeamMember()`: List real members, confirm removal, call `removeMemberFromTeam()`.
- `Leave Team`: In both Leader/Member, call `leaveTeam()`, reset local session, and navigate back to `teamChoice`.

---

## 5. Testing & Verification Plan
1. **Unit Tests (`ExampleUnitTest.kt`):**
   - Test `UserProfile` mapping with `fullName`, `phone`, `country`, `language`, and `ministryRole`.
   - Test `TeamMember` mapping from Firestore document map.
   - Test `TeamData` default roles and custom roles serialization.
2. **Build Verification:**
   - Run `./gradlew.bat compileDebugSources`.
   - Run `./gradlew.bat test`.
3. **Emulator / Runtime Check:**
   - Install to emulator and verify profile save and roster display without crashes.
