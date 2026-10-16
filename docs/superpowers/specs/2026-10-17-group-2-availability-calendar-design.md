# Design Spec: Group 2 — Member Availability & Calendar Sync

**Date:** 2026-10-17  
**Status:** In Review / Ready for Plan  
**Package:** `com.example.syncedn.data` and `com.example.syncedn`

---

## 1. Overview & Objective
Sa kasalukuyang estado ng aplikasyon:
- Sa `MemberActivity.kt` (`availabilityView()` at `calendarView()`), iisang date range lang (`unavailableStart`, `unavailableEnd`, `unavailableReason`) ang naitatala at nakaimbak lamang ito sa local `SharedPreferences` ng telepono.
- Sa `AdminActivity.kt` (`memberAvailabilityView()`), hardcoded sample data pa lamang ang ipinapakita at hindi nakikita ng Team Leader kung sino talaga ang available o may leave/conflict sa petsa ng gagawing Worship Service.

**Objective para sa Group 2:**
Magpatupad ng **Group 2 backend and database sync layer** para sa Member Availability at Calendar:
1. **Multiple Unavailable Date Ranges:** Ang bawat miyembro ay makakapagtakda ng higit sa isang schedule kung kailan siya hindi pwede (hal. *"2026-10-20 to 2026-10-22: Family vacation"*, *"2026-11-01 to 2026-11-03: Work shift"*).
2. **Cloud Firestore Sync:** I-store ang `unavailableRanges` sa `/users/{uid}` document ng bawat miyembro.
3. **Leader Service Scheduling Integration:** Kapag gumagawa ng Worship Service ang Leader para sa isang partikular na petsa, awtomatikong susuriin ng system ang lahat ng members sa team roster. Ang mga miyembrong may conflict sa petsang iyon ay mamarkahang **"Unavailable"** (kasama ang kanilang dahilan), habang ang iba ay mamarkahang **"Available"**.
4. **Member Calendar Display:** Sa calendar ng member, kukulayan o lalagyan ng indicator ang lahat ng araw na nasasakupan ng kanyang mga naka-set na unavailable ranges, at maaari siyang magbura o magdagdag ng range anumang oras.
5. **Offline Resiliency:** Lahat ng availability data ay naka-cache sa `TeamRepository` at `SharedPreferences` upang mabilis pa ring gumana kahit walang internet.

---

## 2. Data Models (`com.example.syncedn.data.model`)

### 2.1 Bagong Model: `UnavailableRange.kt`
Kumakatawan sa isang period ng unavailability ng miyembro:
```kotlin
data class UnavailableRange(
    val id: String = "",
    val startDate: String = "",     // Format: "YYYY-MM-DD"
    val endDate: String = "",       // Format: "YYYY-MM-DD"
    val reason: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "startDate" to startDate,
        "endDate" to endDate,
        "reason" to reason,
        "createdAt" to createdAt
    )

    fun includes(date: String): Boolean {
        if (startDate.isBlank() || endDate.isBlank() || date.isBlank()) return false
        return date >= startDate && date <= endDate
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): UnavailableRange {
            return UnavailableRange(
                id = map["id"] as? String ?: "",
                startDate = map["startDate"] as? String ?: "",
                endDate = map["endDate"] as? String ?: "",
                reason = map["reason"] as? String ?: "",
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}
```

### 2.2 Extension sa `UserProfile.kt` at `TeamMember.kt`
Idaragdag ang property na `unavailableRanges`:
- `val unavailableRanges: List<UnavailableRange> = emptyList()`
- Isasama sa `toMap()` at `fromMap()` para ma-serialize at ma-deserialize nang maayos mula sa Firestore at local JSON cache.

---

## 3. Backend Repository Layer (`com.example.syncedn.data.database.TeamRepository`)

Palalawakin ang `TeamRepository` para suportahan ang mga sumusunod na operasyon:

1. **`addUnavailableRange(uid: String, range: UnavailableRange, preferences: SharedPreferences?, onComplete: (Result<Unit>) -> Unit)`**
   - Idaragdag ang bagong `UnavailableRange` sa memory cache at local `preferences`.
   - I-a-update ang `/users/{uid}` sa Firestore gamit ang `FieldValue.arrayUnion(range.toMap())` o `set(updates, SetOptions.merge())`.

2. **`removeUnavailableRange(uid: String, rangeId: String, preferences: SharedPreferences?, onComplete: (Result<Unit>) -> Unit)`**
   - Tatanggalin ang range mula sa cache at `preferences`.
   - I-a-update ang `/users/{uid}` sa Firestore upang alisin ang tinukoy na range.

3. **`getMemberUnavailableRanges(uid: String): List<UnavailableRange>`**
   - Ibabalik ang kasalukuyang aktibong listahan ng date ranges ng miyembro.

4. **`checkTeamAvailabilityForDate(teamCode: String, targetDate: String): TeamAvailabilityResult`**
   - Susuriin ang buong `cachedRosters[teamCode]`:
     - **Available Members:** Mga miyembro na walang sumasakop na unavailable range sa `targetDate`.
     - **Unavailable Members:** Mga miyembro na ang `targetDate` ay pasok sa `range.includes(targetDate)`, kasama ang `reason`.

---

## 4. Cloud Firestore Security Rules Update

I-a-update ang `firestore.rules` sa `isValidUser` function:
1. Idaragdag ang `'unavailableRanges'` sa `hasOnlyAllowedFields`:
   ```javascript
   data.keys().hasOnly([
     'uid', 'username', 'email', 'role', 'teamName', 'teamCode',
     'resetPassword', 'fullName', 'phone', 'country', 'language',
     'ministryRole', 'unavailableRanges', 'createdAt'
   ])
   ```
2. Magdaragdag ng validation para sa listahan:
   ```javascript
   (!('unavailableRanges' in data) || (data.unavailableRanges is list && data.unavailableRanges.size() <= 100))
   ```
3. Dahil sakop na ito ng rule kung saan ang may-ari ng account (`isOwner(userId)`) ang nag-a-update ng kanyang sariling user document, magiging seamless at walang `PERMISSION_DENIED` error ang pag-save at pagbura.

---

## 5. Frontend / UI Flow

### 5.1 Member Activity (`MemberActivity.kt`)
1. **`availabilityView()` (Manage Availability Screen):**
   - **Form sa Itaas:** `Starts` date picker, `Ends` date picker, at `Reason` input field (may character limit). Button: **"+ Add Unavailable Dates"**.
   - **Listahan sa Ibaba:** Ipinapakita ang lahat ng naka-save na unavailable ranges:
     - Format: `[Calendar Icon] 2026-10-20 – 2026-10-22 • Out of town`
     - May pulang button na `✕` (Remove/Delete) sa bawat card para madaling burahin.
2. **`calendarView()` (Calendar Screen):**
   - Sa buwanang grid, lalagyan ng pulang dot (`•`) ang bawat araw kung kailan may umiiral na unavailable range.
   - Kapag pinindot ang partikular na araw, lilitaw sa ibaba ang pulang panel na *"Unavailable: <Reason>"* kapag may conflict, o berdeng panel na *"Available"* kapag walang conflict.

### 5.2 Leader Activity (`AdminActivity.kt`)
1. **`memberAvailabilityView()`:**
   - Babasahin ang petsa mula sa `leaderDraft.optString("date")` (ang petsa ng ginagawang service, hal. `2026-10-25`).
   - Hahatiin ang team roster sa:
     - **Available (Green Tab / Section):** Ipinapakita ang mga miyembro na handang maglingkod sa araw na iyon.
     - **Unavailable (Red Tab / Section):** Ipinapakita ang mga miyembro na hindi pwede, kasama ang kanilang eksaktong dahilan (`reason`) at date range.
   - Sa susunod na hakbang ng Service Wizard (`serviceAssignmentsView()`), maiiwasan ng Leader na maitalaga ang mga miyembrong unavailable.

---

## 6. Testing & Verification Plan

1. **Unit Tests (`ExampleUnitTest.kt`):**
   - Subukan ang `UnavailableRange` serialization (`toMap` at `fromMap`).
   - Subukan ang `UnavailableRange.includes(date)` matching logic para sa mga petsang nasa loob, labas, at boundary (start/end dates).
   - Subukan ang pag-save at pag-load ng `unavailableRanges` sa `UserProfile` at `TeamMember`.
2. **Build Verification:**
   - Patakbuhin ang `.\gradlew.bat testDebugUnitTest` upang masigurong walang sirang regression.
3. **Emulator Integration Check:**
   - Mag-login bilang Member: Magdagdag ng 2 magkaibang unavailable date ranges, suriin ang red dots sa kalendaryo, at subukang magbura ng isa.
   - Mag-login bilang Leader: Gumawa ng service sa petsang iyon at tingnan kung lumilitaw sa "Unavailable" ang miyembro na may tamang dahilan.
