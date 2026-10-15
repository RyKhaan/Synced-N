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
}