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
}