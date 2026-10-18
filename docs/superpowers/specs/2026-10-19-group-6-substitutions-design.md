# Group 6: Service Substitutions & Swap Requests Design Specification

## 1. Overview & Business Intent

### Purpose
In church worship ministry management, members often have unforeseen scheduling conflicts (illness, academic exams, family emergencies, or work travel) preventing them from serving in their assigned worship service roles. Currently, `Synced N` contains static placeholder/demo cards for "Substitution Request" in `announcementsView()` and a hardcoded submission form in `MemberActivity.kt:substitutionView()`.

Group 6 connects these disparate components into an end-to-end, real-time Cloud Firestore-backed substitution workflow. It empowers scheduled members to request a substitute from their active ministry team roster, enables proposed substitutes (or the Team Leader) to review and accept/decline the request, and automatically syncs the updated personnel assignment to the worship service schedule.

### Success Criteria
1. **Model & Validation:** A robust `SubstitutionData` model with full serialization/deserialization, fallback parsing, and Firestore timestamp coercion.
2. **Database Security:** Production-grade Cloud Firestore security rules with `isValidSubstitution()` validator restricting operations to authenticated team members and designated roles.
3. **Repository Abstraction:** High-performance Firestore CRUD methods in `TeamRepository.kt` with local `SharedPreferences` caching and 15-second debounce/throttling.
4. **Member Experience:**
   - Pre-filled substitution request form based on current service assignment.
   - Dynamic roster selection (excluding the requester).
   - Real-time substitution feed in `announcementsView()` with contextual status badges and Accept/Decline action buttons.
5. **Leader Oversight & Service Synchronization:**
   - Team Leader can view, accept, decline, or delete substitution requests.
   - Upon acceptance, the corresponding `ServiceData` document in Firestore is automatically updated to replace the original assignee with the substitute.
6. **Zero UI Disruption:** Strict adherence to existing design language, fonts, padding, and colors (`navy = Color.rgb(13, 27, 42)`, `purple = Color.rgb(108, 74, 182)`).

---

## 2. Assumed Data Model: `SubstitutionData`

The Kotlin data model resides at `app/src/main/java/com/example/syncedn/data/model/SubstitutionData.kt`:

```kotlin
package com.example.syncedn.data.model

data class SubstitutionData(
    val id: String = "",                     // Unique substitution ID (matches document ID)
    val teamCode: String = "",               // 6-character team invite code
    val serviceId: String = "",              // Linked ServiceData ID (if scheduled service)
    val serviceTitle: String = "",           // Name of the service (e.g. "Sunday Worship Service")
    val serviceDate: String = "",            // Date of the service ("YYYY-MM-DD")
    val requesterUid: String = "",           // UID of the requesting member
    val requesterName: String = "",         // Full name of the requester
    val role: String = "",                   // Ministry role needing substitution (e.g. "Keyboardist")
    val substituteUid: String = "",          // UID of the chosen substitute teammate
    val substituteName: String = "",         // Name of the chosen substitute teammate
    val reason: String = "",                 // Member's reason for substitution (1-500 chars)
    val status: String = "Pending",          // "Pending", "Accepted", "Declined", "Cancelled"
    val createdBy: String = "",              // UID of creator (matches requesterUid)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "teamCode" to teamCode,
        "serviceId" to serviceId,
        "serviceTitle" to serviceTitle,
        "serviceDate" to serviceDate,
        "requesterUid" to requesterUid,
        "requesterName" to requesterName,
        "role" to role,
        "substituteUid" to substituteUid,
        "substituteName" to substituteName,
        "reason" to reason,
        "status" to status,
        "createdBy" to createdBy,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): SubstitutionData {
            return SubstitutionData(
                id = map["id"] as? String ?: "",
                teamCode = map["teamCode"] as? String ?: "",
                serviceId = map["serviceId"] as? String ?: "",
                serviceTitle = map["serviceTitle"] as? String ?: "",
                serviceDate = map["serviceDate"] as? String ?: "",
                requesterUid = map["requesterUid"] as? String ?: "",
                requesterName = map["requesterName"] as? String ?: "",
                role = map["role"] as? String ?: "",
                substituteUid = map["substituteUid"] as? String ?: "",
                substituteName = map["substituteName"] as? String ?: "",
                reason = map["reason"] as? String ?: "",
                status = map["status"] as? String ?: "Pending",
                createdBy = map["createdBy"] as? String ?: "",
                createdAt = parseTimestamp(map["createdAt"]),
                updatedAt = parseTimestamp(map["updatedAt"])
            )
        }

        private fun parseTimestamp(raw: Any?): Long {
            return when (raw) {
                is Number -> raw.toLong()
                is java.util.Date -> raw.time
                else -> try {
                    val method = raw?.javaClass?.getMethod("toDate")
                    (method?.invoke(raw) as? java.util.Date)?.time ?: System.currentTimeMillis()
                } catch (_: Throwable) {
                    System.currentTimeMillis()
                }
            }
        }
    }
}
```

---

## 3. Firestore Architecture & Security Rules

### Path Structure
- Subcollection: `/teams/{teamCode}/substitutions/{substitutionId}`

### Security Validator & Rules (`firestore.rules`)
```javascript
// Strict validator for /teams/{teamCode}/substitutions/{substitutionId} (Group 6)
function isValidSubstitution(data, substitutionId, teamCode) {
  return hasOnlyAllowedFields(data, [
    'id', 'teamCode', 'serviceId', 'serviceTitle', 'serviceDate',
    'requesterUid', 'requesterName', 'role', 'substituteUid',
    'substituteName', 'reason', 'status', 'createdBy', 'createdAt', 'updatedAt'
  ]) &&
  hasRequiredFields(data, [
    'id', 'teamCode', 'serviceTitle', 'requesterUid', 'requesterName',
    'role', 'substituteUid', 'substituteName', 'reason', 'status', 'createdBy'
  ]) &&
  data.id is string && data.id == substitutionId &&
  data.teamCode is string && data.teamCode == teamCode &&
  data.serviceTitle is string && data.serviceTitle.size() > 0 && data.serviceTitle.size() <= 120 &&
  (!('serviceId' in data) || (data.serviceId is string && data.serviceId.size() <= 128)) &&
  (!('serviceDate' in data) || (data.serviceDate is string && data.serviceDate.size() <= 50)) &&
  data.requesterUid is string && data.requesterUid.size() > 0 && data.requesterUid.size() <= 128 &&
  data.requesterName is string && data.requesterName.size() > 0 && data.requesterName.size() <= 100 &&
  data.role is string && data.role.size() > 0 && data.role.size() <= 50 &&
  data.substituteUid is string && data.substituteUid.size() > 0 && data.substituteUid.size() <= 128 &&
  data.substituteName is string && data.substituteName.size() > 0 && data.substituteName.size() <= 100 &&
  data.reason is string && data.reason.size() > 0 && data.reason.size() <= 500 &&
  data.status in ['Pending', 'Accepted', 'Declined', 'Cancelled'] &&
  data.createdBy is string && data.createdBy.size() > 0 && data.createdBy.size() <= 128 &&
  (!('createdAt' in data) || (data.createdAt is number && data.createdAt >= 0) || (data.createdAt is timestamp)) &&
  (!('updatedAt' in data) || (data.updatedAt is number && data.updatedAt >= 0) || (data.updatedAt is timestamp));
}

// Subcollection match block in match /teams/{teamCode}
match /substitutions/{substitutionId} {
  // Read: Any team member can read team substitutions
  allow read: if isAuthenticated() && isSameTeam(teamCode);

  // Create: Member creates for themselves
  allow create: if isAuthenticated() && isSameTeam(teamCode) &&
                   isValidSubstitution(request.resource.data, substitutionId, teamCode) &&
                   request.resource.data.requesterUid == request.auth.uid &&
                   request.resource.data.createdBy == request.auth.uid;

  // Update: Substitute accepts/declines, requester cancels, or Team Leader updates
  allow update: if isAuthenticated() && isSameTeam(teamCode) &&
                   isValidSubstitution(request.resource.data, substitutionId, teamCode) &&
                   areImmutableFieldsUnchanged(['id', 'teamCode', 'requesterUid', 'createdBy', 'createdAt']) &&
                   (
                     request.auth.uid == resource.data.substituteUid || 
                     request.auth.uid == resource.data.requesterUid || 
                     isLeaderOfTeam(teamCode)
                   );

  // Delete: Team Leader or original requester can delete
  allow delete: if isAuthenticated() && isSameTeam(teamCode) &&
                   (isLeaderOfTeam(teamCode) || request.auth.uid == resource.data.requesterUid);
}
```

---

## 4. Repository Architecture (`TeamRepository.kt`)

The repository encapsulates all Firestore mutations and read caching:

1. **Local Caching:**
   - Key: `cached_substitutions_${teamCode}` in `SharedPreferences`.
   - `saveSubstitutionsLocally(teamCode: String, list: List<SubstitutionData>, preferences: SharedPreferences)`
   - `getCachedSubstitutions(teamCode: String, preferences: SharedPreferences): List<SubstitutionData>`
2. **Debounced Querying:**
   - `getSubstitutions(teamCode: String, forceRefresh: Boolean = false, preferences: SharedPreferences, onComplete: (Result<List<SubstitutionData>>) -> Unit)`
   - Enforces a 15-second debounce window (`lastSubstitutionFetchMap[teamCode]`) and limits queries to the 50 most recent documents (`.orderBy("createdAt", Query.Direction.DESCENDING).limit(50)`).
3. **Mutation Operations:**
   - `createSubstitutionRequest(sub: SubstitutionData, preferences: SharedPreferences, onComplete: (Result<Unit>) -> Unit)`
   - `updateSubstitutionStatus(teamCode: String, substitutionId: String, newStatus: String, preferences: SharedPreferences, onComplete: (Result<Unit>) -> Unit)`
   - `deleteSubstitution(teamCode: String, substitutionId: String, preferences: SharedPreferences, onComplete: (Result<Unit>) -> Unit)`
4. **Atomic Schedule Roster Sync:**
   - `acceptSubstitutionAndUpdateService(teamCode: String, sub: SubstitutionData, preferences: SharedPreferences, onComplete: (Result<Unit>) -> Unit)`
     - Step A: Update substitution status in `/teams/{teamCode}/substitutions/{sub.id}` to `"Accepted"`.
     - Step B: If `sub.serviceId` is non-empty, load the service document from `/teams/{teamCode}/services/{sub.serviceId}`.
     - Step C: Find the assignment matching `role == sub.role` and `(uid == sub.requesterUid || name == sub.requesterName)`. Replace with `ServiceAssignment(uid = sub.substituteUid, name = sub.substituteName, role = sub.role)`.
     - Step D: Write the updated `ServiceData` to Firestore and update local service cache.

---

## 5. UI Workflows & Screen Architecture

### 5.1 MemberActivity: Requesting a Substitute
- **Entry Point:** Click *"Request Substitution"* in `serviceView()`.
- **Form Screen (`substitutionView()`):**
  - Displays current assignment details (Service title, date, venue, member's assigned role).
  - Reason Input: Plain multi-line input (1-500 chars, validated against blank).
  - Substitute Selector: Dynamically renders the team roster fetched via `TeamRepository.getTeamRoster()`, filtering out the current user (`member.uid != currentUid`).
  - Clicking a team member sets `selectedSubstitute = member` with visual confirmation.
  - On submit: calls `TeamRepository.createSubstitutionRequest()`, shows success toast, and navigates to `"announcements"`.

### 5.2 Announcements & Action Processing (`announcementsView()`)
- In both `MemberActivity` and `AdminActivity`, replace the static single substitution demo panel with a dynamic container iterating over `TeamRepository.getCachedSubstitutions()`.
- Each substitution item displays:
  - Header: Service Title and Date.
  - Status Badge:
    - `"Pending"`: Yellow/Purple badge labeled *"Waiting for substitute"*.
    - `"Accepted"`: Green badge labeled *"Accepted"*.
    - `"Declined"`: Red badge labeled *"Declined"*.
    - `"Cancelled"`: Gray badge labeled *"Cancelled"*.
  - Details: Requester name & role, proposed substitute name, and reason.
  - **Action Buttons (`Accept` / `Decline`):**
    - Rendered when status is `"Pending"` AND (current user is `substituteUid` OR current user is Team Leader).
    - Clicking **Accept**: Displays confirmation dialog (`"Accept substitution?"`). When confirmed, executes `acceptSubstitutionAndUpdateService()`, updates badge immediately to `"Accepted"`, and refreshes local state.
    - Clicking **Decline**: Displays confirmation dialog. When confirmed, calls `updateSubstitutionStatus(..., "Declined")`.
  - **Cancel Button:**
    - Rendered when status is `"Pending"` AND current user is `requesterUid`. Allows the requester to cancel their request if their conflict is resolved.

---

## 6. Testing Strategy

1. **Unit Tests (`ExampleUnitTest.kt`):**
   - `testSubstitutionDataSerializationAndDeserialization()`: Verifies `toMap()` and `fromMap()` preserve all fields, handle missing optional attributes, and properly parse timestamps.
   - `testTeamRepositorySubstitutionCachingAndFiltering()`: Validates local SharedPreferences storage, retrieval, and status updates.
   - `testServiceRosterReplacementOnAccept()`: Validates that replacing an assigned member with a substitute correctly updates the `assignments` list without altering other roles or songs.
2. **Live Emulator E2E Verification:**
   - Launch app on `emulator-5554` as Leader.
   - Log in as Member, navigate to scheduled service, click *"Request Substitution"*.
   - Fill reason, pick substitute teammate, submit request.
   - Verify request appears in Announcements feed as *"Waiting for substitute"*.
   - Switch / test as substitute or Leader: click **Accept**.
   - Verify status transitions to *"Accepted"*, and schedule assignment updates to reflect the substitute.
