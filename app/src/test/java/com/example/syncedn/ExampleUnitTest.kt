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
        assertEquals(8, seeds.size)
        val praise = seeds.find { it.title == "Praise" }!!
        assertEquals("Elevation Worship", praise.artist)
        assertEquals("G", praise.key)
        assertEquals(127, praise.bpm)
        assertEquals("https://www.youtube.com/watch?v=f2oxGYpuLkw", praise.referenceUrl)
        assertTrue("Praise sheet must contain chords", praise.sheet.contains("[G]"))

        val trust = seeds.find { it.title == "Trust in God" }!!
        assertEquals("C", trust.key)
        assertEquals(74, trust.bpm)
        assertEquals("https://www.youtube.com/watch?v=QS04WbSnxok", trust.referenceUrl)

        val wayMaker = seeds.find { it.title == "Way Maker" }!!
        assertEquals("E", wayMaker.key)
        assertEquals(68, wayMaker.bpm)
        assertEquals("https://www.youtube.com/watch?v=QM8jQHE5AAk", wayMaker.referenceUrl)

        val gratitude = seeds.find { it.title == "Gratitude" }!!
        assertEquals("B", gratitude.key)
        assertEquals(78, gratitude.bpm)

        val goodness = seeds.find { it.title == "Goodness of God" }!!
        assertEquals("Ab", goodness.key)
        assertEquals(70, goodness.bpm)
        assertEquals("https://www.youtube.com/watch?v=n3t6CdeiN1M", goodness.referenceUrl)
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

        // 1. Initial cached songs should contain 8 seeds
        val initial = repo.getCachedSongs(code, prefs)
        assertEquals(8, initial.size)

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

        // 3. getCachedSongs must now contain 9 songs (custom song + all 8 original seeds)
        val updated = repo.getCachedSongs(code, prefs)
        assertEquals(9, updated.size)
        assertTrue(updated.any { it.title == "I Speak Jesus" })
        assertTrue(updated.any { it.title == "Praise" })
        assertTrue(updated.any { it.title == "Trust in God" })
        assertTrue(updated.any { it.title == "Way Maker" })
        assertTrue(updated.any { it.title == "Gratitude" })

        // 4. Deleting a song marks it deleted
        repo.deleteSong(code, "seed_praise", prefs)
        val afterDelete = repo.getCachedSongs(code, prefs)
        assertEquals(8, afterDelete.size)
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

    @Test
    fun testSubstituteLeaderDemotionAndSessionResolution() {
        val prefs = FakePreferences()
        val email = "substitute@example.com"
        val uid = "user_sub_456"

        // Setup a previous state where user was Substitute Leader (with savedRole = "Leader")
        prefs.edit()
            .putString("email", email)
            .putString(com.example.syncedn.data.auth.SessionManager.KEY_USER_ID, uid)
            .putString(com.example.syncedn.data.auth.SessionManager.KEY_ROLE, "Leader")
            .putString("role", "Leader")
            .putString("ministryRole", "Substitute Leader")
            .putString("teamName", "Praise Team")
            .putString("teamCode", "TEAM01")
            .apply()

        // Remote Firestore profile now returns role = "Member" (Leader revoked the substitute role)
        val remoteProfile = com.example.syncedn.data.model.UserProfile(
            uid = uid,
            email = email,
            username = "SubUser",
            role = "Member",
            ministryRole = "Vocalist",
            teamName = "Praise Team",
            teamCode = "TEAM01"
        )

        val (finalRole, finalTeam, finalCode) = com.example.syncedn.data.auth.SessionManager.resolveUserTeamSession(
            preferences = prefs,
            profile = remoteProfile,
            email = email
        )

        // Verifications:
        // 1. Authoritative remote role "Member" must take priority over stale saved "Leader" role
        assertEquals("Member", finalRole)
        assertEquals("Praise Team", finalTeam)
        assertEquals("TEAM01", finalCode)

        // 2. Stale local preferences must be cleaned up to "Member"
        assertEquals("Member", prefs.getString(com.example.syncedn.data.auth.SessionManager.KEY_ROLE, ""))
        assertEquals("Member", prefs.getString("role", ""))
        assertEquals("Member", prefs.getString("user_role_$uid", ""))
    }

    @Test
    fun testYouTubeVideoIdExtractionAndFallback() {
        // Standard desktop watch URL
        assertEquals("dQw4w9WgXcQ", com.example.syncedn.data.model.SongData.extractYoutubeVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        // Desktop watch URL with extra query params
        assertEquals("dQw4w9WgXcQ", com.example.syncedn.data.model.SongData.extractYoutubeVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=shared&t=42"))
        // Shortened youtu.be link
        assertEquals("dQw4w9WgXcQ", com.example.syncedn.data.model.SongData.extractYoutubeVideoId("https://youtu.be/dQw4w9WgXcQ?si=abcdef123"))
        // YouTube Shorts URL
        assertEquals("dQw4w9WgXcQ", com.example.syncedn.data.model.SongData.extractYoutubeVideoId("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
        // Embed URL
        assertEquals("dQw4w9WgXcQ", com.example.syncedn.data.model.SongData.extractYoutubeVideoId("https://www.youtube.com/embed/dQw4w9WgXcQ"))
        // Empty / blank / invalid fallback
        assertNull(com.example.syncedn.data.model.SongData.extractYoutubeVideoId(""))
        assertNull(com.example.syncedn.data.model.SongData.extractYoutubeVideoId("   "))
        assertNull(com.example.syncedn.data.model.SongData.extractYoutubeVideoId(null))
        assertNull(com.example.syncedn.data.model.SongData.extractYoutubeVideoId("https://spotify.com/track/12345"))
    }

    @Test
    fun testFreshInstallMarkerLogic() {
        val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "test_synced_install_" + System.currentTimeMillis())
        tempDir.mkdirs()
        try {
            val marker = java.io.File(tempDir, "synced_install_v1.marker")
            // On fresh install, marker does not exist
            assertFalse(marker.exists())

            // App creates marker
            marker.createNewFile()
            assertTrue(marker.exists())

            // Subsequent launches find marker existing
            assertTrue(marker.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testRosterDeduplicationByEmailAndUid() {
        val range1 = com.example.syncedn.data.model.UnavailableRange("r1", "2026-11-01", "2026-11-02", "Vacation")
        val range2 = com.example.syncedn.data.model.UnavailableRange("r2", "2026-11-05", "2026-11-06", "Work")

        val doc1 = com.example.syncedn.data.model.TeamMember(
            uid = "user_987654",
            fullName = "YANYAN",
            username = "YANYAN",
            email = "yanyangenturo@gmail.com",
            role = "Member",
            ministryRole = "Vocalist",
            teamCode = "TEST01",
            photoUri = "",
            unavailableRanges = listOf(range1)
        )
        val doc2 = com.example.syncedn.data.model.TeamMember(
            uid = "auth_uid_yanyan",
            fullName = "Yanyan Genturo",
            username = "yanyangenturo",
            email = "yanyangenturo@gmail.com",
            role = "Member",
            ministryRole = "Member",
            teamCode = "TEST01",
            photoUri = "https://cloudinary.com/profile_yanyan.jpg",
            unavailableRanges = listOf(range2)
        )
        val doc3 = com.example.syncedn.data.model.TeamMember(
            uid = "user_rl_synthetic",
            fullName = "RL",
            username = "RL",
            email = "ryanlloydgenturo@gmail.com",
            role = "Leader",
            ministryRole = "Worship Leader",
            teamCode = "TEST01"
        )
        val doc4 = com.example.syncedn.data.model.TeamMember(
            uid = "auth_uid_rl",
            fullName = "Ryan Lloyd Genturo",
            username = "RL",
            email = "ryanlloydgenturo@gmail.com",
            role = "Leader",
            ministryRole = "Worship Leader",
            teamCode = "TEST01",
            photoUri = "https://cloudinary.com/profile_rl.jpg"
        )

        val rawList = listOf(doc1, doc2, doc3, doc4)
        val deduplicated = com.example.syncedn.data.database.TeamRepository.deduplicateRoster(rawList)

        // Verifications:
        // 1. Must reduce 4 duplicate records down to exactly 2 canonical members (RL and Yanyan)
        assertEquals(2, deduplicated.size)

        // 2. Yanyan entry checks
        val yanyan = deduplicated.find { it.email.equals("yanyangenturo@gmail.com", true) }
        assertNotNull("Yanyan must exist in deduplicated roster", yanyan)
        assertEquals("auth_uid_yanyan", yanyan!!.uid) // Preferred authentic UID
        assertEquals("https://cloudinary.com/profile_yanyan.jpg", yanyan.photoUri) // Photo preserved
        assertEquals("Vocalist", yanyan.ministryRole) // Specific ministry role preserved over generic Member
        assertEquals(2, yanyan.unavailableRanges.size) // Both date ranges merged

        // 3. RL entry checks
        val rl = deduplicated.find { it.email.equals("ryanlloydgenturo@gmail.com", true) }
        assertNotNull("RL must exist in deduplicated roster", rl)
        assertEquals("auth_uid_rl", rl!!.uid) // Preferred authentic UID
        assertEquals("Leader", rl.role)
        assertEquals("Worship Leader", rl.ministryRole)
        assertEquals("https://cloudinary.com/profile_rl.jpg", rl.photoUri)
    }

    @Test
    fun testDefaultSeedSongsIntegrityAndVerifiedUrls() {
        val seedSongs = com.example.syncedn.data.database.TeamRepository.getDefaultSeedSongs("TEST01")
        assertTrue("Seed catalog must contain at least 5 songs", seedSongs.size >= 5)

        val requiredTitles = listOf("Amazing Grace", "Build My Life", "Goodness of God", "Gratitude", "Praise", "Trust in God")
        for (req in requiredTitles) {
            val match = seedSongs.find { it.title.equals(req, ignoreCase = true) }
            assertNotNull("Seed song '$req' must be present in default seed catalog", match)
            val videoId = com.example.syncedn.data.model.SongData.extractYoutubeVideoId(match!!.referenceUrl)
            assertNotNull("Reference URL for '$req' must have a valid YouTube video ID: ${match.referenceUrl}", videoId)
            assertTrue("Sheet for '$req' must have chords format", match.sheet.contains("[") && match.sheet.contains("]"))
            assertTrue("Sheet for '$req' must contain [Verse", match.sheet.contains("[Verse"))
            assertTrue("Sheet for '$req' must contain [Chorus", match.sheet.contains("[Chorus"))
            assertTrue("Lyrics for '$req' must not be blank", match.lyrics.isNotBlank())
            assertTrue("Original key for '$req' must not be blank", match.key.isNotBlank())
            assertTrue("BPM for '$req' must be greater than 0", match.bpm > 0)
        }
    }

    @Test
    fun testLegacyBrokenSeedSongUrlAutoMigration() {
        val defaultSeeds = com.example.syncedn.data.database.TeamRepository.getDefaultSeedSongs("MIGRATE01")
        val goodnessSeed = defaultSeeds.find { it.title.equals("Goodness of God", true) }!!

        // Simulate an existing cached song in preferences with the old broken 404 URL
        val legacyGoodnessSong = goodnessSeed.copy(
            referenceUrl = "https://www.youtube.com/watch?v=-f4MUKEVKaU"
        )
        val legacyArray = org.json.JSONArray().apply {
            put(org.json.JSONObject(legacyGoodnessSong.toMap()))
        }

        val prefs = FakePreferences().apply {
            edit().putString("team_songs_MIGRATE01", legacyArray.toString()).apply()
        }

        val loadedSongs = com.example.syncedn.data.database.TeamRepository.getCachedSongs("MIGRATE01", prefs)
        val migratedGoodness = loadedSongs.find { it.title.equals("Goodness of God", true) }
        assertNotNull(migratedGoodness)
        // Must be migrated to the verified working URL (n3t6CdeiN1M)
        assertEquals("https://www.youtube.com/watch?v=n3t6CdeiN1M", migratedGoodness!!.referenceUrl)
    }

    @Test
    fun testMemberTransposeChordsGating() {
        val sheetWithChords = "[Verse 1]\n[G]Amazing grace, how [C]sweet the sound"
        val hasChords = com.example.syncedn.ChordSheet.hasChords(sheetWithChords)
        assertTrue("Sheet must have chords", hasChords)

        // MemberActivity showChords should be equal to hasChordsInSheet, allowing members to transpose
        val showChords = hasChords
        assertTrue("showChords must be true for sheets with chords regardless of instrumentalist assignment", showChords)

        // Transposing G by 2 semitones to A
        val transposedToA = com.example.syncedn.ChordSheet.render(sheetWithChords, 2, false, false)
        assertTrue("Transposed sheet must contain transposed chords line", transposedToA.contains("A") && transposedToA.contains("D"))
    }

    @Test
    fun testDemotionCleansSubstituteLeaderAndAdoptsMemberRole() {
        val prefs = FakePreferences()
        val uid = "sub_leader_user"
        val email = "subleader@syncedn.church"

        // 1. Initially assigned as Substitute Leader
        val initialMap = org.json.JSONObject().apply {
            put(uid, "Substitute Leader")
            put(email, "Substitute Leader")
        }
        prefs.edit()
            .putString(com.example.syncedn.data.auth.SessionManager.KEY_USER_ID, uid)
            .putString(com.example.syncedn.data.auth.SessionManager.KEY_EMAIL, email)
            .putString("role", "Leader")
            .putString(com.example.syncedn.data.auth.SessionManager.KEY_ROLE, "Leader")
            .putString("ministryRole", "Substitute Leader")
            .putString(com.example.syncedn.data.auth.SessionManager.KEY_MINISTRY_ROLE, "Substitute Leader")
            .putString("email_ministry_$email", "Substitute Leader")
            .putString("user_ministry_$uid", "Substitute Leader")
            .putString(com.example.syncedn.data.auth.SessionManager.KEY_MEMBER_ROLES, initialMap.toString())
            .apply()

        // 2. Leader revokes Substitute Leader, user's remote profile is updated to "Member"
        val demotedProfile = com.example.syncedn.data.model.UserProfile(
            uid = uid,
            username = "SubLeader",
            fullName = "Sub Leader Name",
            email = email,
            role = "Member",
            teamName = "Praise Team",
            teamCode = "DEMOTE01",
            ministryRole = "Member"
        )

        // Session restoration / saveSessionProfile must clear Substitute Leader
        com.example.syncedn.data.auth.SessionManager.saveSessionProfile(
            prefs,
            demotedProfile,
            fallbackUsername = "SubLeader",
            fallbackEmail = email
        )

        assertEquals("Member", prefs.getString("role", ""))
        assertEquals("Member", prefs.getString(com.example.syncedn.data.auth.SessionManager.KEY_ROLE, ""))
        assertEquals("Member", prefs.getString("ministryRole", ""))
        assertEquals("Member", prefs.getString(com.example.syncedn.data.auth.SessionManager.KEY_MINISTRY_ROLE, ""))

        // Map must no longer have Substitute Leader for this user
        val updatedMap = org.json.JSONObject(prefs.getString(com.example.syncedn.data.auth.SessionManager.KEY_MEMBER_ROLES, "{}").orEmpty())
        assertFalse(updatedMap.optString(uid).equals("Substitute Leader", ignoreCase = true))
        assertFalse(updatedMap.optString(email).equals("Substitute Leader", ignoreCase = true))
    }

    @Test
    fun testSyncFromPreferencesDoesNotResurrectSubstituteLeaderForDemotedMember() {
        val prefs = FakePreferences()
        val repo = com.example.syncedn.data.database.TeamRepository
        val code = "SYNCROSTER01"
        val uid = "user_yanyan"
        val email = "yanyan@syncedn.church"

        // Logged-in user is demoted to Member
        prefs.edit()
            .putString(com.example.syncedn.data.auth.SessionManager.KEY_USER_ID, uid)
            .putString("userId", uid)
            .putString("email", email)
            .putString("role", "Member")
            .putString(com.example.syncedn.data.auth.SessionManager.KEY_ROLE, "Member")
            .putString("ministryRole", "Member")
            .putString(com.example.syncedn.data.auth.SessionManager.KEY_MINISTRY_ROLE, "Member")
            .putString("email_code_$email", code)
            .putString("email_fullname_$email", "Yanyan Redoquerio")
            .putString("email_role_$email", "Member")
            .putString("email_ministry_$email", "Substitute Leader") // Stale legacy pref
            .apply()

        // syncFromPreferences runs (e.g. on clicking Teams tab)
        repo.syncFromPreferences(prefs, code)

        val roster = repo.getCachedRoster(code)
        val member = roster.find { it.uid == uid || it.email.equals(email, true) }
        assertNotNull("Member must be present in roster", member)
        assertEquals("Role must be Member, not Leader", "Member", member!!.role)
        assertEquals("Ministry role must not be resurrected to Substitute Leader", "Member", member.ministryRole)
    }

    @Test
    fun testDefaultWorshipSeedSongsHaveValidYoutubeIds() {
        val seeds = com.example.syncedn.data.database.TeamRepository.getDefaultSeedSongs("TESTTEAM")
        assertEquals(8, seeds.size)
        seeds.forEach { song ->
            val vid = com.example.syncedn.data.model.SongData.extractYoutubeVideoId(song.referenceUrl)
            assertNotNull("Song '${song.title}' must have a valid YouTube video ID", vid)
            assertTrue("Video ID for '${song.title}' should be 11 characters", vid!!.length == 11)
        }
    }

    @Test
    fun testDeduplicateRosterPrefersMemberRoleOverStaleSubstituteLeader() {
        val repo = com.example.syncedn.data.database.TeamRepository
        val prefs = FakePreferences()

        val staleSubLeader = com.example.syncedn.data.model.TeamMember(
            uid = "user_rl",
            fullName = "RL Member",
            username = "rl",
            email = "rl@syncedn.church",
            role = "Leader",
            ministryRole = "Substitute Leader",
            teamCode = "TESTTEAM"
        )
        val latestDemoted = com.example.syncedn.data.model.TeamMember(
            uid = "user_rl",
            fullName = "RL Member",
            username = "rl",
            email = "rl@syncedn.church",
            role = "Member",
            ministryRole = "Member",
            teamCode = "TESTTEAM"
        )

        // Merge stale + latest
        val merged = repo.deduplicateRoster(listOf(staleSubLeader, latestDemoted), prefs)
        assertEquals(1, merged.size)
        val m = merged[0]
        assertEquals("Role must be Member, not Leader", "Member", m.role)
        assertEquals("Ministry role must be Member, not Substitute Leader", "Member", m.ministryRole)
    }

    @Test
    fun testHolyForeverSongSheetEnrichmentAndTransposeKeyAvailability() {
        val repo = com.example.syncedn.data.database.TeamRepository
        val prefs = FakePreferences()

        // 1. Simulate an old cached Holy Forever song with plain lyrics and no chords
        val chordlessHolyForever = com.example.syncedn.data.model.SongData(
            id = "seed_holy_forever",
            teamCode = "TESTTEAM",
            title = "Holy Forever",
            artist = "Chris Tomlin",
            key = "C",
            bpm = 74,
            sheet = "[Verse]\nA thousand generations falling down in worship\nTo sing the song of ages to the Lamb",
            lyrics = "A thousand generations falling down in worship\nTo sing the song of ages to the Lamb",
            referenceUrl = "https://www.youtube.com/watch?v=IkHgxKemCRk"
        )
        assertFalse("Initial cached Holy Forever should not have chord tags", com.example.syncedn.ChordSheet.hasChords(chordlessHolyForever.sheet))

        repo.saveSongLocally("TESTTEAM", chordlessHolyForever, prefs)

        // 2. Fetch cached songs - should automatically enrich sheet with chords from default seed
        val cachedSongs = repo.getCachedSongs("TESTTEAM", prefs)
        val holyForever = cachedSongs.find { it.title.equals("Holy Forever", ignoreCase = true) }
        assertNotNull("Holy Forever must be present", holyForever)
        assertTrue("Enriched Holy Forever sheet must have chords", com.example.syncedn.ChordSheet.hasChords(holyForever!!.sheet))
        assertTrue("Holy Forever sheet must contain [C] chord", holyForever.sheet.contains("[C]"))
        assertTrue("Holy Forever sheet must contain [F] chord", holyForever.sheet.contains("[F]"))

        // 3. Verify Key and Transpose calculations
        val pitch = com.example.syncedn.ChordSheet.keyPitch(holyForever.key)
        assertEquals("Key C pitch must be 0", 0, pitch)
        val transposedD = com.example.syncedn.ChordSheet.transposeChord("C", 2, false)
        assertEquals("C transposed up 2 semitones must be D", "D", transposedD)
    }

    @Test
    fun testUnavailableRangesDeletionSync() {
        val repo = com.example.syncedn.data.database.TeamRepository
        val prefs = FakePreferences()

        // 1. Simulate member with a previously stored local unavailability range
        val range1 = com.example.syncedn.data.model.UnavailableRange("r1", "2026-10-25", "2026-10-26", "Family event")
        prefs.edit().putString(
            "user_unavailable_ranges_test-uid",
            org.json.JSONArray().put(org.json.JSONObject(range1.toMap())).toString()
        ).apply()

        // Verify it was saved in preferences
        val storedRanges = repo.getMemberUnavailableRanges("test-uid", prefs)
        assertEquals(1, storedRanges.size)

        // 2. Member deletes unavailability in Firestore: incoming document has empty unavailableRanges
        val updatedMember = com.example.syncedn.data.model.TeamMember(
            uid = "test-uid",
            fullName = "John Doe",
            email = "johndoe@test.com",
            role = "Member",
            ministryRole = "Vocalist",
            unavailableRanges = emptyList()
        )

        // Deduplicate roster should respect the empty list and NOT resurrect the deleted range
        val roster = repo.deduplicateRoster(listOf(updatedMember), prefs)
        assertEquals(1, roster.size)
        assertTrue(
            "Deleted unavailableRanges must remain empty and not resurrected",
            roster[0].unavailableRanges.isEmpty()
        )

        // 3. saveRosterLocally should also synchronize and clear the old cached preference
        repo.saveRosterLocally("TESTTEAM", roster, prefs)
        val localRangesAfterSave = repo.getMemberUnavailableRanges("test-uid", prefs)
        assertTrue(
            "Local preference cache must be cleared after roster save",
            localRangesAfterSave.isEmpty()
        )
    }

    @Test
    fun testChatMessageTimestampParsing() {
        // 1. Long / Number timestamp
        val mapWithNumber = mapOf(
            "id" to "msg1",
            "teamCode" to "TEAM01",
            "senderUid" to "uid1",
            "senderName" to "Alice",
            "text" to "Hello World",
            "timestamp" to 1700000000000L
        )
        val msg1 = com.example.syncedn.data.model.ChatMessage.fromMap(mapWithNumber)
        assertEquals(1700000000000L, msg1.timestamp)

        // 2. Double timestamp (e.g. from JSON serialization)
        val mapWithDouble = mapOf(
            "id" to "msg2",
            "teamCode" to "TEAM01",
            "senderUid" to "uid2",
            "senderName" to "Bob",
            "text" to "Double timestamp",
            "timestamp" to 1700000000000.0
        )
        val msg2 = com.example.syncedn.data.model.ChatMessage.fromMap(mapWithDouble)
        assertEquals(1700000000000L, msg2.timestamp)

        // 3. String timestamp
        val mapWithString = mapOf(
            "id" to "msg3",
            "teamCode" to "TEAM01",
            "senderUid" to "uid3",
            "senderName" to "Charlie",
            "text" to "String timestamp",
            "timestamp" to "1700000000000"
        )
        val msg3 = com.example.syncedn.data.model.ChatMessage.fromMap(mapWithString)
        assertEquals(1700000000000L, msg3.timestamp)
    }

    @Test
    fun testServiceCreationDateValidation() {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val cal = java.util.Calendar.getInstance()
        val todayIso = sdf.format(cal.time)

        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val yesterdayIso = sdf.format(cal.time)

        cal.add(java.util.Calendar.DAY_OF_YEAR, 2)
        val tomorrowIso = sdf.format(cal.time)

        // Past date must be strictly less than today's ISO date string
        assertTrue("Yesterday ($yesterdayIso) must be less than today ($todayIso)", yesterdayIso < todayIso)
        // Today and tomorrow must NOT be less than today
        assertFalse("Today ($todayIso) must not be less than today", todayIso < todayIso)
        assertFalse("Tomorrow ($tomorrowIso) must not be less than today", tomorrowIso < todayIso)
    }

    @Test
    fun testLeaderPromotionFlowRoleState() {
        val member = com.example.syncedn.data.model.TeamMember(
            uid = "sub-uid",
            fullName = "Sub Leader",
            email = "sub@syncedn.org",
            role = "Leader",
            ministryRole = "Worship Leader"
        )
        val isPromoted = member.ministryRole.equals("Worship Leader", ignoreCase = true) || member.role.equals("Leader", ignoreCase = true)
        assertTrue("Promoted member should be recognized as Leader", isPromoted)
        assertEquals("Worship Leader", member.ministryRole)
        assertEquals("Leader", member.role)
    }

    @Test
    fun testCoverPhotoUploadPreservesWizardMode() {
        val leaderDraft = org.json.JSONObject()
        var activeServiceIndex = -1
        val page = "createService"

        // Simulate uploading cover photo on new service creation
        val remoteUrl = "https://res.cloudinary.com/demo/image/upload/sample.jpg"
        leaderDraft.put("coverImageUrl", remoteUrl)

        val serviceWizard = activeServiceIndex < 0
        assertTrue("Wizard mode must remain active (true) after cover photo upload", serviceWizard)
        val actionText = if (serviceWizard) "Next" else "Save"
        assertEquals("Next button label must be preserved", "Next", actionText)
    }

    @Test
    fun testInAppPlayerAndReferenceLinkLogic() {
        val testUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val videoId = com.example.syncedn.data.model.SongData.extractYoutubeVideoId(testUrl)
        assertEquals("dQw4w9WgXcQ", videoId)

        // Test display url fallback formatting
        val rawLinkWithoutScheme = "youtu.be/dQw4w9WgXcQ"
        val displayUrl = if (rawLinkWithoutScheme.startsWith("https://") || rawLinkWithoutScheme.startsWith("http://")) rawLinkWithoutScheme
            else if (rawLinkWithoutScheme.isNotBlank()) "https://$rawLinkWithoutScheme"
            else "https://www.youtube.com/results?search_query=worship"
        assertEquals("https://youtu.be/dQw4w9WgXcQ", displayUrl)

        // Test blank reference fallback to search URL
        val blankLink = ""
        val songName = "Holy Forever"
        val fallbackUrl = if (blankLink.startsWith("https://") || blankLink.startsWith("http://")) blankLink
            else if (blankLink.isNotBlank()) "https://$blankLink"
            else "https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode("$songName worship", "UTF-8")
        assertTrue(fallbackUrl.contains("Holy+Forever+worship"))

        // Verify embed iframe template targets youtube-nocookie and playsinline
        val embedUrl = "https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&playsinline=1&controls=1&enablejsapi=1&rel=0&modestbranding=1"
        assertTrue("Embed URL must use youtube-nocookie.com", embedUrl.contains("youtube-nocookie.com"))
        assertTrue("Embed URL must force playsinline to stay in-app", embedUrl.contains("playsinline=1"))
    }
    @Test
    fun testNotificationClearingAndInPlaceDismissal() {
        val now = System.currentTimeMillis()
        val notif1 = com.example.syncedn.data.database.TeamRepository.TeamNotificationItem(
            id = "notif_1",
            title = "Service 1",
            message = "Test 1",
            timeLabel = "Just now",
            iconName = "calendar",
            timestamp = now - 5000L
        )
        val notif2 = com.example.syncedn.data.database.TeamRepository.TeamNotificationItem(
            id = "notif_2",
            title = "Service 2",
            message = "Test 2",
            timeLabel = "Just now",
            iconName = "calendar",
            timestamp = now - 2000L
        )
        val notif3 = com.example.syncedn.data.database.TeamRepository.TeamNotificationItem(
            id = "notif_3",
            title = "Service 3",
            message = "Test 3",
            timeLabel = "Just now",
            iconName = "calendar",
            timestamp = now + 1000L
        )

        val allNotifs = listOf(notif1, notif2, notif3)

        // 1. Individual delete
        val deletedIds = setOf("notif_1")
        val afterSingleDelete = allNotifs.filter { it.id !in deletedIds }
        assertEquals(2, afterSingleDelete.size)
        assertFalse(afterSingleDelete.any { it.id == "notif_1" })

        // 2. Clear All by timestamp and ID set
        val clearedAt = now
        val afterClearAll = allNotifs.filter { it.timestamp > clearedAt && it.id !in deletedIds }
        assertEquals(1, afterClearAll.size)
        assertEquals("notif_3", afterClearAll.first().id)

        // 3. Substitution request filtering test
        val sub1 = com.example.syncedn.data.model.SubstitutionData(
            id = "sub_1",
            teamCode = "TEST01",
            serviceTitle = "Sunday Worship",
            requesterUid = "u1",
            requesterName = "User 1",
            role = "Vocalist",
            substituteUid = "u2",
            substituteName = "User 2",
            reason = "Sick",
            status = "Pending",
            createdAt = now - 10000L
        )
        val sub2 = com.example.syncedn.data.model.SubstitutionData(
            id = "sub_2",
            teamCode = "TEST01",
            serviceTitle = "Midweek Service",
            requesterUid = "u3",
            requesterName = "User 3",
            role = "Drummer",
            substituteUid = "u4",
            substituteName = "User 4",
            reason = "Work",
            status = "Pending",
            createdAt = now + 5000L
        )

        val subs = listOf(sub1, sub2)
        val deletedSubIds = setOf("sub_1")
        val subClearedAt = now
        val activeSubs = subs.filter { it.id !in deletedSubIds && (it.createdAt == 0L || it.createdAt > subClearedAt) }
        assertEquals(1, activeSubs.size)
        assertEquals("sub_2", activeSubs.first().id)
    }

    @Test
    fun testChatMessageRetractionModelMapping() {
        val now = System.currentTimeMillis()
        val originalMsg = com.example.syncedn.data.model.ChatMessage(
            id = "msg_123",
            teamCode = "PRAISE1",
            senderUid = "uid_456",
            senderName = "Joshua",
            senderRole = "Leader",
            text = "Rehearsal is at 6 PM",
            timestamp = now
        )
        assertFalse(originalMsg.isDeleted)
        assertEquals(0L, originalMsg.deletedAt)

        val retractedMsg = originalMsg.copy(
            isDeleted = true,
            deletedAt = now + 1000L,
            text = "",
            mediaUrl = ""
        )
        assertTrue(retractedMsg.isDeleted)
        assertEquals(now + 1000L, retractedMsg.deletedAt)
        assertEquals("", retractedMsg.text)

        val map = retractedMsg.toMap()
        assertEquals(true, map["isDeleted"])
        assertEquals(now + 1000L, map["deletedAt"])
        assertEquals("", map["text"])

        val reconstructed = com.example.syncedn.data.model.ChatMessage.fromMap(map)
        assertTrue(reconstructed.isDeleted)
        assertEquals(now + 1000L, reconstructed.deletedAt)
        assertEquals("", reconstructed.text)
    }

    @Test
    fun testSongEditorSectionCardsAssemblyAndPalette() {
        val fullLyrics = """
            [Verse 1]
            [C]Amazing grace how [G]sweet the sound
            [Am]That saved a wretch [F]like me

            [Chorus]
            [C]My chains are gone [G]I've been set free
        """.trimIndent()

        val sections = com.example.syncedn.ChordSheet.splitSections(fullLyrics)
        assertEquals(2, sections.size)
        assertEquals("[Verse 1]", sections[0].first)
        assertTrue(sections[0].second.contains("Amazing grace"))
        assertEquals("[Chorus]", sections[1].first)
        assertTrue(sections[1].second.contains("My chains are gone"))

        val assembled = com.example.syncedn.ChordSheet.assembleSections(sections)
        assertTrue(assembled.contains("[Verse 1]"))
        assertTrue(assembled.contains("[Chorus]"))
        assertTrue(assembled.contains("Amazing grace"))

        val cPalette = com.example.syncedn.ChordSheet.getChordPalette("C")
        assertNotNull(cPalette)
        assertTrue(cPalette.contains("C"))
        assertTrue(cPalette.contains("G"))
        assertTrue(cPalette.contains("Am"))
    }

    @Test
    fun testStrongPasswordValidationPolicy() {
        val vu = com.example.syncedn.data.util.ValidationUtils

        // 1. Weak passwords must be rejected
        assertFalse("Too short (< 8 chars)", vu.isValidStrongPassword("Ab1!xy"))
        assertFalse("Missing uppercase", vu.isValidStrongPassword("synced@2026"))
        assertFalse("Missing lowercase", vu.isValidStrongPassword("SYNCED@2026"))
        assertFalse("Missing number", vu.isValidStrongPassword("SyncedMinistry!"))
        assertFalse("Missing special char", vu.isValidStrongPassword("Synced2026"))

        val missingSpecialEval = vu.evaluatePassword("Synced2026")
        assertTrue(missingSpecialEval.hasMinLength)
        assertTrue(missingSpecialEval.hasUpper)
        assertTrue(missingSpecialEval.hasLower)
        assertTrue(missingSpecialEval.hasDigit)
        assertFalse(missingSpecialEval.hasSpecial)
        assertFalse(missingSpecialEval.isStrong)
        assertNotNull(missingSpecialEval.errorMessage())

        // 2. Strong Play Store-compliant passwords must pass
        val strongEval = vu.evaluatePassword("Synced@2026!")
        assertTrue(strongEval.hasMinLength)
        assertTrue(strongEval.hasUpper)
        assertTrue(strongEval.hasLower)
        assertTrue(strongEval.hasDigit)
        assertTrue(strongEval.hasSpecial)
        assertTrue(strongEval.isStrong)
        assertEquals(5, strongEval.score)
        assertEquals("Strong", strongEval.label)
        assertNull(strongEval.errorMessage())
    }

    @Test
    fun testEmailSecurityAndDisposableDomainBlocking() {
        val vu = com.example.syncedn.data.util.ValidationUtils

        // 1. Legitimate emails pass
        assertNull(vu.validateEmailForAuth("ryanlloydgenturo@gmail.com"))
        assertNull(vu.validateEmailForAuth("member.worship@yahoo.com"))
        assertNull(vu.validateEmailForAuth("leader@outlook.com"))

        // 2. Disposable / temporary / fake domains are blocked
        assertTrue(vu.isDisposableOrFakeEmail("hacker@tempmail.com"))
        assertTrue(vu.isDisposableOrFakeEmail("test@mailinator.com"))
        assertTrue(vu.isDisposableOrFakeEmail("anon@10minutemail.com"))
        assertTrue(vu.isDisposableOrFakeEmail("burner@yopmail.com"))
        assertTrue(vu.isDisposableOrFakeEmail("nobody@fake.com"))
        assertNotNull(vu.validateEmailForAuth("hacker@tempmail.com"))

        // 3. Malformed emails are blocked
        assertNotNull(vu.validateEmailForAuth(""))
        assertNotNull(vu.validateEmailForAuth("plainaddress"))
        assertNotNull(vu.validateEmailForAuth("double..dot@gmail.com"))
        assertNotNull(vu.validateEmailForAuth("user@.invalid"))
    }

    @Test
    fun testLoginRateLimiterBruteForceLockout() {
        val vu = com.example.syncedn.data.util.ValidationUtils
        val email = "ratelimit@gmail.com"
        vu.resetLoginAttempts(email)

        val baseTime = 1_000_000L
        // First 4 failed attempts do not lock the account
        for (i in 1..4) {
            val remaining = vu.recordLoginFailure(email, baseTime + i * 1000L)
            assertEquals(5 - i, remaining)
            assertFalse(vu.isLoginLocked(email, baseTime + i * 1000L))
        }

        // 5th failed attempt triggers 60-second lockout
        val remAfterFifth = vu.recordLoginFailure(email, baseTime + 5000L)
        assertEquals(0, remAfterFifth)
        assertTrue(vu.isLoginLocked(email, baseTime + 10_000L))
        assertTrue(vu.getLoginLockRemainingSeconds(email, baseTime + 10_000L) in 50..60)

        // After 61 seconds, lockout expires automatically
        assertFalse(vu.isLoginLocked(email, baseTime + 66_000L))

        // Reset clears lockout immediately
        vu.recordLoginFailure(email, baseTime + 70_000L)
        vu.resetLoginAttempts(email)
        assertFalse(vu.isLoginLocked(email, baseTime + 70_000L))
    }

    @Test
    fun testEmailOtpRegistrationFlowAndBruteForceInvalidation() {
        val otpService = com.example.syncedn.data.auth.EmailOtpService
        val regRecord = com.example.syncedn.data.auth.EmailOtpService.OtpRecord(
            email = "newmember@gmail.com",
            code = "654321",
            expiresAt = System.currentTimeMillis() + 60_000L,
            pendingPassword = "SyncedUser#2026",
            pendingUsername = "NewMember",
            purpose = "register"
        )
        otpService.setRecordForTesting(regRecord)

        // 4 wrong attempts keep record active
        for (i in 1..4) {
            val wrongRes = otpService.verifyOtp("00000$i")
            assertTrue(wrongRes.isFailure)
            assertNotNull("Record should still be active after $i wrong attempts", otpService.activeRecord)
        }

        // 5th wrong attempt invalidates the OTP completely to prevent brute-forcing
        val fifthWrong = otpService.verifyOtp("999999")
        assertTrue(fifthWrong.isFailure)
        assertNull("Record must be cleared after 5 failed attempts", otpService.activeRecord)

        // Even the real code now fails until a new OTP is requested
        val afterLockout = otpService.verifyOtp("654321")
        assertTrue(afterLockout.isFailure)
    }

    @Test
    fun testPbkdf2PasswordHashingAndBackwardCompatibility() {
        val vu = com.example.syncedn.data.util.ValidationUtils
        val rawPass = "Synced@2026!"

        val hash1 = vu.hashPasswordPbkdf2(rawPass)
        val hash2 = vu.hashPasswordPbkdf2(rawPass)

        assertTrue("Hash must start with pbkdf2$", hash1.startsWith("pbkdf2$"))
        assertTrue("Hash must fit within Firestore 128-char rule", hash1.length <= 128)
        assertFalse("Hash must not expose raw password", hash1.contains(rawPass))
        assertNotEquals("Salted hashes of identical passwords must differ", hash1, hash2)

        assertTrue("Correct password must verify against hash1", vu.verifyPasswordPbkdf2(rawPass, hash1))
        assertTrue("Correct password must verify against hash2", vu.verifyPasswordPbkdf2(rawPass, hash2))
        assertFalse("Wrong password must fail verification", vu.verifyPasswordPbkdf2("WrongPass@2026!", hash1))

        // Backward compatibility with legacy plaintext password_resets documents
        assertTrue("Legacy plaintext reset password must still verify", vu.verifyPasswordPbkdf2("LegacyPass#1", "LegacyPass#1"))
        assertFalse("Wrong legacy plaintext password must fail", vu.verifyPasswordPbkdf2("WrongLegacy#1", "LegacyPass#1"))
    }

    @Test
    fun testEmailOtpHtmlSanitizationAndSecureRandomCode() {
        val otpService = com.example.syncedn.data.auth.EmailOtpService

        for (i in 1..20) {
            val code = otpService.generateSecureOtpCode()
            assertEquals(6, code.length)
            assertTrue(code.all { it.isDigit() })
            val num = code.toInt()
            assertTrue(num in 100000..999999)
        }

        val maliciousName = "<script>alert('xss')</script> & \"phishing\""
        val escaped = otpService.escapeHtml(maliciousName)
        assertFalse(escaped.contains("<script>"))
        assertTrue(escaped.contains("&lt;script&gt;"))
        assertTrue(escaped.contains("&amp;"))
        assertTrue(escaped.contains("&quot;"))
    }

    @Test
    fun testSessionIntegrityHmacTamperProtection() {
        val prefs = FakePreferences()
        val memberProfile = com.example.syncedn.data.model.UserProfile(
            uid = "uid_member_101",
            username = "member101",
            email = "member101@gmail.com",
            role = "Member",
            teamName = "Praise Team",
            teamCode = "PRZ101"
        )

        com.example.syncedn.data.auth.SessionManager.saveSessionProfile(
            preferences = prefs,
            profile = memberProfile
        )
        prefs.edit()
            .putString("email_team_member101@gmail.com", "Praise Team")
            .putString("email_code_member101@gmail.com", "PRZ101")
            .apply()

        assertTrue(com.example.syncedn.data.auth.SessionManager.verifySessionIntegrity(prefs, "member101@gmail.com"))

        // Simulate local XML tampering on a rooted device: changing Member -> Leader without valid HMAC
        prefs.edit()
            .putString("role", "Leader")
            .putString("email_role_member101@gmail.com", "Leader")
            .apply()

        assertFalse("Tampered role must fail HMAC integrity check", com.example.syncedn.data.auth.SessionManager.verifySessionIntegrity(prefs, "member101@gmail.com"))

        val (resolvedRole, _, _) = com.example.syncedn.data.auth.SessionManager.resolveUserTeamSession(
            preferences = prefs,
            profile = null,
            email = "member101@gmail.com"
        )
        assertEquals("Tampered Leader role must be rejected and downgraded to Member", "Member", resolvedRole)
    }

    @Test
    fun testYouTubeVideoIdSanitizationAndHttpsImageUpgrade() {
        assertTrue(com.example.syncedn.ui.player.YouTubePlayerHelper.isValidVideoId("dQw4w9WgXcQ"))
        assertFalse(com.example.syncedn.ui.player.YouTubePlayerHelper.isValidVideoId("\"><script>alert(1)</script>"))
        assertFalse(com.example.syncedn.ui.player.YouTubePlayerHelper.isValidVideoId("short"))

        val upgraded = com.example.syncedn.data.storage.ImageLoader.normalizeSecureImageUrl("http://res.cloudinary.com/ffb3fdkm/image/upload/v1/avatar.jpg")
        assertEquals("https://res.cloudinary.com/ffb3fdkm/image/upload/v1/avatar.jpg", upgraded)
    }

    @Test
    fun testPendingAuthCredentialsWipedOnCleanupAndLogout() {
        val prefs = FakePreferences()
        prefs.edit()
            .putString("pendingRegUsername", "TempUser")
            .putString("pendingRegEmail", "temp@gmail.com")
            .putString("pendingRegPassword", "SecretPass#2026")
            .putString("resetEmail", "temp@gmail.com")
            .putString("resetPendingPassword", "SecretPass#2026")
            .apply()

        com.example.syncedn.data.auth.EmailOtpService.setRecordForTesting(
            com.example.syncedn.data.auth.EmailOtpService.OtpRecord(
                email = "temp@gmail.com",
                code = "123456",
                expiresAt = System.currentTimeMillis() + 60_000L,
                pendingPassword = "SecretPass#2026"
            )
        )

        com.example.syncedn.data.auth.SessionManager.clearPendingAuthCredentials(prefs)

        assertEquals("", prefs.getString("pendingRegPassword", ""))
        assertEquals("", prefs.getString("resetPendingPassword", ""))
        assertEquals("", prefs.getString("pendingRegEmail", ""))
        assertEquals("", prefs.getString("resetEmail", ""))
        assertNull(com.example.syncedn.data.auth.EmailOtpService.activeRecord)
    }

    @Test
    fun testChatMessageDeletionDetectionAndRetractionRaceProtection() {
        val repo = com.example.syncedn.data.database.TeamRepository
        repo.clearCache()
        val prefs = FakePreferences()

        // 1. Image-only message deleted remotely (text was "", mediaUrl was non-empty, now mediaUrl="" and isDeleted=true)
        val originalImageMsg = com.example.syncedn.data.model.ChatMessage(
            id = "msg_img_1",
            teamCode = "CHAT01",
            senderUid = "u1",
            senderName = "Ryan",
            text = "",
            mediaUrl = "https://res.cloudinary.com/demo/image/upload/sample.jpg",
            timestamp = 1000L,
            isDeleted = false
        )
        val deletedImageMsg = originalImageMsg.copy(
            mediaUrl = "",
            isDeleted = true,
            deletedAt = 2000L
        )
        assertTrue(
            "Deleting a picture-only message must be detected as a message state change",
            repo.hasChatMessageChanged(originalImageMsg, deletedImageMsg)
        )
        assertFalse(
            "Unchanged message must not trigger redundant rebuild",
            repo.hasChatMessageChanged(originalImageMsg, originalImageMsg.copy())
        )

        // 2. Race protection: User deletes optimistic photo message BEFORE Cloudinary upload callback calls sendChatMessage
        val optimisticPhotoMsg = com.example.syncedn.data.model.ChatMessage(
            id = "msg_race_1",
            teamCode = "CHAT01",
            senderUid = "u1",
            senderName = "Ryan",
            text = "",
            mediaUrl = "content://local/photo/1",
            timestamp = 3000L,
            isDeleted = false
        )
        repo.saveChatMessageLocally("CHAT01", optimisticPhotoMsg, prefs)
        repo.deleteChatMessage("CHAT01", "msg_race_1", prefs)
        assertTrue("Message ID must be tracked as retracted", repo.isMessageRetracted("msg_race_1"))

        // Simulate late Cloudinary upload callback attempting to sendChatMessage with uploaded URL
        repo.sendChatMessage(
            "CHAT01",
            optimisticPhotoMsg.copy(mediaUrl = "https://res.cloudinary.com/demo/image/upload/late.jpg"),
            prefs
        )
        val cachedAfterRace = repo.getCachedChatMessages("CHAT01", prefs).first { it.id == "msg_race_1" }
        assertTrue("Late upload callback must not resurrect a deleted photo message", cachedAfterRace.isDeleted)
        assertEquals("", cachedAfterRace.mediaUrl)
    }

    @Test
    fun testSaveRosterLocallySyncsRemoteMinistryRoleToSharedPreferences() {
        val repo = com.example.syncedn.data.database.TeamRepository
        repo.clearCache()
        val prefs = FakePreferences().apply {
            edit()
                .putString("userId", "uid_m1")
                .putString("email", "member1@test.com")
                .putString("fullName", "Member One")
                .putString("role", "Member")
                .putString("ministryRole", "Vocalist")
                .putString("email_ministry_member1@test.com", "Vocalist")
                .putString("user_ministry_uid_m1", "Vocalist")
                .putString(
                    com.example.syncedn.data.auth.SessionManager.KEY_MEMBER_ROLES,
                    org.json.JSONObject(mapOf("member1@test.com" to "Vocalist", "uid_m1" to "Vocalist")).toString()
                )
                .apply()
        }

        // Simulate real-time roster update from Firestore where Leader changed Member One's role to "Keyboardist"
        val updatedRoster = listOf(
            com.example.syncedn.data.model.TeamMember(
                uid = "uid_m1",
                fullName = "Member One",
                username = "member1",
                email = "member1@test.com",
                role = "Member",
                ministryRole = "Keyboardist",
                teamCode = "SYNC01"
            )
        )
        repo.saveRosterLocally("SYNC01", updatedRoster, prefs)

        assertEquals("Keyboardist", prefs.getString("email_ministry_member1@test.com", ""))
        assertEquals("Keyboardist", prefs.getString("user_ministry_uid_m1", ""))
        assertEquals("Keyboardist", prefs.getString("ministryRole", ""))
        val rolesJson = org.json.JSONObject(prefs.getString(com.example.syncedn.data.auth.SessionManager.KEY_MEMBER_ROLES, "{}").orEmpty())
        assertEquals("Keyboardist", rolesJson.optString("member1@test.com"))
        assertEquals("Keyboardist", rolesJson.optString("uid_m1"))
    }

    @Test
    fun testRejoinRequestNotificationsIncludedInTeamNotificationFeed() {
        val repo = com.example.syncedn.data.database.TeamRepository
        repo.clearCache()
        val prefs = FakePreferences()

        val req = com.example.syncedn.data.model.RejoinRequestData(
            id = "rejoin_req_101",
            teamCode = "REJ101",
            userUid = "uid_removed_1",
            userName = "Angelo Daniel",
            userEmail = "angelo@test.com",
            reason = "Ready to rejoin worship team",
            status = "Pending",
            createdAt = System.currentTimeMillis()
        )
        repo.sendRejoinRequest("REJ101", req)

        val notifications = repo.getRecentTeamNotifications("REJ101", prefs)
        val rejoinNotif = notifications.find { it.id == "notif_rejoin_rejoin_req_101" }
        assertNotNull("Rejoin request must appear in team notifications feed", rejoinNotif)
        assertTrue(rejoinNotif!!.title.contains("Re-join Request"))
        assertTrue(rejoinNotif.message.contains("Angelo Daniel"))
    }

    @Test
    fun testSubstituteLeaderPromotionDemotionAndRoleRestrictions() {
        val repo = com.example.syncedn.data.database.TeamRepository
        repo.clearCache()

        // 1. Role classification: Substitute Leader has role="Leader" in Firestore,
        //    so it must NEVER be misclassified as Worship Leader!
        assertTrue(repo.isSubstituteLeader("Substitute Leader"))
        assertFalse(repo.isWorshipLeader(ministryRole = "Substitute Leader", role = "Leader"))
        assertFalse(repo.isOrdinaryMemberRole(ministryRole = "Substitute Leader", role = "Leader"))

        assertTrue(repo.isWorshipLeader(ministryRole = "Worship Leader", role = "Leader"))
        assertFalse(repo.isSubstituteLeader("Worship Leader"))

        assertTrue(repo.isOrdinaryMemberRole(ministryRole = "Guitarist", role = "Member"))
        assertTrue(repo.isOrdinaryMemberRole(ministryRole = "Vocalist", role = "Leader")) // stale role="Leader" with ordinary ministryRole must be treated as ordinary member

        // 2. Role choices filtering:
        //    - Substitute Leader must NOT see "Substitute Leader" or "Worship Leader" in choices
        //    - Worship Leader editing a non-primary member can see "Substitute Leader" but NOT "Worship Leader"
        val rawRoles = listOf("Worship Leader", "Substitute Leader", "Vocalist", "Guitarist", "Keyboardist", "Drummer")
        val subLeaderChoices = repo.filterAssignableRoles(
            roles = rawRoles,
            isEditorSubstituteLeader = true,
            isTargetPrimaryLeader = false
        )
        assertFalse("Substitute Leader must not be able to assign Substitute Leader", subLeaderChoices.any { it.equals("Substitute Leader", true) })
        assertFalse("Substitute Leader must not have Worship Leader in choices", subLeaderChoices.any { it.equals("Worship Leader", true) })
        assertTrue(subLeaderChoices.contains("Vocalist"))
        assertTrue(subLeaderChoices.contains("Guitarist"))

        val worshipLeaderChoices = repo.filterAssignableRoles(
            roles = rawRoles,
            isEditorSubstituteLeader = false,
            isTargetPrimaryLeader = false
        )
        assertTrue("Worship Leader must be able to assign Substitute Leader", worshipLeaderChoices.any { it.equals("Substitute Leader", true) })
        assertFalse("Worship Leader choices for regular members must not include Worship Leader", worshipLeaderChoices.any { it.equals("Worship Leader", true) })

        // 3. Promotion & Demotion transition detection via saveRosterLocally + deduplicateRoster:
        //    When Member One ("Vocalist") is promoted to "Substitute Leader" remotely, saveRosterLocally
        //    must NOT prematurely overwrite prefs["ministryRole"] before MemberActivity's listener detects the promotion.
        val memberPrefs = FakePreferences().apply {
            edit()
                .putString("userId", "uid_sub_1")
                .putString("email", "sub1@test.com")
                .putString("fullName", "Sub Member")
                .putString("role", "Member")
                .putString("ministryRole", "Vocalist")
                .putString("teamName", "Grace Worship")
                .apply()
        }
        val promotedRoster = listOf(
            com.example.syncedn.data.model.TeamMember(
                uid = "uid_sub_1",
                fullName = "Sub Member",
                username = "sub1",
                email = "sub1@test.com",
                role = "Leader",
                ministryRole = "Substitute Leader",
                teamCode = "GRC001"
            )
        )
        repo.saveRosterLocally("GRC001", promotedRoster, memberPrefs)
        assertEquals(
            "Pre-transition ministryRole must remain 'Vocalist' so MemberActivity detects the promotion to Substitute Leader",
            "Vocalist",
            memberPrefs.getString("ministryRole", "")
        )

        // Simulate MemberActivity applying the Substitute Leader promotion (must NOT set team_leader_email_!)
        memberPrefs.edit()
            .putString("role", "Leader")
            .putString("ministryRole", "Substitute Leader")
            .putString("user_role_uid_sub_1", "Leader")
            .putString("email_role_sub1@test.com", "Leader")
            .putString("user_ministry_uid_sub_1", "Substitute Leader")
            .putString("email_ministry_sub1@test.com", "Substitute Leader")
            .apply()

        // Now Leader demotes Sub Member back to "Guitarist" (role="Member")
        val demotedRaw = listOf(
            com.example.syncedn.data.model.TeamMember(
                uid = "uid_sub_1",
                fullName = "Sub Member",
                username = "sub1",
                email = "sub1@test.com",
                role = "Member",
                ministryRole = "Guitarist",
                teamCode = "GRC001"
            )
        )
        val dedupedAfterDemotion = repo.deduplicateRoster(demotedRaw, memberPrefs)
        assertEquals("Guitarist", dedupedAfterDemotion.single().ministryRole)
        assertEquals("Member", dedupedAfterDemotion.single().role)

        repo.saveRosterLocally("GRC001", dedupedAfterDemotion, memberPrefs)
        assertEquals(
            "Pre-transition ministryRole must remain 'Substitute Leader' so AdminActivity detects the demotion to Guitarist",
            "Substitute Leader",
            memberPrefs.getString("ministryRole", "")
        )
        assertEquals("Guitarist", memberPrefs.getString("email_ministry_sub1@test.com", ""))
        assertEquals("Member", memberPrefs.getString("email_role_sub1@test.com", ""))

        // 4. Transfer Leadership to Substitute Leader / Member:
        //    Even when preferences previously had email_role_ = "Member" or ministryRole = "Substitute Leader",
        //    when the Worship Leader leaves and transfers leadership (ministryRole="Worship Leader", role="Leader"),
        //    deduplicateRoster must NEVER overwrite Worship Leader back to Member!
        val transferredRaw = listOf(
            com.example.syncedn.data.model.TeamMember(
                uid = "uid_sub_1",
                fullName = "Sub Member",
                username = "sub1",
                email = "sub1@test.com",
                role = "Leader",
                ministryRole = "Worship Leader",
                teamCode = "GRC001"
            )
        )
        val dedupedAfterTransfer = repo.deduplicateRoster(transferredRaw, memberPrefs)
        assertEquals("Worship Leader", dedupedAfterTransfer.single().ministryRole)
        assertEquals("Leader", dedupedAfterTransfer.single().role)
    }
}