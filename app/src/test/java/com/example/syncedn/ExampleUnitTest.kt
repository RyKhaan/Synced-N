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

    private class FakePreferences : android.content.SharedPreferences {
        private val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
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
            override fun putStringSet(key: String?, values: MutableSet<String>?): android.content.SharedPreferences.Editor = this
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
}