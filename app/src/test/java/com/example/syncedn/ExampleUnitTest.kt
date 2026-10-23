package com.example.syncedn

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testArtAssets() {
        val names = listOf(
            "landing_background",
            "welcome_artwork",
            "google_mark",
            "profile_photo",
            "hero_container",
            "team_badge",
            "song_covers_source",
            "team_sunset",
            "team_header_badge",
            "team_brand_logo"
        )
        for (name in names) {
            val data = Art.data(name)
            assertNotNull("Asset $name should not be null", data)
            assertTrue("Asset $name should not be empty", data.isNotEmpty())
            val bytes = java.util.Base64.getDecoder().decode(data)
            val img = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes))
            assertNotNull("Asset $name should decode to a valid image", img)
            println("$name: width=${img.width}, height=${img.height}")

            when (name) {
                "welcome_artwork" -> {
                    assertTrue("welcome_artwork height >= 280 for crop", img.height >= 280)
                }
                "hero_container" -> {
                    assertTrue("hero_container width >= 336 (24 + 312)", img.width >= 336)
                    assertTrue("hero_container height >= 216 (16 + 200)", img.height >= 216)
                }
                "song_covers_source" -> {
                    assertTrue("song_covers_source width >= 149 (29 + 120)", img.width >= 149)
                    assertTrue("song_covers_source height >= 606 (540 + 66)", img.height >= 606)
                }
            }
        }

        // Test fallback for unknown asset
        val fallbackData = Art.data("non_existent_asset")
        assertTrue("Fallback data should not be empty", fallbackData.isNotEmpty())
        val fallbackBytes = java.util.Base64.getDecoder().decode(fallbackData)
        val fallbackImg = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(fallbackBytes))
        assertNotNull("Fallback should decode to a valid image", fallbackImg)
    }

    @Test
    fun testFirebaseClassesAvailable() {
        assertNotNull(com.google.firebase.auth.FirebaseAuth::class.java)
        assertNotNull(com.google.firebase.firestore.FirebaseFirestore::class.java)
    }

    @Test
    fun testUserProfileModelMapping() {
        val profile = com.example.syncedn.data.model.UserProfile(
            uid = "test-uid-123",
            username = "TestUser",
            email = "test@example.com",
            role = "Admin",
            teamName = "Praise Team",
            resetPassword = "newPassword456",
            createdAt = 123456789L
        )
        val map = profile.toMap()
        assertEquals("test-uid-123", map["uid"])
        assertEquals("TestUser", map["username"])
        assertEquals("test@example.com", map["email"])
        assertEquals("Admin", map["role"])
        assertEquals("Praise Team", map["teamName"])
        assertEquals("newPassword456", map["resetPassword"])
        assertEquals(123456789L, map["createdAt"])

        val reconstructed = com.example.syncedn.data.model.UserProfile.fromMap(map)
        assertEquals(profile.uid, reconstructed.uid)
        assertEquals(profile.username, reconstructed.username)
        assertEquals(profile.email, reconstructed.email)
        assertEquals(profile.role, reconstructed.role)
        assertEquals(profile.teamName, reconstructed.teamName)
        assertEquals(profile.resetPassword, reconstructed.resetPassword)
        assertEquals(profile.createdAt, reconstructed.createdAt)
    }

    @Test
    fun testEmailOtpVerificationLogic() {
        val otpService = com.example.syncedn.data.auth.EmailOtpService
        val testEmail = "test@syncedn.org"
        val testCode = "481920"

        // 1. Success case
        val validRecord = com.example.syncedn.data.auth.EmailOtpService.OtpRecord(
            email = testEmail,
            code = testCode,
            expiresAt = System.currentTimeMillis() + 60000L,
            pendingPassword = "newPassword123"
        )
        otpService.setRecordForTesting(validRecord)
        val successResult = otpService.verifyOtp("481920")
        assertTrue("Correct OTP should verify successfully", successResult.isSuccess)
        assertEquals(testEmail, successResult.getOrNull()?.email)
        assertEquals("newPassword123", successResult.getOrNull()?.pendingPassword)

        // 2. Invalid code case
        otpService.setRecordForTesting(validRecord)
        val failResult = otpService.verifyOtp("000000")
        assertTrue("Incorrect OTP should fail verification", failResult.isFailure)

        // 3. Expired code case
        val expiredRecord = com.example.syncedn.data.auth.EmailOtpService.OtpRecord(
            email = testEmail,
            code = testCode,
            expiresAt = System.currentTimeMillis() - 1000L
        )
        otpService.setRecordForTesting(expiredRecord)
        val expiredResult = otpService.verifyOtp("481920")
        assertTrue("Expired OTP should fail verification", expiredResult.isFailure)

        // 4. No active record case
        otpService.clear()
        val emptyResult = otpService.verifyOtp("481920")
        assertTrue("Verification with no active OTP should fail", emptyResult.isFailure)
    }

    @Test
    fun testUserProfileDefaultTeamNameIsEmpty() {
        val newProfile = com.example.syncedn.data.model.UserProfile(
            uid = "new-user-1",
            username = "Newbie",
            email = "newbie@syncedn.org"
        )
        assertEquals("", newProfile.teamName)
        assertEquals("Member", newProfile.role)

        val map = newProfile.toMap()
        assertEquals("", map["teamName"])

        val reconstructed = com.example.syncedn.data.model.UserProfile.fromMap(map)
        assertEquals("", reconstructed.teamName)
    }

    @Test
    fun testRoleAndTeamRoutingLogic() {
        // Routing decision logic representing MainActivity and MemberActivity role routing
        fun resolveDashboard(role: String, teamName: String): String {
            return if (teamName.isBlank()) {
                "teamChoice"
            } else if (role.equals("Leader", ignoreCase = true)) {
                "AdminActivity"
            } else {
                "MemberActivity"
            }
        }

        // 1. Newly registered user with no team yet
        assertEquals("teamChoice", resolveDashboard(role = "", teamName = ""))
        assertEquals("teamChoice", resolveDashboard(role = "Member", teamName = ""))

        // 2. Leader who created a team
        assertEquals("AdminActivity", resolveDashboard(role = "Leader", teamName = "Praise & Worship Team"))

        // 3. Member who joined a team
        assertEquals("MemberActivity", resolveDashboard(role = "Member", teamName = "Worship Ministry Team"))
    }

    @Test
    fun testTeamCodeGenerationAndValidation() {
        val fm = com.example.syncedn.data.database.FirestoreManager

        // 1. Generated codes must be 6 uppercase alphanumeric characters
        repeat(10) {
            val code = fm.generateTeamCode()
            assertEquals(6, code.length)
            assertTrue("Generated code $code should be valid format", fm.isValidTeamCodeFormat(code))
            assertEquals(code, code.uppercase())
        }

        // 2. Format validation checks
        assertTrue(fm.isValidTeamCodeFormat("A7K9P2"))
        assertTrue(fm.isValidTeamCodeFormat("  a7k9p2  "))
        assertFalse(fm.isValidTeamCodeFormat(""))
        assertFalse(fm.isValidTeamCodeFormat("ABC12")) // too short
        assertFalse(fm.isValidTeamCodeFormat("ABC1234")) // too long
        assertFalse(fm.isValidTeamCodeFormat("AB-123")) // non-alphanumeric

        // 3. UserProfile teamCode serialization
        val leaderProfile = com.example.syncedn.data.model.UserProfile(
            uid = "leader-1",
            username = "LeaderRyan",
            email = "ryan@syncedn.org",
            role = "Leader",
            teamName = "Youth Ministry",
            teamCode = "K7M9P2"
        )
        val map = leaderProfile.toMap()
        assertEquals("K7M9P2", map["teamCode"])
        val restored = com.example.syncedn.data.model.UserProfile.fromMap(map)
        assertEquals("K7M9P2", restored.teamCode)
    }

    @Test
    fun testCreateTeamAndVerifyCodeFlow() {
        val fm = com.example.syncedn.data.database.FirestoreManager
        fm.clearLocalTeams()

        // 1. Creating a team completes immediately and generates a valid 6-character code
        var createdCode = ""
        fm.createTeamWithCode(
            leaderUid = "leader-uid-1",
            leaderName = "Leader Ryan",
            teamName = "Praise Ministry"
        ) { result ->
            assertTrue("Team creation should succeed immediately", result.isSuccess)
            createdCode = result.getOrNull().orEmpty()
        }
        assertEquals(6, createdCode.length)
        assertEquals("Praise Ministry", fm.getLocalTeamName(createdCode))

        // 2. Member joining with the valid code (even lowercase/with spaces) succeeds immediately
        var joinedTeam: Pair<String, String>? = null
        fm.getTeamByCode("  ${createdCode.lowercase()}  ") { result ->
            assertTrue("Valid team code should succeed", result.isSuccess)
            joinedTeam = result.getOrNull()
        }
        assertNotNull(joinedTeam)
        assertEquals(createdCode, joinedTeam?.first)
        assertEquals("Praise Ministry", joinedTeam?.second)

        // 3. Member entering a random unregistered 6-character code is rejected
        var randomAllowed = true
        fm.getTeamByCode("123456") { result ->
            randomAllowed = result.isSuccess
        }
        assertFalse("Random unregistered 6-digit code must be rejected", randomAllowed)
    }

    @Test
    fun testLeaderRoleAndTeamCodePersistAcrossRelogin() {
        val fm = com.example.syncedn.data.database.FirestoreManager
        fm.clearLocalTeams()

        // 1. Leader creates a team
        var generatedCode = ""
        fm.createTeamWithCode(
            leaderUid = "leader-uid-99",
            leaderName = "Ryan",
            teamName = "Synced Worship"
        ) { result ->
            generatedCode = result.getOrNull().orEmpty()
        }
        assertEquals(6, generatedCode.length)
        assertEquals(generatedCode, fm.findCodeByTeamName("Synced Worship"))

        // 2. Simulate re-login where Firestore UserProfile still has default role="Member"
        val firestoreProfile = com.example.syncedn.data.model.UserProfile(
            uid = "leader-uid-99",
            username = "Ryan",
            email = "ryan@syncedn.org",
            role = "Member", // Default value from initial registration
            teamName = "",
            teamCode = ""
        )

        val savedRole = "Leader"
        val savedTeam = "Synced Worship"
        val savedCode = generatedCode

        val finalTeam = savedTeam.ifBlank { firestoreProfile.teamName }
        val finalCode = savedCode
            .ifBlank { firestoreProfile.teamCode }
            .ifBlank { fm.findCodeByTeamName(finalTeam).orEmpty() }
        val finalRole = when {
            savedRole.equals("Leader", ignoreCase = true) || firestoreProfile.role.equals("Leader", ignoreCase = true) -> "Leader"
            savedRole.isNotBlank() -> savedRole
            firestoreProfile.role.isNotBlank() -> firestoreProfile.role
            else -> "Member"
        }

        assertEquals("Leader role must not be overwritten by default 'Member' on re-login", "Leader", finalRole)
        assertEquals("Synced Worship", finalTeam)
        assertEquals(generatedCode, finalCode)
    }

    @Test
    fun testUserProfileExtendedFieldsSerialization() {
        val profile = com.example.syncedn.data.model.UserProfile(
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
        val restored = com.example.syncedn.data.model.UserProfile.fromMap(map)
        assertEquals("John Doe", restored.fullName)
        assertEquals("09123456789", restored.phone)
        assertEquals("Guitarist", restored.ministryRole)
        assertEquals("Philippines", restored.country)
        assertEquals("English", restored.language)
    }

    @Test
    fun testTeamMemberAndTeamDataMapping() {
        val member = com.example.syncedn.data.model.TeamMember(
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
        val restored = com.example.syncedn.data.model.TeamMember.fromMap(map)
        assertEquals("Jane Doe", restored.fullName)
        assertEquals("Vocalist", restored.ministryRole)
        assertEquals("TEST01", restored.teamCode)

        val team = com.example.syncedn.data.model.TeamData(
            teamCode = "TEST01",
            teamName = "Praise Ministry",
            leaderUid = "leader-1",
            leaderName = "Leader Ryan"
        )
        val teamMap = team.toMap()
        val restoredTeam = com.example.syncedn.data.model.TeamData.fromMap(teamMap)
        assertEquals("Praise Ministry", restoredTeam.teamName)
        assertTrue(restoredTeam.roles.contains("Worship Leader"))
        assertTrue(restoredTeam.roles.contains("Vocalist"))
    }

    @Test
    fun testTeamRepositoryRosterAndRoleCaching() {
        val repo = com.example.syncedn.data.database.TeamRepository
        repo.clearCache()
        repo.cacheMember(com.example.syncedn.data.model.TeamMember(
            uid = "u1", fullName = "Leader Ryan", role = "Leader", ministryRole = "Worship Leader", teamCode = "CODE01"
        ))
        repo.cacheMember(com.example.syncedn.data.model.TeamMember(
            uid = "u2", fullName = "Member Dave", role = "Member", ministryRole = "Bassist", teamCode = "CODE01"
        ))

        val roster = repo.getCachedRoster("CODE01")
        assertEquals(2, roster.size)
        assertEquals("Leader Ryan", roster[0].fullName)
        assertEquals("Member Dave", roster[1].fullName)

        // Test ministry role update in cache
        repo.updateMemberMinistryRole("u2", "Guitarist") {}
        val updated = repo.getCachedRoster("CODE01")
        assertEquals("Guitarist", updated.find { it.uid == "u2" }?.ministryRole)

        // Test removing member from team
        repo.removeMemberFromTeam("u2") {}
        val afterRemove = repo.getCachedRoster("CODE01")
        assertEquals(1, afterRemove.size)
        assertEquals("u1", afterRemove[0].uid)
    }

    @Test
    fun testProfileUpdateCreatesValidUserProfile() {
        val uid = "test_user_1"
        val profile = com.example.syncedn.data.model.UserProfile(
            uid = uid,
            fullName = "Ryan Lloyd Genturo",
            username = "Yanyan",
            phone = "09123456789",
            country = "Philippines",
            language = "English"
        )
        assertEquals("Ryan Lloyd Genturo", profile.fullName)
        assertEquals("Yanyan", profile.username)
        assertEquals("09123456789", profile.phone)
        assertEquals("Philippines", profile.country)
        assertEquals("English", profile.language)
    }

    @Test
    fun testRosterGroupingByMinistryRole() {
        val members = listOf(
            com.example.syncedn.data.model.TeamMember(uid = "1", fullName = "Joshua Lagare", ministryRole = "Worship Leader", role = "Leader"),
            com.example.syncedn.data.model.TeamMember(uid = "2", fullName = "Ryan Lloyd Genturo", ministryRole = "Keyboardist", role = "Member"),
            com.example.syncedn.data.model.TeamMember(uid = "3", fullName = "Angelo Daniel", ministryRole = "Guitarist", role = "Member"),
            com.example.syncedn.data.model.TeamMember(uid = "4", fullName = "Ralph Lauren", ministryRole = "Guitarist", role = "Member"),
            com.example.syncedn.data.model.TeamMember(uid = "5", fullName = "Jessa Mae Dizon", ministryRole = "Vocalist", role = "Member")
        )
        val grouped = members.groupBy { it.ministryRole }
        assertEquals(4, grouped.size)
        assertEquals(2, grouped["Guitarist"]?.size)
        assertEquals(1, grouped["Worship Leader"]?.size)
        assertEquals(1, grouped["Keyboardist"]?.size)
        assertEquals(1, grouped["Vocalist"]?.size)
    }

    @Test
    fun testTeamRepositoryConcurrentAccessAndRoleRetrieval() {
        val repo = com.example.syncedn.data.database.TeamRepository
        repo.clearCache()
        val threads = (1..10).map { i ->
            Thread {
                repo.cacheMember(com.example.syncedn.data.model.TeamMember(
                    uid = "uid_$i",
                    fullName = "Member $i",
                    teamCode = "CONCURRENT"
                ))
                repo.getCachedRoster("CONCURRENT")
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        val roster = repo.getCachedRoster("CONCURRENT")
        assertEquals(10, roster.size)
    }

    @Test
    fun testSessionManagerIsolationBetweenUsers() {
        val prefs = FakePreferences()
        val session = com.example.syncedn.data.auth.SessionManager

        // Step 1: User A (Leader) logs in and sets their profile
        val leaderProfile = com.example.syncedn.data.model.UserProfile(
            uid = "leader_123",
            username = "LeaderRyan",
            email = "leader@test.com",
            fullName = "Ryan Lloyd Genturo",
            phone = "09123456789",
            country = "Philippines",
            language = "English",
            ministryRole = "Worship Leader"
        )
        session.saveSessionProfile(prefs, leaderProfile, "LeaderRyan", "leader@test.com")
        assertEquals("Ryan Lloyd Genturo", prefs.getString("fullName", ""))
        assertEquals("09123456789", prefs.getString("phone", ""))
        assertEquals("leader@test.com", prefs.getString("email", ""))

        // Step 2: User A logs out
        session.clearSession(prefs)
        assertEquals("", prefs.getString("phone", ""))
        assertEquals("", prefs.getString("fullName", ""))
        assertEquals("", prefs.getString("email", ""))

        // Step 3: User B (Member) logs in with no phone and no full name
        val memberProfile = com.example.syncedn.data.model.UserProfile(
            uid = "member_456",
            username = "MemberAngelo",
            email = "member@test.com",
            fullName = "",
            phone = "",
            country = "Philippines",
            language = "English",
            ministryRole = "Vocalist"
        )
        session.saveSessionProfile(prefs, memberProfile, "MemberAngelo", "member@test.com")

        // CRUCIAL: Verify no data leakage from User A to User B
        assertEquals("", prefs.getString("phone", "")) // Must NOT be 09123456789!
        assertEquals("", prefs.getString("fullName", "")) // Must NOT be Ryan Lloyd Genturo!
        assertEquals("member@test.com", prefs.getString("email", ""))
        assertEquals("MemberAngelo", prefs.getString("username", ""))
        assertEquals("Vocalist", prefs.getString("ministryRole", ""))
    }

    @Test
    fun testSessionManagerClearsRosterCacheOnLogout() {
        val prefs = FakePreferences()
        val repo = com.example.syncedn.data.database.TeamRepository
        repo.cacheMember(com.example.syncedn.data.model.TeamMember(
            uid = "member_1",
            fullName = "Member One",
            teamCode = "LOGOUT_TEAM"
        ))
        assertEquals(1, repo.getCachedRoster("LOGOUT_TEAM").size)

        com.example.syncedn.data.auth.SessionManager.clearSession(prefs)
        assertEquals(0, repo.getCachedRoster("LOGOUT_TEAM").size)
    }

    @Test
    fun testSessionManagerClearPreferencesOnlyClearsPrefsAndCacheWithoutSignout() {
        val prefs = FakePreferences()
        val repo = com.example.syncedn.data.database.TeamRepository
        prefs.edit()
            .putString("userId", "user_123")
            .putString("email", "test@domain.com")
            .putString("fullName", "Test User")
            .apply()
        repo.cacheMember(com.example.syncedn.data.model.TeamMember(
            uid = "user_123",
            fullName = "Test User",
            teamCode = "TEST_CODE"
        ))
        assertEquals(1, repo.getCachedRoster("TEST_CODE").size)

        com.example.syncedn.data.auth.SessionManager.clearPreferencesOnly(prefs)
        assertEquals("", prefs.getString("userId", ""))
        assertEquals("", prefs.getString("email", ""))
        assertEquals("", prefs.getString("fullName", ""))
        assertEquals(0, repo.getCachedRoster("TEST_CODE").size)
    }

    @Test
    fun testPhilippinePhoneValidationAndCharacterLimits() {
        val vu = com.example.syncedn.data.util.ValidationUtils

        // 1. Valid Philippine Mobile Numbers
        assertTrue("09123456789 should be valid", vu.isValidPhilippinePhone("09123456789"))
        assertTrue("+639123456789 should be valid", vu.isValidPhilippinePhone("+639123456789"))
        assertTrue("639123456789 should be valid", vu.isValidPhilippinePhone("639123456789"))
        assertTrue("Blank should be valid if optional", vu.isValidPhilippinePhone(""))
        assertTrue("Whitespace should be valid if optional", vu.isValidPhilippinePhone("   "))

        // 2. Invalid Philippine Mobile Numbers
        assertFalse("Too short should be invalid", vu.isValidPhilippinePhone("0912345678"))
        assertFalse("Too long should be invalid", vu.isValidPhilippinePhone("091234567890"))
        assertFalse("Wrong prefix 08 should be invalid", vu.isValidPhilippinePhone("08123456789"))
        assertFalse("Non-digits should be invalid", vu.isValidPhilippinePhone("0912345678a"))
        assertFalse("US country code should be invalid", vu.isValidPhilippinePhone("+12025550123"))

        // 3. Character length limits
        assertTrue("Name <= 50 chars valid", vu.isValidName("Ryan Lloyd Genturo"))
        assertFalse("Blank name invalid", vu.isValidName(""))
        assertFalse("Name > 50 chars invalid", vu.isValidName("A".repeat(51)))

        assertTrue("Username <= 30 chars valid", vu.isValidUsername("RL Gents"))
        assertFalse("Blank username invalid", vu.isValidUsername(""))
        assertFalse("Username > 30 chars invalid", vu.isValidUsername("U".repeat(31)))
    }

    @Test
    fun testMinistryRoleAssignmentAndRosterOverlay() {
        val repo = com.example.syncedn.data.database.TeamRepository
        val teamCode = "ROSTER_TEST"

        // Cache two members: Leader (Worship Leader) and Member (empty ministryRole initially)
        repo.cacheMember(com.example.syncedn.data.model.TeamMember(
            uid = "leader_uid_1",
            fullName = "Ryan Lloyd Genturo",
            role = "Leader",
            ministryRole = "Worship Leader",
            teamCode = teamCode
        ))
        repo.cacheMember(com.example.syncedn.data.model.TeamMember(
            uid = "member_uid_2",
            fullName = "RL Gents",
            username = "RL Gents",
            role = "Member",
            ministryRole = "",
            teamCode = teamCode
        ))

        // Before assignment: Vocalist count is 0
        var cached = repo.getCachedRoster(teamCode)
        val vocalistsBefore = cached.filter { it.ministryRole.equals("Vocalist", true) }
        assertEquals(0, vocalistsBefore.size)

        // Leader assigns "Vocalist" to RL Gents
        repo.updateMemberMinistryRole("member_uid_2", "Vocalist", memberName = "RL Gents")

        // After assignment: In-memory cache must immediately reflect "Vocalist"
        cached = repo.getCachedRoster(teamCode)
        val vocalistsAfter = cached.filter { it.ministryRole.equals("Vocalist", true) }
        assertEquals(1, vocalistsAfter.size)
        assertEquals("RL Gents", vocalistsAfter[0].fullName)
        assertEquals("Vocalist", vocalistsAfter[0].ministryRole)

        // Overlay with SharedPreferences memberRoles map simulation
        val memberRolesMap = mapOf("RL Gents" to "Vocalist")

        // Even with a member who had blank ministryRole, overlay resolves to "Vocalist"
        val rawMember = com.example.syncedn.data.model.TeamMember(
            uid = "unknown_uid",
            fullName = "RL Gents",
            ministryRole = "",
            teamCode = teamCode
        )
        val resolvedRole = rawMember.ministryRole.ifBlank {
            memberRolesMap[rawMember.uid] ?: memberRolesMap[rawMember.fullName] ?: memberRolesMap[rawMember.username] ?: ""
        }
        assertEquals("Vocalist", resolvedRole)
    }

    @Test
    fun testTeamRosterSyncFromPreferencesAndMultiMemberRoster() {
        val repo = com.example.syncedn.data.database.TeamRepository
        repo.clearCache()

        val mockPrefs = FakePreferences().apply {
            edit()
                // Leader
                .putString("email_code_ryanlloydgenturo@gmail.com", "MVBRWM")
                .putString("email_role_ryanlloydgenturo@gmail.com", "Leader")
                .putString("email_fullname_ryanlloydgenturo@gmail.com", "Ryan Lloyd Genturo")
                .putString("email_username_ryanlloydgenturo@gmail.com", "ryanlloydgenturo")
                .putString("email_ministry_ryanlloydgenturo@gmail.com", "Worship Leader")
                .putString("email_uid_ryanlloydgenturo@gmail.com", "uid_leader_1")
                // Member
                .putString("email_code_rlgenturo@gmail.com", "MVBRWM")
                .putString("email_role_rlgenturo@gmail.com", "Member")
                .putString("email_fullname_rlgenturo@gmail.com", "RL Gents")
                .putString("email_username_rlgenturo@gmail.com", "RL")
                .putString("email_ministry_rlgenturo@gmail.com", "Vocalist")
                .putString("email_uid_rlgenturo@gmail.com", "uid_member_2")
                .apply()
        }

        // Initially empty
        assertEquals(0, repo.getCachedRoster("MVBRWM").size)

        // Sync from preferences
        repo.syncFromPreferences(mockPrefs, "MVBRWM")

        // Roster must now contain both Leader and Member
        val roster = repo.getCachedRoster("MVBRWM")
        assertEquals(2, roster.size)

        // Leader is sorted first
        val leader = roster[0]
        assertEquals("Leader", leader.role)
        assertEquals("Ryan Lloyd Genturo", leader.fullName)
        assertEquals("Worship Leader", leader.ministryRole)

        // Member is sorted second
        val member = roster[1]
        assertEquals("Member", member.role)
        assertEquals("RL Gents", member.fullName)
        assertEquals("Vocalist", member.ministryRole)
    }

    @Test
    fun testMinistryRolePersistenceAcrossLogoutAndMemberLogin() {
        val repo = com.example.syncedn.data.database.TeamRepository
        repo.clearCache()

        val mockPrefs = FakePreferences().apply {
            edit()
                // Leader session initially logged in
                .putString(com.example.syncedn.data.auth.SessionManager.KEY_USER_ID, "uid_leader_1")
                .putString(com.example.syncedn.data.auth.SessionManager.KEY_EMAIL, "ryanlloydgenturo@gmail.com")
                .putString(com.example.syncedn.data.auth.SessionManager.KEY_ROLE, "Leader")
                .putString(com.example.syncedn.data.auth.SessionManager.KEY_TEAM_CODE, "MVBRWM")
                .putString(com.example.syncedn.data.auth.SessionManager.KEY_TEAM_NAME, "Ryan")
                // Seed Leader and Member records
                .putString("email_code_ryanlloydgenturo@gmail.com", "MVBRWM")
                .putString("email_role_ryanlloydgenturo@gmail.com", "Leader")
                .putString("email_fullname_ryanlloydgenturo@gmail.com", "Ryan Lloyd Genturo")
                .putString("email_ministry_ryanlloydgenturo@gmail.com", "Worship Leader")
                .putString("email_uid_ryanlloydgenturo@gmail.com", "uid_leader_1")
                .putString("email_code_rlgenturo@gmail.com", "MVBRWM")
                .putString("email_role_rlgenturo@gmail.com", "Member")
                .putString("email_fullname_rlgenturo@gmail.com", "RL Gents")
                .putString("email_username_rlgenturo@gmail.com", "RL")
                .putString("email_ministry_rlgenturo@gmail.com", "Vocalist")
                .putString("email_uid_rlgenturo@gmail.com", "uid_member_2")
                .apply()
        }

        // Leader syncs roster and sees RL Gents as Vocalist
        repo.syncFromPreferences(mockPrefs, "MVBRWM")
        var roster = repo.getCachedRoster("MVBRWM")
        assertEquals(2, roster.size)
        assertEquals("Vocalist", roster.first { it.email == "rlgenturo@gmail.com" }.ministryRole)

        // 1. Leader changes role from "Vocalist" to "Bassist" in roleEditView
        repo.updateMemberMinistryRole(
            memberUid = "uid_member_2",
            newRole = "Bassist",
            memberName = "RL Gents",
            memberEmail = "rlgenturo@gmail.com",
            preferences = mockPrefs
        )

        // Verify local persistence was updated
        assertEquals("Bassist", mockPrefs.getString("email_ministry_rlgenturo@gmail.com", ""))
        assertEquals("Bassist", mockPrefs.getString("user_ministry_uid_member_2", ""))
        val memberRolesMapStr = mockPrefs.getString(com.example.syncedn.data.auth.SessionManager.KEY_MEMBER_ROLES, "{}").orEmpty()
        assertTrue(memberRolesMapStr.contains("Bassist"))

        // 2. Leader logs out (clearSession)
        com.example.syncedn.data.auth.SessionManager.clearSession(mockPrefs)
        // Verify KEY_MEMBER_ROLES and email_ministry are preserved across logout!
        assertEquals("Bassist", mockPrefs.getString("email_ministry_rlgenturo@gmail.com", ""))
        assertTrue(mockPrefs.getString(com.example.syncedn.data.auth.SessionManager.KEY_MEMBER_ROLES, "{}").orEmpty().contains("Bassist"))

        // 3. Member logs in
        val memberProfile = com.example.syncedn.data.model.UserProfile(
            uid = "uid_member_2",
            username = "RL",
            email = "rlgenturo@gmail.com",
            role = "Member",
            teamName = "Ryan",
            teamCode = "MVBRWM",
            fullName = "RL Gents",
            ministryRole = "Vocalist" // Even if Firestore doc had stale "Vocalist"
        )
        com.example.syncedn.data.auth.SessionManager.saveSessionProfile(mockPrefs, memberProfile, fallbackUsername = "RL", fallbackEmail = "rlgenturo@gmail.com")

        // Member's active session MUST adopt the Leader-assigned "Bassist" role
        assertEquals("Bassist", mockPrefs.getString(com.example.syncedn.data.auth.SessionManager.KEY_MINISTRY_ROLE, ""))
        assertEquals("Bassist", mockPrefs.getString("email_ministry_rlgenturo@gmail.com", ""))

        // Member caches state
        val selfMember = com.example.syncedn.data.model.TeamMember(
            uid = "uid_member_2",
            fullName = "RL Gents",
            username = "RL",
            email = "rlgenturo@gmail.com",
            role = "Member",
            ministryRole = mockPrefs.getString(com.example.syncedn.data.auth.SessionManager.KEY_MINISTRY_ROLE, "").orEmpty(),
            teamCode = "MVBRWM"
        )
        repo.cacheMember(selfMember, mockPrefs)

        // 4. Member logs out
        com.example.syncedn.data.auth.SessionManager.clearSession(mockPrefs)

        // 5. Leader logs back in
        repo.syncFromPreferences(mockPrefs, "MVBRWM")
        roster = repo.getCachedRoster("MVBRWM")
        val rlGents = roster.first { it.email == "rlgenturo@gmail.com" }
        assertEquals("Bassist", rlGents.ministryRole)

        // Role tab groupings: Bassist has 1 member, Vocalist has 0 members
        val bassists = roster.filter { it.ministryRole.equals("Bassist", ignoreCase = true) }
        val vocalists = roster.filter { it.ministryRole.equals("Vocalist", ignoreCase = true) }
        assertEquals(1, bassists.size)
        assertEquals(0, vocalists.size)
        assertEquals("RL Gents", bassists[0].fullName)
    }

    @Test
    fun testUnavailableRangeSerializationAndIncludes() {
        val range = com.example.syncedn.data.model.UnavailableRange(
            id = "range1",
            startDate = "2026-10-20",
            endDate = "2026-10-25",
            reason = "Out of town",
            createdAt = 1000L
        )
        val map = range.toMap()
        val restored = com.example.syncedn.data.model.UnavailableRange.fromMap(map)
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
        val range = com.example.syncedn.data.model.UnavailableRange("r1", "2026-11-01", "2026-11-05", "Medical leave")
        val profile = com.example.syncedn.data.model.UserProfile(
            uid = "u1",
            fullName = "Test User",
            unavailableRanges = listOf(range)
        )
        val restoredProfile = com.example.syncedn.data.model.UserProfile.fromMap(profile.toMap())
        assertEquals(1, restoredProfile.unavailableRanges.size)
        assertEquals("Medical leave", restoredProfile.unavailableRanges[0].reason)

        val member = com.example.syncedn.data.model.TeamMember(
            uid = "u1",
            fullName = "Test Member",
            unavailableRanges = listOf(range)
        )
        val restoredMember = com.example.syncedn.data.model.TeamMember.fromMap(member.toMap())
        assertEquals(1, restoredMember.unavailableRanges.size)
        assertEquals("r1", restoredMember.unavailableRanges[0].id)
    }

    @Test
    fun testTeamAvailabilityCalculationForDate() {
        val repo = com.example.syncedn.data.database.TeamRepository
        val range1 = com.example.syncedn.data.model.UnavailableRange("r1", "2026-10-20", "2026-10-25", "Family reunion")
        val member1 = com.example.syncedn.data.model.TeamMember(uid = "m1", fullName = "Alice", teamCode = "TEAM99", unavailableRanges = listOf(range1))
        val member2 = com.example.syncedn.data.model.TeamMember(uid = "m2", fullName = "Bob", teamCode = "TEAM99", unavailableRanges = emptyList())

        repo.cacheMember(member1)
        repo.cacheMember(member2)

        val (available, unavailable) = repo.checkTeamAvailabilityForDate("TEAM99", "2026-10-22")
        assertEquals(1, available.size)
        assertEquals("Bob", available[0].fullName)
        assertEquals(1, unavailable.size)
        assertEquals("Alice", unavailable[0].first.fullName)
        assertEquals("Family reunion", unavailable[0].second)

        val (allAvailable, noneUnavailable) = repo.checkTeamAvailabilityForDate("TEAM99", "2026-10-30")
        assertEquals(2, allAvailable.size)
        assertEquals(0, noneUnavailable.size)
    }

    @Test
    fun testEmptyUnavailableRangesMemoryCacheNotBypassed() {
        val repo = com.example.syncedn.data.database.TeamRepository
        val uid = "test_empty_cache_uid"
        val prefs = FakePreferences()

        // Pre-populate stale data in preferences
        val staleJson = """[{"id":"range_stale","startDate":"2026-10-10","endDate":"2026-10-12","reason":"Stale"}]"""
        prefs.edit().putString("user_unavailable_ranges_$uid", staleJson).apply()

        // Simulate user having cleared all ranges (empty list cached in memory)
        repo.removeUnavailableRange(uid, "non_existent_range", null) // ensures cachedUserRanges has empty list

        // When cachedUserRanges has uid with empty list, fetching with prefs must NOT resurrect stale data from prefs
        val result = repo.getMemberUnavailableRanges(uid, prefs)
        assertEquals(0, result.size)
    }

    @Test
    fun testCleanDeviceLoginResolvesLeaderFromRemoteProfile() {
        val fm = com.example.syncedn.data.database.FirestoreManager
        fm.clearLocalTeams()
        val prefs = FakePreferences()

        // Clean device: preferences has 0 keys
        assertTrue("Prefs must be empty initially on clean device", prefs.getAll().isEmpty())

        // Remote Firestore profile fetched after login
        val remoteProfile = com.example.syncedn.data.model.UserProfile(
            uid = "firebase_leader_uid_777",
            username = "ryanlloydgenturo",
            email = "ryanlloydgenturo@gmail.com",
            role = "Leader",
            teamName = "Ryan",
            teamCode = "MVBRWM",
            fullName = "Ryan Lloyd Genturo",
            ministryRole = "Worship Leader"
        )

        // SessionManager resolves session from remote profile
        val (finalRole, finalTeam, finalCode) = com.example.syncedn.data.auth.SessionManager.resolveUserTeamSession(
            preferences = prefs,
            profile = remoteProfile,
            email = "ryanlloydgenturo@gmail.com"
        )

        assertEquals("Leader", finalRole)
        assertEquals("Ryan", finalTeam)
        assertEquals("MVBRWM", finalCode)
        assertEquals("Ryan", fm.getLocalTeamName("MVBRWM"))

        // Save session profile to clean device
        com.example.syncedn.data.auth.SessionManager.saveSessionProfile(
            preferences = prefs,
            profile = remoteProfile,
            fallbackUsername = "ryanlloydgenturo",
            fallbackEmail = "ryanlloydgenturo@gmail.com"
        )

        assertEquals("firebase_leader_uid_777", prefs.getString("userId", ""))
        assertEquals("ryanlloydgenturo@gmail.com", prefs.getString("email", ""))
        assertEquals("Ryan Lloyd Genturo", prefs.getString("fullName", ""))
        assertEquals("Worship Leader", prefs.getString("ministryRole", ""))
    }

    @Test
    fun testSessionManagerPreservesLocalTeamWhenOffline() {
        val fm = com.example.syncedn.data.database.FirestoreManager
        fm.clearLocalTeams()
        val prefs = FakePreferences()

        // Offline device has cached role and team in preferences
        prefs.edit()
            .putString("email_role_leader@synced.org", "Leader")
            .putString("email_team_leader@synced.org", "Worship Team")
            .putString("email_code_leader@synced.org", "WRSHP1")
            .apply()

        // Remote profile is null (offline network timeout)
        val (finalRole, finalTeam, finalCode) = com.example.syncedn.data.auth.SessionManager.resolveUserTeamSession(
            preferences = prefs,
            profile = null,
            email = "leader@synced.org"
        )

        assertEquals("Leader", finalRole)
        assertEquals("Worship Team", finalTeam)
        assertEquals("WRSHP1", finalCode)
        assertEquals("Worship Team", fm.getLocalTeamName("WRSHP1"))
    }

    @Test
    fun testRealFirebaseUidTakesPrecedenceOverDummyHashCodeUid() {
        val prefs = FakePreferences()

        // Stale preference had a hashCode dummy UID
        prefs.edit()
            .putString("userId", "user_1227807481")
            .putString("email", "ryanlloydgenturo@gmail.com")
            .apply()

        // Fresh profile with real Firebase Auth UID
        val realProfile = com.example.syncedn.data.model.UserProfile(
            uid = "actual_firebase_uid_abc",
            username = "ryanlloydgenturo",
            email = "ryanlloydgenturo@gmail.com",
            role = "Leader",
            teamName = "Ryan",
            teamCode = "MVBRWM"
        )

        com.example.syncedn.data.auth.SessionManager.saveSessionProfile(
            preferences = prefs,
            profile = realProfile
        )

        // Must be updated to the genuine Firebase UID, not the dummy hashCode UID
        assertEquals("actual_firebase_uid_abc", prefs.getString("userId", ""))
    }

    @Test
    fun testTeamRepositoryRosterThrottlingAndInFlightGuard() {
        val repo = com.example.syncedn.data.database.TeamRepository
        repo.clearCache()

        val member = com.example.syncedn.data.model.TeamMember(
            uid = "member_1",
            fullName = "Joshua Lagare",
            username = "joshua",
            email = "joshua@test.com",
            role = "Leader",
            ministryRole = "Worship Leader",
            teamCode = "THROT1"
        )
        repo.cacheMember(member)

        var callCount = 0
        // First call should return cached roster
        repo.getTeamRoster("THROT1") { result ->
            callCount++
            assertTrue(result.isSuccess)
            assertEquals(1, result.getOrNull()?.size)
        }

        // Rapid second call should return cached roster immediately via throttling
        repo.getTeamRoster("THROT1") { result ->
            callCount++
            assertTrue(result.isSuccess)
            assertEquals(1, result.getOrNull()?.size)
        }

        assertEquals(2, callCount)
    }

    @Test
    fun testServiceDataSerializationAndDeserialization() {
        val assignment = com.example.syncedn.data.model.ServiceAssignment(
            uid = "user_123",
            email = "member@example.com",
            name = "John Doe",
            role = "Guitarist"
        )
        val rehearsal = com.example.syncedn.data.model.RehearsalData(
            date = "2026-10-24",
            start = "18:00",
            end = "19:30",
            venue = "Fellowship Hall",
            notes = "Review tempo for Song 2"
        )
        val song = com.example.syncedn.data.model.ServiceSong(
            title = "Way Maker",
            artist = "Sinach",
            key = "E",
            bpm = 68
        )
        val service = com.example.syncedn.data.model.ServiceData(
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

        val restored = com.example.syncedn.data.model.ServiceData.fromMap(map)
        assertEquals(service.id, restored.id)
        assertEquals(service.title, restored.title)
        assertEquals(1, restored.assignments.size)
        assertEquals("Guitarist", restored.assignments[0].role)
        assertEquals("Fellowship Hall", restored.rehearsal.venue)
        assertEquals(1, restored.songs.size)
        assertEquals(68, restored.songs[0].bpm)
    }

    @Test
    fun testTeamRepositoryServiceCachingAndFiltering() {
        val prefs = FakePreferences()
        val draftService = com.example.syncedn.data.model.ServiceData(
            id = "srv_draft",
            teamCode = "ALPHA1",
            title = "Midweek Service",
            status = "Draft"
        )
        val pubService = com.example.syncedn.data.model.ServiceData(
            id = "srv_pub",
            teamCode = "ALPHA1",
            title = "Sunday Service",
            status = "Published"
        )

        val repo = com.example.syncedn.data.database.TeamRepository
        repo.saveServiceLocally("ALPHA1", draftService, prefs)
        repo.saveServiceLocally("ALPHA1", pubService, prefs)

        val cached = repo.getCachedServices("ALPHA1", prefs)
        assertEquals(2, cached.size)

        val forMembers = cached.filter { it.status == "Published" }
        assertEquals(1, forMembers.size)
        assertEquals("Sunday Service", forMembers[0].title)
    }

    @Test
    fun testServiceDataUpdatePreservesImmutableFields() {
        val initialCreatedAt = 1715000000000L
        val originalService = com.example.syncedn.data.model.ServiceData(
            id = "service_immutable_01",
            teamCode = "PRAISE_A",
            title = "Initial Draft Service",
            date = "2026-10-25",
            start = "09:00",
            end = "11:00",
            venue = "Main Hall",
            status = "Draft",
            createdBy = "leader_uid_original",
            createdAt = initialCreatedAt,
            updatedAt = initialCreatedAt
        )

        // Simulate update: change status to "Published", change venue, update title
        val newUpdatedAt = System.currentTimeMillis()
        val updatedService = originalService.copy(
            title = "Finalized Sunday Service",
            venue = "New Sanctuary",
            status = "Published",
            updatedAt = newUpdatedAt
        )

        // Strict validation: Immutable fields must match exactly
        assertEquals("service_immutable_01", updatedService.id)
        assertEquals("PRAISE_A", updatedService.teamCode)
        assertEquals("leader_uid_original", updatedService.createdBy)
        assertEquals(initialCreatedAt, updatedService.createdAt)

        // Mutable fields are updated
        assertEquals("Finalized Sunday Service", updatedService.title)
        assertEquals("New Sanctuary", updatedService.venue)
        assertEquals("Published", updatedService.status)

        // Test Date / Timestamp reflection parsing in fromMap
        val testDate = java.util.Date(1720000000000L)
        val mapWithDate = mapOf<String, Any?>(
            "id" to "date_test_srv",
            "teamCode" to "TEAM_D",
            "title" to "Date Test",
            "createdAt" to testDate,
            "updatedAt" to 1720000005000L
        )
        val fromDateService = com.example.syncedn.data.model.ServiceData.fromMap(mapWithDate)
        assertEquals(1720000000000L, fromDateService.createdAt)
        assertEquals(1720000005000L, fromDateService.updatedAt)
    }

    @Test
    fun testSongDataSerializationAndDeserialization() {
        val song = com.example.syncedn.data.model.SongData(
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

        val fromMap = com.example.syncedn.data.model.SongData.fromMap(map)
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

        // Test string BPM coercion
        val mapWithStringBpm = HashMap<String, Any?>(map).apply { put("bpm", "82") }
        val coerced = com.example.syncedn.data.model.SongData.fromMap(mapWithStringBpm)
        assertEquals(82, coerced.bpm)
    }

    @Test
    fun testTeamRepositorySongCachingAndSeedFallback() {
        val prefs = FakePreferences()
        val repo = com.example.syncedn.data.database.TeamRepository

        // 1. Verify default seed songs
        val seeds = repo.getDefaultSeedSongs("UPJAT7")
        assertTrue(seeds.isNotEmpty())
        assertTrue(seeds.any { it.title == "Praise" })
        assertTrue(seeds.any { it.title == "Way Maker" })
        assertTrue(seeds.any { it.title == "Trust in God" })

        // 2. When cache is empty, getCachedSongs returns seed songs
        val cached = repo.getCachedSongs("UPJAT7", prefs)
        assertEquals(seeds.size, cached.size)
        assertEquals(seeds.first().title, cached.first().title)

        // 3. Save a new custom song locally
        val customSong = com.example.syncedn.data.model.SongData(
            id = "song_custom_1",
            teamCode = "UPJAT7",
            title = "Living Hope",
            artist = "Phil Wickham",
            key = "D",
            bpm = 72
        )
        repo.saveSongLocally("UPJAT7", customSong, prefs)

        val updated = repo.getCachedSongs("UPJAT7", prefs)
        assertTrue(updated.any { it.id == "song_custom_1" && it.title == "Living Hope" })
    }

    @Test
    fun testTeamRepositorySongOptimisticUpdateAndDeletion() {
        val prefs = FakePreferences()
        val repo = com.example.syncedn.data.database.TeamRepository

        val song = com.example.syncedn.data.model.SongData(
            id = "song_edit_1",
            teamCode = "TEAM_B",
            title = "Goodness of God",
            key = "G",
            bpm = 70
        )
        repo.saveSongLocally("TEAM_B", song, prefs)
        assertEquals("G", repo.getCachedSongs("TEAM_B", prefs).find { it.id == "song_edit_1" }?.key)

        // Edit key to A
        val editedSong = song.copy(key = "A")
        repo.saveSongLocally("TEAM_B", editedSong, prefs)
        assertEquals("A", repo.getCachedSongs("TEAM_B", prefs).find { it.id == "song_edit_1" }?.key)

        // Delete song
        repo.removeSongLocally("TEAM_B", "song_edit_1", prefs)
        assertNull(repo.getCachedSongs("TEAM_B", prefs).find { it.id == "song_edit_1" })
    }

    @Test
    fun testSongDataUrlNormalizationAndCreatorPreservation() {
        // 1. Creator resolution
        val originalCreator = "creator_uid_123"
        val currentLoggedInUser = "different_user_456"

        val resolvedExisting = com.example.syncedn.data.model.SongData.resolveCreatorUid(originalCreator, currentLoggedInUser)
        assertEquals("Original creator UID must be preserved on edit", "creator_uid_123", resolvedExisting)

        val resolvedNew = com.example.syncedn.data.model.SongData.resolveCreatorUid(null, currentLoggedInUser)
        assertEquals("Fallback UID must be used for new songs", "different_user_456", resolvedNew)

        val resolvedBlank = com.example.syncedn.data.model.SongData.resolveCreatorUid("", currentLoggedInUser)
        assertEquals("Fallback UID must be used if existing is blank", "different_user_456", resolvedBlank)

        // 2. Reference URL normalization
        assertEquals("Blank remains blank", "", com.example.syncedn.data.model.SongData.normalizeReferenceUrl(""))
        assertEquals("Whitespace remains blank", "", com.example.syncedn.data.model.SongData.normalizeReferenceUrl("   "))
        assertEquals("https URL preserved", "https://youtu.be/xyz", com.example.syncedn.data.model.SongData.normalizeReferenceUrl("https://youtu.be/xyz"))
        assertEquals("http URL preserved", "http://example.com", com.example.syncedn.data.model.SongData.normalizeReferenceUrl("http://example.com"))
        assertEquals("Bare domain gets https", "https://youtu.be/xyz", com.example.syncedn.data.model.SongData.normalizeReferenceUrl("youtu.be/xyz"))
        assertEquals("www domain gets https", "https://www.youtube.com/watch?v=123", com.example.syncedn.data.model.SongData.normalizeReferenceUrl("www.youtube.com/watch?v=123"))
    }

    @Test
    fun testSongDataYoutubeThumbnailExtractionAndSeedCatalogMetadata() {
        val sd = com.example.syncedn.data.model.SongData

        // 1. YouTube Video ID Extraction
        assertEquals("f2OXgn6mHHY", sd.extractYoutubeVideoId("https://www.youtube.com/watch?v=f2OXgn6mHHY"))
        assertEquals("y7495tY_k_8", sd.extractYoutubeVideoId("https://youtu.be/y7495tY_k_8"))
        assertEquals("dQw4w9WgXcQ", sd.extractYoutubeVideoId("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", sd.extractYoutubeVideoId("https://www.youtube.com/watch?feature=shared&v=dQw4w9WgXcQ&t=120"))
        assertEquals("Z32HiSuUY8o", sd.extractYoutubeVideoId("https://www.youtube.com/embed/Z32HiSuUY8o"))
        assertEquals("y7495tY_k_8", sd.extractYoutubeVideoId("http://youtu.be/y7495tY_k_8?t=45"))
        assertNull(sd.extractYoutubeVideoId(""))
        assertNull(sd.extractYoutubeVideoId("   "))
        assertNull(sd.extractYoutubeVideoId("https://example.com/not-youtube"))

        // 2. YouTube Thumbnail URL Generation
        assertEquals(
            "https://img.youtube.com/vi/f2OXgn6mHHY/hqdefault.jpg",
            sd.getYoutubeThumbnailUrl("https://www.youtube.com/watch?v=f2OXgn6mHHY")
        )
        assertEquals(
            "https://img.youtube.com/vi/y7495tY_k_8/hqdefault.jpg",
            sd.getYoutubeThumbnailUrl("https://youtu.be/y7495tY_k_8")
        )
        assertNull(sd.getYoutubeThumbnailUrl("invalid"))

        // 3. Verify Seed Catalog has correct Keys, BPMs, and YouTube Reference URLs
        val seeds = com.example.syncedn.data.database.TeamRepository.getDefaultSeedSongs("TEST")
        assertEquals(7, seeds.size)
        val praise = seeds.find { it.title == "Praise" }!!
        assertEquals("Elevation Worship", praise.artist)
        assertEquals("G", praise.key)
        assertEquals(127, praise.bpm)
        assertEquals("https://www.youtube.com/watch?v=f2OXgn6mHHY", praise.referenceUrl)
        assertTrue("Praise sheet must contain chords", praise.sheet.contains("[G]"))

        val trust = seeds.find { it.title == "Trust in God" }!!
        assertEquals("C", trust.key)
        assertEquals(74, trust.bpm)
        assertEquals("https://www.youtube.com/watch?v=y7495tY_k_8", trust.referenceUrl)

        val wayMaker = seeds.find { it.title == "Way Maker" }!!
        assertEquals("E", wayMaker.key)
        assertEquals(68, wayMaker.bpm)
        assertEquals("https://www.youtube.com/watch?v=n4XWfwLHeLM", wayMaker.referenceUrl)

        val gratitude = seeds.find { it.title == "Gratitude" }!!
        assertEquals("B", gratitude.key)
        assertEquals(78, gratitude.bpm)

        val goodness = seeds.find { it.title == "Goodness of God" }!!
        assertEquals("Ab", goodness.key)
        assertEquals(70, goodness.bpm)
    }

    @Test
    fun testChordSheetHasChordsAndStanzaFormatting() {
        val chordSheet = "[Verse 1]\n[G]Let everything that has breath\nPraise the Lord\n[Chorus]\n[Em]Praise the Lord"
        assertTrue(com.example.syncedn.ChordSheet.hasChords(chordSheet))

        val plainLyrics = "I just wanna speak the name of Jesus\nOver every heart and every mind\n'Cause I know there is peace within Your presence"
        assertFalse(com.example.syncedn.ChordSheet.hasChords(plainLyrics))

        val unspacedSections = "Verse 1\nLine 1\nChorus\nLine 2\nBridge\nLine 3"
        val formatted = com.example.syncedn.ChordSheet.formatStanzas(unspacedSections)
        assertTrue("Formatted text should have double newlines before Chorus", formatted.contains("\n\n[Chorus]"))
        assertTrue("Formatted text should have double newlines before Bridge", formatted.contains("\n\n[Bridge]"))
    }

    @Test
    fun testSongCatalogPreservesSeedsWhenCustomSongAdded() {
        val prefs = FakePreferences()
        val repo = com.example.syncedn.data.database.TeamRepository
        val code = "TESTTEAM"

        // 1. Initial cached songs should contain 7 seeds
        val initial = repo.getCachedSongs(code, prefs)
        assertEquals(7, initial.size)

        // 2. Add custom song "I Speak Jesus"
        val customSong = com.example.syncedn.data.model.SongData(
            id = "custom_speak_jesus",
            teamCode = code,
            title = "I Speak Jesus",
            artist = "Charity Gayle",
            key = "E",
            bpm = 74,
            sheet = "I just wanna speak the name of Jesus",
            lyrics = "I just wanna speak the name of Jesus"
        )
        repo.saveSongLocally(code, customSong, prefs)

        // 3. getCachedSongs must now contain 8 songs (custom song + all 7 original seeds)
        val updated = repo.getCachedSongs(code, prefs)
        assertEquals(8, updated.size)
        assertTrue(updated.any { it.title == "I Speak Jesus" })
        assertTrue(updated.any { it.title == "Praise" })
        assertTrue(updated.any { it.title == "Trust in God" })
        assertTrue(updated.any { it.title == "Way Maker" })
        assertTrue(updated.any { it.title == "Gratitude" })

        // 4. Deleting a song marks it deleted
        repo.deleteSong(code, "seed_praise", prefs)
        val afterDelete = repo.getCachedSongs(code, prefs)
        assertEquals(7, afterDelete.size)
        assertFalse(afterDelete.any { it.title == "Praise" })
        assertTrue(afterDelete.any { it.title == "I Speak Jesus" })
    }

    @Test
    fun testServiceSongUnpackDefensiveRobustness() {
        // 1. Plain title string
        val plain = com.example.syncedn.data.model.ServiceSong.unpack("Build My Life")
        assertEquals("Build My Life", plain.title)
        assertEquals("", plain.artist)
        assertEquals("C", plain.key)
        assertEquals(74, plain.bpm)

        // 2. Exact raw JSON string from user bug report
        val jsonString = "{\"title\":\"Amazing Grace\",\"artist\":\"John Newton\",\"key\":\"C\",\"bpm\":74}"
        val fromJsonStr = com.example.syncedn.data.model.ServiceSong.unpack(jsonString)
        assertEquals("Amazing Grace", fromJsonStr.title)
        assertEquals("John Newton", fromJsonStr.artist)
        assertEquals("C", fromJsonStr.key)
        assertEquals(74, fromJsonStr.bpm)

        // 3. Doubly-nested JSON (title contains stringified JSON object)
        val nestedJsonObj = org.json.JSONObject()
            .put("title", "{\"title\":\"Way Maker\",\"artist\":\"Sinach\",\"key\":\"E\",\"bpm\":68}")
            .put("key", "C")
            .put("bpm", 74)
        val fromNested = com.example.syncedn.data.model.ServiceSong.unpack(nestedJsonObj)
        assertEquals("Way Maker", fromNested.title)
        assertEquals("Sinach", fromNested.artist)
        assertEquals("E", fromNested.key)
        assertEquals(68, fromNested.bpm)

        // 4. Map representation
        val map = mapOf("title" to "Goodness of God", "artist" to "Bethel Music", "key" to "Ab", "bpm" to 70)
        val fromMap = com.example.syncedn.data.model.ServiceSong.fromMap(map)
        assertEquals("Goodness of God", fromMap.title)
        assertEquals("Bethel Music", fromMap.artist)
        assertEquals("Ab", fromMap.key)
        assertEquals(70, fromMap.bpm)

        // 5. ServiceData.fromMap deserializing songs array containing JSON strings
        val serviceMap = mapOf(
            "id" to "srv_test",
            "title" to "Sunday Service",
            "songs" to listOf(jsonString, "Praise")
        )
        val service = com.example.syncedn.data.model.ServiceData.fromMap(serviceMap)
        assertEquals(2, service.songs.size)
        assertEquals("Amazing Grace", service.songs[0].title)
        assertEquals("John Newton", service.songs[0].artist)
        assertEquals("Praise", service.songs[1].title)
    }

    @Test
    fun testChordSheetStanzaFormattingAndAutoDetection() {
        // 1. Header normalization from raw unbracketed text
        val rawInput = "Verse 1:\nAmazing grace how sweet the sound\nChorus:\nMy chains are gone I've been set free\nBridge:\nThe Lord has promised good to me"

        val formatted = com.example.syncedn.ChordSheet.formatStanzas(rawInput)
        assertTrue("Must normalize Verse 1 header into [Verse 1]", formatted.contains("[Verse 1]"))
        assertTrue("Must normalize Chorus header into [Chorus]", formatted.contains("[Chorus]"))
        assertTrue("Must normalize Bridge header into [Bridge]", formatted.contains("[Bridge]"))
        assertTrue("Must cleanly separate stanzas with double newline", formatted.contains("\n\n[Chorus]\n"))
        assertTrue("Must cleanly separate stanzas with double newline", formatted.contains("\n\n[Bridge]\n"))

        // 2. autoDetectSections on text with NO headers
        val unheadedText = "Amazing grace how sweet the sound\nThat saved a wretch like me\n\nMy chains are gone\nI've been set free\n\nThe earth shall soon dissolve like snow"

        val autoDetected = com.example.syncedn.ChordSheet.autoDetectSections(unheadedText)
        assertTrue("Paragraph 1 should be detected as [Verse 1]", autoDetected.startsWith("[Verse 1]\n"))
        assertTrue("Paragraph 2 should be detected as [Chorus]", autoDetected.contains("\n\n[Chorus]\n"))
        assertTrue("Paragraph 3 should be detected as [Verse 2]", autoDetected.contains("\n\n[Verse 2]\n"))

        // 3. Transpose rendering
        val chordSource = "[C]Amazing [G]grace"
        val transposedD = com.example.syncedn.ChordSheet.render(chordSource, 2, false, false)
        assertTrue("C shifted by 2 semitones must become D", transposedD.contains("D") && transposedD.contains("A"))
    }

    @Test
    fun testChordPaletteAndSectionSplitting() {
        // 1. Test getChordPalette
        val cPalette = com.example.syncedn.ChordSheet.getChordPalette("C")
        assertTrue(cPalette.contains("C"))
        assertTrue(cPalette.contains("F"))
        assertTrue(cPalette.contains("G"))
        assertTrue(cPalette.contains("Am"))
        assertTrue(cPalette.contains("Dm"))
        assertTrue(cPalette.contains("Em"))

        val dmPalette = com.example.syncedn.ChordSheet.getChordPalette("Dm")
        assertTrue(dmPalette.contains("Dm"))
        assertTrue(dmPalette.contains("Bb"))
        assertTrue(dmPalette.contains("F"))
        assertTrue(dmPalette.contains("C"))
        assertTrue(dmPalette.contains("Gm"))
        assertTrue(dmPalette.contains("A7"))

        // 2. Test splitSections
        val rawSong = """
            [Verse 1]
            [Dm]Burn the ships, cut the [Bb]ties

            [Chorus]
            [F]Step into the [C]light

            [Verse 2]
            [Dm]Out of the shadows [Bb]into the day
        """.trimIndent()

        val sections = com.example.syncedn.ChordSheet.splitSections(rawSong)
        assertEquals(3, sections.size)
        assertEquals("[Verse 1]", sections[0].first)
        assertTrue(sections[0].second.contains("[Dm]Burn the ships"))
        assertEquals("[Chorus]", sections[1].first)
        assertTrue(sections[1].second.contains("[F]Step into the [C]light"))
        assertEquals("[Verse 2]", sections[2].first)
        assertTrue(sections[2].second.contains("[Dm]Out of the shadows"))

        // 3. Test assembleSections
        val assembled = com.example.syncedn.ChordSheet.assembleSections(sections)
        assertTrue(assembled.contains("[Verse 1]\n[Dm]Burn the ships, cut the [Bb]ties"))
        assertTrue(assembled.contains("[Chorus]\n[F]Step into the [C]light"))
        assertTrue(assembled.contains("[Verse 2]\n[Dm]Out of the shadows [Bb]into the day"))

        // 4. Test rendering of the assembled song
        val rendered = com.example.syncedn.ChordSheet.render(assembled, 0, false, true)
        assertTrue("Rendered must put chords above lyrics", rendered.contains("Dm") && rendered.contains("Burn the ships"))
    }

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
        assertEquals("Please be informed that Saturday rehearsal is moved to 4 PM.", map["message"])
        assertEquals(true, map["urgent"])
        assertEquals("RYAN LLOYD", map["author"])
        assertEquals("h9yRCswmV1f0Pnia2EnxDv4x4X43", map["createdBy"])
        assertEquals(1792330000000L, map["createdAt"])
        assertEquals(1792330000000L, map["updatedAt"])

        val restored = com.example.syncedn.data.model.AnnouncementData.fromMap(map)
        assertEquals(announcement.id, restored.id)
        assertEquals(announcement.teamCode, restored.teamCode)
        assertEquals(announcement.title, restored.title)
        assertEquals(announcement.message, restored.message)
        assertEquals(announcement.urgent, restored.urgent)
        assertEquals(announcement.author, restored.author)
        assertEquals(announcement.createdBy, restored.createdBy)
        assertEquals(announcement.createdAt, restored.createdAt)
        assertEquals(announcement.updatedAt, restored.updatedAt)
    }

    @Test
    fun testTeamRepositoryAnnouncementCachingAndSorting() {
        val repo = com.example.syncedn.data.database.TeamRepository
        val prefs = FakePreferences()
        val code = "NGK8VN"

        // 1. Initial cached announcements should be empty
        val initial = repo.getCachedAnnouncements(code, prefs)
        assertTrue(initial.isEmpty())

        // 2. Add first announcement
        val ann1 = com.example.syncedn.data.model.AnnouncementData(
            id = "ann_1",
            teamCode = code,
            title = "Welcome Team",
            message = "Welcome to the new worship season!",
            urgent = false,
            author = "Ryan",
            createdAt = 1000L
        )
        repo.saveAnnouncementLocally(code, ann1, prefs)

        // 3. Add second announcement (newer timestamp, urgent)
        val ann2 = com.example.syncedn.data.model.AnnouncementData(
            id = "ann_2",
            teamCode = code,
            title = "Urgent: Sound Check Moved",
            message = "Sound check is moved to 7:00 AM.",
            urgent = true,
            author = "Ryan",
            createdAt = 2000L
        )
        repo.saveAnnouncementLocally(code, ann2, prefs)

        // 4. Verify sorting: ann2 (newer, 2000L) must appear before ann1 (1000L)
        val cached = repo.getCachedAnnouncements(code, prefs)
        assertEquals(2, cached.size)
        assertEquals("ann_2", cached[0].id)
        assertEquals(true, cached[0].urgent)
        assertEquals("ann_1", cached[1].id)

        // 5. Delete an announcement
        repo.deleteAnnouncement(code, "ann_1", prefs)
        val afterDelete = repo.getCachedAnnouncements(code, prefs)
        assertEquals(1, afterDelete.size)
        assertEquals("ann_2", afterDelete[0].id)
    }

    @Test
    fun testAnnouncementDataMissingFieldsAndTimestampCoercion() {
        val date = java.util.Date(1792330000000L)
        val sparseMap = mapOf<String, Any?>(
            "id" to "ann_sparse",
            "title" to "Sparse Title",
            "createdAt" to com.google.firebase.Timestamp(date)
        )
        val data = com.example.syncedn.data.model.AnnouncementData.fromMap(sparseMap)
        assertEquals("ann_sparse", data.id)
        assertEquals("Sparse Title", data.title)
        assertEquals("", data.teamCode)
        assertEquals("", data.message)
        assertEquals(false, data.urgent)
        assertEquals("", data.author)
        assertEquals("", data.createdBy)
        assertEquals(1792330000000L, data.createdAt)
        assertTrue(data.updatedAt > 0L)
    }

    @Test
    fun testSubstitutionDataSerializationAndDeserialization() {
        val original = com.example.syncedn.data.model.SubstitutionData(
            id = "sub_101",
            teamCode = "74XWB7",
            serviceId = "srv_202",
            serviceTitle = "Sunday Worship Service",
            serviceDate = "2026-10-25",
            requesterUid = "req_user_1",
            requesterName = "Angelo Daniel",
            role = "Keyboardist",
            substituteUid = "sub_user_2",
            substituteName = "Ryan Lloyd Genturo",
            reason = "School examination out of town",
            status = "Pending",
            createdBy = "req_user_1",
            createdAt = 1792400000000L,
            updatedAt = 1792400000000L
        )

        val map = original.toMap()
        assertEquals("sub_101", map["id"])
        assertEquals("74XWB7", map["teamCode"])
        assertEquals("srv_202", map["serviceId"])
        assertEquals("Sunday Worship Service", map["serviceTitle"])
        assertEquals("2026-10-25", map["serviceDate"])
        assertEquals("req_user_1", map["requesterUid"])
        assertEquals("Angelo Daniel", map["requesterName"])
        assertEquals("Keyboardist", map["role"])
        assertEquals("sub_user_2", map["substituteUid"])
        assertEquals("Ryan Lloyd Genturo", map["substituteName"])
        assertEquals("School examination out of town", map["reason"])
        assertEquals("Pending", map["status"])
        assertEquals("req_user_1", map["createdBy"])
        assertEquals(1792400000000L, map["createdAt"])
        assertEquals(1792400000000L, map["updatedAt"])

        val deserialized = com.example.syncedn.data.model.SubstitutionData.fromMap(map)
        assertEquals(original.id, deserialized.id)
        assertEquals(original.teamCode, deserialized.teamCode)
        assertEquals(original.serviceId, deserialized.serviceId)
        assertEquals(original.serviceTitle, deserialized.serviceTitle)
        assertEquals(original.serviceDate, deserialized.serviceDate)
        assertEquals(original.requesterUid, deserialized.requesterUid)
        assertEquals(original.requesterName, deserialized.requesterName)
        assertEquals(original.role, deserialized.role)
        assertEquals(original.substituteUid, deserialized.substituteUid)
        assertEquals(original.substituteName, deserialized.substituteName)
        assertEquals(original.reason, deserialized.reason)
        assertEquals(original.status, deserialized.status)
        assertEquals(original.createdBy, deserialized.createdBy)
        assertEquals(original.createdAt, deserialized.createdAt)
        assertEquals(original.updatedAt, deserialized.updatedAt)

        // Test sparse map handling
        val sparse = com.example.syncedn.data.model.SubstitutionData.fromMap(mapOf(
            "id" to "sub_sparse",
            "serviceTitle" to "Midweek Service"
        ))
        assertEquals("sub_sparse", sparse.id)
        assertEquals("Midweek Service", sparse.serviceTitle)
        assertEquals("Pending", sparse.status)
        assertEquals("", sparse.reason)
        assertTrue(sparse.createdAt > 0L)
    }

    @Test
    fun testTeamRepositorySubstitutionCachingAndFiltering() {
        val prefs = FakePreferences()
        val repo = com.example.syncedn.data.database.TeamRepository
        val code = "SUBTEST"

        // 1. Initial cached list should be empty
        val initial = repo.getCachedSubstitutions(code, prefs)
        assertTrue(initial.isEmpty())

        // 2. Add first substitution
        val sub1 = com.example.syncedn.data.model.SubstitutionData(
            id = "sub_1",
            teamCode = code,
            serviceTitle = "Sunday Worship",
            serviceDate = "2026-10-25",
            requesterUid = "req_1",
            requesterName = "Angelo Daniel",
            role = "Keyboardist",
            substituteUid = "sub_1",
            substituteName = "Ryan Lloyd Genturo",
            reason = "School exam",
            status = "Pending",
            createdBy = "req_1",
            createdAt = 1000L
        )
        repo.saveSubstitutionLocally(code, sub1, prefs)

        // 3. Add second substitution (newer timestamp)
        val sub2 = com.example.syncedn.data.model.SubstitutionData(
            id = "sub_2",
            teamCode = code,
            serviceTitle = "Midweek Service",
            serviceDate = "2026-10-28",
            requesterUid = "req_2",
            requesterName = "John Doe",
            role = "Guitarist",
            substituteUid = "sub_2",
            substituteName = "Kid Gamboa",
            reason = "Family emergency",
            status = "Accepted",
            createdBy = "req_2",
            createdAt = 2000L
        )
        repo.saveSubstitutionLocally(code, sub2, prefs)

        // 4. Verify sorting: sub2 (2000L) must appear before sub1 (1000L)
        val cached = repo.getCachedSubstitutions(code, prefs)
        assertEquals(2, cached.size)
        assertEquals("sub_2", cached[0].id)
        assertEquals("Accepted", cached[0].status)
        assertEquals("sub_1", cached[1].id)
        assertEquals("Pending", cached[1].status)

        // 5. Update status of sub1 locally to Accepted
        repo.updateSubstitutionStatusLocally(code, "sub_1", "Accepted", prefs)
        val afterUpdate = repo.getCachedSubstitutions(code, prefs)
        val updatedSub1 = afterUpdate.find { it.id == "sub_1" }
        assertNotNull(updatedSub1)
        assertEquals("Accepted", updatedSub1?.status)

        // 6. Delete a substitution
        repo.deleteSubstitution(code, "sub_2", prefs)
        val afterDelete = repo.getCachedSubstitutions(code, prefs)
        assertEquals(1, afterDelete.size)
        assertEquals("sub_1", afterDelete[0].id)
    }

    @Test
    fun testServiceRosterReplacementOnAccept() {
        val repo = com.example.syncedn.data.database.TeamRepository
        val originalService = com.example.syncedn.data.model.ServiceData(
            id = "srv_test_100",
            teamCode = "74XWB7",
            title = "Sunday Worship Service",
            date = "2026-10-25",
            start = "08:00",
            end = "10:30",
            venue = "Main Sanctuary",
            assignments = listOf(
                com.example.syncedn.data.model.ServiceAssignment(
                    uid = "uid_angelo",
                    name = "Angelo Daniel",
                    role = "Keyboardist"
                ),
                com.example.syncedn.data.model.ServiceAssignment(
                    uid = "uid_kid",
                    name = "Kid Gamboa",
                    role = "Bassist"
                )
            ),
            songs = listOf(
                com.example.syncedn.data.model.ServiceSong(title = "Goodness of God")
            )
        )

        val subRequest = com.example.syncedn.data.model.SubstitutionData(
            id = "sub_999",
            teamCode = "74XWB7",
            serviceId = "srv_test_100",
            serviceTitle = "Sunday Worship Service",
            serviceDate = "2026-10-25",
            requesterUid = "uid_angelo",
            requesterName = "Angelo Daniel",
            role = "Keyboardist",
            substituteUid = "uid_ryan",
            substituteName = "Ryan Lloyd Genturo",
            reason = "Out of town",
            status = "Accepted"
        )

        val updatedService = repo.replaceAssignmentInService(originalService, subRequest)

        // Assert Keyboardist is now Ryan Lloyd Genturo
        val keyboardist = updatedService.assignments.find { it.role == "Keyboardist" }
        assertNotNull(keyboardist)
        assertEquals("uid_ryan", keyboardist?.uid)
        assertEquals("Ryan Lloyd Genturo", keyboardist?.name)

        // Assert Bassist is unchanged
        val bassist = updatedService.assignments.find { it.role == "Bassist" }
        assertNotNull(bassist)
        assertEquals("uid_kid", bassist?.uid)
        assertEquals("Kid Gamboa", bassist?.name)

        // Assert songs and metadata are completely untouched
        assertEquals(1, updatedService.songs.size)
        assertEquals("Goodness of God", updatedService.songs[0].title)
        assertEquals("Main Sanctuary", updatedService.venue)
    }

    @Test
    fun testSubstitutionStatusBadgeMapping() {
        fun badgeLabel(status: String): String {
            val isPending = status.equals("Pending", ignoreCase = true)
            val isAccepted = status.equals("Accepted", ignoreCase = true)
            val isDeclined = status.equals("Declined", ignoreCase = true)
            return if (isPending) "WAITING FOR SUBSTITUTE"
            else if (isAccepted) "APPROVED"
            else if (isDeclined) "DECLINED"
            else status.uppercase()
        }

        assertEquals("WAITING FOR SUBSTITUTE", badgeLabel("Pending"))
        assertEquals("APPROVED", badgeLabel("Accepted"))
        assertEquals("DECLINED", badgeLabel("Declined"))
        assertEquals("CANCELLED", badgeLabel("Cancelled"))

        val repo = com.example.syncedn.data.database.TeamRepository
        val prefs = FakePreferences()
        val sub = com.example.syncedn.data.model.SubstitutionData(
            id = "sub_status_test",
            teamCode = "TEST01",
            serviceTitle = "Youth Service",
            requesterUid = "req_1",
            requesterName = "Member One",
            role = "Vocalist",
            substituteUid = "sub_1",
            substituteName = "Member Two",
            reason = "Sick leave",
            status = "Pending"
        )
        repo.saveSubstitutionLocally("TEST01", sub, prefs)
        val initial = repo.getCachedSubstitutions("TEST01", prefs).find { it.id == "sub_status_test" }
        assertEquals("Pending", initial?.status)
        assertEquals("WAITING FOR SUBSTITUTE", badgeLabel(initial!!.status))

        // Update to Accepted -> should map to APPROVED
        repo.updateSubstitutionStatusLocally("TEST01", "sub_status_test", "Accepted", prefs)
        val approved = repo.getCachedSubstitutions("TEST01", prefs).find { it.id == "sub_status_test" }
        assertEquals("Accepted", approved?.status)
        assertEquals("APPROVED", badgeLabel(approved!!.status))

        // Update to Declined -> should map to DECLINED
        repo.updateSubstitutionStatusLocally("TEST01", "sub_status_test", "Declined", prefs)
        val declined = repo.getCachedSubstitutions("TEST01", prefs).find { it.id == "sub_status_test" }
        assertEquals("Declined", declined?.status)
        assertEquals("DECLINED", badgeLabel(declined!!.status))
    }

    @Test
    fun testPhotoUriSerializationAndSessionManagement() {
        val testUrl = "https://res.cloudinary.com/ffb3fdkm/image/upload/v1234567890/synced_avatar.jpg"

        // 1. UserProfile serialization with photoUri
        val profile = com.example.syncedn.data.model.UserProfile(
            uid = "user_photo_test_123",
            username = "TestLeader",
            email = "leader@test.com",
            role = "Leader",
            teamName = "Praise Team",
            teamCode = "TEST01",
            photoUri = testUrl
        )
        val profileMap = profile.toMap()
        assertEquals(testUrl, profileMap["photoUri"])

        val restoredProfile = com.example.syncedn.data.model.UserProfile.fromMap(profileMap)
        assertEquals(testUrl, restoredProfile.photoUri)

        // 2. TeamMember serialization with photoUri
        val member = com.example.syncedn.data.model.TeamMember(
            uid = "member_photo_test_456",
            fullName = "John Worship",
            username = "johnw",
            email = "john@test.com",
            role = "Member",
            ministryRole = "Vocalist",
            teamCode = "TEST01",
            photoUri = testUrl
        )
        val memberMap = member.toMap()
        assertEquals(testUrl, memberMap["photoUri"])

        val restoredMember = com.example.syncedn.data.model.TeamMember.fromMap(memberMap)
        assertEquals(testUrl, restoredMember.photoUri)

        // 3. SessionManager photoUri persistence and user-scoped caching
        val prefs = FakePreferences()
        com.example.syncedn.data.auth.SessionManager.saveSessionProfile(
            prefs,
            profile,
            fallbackUsername = "TestLeader",
            fallbackEmail = "leader@test.com"
        )
        assertEquals(testUrl, prefs.getString(com.example.syncedn.data.auth.SessionManager.KEY_PHOTO_URI, ""))
        assertEquals(testUrl, prefs.getString("email_photo_leader@test.com", ""))
        assertEquals(testUrl, prefs.getString("user_photo_user_photo_test_123", ""))

        // 4. SessionManager.updatePhotoUri
        val updatedUrl = "https://res.cloudinary.com/ffb3fdkm/image/upload/v9876543210/new_avatar.jpg"
        com.example.syncedn.data.auth.SessionManager.updatePhotoUri(prefs, updatedUrl, "leader@test.com", "user_photo_test_123")
        assertEquals(updatedUrl, prefs.getString(com.example.syncedn.data.auth.SessionManager.KEY_PHOTO_URI, ""))
        assertEquals(updatedUrl, prefs.getString("email_photo_leader@test.com", ""))
        assertEquals(updatedUrl, prefs.getString("user_photo_user_photo_test_123", ""))

        // 5. Cloudinary config sanity
        assertEquals("ffb3fdkm", com.example.syncedn.data.storage.CloudinaryUploader.CLOUD_NAME)
        assertEquals("synced_preset", com.example.syncedn.data.storage.CloudinaryUploader.UPLOAD_PRESET)
    }

    private class FakePreferences : android.content.SharedPreferences {
        private val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = (map[key] as? Set<*>)?.mapNotNull { it?.toString() }?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): android.content.SharedPreferences.Editor = FakeEditor(map)
        override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class FakeEditor(private val target: MutableMap<String, Any?>) : android.content.SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removes = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): android.content.SharedPreferences.Editor { key?.let { pending[it] = value }; return this }
            override fun putStringSet(key: String?, values: MutableSet<String>?): android.content.SharedPreferences.Editor { key?.let { pending[it] = values }; return this }
            override fun putInt(key: String?, value: Int): android.content.SharedPreferences.Editor { key?.let { pending[it] = value }; return this }
            override fun putLong(key: String?, value: Long): android.content.SharedPreferences.Editor { key?.let { pending[it] = value }; return this }
            override fun putFloat(key: String?, value: Float): android.content.SharedPreferences.Editor { key?.let { pending[it] = value }; return this }
            override fun putBoolean(key: String?, value: Boolean): android.content.SharedPreferences.Editor { key?.let { pending[it] = value }; return this }
            override fun remove(key: String?): android.content.SharedPreferences.Editor { key?.let { removes.add(it); pending.remove(it) }; return this }
            override fun clear(): android.content.SharedPreferences.Editor { clearAll = true; return this }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearAll) target.clear()
                removes.forEach { target.remove(it) }
                pending.forEach { (k, v) -> if (v != null) target[k] = v else target.remove(k) }
            }
        }
    }

    @Test
    fun testChatMessageSerializationAndCaching() {
        val msg = com.example.syncedn.data.model.ChatMessage(
            id = "msg_001",
            teamCode = "TEST01",
            senderUid = "user_123",
            senderName = "Ryan Kyle",
            senderRole = "Worship Leader",
            senderPhotoUri = "https://res.cloudinary.com/ffb3fdkm/image/upload/v1234/test.jpg",
            text = "Welcome to team rehearsal this Sunday!",
            mediaUrl = "https://res.cloudinary.com/ffb3fdkm/image/upload/v5678/lineup.jpg",
            timestamp = 1729418400000L
        )

        val map = msg.toMap()
        assertEquals("msg_001", map["id"])
        assertEquals("TEST01", map["teamCode"])
        assertEquals("user_123", map["senderUid"])
        assertEquals("Ryan Kyle", map["senderName"])
        assertEquals("Worship Leader", map["senderRole"])
        assertEquals("https://res.cloudinary.com/ffb3fdkm/image/upload/v1234/test.jpg", map["senderPhotoUri"])
        assertEquals("Welcome to team rehearsal this Sunday!", map["text"])
        assertEquals("https://res.cloudinary.com/ffb3fdkm/image/upload/v5678/lineup.jpg", map["mediaUrl"])
        assertEquals(1729418400000L, map["timestamp"])

        val restored = com.example.syncedn.data.model.ChatMessage.fromMap(map)
        assertEquals(msg.id, restored.id)
        assertEquals(msg.teamCode, restored.teamCode)
        assertEquals(msg.senderUid, restored.senderUid)
        assertEquals(msg.senderName, restored.senderName)
        assertEquals(msg.senderRole, restored.senderRole)
        assertEquals(msg.senderPhotoUri, restored.senderPhotoUri)
        assertEquals(msg.text, restored.text)
        assertEquals(msg.mediaUrl, restored.mediaUrl)
        assertEquals(msg.timestamp, restored.timestamp)

        val fakePrefs = FakePreferences()
        com.example.syncedn.data.database.TeamRepository.saveChatMessageLocally("TEST01", msg, fakePrefs)

        val cached = com.example.syncedn.data.database.TeamRepository.getCachedChatMessages("TEST01", fakePrefs)
        assertEquals(1, cached.size)
        assertEquals("msg_001", cached[0].id)
        assertEquals("Ryan Kyle", cached[0].senderName)
        assertEquals("Welcome to team rehearsal this Sunday!", cached[0].text)
        assertEquals("https://res.cloudinary.com/ffb3fdkm/image/upload/v5678/lineup.jpg", cached[0].mediaUrl)
    }

    @Test
    fun testImageLoaderDownsamplingCalculations() {
        val sampleSizeChat = com.example.syncedn.data.storage.ImageLoader.calculateInSampleSize(4000, 3000, 800)
        assertEquals(4, sampleSizeChat)

        val sampleSizeMedium = com.example.syncedn.data.storage.ImageLoader.calculateInSampleSize(1200, 900, 800)
        assertEquals(1, sampleSizeMedium)

        val sampleSizeAvatar = com.example.syncedn.data.storage.ImageLoader.calculateInSampleSize(2000, 2000, 320)
        assertEquals(4, sampleSizeAvatar)

        val sampleSizeSmall = com.example.syncedn.data.storage.ImageLoader.calculateInSampleSize(300, 200, 800)
        assertEquals(1, sampleSizeSmall)
    }

    @Test
    fun testChatMessagesDoNotDisappearOnNewImageOrRemoteMerge() {
        val fakePrefs = FakePreferences()
        val team = "TEST_MERGE"

        val msg1 = com.example.syncedn.data.model.ChatMessage(
            id = "msg_1",
            teamCode = team,
            senderUid = "u1",
            senderName = "Ryan",
            text = "Hello team!",
            timestamp = 1000L
        )
        val msg2 = com.example.syncedn.data.model.ChatMessage(
            id = "msg_2",
            teamCode = team,
            senderUid = "u2",
            senderName = "Yan",
            text = "Rehearsal is on!",
            timestamp = 2000L
        )
        com.example.syncedn.data.database.TeamRepository.saveChatMessageLocally(team, msg1, fakePrefs)
        com.example.syncedn.data.database.TeamRepository.saveChatMessageLocally(team, msg2, fakePrefs)

        val cached = com.example.syncedn.data.database.TeamRepository.getCachedChatMessages(team, fakePrefs)
        assertEquals(2, cached.size)

        // Now user sends a new local image
        val newImgMsg = com.example.syncedn.data.model.ChatMessage(
            id = "msg_img_local",
            teamCode = team,
            senderUid = "u1",
            senderName = "Ryan",
            mediaUrl = "content://media/external/123",
            timestamp = 3000L
        )
        com.example.syncedn.data.database.TeamRepository.saveChatMessageLocally(team, newImgMsg, fakePrefs)
        val cachedWithImg = com.example.syncedn.data.database.TeamRepository.getCachedChatMessages(team, fakePrefs)
        assertEquals(3, cachedWithImg.size)
        assertEquals("msg_1", cachedWithImg[0].id)
        assertEquals("msg_2", cachedWithImg[1].id)
        assertEquals("msg_img_local", cachedWithImg[2].id)

        // Cloudinary upload succeeds -> updates the image message to https://
        val remoteImgMsg = newImgMsg.copy(mediaUrl = "https://res.cloudinary.com/ffb3fdkm/image/upload/photo.jpg")
        com.example.syncedn.data.database.TeamRepository.saveChatMessageLocally(team, remoteImgMsg, fakePrefs)

        val finalCached = com.example.syncedn.data.database.TeamRepository.getCachedChatMessages(team, fakePrefs)
        assertEquals(3, finalCached.size)
        assertEquals("msg_1", finalCached[0].id)
        assertEquals("msg_2", finalCached[1].id)
        assertEquals("msg_img_local", finalCached[2].id)
        assertEquals("https://res.cloudinary.com/ffb3fdkm/image/upload/photo.jpg", finalCached[2].mediaUrl)
    }
    @Test
    fun testTeamRosterPersistenceAndInstantCachedRetrieval() {
        val team = "TEST_ROSTER_SYNC"
        val fakePrefs = FakePreferences()

        val member1 = com.example.syncedn.data.model.TeamMember(
            uid = "uid_leader_1",
            fullName = "Joshua Lagare",
            username = "joshua",
            email = "joshua@syncedn.org",
            role = "Leader",
            ministryRole = "Worship Leader",
            teamCode = team,
            photoUri = "https://res.cloudinary.com/ffb3fdkm/image/upload/v1234/joshua.jpg"
        )
        val member2 = com.example.syncedn.data.model.TeamMember(
            uid = "uid_member_2",
            fullName = "Ryan Lloyd Genturo",
            username = "ryan",
            email = "rlgenturo@gmail.com",
            role = "Member",
            ministryRole = "Keyboardist",
            teamCode = team,
            photoUri = "https://res.cloudinary.com/ffb3fdkm/image/upload/v1234/ryan.jpg"
        )

        // 1. Save roster locally with preferences
        com.example.syncedn.data.database.TeamRepository.saveRosterLocally(team, listOf(member1, member2), fakePrefs)

        // Verify individual photo keys were seeded for 0ms lookup
        assertEquals("https://res.cloudinary.com/ffb3fdkm/image/upload/v1234/joshua.jpg", fakePrefs.getString("user_photo_uid_leader_1", ""))
        assertEquals("https://res.cloudinary.com/ffb3fdkm/image/upload/v1234/ryan.jpg", fakePrefs.getString("user_photo_uid_member_2", ""))
        assertEquals("https://res.cloudinary.com/ffb3fdkm/image/upload/v1234/ryan.jpg", fakePrefs.getString("email_photo_rlgenturo@gmail.com", ""))

        // 2. Simulate complete cold start by clearing all in-memory caches
        com.example.syncedn.data.database.TeamRepository.clearCache()

        // 3. getCachedRoster with preferences must immediately retrieve the persisted members from SharedPreferences
        val coldCached = com.example.syncedn.data.database.TeamRepository.getCachedRoster(team, fakePrefs)
        assertEquals(2, coldCached.size)
        val leader = coldCached.find { it.role == "Leader" }
        assertNotNull(leader)
        assertEquals("Joshua Lagare", leader!!.fullName)
        assertEquals("https://res.cloudinary.com/ffb3fdkm/image/upload/v1234/joshua.jpg", leader.photoUri)

        val member = coldCached.find { it.role == "Member" }
        assertNotNull(member)
        assertEquals("Ryan Lloyd Genturo", member!!.fullName)
        assertEquals("https://res.cloudinary.com/ffb3fdkm/image/upload/v1234/ryan.jpg", member.photoUri)
    }

    @Test
    fun testRosterPersistencePreservesUnavailableRangesAcrossColdStartAndSync() {
        val team = "TEST_AVAIL_SYNC"
        val fakePrefs = FakePreferences()
        com.example.syncedn.data.database.TeamRepository.clearCache()

        val vacayRange = com.example.syncedn.data.model.UnavailableRange(
            id = "range_vacay_1",
            startDate = "2026-10-25",
            endDate = "2026-10-27",
            reason = "Family Vacation"
        )
        val member1 = com.example.syncedn.data.model.TeamMember(
            uid = "uid_leader_1",
            fullName = "Joshua Lagare",
            username = "joshua",
            email = "joshua@syncedn.org",
            role = "Leader",
            ministryRole = "Worship Leader",
            teamCode = team
        )
        val member2 = com.example.syncedn.data.model.TeamMember(
            uid = "uid_member_2",
            fullName = "RL Gents",
            username = "rlgents",
            email = "rlgenturo@gmail.com",
            role = "Member",
            ministryRole = "Vocalist",
            teamCode = team,
            unavailableRanges = listOf(vacayRange)
        )

        // 1. Save roster locally with preferences (simulating Firestore roster fetch)
        com.example.syncedn.data.database.TeamRepository.saveRosterLocally(team, listOf(member1, member2), fakePrefs)

        // 2. Simulate cold start by clearing in-memory cache
        com.example.syncedn.data.database.TeamRepository.clearCache()

        // 3. getCachedRoster must deserialize unavailableRanges from JSONArray in SharedPreferences
        val coldCached = com.example.syncedn.data.database.TeamRepository.getCachedRoster(team, fakePrefs)
        val restoredMember = coldCached.find { it.uid == "uid_member_2" }
        assertNotNull(restoredMember)
        assertEquals("Unavailable ranges must survive JSONArray serialization in cached_roster", 1, restoredMember!!.unavailableRanges.size)
        assertEquals("Family Vacation", restoredMember.unavailableRanges[0].reason)

        // 4. syncFromPreferences must NOT overwrite unavailableRanges with emptyList()
        fakePrefs.edit()
            .putString("email_code_rlgenturo@gmail.com", team)
            .putString("email_role_rlgenturo@gmail.com", "Member")
            .putString("email_fullname_rlgenturo@gmail.com", "RL Gents")
            .putString("email_username_rlgenturo@gmail.com", "rlgents")
            .putString("email_ministry_rlgenturo@gmail.com", "Vocalist")
            .putString("email_uid_rlgenturo@gmail.com", "uid_member_2")
            .apply()
        com.example.syncedn.data.database.TeamRepository.syncFromPreferences(fakePrefs, team)

        val afterSync = com.example.syncedn.data.database.TeamRepository.getCachedRoster(team, fakePrefs)
        val memberAfterSync = afterSync.find { it.email == "rlgenturo@gmail.com" }
        assertNotNull(memberAfterSync)
        assertEquals("syncFromPreferences must preserve unavailableRanges", 1, memberAfterSync!!.unavailableRanges.size)
        assertEquals("Family Vacation", memberAfterSync.unavailableRanges[0].reason)

        // 5. Calling cacheMember with empty unavailableRanges (like saveUserTeamState) must preserve existing ranges
        com.example.syncedn.data.database.TeamRepository.cacheMember(
            member2.copy(unavailableRanges = emptyList()),
            fakePrefs
        )
        val afterCacheMember = com.example.syncedn.data.database.TeamRepository.getCachedRoster(team, fakePrefs)
        val memberAfterCache = afterCacheMember.find { it.uid == "uid_member_2" }
        assertNotNull(memberAfterCache)
        assertEquals("cacheMember must not wipe existing unavailableRanges", 1, memberAfterCache!!.unavailableRanges.size)

        // 6. Leader service creation check on 2026-10-26 must detect RL Gents as unavailable
        val (_, unavailableList) = com.example.syncedn.data.database.TeamRepository.checkTeamAvailabilityForDate(
            team,
            "2026-10-26"
        )
        assertEquals(1, unavailableList.size)
        assertEquals("RL Gents", unavailableList[0].first.fullName)
        assertEquals("Family Vacation", unavailableList[0].second)
    }

    @Test
    fun testMemberUnavailabilityPersistsAcrossLogoutAndLeaderDetection() {
        val team = "TEST_LEADER_DETECT"
        val fakePrefs = FakePreferences()
        val repo = com.example.syncedn.data.database.TeamRepository
        repo.clearCache()

        // 1. Seed Leader and Member in preferences
        fakePrefs.edit()
            .putString("email_code_leader@syncedn.org", team)
            .putString("email_role_leader@syncedn.org", "Leader")
            .putString("email_fullname_leader@syncedn.org", "Leader Ryan")
            .putString("email_username_leader@syncedn.org", "ryan")
            .putString("email_ministry_leader@syncedn.org", "Worship Leader")
            .putString("email_uid_leader@syncedn.org", "uid_leader_1")
            .putString("email_code_member@syncedn.org", team)
            .putString("email_role_member@syncedn.org", "Member")
            .putString("email_fullname_member@syncedn.org", "Member Angelo")
            .putString("email_username_member@syncedn.org", "angelo")
            .putString("email_ministry_member@syncedn.org", "Guitarist")
            .putString("email_uid_member@syncedn.org", "uid_member_2")
            .putString(com.example.syncedn.data.auth.SessionManager.KEY_USER_ID, "uid_member_2")
            .putString(com.example.syncedn.data.auth.SessionManager.KEY_EMAIL, "member@syncedn.org")
            .putString(com.example.syncedn.data.auth.SessionManager.KEY_TEAM_CODE, team)
            .apply()

        repo.syncFromPreferences(fakePrefs, team)

        // 2. Member adds an unavailable range on 2026-10-15..2026-10-18
        val range = com.example.syncedn.data.model.UnavailableRange(
            id = "r_oct15",
            startDate = "2026-10-15",
            endDate = "2026-10-18",
            reason = "Out of Town Conference"
        )
        repo.addUnavailableRange("uid_member_2", range, fakePrefs)

        // 3. Member logs out
        com.example.syncedn.data.auth.SessionManager.clearSession(fakePrefs)

        // 4. Leader logs in on the device
        val leaderProfile = com.example.syncedn.data.model.UserProfile(
            uid = "uid_leader_1",
            username = "ryan",
            email = "leader@syncedn.org",
            role = "Leader",
            teamName = "Synced Worship",
            teamCode = team,
            fullName = "Leader Ryan",
            ministryRole = "Worship Leader"
        )
        com.example.syncedn.data.auth.SessionManager.saveSessionProfile(
            fakePrefs,
            leaderProfile,
            fallbackUsername = "ryan",
            fallbackEmail = "leader@syncedn.org"
        )
        repo.syncFromPreferences(fakePrefs, team)

        // 5. Leader checks availability for 2026-10-16
        val (available, unavailable) = repo.checkTeamAvailabilityForDate(team, "2026-10-16")
        assertEquals(1, available.size)
        assertEquals("Leader Ryan", available[0].fullName)
        assertEquals(1, unavailable.size)
        assertEquals("Member Angelo", unavailable[0].first.fullName)
        assertEquals("Out of Town Conference", unavailable[0].second)
    }

    @Test
    fun testDynamicRecentNotificationsFeedGeneration() {
        val team = "TEST_NOTIF_FEED"
        val fakePrefs = FakePreferences()
        val repo = com.example.syncedn.data.database.TeamRepository
        repo.clearCache()

        // 1. Empty team has no notifications
        val initial = repo.getRecentTeamNotifications(team, fakePrefs)
        assertTrue("New team without activity should have empty notifications", initial.isEmpty())

        val now = System.currentTimeMillis()
        // 2. Add a published service with 2 songs
        val srv = com.example.syncedn.data.model.ServiceData(
            id = "srv_notif_1",
            teamCode = team,
            title = "Youth Worship Night",
            date = "2026-10-25",
            start = "18:00",
            end = "20:00",
            venue = "Main Sanctuary",
            status = "Published",
            songs = listOf(
                com.example.syncedn.data.model.ServiceSong(title = "Praise"),
                com.example.syncedn.data.model.ServiceSong(title = "Way Maker")
            ),
            createdAt = now - 3600_000L,
            updatedAt = now - 3600_000L
        )
        repo.saveServiceLocally(team, srv, fakePrefs)

        // 3. Add an urgent announcement
        val ann = com.example.syncedn.data.model.AnnouncementData(
            id = "ann_notif_1",
            teamCode = team,
            title = "Soundcheck Moved",
            message = "Please arrive at 4:30 PM for soundcheck.",
            urgent = true,
            author = "Leader Ryan",
            createdBy = "uid_leader",
            createdAt = now - 1800_000L,
            updatedAt = now - 1800_000L
        )
        repo.saveAnnouncementLocally(team, ann, fakePrefs)

        // 4. Add a substitution request
        val sub = com.example.syncedn.data.model.SubstitutionData(
            id = "sub_notif_1",
            teamCode = team,
            serviceId = "srv_notif_1",
            serviceTitle = "Youth Worship Night",
            serviceDate = "2026-10-25",
            requesterUid = "uid_m1",
            requesterName = "Angelo Daniel",
            role = "Guitarist",
            substituteUid = "uid_m2",
            substituteName = "RL Gents",
            reason = "Exam schedule",
            status = "Pending",
            createdBy = "uid_m1",
            createdAt = now - 60_000L,
            updatedAt = now - 60_000L
        )
        repo.saveSubstitutionLocally(team, sub, fakePrefs)

        val feed = repo.getRecentTeamNotifications(team, fakePrefs)
        assertEquals(4, feed.size)
        assertTrue(feed[0].title.contains("Substitution"))
        assertTrue(feed[1].title.contains("Soundcheck Moved"))
        assertEquals("New Service Scheduled", feed[2].title)
        assertEquals("Lineup Published", feed[3].title)
    }

    @Test
    fun testTeamReportSubmissionAndCaching() {
        val team = "TEST_REPORT_01"
        val fakePrefs = FakePreferences()
        val repo = com.example.syncedn.data.database.TeamRepository
        repo.clearCache()

        var callbackSuccess = false
        repo.submitTeamReport(
            teamCode = team,
            reporterUid = "uid_reporter_1",
            reporterName = "RL Gents",
            reason = "Incorrect schedule dates posted",
            preferences = fakePrefs
        ) { result ->
            callbackSuccess = result.isSuccess
        }

        assertTrue(callbackSuccess)
        val reports = repo.getCachedTeamReports(team, fakePrefs)
        assertEquals(1, reports.size)
        assertEquals("Incorrect schedule dates posted", reports[0]["reason"])
        assertEquals("RL Gents", reports[0]["reporterName"])
    }

    @Test
    fun testNotificationPreferencesPersistence() {
        val fakePrefs = FakePreferences()
        val repo = com.example.syncedn.data.database.TeamRepository

        repo.saveNotificationPreferences(
            uid = "uid_pref_1",
            notificationsEnabled = false,
            teamUnmuted = false,
            preferences = fakePrefs
        )

        assertFalse(fakePrefs.getBoolean("notifications", true))
        assertFalse(fakePrefs.getBoolean("teamUnmuted", true))

        repo.saveNotificationPreferences(
            uid = "uid_pref_1",
            notificationsEnabled = true,
            teamUnmuted = true,
            preferences = fakePrefs
        )

        assertTrue(fakePrefs.getBoolean("notifications", false))
        assertTrue(fakePrefs.getBoolean("teamUnmuted", false))
    }

    @Test
    fun testUserProfileFcmTokenSerializationAndDeserialization() {
        val token = "fcm_mock_token_abc_123_xyz"
        val profile = com.example.syncedn.data.model.UserProfile(
            uid = "user_fcm_test",
            username = "FcmUser",
            email = "fcm@test.com",
            fcmToken = token
        )
        val map = profile.toMap()
        assertEquals(token, map["fcmToken"])

        val restored = com.example.syncedn.data.model.UserProfile.fromMap(map)
        assertEquals("user_fcm_test", restored.uid)
        assertEquals(token, restored.fcmToken)

        // Empty / absent fallback
        val emptyRestored = com.example.syncedn.data.model.UserProfile.fromMap(emptyMap())
        assertEquals("", emptyRestored.fcmToken)
    }

    @Test
    fun testServiceDataCoverImageUrlSerialization() {
        val srv = com.example.syncedn.data.model.ServiceData(
            id = "srv_cover_test",
            teamCode = "TEST01",
            title = "Sunday Celebration",
            date = "2026-10-25",
            start = "09:00",
            end = "11:00",
            venue = "Main Hall",
            coverImageUrl = "https://res.cloudinary.com/ffb3fdkm/image/upload/v1234/cover.jpg"
        )
        val map = srv.toMap()
        assertEquals("https://res.cloudinary.com/ffb3fdkm/image/upload/v1234/cover.jpg", map["coverImageUrl"])

        val restored = com.example.syncedn.data.model.ServiceData.fromMap(map)
        assertEquals("srv_cover_test", restored.id)
        assertEquals("https://res.cloudinary.com/ffb3fdkm/image/upload/v1234/cover.jpg", restored.coverImageUrl)

        // Absent fallback defaults to empty string
        val absentRestored = com.example.syncedn.data.model.ServiceData.fromMap(emptyMap())
        assertEquals("", absentRestored.coverImageUrl)
    }

    @Test
    fun testSubstitutionDataMultiLayerAndTypeSerialization() {
        val sub = com.example.syncedn.data.model.SubstitutionData(
            id = "sub_inclusion_test",
            teamCode = "TEST01",
            serviceTitle = "Youth Night",
            requesterUid = "req_1",
            requesterName = "Member A",
            role = "Drummer",
            substituteUid = "sub_1",
            substituteName = "Member B",
            reason = "[Inclusion] Available to serve",
            type = "Inclusion",
            status = "Substitute Accepted"
        )
        val map = sub.toMap()
        assertEquals("Inclusion", map["type"])
        assertEquals("Substitute Accepted", map["status"])

        val restored = com.example.syncedn.data.model.SubstitutionData.fromMap(map)
        assertEquals("Inclusion", restored.type)
        assertEquals("Substitute Accepted", restored.status)

        // Default type fallback
        val defaultRestored = com.example.syncedn.data.model.SubstitutionData.fromMap(emptyMap())
        assertEquals("Exclusion", defaultRestored.type)
    }

    @Test
    fun testReplaceAssignmentInServiceExclusionAndInclusion() {
        val initialAssignments = listOf(
            com.example.syncedn.data.model.ServiceAssignment(uid = "user_assigned", name = "Assigned Member", role = "Guitarist")
        )
        val service = com.example.syncedn.data.model.ServiceData(
            id = "srv_swap_test",
            teamCode = "TEST01",
            title = "Sunday Service",
            assignments = initialAssignments
        )

        // 1. Exclusion: requester (user_assigned) cannot attend -> replaced by substitute (user_relief)
        val exclusionSub = com.example.syncedn.data.model.SubstitutionData(
            id = "sub_ex",
            teamCode = "TEST01",
            serviceId = "srv_swap_test",
            requesterUid = "user_assigned",
            requesterName = "Assigned Member",
            role = "Guitarist",
            substituteUid = "user_relief",
            substituteName = "Relief Player",
            type = "Exclusion"
        )
        val postExclusion = com.example.syncedn.data.database.TeamRepository.replaceAssignmentInService(service, exclusionSub)
        assertEquals(1, postExclusion.assignments.size)
        assertEquals("user_relief", postExclusion.assignments[0].uid)
        assertEquals("Relief Player", postExclusion.assignments[0].name)
        assertEquals("Guitarist", postExclusion.assignments[0].role)

        // 2. Inclusion: requester (user_step_in) volunteers to replace assigned substitute (user_relief)
        val inclusionSub = com.example.syncedn.data.model.SubstitutionData(
            id = "sub_inc",
            teamCode = "TEST01",
            serviceId = "srv_swap_test",
            requesterUid = "user_step_in",
            requesterName = "Volunteer Member",
            role = "Guitarist",
            substituteUid = "user_relief",
            substituteName = "Relief Player",
            type = "Inclusion"
        )
        val postInclusion = com.example.syncedn.data.database.TeamRepository.replaceAssignmentInService(postExclusion, inclusionSub)
        assertEquals(1, postInclusion.assignments.size)
        assertEquals("user_step_in", postInclusion.assignments[0].uid)
        assertEquals("Volunteer Member", postInclusion.assignments[0].name)
        assertEquals("Guitarist", postInclusion.assignments[0].role)
    }

    @Test
    fun testRejoinRequestDataSerializationAndDeserialization() {
        val now = System.currentTimeMillis()
        val request = com.example.syncedn.data.model.RejoinRequestData(
            id = "rejoin_101",
            teamCode = "ALPHA7",
            userUid = "uid_member_99",
            userName = "Removed Teammate",
            userEmail = "teammate@example.com",
            reason = "Would like to serve again on Sunday",
            status = "Pending",
            createdAt = now,
            updatedAt = now
        )

        val map = request.toMap()
        assertEquals("rejoin_101", map["id"])
        assertEquals("ALPHA7", map["teamCode"])
        assertEquals("uid_member_99", map["userUid"])
        assertEquals("Removed Teammate", map["userName"])
        assertEquals("teammate@example.com", map["userEmail"])
        assertEquals("Would like to serve again on Sunday", map["reason"])
        assertEquals("Pending", map["status"])
        assertEquals(now, map["createdAt"])
        assertEquals(now, map["updatedAt"])

        val deserialized = com.example.syncedn.data.model.RejoinRequestData.fromMap(map)
        assertEquals(request.id, deserialized.id)
        assertEquals(request.teamCode, deserialized.teamCode)
        assertEquals(request.userUid, deserialized.userUid)
        assertEquals(request.userName, deserialized.userName)
        assertEquals(request.userEmail, deserialized.userEmail)
        assertEquals(request.reason, deserialized.reason)
        assertEquals(request.status, deserialized.status)
        assertEquals(request.createdAt, deserialized.createdAt)
        assertEquals(request.updatedAt, deserialized.updatedAt)

        // Test with Firestore Timestamp
        val ts = com.google.firebase.Timestamp(java.util.Date(now))
        val mapWithTimestamp = map.toMutableMap()
        mapWithTimestamp["createdAt"] = ts
        mapWithTimestamp["updatedAt"] = ts
        val deserializedTs = com.example.syncedn.data.model.RejoinRequestData.fromMap(mapWithTimestamp)
        assertEquals(now, deserializedTs.createdAt)
        assertEquals(now, deserializedTs.updatedAt)
    }

    @Test
    fun testTeamNotificationItemFiltering() {
        val n1 = com.example.syncedn.data.database.TeamRepository.TeamNotificationItem(
            id = "notif_1",
            title = "Service Updated",
            message = "New song added",
            timeLabel = "Just now",
            iconName = "music",
            timestamp = 1000L
        )
        val n2 = com.example.syncedn.data.database.TeamRepository.TeamNotificationItem(
            id = "notif_2",
            title = "Announcement",
            message = "Practice at 6pm",
            timeLabel = "5m ago",
            iconName = "announce",
            timestamp = 2000L
        )
        val allNotifs = listOf(n1, n2)
        val deletedIds = setOf("notif_1")
        val activeNotifs = allNotifs.filter { it.id !in deletedIds }

        assertEquals(1, activeNotifs.size)
        assertEquals("notif_2", activeNotifs[0].id)
    }

    @Test
    fun testUserProfileRemovedByLeaderSerialization() {
        val profile = com.example.syncedn.data.model.UserProfile(
            uid = "user_test_123",
            username = "YANYAN",
            email = "yanyangenturo@gmail.com",
            role = "Member",
            teamName = "",
            teamCode = "",
            removedByLeader = true,
            removedTeamCode = "NGK8VN"
        )
        val map = profile.toMap()
        assertEquals(true, map["removedByLeader"])
        assertEquals("NGK8VN", map["removedTeamCode"])
        assertEquals("", map["teamName"])
        assertEquals("", map["teamCode"])

        val deserialized = com.example.syncedn.data.model.UserProfile.fromMap(map)
        assertEquals("user_test_123", deserialized.uid)
        assertEquals("YANYAN", deserialized.username)
        assertEquals("yanyangenturo@gmail.com", deserialized.email)
        assertTrue(deserialized.removedByLeader)
        assertEquals("NGK8VN", deserialized.removedTeamCode)
        assertEquals("", deserialized.teamName)
        assertEquals("", deserialized.teamCode)
    }

    @Test
    fun testSessionManagerRemovedUserSessionResolution() {
        val prefs = FakePreferences()
        prefs.edit()
            .putString("email_team_yanyangenturo@gmail.com", "WISE MAN TEAM")
            .putString("email_code_yanyangenturo@gmail.com", "NGK8VN")
            .putString("teamName", "WISE MAN TEAM")
            .putString("teamCode", "NGK8VN")
            .apply()

        // Profile with removedByLeader = true and blank team
        val removedProfile = com.example.syncedn.data.model.UserProfile(
            uid = "user_test_123",
            username = "YANYAN",
            email = "yanyangenturo@gmail.com",
            role = "Member",
            teamName = "",
            teamCode = "",
            removedByLeader = true,
            removedTeamCode = "NGK8VN"
        )

        val (role, team, code) = com.example.syncedn.data.auth.SessionManager.resolveUserTeamSession(
            prefs,
            removedProfile,
            "yanyangenturo@gmail.com"
        )

        assertEquals("Member", role)
        assertEquals("", team)
        assertEquals("", code)
        assertTrue(prefs.getBoolean("removedByLeader", false))
        assertEquals("NGK8VN", prefs.getString("removedTeamCode", ""))
        assertEquals("", prefs.getString("teamName", ""))
        assertEquals("", prefs.getString("teamCode", ""))
    }

    @Test
    fun testTeamMemberRemovedByLeaderSerialization() {
        val member = com.example.syncedn.data.model.TeamMember(
            uid = "m_uid_99",
            fullName = "Joshua Doe",
            username = "joshua",
            email = "joshua@syncedn.org",
            role = "Member",
            ministryRole = "Vocalist",
            teamCode = "NGK8VN",
            removedByLeader = false
        )
        val map = member.toMap()
        assertEquals(false, map["removedByLeader"])

        val deserialized = com.example.syncedn.data.model.TeamMember.fromMap(map)
        assertEquals("m_uid_99", deserialized.uid)
        assertEquals("Joshua Doe", deserialized.fullName)
        assertEquals(false, deserialized.removedByLeader)

        val removedMap = map.toMutableMap()
        removedMap["removedByLeader"] = true
        val removedMember = com.example.syncedn.data.model.TeamMember.fromMap(removedMap)
        assertEquals(true, removedMember.removedByLeader)
    }

    @Test
    fun testReplaceAssignmentInServiceFuzzyNameMatching() {
        val initialAssignments = listOf(
            com.example.syncedn.data.model.ServiceAssignment(
                uid = "u1",
                name = "YANYAN",
                role = "Worship Leader"
            )
        )
        val service = com.example.syncedn.data.model.ServiceData(
            id = "svc_test_fuzzy",
            teamCode = "NGK8VN",
            title = "Sunday Service",
            assignments = initialAssignments
        )
        val sub = com.example.syncedn.data.model.SubstitutionData(
            id = "sub_fuzzy",
            teamCode = "NGK8VN",
            serviceId = "svc_test_fuzzy",
            serviceTitle = "Sunday Service",
            requesterUid = "u1",
            requesterName = "Yanyan Genturo",
            role = "Worship Leader",
            substituteUid = "u2",
            substituteName = "Angelo Daniel",
            status = "Pending",
            type = "Exclusion"
        )

        val updated = com.example.syncedn.data.database.TeamRepository.replaceAssignmentInService(service, sub)
        assertEquals(1, updated.assignments.size)
        assertEquals("u2", updated.assignments[0].uid)
        assertEquals("Angelo Daniel", updated.assignments[0].name)
        assertEquals("Worship Leader", updated.assignments[0].role)
    }

    @Test
    fun testApproveRejoinRequestClearsFullNameFromRemovedMembers() {
        val prefs = FakePreferences()
        val code = "NGK8VN"
        val curRemoved = mutableSetOf("user_123", "yanyangenturo@gmail.com", "YANYAN", "yanyan")
        prefs.edit()
            .putStringSet("team_removed_members_$code", curRemoved)
            .putStringSet("removedMembers", curRemoved)
            .apply()

        val req = com.example.syncedn.data.model.RejoinRequestData(
            id = "req_1",
            teamCode = code,
            userUid = "user_123",
            userName = "YANYAN",
            userEmail = "yanyangenturo@gmail.com",
            status = "Pending"
        )

        com.example.syncedn.data.database.TeamRepository.approveRejoinRequest(code, req, "WISE MAN TEAM", prefs)

        val postTeamRemoved = prefs.getStringSet("team_removed_members_$code", mutableSetOf<String>()).orEmpty()
        val postGenRemoved = prefs.getStringSet("removedMembers", mutableSetOf<String>()).orEmpty()

        assertFalse(postTeamRemoved.contains("user_123"))
        assertFalse(postTeamRemoved.contains("yanyangenturo@gmail.com"))
        assertFalse(postTeamRemoved.contains("YANYAN"))
        assertFalse(postGenRemoved.contains("user_123"))
        assertFalse(postGenRemoved.contains("yanyangenturo@gmail.com"))
        assertFalse(postGenRemoved.contains("YANYAN"))
    }
}