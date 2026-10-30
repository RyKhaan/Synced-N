package com.example.syncedn

import com.example.syncedn.data.auth.AuthManager
import com.example.syncedn.data.auth.EmailOtpService
import com.example.syncedn.data.auth.SessionManager
import com.example.syncedn.data.database.FirestoreManager
import com.example.syncedn.data.database.TeamRepository
import com.example.syncedn.data.model.ChatMessage
import com.example.syncedn.data.model.SongData
import com.example.syncedn.data.model.AnnouncementData
import com.example.syncedn.data.model.SubstitutionData
import com.example.syncedn.data.model.TeamMember
import com.example.syncedn.data.storage.CloudinaryUploader
import com.example.syncedn.data.storage.ImageLoader
import com.example.syncedn.data.model.UnavailableRange
import com.example.syncedn.data.util.ValidationUtils
import com.google.firebase.firestore.ListenerRegistration
import android.text.InputFilter
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import androidx.core.content.edit
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.Switch
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Outline
import android.view.ViewOutlineProvider
import android.view.ViewGroup
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.Base64
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.doOnLayout
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import android.graphics.Typeface
import androidx.core.graphics.PathParser
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation

import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * MEMBER VERSION: ONE-FILE Synced N front-end. Create an Empty Views Activity project in Kotlin.
 * Package: com.example.syncedn. Add this alongside MainActivity.kt and SharedUi.kt.
 * No custom XML, drawable files, CardView, server or extra dependencies required.
 * If your package differs, change the first line. Keep the generated manifest.
 * Screens are built below in Kotlin; artwork is embedded at the end of this file.
 * Demo login: any nonempty email/mobile + at least 5 password characters.
 * Demo OTP: 123456. Google sign-in and team actions are not real authentication.
 * Member pages: calendar, availability, schedules, service assignments, substitution,
 * announcements, songs/details/lineup, team chat/members/roles and team settings.
 * Requests, reports and availability are local demo data; messages are not sent.
 */
open class MemberActivity : AppCompatActivity() {
    private fun showMemberProfileDialog(uid: String?, name: String, role: String, photoUri: String?) {
        val roster = getActiveRoster()
        val m = roster.find { (uid != null && uid.isNotBlank() && it.uid == uid) || it.fullName.equals(name, true) || it.username.equals(name, true) }
        val dialog = AlertDialog.Builder(this)
        val pUri = photoUri?.takeIf { it.isNotBlank() } ?: m?.photoUri.orEmpty()
        val view = vertical().apply {
            setPadding(dp(24), dp(24), dp(24), dp(24))
            gravity = Gravity.CENTER_HORIZONTAL
            add(memberAvatarView(pUri, 72), 72, 72)
            space(12)
            add(text(m?.fullName?.ifBlank { name } ?: name, 16, navy, bold = true, centered = true))
            space(4)
            add(text("@${m?.username?.ifBlank { name } ?: name}", 12, Color.GRAY, centered = true))
            space(8)
            add(text(m?.ministryRole?.ifBlank { role } ?: role, 13, purple, bold = true, centered = true).apply {
                background = bg(Color.rgb(245, 208, 254), 12)
                setPadding(dp(12), dp(4), dp(12), dp(4))
            })
            if (m?.email?.isNotBlank() == true) {
                space(12)
                add(text("✉ ${m.email}", 12, navy, centered = true))
            }
        }
        dialog.setView(view).setPositiveButton("Close", null).show()
    }

    private fun extractYouTubeVideoId(url: String): String? {
        if (url.isBlank()) return null
        val pattern = "(?<=watch\\?v=|/videos/|embed/|youtu\\.be/|/v/|/e/|watch\\?feature=player_embedded&v=)[^#&?\\n]*"
        val compiledPattern = java.util.regex.Pattern.compile(pattern)
        val matcher = compiledPattern.matcher(url)
        return if (matcher.find()) matcher.group() else null
    }

    private fun showChatImageViewer(imageUrl: String) {
        if (imageUrl.isBlank()) return
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val container = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(235, 0, 0, 0))
        }
        val iv = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        ImageLoader.loadProfilePhoto(iv, imageUrl, circular = false)
        container.addView(iv, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))

        val closeBtn = text("✕ Close", 14, Color.WHITE, bold = true, centered = true).apply {
            background = bg(Color.argb(120, 50, 50, 50), 16)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            isClickable = true
            isFocusable = true
            setOnClickListener { dialog.dismiss() }
        }
        container.addView(closeBtn, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
            topMargin = dp(40); rightMargin = dp(20)
        })
        dialog.setContentView(container)
        dialog.show()
    }

    private var substitutionType = "Exclusion"
    private var servicesListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var substitutionsListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var rosterListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var customRolesListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var userMembershipListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var teamDocListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var userEmailMembershipListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var isShowingRemovedDialog = false
    private var isSwitchingToLeaderDashboard = false
    private var lastObservedMinistryRole = ""
    private val chatScrollRunnable = Runnable { chatScrollView?.fullScroll(View.FOCUS_DOWN) }
    protected open val initialPage: String = "home"
    private var page = "start"
    private var displayName = ""
    private var fullName = ""
    private var profileReturnPage = "settings"
    protected val preferences by lazy { getSharedPreferences("synced_profile", MODE_PRIVATE) }
    private var teamName = ""
    private var successFlow = "register"

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val code = preferences.getString("teamCode", "").orEmpty()
            com.example.syncedn.data.auth.SyncedFirebaseMessagingService.syncToken(this, preferences)
            if (code.isNotBlank()) {
                com.example.syncedn.data.auth.SyncedFirebaseMessagingService.subscribeToTeam(code)
                com.example.syncedn.data.auth.SyncedMessagingService.startRealtimeTeamWatchers(this, preferences)
            }
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        val code = preferences.getString("teamCode", "").orEmpty()
        com.example.syncedn.data.auth.SyncedFirebaseMessagingService.syncToken(this, preferences)
        if (code.isNotBlank()) {
            com.example.syncedn.data.auth.SyncedFirebaseMessagingService.subscribeToTeam(code)
                com.example.syncedn.data.auth.SyncedMessagingService.startRealtimeTeamWatchers(this, preferences)
        }
    }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken != null) {
                    toast("Signing in with Google...")
                    AuthManager.signInWithGoogle(idToken) { authResult ->
                        runOnUiThread {
                            if (authResult.isSuccess) {
                                val profile = authResult.getOrNull()
                                displayName = profile?.username ?: account.displayName ?: "Member"
                                val emailStr = account.email.orEmpty().trim()
                                val uid = profile?.uid.orEmpty()
                                SessionManager.clearPreferencesOnly(preferences)
                                val (userRole, userTeam, userTeamCode) = resolveUserTeamSession(profile, emailStr)
                                teamName = userTeam
                                SessionManager.saveSessionProfile(preferences, profile, displayName, emailStr)
                                fullName = preferences.getString(SessionManager.KEY_FULL_NAME, "").orEmpty()
                                displayName = preferences.getString(SessionManager.KEY_USERNAME, displayName).orEmpty()
                                val isRemoved = profile?.removedByLeader == true && (profile.removedTeamCode.isBlank() || profile.removedTeamCode == userTeamCode)
                                if (userTeam.isNotBlank() && !isRemoved) {
                                    saveUserTeamState(userRole, userTeam, userTeamCode)
                                    FirestoreManager.updateUserRoleAndTeam(uid, userRole, userTeam, userTeamCode, emailStr)
                                } else {
                                    preferences.edit {
                                        putString("role", "Member")
                                        putString("teamName", "")
                                        putString("teamCode", "")
                                        putString("ministryRole", "")
                                        putBoolean("removedByLeader", isRemoved)
                                        if (isRemoved) {
                                            val rCode = profile?.removedTeamCode?.ifBlank { userTeamCode }.orEmpty()
                                            if (rCode.isNotBlank()) putString("removedTeamCode", rCode)
                                        }
                                    }
                                }
                                toast("Welcome, $displayName!")
                                if (userTeam.isBlank() || isRemoved) {
                                    navigate("teamChoice")
                                } else if (userRole.equals("Leader", ignoreCase = true)) {
                                    startActivity(Intent(this@MemberActivity, AdminActivity::class.java).putExtra("startPage", "home"))
                                    finish()
                                } else {
                                    if (javaClass == MainActivity::class.java) {
                                        startActivity(Intent(this@MemberActivity, MemberActivity::class.java).putExtra("startPage", "home"))
                                        finish()
                                    } else {
                                        navigate("home")
                                    }
                                }
                            } else {
                                val msg = authResult.exceptionOrNull()?.localizedMessage ?: "Google sign-in failed."
                                toast(msg)
                            }
                        }
                    }
                } else {
                    toast("Could not retrieve Google ID Token.")
                }
            } catch (e: ApiException) {
                toast("Google sign-in cancelled or failed (${e.statusCode})")
            }
        }
    }

    private fun syncLocalTeamsToMemory() {
        for ((key, value) in preferences.all) {
            if (key.startsWith("team_registry_") && value is String) {
                val code = key.removePrefix("team_registry_")
                FirestoreManager.registerLocalTeam(code, value)
            }
        }
        val activeCode = preferences.getString("teamCode", "").orEmpty()
        val activeTeam = preferences.getString("teamName", "").orEmpty()
        if (activeCode.isNotBlank() && activeTeam.isNotBlank()) {
            FirestoreManager.registerLocalTeam(activeCode, activeTeam)
        }
        if (activeCode.isNotBlank()) {
            com.example.syncedn.data.database.TeamRepository.syncFromPreferences(preferences, activeCode)
        }
    }

    private fun startRealtimeTeamListeners(teamCode: String) {
        val code = teamCode.trim().uppercase()
        if (code.isBlank()) return
        servicesListener?.remove()
        servicesListener = TeamRepository.subscribeToServices(code, isLeader = false, preferences) { updatedList ->
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    if (page in listOf("home", "schedule", "notifications")) {
                        navigate(page, triggerRefresh = false)
                    } else if (page == "service") {
                        selectedSheetService?.let { sel ->
                            val sid = sel.optString("id")
                            val fresh = updatedList.find { it.id == sid }
                            if (fresh != null) {
                                selectedSheetService = org.json.JSONObject(fresh.toMap())
                                navigate("service", triggerRefresh = false)
                            }
                        }
                    }
                }
            }
        }
        substitutionsListener?.remove()
        teamDocListener?.remove()
        teamDocListener = TeamRepository.subscribeToTeam(code, preferences) {
            runOnUiThread {
                handleTeamDeletedRemotely()
            }
        }
        substitutionsListener = TeamRepository.subscribeToSubstitutions(code, preferences) { _ ->
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    if (page in listOf("notifications", "announcements", "schedule", "service", "substitution")) {
                        navigate(page, triggerRefresh = false)
                    }
                }
            }
        }
        rosterListener?.remove()
        rosterListener = TeamRepository.subscribeToRoster(code, preferences) { updatedRoster ->
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    teamRoster = updatedRoster
                    updateRosterViews()
                }
            }
        }
        customRolesListener?.remove()
        customRolesListener = TeamRepository.subscribeToCustomRoles(code) { _ ->
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    if (page == "teams" && teamTab in listOf("Role", "Overview")) {
                        navigate("teams", triggerRefresh = false)
                    }
                }
            }
        }
    }

    private fun resolveUserTeamSession(
        profile: com.example.syncedn.data.model.UserProfile?,
        email: String
    ): Triple<String, String, String> {
        syncLocalTeamsToMemory()
        val currentTeamCode = preferences.getString(SessionManager.KEY_TEAM_CODE, "").orEmpty()
            .ifBlank { preferences.getString("teamCode", "").orEmpty() }
        if (currentTeamCode.isNotBlank()) {
            startRealtimeTeamListeners(currentTeamCode)
        }

        return SessionManager.resolveUserTeamSession(preferences, profile, email)
    }

    private fun currentUserId(): String {
        val emailLower = preferences.getString("email", "").orEmpty().trim().lowercase()
        return com.example.syncedn.data.auth.AuthManager.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: preferences.getString(SessionManager.KEY_USER_ID, "").orEmpty().takeIf { it.isNotBlank() && !it.startsWith("user_") }
            ?: (if (emailLower.isNotBlank()) preferences.getString("email_uid_$emailLower", "").orEmpty().takeIf { it.isNotBlank() } else null)
            ?: preferences.getString(SessionManager.KEY_USER_ID, "").orEmpty().ifBlank {
                "user_${emailLower.ifBlank { displayName.lowercase() }.hashCode()}"
            }
    }

    private fun saveUserTeamState(role: String, team: String, code: String) {
        val uid = currentUserId()
        val emailLower = preferences.getString("email", "").orEmpty().trim().lowercase()
        val cleanTeamLower = team.trim().lowercase()
        if (code.isNotBlank() && team.isNotBlank()) {
            FirestoreManager.registerLocalTeam(code, team)
            val currentName = fullName.ifBlank { displayName }
            val ministry = preferences.getString("ministryRole", if (role.equals("Leader", true)) "Worship Leader" else "Member").orEmpty()
            val existingRanges = TeamRepository.getMemberUnavailableRanges(uid, preferences, emailLower, currentName)
            val member = com.example.syncedn.data.model.TeamMember(
                uid = uid,
                fullName = currentName,
                username = displayName,
                email = emailLower,
                role = role,
                ministryRole = ministry,
                teamCode = code,
                photoUri = preferences.getString("photoUri", "").orEmpty(),
                unavailableRanges = existingRanges
            )
            TeamRepository.cacheMember(member, preferences)
        }
        FirestoreManager.cacheUserRoleAndTeam(uid, emailLower, role, team, code)
        if (code.isNotBlank()) {
            com.example.syncedn.data.auth.SyncedFirebaseMessagingService.subscribeToTeam(code)
            com.example.syncedn.data.auth.SyncedMessagingService.startRealtimeTeamWatchers(this, preferences)
            com.example.syncedn.data.auth.SyncedFirebaseMessagingService.syncToken(this, preferences)
            startRealtimeTeamListeners(code)
        }
        preferences.edit {
            putBoolean("leftTeam", false)
            putString("role", role)
            putString("teamName", team)
            putString("teamCode", code)
            if (code.isNotBlank() && team.isNotBlank()) {
                putString("team_registry_$code", team)
                putString("team_leader_code_$cleanTeamLower", code)
            }
            val isSubLeader = preferences.getString("ministryRole", "").equals("Substitute Leader", ignoreCase = true)
            if (!isSubLeader && role.equals("Leader", ignoreCase = true) && cleanTeamLower.isNotBlank() && emailLower.isNotBlank()) {
                putString("team_leader_email_$cleanTeamLower", emailLower)
            }
            if (uid.isNotBlank()) {
                putString("user_role_$uid", role)
                putString("user_team_$uid", team)
                putString("user_code_$uid", code)
            }
            if (emailLower.isNotBlank()) {
                putString("email_role_$emailLower", role)
                putString("email_team_$emailLower", team)
                putString("email_code_$emailLower", code)
                if (uid.isNotBlank()) putString("email_uid_$emailLower", uid)
            }
        }
    }

    private fun handleSelfRoleSync(
        remoteMinistryRole: String,
        remoteRole: String,
        myUid: String = currentUserId(),
        myEmail: String = preferences.getString("email", "").orEmpty()
    ) {
        if (isFinishing || isDestroyed || isSwitchingToLeaderDashboard) return
        val isAuthOrOnboardingScreen = javaClass == MainActivity::class.java ||
            page in listOf("start", "login", "register", "reset", "teamChoice", "createTeam", "joinTeam")
        val emailLower = myEmail.trim().lowercase()
        val cleanTeam = teamName.trim().lowercase()
        val currentPref = preferences.getString("ministryRole", "").orEmpty()
        val currentRolePref = preferences.getString("role", "").orEmpty()
        val now = System.currentTimeMillis()
        val lastRoleSwitchTime = preferences.getLong("last_role_switch_time_$myUid", 0L)
        val lastSwitchedRole = preferences.getString("last_switched_role_$myUid", "").orEmpty()

        // 1. Check Substitute Leader FIRST (because Substitute Leader also has role == "Leader" in Firestore!)
        if (TeamRepository.isSubstituteLeader(remoteMinistryRole) && !remoteRole.equals("Member", ignoreCase = true)) {
            if (now - lastRoleSwitchTime < 3000L && lastSwitchedRole == "Substitute Leader") {
                return
            }
            lastObservedMinistryRole = "Substitute Leader"
            val editor = preferences.edit()
                .putString("role", "Leader")
                .putString(SessionManager.KEY_ROLE, "Leader")
                .putString("ministryRole", "Substitute Leader")
                .putString(SessionManager.KEY_MINISTRY_ROLE, "Substitute Leader")
                .putLong("last_role_switch_time_$myUid", now)
                .putString("last_switched_role_$myUid", "Substitute Leader")
            if (myUid.isNotBlank()) {
                editor.putString("user_role_$myUid", "Leader")
                editor.putString("user_ministry_$myUid", "Substitute Leader")
            }
            if (emailLower.isNotBlank()) {
                editor.putString("email_role_$emailLower", "Leader")
                editor.putString("email_ministry_$emailLower", "Substitute Leader")
                val sig = com.example.syncedn.data.util.ValidationUtils.computeSessionHmac(emailLower, "Leader", myUid)
                if (sig.isNotBlank()) editor.putString("session_hmac_sig_$emailLower", sig)
            }
            if (cleanTeam.isNotBlank() && preferences.getString("team_leader_email_$cleanTeam", "").equals(emailLower, ignoreCase = true)) {
                editor.remove("team_leader_email_$cleanTeam")
            }
            try {
                val map = org.json.JSONObject(preferences.getString(SessionManager.KEY_MEMBER_ROLES, "{}").orEmpty())
                if (myUid.isNotBlank()) map.put(myUid, "Substitute Leader")
                if (emailLower.isNotBlank()) map.put(emailLower, "Substitute Leader")
                editor.putString(SessionManager.KEY_MEMBER_ROLES, map.toString())
            } catch (_: Throwable) {}
            editor.commit()

            val lastNotifiedRole = preferences.getString("last_notified_role_$myUid", "").orEmpty()
            if (!isAuthOrOnboardingScreen) {
                isSwitchingToLeaderDashboard = true
                if (lastNotifiedRole != "Substitute Leader") {
                    preferences.edit().putString("last_notified_role_$myUid", "Substitute Leader").commit()
                    com.example.syncedn.data.auth.SyncedMessagingService.dispatchSystemNotification(
                        this@MemberActivity,
                        preferences,
                        "⭐ Promoted to Substitute Leader",
                        "You have been assigned as Substitute Leader for ${teamName.ifBlank { "your team" }}! Switching to Leader dashboard..."
                    )
                }
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        toast("You have been assigned as Substitute Leader! Switching to Leader dashboard...")
                        val intent = Intent(this@MemberActivity, AdminActivity::class.java)
                            .putExtra("startPage", "home")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(intent)
                        finish()
                    }
                }
            }
            return
        }

        // 2. Check Permanent Worship Leader (never true for Substitute Leader)
        val activeTeamCode = preferences.getString("teamCode", "").orEmpty().trim().uppercase()
        val transferred = preferences.getBoolean("transferred_leadership_$activeTeamCode", false)
        val designatedLeader = preferences.getString("team_leader_email_$activeTeamCode", "").orEmpty().trim().lowercase()
            .ifBlank { preferences.getString("team_leader_email_$cleanTeam", "").orEmpty().trim().lowercase() }
        val isExplicitMember = remoteRole.equals("Member", ignoreCase = true) || remoteMinistryRole.equals("Member", ignoreCase = true)
        val isWorshipLeadEligible = !isExplicitMember && (
            (designatedLeader.isNotBlank() && designatedLeader == emailLower) ||
            (!transferred && designatedLeader.isBlank() && TeamRepository.isWorshipLeader(remoteMinistryRole, remoteRole))
        )
        if (isWorshipLeadEligible) {
            if (now - lastRoleSwitchTime < 3000L && lastSwitchedRole == "Worship Leader") {
                return
            }
            lastObservedMinistryRole = "Worship Leader"
            val editor = preferences.edit()
                .putString("role", "Leader")
                .putString(SessionManager.KEY_ROLE, "Leader")
                .putString("ministryRole", "Worship Leader")
                .putString(SessionManager.KEY_MINISTRY_ROLE, "Worship Leader")
                .putLong("last_role_switch_time_$myUid", now)
                .putString("last_switched_role_$myUid", "Worship Leader")
            if (myUid.isNotBlank()) {
                editor.putString("user_role_$myUid", "Leader")
                editor.putString("user_ministry_$myUid", "Worship Leader")
            }
            if (emailLower.isNotBlank()) {
                editor.putString("email_role_$emailLower", "Leader")
                editor.putString("email_ministry_$emailLower", "Worship Leader")
                val sig = com.example.syncedn.data.util.ValidationUtils.computeSessionHmac(emailLower, "Leader", myUid)
                if (sig.isNotBlank()) editor.putString("session_hmac_sig_$emailLower", sig)
            }
            if (cleanTeam.isNotBlank() && emailLower.isNotBlank()) {
                editor.putString("team_leader_email_$cleanTeam", emailLower)
            }
            editor.commit()

            val lastNotifiedRole = preferences.getString("last_notified_role_$myUid", "").orEmpty()
            if (!isAuthOrOnboardingScreen) {
                isSwitchingToLeaderDashboard = true
                if (lastNotifiedRole != "Worship Leader") {
                    preferences.edit().putString("last_notified_role_$myUid", "Worship Leader").commit()
                    com.example.syncedn.data.auth.SyncedMessagingService.dispatchSystemNotification(
                        this@MemberActivity,
                        preferences,
                        "👑 Promoted to Worship Leader",
                        "You are now the Worship Leader of ${teamName.ifBlank { "your team" }}!"
                    )
                }
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        toast("You are now the Worship Leader of $teamName!")
                        val intent = Intent(this@MemberActivity, AdminActivity::class.java)
                            .putExtra("startPage", "home")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(intent)
                        finish()
                    }
                }
            }
            return
        }

        // 3. Ordinary Member Role
        val resolvedMinistry = if (remoteMinistryRole.isNotBlank() && !TeamRepository.isSubstituteLeader(remoteMinistryRole) && !remoteMinistryRole.equals("Worship Leader", ignoreCase = true)) {
            remoteMinistryRole
        } else {
            "Member"
        }
        val lastNotifiedRole = preferences.getString("last_notified_role_$myUid", "").orEmpty()
        val roleMeaningfullyChanged = lastNotifiedRole.isNotBlank() && !lastNotifiedRole.equals(resolvedMinistry, ignoreCase = true)
        val needsPrefSync = roleMeaningfullyChanged ||
            !currentPref.equals(resolvedMinistry, ignoreCase = true) ||
            !currentRolePref.equals("Member", ignoreCase = true)
        lastObservedMinistryRole = resolvedMinistry

        if (needsPrefSync) {
            val editor = preferences.edit()
                .putString("role", "Member")
                .putString(SessionManager.KEY_ROLE, "Member")
                .putString("ministryRole", resolvedMinistry)
                .putString(SessionManager.KEY_MINISTRY_ROLE, resolvedMinistry)
                .putLong("last_role_switch_time_$myUid", now)
                .putString("last_switched_role_$myUid", resolvedMinistry)
            if (myUid.isNotBlank()) {
                editor.putString("user_role_$myUid", "Member")
                editor.putString("user_ministry_$myUid", resolvedMinistry)
            }
            if (emailLower.isNotBlank()) {
                editor.putString("email_role_$emailLower", "Member")
                editor.putString("email_ministry_$emailLower", resolvedMinistry)
                val sig = com.example.syncedn.data.util.ValidationUtils.computeSessionHmac(emailLower, "Member", myUid)
                if (sig.isNotBlank()) editor.putString("session_hmac_sig_$emailLower", sig)
            }
            if (cleanTeam.isNotBlank() && preferences.getString("team_leader_email_$cleanTeam", "").equals(emailLower, ignoreCase = true)) {
                editor.remove("team_leader_email_$cleanTeam")
            }
            try {
                val map = org.json.JSONObject(preferences.getString(SessionManager.KEY_MEMBER_ROLES, "{}").orEmpty())
                if (myUid.isNotBlank()) map.put(myUid, resolvedMinistry)
                if (emailLower.isNotBlank()) map.put(emailLower, resolvedMinistry)
                editor.putString(SessionManager.KEY_MEMBER_ROLES, map.toString())
            } catch (_: Throwable) {}
            editor.commit()

            if (roleMeaningfullyChanged && !isAuthOrOnboardingScreen) {
                preferences.edit().putString("last_notified_role_$myUid", resolvedMinistry).commit()
                com.example.syncedn.data.auth.SyncedMessagingService.dispatchSystemNotification(
                    this@MemberActivity,
                    preferences,
                    "🔄 Ministry Role Updated",
                    "Your ministry role has been updated to $resolvedMinistry."
                )
            }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    if (roleMeaningfullyChanged && !isAuthOrOnboardingScreen) {
                        toast("Your ministry role has been updated to $resolvedMinistry.")
                    }
                    updateRosterViews()
                    if (page == "home") setupHome()
                    if (page == "teams" && teamTab in listOf("Role", "Overview")) {
                        navigate("teams", triggerRefresh = false)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ImageLoader.prewarmAppAssets(this)
        lastObservedMinistryRole = preferences.getString("ministryRole", "").orEmpty()
        val myCurrentUid = com.example.syncedn.data.auth.AuthManager.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: preferences.getString(SessionManager.KEY_USER_ID, "").orEmpty().ifBlank {
                preferences.getString("userId", "").orEmpty()
            }
        val myCurrentEmail = preferences.getString("email", "").orEmpty().ifBlank {
            com.example.syncedn.data.auth.AuthManager.currentUser?.email.orEmpty()
        }
        fun handleMyUserDocSnapshot(
            roleFromDoc: String,
            ministryRoleFromDoc: String,
            removedByLeader: Boolean,
            removedTeamCode: String
        ) {
            val myActiveTeamCode = preferences.getString(SessionManager.KEY_TEAM_CODE, "").orEmpty()
                .ifBlank { preferences.getString("teamCode", "").orEmpty() }
            val transferred = preferences.getBoolean("transferred_leadership_$myActiveTeamCode", false)
            val designatedLeader = preferences.getString("team_leader_email_$myActiveTeamCode", "").orEmpty().trim().lowercase()
            val isLeadUser = (designatedLeader.isNotBlank() && designatedLeader == myCurrentEmail) ||
                             (!transferred && designatedLeader.isBlank() && myCurrentEmail.equals("ryanlloydgenturo@gmail.com", ignoreCase = true) &&
                              !preferences.getString("role", "").equals("Member", ignoreCase = true))

            if (!isLeadUser && removedByLeader && myActiveTeamCode.isNotBlank() &&
                (removedTeamCode.isBlank() || removedTeamCode.equals(myActiveTeamCode, ignoreCase = true)) &&
                !isShowingRemovedDialog
            ) {
                isShowingRemovedDialog = true
                val emailLower = myCurrentEmail.trim().lowercase()
                val targetRemovedCode = removedTeamCode.ifBlank { myActiveTeamCode }
                com.example.syncedn.data.auth.SyncedFirebaseMessagingService.unsubscribeFromTeam(myActiveTeamCode)
                servicesListener?.remove(); servicesListener = null
                substitutionsListener?.remove(); substitutionsListener = null
                rosterListener?.remove(); rosterListener = null
                customRolesListener?.remove(); customRolesListener = null
                preferences.edit()
                    .putString("role", "")
                    .remove("teamName")
                    .remove("teamCode")
                    .remove(SessionManager.KEY_TEAM_NAME)
                    .remove(SessionManager.KEY_TEAM_CODE)
                    .remove("ministryRole")
                    .remove("email_team_$emailLower")
                    .remove("email_code_$emailLower")
                    .remove("user_team_$myCurrentUid")
                    .remove("user_code_$myCurrentUid")
                    .putBoolean("leftTeam", true)
                    .putBoolean("removedByLeader", false)
                    .remove("removedTeamCode")
                    .putString("pending_rejoin_team_code", targetRemovedCode)
                    .apply()
                teamName = ""
                com.example.syncedn.data.auth.SyncedMessagingService.startRealtimeTeamWatchers(this@MemberActivity, preferences)
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        if (page != "teamChoice") {
                            navigate("teamChoice", triggerRefresh = false)
                        }
                        AlertDialog.Builder(this@MemberActivity)
                            .setTitle("Removed from Team")
                            .setMessage("You have been removed from the team by the Team Leader.")
                            .setPositiveButton("OK") { _, _ ->
                                isShowingRemovedDialog = false
                                if (page != "teamChoice") navigate("teamChoice", triggerRefresh = false)
                            }
                            .setCancelable(false)
                            .show()
                    } else {
                        isShowingRemovedDialog = false
                    }
                }
                return
            }

            if (removedByLeader || myActiveTeamCode.isBlank()) return

            handleSelfRoleSync(
                remoteMinistryRole = ministryRoleFromDoc,
                remoteRole = roleFromDoc,
                myUid = myCurrentUid,
                myEmail = myCurrentEmail
            )
        }
        if (myCurrentUid.isNotBlank()) {
            userMembershipListener?.remove()
            userMembershipListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(myCurrentUid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    handleMyUserDocSnapshot(
                        roleFromDoc = snapshot.getString("role").orEmpty(),
                        ministryRoleFromDoc = snapshot.getString("ministryRole").orEmpty(),
                        removedByLeader = snapshot.getBoolean("removedByLeader") ?: false,
                        removedTeamCode = snapshot.getString("removedTeamCode").orEmpty()
                    )
                }
        }
        if (myCurrentEmail.isNotBlank()) {
            userEmailMembershipListener?.remove()
        teamDocListener?.remove()
            userEmailMembershipListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").whereEqualTo("email", myCurrentEmail)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || snapshot.isEmpty) return@addSnapshotListener
                    val myUidDoc = if (myCurrentUid.isNotBlank()) snapshot.documents.find { it.id == myCurrentUid } else null
                    if (myCurrentUid.isNotBlank() && myUidDoc != null) {
                        return@addSnapshotListener
                    }
                    val doc = snapshot.documents.firstOrNull { !it.id.startsWith("user_") } ?: snapshot.documents.firstOrNull() ?: return@addSnapshotListener
                    handleMyUserDocSnapshot(
                        roleFromDoc = doc.getString("role").orEmpty(),
                        ministryRoleFromDoc = doc.getString("ministryRole").orEmpty(),
                        removedByLeader = doc.getBoolean("removedByLeader") ?: false,
                        removedTeamCode = doc.getString("removedTeamCode").orEmpty()
                    )
                }
        }
        com.example.syncedn.data.auth.SyncedMessagingService.onRejoinApprovedInApp = { approvedCode ->
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    AlertDialog.Builder(this@MemberActivity)
                        .setTitle("Re-join Request Approved!")
                        .setMessage("Your request to re-join has been approved by the Team Leader! You can now enter the team code ($approvedCode) to join.")
                        .setPositiveButton("Enter Team Code") { _, _ ->
                            if (page != "joinTeam") navigate("joinTeam", triggerRefresh = false)
                        }
                        .setNegativeButton("Later", null)
                        .show()
                }
            }
        }
        val activeStartupCode = preferences.getString(SessionManager.KEY_TEAM_CODE, "").orEmpty()
            .ifBlank { preferences.getString("teamCode", "").orEmpty() }
        if (activeStartupCode.isNotBlank()) {
            startRealtimeTeamListeners(activeStartupCode)
        } else {
            com.example.syncedn.data.auth.SyncedMessagingService.startRealtimeTeamWatchers(this, preferences)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        syncLocalTeamsToMemory()
        checkAndRequestNotificationPermission()
        val currentMinistryOnStart = preferences.getString("ministryRole", "").orEmpty()
        val currentEmailOnStart = preferences.getString("email", "").orEmpty().trim().lowercase()
        val currentTeamName = preferences.getString("teamName", "").orEmpty().trim().lowercase()
        val currentTeamCode = preferences.getString("teamCode", "").orEmpty().trim().uppercase()
        val teamLeaderEmail = preferences.getString("team_leader_email_$currentTeamCode", "").orEmpty().trim().lowercase()
            .ifBlank { preferences.getString("team_leader_email_$currentTeamName", "").orEmpty().trim().lowercase() }
        val transferred = preferences.getBoolean("transferred_leadership_$currentTeamCode", false)
        val selfRole = preferences.getString("role", "").orEmpty()
        val isSelfExplicitMember = selfRole.equals("Member", ignoreCase = true) || currentMinistryOnStart.equals("Member", ignoreCase = true)

        val isPrimaryLeader = !isSelfExplicitMember && (
            (teamLeaderEmail.isNotBlank() && teamLeaderEmail == currentEmailOnStart) ||
            (teamLeaderEmail.isBlank() && !transferred && currentEmailOnStart == "ryanlloydgenturo@gmail.com") ||
            (currentMinistryOnStart.equals("Worship Leader", ignoreCase = true) && !transferred && (teamLeaderEmail.isBlank() || teamLeaderEmail == currentEmailOnStart))
        )
        val isSubLeader = !isSelfExplicitMember && currentMinistryOnStart.equals("Substitute Leader", ignoreCase = true)
        if (isPrimaryLeader || isSubLeader) {
            val intent = Intent(this, AdminActivity::class.java).putExtra("startPage", intent.getStringExtra("startPage") ?: "home")
            startActivity(intent)
            finish()
            return
        }
        displayName = savedInstanceState?.getString("displayName") ?: preferences.getString(SessionManager.KEY_USERNAME, "").orEmpty()
        fullName = preferences.getString(SessionManager.KEY_FULL_NAME, "").orEmpty()
        profileReturnPage = savedInstanceState?.getString("profileReturnPage") ?: "settings"
        teamName = savedInstanceState?.getString("teamName") ?: preferences.getString("teamName", "").orEmpty()
        successFlow = savedInstanceState?.getString("successFlow") ?: "register"
        savedInstanceState?.getString("selectedSheetService")?.let { value ->
            selectedSheetService = try { org.json.JSONObject(value) } catch(_:Exception) { null }
        }
        savedInstanceState?.let { state ->
            scheduleFilter = state.getString("scheduleFilter", "Upcoming") ?: "Upcoming"
            teamTab = state.getString("teamTab", "Team Chat") ?: "Team Chat"
            expandedRole = state.getString("expandedRole")
            selectedSong = state.getString("selectedSong", "Trust in God") ?: "Trust in God"
            songDetailsBack = state.getString("songDetailsBack", "songs") ?: "songs"
            selectedService = state.getString("selectedService", "Sunday Worship Service") ?: "Sunday Worship Service"
            serviceReturnPage = state.getString("serviceReturnPage", "schedule") ?: "schedule"
            calendarYear = state.getInt("calendarYear", 2026)
            calendarMonth = state.getInt("calendarMonth", 9)
            calendarDay = state.getInt("calendarDay", 4)
            substitute = state.getString("substitute", "John Doe") ?: "John Doe"
            songsReturnPage = state.getString("songsReturnPage", "home") ?: "home"
            state.getStringArrayList("lineupSongs")?.let { lineupSongs.clear(); lineupSongs.addAll(it) }
        }
        navigate(savedInstanceState?.getString("page") ?: intent.getStringExtra("startPage") ?: initialPage)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (page == "home") {
                    AlertDialog.Builder(this@MemberActivity)
                        .setTitle("Exit Synced N")
                        .setMessage("Are you sure you want to quit the app?")
                        .setPositiveButton("Exit") { _, _ -> finishAffinity() }
                        .setNegativeButton("Cancel", null)
                        .show()
                    return
                }
                val previous = when (page) {
                    "login" -> "start"
                    "register", "reset" -> "login"
                    "otp" -> if (successFlow == "register") "register" else "reset"
                    "success" -> "login"
                    "teamChoice" -> "login"
                    "createTeam", "joinTeam" -> "teamChoice"
                    "home" -> null
                    "settings" -> "home"
                    "profile" -> profileReturnPage
                    "schedule", "calendar", "announcements", "notifications", "teams" -> "home"
                    "availability" -> "calendar"
                    "service" -> serviceReturnPage
                    "substitution", "lineup" -> "service"
                    "songs" -> songsReturnPage
                    "songDetails" -> songDetailsBack
                    "teamDetails" -> "teams"
                    else -> null
                }
                if (previous != null) navigate(previous)
                else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })
    }

    override fun onDestroy() {
        servicesListener?.remove()
        servicesListener = null
        substitutionsListener?.remove()
        substitutionsListener = null
        rosterListener?.remove()
        rosterListener = null
        customRolesListener?.remove()
        customRolesListener = null
        com.example.syncedn.data.auth.SyncedMessagingService.onRejoinApprovedInApp = null
        super.onDestroy()
        chatListener?.remove()
        chatListener = null
        userMembershipListener?.remove()
        userMembershipListener = null
        userEmailMembershipListener?.remove()
        teamDocListener?.remove()
        userEmailMembershipListener = null
        teamDocListener = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("page", page)
        outState.putString("profileReturnPage", profileReturnPage)
        outState.putString("displayName", displayName)
        outState.putString("teamName", teamName)
        outState.putString("successFlow", successFlow)
        outState.putString("scheduleFilter", scheduleFilter)
        outState.putString("teamTab", teamTab)
        outState.putString("expandedRole", expandedRole)
        outState.putString("selectedSong", selectedSong)
        outState.putString("songDetailsBack", songDetailsBack)
        outState.putString("selectedSheetService", selectedSheetService?.toString())
        outState.putString("selectedService", selectedService)
        outState.putString("serviceReturnPage", serviceReturnPage)
        outState.putString("substitute", substitute)
        outState.putString("songsReturnPage", songsReturnPage)
        outState.putInt("calendarYear", calendarYear)
        outState.putInt("calendarMonth", calendarMonth)
        outState.putInt("calendarDay", calendarDay)
        outState.putStringArrayList("lineupSongs", ArrayList(lineupSongs))
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        chatListener?.remove()
        chatListener = null
        sheetAutoScroll?.pause()
        super.onPause()
    }

    override fun onStop() {
        songMetronome?.stop()
        super.onStop()
    }

    protected open fun navigate(next: String) {
        navigate(next, true)
    }

    protected open fun navigate(next: String, triggerRefresh: Boolean) {
        val userMinistry = preferences.getString("ministryRole", "").orEmpty()
        val userEmail = preferences.getString("email", "").orEmpty().trim().lowercase()
        val userRole = preferences.getString("role", "").orEmpty()
        val userTeamName = preferences.getString("teamName", "").orEmpty().trim().lowercase()
        val teamCode = preferences.getString("teamCode", "").orEmpty().trim().uppercase()
        val teamLeaderEmail = preferences.getString("team_leader_email_$teamCode", "").orEmpty().trim().lowercase()
            .ifBlank { preferences.getString("team_leader_email_$userTeamName", "").orEmpty().trim().lowercase() }
        val transferred = preferences.getBoolean("transferred_leadership_$teamCode", false)
        val isLeadUser = (teamLeaderEmail.isNotBlank() && teamLeaderEmail == userEmail) ||
                         (!transferred && teamLeaderEmail.isBlank() && userEmail == "ryanlloydgenturo@gmail.com" &&
                          !userRole.equals("Member", ignoreCase = true) && !userMinistry.equals("Member", ignoreCase = true))
        val myTeam = preferences.getString("teamName", "").orEmpty()
        val removedTeamCode = preferences.getString("removedTeamCode", "").orEmpty()
        val isRemovedUser = if (isLeadUser) false else (preferences.getBoolean("removedByLeader", false) && (removedTeamCode.isBlank() || teamCode.isBlank() || removedTeamCode.equals(teamCode, ignoreCase = true)))
        val effectiveNext = if (!isLeadUser && (isRemovedUser || myTeam.isBlank()) && next in listOf("home", "schedule", "teams", "announcements", "calendar", "songs", "service")) {
            "teamChoice"
        } else {
            next
        }
        val isSelfExplicitMember = userRole.equals("Member", ignoreCase = true) || userMinistry.equals("Member", ignoreCase = true)
        val isSubLeader = !isSelfExplicitMember && userMinistry.equals("Substitute Leader", ignoreCase = true)
        val isPrimaryLeader = !isSelfExplicitMember && (
            (teamLeaderEmail.isNotBlank() && teamLeaderEmail == userEmail) ||
            (!isSubLeader && !transferred && teamLeaderEmail.isBlank() && userEmail == "ryanlloydgenturo@gmail.com") ||
            (userMinistry.equals("Worship Leader", ignoreCase = true) && !transferred && (teamLeaderEmail.isBlank() || teamLeaderEmail == userEmail))
        )
        if (effectiveNext in listOf("home", "schedule", "teams", "announcements", "calendar", "songs", "service")) {
            if (isPrimaryLeader || isSubLeader) {
                startActivity(Intent(this, AdminActivity::class.java).putExtra("startPage", effectiveNext))
                finish()
                return
            }
        }
        if (next in listOf("start", "login") && javaClass == MemberActivity::class.java) {
            startActivity(Intent(this, MainActivity::class.java).putExtra("startPage", next))
            finish()
            return
        }
        sheetAutoScroll?.close()
        sheetAutoScroll = null
        songMetronome?.close()
        songMetronome = null
        page = effectiveNext
        homeMemberCount = null
        val root = buildScreen(page)
        screenRoot = root
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(root)
        when (page) {
            "start" -> {
                SessionManager.clearPendingAuthCredentials(preferences)
                click(UI.getStartedButton) {
                    val indicator = findViewById<View>(9999)
                    indicator?.visibility = View.VISIBLE
                    indicator?.postDelayed({ navigate("login") }, 400) ?: navigate("login")
                }
            }
            "login" -> { SessionManager.clearPendingAuthCredentials(preferences); setupLogin() }
            "register" -> setupRegister()
            "reset" -> setupReset()
            "otp" -> setupOtp()
            "success" -> setupSuccess()
            "teamChoice" -> {
                click(UI.createTeamChoiceButton) { navigate("createTeam") }
                click(UI.joinTeamChoiceButton) { navigate("joinTeam") }
            }
            "createTeam" -> click(UI.createTeamButton) {
                if (!com.example.syncedn.data.util.ValidationUtils.isNetworkAvailable(this)) {
                    AlertDialog.Builder(this)
                        .setTitle("No Internet Connection")
                        .setMessage("You appear to be offline. Creating a team requires an active internet connection to generate and sync team records.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@click
                }
                val name = input(UI.teamName)
                if (name.text.isBlank()) invalid(name, "Enter a team name.")
                else {
                    val enteredTeamName = name.text.toString().trim()
                    val createBtn = findViewById<Button>(UI.createTeamButton)
                    val uid = com.example.syncedn.data.auth.AuthManager.currentUser?.uid?.takeIf { it.isNotBlank() }
                        ?: preferences.getString("userId", "").orEmpty().takeIf { it.isNotBlank() && !it.startsWith("user_") }
                        ?: preferences.getString("userId", "").orEmpty().ifBlank { "user_${displayName.hashCode()}" }
                    val emailStr = preferences.getString("email", "").orEmpty()
                        .ifBlank { com.example.syncedn.data.auth.AuthManager.currentUser?.email.orEmpty() }
                    preferences.edit {
                        putString("userId", uid)
                        putString("email", emailStr)
                    }
                    FirestoreManager.createTeamWithCode(uid, displayName, enteredTeamName) { result ->
                        runOnUiThread {
                            createBtn?.isEnabled = true
                            if (result.isSuccess) {
                                val generatedCode = result.getOrNull().orEmpty()
                                teamName = enteredTeamName
                                saveUserTeamState("Leader", enteredTeamName, generatedCode)
                                FirestoreManager.updateUserRoleAndTeam(uid, "Leader", enteredTeamName, generatedCode, emailStr)
                                val leaderName = fullName.ifBlank { displayName }
                                val leaderMember = com.example.syncedn.data.model.TeamMember(
                                    uid = uid,
                                    fullName = leaderName,
                                    username = displayName,
                                    email = emailStr,
                                    role = "Leader",
                                    ministryRole = preferences.getString("ministryRole", "Worship Leader").orEmpty().ifBlank { "Worship Leader" },
                                    teamCode = generatedCode
                                )
                                TeamRepository.cacheMember(leaderMember, preferences)
                                try {
                                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(
                                        android.content.ClipData.newPlainText("Team Invite Code", generatedCode)
                                    )
                                } catch (_: Exception) {}
                                toast("Team created! Invite Code: $generatedCode")
                                startActivity(
                                    Intent(this@MemberActivity, AdminActivity::class.java)
                                        .putExtra("startPage", "home")
                                        .putExtra("newTeamCode", generatedCode)
                                )
                                finish()
                            } else {
                                val err = result.exceptionOrNull()?.localizedMessage ?: "Failed to create team."
                                invalid(name, err)
                                toast(err)
                            }
                        }
                    }
                }
            }
            "joinTeam" -> click(UI.joinTeamButton) {
                if (!com.example.syncedn.data.util.ValidationUtils.isNetworkAvailable(this)) {
                    AlertDialog.Builder(this)
                        .setTitle("No Internet Connection")
                        .setMessage("You appear to be offline. Joining a team requires an active internet connection to verify team invite codes.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@click
                }
                val code = input(UI.teamCode)
                val enteredCode = code.text.toString().trim().uppercase()
                if (enteredCode.isBlank()) {
                    invalid(code, "Enter a team code.")
                } else if (!FirestoreManager.isValidTeamCodeFormat(enteredCode)) {
                    invalid(code, "Enter a valid 6-character team code.")
                } else {
                    val joinBtn = findViewById<Button>(UI.joinTeamButton)
                    joinBtn?.isEnabled = false
                    syncLocalTeamsToMemory()
                    val uid = preferences.getString("userId", "").orEmpty()
                        .ifBlank { com.example.syncedn.data.auth.AuthManager.currentUser?.uid.orEmpty() }
                        .ifBlank { "user_${displayName.hashCode()}" }
                    val emailStr = preferences.getString("email", "").orEmpty()
                        .ifBlank { com.example.syncedn.data.auth.AuthManager.currentUser?.email.orEmpty() }

                    TeamRepository.isMemberRemovedFromTeam(enteredCode, uid, emailStr, preferences) { isRemoved ->
                        runOnUiThread {
                            if (isRemoved) {
                                joinBtn?.isEnabled = true
                                AlertDialog.Builder(this@MemberActivity)
                                    .setTitle("Re-join Approval Required")
                                    .setMessage("You were previously removed from this team by the Team Leader. Would you like to send a re-join request to the Team Leader for approval?")
                                    .setPositiveButton("Send Request") { _, _ ->
                                        val reqId = "rejoin_${System.currentTimeMillis()}_${uid.take(6)}"
                                        val req = com.example.syncedn.data.model.RejoinRequestData(
                                            id = reqId,
                                            teamCode = enteredCode,
                                            userUid = uid,
                                            userName = fullName.ifBlank { displayName },
                                            userEmail = emailStr,
                                            reason = "Member requested to re-join the team",
                                            status = "Pending"
                                        )
                                        preferences.edit().putString("pending_rejoin_team_code", enteredCode).apply()
                                        com.example.syncedn.data.auth.SyncedMessagingService.startRealtimeTeamWatchers(this@MemberActivity, preferences)
                                        TeamRepository.sendRejoinRequest(enteredCode, req) { sRes ->
                                            runOnUiThread {
                                                if (sRes.isSuccess) {
                                                    toast("Re-join request sent to Team Leader!")
                                                    AlertDialog.Builder(this@MemberActivity)
                                                        .setTitle("Request Submitted")
                                                        .setMessage("Your request to re-join has been sent to the Team Leader. You will receive a notification as soon as the Leader approves your request.")
                                                        .setPositiveButton("OK", null)
                                                        .show()
                                                } else {
                                                    toast("Failed to send request: ${sRes.exceptionOrNull()?.message}")
                                                }
                                            }
                                        }
                                    }
                                    .setNegativeButton("Cancel", null)
                                    .show()
                                return@runOnUiThread
                            }

                            FirestoreManager.getTeamByCode(enteredCode) { result ->
                                runOnUiThread {
                                    joinBtn?.isEnabled = true
                                    if (result.isSuccess) {
                                        val (validCode, foundTeamName) = result.getOrNull()!!
                                        teamName = foundTeamName
                                        val curRem = preferences.getStringSet("team_removed_members_$validCode", null)?.toMutableSet()
                                        if (curRem != null) {
                                            curRem.remove(uid)
                                            curRem.remove(emailStr)
                                            curRem.remove(displayName)
                                            curRem.remove(fullName)
                                        }
                                        val genRem = preferences.getStringSet("removedMembers", null)?.toMutableSet()
                                        if (genRem != null) {
                                            genRem.remove(uid)
                                            genRem.remove(emailStr)
                                            genRem.remove(displayName)
                                            genRem.remove(fullName)
                                        }
                                        preferences.edit {
                                            putString("userId", uid)
                                            putString("email", emailStr)
                                            putBoolean("leftTeam", false)
                                            putBoolean("removedByLeader", false)
                                            remove("removedTeamCode")
                                            if (emailStr.isNotBlank()) {
                                                putBoolean("email_removed_by_leader_$emailStr", false)
                                                remove("email_removed_team_code_$emailStr")
                                            }
                                            if (uid.isNotBlank()) {
                                                putBoolean("user_removed_by_leader_$uid", false)
                                                remove("user_removed_team_code_$uid")
                                            }
                                            if (curRem != null) putStringSet("team_removed_members_$validCode", curRem)
                                            if (genRem != null) putStringSet("removedMembers", genRem)
                                        }
                                        val cleanCode = validCode.trim().uppercase()
                                        val teamLeaderUid = FirestoreManager.getLocalTeamLeaderUid(cleanCode).orEmpty().ifBlank { preferences.getString("team_leader_uid_$cleanCode", "").orEmpty() }
                                        val designatedLeader = preferences.getString("team_leader_email_$cleanCode", "").orEmpty().trim().lowercase()
                                        val transferred = preferences.getBoolean("transferred_leadership_$cleanCode", false)
                                        val isDesignatedTeamLeader = (teamLeaderUid.isNotBlank() && uid == teamLeaderUid) ||
                                                                     (designatedLeader.isNotBlank() && designatedLeader == emailStr.trim().lowercase()) ||
                                                                     (!transferred && teamLeaderUid.isBlank() && designatedLeader.isBlank() && emailStr.equals("ryanlloydgenturo@gmail.com", ignoreCase = true) && !preferences.getString("role", "").equals("Member", ignoreCase = true))
                                        val resolvedRole = if (isDesignatedTeamLeader) "Leader" else "Member"
                                        val resolvedMinistry = if (isDesignatedTeamLeader) "Worship Leader" else "Member"
                                        preferences.edit(commit = true) {
                                            putString("role", resolvedRole)
                                            putString("ministryRole", resolvedMinistry)
                                            putString(SessionManager.KEY_ROLE, resolvedRole)
                                            putString(SessionManager.KEY_MINISTRY_ROLE, resolvedMinistry)
                                            if (uid.isNotBlank()) {
                                                putString("user_role_$uid", resolvedRole)
                                                putString("user_ministry_$uid", resolvedMinistry)
                                            }
                                            if (emailStr.isNotBlank()) {
                                                putString("email_role_$emailStr", resolvedRole)
                                                putString("email_ministry_$emailStr", resolvedMinistry)
                                            }
                                        }
                                        saveUserTeamState(resolvedRole, foundTeamName, validCode)
                                        FirestoreManager.updateUserRoleAndTeam(uid, resolvedRole, foundTeamName, validCode, emailStr)
                                        val memberName = fullName.ifBlank { displayName }
                                        val newMember = com.example.syncedn.data.model.TeamMember(
                                            uid = uid,
                                            fullName = memberName,
                                            username = displayName,
                                            email = emailStr,
                                            role = resolvedRole,
                                            ministryRole = resolvedMinistry,
                                            teamCode = validCode
                                        )
                                        TeamRepository.cacheMember(newMember, preferences)
                                        toast("Joined team: $foundTeamName")
                                        if (resolvedRole == "Leader") {
                                            startActivity(Intent(this@MemberActivity, AdminActivity::class.java).putExtra("startPage", "home"))
                                            finish()
                                        } else if (javaClass == MainActivity::class.java) {
                                            startActivity(Intent(this@MemberActivity, MemberActivity::class.java).putExtra("startPage", "home"))
                                            finish()
                                        } else {
                                            navigate("home")
                                        }
                                    } else {
                                        val err = result.exceptionOrNull()?.localizedMessage ?: "Invalid team code. Please check with your Team Leader."
                                        invalid(code, err)
                                        toast(err)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "home" -> {
                setupHome()
                if (triggerRefresh) {
                    root.post {
                        if (!isFinishing && !isDestroyed) {
                            refreshTeamRoster()
                            refreshTeamServices()
                            val activeCode = preferences.getString("teamCode", "").orEmpty()
                                .ifBlank { FirestoreManager.findCodeByTeamName(teamName).orEmpty() }
                            if (activeCode.isNotBlank()) {
                                TeamRepository.getAnnouncements(activeCode, false, preferences)
                            }
                        }
                    }
                }
            }
            "schedule" -> {
                if (triggerRefresh) {
                    root.post {
                        if (!isFinishing && !isDestroyed) refreshTeamServices()
                    }
                }
            }
            "teams" -> if (triggerRefresh) {
                root.post {
                    if (!isFinishing && !isDestroyed) refreshTeamRoster()
                }
            }
            "calendar" -> {
                if (triggerRefresh) {
                    root.post {
                        if (!isFinishing && !isDestroyed) {
                            refreshTeamRoster(force = true)
                            refreshTeamServices()
                        }
                    }
                }
            }
            "settings" -> setupSettings()
            "profile" -> setupProfile()
        }
    }

    private fun click(id: Int, action: () -> Unit) {
        findViewById<View>(id).setOnClickListener { action() }
    }
    private fun input(id: Int): EditText = findViewById(id)
    private fun invalid(field: EditText, message: String) {
        field.error = message
        field.requestFocus()
    }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun passwordToggle(fieldId: Int, buttonId: Int) {
        var visible = false
        click(buttonId) {
            visible = !visible
            val field = input(fieldId)
            field.transformationMethod = if (visible)
                HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
            field.setSelection(field.text.length)
            findViewById<ImageButton>(buttonId).contentDescription =
                if (visible) "Hide password" else "Show password"
        }
    }

    private fun setupLogin() {
        click(UI.openRegisterButton) { navigate("register") }
        click(UI.forgotPasswordButton) { navigate("reset") }
        passwordToggle(UI.loginPassword, UI.loginPasswordToggle)
        click(UI.googleButton) {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(AuthManager.WEB_CLIENT_ID)
                .requestEmail()
                .build()
            val client = GoogleSignIn.getClient(this, gso)
            client.signOut().addOnCompleteListener {
                googleSignInLauncher.launch(client.signInIntent)
            }
        }
        click(UI.loginButton) {
            val email = input(UI.loginEmail)
            val password = input(UI.loginPassword)
            val emailStr = email.text.toString().trim()
            val passStr = password.text.toString()
            val emailErr = com.example.syncedn.data.util.ValidationUtils.validateEmailForAuth(emailStr)
            when {
                emailErr != null -> invalid(email, emailErr)
                com.example.syncedn.data.util.ValidationUtils.isLoginLocked(emailStr) -> {
                    val waitSec = com.example.syncedn.data.util.ValidationUtils.getLoginLockRemainingSeconds(emailStr)
                    val msg = "Too many failed attempts. Please wait ${waitSec}s before trying again."
                    invalid(password, msg)
                    toast(msg)
                }
                passStr.length < 6 -> invalid(password, "Enter your password (at least 6 characters).")
                else -> {
                    val loginBtn = findViewById<Button>(UI.loginButton)
                    loginBtn?.isEnabled = false
                    toast("Signing in...")
                    AuthManager.loginUser(emailStr, passStr) { result ->
                        runOnUiThread {
                            loginBtn?.isEnabled = true
                            if (result.isSuccess) {
                                com.example.syncedn.data.util.ValidationUtils.resetLoginAttempts(emailStr)
                                val profile = result.getOrNull()
                                val uid = com.example.syncedn.data.auth.AuthManager.currentUser?.uid?.takeIf { it.isNotBlank() }
                                    ?: profile?.uid.orEmpty()
                                displayName = profile?.username?.ifBlank { emailStr.substringBefore("@") } ?: "Member"
                                SessionManager.clearPreferencesOnly(preferences)
                                val (userRole, userTeam, userTeamCode) = resolveUserTeamSession(profile, emailStr)
                                teamName = userTeam
                                SessionManager.saveSessionProfile(preferences, profile, displayName, emailStr)
                                fullName = preferences.getString(SessionManager.KEY_FULL_NAME, "").orEmpty()
                                displayName = preferences.getString(SessionManager.KEY_USERNAME, displayName).orEmpty()
                                val isRemoved = profile?.removedByLeader == true && (profile.removedTeamCode.isBlank() || profile.removedTeamCode == userTeamCode)
                                if (userTeam.isNotBlank() && !isRemoved) {
                                    saveUserTeamState(userRole, userTeam, userTeamCode)
                                    FirestoreManager.updateUserRoleAndTeam(uid, userRole, userTeam, userTeamCode, emailStr)
                                } else {
                                    preferences.edit {
                                        putString("role", "Member")
                                        putString("teamName", "")
                                        putString("teamCode", "")
                                        putString("ministryRole", "")
                                        putBoolean("removedByLeader", isRemoved)
                                        if (isRemoved) {
                                            val rCode = profile?.removedTeamCode?.ifBlank { userTeamCode }.orEmpty()
                                            if (rCode.isNotBlank()) putString("removedTeamCode", rCode)
                                        }
                                    }
                                }
                                toast("Welcome, $displayName!")
                                if (userTeam.isBlank() || isRemoved) {
                                    navigate("teamChoice")
                                } else if (userRole.equals("Leader", ignoreCase = true)) {
                                    startActivity(Intent(this@MemberActivity, AdminActivity::class.java).putExtra("startPage", "home"))
                                    finish()
                                } else {
                                    if (javaClass == MainActivity::class.java) {
                                        startActivity(Intent(this@MemberActivity, MemberActivity::class.java).putExtra("startPage", "home"))
                                        finish()
                                    } else {
                                        navigate("home")
                                    }
                                }
                            } else {
                                val rem = com.example.syncedn.data.util.ValidationUtils.recordLoginFailure(emailStr)
                                val baseMsg = result.exceptionOrNull()?.localizedMessage ?: "Login failed. Check your email and password."
                                val fullMsg = if (rem == 0) {
                                    "Too many failed login attempts. Account locked for 60 seconds."
                                } else if (rem <= 2) {
                                    "$baseMsg ($rem attempts remaining)"
                                } else {
                                    baseMsg
                                }
                                toast(fullMsg)
                                if (baseMsg.contains("No registered account", ignoreCase = true) || baseMsg.contains("No account found", ignoreCase = true)) {
                                    invalid(email, baseMsg)
                                } else {
                                    invalid(password, fullMsg)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupRegister() {
        passwordToggle(UI.registerPassword, UI.registerPasswordToggle)
        passwordToggle(UI.registerConfirm, UI.registerConfirmToggle)
        click(UI.registerButton) {
            val name = input(UI.registerName)
            val email = input(UI.registerEmail)
            val password = input(UI.registerPassword)
            val confirm = input(UI.registerConfirm)
            val nameStr = name.text.toString().trim()
            val emailStr = email.text.toString().trim()
            val passStr = password.text.toString()
            val emailErr = com.example.syncedn.data.util.ValidationUtils.validateEmailForAuth(emailStr)
            val passEval = com.example.syncedn.data.util.ValidationUtils.evaluatePassword(passStr)
            when {
                !com.example.syncedn.data.util.ValidationUtils.isValidUsername(nameStr) ->
                    invalid(name, "Enter a valid username (1- characters).")
                emailErr != null -> invalid(email, emailErr)
                !passEval.isStrong -> invalid(password, passEval.errorMessage() ?: "Use a stronger password.")
                passStr != confirm.text.toString() -> invalid(confirm, "Passwords do not match.")
                !com.example.syncedn.data.util.ValidationUtils.isNetworkAvailable(this) ->
                    toast("No internet connection. Please connect to the internet to verify your email.")
                else -> {
                    val regBtn = findViewById<Button>(UI.registerButton)
                    regBtn?.isEnabled = false
                    toast("Verifying email availability...")
                    FirestoreManager.checkEmailExistsInFirestore(emailStr, preferences) { alreadyExists ->
                        runOnUiThread {
                            if (alreadyExists) {
                                regBtn?.isEnabled = true
                                val msg = "This email is already registered. Please log in instead."
                                invalid(email, msg)
                                toast(msg)
                            } else {
                                toast("Sending 6-digit verification code to $emailStr...")
                                preferences.edit()
                                    .putString("pendingRegUsername", nameStr)
                                    .putString("pendingRegEmail", emailStr)
                                    .putString("pendingRegPassword", passStr)
                                    .putString("resetEmail", emailStr)
                                    .putString("resetPendingPassword", passStr)
                                    .apply()
                                EmailOtpService.sendOtp(
                                    recipientEmail = emailStr,
                                    pendingPassword = passStr,
                                    pendingUsername = nameStr,
                                    purpose = "register"
                                ) { otpResult ->
                                    runOnUiThread {
                                        regBtn?.isEnabled = true
                                        if (otpResult.isSuccess) {
                                            successFlow = "register"
                                            toast("Verification code sent to $emailStr! Please check your inbox.")
                                            navigate("otp")
                                        } else {
                                            val errorMsg = otpResult.exceptionOrNull()?.localizedMessage ?: "Failed to send verification code."
                                            toast(errorMsg)
                                            invalid(email, "Could not send verification code to this email.")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupReset() {
        passwordToggle(UI.resetPassword, UI.resetPasswordToggle)
        passwordToggle(UI.resetConfirm, UI.resetConfirmToggle)
        click(UI.sendCodeButton) {
            val email = input(UI.resetEmail)
            val newPass = input(UI.resetPassword)
            val confirmPass = input(UI.resetConfirm)
            val emailStr = email.text.toString().trim()
            val passStr = newPass.text.toString()
            val confirmStr = confirmPass.text.toString()
            val emailErr = com.example.syncedn.data.util.ValidationUtils.validateEmailForAuth(emailStr)
            val passEval = com.example.syncedn.data.util.ValidationUtils.evaluatePassword(passStr)

            when {
                emailErr != null -> invalid(email, emailErr)
                !passEval.isStrong -> invalid(newPass, passEval.errorMessage() ?: "Use a stronger password.")
                passStr != confirmStr -> invalid(confirmPass, "Passwords do not match.")
                !com.example.syncedn.data.util.ValidationUtils.isNetworkAvailable(this) ->
                    toast("No internet connection. Please connect to the internet to reset your password.")
                else -> {
                    val sendBtn = findViewById<Button>(UI.sendCodeButton)
                    sendBtn?.isEnabled = false
                    toast("Checking account for $emailStr...")
                    FirestoreManager.checkEmailExistsInFirestore(emailStr, preferences) { exists ->
                        runOnUiThread {
                            if (!exists) {
                                sendBtn?.isEnabled = true
                                val msg = "No registered account found with this email address."
                                invalid(email, msg)
                                toast(msg)
                            } else {
                                toast("Sending 6-digit code to $emailStr...")
                                preferences.edit().putString("resetEmail", emailStr).putString("resetPendingPassword", passStr).apply()
                                EmailOtpService.sendOtp(
                                    recipientEmail = emailStr,
                                    pendingPassword = passStr,
                                    purpose = "reset"
                                ) { result ->
                                    runOnUiThread {
                                        sendBtn?.isEnabled = true
                                        if (result.isSuccess) {
                                            toast("Verification code sent! Check your email inbox.")
                                            successFlow = "reset"
                                            navigate("otp")
                                        } else {
                                            val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Failed to send verification code."
                                            toast(errorMsg)
                                            invalid(email, "Failed to send code.")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupOtp() {
        click(UI.verifyOtpButton) {
            val codeField = input(UI.otpCode)
            val codeStr = codeField.text.toString().trim()
            if (codeStr.length < 6) {
                invalid(codeField, "Enter the full 6-digit verification code.")
                return@click
            }

            val verifyBtn = findViewById<Button>(UI.verifyOtpButton)
            verifyBtn?.isEnabled = false
            val result = EmailOtpService.verifyOtp(codeStr)
            if (result.isSuccess) {
                val record = result.getOrNull()
                val isRegFlow = successFlow == "register" || record?.purpose.equals("register", ignoreCase = true)
                if (isRegFlow) {
                    val nameStr = record?.pendingUsername?.takeIf { it.isNotBlank() }
                        ?: preferences.getString("pendingRegUsername", "").orEmpty()
                    val emailStr = record?.email?.takeIf { it.isNotBlank() }
                        ?: preferences.getString("pendingRegEmail", "").orEmpty()
                    val passStr = record?.pendingPassword?.takeIf { it.isNotBlank() }
                        ?: preferences.getString("pendingRegPassword", "").orEmpty()
                    toast("Email verified! Creating your account...")
                    AuthManager.registerUser(nameStr, emailStr, passStr) { regResult ->
                        runOnUiThread {
                            verifyBtn?.isEnabled = true
                            if (regResult.isSuccess) {
                                val registeredProfile = regResult.getOrNull()
                                displayName = nameStr
                                fullName = ""
                                SessionManager.clearPreferencesOnly(preferences)
                                SessionManager.saveSessionProfile(preferences, registeredProfile, displayName, emailStr)
                                preferences.edit {
                                    putString("role", "")
                                    putString("teamName", "")
                                    putString("teamCode", "")
                                    remove("pendingRegUsername")
                                    remove("pendingRegEmail")
                                    remove("pendingRegPassword")
                                }
                                successFlow = "register"
                                toast("Account created successfully!")
                                navigate("success")
                            } else {
                                val errorMsg = regResult.exceptionOrNull()?.localizedMessage ?: "Registration failed."
                                toast(errorMsg)
                                navigate("register")
                            }
                        }
                    }
                } else {
                    verifyBtn?.isEnabled = true
                    if (record != null && record.pendingPassword.isNotEmpty()) {
                        FirestoreManager.savePasswordReset(record.email, record.pendingPassword)
                    }
                    toast("Verification successful! Password reset.")
                    successFlow = "reset"
                    navigate("success")
                }
            } else {
                verifyBtn?.isEnabled = true
                val err = result.exceptionOrNull()?.localizedMessage ?: "Invalid or expired verification code."
                invalid(codeField, err)
                toast(err)
            }
        }
    }
    private fun setupSuccess() {
        findViewById<TextView>(UI.successMessage).text =
            if (successFlow == "reset") "Password has been successfully reset! You can now log in." else "Your account has been created!"
        click(UI.continueButton) {
            navigate(if (successFlow == "reset") "login" else "teamChoice")
        }
    }

    private fun setupHome() {
        findViewById<TextView>(UI.welcomeText)?.text = displayName.ifBlank { "Member" }
        click(UI.notificationsButton) { navigate("notifications") }
        applyProfilePhoto(UI.profileButton)
        click(UI.profileButton) { openProfile("home") }
        click(UI.worshipCard) {
            openHomeUpcoming()
        }
        findViewById<TextView>(UI.teamTitle)?.apply {
            text = teamName.ifBlank { "Worship\nTeam" }
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        click(UI.teamHubButton) { navigate("teams") }
        click(UI.announcementsButton) { navigate("announcements") }
        click(UI.calendarButton) { navigate("calendar") }
        click(UI.songsButton) { selectedSheetService = null; songsReturnPage = "home"; navigate("songs") }
        click(UI.scheduleCard) { openHomeSecondUpcoming() }
        click(UI.homeTab) { /* already on Home */ }
        click(UI.scheduleTab) { schedule() }
        click(UI.teamsTab) { navigate("teams") }
        click(UI.settingsTab) { navigate("settings") }
    }

    private fun schedule() = navigate("schedule")
    private fun detail(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("Close", null).show()
    }

    // ---- EDITABLE UI: change text, sizes, colors and layout spacing here. ----
    private val purple = Color.rgb(80, 5, 104)
    private val navy = Color.rgb(16, 17, 63)
    private val bitmaps = mutableMapOf<String, Bitmap>()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun bg(color: Int, radius: Int = 0, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        stroke?.let { setStroke(dp(1), it) }
    }
    private fun vertical() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren=false; clipToPadding=false }
    private fun horizontal() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren=false; clipToPadding=false }
    private fun LinearLayout.add(view: View, width: Int = -1, height: Int = -2, weight: Float = 0f) {
        addView(view, LinearLayout.LayoutParams(if (width >= 0) dp(width) else width,
            if (height >= 0) dp(height) else height, weight))
    }
    private fun LinearLayout.space(height: Int) { add(View(this@MemberActivity), 1, height) }
    private fun text(value: String, size: Int = 14, color: Int = Color.BLACK, bold: Boolean = false,
                     centered: Boolean = false, id: Int = View.NO_ID) = TextView(this).apply {
        this.id = id; this.text = value; textSize = size.toFloat(); setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        includeFontPadding = false
        gravity = if (centered) Gravity.CENTER else Gravity.START or Gravity.CENTER_VERTICAL
    }
    private fun heading(value: String) = text(value, 21, navy, true)
    private fun image(name: String, description: String? = null) = ImageView(this).apply {
        val bitmap = if (name == "profile_photo") {
            val uri = preferences.getString("photoUri", null)?.takeIf { it.isNotBlank() }
            val cached = if (uri != null) ImageLoader.getCachedProfileBitmap(this@MemberActivity, uri) else null
            cached ?: bitmaps.getOrPut(name) {
                ImageLoader.getAppAssetBitmap(this@MemberActivity, name) {
                    val bytes = Base64.decode(Art.data(name), Base64.DEFAULT)
                    requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                }
            }
        } else {
            bitmaps.getOrPut(name) {
                ImageLoader.getAppAssetBitmap(this@MemberActivity, name) {
                    val bytes = Base64.decode(Art.data(name), Base64.DEFAULT)
                    requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                }
            }
        }
        setImageBitmap(bitmap); scaleType = ImageView.ScaleType.FIT_CENTER
        if (name == "profile_photo") {
            // Transparent supplied avatar: no background, border or inset ring.
            background = null
            setPadding(0, 0, 0, 0)
            elevation = 0f
            (drawable as? android.graphics.drawable.BitmapDrawable)?.paint?.apply {
                isFilterBitmap = true
                isAntiAlias = true
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            clipToOutline = true
        }
        contentDescription = description
        if (description == null) importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private fun button(id: Int, label: String, width: Int = 180, link: Boolean = false): View =
        if (link) text(label, 14, Color.rgb(0,103,96), centered = true, id = id).apply {
            isClickable = true; isFocusable = true
            layoutParams = LinearLayout.LayoutParams(if (width < 0) width else dp(width), dp(48)).apply { gravity = Gravity.CENTER_HORIZONTAL }
        } else Button(this).apply {
            this.id = id; text = label; textSize = 14f; isAllCaps = false
            setTextColor(Color.WHITE); background = bg(purple, 24)
            minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
            stateListAnimator = null; setPadding(0,0,0,0)
            layoutParams = LinearLayout.LayoutParams(if (width < 0) width else dp(width), dp(40)).apply { gravity = Gravity.CENTER_HORIZONTAL }
        }
    private fun LinearLayout.action(id: Int, label: String, width: Int = 180, link: Boolean = false) {
        addView(button(id,label,width,link))
    }
    private fun field(id: Int, label: String = "", hint: String = "", password: Boolean = false,
                      email: Boolean = false, number: Boolean = false, icon: String? = null,
                      toggle: Int = View.NO_ID): LinearLayout {
        val container = vertical()
        if (label.isNotBlank()) container.add(text(label).apply { setPadding(dp(22),0,0,dp(5)) })
        val row = horizontal().apply { background = bg(Color.rgb(217,217,217),10); setPadding(dp(12),0,dp(4),0) }
        if (icon != null) row.add(Symbol(this,icon,Color.GRAY),24,24)
        val edit = EditText(this).apply {
            this.id = id; this.hint = hint; textSize = 14f; setTextColor(Color.BLACK)
            setHintTextColor(Color.rgb(115,115,115)); background = null; setPadding(dp(6),0,dp(6),0)
            inputType = when {
                password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                email -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                number -> InputType.TYPE_CLASS_NUMBER
                else -> InputType.TYPE_CLASS_TEXT
            }
            setSingleLine(true); isSaveEnabled = false
            if (android.os.Build.VERSION.SDK_INT >= 26) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        row.add(edit,0,48,1f)
        if (password) {
            val eye = ImageButton(this).apply {
                this.id = toggle; background = null; contentDescription = "Show password"
                setImageDrawable(EyeDrawable()); setPadding(dp(12),dp(12),dp(12),dp(12))
            }
            row.add(eye,48,48)
        }
        container.add(row,-1,48)
        return container
    }
    private fun passwordStrengthBox(passwordFieldId: Int): View {
        val mutedText = Color.rgb(100, 116, 139)
        val okColor = Color.rgb(22, 163, 74)
        val card = vertical().apply {
            background = GradientDrawable().apply {
                setColor(Color.rgb(248, 250, 252))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.rgb(226, 232, 240))
            }
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val topRow = horizontal()
        val titleTv = text("Password Requirements", 11, Color.rgb(51, 65, 85), bold = true)
        val badgeTv = text("Min. 8 chars", 11, mutedText, bold = true)
        topRow.add(titleTv, 0, -2, 1f)
        topRow.add(badgeTv, -2, -2)
        card.add(topRow)
        card.space(5)

        val barsRow = horizontal()
        val barViews = List(4) { idx ->
            View(this).apply {
                background = GradientDrawable().apply {
                    setColor(Color.rgb(226, 232, 240))
                    cornerRadius = dp(2).toFloat()
                }
            }.also { v ->
                val lp = LinearLayout.LayoutParams(0, dp(4), 1f).apply {
                    if (idx < 3) rightMargin = dp(4)
                }
                barsRow.addView(v, lp)
            }
        }
        card.add(barsRow, -1, -2)
        card.space(6)

        val reqLen = text("○ 8+ characters", 11, mutedText)
        val reqCase = text("○ Upper & lower (Aa)", 11, mutedText)
        val reqNum = text("○ Number (0-9)", 11, mutedText)
        val reqSpec = text("○ Special (!@#$)", 11, mutedText)

        val row1 = horizontal()
        row1.add(reqLen, 0, -2, 1f)
        row1.add(reqCase, 0, -2, 1f)
        card.add(row1)
        card.space(2)
        val row2 = horizontal()
        row2.add(reqNum, 0, -2, 1f)
        row2.add(reqSpec, 0, -2, 1f)
        card.add(row2)

        card.post {
            val edit = findViewById<EditText>(passwordFieldId) ?: return@post
            edit.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val pass = s?.toString().orEmpty()
                    val eval = com.example.syncedn.data.util.ValidationUtils.evaluatePassword(pass)
                    val bothCases = eval.hasUpper && eval.hasLower
                    val passedRules = listOf(eval.hasMinLength, bothCases, eval.hasDigit, eval.hasSpecial).count { it }

                    reqLen.text = if (eval.hasMinLength) "✓ 8+ characters" else "○ 8+ characters"
                    reqLen.setTextColor(if (eval.hasMinLength) okColor else mutedText)

                    reqCase.text = if (bothCases) "✓ Upper & lower (Aa)" else "○ Upper & lower (Aa)"
                    reqCase.setTextColor(if (bothCases) okColor else mutedText)

                    reqNum.text = if (eval.hasDigit) "✓ Number (0-9)" else "○ Number (0-9)"
                    reqNum.setTextColor(if (eval.hasDigit) okColor else mutedText)

                    reqSpec.text = if (eval.hasSpecial) "✓ Special (!@#$)" else "○ Special (!@#$)"
                    reqSpec.setTextColor(if (eval.hasSpecial) okColor else mutedText)

                    val activeBarColor = when {
                        pass.isEmpty() -> Color.rgb(226, 232, 240)
                        eval.isStrong -> okColor
                        passedRules >= 3 -> Color.rgb(37, 99, 235)
                        passedRules == 2 -> Color.rgb(217, 119, 6)
                        else -> Color.rgb(220, 38, 38)
                    }
                    badgeTv.text = when {
                        pass.isEmpty() -> "Min. 8 chars"
                        eval.isStrong -> "Strong ✓"
                        passedRules >= 3 -> "Good"
                        passedRules == 2 -> "Fair"
                        else -> "Weak"
                    }
                    badgeTv.setTextColor(if (pass.isEmpty()) mutedText else activeBarColor)

                    barViews.forEachIndexed { idx, v ->
                        (v.background as? GradientDrawable)?.setColor(
                            if (pass.isNotEmpty() && idx < passedRules.coerceAtLeast(1)) activeBarColor
                            else Color.rgb(226, 232, 240)
                        )
                    }
                }
            })
        }
        return card
    }
    private fun otpInputGroup(hiddenId: Int = UI.otpCode): View {
        val root = vertical().apply { gravity = Gravity.CENTER_HORIZONTAL }
        val boxesLayout = horizontal().apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
        }
        val hiddenEdit = EditText(this).apply {
            this.id = hiddenId
            visibility = View.GONE
        }
        root.addView(hiddenEdit)

        val boxes = mutableListOf<EditText>()
        for (i in 0 until 6) {
            val cell = EditText(this).apply {
                gravity = Gravity.CENTER
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(purple)
                inputType = InputType.TYPE_CLASS_NUMBER
                filters = arrayOf(InputFilter.LengthFilter(1))
                background = bg(Color.rgb(240, 240, 244), 10, Color.rgb(215, 215, 222))
                setPadding(0, 0, 0, 0)
                isSaveEnabled = false
                if (android.os.Build.VERSION.SDK_INT >= 26) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                setOnFocusChangeListener { _, hasFocus ->
                    background = if (hasFocus) bg(Color.WHITE, 10, purple)
                    else bg(Color.rgb(240, 240, 244), 10, Color.rgb(215, 215, 222))
                }
            }
            val lp = LinearLayout.LayoutParams(dp(44), dp(52)).apply {
                setMargins(dp(4), 0, dp(4), 0)
            }
            boxes.add(cell)
            boxesLayout.addView(cell, lp)
        }

        fun syncHidden() {
            val sb = StringBuilder()
            boxes.forEach { sb.append(it.text.toString()) }
            hiddenEdit.setText(sb.toString())
        }

        for (i in 0 until 6) {
            val cell = boxes[i]
            cell.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val str = s?.toString().orEmpty()
                    if (str.length > 1) {
                        val digits = str.filter { it.isDigit() }
                        for (j in 0 until 6) {
                            if (j < digits.length) boxes[j].setText(digits[j].toString())
                            else boxes[j].setText("")
                        }
                        syncHidden()
                        val target = digits.length.coerceAtMost(5)
                        boxes[target].requestFocus()
                        return
                    }
                    syncHidden()
                    if (str.isNotEmpty() && i < 5) {
                        boxes[i + 1].requestFocus()
                    }
                }
            })

            cell.setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DEL) {
                    if (cell.text.isNullOrEmpty() && i > 0) {
                        boxes[i - 1].setText("")
                        boxes[i - 1].requestFocus()
                        syncHidden()
                        return@setOnKeyListener true
                    }
                }
                false
            }
        }

        root.addView(boxesLayout)
        return root
    }
    private fun teamCodeInputGroup(hiddenId: Int = UI.teamCode): View {
        val root = vertical().apply { gravity = Gravity.CENTER_HORIZONTAL }
        val boxesLayout = horizontal().apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
        }
        val hiddenEdit = EditText(this).apply {
            this.id = hiddenId
            visibility = View.GONE
        }
        root.addView(hiddenEdit)

        val boxes = mutableListOf<EditText>()
        for (i in 0 until 6) {
            val cell = EditText(this).apply {
                gravity = Gravity.CENTER
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(purple)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                filters = arrayOf(InputFilter.LengthFilter(1), InputFilter.AllCaps())
                background = bg(Color.rgb(240, 240, 244), 10, Color.rgb(215, 215, 222))
                setPadding(0, 0, 0, 0)
                isSaveEnabled = false
                if (android.os.Build.VERSION.SDK_INT >= 26) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                setOnFocusChangeListener { _, hasFocus ->
                    background = if (hasFocus) bg(Color.WHITE, 10, purple)
                    else bg(Color.rgb(240, 240, 244), 10, Color.rgb(215, 215, 222))
                }
            }
            val lp = LinearLayout.LayoutParams(dp(44), dp(52)).apply {
                setMargins(dp(4), 0, dp(4), 0)
            }
            boxes.add(cell)
            boxesLayout.addView(cell, lp)
        }

        fun syncHidden() {
            val sb = StringBuilder()
            boxes.forEach { sb.append(it.text.toString().trim().uppercase()) }
            hiddenEdit.setText(sb.toString())
        }

        for (i in 0 until 6) {
            val cell = boxes[i]
            cell.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val str = s?.toString().orEmpty()
                    if (str.length > 1) {
                        val chars = str.filter { it.isLetterOrDigit() }.uppercase()
                        for (j in 0 until 6) {
                            if (j < chars.length) boxes[j].setText(chars[j].toString())
                            else boxes[j].setText("")
                        }
                        syncHidden()
                        val target = chars.length.coerceAtMost(5)
                        boxes[target].requestFocus()
                        return
                    }
                    syncHidden()
                    if (str.isNotEmpty() && i < 5) {
                        boxes[i + 1].requestFocus()
                    }
                }
            })

            cell.setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DEL) {
                    if (cell.text.isNullOrEmpty() && i > 0) {
                        boxes[i - 1].setText("")
                        boxes[i - 1].requestFocus()
                        syncHidden()
                        return@setOnKeyListener true
                    }
                }
                false
            }
        }

        root.addView(boxesLayout)
        return root
    }
    private fun form(right: Boolean = false): Pair<FrameLayout, LinearLayout> {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        val scroll = StableScrollView(this).apply { isFillViewport = true }
        val col = vertical()
        scroll.addView(col, FrameLayout.LayoutParams(-1, -2))
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        // Login uses Welcome Page(1); the other forms use Create Team 1.
        // Proportional height preserves the supplied 360 x 291 header designs.
        val header = if (right) "welcome_header" else "form_header"
        col.addView(image(header, "Synced N header").apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(-1, -2))
        return root to col
    }
    private fun LinearLayout.section(padding: Int = 15): LinearLayout {
        val child=vertical().apply { setPadding(dp(padding),0,dp(padding),0) }
        add(child)
        return child
    }
    private fun buildScreen(screen: String): View = when(screen) {
        "start" -> startView()
        "home" -> homeView()
        "settings" -> settingsView()
        "profile" -> profileView()
        "schedule" -> scheduleView()
        "calendar" -> calendarView()
        "availability" -> availabilityView()
        "service" -> serviceView()
        "substitution" -> substitutionView()
        "announcements" -> announcementsView()
        "notifications" -> notificationsView()
        "songs" -> songsView()
        "songDetails" -> songDetailsView()
        "lineup" -> lineupView()
        "teams" -> teamsView()
        "teamDetails" -> teamDetailsView()
        else -> formView(screen)
    }
    private fun formView(screen: String): View {
        val (root,col) = form(right = screen == "login")
        when (screen) {
            "login" -> {
                // The tagline is already part of the supplied Welcome header.
                col.space(8)
                col.section().apply {
                    add(heading("Welcome Back!")); space(8)
                    add(text("Sign in to keep your ministry in sync with your team, schedules, and worship updates.")); space(24)
                    add(field(UI.loginEmail,hint="Email",icon="mail")); space(20)
                    add(field(UI.loginPassword,hint="Password",password=true,icon="lock",toggle=UI.loginPasswordToggle)); space(26)
                    action(UI.loginButton,"Log In"); action(UI.forgotPasswordButton,"Forgot Password?",-2,true)
                    space(10)
                    val divider = horizontal()
                    divider.add(View(this@MemberActivity).apply { setBackgroundColor(Color.DKGRAY) },0,1,1f)
                    divider.add(text("or",centered=true),44,30)
                    divider.add(View(this@MemberActivity).apply { setBackgroundColor(Color.DKGRAY) },0,1,1f)
                    add(divider); space(16)
                    val google = horizontal().apply { id=UI.googleButton; gravity=Gravity.CENTER; background=bg(Color.rgb(217,217,217),10); isClickable=true; isFocusable=true; contentDescription="Continue with Google" }
                    google.add(image("google_mark"),26,26)
                    google.add(text("  Continue with Google",14,navy,true),-2,44)
                    add(google,-1,44); space(16)
                    action(UI.openRegisterButton,"Don’t have an account?   Sign Up",-1,true); space(20)
                }
            }
            "register" -> {
                col.space(12); col.section().apply {
                    add(heading("Register")); space(12)
                    add(field(UI.registerName,"Username")); space(8)
                    add(field(UI.registerPassword,"Password","At least 8 characters (Aa1!)",password=true,toggle=UI.registerPasswordToggle)); space(6)
                    add(passwordStrengthBox(UI.registerPassword)); space(8)
                    add(field(UI.registerConfirm,"Re-Enter Password","Re-Enter Password",password=true,toggle=UI.registerConfirmToggle)); space(8)
                    add(field(UI.registerEmail,"Email",email=true)); space(20)
                    action(UI.registerButton,"Register"); space(24)
                }
            }
            "reset" -> {
                col.space(16); col.section().apply {
                    add(heading("Reset Password")); space(12)
                    add(field(UI.resetEmail,"Email",email=true)); space(12)
                    add(field(UI.resetPassword,"New Password","At least 8 characters (Aa1!)",password=true,toggle=UI.resetPasswordToggle)); space(6)
                    add(passwordStrengthBox(UI.resetPassword)); space(10)
                    add(field(UI.resetConfirm,"Re-Enter Password","Re-Enter Password",password=true,toggle=UI.resetConfirmToggle)); space(24)
                    action(UI.sendCodeButton,"Send Code"); space(24)
                }
            }
            "otp" -> {
                val isRegFlow = successFlow == "register"
                val targetEmail = if (isRegFlow) {
                    preferences.getString("pendingRegEmail", "").orEmpty().ifBlank {
                        preferences.getString("resetEmail", "").orEmpty()
                    }
                } else {
                    preferences.getString("resetEmail", "").orEmpty()
                }
                col.space(42); col.section().apply {
                    add(text("OTP Verification Code", 16, navy, bold = true, centered = true)); space(6)
                    if (targetEmail.isNotBlank()) {
                        add(text("Enter the 6-digit code sent to $targetEmail", 12, Color.rgb(100, 116, 139), centered = true)); space(14)
                    } else {
                        space(14)
                    }
                    add(otpInputGroup(UI.otpCode)); space(16)
                    val resendRow = horizontal().apply {
                        gravity = Gravity.CENTER
                        add(text("Didn't receive a code? ", 13, Color.rgb(100, 100, 110)), -2, -2)
                        add(text("Resend", 13, purple, bold = true).apply {
                            isClickable = true; isFocusable = true
                            setOnClickListener {
                                val (canResend, waitSec) = EmailOtpService.canResendOtp()
                                if (!canResend) {
                                    toast("Please wait ${waitSec}s before requesting another code.")
                                    return@setOnClickListener
                                }
                                val emailStr = if (successFlow == "register") {
                                    preferences.getString("pendingRegEmail", "").orEmpty().ifBlank {
                                        preferences.getString("resetEmail", "").orEmpty()
                                    }
                                } else {
                                    preferences.getString("resetEmail", "").orEmpty()
                                }
                                val passStr = if (successFlow == "register") {
                                    preferences.getString("pendingRegPassword", "").orEmpty().ifBlank {
                                        preferences.getString("resetPendingPassword", "").orEmpty()
                                    }
                                } else {
                                    preferences.getString("resetPendingPassword", "").orEmpty()
                                }
                                val userStr = if (successFlow == "register") {
                                    preferences.getString("pendingRegUsername", "").orEmpty()
                                } else ""
                                if (emailStr.isNotBlank()) {
                                    toast("Resending verification code to $emailStr...")
                                    EmailOtpService.sendOtp(
                                        recipientEmail = emailStr,
                                        pendingPassword = passStr,
                                        pendingUsername = userStr,
                                        purpose = if (successFlow == "register") "register" else "reset"
                                    ) { res ->
                                        runOnUiThread {
                                            if (res.isSuccess) toast("Verification code sent to $emailStr")
                                            else toast("Failed to resend code: ${res.exceptionOrNull()?.localizedMessage}")
                                        }
                                    }
                                } else {
                                    toast(if (successFlow == "register") "Please request a code from the register screen." else "Please request a code from the reset screen.")
                                }
                            }
                        })
                    }
                    add(resendRow); space(28)
                    action(UI.verifyOtpButton, if (isRegFlow) "Verify & Register" else "Reset Password"); space(24)
                }
            }
            "success" -> {
                col.space(44); col.section().apply {
                    add(text("Success!", 21, navy, bold = true, centered = true)); space(16)
                    add(text("Your account has been created",centered=true,id=UI.successMessage)); space(16)
                    action(UI.continueButton,"Continue",120); space(24)
                }
            }
            "teamChoice" -> {
                col.section(10).apply { add(heading("Almost there!")); space(8); add(text("Join an existing Team or create a new Team")) }
                col.add(View(this),1,0,1f)
                col.section(34).apply {
                    action(UI.createTeamChoiceButton,"Create new Team",-1); space(10)
                    action(UI.joinTeamChoiceButton,"Join Team",-1); space(64)
                }
            }
            "createTeam" -> {
                col.space(71); col.section().apply {
                    add(field(UI.teamName, "Team Name")); space(18)
                    action(UI.createTeamButton, "Create", -1); space(24)
                }
            }
            "joinTeam" -> {
                col.space(50); col.section().apply {
                    add(text("Enter Team’s Code", 16, navy, bold = true, centered = true)); space(20)
                    add(teamCodeInputGroup(UI.teamCode)); space(24)
                    action(UI.joinTeamButton, "Enter", -1); space(24)
                }
            }
        }
        return root
    }
    private fun startView(): View = FrameLayout(this).apply {
        setBackgroundColor(Color.rgb(2, 12, 40))
        addView(image("landing_background", "Worshippers and glowing cross").apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }, FrameLayout.LayoutParams(-1, -1))

        val footer = vertical().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            add(button(UI.getStartedButton, "Get Started", 180).apply {
                background = bg(Color.rgb(103, 5, 103), 24)
            }, 180, 40)
            space(16)
            add(text("Church Ministry Management", 15, Color.WHITE, centered = true))
            space(20)
            add(landingIndicator().apply { id = 9999; visibility = View.GONE }, 32, 32)
        }
        addView(footer, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(48)
        })
    }
    private fun landingIndicator(): View = object : View(this) {
        private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        private val arcRect = android.graphics.RectF(2.5f, 2.5f, 29.5f, 29.5f)
        private var rotationAngle = 0f
        private var lastFrameTime = 0L

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            if (hasWindowFocus()) {
                lastFrameTime = android.os.SystemClock.uptimeMillis()
                postInvalidateOnAnimation()
            }
        }

        override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
            super.onWindowFocusChanged(hasWindowFocus)
            if (hasWindowFocus && isAttachedToWindow && windowVisibility == View.VISIBLE) {
                lastFrameTime = android.os.SystemClock.uptimeMillis()
                postInvalidateOnAnimation()
            }
        }

        override fun onVisibilityChanged(changedView: View, visibility: Int) {
            super.onVisibilityChanged(changedView, visibility)
            if (visibility == View.VISIBLE && hasWindowFocus()) {
                lastFrameTime = android.os.SystemClock.uptimeMillis()
                postInvalidateOnAnimation()
            }
        }

        override fun onWindowVisibilityChanged(visibility: Int) {
            super.onWindowVisibilityChanged(visibility)
            if (visibility == View.VISIBLE && hasWindowFocus()) {
                lastFrameTime = android.os.SystemClock.uptimeMillis()
                postInvalidateOnAnimation()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val now = android.os.SystemClock.uptimeMillis()
            if (lastFrameTime > 0L) {
                val delta = (now - lastFrameTime).coerceIn(0L, 100L)
                rotationAngle = (rotationAngle + delta * 0.36f) % 360f
            }
            lastFrameTime = now

            val checkpoint = canvas.save()
            canvas.rotate(rotationAngle, width / 2f, height / 2f)
            canvas.scale(width / 32f, height / 32f)
            ink.color = Color.rgb(35, 187, 125); ink.alpha = 255; ink.strokeWidth = 3.5f
            canvas.drawArc(arcRect, 180f, 180f, false, ink)
            ink.strokeWidth = 1.5f
            repeat(9) { step ->
                val angle = Math.toRadians((12 + step * 18).toDouble())
                ink.alpha = (240 - step * 20).coerceAtLeast(60)
                val x = kotlin.math.cos(angle).toFloat(); val y = kotlin.math.sin(angle).toFloat()
                canvas.drawLine(16f + x * 11.5f, 16f + y * 11.5f, 16f + x * 14.5f, 16f + y * 14.5f, ink)
            }
            canvas.restoreToCount(checkpoint)

            if (isAttachedToWindow && windowVisibility == View.VISIBLE && hasWindowFocus()) {
                postInvalidateOnAnimation()
            }
        }
    }
    private fun homeUpcomingRecord(): org.json.JSONObject? {
        val phtZone = java.util.TimeZone.getTimeZone("Asia/Manila")
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = phtZone }.format(java.util.Date())
        val published = storedServices().filter { it.optString("status") == "Published" }
        val upcoming = published.filter { it.optString("date") >= today }.sortedWith(compareBy({ it.optString("date") }, { it.optString("start") }))
        if (upcoming.isNotEmpty()) {
            return upcoming.first()
        }
        return published.maxByOrNull { it.optString("date") + it.optString("start") }
    }
    private fun formatHeroDateSubtitle(rawDate: String?, start: String?, end: String?): String {
        if (rawDate.isNullOrBlank()) return "Sunday, May 18, 2025 - 9:00 AM"
        val formattedDate = try {
            val inFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("Asia/Manila")
            }
            val d = inFormat.parse(rawDate.trim())
            if (d != null) {
                java.text.SimpleDateFormat("EEEE, MMM d, yyyy", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("Asia/Manila")
                }.format(d)
            } else rawDate
        } catch (_: Exception) { rawDate }

        val sTime = if (!start.isNullOrBlank()) formatServiceTime(start) else ""
        return if (sTime.isNotBlank()) "$formattedDate - $sTime" else formattedDate
    }

    private fun homeUpcomingSecondRecord(): org.json.JSONObject? {
        val phtZone = java.util.TimeZone.getTimeZone("Asia/Manila")
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = phtZone }.format(java.util.Date())
        val published = storedServices().filter { it.optString("status") == "Published" }
        val upcoming = published.filter { it.optString("date") >= today }.sortedWith(compareBy({ it.optString("date") }, { it.optString("start") }))
        if (upcoming.size > 1) {
            return upcoming[1]
        }
        return null
    }
    private fun formatBadgeDate(rawDate: String): String = try {
        if (rawDate.contains("\n")) rawDate
        else {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val parsed = sdf.parse(rawDate)
            if (parsed != null) {
                val month = java.text.SimpleDateFormat("MMM", java.util.Locale.US).format(parsed).uppercase()
                val cal = java.util.Calendar.getInstance().apply { time = parsed }
                "$month\n${cal.get(java.util.Calendar.DAY_OF_MONTH)}"
            } else rawDate
        }
    } catch (_: Exception) { rawDate }
    private fun storedServices(): List<org.json.JSONObject> = try {
        val currentCode = preferences.getString("teamCode", "").orEmpty()
            .ifBlank { FirestoreManager.findCodeByTeamName(teamName).orEmpty() }
        val cached = if (currentCode.isNotBlank()) {
            TeamRepository.getCachedServices(currentCode, preferences).filter { it.status == "Published" }
        } else emptyList()
        if (cached.isNotEmpty()) {
            cached.map { srv ->
                val obj = org.json.JSONObject()
                srv.toMap().forEach { (k, v) ->
                    if (v is List<*>) {
                        val arr = org.json.JSONArray()
                        v.forEach { c -> if (c is Map<*, *>) arr.put(org.json.JSONObject(c)) else arr.put(c) }
                        obj.put(k, arr)
                    } else if (v is Map<*, *>) {
                        obj.put(k, org.json.JSONObject(v))
                    } else {
                        obj.put(k, v)
                    }
                }
                obj
            }
        } else {
            val records = org.json.JSONArray(preferences.getString("leaderServices", "[]"))
            (0 until records.length()).map { records.getJSONObject(it) }.filter { it.optString("status", "Published") == "Published" }
        }
    } catch (_: Exception) { emptyList() }
    private fun formatServiceTime(value: String): String = try {
        val parts=value.split(":");val hour=parts[0].toInt()
        "${if(hour % 12 == 0) 12 else hour % 12}:${parts[1]}${if(hour < 12) "AM" else "PM"}"
    } catch (_: Exception) { value }
    private fun openHomeUpcoming() {
        val record = homeUpcomingRecord()
        openSpecificService(record)
    }
    private fun openHomeSecondUpcoming() {
        val record = homeUpcomingSecondRecord() ?: homeUpcomingRecord()
        openSpecificService(record)
    }
    private fun openSpecificService(record: org.json.JSONObject?) {
        serviceReturnPage = "home"
        selectSheetService(record ?: sampleSheetService())
        selectedService = record?.optString("title") ?: "Sunday Worship Service"
        preferences.edit().putString("serviceDate", record?.optString("date") ?: "May 18, 2025")
            .putString("serviceTime", if (record != null) formatServiceTime(record.optString("start")) + " – " + formatServiceTime(record.optString("end")) else "6:00AM – 11:00AM")
            .putString("serviceVenue", record?.optString("venue") ?: "Main Sanctuary").apply()
        navigate("service")
    }
    private fun announcementHomeTile(): View = memberTile(UI.announcementsButton, "announcement", "Announcements", 11).apply {
        val activeCode = preferences.getString("teamCode", "").orEmpty()
        val announcements = TeamRepository.getCachedAnnouncements(activeCode, preferences)
        val count = announcements.size
        if (count > 0) {
            space(3)
            val hasUrgent = announcements.any { it.urgent }
            if (hasUrgent) {
                add(text("$count post${if (count == 1) "" else "s"} • Urgent", 10, Color.rgb(220, 38, 38), bold = true, centered = true))
            } else {
                add(text("$count post${if (count == 1) "" else "s"}", 10, purple, centered = true))
            }
        }
        setOnClickListener { navigate("announcements") }
    }

    private fun urgentAlertBanner(): View? {
        val activeCode = preferences.getString("teamCode", "").orEmpty()
        val urgentItem = TeamRepository.getCachedAnnouncements(activeCode, preferences).firstOrNull { it.urgent } ?: return null
        return vertical().apply {
            background = bg(Color.rgb(255, 250, 250), 16, Color.rgb(254, 202, 202))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            elevation = dp(2).toFloat()
            isClickable = true
            isFocusable = true
            contentDescription = "Urgent announcement: ${urgentItem.title}. Tap to view."
            setOnClickListener { navigate("announcements") }

            val row = horizontal().apply { gravity = Gravity.CENTER_VERTICAL }
            val iconBadge = horizontal().apply {
                gravity = Gravity.CENTER_VERTICAL
                background = bg(Color.rgb(254, 226, 226), 8)
                setPadding(dp(8), dp(4), dp(8), dp(4))
                add(Symbol(this@MemberActivity, "warning", Color.rgb(220, 38, 38)), 14, 14)
                addView(View(this@MemberActivity), LinearLayout.LayoutParams(dp(6), 1))
                add(text("Urgent Notice", 11, Color.rgb(185, 28, 28), bold = true))
            }
            row.addView(iconBadge)
            row.addView(View(this@MemberActivity), LinearLayout.LayoutParams(dp(10), 1))
            val dateStr = formatAnnouncementDate(urgentItem.createdAt)
            row.add(text(dateStr, 11, Color.rgb(120, 113, 108)), 0, -2, 1f)
            row.add(text("›", 18, Color.rgb(156, 163, 175), bold = true), -2, -2)
            add(row)
            space(8)
            add(text(urgentItem.title, 14, Color.rgb(15, 23, 42), bold = true))
            space(4)
            val snippet = if (urgentItem.message.length > 90) urgentItem.message.take(87) + "..." else urgentItem.message
            add(text(snippet, 12, Color.rgb(71, 85, 105)))
        }
    }
    private fun homeView(): View {
        val root=vertical().apply { setBackgroundColor(Color.WHITE) }
        val swipeRefresh = androidx.swiperefreshlayout.widget.SwipeRefreshLayout(this).apply {
            setColorSchemeColors(navy, Color.rgb(167, 0, 213))
            setOnRefreshListener {
                refreshTeamRoster(force = true)
                refreshTeamServices(force = true)
                val activeCode = preferences.getString("teamCode", "").orEmpty()
                    .ifBlank { FirestoreManager.findCodeByTeamName(teamName).orEmpty() }
                if (activeCode.isNotBlank()) {
                    TeamRepository.getAnnouncements(activeCode, true, preferences)
                    TeamRepository.getSubstitutions(activeCode, true, preferences)
                }
                postDelayed({
                    isRefreshing = false
                    navigate("home")
                }, 900)
            }
        }
        val scroll=StableScrollView(this).apply { isFillViewport=true }
        val content=vertical(); scroll.addView(content, FrameLayout.LayoutParams(-1,-2))
        swipeRefresh.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        root.add(swipeRefresh,-1,0,1f)
        val hero=HomeHeroLayout(this)
        val header=View(this).apply {
            background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(Color.rgb(8,7,32),Color.rgb(21,18,105))).apply { cornerRadii=floatArrayOf(0f,0f,0f,0f,dp(60).toFloat(),dp(60).toFloat(),dp(60).toFloat(),dp(60).toFloat()) }
        }
        hero.addView(View(this).apply { background = bg(Color.rgb(167,0,213),60) },
            FrameLayout.LayoutParams(-1,dp(280)))
        hero.addView(header,FrameLayout.LayoutParams(-1,dp(245)))
        val top=vertical().apply { setPadding(dp(24),dp(32),dp(24),0) }
        val row=horizontal()
        val greeting=vertical().apply { add(text("Welcome to Ministry",14,Color.WHITE)); space(4); add(text(displayName.ifBlank { "Member" },23,Color.WHITE,id=UI.welcomeText)) }
        row.add(greeting,0,-2,1f)
        val bell=FrameLayout(this).apply { id=UI.notificationsButton; background=bg(Color.rgb(244,243,227),24); isClickable=true; isFocusable=true; contentDescription="Notifications" }
        bell.addView(Symbol(this,"bell",navy),FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));row.add(bell,40,40)
        row.addView(image("profile_photo","Profile demo").apply {id=UI.profileButton;isClickable=true;isFocusable=true},
            LinearLayout.LayoutParams(dp(50),dp(50)).apply {leftMargin=dp(12)})
        top.add(row);top.space(18);top.add(text("Together, in Worship, in Sync",14,Color.rgb(183,209,255)))
        hero.addView(top,FrameLayout.LayoutParams(-1,-2))
        // Clean high-contrast worship hero card strictly matching user specification
        val upcomingRec = homeUpcomingRecord()
        val card = FrameLayout(this).apply {
            id = UI.worshipCard
            background = bg(Color.rgb(10, 13, 27), 24)
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            elevation = dp(8).toFloat()
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                outlineAmbientShadowColor = Color.rgb(135, 135, 145)
                outlineSpotShadowColor = Color.rgb(135, 135, 145)
            }
            isClickable = true
            isFocusable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES

            val coverUrl = upcomingRec?.optString("coverImageUrl").orEmpty()
            val imgView = ImageView(this@MemberActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                if (coverUrl.isNotBlank()) {
                    com.example.syncedn.data.storage.ImageLoader.loadProfilePhoto(this, coverUrl, placeholderResId = R.drawable.worship_hero_bg, circular = false, maxDimension = 800)
                } else {
                    setImageResource(R.drawable.worship_hero_bg)
                }
            }
            addView(imgView, FrameLayout.LayoutParams(-1, -1))

            val overlay = View(this@MemberActivity).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.argb(85, 0, 0, 0), Color.argb(225, 8, 11, 24))
                )
            }
            addView(overlay, FrameLayout.LayoutParams(-1, -1))

            val innerRow = horizontal().apply {
                setPadding(dp(22), dp(20), dp(20), dp(20))
                gravity = Gravity.CENTER_VERTICAL
                clipChildren = false
                clipToPadding = false

                val textCol = vertical().apply {
                    val rawTitle = upcomingRec?.optString("title")?.ifBlank { "Next Worship\nSession" } ?: "Next Worship\nSession"
                    val formattedTitle = if (rawTitle.contains("Worship", ignoreCase = true) && !rawTitle.contains("\n")) {
                        rawTitle.replaceFirst(" ", "\n")
                    } else rawTitle

                    val titleTv = TextView(this@MemberActivity).apply {
                        text = formattedTitle
                        setTextColor(Color.WHITE)
                        textSize = 22f
                        typeface = Typeface.DEFAULT_BOLD
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                    addView(titleTv, LinearLayout.LayoutParams(-1, -2))
                    space(6)

                    val rawDate = upcomingRec?.optString("date")
                    val rawStart = upcomingRec?.optString("start")
                    val rawEnd = upcomingRec?.optString("end")
                    val formattedSubtitle = formatHeroDateSubtitle(rawDate, rawStart, rawEnd)

                    val subTv = TextView(this@MemberActivity).apply {
                        text = formattedSubtitle
                        setTextColor(Color.rgb(203, 213, 225))
                        textSize = 13f
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                    addView(subTv, LinearLayout.LayoutParams(-1, -2))
                    space(14)

                    val venuePill = FrameLayout(this@MemberActivity).apply {
                        background = bg(Color.argb(175, 26, 32, 44), 16, Color.argb(90, 255, 255, 255))
                        setPadding(dp(14), dp(6), dp(14), dp(6))
                        val vTv = TextView(this@MemberActivity).apply {
                            text = upcomingRec?.optString("venue")?.ifBlank { "Main Sanctuary" } ?: "Main Sanctuary"
                            setTextColor(Color.WHITE)
                            textSize = 12f
                            typeface = Typeface.DEFAULT_BOLD
                            maxLines = 1
                            ellipsize = android.text.TextUtils.TruncateAt.END
                        }
                        addView(vTv, FrameLayout.LayoutParams(-2, -2))
                    }
                    addView(venuePill, LinearLayout.LayoutParams(-2, -2))
                }
                add(textCol, 0, -2, 1f)

                val chevronBtn = FrameLayout(this@MemberActivity).apply {
                    background = bg(Color.argb(135, 30, 41, 59), 23, Color.argb(60, 255, 255, 255))
                    val arrow = TextView(this@MemberActivity).apply {
                        text = "›"
                        setTextColor(Color.WHITE)
                        textSize = 24f
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                    }
                    addView(arrow, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
                }
                addView(chevronBtn, LinearLayout.LayoutParams(dp(46), dp(46)).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    leftMargin = dp(12)
                })
            }
            addView(innerRow, FrameLayout.LayoutParams(-1, -1))

            contentDescription = if (upcomingRec != null) {
                "Next Worship Session. ${upcomingRec.optString("title")}, ${upcomingRec.optString("date")}. ${upcomingRec.optString("venue", "Main Sanctuary")}. Open details."
            } else {
                "Next Worship Session. Open details."
            }
        }

        hero.addView(card, FrameLayout.LayoutParams(-1, dp(200)).apply {
            leftMargin = dp(24)
            rightMargin = dp(24)
            topMargin = dp(132)
        })
        hero.card = card
        content.add(hero, -1, -2)
        content.space(2)
        content.section(15).apply {
            add(text("Ministry Hub",17,navy,true)); space(12)
            val upper=horizontal().apply { gravity=Gravity.TOP }
            val team=FrameLayout(this@MemberActivity).apply {
                id=UI.teamHubButton
                background=bg(Color.WHITE,24,Color.rgb(211,211,211))
                elevation=dp(4).toFloat();isClickable=true;isFocusable=true
                contentDescription="Worship Team, 10 members, view team"
            }
            val teamInfo=vertical().apply {
                setPadding(dp(13),dp(32),dp(12),dp(12))
                val titleRow=horizontal()
                titleRow.add(ImageView(this@MemberActivity).apply {
                    setImageResource(R.drawable.synced_n_logo_sharp)
                    contentDescription = "Synced N Ministry Team Logo"
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }, 50, 50)
                titleRow.add(text("Worship\nTeam",18,Color.rgb(66,82,114),id=UI.teamTitle).apply {
                    setPadding(dp(16),0,0,0)
                },0,-2,1f)
                add(titleRow);space(8)
                val memberCount = getActiveRoster().size
                add(text("$memberCount Member${if (memberCount == 1) "" else "s"}",11,Color.rgb(84,91,116),true).also {
                    homeMemberCount = it
                })
            }
            team.addView(teamInfo,FrameLayout.LayoutParams(-1,-1))
            val teamArrow=FrameLayout(this@MemberActivity).apply {background=bg(Color.WHITE,24,navy)}
            teamArrow.addView(Symbol(this@MemberActivity,"arrow",navy),FrameLayout.LayoutParams(dp(20),dp(20),Gravity.CENTER))
            team.addView(teamArrow,FrameLayout.LayoutParams(dp(40),dp(40),Gravity.BOTTOM or Gravity.END).apply {rightMargin=dp(23);bottomMargin=dp(20)})
            upper.add(team,0,170,0.63f)
            upper.add(View(this@MemberActivity),24,1)
            val rightColumn=vertical().apply {
                add(memberTile(UI.calendarButton,"calendar","Calendar"),-1,76);space(18)
                add(memberTile(UI.songsButton,"music","Songs"),-1,76)
            }
            upper.add(rightColumn,0,-2,0.37f)
            add(upper);space(18)
            urgentAlertBanner()?.let { alertCard ->
                add(alertCard); space(18)
            }
            add(text("Upcoming",17,navy,true));space(12)
            val upcoming=horizontal().apply { gravity=Gravity.TOP }
            val service=horizontal().apply {
                id=UI.scheduleCard
                background=bg(Color.WHITE,20,Color.rgb(226,232,240))
                elevation=dp(3).toFloat();setPadding(dp(8),dp(10),dp(10),dp(10))
                isClickable=true;isFocusable=true;minimumHeight=dp(104)
                val secondRec = homeUpcomingSecondRecord()
                if (secondRec != null) {
                    val dateRaw = secondRec.optString("date")
                    val badgeDate = formatBadgeDate(dateRaw)
                    val serviceTitle = secondRec.optString("title").ifBlank { "Sunday Worship Service" }
                    val start = secondRec.optString("start")
                    val end = secondRec.optString("end")
                    val timeLabel = if(start.isNotBlank() && end.isNotBlank()) formatServiceTime(start) + " – " + formatServiceTime(end) else if (start.isNotBlank()) formatServiceTime(start) else ""
                    val venue = secondRec.optString("venue", "Main Sanctuary")
                    contentDescription = "$dateRaw, $serviceTitle, $timeLabel. Open service details."

                    add(text(badgeDate, 13, Color.WHITE, bold = true, centered = true).apply {
                        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                            intArrayOf(purple, Color.rgb(167, 0, 214))).apply { cornerRadius = dp(12).toFloat() }
                    }, 52, 62)
                    add(vertical().apply {
                        setPadding(dp(8), 0, dp(6), 0)
                        add(text(serviceTitle, 12, navy, true)); space(3)
                        add(text(timeLabel, 10, navy)); space(2)
                        add(text(venue, 10, purple, true))
                    }, 0, -2, 1f)
                    add(Symbol(this@MemberActivity, "arrow", Color.GRAY), 14, 20)
                } else {
                    val fallbackBadge = formatBadgeDate(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()))
                    add(text(fallbackBadge, 13, Color.WHITE, bold = true, centered = true).apply {
                        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                            intArrayOf(purple, Color.rgb(167, 0, 214))).apply { cornerRadius = dp(12).toFloat() }
                    }, 52, 62)
                    add(vertical().apply {
                        setPadding(dp(8), 0, dp(6), 0)
                        add(text("No upcoming", 12, navy, true)); space(3)
                        add(text("Check back soon.", 10, Color.GRAY))
                    }, 0, -2, 1f)
                }
            }
            upcoming.add(service,0,-2,0.63f)
            upcoming.add(View(this@MemberActivity),24,1)
            upcoming.add(announcementHomeTile(),0,104,0.37f)
            add(upcoming);space(44)
        }
        val nav=horizontal().apply { elevation=dp(8).toFloat();setBackgroundColor(Color.WHITE) }
        for ((id,symbol,label) in listOf(Triple(UI.homeTab,"home","Home"),Triple(UI.scheduleTab,"calendar","Schedule"),Triple(UI.teamsTab,"teams","Teams"),Triple(UI.settingsTab,"settings","Settings"))) {
            val c=if(id==UI.homeTab) Color.rgb(214,0,242) else navy
            val tab=vertical().apply { this.id=id;gravity=Gravity.CENTER;isClickable=true;isFocusable=true;contentDescription=label;add(Symbol(this@MemberActivity,symbol,c),24,24);space(2);add(text(label,9,c,centered=true)) }
            nav.add(tab,0,64,1f)
        }
        root.add(nav,-1,64)
        return root
    }

    private fun openProfile(from: String) { profileReturnPage=from; navigate("profile") }
    private fun settingsHeader(title: String, back: Boolean): View {
        val row=horizontal().apply {
            setBackgroundColor(Color.WHITE)
            elevation=dp(4).toFloat()
        }
        val backView=FrameLayout(this).apply {
            id=UI.pageBack;isClickable=true;isFocusable=true;contentDescription=if(back) "Back" else "Back to Home"
        }
        backView.addView(Symbol(this,if(back) "back" else "settings",navy),FrameLayout.LayoutParams(dp(30),dp(30),Gravity.CENTER))
        row.add(backView,70,80)
        val banner=FrameLayout(this).apply {background=bg(purple,48)}
        val inner=FrameLayout(this).apply {
            background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(Color.rgb(8,7,32),Color.rgb(42,33,186))).apply {
                cornerRadii=floatArrayOf(dp(48).toFloat(),dp(48).toFloat(),0f,0f,0f,0f,dp(48).toFloat(),dp(48).toFloat())
            }
        }
        inner.addView(text(title,22,Color.WHITE,centered=true),FrameLayout.LayoutParams(-1,-1))
        banner.addView(inner,FrameLayout.LayoutParams(-1,-1).apply {leftMargin=dp(35)})
        row.add(banner,0,80,1f)
        return row
    }
    private fun bottomNavigation(selected: String): View {
        val nav=horizontal().apply {elevation=dp(8).toFloat();setBackgroundColor(Color.WHITE)}
        for((id,icon,label) in listOf(Triple(UI.homeTab,"home","Home"),Triple(UI.scheduleTab,"calendar","Schedule"),Triple(UI.teamsTab,"teams","Teams"),Triple(UI.settingsTab,"settings","Settings"))) {
            val color=if(label==selected) Color.rgb(214,0,242) else navy
            nav.add(vertical().apply {
                this.id=id;gravity=Gravity.CENTER;isClickable=true;isFocusable=true;contentDescription=label
                add(Symbol(this@MemberActivity,icon,color),24,24);space(2);add(text(label,9,color,centered=true))
            },0,64,1f)
        }
        return nav
    }
    private fun teamAvatarStack(members: List<com.example.syncedn.data.model.TeamMember>, maxCount: Int = 3): View {
        val container = FrameLayout(this)
        val displayList = members.take(maxCount)
        val sizeDp = 26
        val overlapDp = 16

        if (displayList.isEmpty()) {
            val emptyIv = ImageView(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.rgb(90, 188, 248))
                }
            }
            container.addView(emptyIv, FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp), Gravity.CENTER_VERTICAL))
            return container
        }

        displayList.forEachIndexed { index, member ->
            val avatarFrame = FrameLayout(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.WHITE)
                    setStroke(dp(1), Color.rgb(217, 217, 217))
                }
                setPadding(dp(1), dp(1), dp(1), dp(1))
            }
            val pUri = member.photoUri.ifBlank {
                findMemberPhotoUri(member.uid, member.fullName.ifBlank { member.username }, member.email)
            }
            val iv = memberAvatarView(pUri, sizeDp - 2)
            avatarFrame.addView(iv, FrameLayout.LayoutParams(dp(sizeDp - 2), dp(sizeDp - 2), Gravity.CENTER))

            val lp = FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp), Gravity.CENTER_VERTICAL).apply {
                leftMargin = dp(index * overlapDp)
            }
            container.addView(avatarFrame, lp)
        }

        val totalWidthDp = sizeDp + (displayList.size - 1) * overlapDp
        container.layoutParams = LinearLayout.LayoutParams(dp(totalWidthDp), dp(sizeDp)).apply {
            gravity = Gravity.CENTER_VERTICAL
            rightMargin = dp(8)
        }
        return container
    }

    private fun settingsView(): View {
        val root=vertical().apply {setBackgroundColor(Color.WHITE)}
        root.add(settingsHeader("Settings",true))
        val scroll=StableScrollView(this).apply {isFillViewport=true; clipToPadding=true}
        val col=vertical();scroll.addView(col,FrameLayout.LayoutParams(-1,-2))
        val swipeRefresh = androidx.swiperefreshlayout.widget.SwipeRefreshLayout(this).apply {
            setColorSchemeColors(navy, Color.rgb(167, 0, 213))
            addView(scroll, ViewGroup.LayoutParams(-1, -1))
            setOnRefreshListener {
                val uid = AuthManager.currentUser?.uid?.takeIf { it.isNotBlank() }
                    ?: preferences.getString(SessionManager.KEY_USER_ID, "").orEmpty()
                val email = preferences.getString("email", "").orEmpty()
                TeamRepository.refreshUserProfile(uid, email, preferences) {
                    runOnUiThread {
                        fullName = preferences.getString(SessionManager.KEY_FULL_NAME, fullName).orEmpty()
                        displayName = preferences.getString(SessionManager.KEY_USERNAME, displayName).orEmpty()
                        isRefreshing = false
                        navigate("settings")
                    }
                }
            }
        }
        root.add(swipeRefresh, -1, 0, 1f)
        col.space(24)
        col.section(18).apply {
            val userRow=horizontal()
            userRow.add(image("profile_photo","Profile photo").apply {id=UI.settingsAvatar},78,78)
            val info=vertical().apply {
                setPadding(dp(20),0,0,0)
                add(text(fullName,14,Color.BLACK,true));space(8)
                add(text(displayName,12).apply {background=bg(Color.rgb(217,217,217),14);setPadding(dp(10),0,dp(10),0);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END},-1,24)
                space(10)
                add(text("Profile",13,Color.BLACK,true,true,id=UI.openProfileButton).apply {
                    background=bg(Color.rgb(217,217,217),4);isClickable=true;isFocusable=true
                },-1,32)
            }
            userRow.add(info,0,-2,1f);add(userRow);space(22)
        }
        col.section(12).apply {
            add(text("Teams",15,Color.BLACK,true));space(8)
            val team=horizontal().apply {id=UI.settingsTeam; background=bg(Color.rgb(217,217,217),4);setPadding(dp(5),0,dp(10),0);isClickable=true;isFocusable=true}
            val memberCount = getActiveRoster().size
            team.add(vertical().apply {
                add(text(teamName.ifBlank {"Worship Team"},14,Color.BLACK,true))
                add(text("$memberCount member${if (memberCount == 1) "" else "s"}",12,Color.GRAY))
            },0,-2,1f)
            team.add(teamAvatarStack(getActiveRoster()), -2, -2)
            add(team,-1,52);space(50)
        }
        col.section(26).apply {
            add(text("Personal",15,Color.BLACK,true));space(16)
            val notification=horizontal()
            notification.add(Symbol(this@MemberActivity,"bell",navy),30,30)
            notification.add(text("Notification",14,Color.BLACK,true).apply {setPadding(dp(10),0,0,0)},0,48,1f)
            notification.add(Switch(this@MemberActivity).apply {
                id=UI.notificationSwitch;contentDescription="Notifications"
                val states=arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf())
                thumbTintList=ColorStateList(states,intArrayOf(Color.WHITE,Color.WHITE))
                trackTintList=ColorStateList(states,intArrayOf(Color.rgb(0,124,224),Color.rgb(178,182,191)))
            },-2,48)
            add(notification)
            add(settingsAction(UI.logoutButton,"logout","Logout"));space(66)
            add(text("General",15,Color.BLACK,true));space(10)
            add(settingsAction(UI.aboutButton,"about","About"))
            add(settingsAction(UI.helpButton,"help","Help"));space(52)
        }
        root.add(bottomNavigation("Settings"),-1,64)
        return root
    }
    private fun settingsAction(id: Int, icon: String, label: String) = horizontal().apply {
        this.id=id;isClickable=true;isFocusable=true;contentDescription=label
        add(Symbol(this@MemberActivity,icon,Color.BLACK),30,30)
        add(text(label,14,Color.BLACK,true).apply {setPadding(dp(10),0,0,0)},0,44,1f)
    }
    private fun setupSettings() {
        applyProfilePhoto(UI.settingsAvatar)
        click(UI.pageBack) {navigate("home")}
        click(UI.openProfileButton) {openProfile("settings")}
        click(UI.settingsTeam) { teamTab = "Members"; navigate("teams") }
        findViewById<Switch>(UI.notificationSwitch).apply {
            isChecked=preferences.getBoolean("notifications",true)
            setOnCheckedChangeListener { _, checked ->
                preferences.edit().putBoolean("notifications", checked).apply()
                val unmuted = preferences.getBoolean("teamUnmuted", true)
                TeamRepository.saveNotificationPreferences(currentUserId(), checked, unmuted, preferences)
                val curCode = preferences.getString("teamCode", "").orEmpty()
                if (checked && curCode.isNotBlank()) {
                    com.example.syncedn.data.auth.SyncedFirebaseMessagingService.subscribeToTeam(curCode)
                } else if (!checked && curCode.isNotBlank()) {
                    com.example.syncedn.data.auth.SyncedFirebaseMessagingService.unsubscribeFromTeam(curCode)
                }
                toast(if(checked) "FCM Notifications enabled" else "FCM Notifications disabled")
            }
        }
        click(UI.logoutButton) {
            AlertDialog.Builder(this).setTitle("Log out?").setMessage("Return to the login page?")
                .setPositiveButton("Log Out") { _, _ ->
                    SessionManager.clearSession(preferences)
                    displayName = ""
                    fullName = ""
                    teamName = ""
                    teamRoster = emptyList()
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra("startPage", "login")
                    })
                    finish()
                }
                .setNegativeButton("Cancel", null).show()
        }
        click(UI.aboutButton) {
            AlertDialog.Builder(this)
                .setTitle("About Synced N")
                .setMessage(
                    "Synced N — Church Ministry Management Platform\n\n" +
                    "Version 1.0.0 (Production Release)\n\n" +
                    "A comprehensive collaborative ministry workflow system designed to synchronize worship leaders, vocalists, instrumentalists, and technical teams.\n\n" +
                    "Key Capabilities:\n" +
                    "• Worship Service Scheduling & Role Assignments\n" +
                    "• Dynamic Member Availability & Conflict Detection\n" +
                    "• Song Catalog, Transposition & Rehearsal Lineups\n" +
                    "• Service Substitution & Cover Requests (Inclusion/Exclusion)\n" +
                    "• Real-time Encrypted Team Communication & Media Sharing\n" +
                    "• Cloud Firestore Synchronization & Cloud Messaging (FCM)\n\n" +
                    "Developed with care for church ministry excellence."
                )
                .setPositiveButton("OK", null)
                .show()
        }
        click(UI.helpButton) {
            AlertDialog.Builder(this)
                .setTitle("Help & User Guide")
                .setMessage(
                    "How to use Synced N:\n\n" +
                    "1. Schedule & Lineups: View worship services, assigned roles, and song lineups. Tap any song to view lyrics, chords, transpose keys, and watch rehearsal videos.\n\n" +
                    "2. Availability: Set your unavailable dates in Calendar to help leaders schedule around your conflicts.\n\n" +
                    "3. Substitutions: If unable to serve or if you want to join a service, tap Request Substitution (Exclusion/Inclusion) on the service card.\n\n" +
                    "4. Team Chat: Tap Teams to chat with your ministry teammates and share rehearsal photos.\n\n" +
                    "5. Notifications: Turn on Push Notifications in Settings to receive instant alerts on service changes and announcements."
                )
                .setPositiveButton("Close", null)
                .show()
        }
        click(UI.homeTab) {navigate("home")};click(UI.scheduleTab) {schedule()}
        click(UI.teamsTab) {navigate("teams")};click(UI.settingsTab) { /* already on Settings */ }
    }
    private fun profileField(
        id: Int,
        label: String,
        value: String,
        hint: String = "",
        kind: Int = InputType.TYPE_CLASS_TEXT,
        maxLength: Int = ValidationUtils.MAX_NAME_LENGTH
    ): View {
        val row = horizontal().apply { background = bg(Color.rgb(217, 217, 217), 5); setPadding(dp(6), 0, dp(6), 0) }
        row.add(text(label, 12).apply { maxLines = 1 }, 100, 40)
        row.add(EditText(this).apply {
            this.id = id; setText(value); this.hint = hint; inputType = kind; setSingleLine(true)
            background = null; textSize = 12f; includeFontPadding = false
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setTextColor(Color.rgb(112, 112, 112)); setHintTextColor(Color.GRAY)
            setPadding(0, 0, dp(3), 0); isSaveEnabled = false
            filters = arrayOf(InputFilter.LengthFilter(maxLength))
            if (kind == InputType.TYPE_CLASS_PHONE) {
                keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789+")
            }
        }, 0, 40, 1f)
        if (id != UI.profileEmail) row.add(Symbol(this, "arrow", Color.rgb(65, 80, 100)), 14, 20)
        return row
    }
    private fun preferenceRow(id: Int, label: String, values: List<String>, value: String): View {
        val row=horizontal().apply {background=bg(Color.rgb(217,217,217),5);setPadding(dp(6),0,0,0)}
        row.add(text(label,12),95,40)
        val choices=object : ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,values) {
            override fun getView(position: Int,convertView: View?,parent: ViewGroup): View {
                return (super.getView(position,convertView,parent) as TextView).apply {
                    textSize=12f;includeFontPadding=false;setTextColor(Color.rgb(112,112,112))
                    gravity=Gravity.END or Gravity.CENTER_VERTICAL;setPadding(0,0,dp(6),0)
                    minHeight=dp(40);setSingleLine(true)
                }
            }
            override fun getDropDownView(position: Int,convertView: View?,parent: ViewGroup): View {
                return (super.getDropDownView(position,convertView,parent) as TextView).apply {
                    textSize=14f;includeFontPadding=false;gravity=Gravity.START or Gravity.CENTER_VERTICAL
                    setPadding(dp(12),0,dp(12),0);minHeight=dp(48)
                }
            }
        }
        row.add(Spinner(this).apply {
            this.id=id;contentDescription=label;adapter=choices
            setSelection(values.indexOf(value).coerceAtLeast(0))
        },0,40,1f)
        return row
    }
    private fun profileView(): View {
        fullName = preferences.getString(SessionManager.KEY_FULL_NAME, fullName).orEmpty()
        displayName = preferences.getString(SessionManager.KEY_USERNAME, displayName).orEmpty()
        val root=vertical().apply {setBackgroundColor(Color.WHITE)}
        root.add(settingsHeader("Profile",true))
        val scroll=StableScrollView(this).apply {isFillViewport=true; clipToPadding=true}
        val col=vertical();scroll.addView(col,FrameLayout.LayoutParams(-1,-2));root.add(scroll,-1,0,1f)
        col.space(28)
        val avatar=FrameLayout(this)
        avatar.addView(image("profile_photo","Profile photo").apply {id=UI.profileAvatar},FrameLayout.LayoutParams(dp(78),dp(78),Gravity.CENTER))
        val camera=FrameLayout(this).apply {id=UI.changePhoto;isClickable=true;isFocusable=true;contentDescription="Choose profile photo";background=bg(Color.WHITE,14)}
        camera.addView(Symbol(this,"camera",Color.BLACK),FrameLayout.LayoutParams(dp(16),dp(16),Gravity.CENTER))
        avatar.addView(camera,FrameLayout.LayoutParams(dp(24),dp(24),Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {leftMargin=dp(48);bottomMargin=dp(2)})
        col.add(avatar,-1,82);col.space(10)
        col.add(text(fullName.ifBlank { displayName.ifBlank { "Member" } },14,Color.BLACK,true,true));col.space(3)
        col.add(text("Ministry Member",12,Color.GRAY,centered=true));col.space(26)
        col.section(11).apply {
            add(text("Profile",15,Color.BLACK,true));space(12)
            add(profileField(UI.profileName,"Full Name",fullName,hint="Add your full name",maxLength=ValidationUtils.MAX_NAME_LENGTH));space(8)
            add(profileField(UI.profileUsername,"Username",displayName,hint="Add your username",maxLength=ValidationUtils.MAX_USERNAME_LENGTH));space(8)
            add(profileField(UI.profilePhone,"Phone Number",preferences.getString(SessionManager.KEY_PHONE,"").orEmpty(),"Add your phone number",InputType.TYPE_CLASS_PHONE,maxLength=ValidationUtils.MAX_PHONE_LENGTH));space(8)
            add(profileField(UI.profileEmail,"Email",preferences.getString(SessionManager.KEY_EMAIL,"").orEmpty(),kind=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,maxLength=ValidationUtils.MAX_EMAIL_LENGTH));space(32)
            action(UI.saveProfileButton,"Save Profile Changes");space(32)
        }
        root.add(bottomNavigation("Settings"),-1,64)
        return root
    }
    private fun setupProfile() {
        applyProfilePhoto(UI.profileAvatar)
        click(UI.pageBack) {navigate(profileReturnPage)}
        click(UI.homeTab) {navigate("home")};click(UI.scheduleTab) {schedule()}
        click(UI.teamsTab) {navigate("teams")};click(UI.settingsTab) {navigate("settings")}
        click(UI.changePhoto) {
            val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply {addCategory(Intent.CATEGORY_OPENABLE);type="image/*"}
            @Suppress("DEPRECATION")
            startActivityForResult(intent,300)
        }
        click(UI.saveProfileButton) {
            val name=input(UI.profileName);val username=input(UI.profileUsername);val email=input(UI.profileEmail)
            val phone=input(UI.profilePhone)
            val phoneStr = phone.text.toString().trim()
            val emailStr = email.text.toString().trim()
            when {
                name.text.isBlank()->invalid(name,"Enter your full name.")
                name.text.length > ValidationUtils.MAX_NAME_LENGTH -> invalid(name, "Full name must not exceed ${ValidationUtils.MAX_NAME_LENGTH} characters.")
                username.text.isBlank()->invalid(username,"Enter your username.")
                username.text.length > ValidationUtils.MAX_USERNAME_LENGTH -> invalid(username, "Username must not exceed ${ValidationUtils.MAX_USERNAME_LENGTH} characters.")
                phoneStr.isNotBlank() && !ValidationUtils.isValidPhilippinePhone(phoneStr) -> invalid(phone, "Enter a valid Philippine number (e.g. 09123456789 or +639123456789).")
                emailStr.isNotBlank() && !Patterns.EMAIL_ADDRESS.matcher(emailStr).matches()->invalid(email,"Enter a valid email.")
                else->{
                    fullName=name.text.toString().trim();displayName=username.text.toString().trim()
                    val countryStr = findViewById<Spinner>(UI.countrySpinner)?.selectedItem?.toString() ?: preferences.getString(SessionManager.KEY_COUNTRY, "Philippines").orEmpty()
                    val langStr = findViewById<Spinner>(UI.languageSpinner)?.selectedItem?.toString() ?: preferences.getString(SessionManager.KEY_LANGUAGE, "English").orEmpty()
                    val uid = preferences.getString(SessionManager.KEY_USER_ID, "").orEmpty()
                    val activeCode = preferences.getString(SessionManager.KEY_TEAM_CODE, "").orEmpty()
                    val activeRole = preferences.getString(SessionManager.KEY_ROLE, "Member").orEmpty()
                    val activeMinistry = preferences.getString(SessionManager.KEY_MINISTRY_ROLE, "").orEmpty()
                    val activePhoto = preferences.getString(SessionManager.KEY_PHOTO_URI, "").orEmpty()
                    val profileObj = com.example.syncedn.data.model.UserProfile(
                        uid = uid,
                        username = displayName,
                        email = emailStr,
                        role = activeRole,
                        teamName = teamName,
                        teamCode = activeCode,
                        fullName = fullName,
                        phone = phoneStr,
                        country = countryStr,
                        language = langStr,
                        ministryRole = activeMinistry,
                        photoUri = activePhoto
                    )
                    SessionManager.saveSessionProfile(preferences, profileObj, displayName, emailStr)
                    TeamRepository.updateUserProfile(profileObj)
                    toast("Profile saved successfully");navigate(profileReturnPage)
                }
            }
        }
    }
    private fun applyProfilePhoto(id: Int) {
        val uri = preferences.getString("photoUri", null)?.takeIf { it.isNotBlank() } ?: return
        val iv = findViewById<ImageView>(id) ?: return
        ImageLoader.loadProfilePhoto(iv, uri)
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int,resultCode: Int,data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (requestCode == 301 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}

            val currentCode = preferences.getString("teamCode", "").orEmpty()
            val currentUid = preferences.getString(SessionManager.KEY_USER_ID, "").orEmpty()
            val currentPhoto = preferences.getString("photoUri", "").orEmpty()
            val localMsgId = java.util.UUID.randomUUID().toString()

            val rawBytes = try {
                contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (_: Exception) { null }

            val localBitmap = if (rawBytes != null && rawBytes.isNotEmpty()) {
                ImageLoader.decodeSampledBitmap(rawBytes, 800)
            } else null

            if (localBitmap != null) {
                ImageLoader.preseedCache(this, uri.toString(), localBitmap, circular = false)
            }

            if (chatMessages.isEmpty()) {
                val cached = TeamRepository.getCachedChatMessages(currentCode, preferences)
                if (cached.isNotEmpty()) chatMessages.addAll(cached)
            }

            val repId = replyingToMessage?.id.orEmpty()
            val repSender = if (repId.isNotBlank()) replyingToMessage?.senderName.orEmpty().ifBlank { "Member" } else ""
            val repText = if (repId.isNotBlank()) {
                replyingToMessage?.text?.takeIf { it.isNotBlank() }
                    ?: if (replyingToMessage?.mediaUrl?.isNotBlank() == true) "📷 Photo" else "Message"
            } else ""
            replyingToMessage = null
            chatReplyBar?.visibility = View.GONE

            val localMsg = ChatMessage(
                id = localMsgId,
                teamCode = currentCode,
                senderUid = currentUid,
                senderName = fullName.ifBlank { displayName.ifBlank { "Member" } },
                senderRole = preferences.getString("ministryRole", "Member").orEmpty(),
                senderPhotoUri = currentPhoto,
                mediaUrl = uri.toString(),
                timestamp = System.currentTimeMillis(),
                replyToId = repId,
                replyToSender = repSender,
                replyToText = repText
            )
            chatMessages.add(localMsg)
            TeamRepository.saveChatMessageLocally(currentCode, localMsg, preferences)
            teamTab = "Team Chat"
            if (chatMessagesCol != null && page == "teams" && teamTab == "Team Chat") {
                appendChatMessageBubble(localMsg, currentUid)
            } else {
                navigate("teams")
            }

            toast("Uploading photo to chat...")
            CloudinaryUploader.uploadProfileImage(this, uri, maxDimension = 800) { result ->
                result.onSuccess { remoteUrl ->
                    if (TeamRepository.isMessageRetracted(localMsgId) ||
                        chatMessages.any { it.id == localMsgId && it.isDeleted }
                    ) {
                        return@onSuccess
                    }
                    if (localBitmap != null) {
                        ImageLoader.preseedCache(this, remoteUrl, localBitmap, circular = false)
                    }
                    val remoteMsg = localMsg.copy(mediaUrl = remoteUrl)
                    val idx = chatMessages.indexOfFirst { it.id == localMsgId }
                    if (idx >= 0) {
                        chatMessages[idx] = remoteMsg
                    }
                    TeamRepository.sendChatMessage(currentCode, remoteMsg, preferences)
                }.onFailure { err ->
                    toast("Cloud upload failed: ${err.message ?: "Offline"}. Kept locally.")
                }
            }
            return
        }
        if(requestCode==300 && resultCode==RESULT_OK) {
            val uri=data?.data ?: return
            try {
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch(_: Exception) {}
                val localUriStr = uri.toString()
                val currentEmail = preferences.getString(SessionManager.KEY_EMAIL, "").orEmpty()
                val uid = preferences.getString(SessionManager.KEY_USER_ID, "").orEmpty()
                SessionManager.updatePhotoUri(preferences, localUriStr, currentEmail, uid)
                if(page=="profile") applyProfilePhoto(UI.profileAvatar)

                toast("Uploading photo...")
                CloudinaryUploader.uploadProfileImage(this, uri) { result ->
                    result.onSuccess { remoteUrl ->
                        toast("Profile photo uploaded to Cloudinary!")
                        SessionManager.updatePhotoUri(preferences, remoteUrl, currentEmail, uid)
                        val activeCode = preferences.getString(SessionManager.KEY_TEAM_CODE, "").orEmpty()
                        val activeRole = preferences.getString(SessionManager.KEY_ROLE, "Member").orEmpty()
                        val activeMinistry = preferences.getString(SessionManager.KEY_MINISTRY_ROLE, "").orEmpty()
                        val currentProfile = com.example.syncedn.data.model.UserProfile(
                            uid = uid,
                            username = displayName,
                            email = currentEmail,
                            role = activeRole,
                            teamName = teamName,
                            teamCode = activeCode,
                            fullName = fullName,
                            phone = preferences.getString(SessionManager.KEY_PHONE, "").orEmpty(),
                            country = preferences.getString(SessionManager.KEY_COUNTRY, "Philippines").orEmpty(),
                            language = preferences.getString(SessionManager.KEY_LANGUAGE, "English").orEmpty(),
                            ministryRole = activeMinistry,
                            photoUri = remoteUrl
                        )
                        TeamRepository.updateUserProfile(currentProfile)
                        if (page == "profile") applyProfilePhoto(UI.profileAvatar)
                        if (page == "home") applyProfilePhoto(UI.profileButton)
                        if (page == "settings") applyProfilePhoto(UI.settingsAvatar)
                    }.onFailure { err ->
                        toast("Cloud upload failed: ${err.message ?: "Network error"}. Saved locally.")
                    }
                }
            } catch(_: Exception) {toast("Could not use that photo. Try another image.")}
        }
    }
    // ---- MEMBER PAGES: shared 20dp margins, flexible rows, scrollable content. ----
    private var scheduleFilter = "Upcoming"
    private var teamTab = "Team Chat"
    private var expandedRole: String? = null
    private var selectedSong = "Trust in God"
    private var sheetAutoScroll: SheetAutoScrollControls? = null
    private var songMetronome: MetronomeView? = null
    private var songDetailsBack = "songs"
    private var selectedSheetService: org.json.JSONObject? = null
    private var selectedService = "Sunday Worship Service"
    private var serviceReturnPage = "schedule"
    private var calendarYear = 2026
    private var calendarMonth = 9 // Calendar months start at zero.
    private var calendarDay = 4
    private var substitute = "John Doe"
    private var selectedSubstituteUid: String = ""
    private var selectedSubstituteName: String = ""
    private var songsReturnPage = "home"
    private val chatMessages = mutableListOf<ChatMessage>()
    private var replyingToMessage: ChatMessage? = null
    private var chatReplyBar: View? = null
    private var chatReplySenderText: TextView? = null
    private var chatReplySnippetText: TextView? = null
    private var chatMessageInput: EditText? = null

    private fun startReplyingTo(msg: ChatMessage) {
        replyingToMessage = msg
        val repSender = msg.senderName.ifBlank { "Member" }
        val repSnippet = msg.text.takeIf { it.isNotBlank() }
            ?: if (msg.mediaUrl.isNotBlank()) "📷 Photo" else "Message"
        chatReplySenderText?.text = "Replying to $repSender"
        chatReplySnippetText?.text = repSnippet
        chatReplyBar?.visibility = View.VISIBLE
        chatMessageInput?.requestFocus()
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        chatMessageInput?.let { imm?.showSoftInput(it, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT) }
    }
    private var chatListener: ListenerRegistration? = null
    private var chatScrollView: StableScrollView? = null
    private var chatMessagesCol: LinearLayout? = null
    private var chatEmptyView: View? = null
    private val lineupSongs = mutableListOf("Amazing Grace", "Way Maker", "Goodness of God", "Build My Life")
    private var teamRoster: List<TeamMember> = emptyList()

    private fun handleTeamDeletedRemotely() {
        if (isFinishing || isDestroyed) return
        val currentCode = preferences.getString("teamCode", "").orEmpty()
        if (currentCode.isBlank() && page == "teamChoice") return
        toast("The team has been deleted by the leader.")
        val uid = currentUserId()
        if (uid.isNotBlank()) {
            FirestoreManager.clearUserTeam(uid)
        }
        preferences.edit {
            remove("teamCode")
            remove("teamName")
            remove(SessionManager.KEY_TEAM_CODE)
            remove("saved_team_code")
            remove("saved_team_name")
            remove("last_active_team_code")
            remove("ministryRole")
        }
        teamRoster = emptyList()
        navigate("teamChoice")
    }

    private fun getActiveRoster(): List<TeamMember> {
        val currentCode = preferences.getString("teamCode", "").orEmpty()
            .ifBlank { FirestoreManager.findCodeByTeamName(teamName).orEmpty() }
        val memberRolesMap = try {
            org.json.JSONObject(preferences.getString(SessionManager.KEY_MEMBER_ROLES, "{}").orEmpty())
        } catch (_: Exception) { org.json.JSONObject() }

        fun applyOverrides(list: List<TeamMember>): List<TeamMember> {
            val selfPhoto = preferences.getString("photoUri", null)?.takeIf { it.isNotBlank() }
            val selfUid = preferences.getString(SessionManager.KEY_USER_ID, "").orEmpty()
            val selfEmail = preferences.getString("email", "").orEmpty().trim().lowercase()
            val selfMinistry = preferences.getString("ministryRole", "").orEmpty()
            val selfRole = preferences.getString("role", "").orEmpty()
            return list.map { m ->
                val emailLower = m.email.trim().lowercase()
                val isSelf = (selfUid.isNotBlank() && m.uid == selfUid) ||
                             (selfEmail.isNotBlank() && emailLower == selfEmail) ||
                             (fullName.isNotBlank() && m.fullName.equals(fullName, ignoreCase = true)) ||
                             (displayName.isNotBlank() && (m.username.equals(displayName, ignoreCase = true) || m.fullName.equals(displayName, ignoreCase = true)))
                val transferred = preferences.getBoolean("transferred_leadership_$currentCode", false)
                val designatedLeaderUid = preferences.getString("team_leader_uid_$currentCode", "").orEmpty().trim()
                val designatedLeader = preferences.getString("team_leader_email_$currentCode", "").orEmpty().trim().lowercase()
                val isPrimaryLeader = (designatedLeaderUid.isNotBlank() && m.uid == designatedLeaderUid) ||
                                      (designatedLeader.isNotBlank() && designatedLeader == emailLower) ||
                                      (!transferred && designatedLeader.isBlank() && designatedLeaderUid.isBlank() && (emailLower == "ryanlloydgenturo@gmail.com" || m.fullName.equals("Ryan Lloyd Genturo", ignoreCase = true)) &&
                                       !preferences.getString("email_role_${currentCode}_$emailLower", "").equals("Member", true) &&
                                       !preferences.getString("user_role_${currentCode}_${m.uid}", "").equals("Member", true))
                val isExplicitlyMember = !isPrimaryLeader && (
                    (isSelf && (selfRole.equals("Member", true) || !selfMinistry.equals("Substitute Leader", true))) ||
                    preferences.getString("user_role_${currentCode}_${m.uid}", "").equals("Member", true) ||
                    preferences.getString("email_role_${currentCode}_$emailLower", "").equals("Member", true) ||
                    (transferred && (emailLower == "ryanlloydgenturo@gmail.com" || m.fullName.equals("Ryan Lloyd Genturo", ignoreCase = true)))
                )

                val override = if (isSelf && selfMinistry.isNotBlank() && !selfMinistry.equals("Substitute Leader", ignoreCase = true)) {
                    selfMinistry
                } else {
                    memberRolesMap.optString(m.uid).ifBlank {
                        memberRolesMap.optString(emailLower).ifBlank {
                            memberRolesMap.optString(m.fullName).ifBlank {
                                memberRolesMap.optString(m.username).ifBlank {
                                    preferences.getString("email_ministry_$emailLower", "").orEmpty()
                                }
                            }
                        }
                    }
                }
                val rawLiveRole = if (isSelf && selfMinistry.isNotBlank() && !selfMinistry.equals("Substitute Leader", ignoreCase = true)) {
                    selfMinistry
                } else if (m.ministryRole.isNotBlank() && !m.ministryRole.equals("Substitute Leader", ignoreCase = true)) {
                    m.ministryRole
                } else {
                    m.ministryRole.takeIf { it.isNotBlank() } ?: override
                }
                val liveRole = if (isExplicitlyMember && rawLiveRole.equals("Substitute Leader", ignoreCase = true)) {
                    if (isSelf && selfMinistry.isNotBlank() && !selfMinistry.equals("Substitute Leader", true)) selfMinistry else "Member"
                } else rawLiveRole

                var updated = if (liveRole.isNotBlank()) {
                    val isOverrideLeader = when {
                        isPrimaryLeader -> true
                        isExplicitlyMember -> false
                        isSelf && !selfRole.equals("Leader", true) && !selfMinistry.equals("Substitute Leader", true) -> false
                        else -> liveRole.equals("Substitute Leader", ignoreCase = true) ||
                                liveRole.equals("Worship Leader", ignoreCase = true)
                    }
                    m.copy(ministryRole = liveRole, role = if (isOverrideLeader) "Leader" else "Member")
                } else m
                if (updated.photoUri.isBlank()) {
                    val p = preferences.getString("user_photo_${m.uid}", "").orEmpty().ifBlank {
                        preferences.getString("email_photo_${emailLower}", "").orEmpty().ifBlank {
                            if (selfPhoto != null && (
                                updated.uid == selfUid ||
                                updated.fullName.equals(fullName, ignoreCase = true) ||
                                updated.fullName.equals(displayName, ignoreCase = true) ||
                                updated.username.equals(displayName, ignoreCase = true)
                            )) selfPhoto else ""
                        }
                    }
                    if (p.isNotBlank()) updated = updated.copy(photoUri = p)
                }
                if (updated.unavailableRanges.isEmpty() && isSelf) {
                    val selfRanges = TeamRepository.getMemberUnavailableRanges(
                        uid = updated.uid,
                        preferences = preferences,
                        email = emailLower,
                        name = updated.fullName.ifBlank { updated.username }
                    )
                    if (selfRanges.isNotEmpty()) {
                        updated = updated.copy(unavailableRanges = selfRanges)
                    }
                }
                updated
            }
        }

        val cached = TeamRepository.getCachedRoster(currentCode, preferences)
        val rawList = if (cached.isNotEmpty()) cached
        else if (teamRoster.isNotEmpty()) teamRoster
        else {
            val currentUid = preferences.getString("userId", "").orEmpty()
            val currentName = fullName.ifBlank { displayName }
            val userRole = preferences.getString("role", "Member").orEmpty()
            val ministry = preferences.getString("ministryRole", "Member").orEmpty()
            if (currentName.isNotBlank() && currentCode.isNotBlank()) {
                val self = TeamMember(
                    uid = currentUid,
                    fullName = currentName,
                    username = displayName,
                    email = preferences.getString("email", "").orEmpty(),
                    role = userRole,
                    ministryRole = ministry,
                    teamCode = currentCode,
                    photoUri = preferences.getString("photoUri", "").orEmpty()
                )
                listOf(self)
            } else {
                ministryMembers.map { (name, role, badge) ->
                    TeamMember(
                        uid = name.lowercase().replace(" ", "_"),
                        fullName = name,
                        username = name.split(" ").firstOrNull() ?: name,
                        email = "${name.lowercase().replace(" ", "")}@syncedn.org",
                        role = if (badge == "Admin") "Leader" else "Member",
                        ministryRole = role,
                        teamCode = currentCode
                    )
                }
            }
        }
        val withLeader = TeamRepository.ensureTeamLeaderInRoster(currentCode, rawList, preferences)
        return TeamRepository.deduplicateRoster(applyOverrides(withLeader), preferences)
    }



    private val rosterRefreshGate = RosterRefreshGate()
    private var screenRoot: View? = null
    private var homeMemberCount: TextView? = null

    private fun activeTeamCode(): String = preferences.getString("teamCode", "").orEmpty()
        .ifBlank { FirestoreManager.findCodeByTeamName(teamName).orEmpty() }
        .trim().uppercase(java.util.Locale.ROOT)

    private fun primaryScroll(view: View?): StableScrollView? {
        if (view is StableScrollView) return view
        if (view is android.view.ViewGroup) {
            for (index in 0 until view.childCount) {
                primaryScroll(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun updateRosterViews() {
        if (page == "home") {
            // Updating one label preserves the current gesture, fling and scroll offset.
            val count = getActiveRoster().size
            val label = "$count Member${if (count == 1) "" else "s"}"
            homeMemberCount?.let { if (it.text.toString() != label) it.text = label }
        } else if ((page == "teams" && teamTab != "Team Chat") || page == "calendar" || page == "teamDetails") {
            val targetPage = page
            val oldRoot = screenRoot
            val oldScroll = primaryScroll(oldRoot)
            if (oldScroll != null) {
                oldScroll.whenIdle {
                    if (screenRoot === oldRoot && page == targetPage) {
                        val position = oldScroll.scrollY
                        // Render updated rows once, without starting another roster request.
                        navigate(targetPage, triggerRefresh = false)
                        primaryScroll(screenRoot)?.let { scroll ->
                            scroll.doOnLayout { scroll.scrollTo(0, position) }
                        }
                    }
                }
            } else if (screenRoot === oldRoot && page == targetPage) {
                navigate(targetPage, triggerRefresh = false)
            }
        }
    }

    private fun refreshTeamRoster(force: Boolean = false) {
        val currentCode = activeTeamCode()
        val ticket = rosterRefreshGate.begin(currentCode)
        if (ticket == -1L && !force) return
        TeamRepository.syncFromPreferences(preferences, currentCode)
        val previous = getActiveRoster().sortedBy { it.uid }
        TeamRepository.getTeamRoster(currentCode, force, preferences) { result ->
            runOnUiThread {
                if ((ticket == -1L || rosterRefreshGate.finish(ticket)) && !isFinishing && !isDestroyed && activeTeamCode() == currentCode) {
                    result.getOrNull()?.let { list ->
                        teamRoster = list
                        if (getActiveRoster().sortedBy { it.uid } != previous) updateRosterViews()
                    }
                }
            }
        }
    }

    private var isRefreshingServices = false
    private var lastServicesRefreshTime = 0L

    private fun refreshTeamServices(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (isRefreshingServices) return
        if (!force && (now - lastServicesRefreshTime < 15_000L)) return

        val currentCode = preferences.getString("teamCode", "").orEmpty()
            .ifBlank { FirestoreManager.findCodeByTeamName(teamName).orEmpty() }
        if (currentCode.isBlank()) return

        isRefreshingServices = true
        lastServicesRefreshTime = now

        val previousStored = storedServices().toString()

        TeamRepository.getServices(currentCode, isLeader = false, preferences) { result ->
            isRefreshingServices = false
            result.getOrNull()?.let { list ->
                com.example.syncedn.data.notification.ScheduleNotificationScheduler.scheduleAll(this, storedServices())
                val currentStored = storedServices().toString()
                if (currentStored != previousStored) {
                    runOnUiThread {
                        if (page == "schedule") {
                            navigate(page, triggerRefresh = false)
                        }
                    }
                }
            }
        }
    }

    private val ministryMembers = listOf(
        Triple("Joshua Lagare", "Worship Leader", "Member"),
        Triple("Ryan Lloyd Genturo", "Keyboardist", "Leader"),
        Triple("Ramirez John Micheal", "Vocalist", "Member"),
        Triple("Jessa Mae Dizon", "Vocalist", "Member"),
        Triple("Justine Dalay Pendre", "Bassist", "Member"),
        Triple("Angelo Daniel", "Guitarist", "Member"))
    private fun memberButton(label: String, color: Int = purple, action: () -> Unit): View =
        button(View.NO_ID, label, -1).apply {
            background = bg(color, 16)
            setOnClickListener { action() }
            minimumHeight = dp(48)
        }
    private fun outlineButton(label: String, strokeColor: Int = Color.rgb(217, 70, 239), action: () -> Unit): View =
        Button(this).apply {
            text = label
            isAllCaps = false
            background = bg(Color.WHITE, 12, strokeColor)
            setTextColor(strokeColor)
            setOnClickListener { action() }
            minimumHeight = dp(48)
        }
    private fun dualButtons(leftLabel: String, onLeft: () -> Unit, rightLabel: String, onRight: () -> Unit): View =
        horizontal().apply {
            add(outlineButton(leftLabel, Color.rgb(217, 70, 239), onLeft), 0, 48, 1f)
            add(View(this@MemberActivity), 16, 1)
            add(memberButton(rightLabel, purple, onRight), 0, 48, 1f)
        }
    private fun panel(stroke: Int? = Color.rgb(215, 215, 220)) = vertical().apply {
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = bg(Color.WHITE, 16, stroke)
        elevation = dp(3).toFloat()
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        clipToOutline = true
    }
    private fun memberPage(title: String, back: String, tab: String? = null,
                           footer: (() -> View?)? = null,
                           body: (LinearLayout) -> Unit): View {
        val root = vertical().apply { setBackgroundColor(Color.WHITE) }
        val header = settingsHeader(title, true)
        header.findViewById<View>(UI.pageBack).setOnClickListener { navigate(back) }
        root.add(header)
        val scroll = StableScrollView(this).apply { isFillViewport = true; clipToPadding = true }
        val col = vertical().apply { setPadding(dp(20), dp(24), dp(20), dp(24)) }
        scroll.addView(col, FrameLayout.LayoutParams(-1, -2))
        body(col)
        val swipeRefresh = androidx.swiperefreshlayout.widget.SwipeRefreshLayout(this).apply {
            setColorSchemeColors(purple, navy)
            addView(scroll, ViewGroup.LayoutParams(-1, -1))
            setOnRefreshListener {
                val currentCode = preferences.getString("teamCode", "").orEmpty()
                if (currentCode.isNotBlank()) {
                    refreshTeamRoster(force = true)
                    refreshTeamServices(force = true)
                    TeamRepository.getSongs(currentCode, true, preferences) {}
                    TeamRepository.getAnnouncements(currentCode, true, preferences) {}
                    TeamRepository.getSubstitutions(currentCode, true, preferences) {}
                }
                postDelayed({
                    isRefreshing = false
                    navigate(page)
                }, 800)
            }
        }
        root.add(swipeRefresh, -1, 0, 1f)
        footer?.invoke()?.let { root.add(it) }
        if (tab != null) root.add(memberNavigation(tab), -1, 64)
        return root
    }
    private fun memberNavigation(tab: String): View = bottomNavigation(tab).apply {
        findViewById<View>(UI.homeTab).setOnClickListener { navigate("home") }
        findViewById<View>(UI.scheduleTab).setOnClickListener { navigate("schedule") }
        findViewById<View>(UI.teamsTab).setOnClickListener { navigate("teams") }
        findViewById<View>(UI.settingsTab).setOnClickListener { navigate("settings") }
    }
    private fun tabRow(labels: List<String>, active: String, onSelect: (String) -> Unit): View =
        horizontal().apply {
            labels.forEachIndexed { index, label ->
                val isActive = label == active
                val tab = text(label, 12, if (isActive) Color.WHITE else navy,
                    bold = true, centered = true).apply {
                    background = bg(if (isActive) navy else Color.WHITE, 24,
                        if (isActive) null else Color.rgb(220, 220, 225))
                    setPadding(dp(3), dp(8), dp(3), dp(8))
                    isClickable = true; isFocusable = true
                    setOnClickListener { onSelect(label) }
                }
                val lp = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    if (index > 0) leftMargin = dp(8)
                }
                addView(tab, lp)
            }
        }
    private fun memberAvatar(): View = object : View(this) {
        private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
        init { contentDescription = "Member avatar" }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val size = minOf(width, height).toFloat()
            val centerX = width / 2f; val centerY = height / 2f
            ink.color = Color.rgb(154, 211, 255)
            canvas.drawCircle(centerX, centerY, size / 2f, ink)
            ink.color = Color.rgb(82, 178, 243)
            canvas.drawCircle(centerX, centerY - size * 0.13f, size * 0.19f, ink)
            canvas.drawOval(centerX - size * 0.28f, centerY + size * 0.12f,
                centerX + size * 0.28f, centerY + size * 0.42f, ink)
        }
    }
    private fun findMemberPhotoUri(uid: String? = null, name: String? = null, email: String? = null): String {
        if (!uid.isNullOrBlank()) {
            val p = preferences.getString("user_photo_$uid", "").orEmpty()
            if (p.isNotBlank()) return p
        }
        if (!email.isNullOrBlank()) {
            val p = preferences.getString("email_photo_${email.trim().lowercase()}", "").orEmpty()
            if (p.isNotBlank()) return p
        }
        val roster = getActiveRoster()
        val match = roster.find {
            (it.uid.isNotBlank() && uid != null && it.uid == uid) ||
            (it.email.isNotBlank() && email != null && it.email.equals(email, true)) ||
            (it.fullName.isNotBlank() && name != null && it.fullName.equals(name, true)) ||
            (it.username.isNotBlank() && name != null && it.username.equals(name, true))
        }
        if (!match?.photoUri.isNullOrBlank()) return match!!.photoUri
        val currentUid = preferences.getString(SessionManager.KEY_USER_ID, "").orEmpty()
        val currentEmail = preferences.getString(SessionManager.KEY_EMAIL, "").orEmpty()
        val currentName = fullName.ifBlank { displayName }
        if ((uid != null && uid == currentUid) ||
            (email != null && email.equals(currentEmail, true)) ||
            (name != null && (name.equals(currentName, true) || name.equals(displayName, true)))) {
            return preferences.getString(SessionManager.KEY_PHOTO_URI, "").orEmpty().ifBlank {
                preferences.getString("photoUri", "").orEmpty()
            }
        }
        return ""
    }
    private fun memberAvatarView(photoUri: String? = null, sizeDp: Int = 44): View {
        val uri = photoUri?.takeIf { it.isNotBlank() }
        val cached = if (uri != null) ImageLoader.getCachedProfileBitmap(this, uri) else null
        if (cached != null) {
            return ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(cached)
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setOval(0, 0, view.width, view.height)
                    }
                }
                clipToOutline = true
                contentDescription = "Member avatar"
            }
        } else if (uri != null) {
            val iv = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setOval(0, 0, view.width, view.height)
                    }
                }
                clipToOutline = true
                contentDescription = "Member avatar"
            }
            ImageLoader.loadProfilePhoto(iv, uri)
            return iv
        }
        return memberAvatar()
    }
    private fun memberRow(
        name: String,
        role: String,
        badge: String? = null,
        onClick: (() -> Unit)? = null,
        photoUri: String? = null,
        uid: String? = null
    ): View = horizontal().apply {
        setPadding(0, dp(8), 0, dp(8))
        val resolvedPhoto = photoUri?.takeIf { it.isNotBlank() } ?: findMemberPhotoUri(uid, name)
        val avatar = memberAvatarView(resolvedPhoto, 48).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { showMemberProfileDialog(uid, name, role, resolvedPhoto) }
        }
        add(avatar, 48, 48)

        val info = vertical().apply {
            setPadding(dp(12), 0, dp(8), 0)
            val nameView = text(name, 13, navy, true).apply {
                isClickable = true
                isFocusable = true
                setOnClickListener { showMemberProfileDialog(uid, name, role, resolvedPhoto) }
            }
            add(nameView)
            space(3)
            val roleView = text(role, 12, Color.DKGRAY)
            add(roleView)
        }
        add(info, 0, -2, 1f)

        if (badge != null) {
            val badgeView = text(badge, 11, navy, true, true).apply {
                if (badge == "Leader" || badge == "Admin") background = bg(Color.rgb(225, 225, 228), 14)
            }
            add(badgeView, 64, 30)
        }
        onClick?.let { action ->
            isClickable = true; isFocusable = true
            setOnClickListener { action() }
        }
    }
    private data class ScheduleCardItem(
        val title: String,
        val date: String,
        val rawStart: String,
        val time: String,
        val venue: String,
        val isRehearsal: Boolean,
        val isPast: Boolean,
        val onClick: () -> Unit
    )

    private fun normalizeToIsoDate(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.matches(Regex("""^\d{4}-\d{2}-\d{2}$"""))) return trimmed
        val tz = java.util.TimeZone.getTimeZone("Asia/Manila")
        val patterns = listOf(
            "MMM d, yyyy",
            "MMM dd, yyyy",
            "MMMM d, yyyy",
            "MMMM dd, yyyy",
            "d MMM yyyy",
            "dd MMM yyyy",
            "yyyy/MM/dd",
            "MM/dd/yyyy",
            "M/d/yyyy"
        )
        for (pat in patterns) {
            try {
                val sdf = java.text.SimpleDateFormat(pat, java.util.Locale.US).apply {
                    timeZone = tz
                    isLenient = false
                }
                val parsed = sdf.parse(trimmed)
                if (parsed != null) {
                    return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
                        timeZone = tz
                    }.format(parsed)
                }
            } catch (_: Exception) {}
        }
        return trimmed
    }

    private fun isEventPast(dateStr: String, endTimeStr: String, startTimeStr: String = ""): Boolean {
        if (dateStr.isBlank()) return false
        val isoDate = normalizeToIsoDate(dateStr)
        val tz = java.util.TimeZone.getTimeZone("Asia/Manila")
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
            timeZone = tz
        }.format(java.util.Date())

        if (isoDate < today) return true
        if (isoDate > today) return false

        val cal = java.util.Calendar.getInstance(tz)
        val currentMinutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val effEnd = if (endTimeStr.contains("–") || endTimeStr.contains("-")) {
            endTimeStr.split(Regex("""[–—\-]""")).last().trim()
        } else {
            endTimeStr.trim()
        }
        val effStart = if (startTimeStr.contains("–") || startTimeStr.contains("-")) {
            startTimeStr.split(Regex("""[–—\-]""")).first().trim()
        } else {
            startTimeStr.trim()
        }
        val timeToParse = effEnd.ifBlank { effStart }
        if (timeToParse.isBlank()) return false

        val eventMinutes = parseTimeToMinutes(timeToParse) ?: return false
        val thresholdMinutes = if (effEnd.isBlank()) eventMinutes + 120 else eventMinutes
        return currentMinutes >= thresholdMinutes
    }

    private fun parseTimeToMinutes(raw: String): Int? {
        return try {
            val trimmed = raw.trim().uppercase()
            val target = if (trimmed.contains("–") || trimmed.contains("-")) {
                trimmed.split(Regex("""[–—\-]""")).last().trim()
            } else trimmed
            if (target.contains("AM") || target.contains("PM")) {
                val isPm = target.contains("PM")
                val clean = target.replace("AM", "").replace("PM", "").trim()
                val parts = clean.split(":")
                var hour = parts[0].trim().toInt()
                val minute = if (parts.size > 1) parts[1].trim().toInt() else 0
                if (isPm && hour < 12) hour += 12
                if (!isPm && hour == 12) hour = 0
                hour * 60 + minute
            } else if (target.contains(":")) {
                val parts = target.split(":")
                val hour = parts[0].trim().toInt()
                val minute = if (parts.size > 1) parts[1].trim().toInt() else 0
                hour * 60 + minute
            } else null
        } catch (_: Exception) { null }
    }

    private fun scheduleView(): View {
        val root = vertical().apply {
            setBackgroundColor(Color.WHITE)
            clipChildren = true
            clipToPadding = true
        }
        val header = settingsHeader("Schedule", true)
        header.findViewById<View>(UI.pageBack).setOnClickListener { navigate("home") }
        root.add(header)
        val topNav = vertical().apply {
            setPadding(dp(20), dp(16), dp(20), dp(8))
            add(memberButton("Calendar / Manage Availability", navy) { navigate("calendar") }, -1, 48)
            space(16)
            add(tabRow(listOf("Upcoming", "Past", "All"), scheduleFilter) {
                scheduleFilter = it; navigate("schedule")
            })
        }
        root.add(topNav)
        val scroll = StableScrollView(this).apply { isFillViewport = true; clipToPadding = true }
        val col = vertical().apply { setPadding(dp(20), dp(8), dp(20), dp(24)) }
        scroll.addView(col, FrameLayout.LayoutParams(-1, -2))
        val upcoming = listOf(Triple("SEPT\n6", "Sunday Worship Service", "6:00AM – 11:00AM"),
            Triple("SEPT\n9", "Rehearsal", "7:00PM – 9:00PM"),
            Triple("SEPT\n13", "Sunday Worship Service", "6:00AM – 11:00AM"),
            Triple("SEPT\n15", "Team Meeting", "7:00PM – 8:00PM"),
            Triple("SEPT\n16", "Rehearsal", "7:00PM – 9:00PM"))
        val past = listOf(Triple("AUG\n30", "Sunday Worship Service", "6:00AM – 11:00AM"),
            Triple("AUG\n26", "Rehearsal", "7:00PM – 9:00PM"),
            Triple("AUG\n23", "Sunday Worship Service", "6:00AM – 11:00AM"),
            Triple("AUG\n21", "Team Meeting", "7:00PM – 8:00PM"),
            Triple("AUG\n19", "Rehearsal", "7:00PM – 9:00PM"))
        val entries = when (scheduleFilter) { "Past" -> past; "All" -> upcoming + past; else -> upcoming }

        val realServices = storedServices()
        val activeCode = preferences.getString("teamCode", "").orEmpty()

        val cardItems = mutableListOf<ScheduleCardItem>()
        realServices.forEach { record ->
            val title = record.optString("title", "Sunday Worship Service")
            val date = record.optString("date")
            val start = record.optString("start")
            val end = record.optString("end")
            val time = if (start.isNotBlank() && end.isNotBlank()) "${formatServiceTime(start)} – ${formatServiceTime(end)}" else if (start.isNotBlank()) formatServiceTime(start) else "6:00AM – 11:00AM"
            val venue = record.optString("venue", "Main Sanctuary")
            val isPast = isEventPast(date, end, start)

            cardItems.add(ScheduleCardItem(
                title = title,
                date = date,
                rawStart = start,
                time = time,
                venue = venue,
                isRehearsal = false,
                isPast = isPast,
                onClick = {
                    selectSheetService(record)
                    selectedService = title; serviceReturnPage = "schedule"
                    preferences.edit().putString("serviceTime", time).putString("serviceDate", date).putString("serviceVenue", venue).apply()
                    navigate("service")
                }
            ))

            val rehObj = record.optJSONObject("rehearsal")
            val rDate = rehObj?.optString("date").orEmpty().ifBlank { record.optString("rehearsalDate").orEmpty() }
            if (rDate.isNotBlank()) {
                val rStart = rehObj?.optString("start").orEmpty().ifBlank { record.optString("rehearsalStart").orEmpty() }
                val rEnd = rehObj?.optString("end").orEmpty().ifBlank { record.optString("rehearsalEnd").orEmpty() }
                val rTime = if (rStart.isNotBlank() && rEnd.isNotBlank()) "${formatServiceTime(rStart)} – ${formatServiceTime(rEnd)}"
                            else if (rStart.isNotBlank()) formatServiceTime(rStart) else "7:00AM – 9:00AM"
                val rVenue = rehObj?.optString("venue").orEmpty()
                    .ifBlank { record.optString("rehearsalVenue").orEmpty() }
                    .ifBlank { venue }
                val rIsPast = isEventPast(rDate, rEnd.ifBlank { rTime }, rStart)

                cardItems.add(ScheduleCardItem(
                    title = "$title (Rehearsal)",
                    date = rDate,
                    rawStart = rStart,
                    time = rTime,
                    venue = "$rVenue • Rehearsal",
                    isRehearsal = true,
                    isPast = rIsPast,
                    onClick = {
                        selectSheetService(record)
                        selectedService = title; serviceReturnPage = "schedule"
                        preferences.edit().putString("serviceTime", rTime).putString("serviceDate", rDate).putString("serviceVenue", rVenue).apply()
                        navigate("service")
                    }
                ))
            }
        }

        val sortedReal = cardItems.sortedWith(compareBy({ it.date }, { it.rawStart }))
        val filteredReal = sortedReal.filter { item ->
            when (scheduleFilter) {
                "Past" -> item.isPast
                "Upcoming" -> !item.isPast
                else -> true
            }
        }

        if (realServices.isNotEmpty() || activeCode.isNotBlank()) {
            if (filteredReal.isEmpty()) {
                col.add(panel().apply {
                    add(text("No ${scheduleFilter.lowercase()} services scheduled yet.", 13, Color.GRAY, centered = true))
                })
                col.space(16)
            } else {
                filteredReal.forEach { item ->
                    val badgeDate = formatBadgeDate(item.date)
                    val badgeBg = if (item.isRehearsal) {
                        GradientDrawable(GradientDrawable.Orientation.TL_BR,
                            intArrayOf(Color.rgb(59, 130, 246), Color.rgb(37, 99, 235))).apply { cornerRadius = dp(14).toFloat() }
                    } else {
                        GradientDrawable(GradientDrawable.Orientation.TL_BR,
                            intArrayOf(purple, Color.rgb(167, 0, 214))).apply { cornerRadius = dp(14).toFloat() }
                    }

                    val row = horizontal().apply {
                        background = bg(Color.WHITE, 20, if (preferences.getString("serviceDate", "") == item.date) purple else Color.rgb(230, 230, 235))
                        elevation = dp(3).toFloat(); setPadding(dp(8), dp(10), dp(12), dp(10))
                        add(text(badgeDate, 15, Color.WHITE, bold = true, centered = true).apply {
                            background = badgeBg
                        }, 60, 66)
                        add(vertical().apply {
                            setPadding(dp(12), 0, dp(8), 0)
                            add(text(item.title, 13, navy, true)); space(4); add(text(item.time, 11, navy))
                            space(3); add(text(item.venue, 11, if (item.isRehearsal) Color.rgb(59, 130, 246) else navy))
                        }, 0, -2, 1f)
                        add(Symbol(this@MemberActivity, "arrow", Color.GRAY), 18, 24)
                        isClickable = true; isFocusable = true
                        setOnClickListener { item.onClick() }
                    }
                    col.add(row); col.space(16)
                }
            }
        } else {
            col.add(text("Sample schedule • dates from your design", 11, Color.GRAY)); col.space(12)
            entries.forEach { (date, title, time) ->
                val row = horizontal().apply {
                    background = bg(Color.WHITE, 20, if (preferences.getString("serviceDate", "") == date.replace("\n", " ")) purple else Color.rgb(230, 230, 235))
                    elevation = dp(3).toFloat(); setPadding(dp(8), dp(10), dp(12), dp(10))
                    add(text(date, 15, Color.WHITE, true, true).apply {
                        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                            intArrayOf(purple, Color.rgb(167, 0, 214))).apply { cornerRadius = dp(14).toFloat() }
                    }, 60, 66)
                    add(vertical().apply {
                        setPadding(dp(12), 0, dp(8), 0)
                        add(text(title, 13, navy, true)); space(4); add(text(time, 11, navy))
                        space(3); add(text(if (title == "Team Meeting") "Fellowship Hall" else "Main Sanctuary", 11, navy))
                    }, 0, -2, 1f)
                    add(Symbol(this@MemberActivity, "arrow", Color.GRAY), 18, 24)
                    isClickable = true; isFocusable = true
                    setOnClickListener {
                        selectSheetService(sampleSheetService())
                        selectedService = title; serviceReturnPage = "schedule"
                        preferences.edit().putString("serviceTime", time).putString("serviceDate", date.replace("\n", " ")).putString("serviceVenue", if(title == "Team Meeting") "Fellowship Hall" else "Main Sanctuary").apply()
                        navigate("service")
                    }
                }
                col.add(row); col.space(16)
            }
        }
        val swipeRefresh = androidx.swiperefreshlayout.widget.SwipeRefreshLayout(this).apply {
            setColorSchemeColors(navy, Color.rgb(167, 0, 213))
            setOnRefreshListener {
                refreshTeamServices(force = true)
                postDelayed({
                    isRefreshing = false
                    navigate("schedule")
                }, 900)
            }
        }
        swipeRefresh.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        root.add(swipeRefresh, -1, 0, 1f)
        root.add(memberNavigation("Schedule"), -1, 64)
        return root
    }
    private fun formatDisplayDate(raw: String): String = try {
        val sdfIn = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val sdfOut = java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.US)
        val d = sdfIn.parse(raw)
        if (d != null) sdfOut.format(d) else raw
    } catch (_: Exception) { raw }

    private fun formatRangeDisplay(startDate: String, endDate: String): String = try {
        val sdfIn = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val sdfOut = java.text.SimpleDateFormat("d MMM", java.util.Locale.US)
        val s = sdfIn.parse(startDate)
        val e = sdfIn.parse(endDate)
        if (s != null && e != null) "${sdfOut.format(s)} - ${sdfOut.format(e)}"
        else "$startDate - $endDate"
    } catch (_: Exception) { "$startDate - $endDate" }

    private fun shortMonthName(monthIdx: Int): String {
        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        return months.getOrElse(monthIdx) { "Oct" }
    }

    private fun calendarKey(day: Int = calendarDay) =
        "%04d-%02d-%02d".format(java.util.Locale.US, calendarYear, calendarMonth + 1, day)

    private fun calendarView(): View = memberPage("Calendar", "home", "Home") { col ->
        val date = java.util.Calendar.getInstance().apply {
            set(calendarYear, calendarMonth, 1, 12, 0, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
        val monthRow = horizontal()
        monthRow.add(text("‹", 28, navy, centered = true).apply {
            setOnClickListener { date.add(java.util.Calendar.MONTH, -1); setCalendarMonth(date) }
            contentDescription = "Previous month"; isFocusable = true
        }, 48, 48)
        monthRow.add(vertical().apply {
            add(text(java.text.SimpleDateFormat("MMMM", java.util.Locale.ENGLISH).format(date.time), 22, navy, true, true))
            space(4); add(text(calendarYear.toString(), 12, Color.GRAY, centered = true))
        }, 0, -2, 1f)
        monthRow.add(text("›", 28, navy, centered = true).apply {
            setOnClickListener { date.add(java.util.Calendar.MONTH, 1); setCalendarMonth(date) }
            contentDescription = "Next month"; isFocusable = true
        }, 48, 48)
        col.add(monthRow); col.space(20)
        col.add(horizontal().apply {
            listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEachIndexed { i, day ->
                add(text(day, 11, if (i == 0 || i == 6) Color.rgb(217, 70, 239) else navy, centered = true), 0, 36, 1f)
            }
        })
        val first = date.get(java.util.Calendar.DAY_OF_WEEK) - 1
        val count = date.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        val prevCal = (date.clone() as java.util.Calendar).apply { add(java.util.Calendar.MONTH, -1) }
        val prevMax = prevCal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        val rows = (first + count + 6) / 7
        val uid = currentUserId()
        val emailStr = preferences.getString("email", "").orEmpty()
        val activeRanges = TeamRepository.getMemberUnavailableRanges(uid, preferences, emailStr, fullName.ifBlank { displayName })
        val allServices = storedServices()

        repeat(rows) { week ->
            col.add(horizontal().apply {
                repeat(7) { weekday ->
                    val day = week * 7 + weekday - first + 1
                    val cell = vertical().apply { gravity = Gravity.CENTER }
                    if (day in 1..count) {
                        val key = calendarKey(day)
                        val isSel = day == calendarDay
                        cell.add(text(day.toString(), 12,
                            if (isSel) Color.WHITE else navy, centered = true).apply {
                            if (isSel) background = bg(purple, 24)
                        }, -1, 36)

                        val hasUnavailable = activeRanges.any { it.includes(key) }
                        val hasService = allServices.any { it.optString("date") == key }
                        val hasRehearsal = allServices.any {
                            it.optJSONObject("rehearsal")?.optString("date") == key || it.optString("rehearsalDate") == key
                        }

                        val dotColor = when {
                            hasUnavailable -> Color.rgb(239, 68, 68)
                            hasService -> Color.rgb(34, 197, 94)
                            hasRehearsal -> Color.rgb(59, 130, 246)
                            else -> null
                        }
                        cell.add(text(if (dotColor != null) "•" else "", 14, dotColor ?: Color.TRANSPARENT, bold = true, centered = true), -1, 14)
                        cell.isClickable = true; cell.isFocusable = true; cell.contentDescription = key
                        cell.setOnClickListener { calendarDay = day; navigate("calendar") }
                    } else if (day <= 0) {
                        val priorDay = prevMax + day
                        cell.add(text(priorDay.toString(), 12, Color.rgb(180, 185, 195), centered = true), -1, 36)
                        cell.add(text("", 14, Color.TRANSPARENT, centered = true), -1, 14)
                    } else {
                        val nextDay = day - count
                        cell.add(text(nextDay.toString(), 12, Color.rgb(180, 185, 195), centered = true), -1, 36)
                        cell.add(text("", 14, Color.TRANSPARENT, centered = true), -1, 14)
                    }
                    add(cell, 0, 56, 1f)
                }
            })
        }
        col.space(20)
        date.set(java.util.Calendar.DAY_OF_MONTH, calendarDay)
        col.add(text(java.text.SimpleDateFormat("EEEE, MMMM d", java.util.Locale.ENGLISH).format(date.time), 13, navy))
        col.space(12)

        val selectedKey = calendarKey()
        val matchingRange = activeRanges.find { it.includes(selectedKey) }
        val isUnavailable = matchingRange != null
        val matchingService = allServices.find { it.optString("date") == selectedKey }
        val isService = matchingService != null && !isUnavailable
        val matchingRehearsalService = allServices.find {
            it.optJSONObject("rehearsal")?.optString("date") == selectedKey || it.optString("rehearsalDate") == selectedKey
        }
        val isRehearsal = matchingRehearsalService != null && !isUnavailable && !isService

        val strokeColor = when {
            isUnavailable -> Color.rgb(239, 68, 68)
            isService -> Color.rgb(34, 197, 94)
            isRehearsal -> Color.rgb(59, 130, 246)
            else -> Color.rgb(226, 232, 240)
        }

        val eventTitle = when {
            isUnavailable -> "Unavailable"
            isService -> "Service"
            isRehearsal -> "Rehearsal"
            else -> "Available"
        }

        val eventSub = when {
            isUnavailable -> {
                if (matchingRange != null) {
                    val rangeLabel = formatRangeDisplay(matchingRange.startDate, matchingRange.endDate)
                    if (matchingRange.reason.isNotBlank()) "$rangeLabel • ${matchingRange.reason}" else rangeLabel
                } else ""
            }
            isService -> {
                val srv = matchingService
                val timeStr = if (srv != null && srv.optString("start").isNotBlank()) {
                    "${formatServiceTime(srv.optString("start"))} - ${formatServiceTime(srv.optString("end"))}"
                } else "8:00AM - 11:00AM"
                "${calendarDay} ${shortMonthName(calendarMonth)}, $timeStr"
            }
            isRehearsal -> {
                val rehObj = matchingRehearsalService?.optJSONObject("rehearsal")
                val rStart = rehObj?.optString("start").orEmpty().ifBlank { matchingRehearsalService?.optString("rehearsalStart").orEmpty() }
                val rEnd = rehObj?.optString("end").orEmpty().ifBlank { matchingRehearsalService?.optString("rehearsalEnd").orEmpty() }
                val timeStr = if (rStart.isNotBlank() && rEnd.isNotBlank()) "${formatServiceTime(rStart)} - ${formatServiceTime(rEnd)}"
                              else if (rStart.isNotBlank()) formatServiceTime(rStart)
                              else "7:00PM - 9:00PM"
                "${calendarDay} ${shortMonthName(calendarMonth)}, $timeStr"
            }
            else -> "No events scheduled"
        }

        val eventCard = horizontal().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = bg(Color.WHITE, 22, strokeColor)
            elevation = dp(3).toFloat()
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            add(text(eventTitle, 17, navy, bold = true), 0, -2, 1f)
            add(text(eventSub, 13, Color.rgb(100, 116, 139)), -2, -2)
            if (matchingService != null) {
                isClickable = true; isFocusable = true
                setOnClickListener {
                    selectSheetService(matchingService)
                    selectedService = matchingService.optString("title", "Sunday Worship Service")
                    serviceReturnPage = "calendar"
                    val start = matchingService.optString("start")
                    val end = matchingService.optString("end")
                    val time = if (start.isNotBlank() && end.isNotBlank()) "${formatServiceTime(start)} – ${formatServiceTime(end)}" else "8:00AM – 11:00AM"
                    preferences.edit().putString("serviceDate", matchingService.optString("date"))
                        .putString("serviceTime", time)
                        .putString("serviceVenue", matchingService.optString("venue", "Main Sanctuary")).apply()
                    navigate("service")
                }
            }
        }
        col.add(eventCard)
        col.space(16)

        val setUnavailableBtn = Button(this).apply {
            text = "Set Unavailable"
            isAllCaps = false
            background = bg(Color.rgb(80, 5, 104), 24)
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            minimumHeight = dp(48)
            setOnClickListener { navigate("availability") }
        }
        col.add(setUnavailableBtn, -1, 48)

        if (matchingRange != null) {
            col.space(12)
            col.add(memberButton("Clear This Unavailability", navy) {
                TeamRepository.removeUnavailableRange(uid, matchingRange.id, preferences)
                toast("Unavailability removed")
                navigate("calendar")
            }, -1, 48)
        }
    }
    private fun setCalendarMonth(date: java.util.Calendar) {
        calendarYear = date.get(java.util.Calendar.YEAR)
        calendarMonth = date.get(java.util.Calendar.MONTH); calendarDay = 1; navigate("calendar")
    }
    private fun dateField(initial: String): TextView = text(initial, 13, navy).apply {
        background = bg(Color.rgb(235, 235, 240), 10)
        setPadding(dp(12), 0, dp(12), 0)
        isFocusable = true; contentDescription = "Choose date"
        setOnClickListener {
            val parts = text.toString().split("-").map { it.toInt() }
            android.app.DatePickerDialog(this@MemberActivity, { _, year, month, day ->
                text = "%04d-%02d-%02d".format(java.util.Locale.US, year, month + 1, day)
            }, parts[0], parts[1] - 1, parts[2]).show()
        }
    }
    private fun plainInput(hintText: String, multiline: Boolean = false) = EditText(this).apply {
        hint = hintText; textSize = 13f; includeFontPadding = false
        setTextColor(navy); setHintTextColor(Color.GRAY)
        background = bg(Color.rgb(242, 242, 245), 10, Color.LTGRAY)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        inputType = InputType.TYPE_CLASS_TEXT or
                (if (multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        setSingleLine(!multiline)
        gravity = if (multiline) Gravity.TOP or Gravity.START else Gravity.CENTER_VERTICAL
    }
    private fun availabilityView(): View {
        val root = vertical().apply { setBackgroundColor(Color.WHITE) }
        val header = settingsHeader("Manage Schedule", true)
        header.findViewById<View>(UI.pageBack).setOnClickListener { navigate("calendar") }
        root.add(header)

        val scroll = StableScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val content = vertical()
        scroll.addView(content, FrameLayout.LayoutParams(-1, -2))

        val uid = currentUserId()
        val emailStr = preferences.getString("email", "").orEmpty()

        // Top Month Calendar view (matching Manage Schedule.png)
        val calContainer = vertical().apply { setPadding(dp(20), dp(10), dp(20), dp(10)) }
        val date = java.util.Calendar.getInstance().apply {
            set(calendarYear, calendarMonth, 1, 12, 0, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
        val monthRow = horizontal()
        monthRow.add(text("‹", 28, navy, centered = true).apply {
            setOnClickListener {
                date.add(java.util.Calendar.MONTH, -1)
                calendarYear = date.get(java.util.Calendar.YEAR)
                calendarMonth = date.get(java.util.Calendar.MONTH)
                calendarDay = 1
                navigate("availability")
            }
            contentDescription = "Previous month"; isFocusable = true
        }, 48, 48)
        monthRow.add(vertical().apply {
            add(text(java.text.SimpleDateFormat("MMMM", java.util.Locale.ENGLISH).format(date.time), 22, navy, true, true))
            space(4); add(text(calendarYear.toString(), 12, Color.GRAY, centered = true))
        }, 0, -2, 1f)
        monthRow.add(text("›", 28, navy, centered = true).apply {
            setOnClickListener {
                date.add(java.util.Calendar.MONTH, 1)
                calendarYear = date.get(java.util.Calendar.YEAR)
                calendarMonth = date.get(java.util.Calendar.MONTH)
                calendarDay = 1
                navigate("availability")
            }
            contentDescription = "Next month"; isFocusable = true
        }, 48, 48)
        calContainer.add(monthRow); calContainer.space(12)
        calContainer.add(horizontal().apply {
            listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEachIndexed { i, day ->
                add(text(day, 11, if (i == 0 || i == 6) Color.rgb(217, 70, 239) else navy, centered = true), 0, 26, 1f)
            }
        })
        val first = date.get(java.util.Calendar.DAY_OF_WEEK) - 1
        val count = date.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        val prevCal = (date.clone() as java.util.Calendar).apply { add(java.util.Calendar.MONTH, -1) }
        val prevMax = prevCal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        val rows = (first + count + 6) / 7
        val activeRanges = TeamRepository.getMemberUnavailableRanges(uid, preferences, emailStr, fullName.ifBlank { displayName })

        repeat(rows) { week ->
            calContainer.add(horizontal().apply {
                repeat(7) { weekday ->
                    val day = week * 7 + weekday - first + 1
                    val cell = vertical().apply { gravity = Gravity.CENTER }
                    if (day in 1..count) {
                        val key = calendarKey(day)
                        val isSel = day == calendarDay
                        val hasUnavail = activeRanges.any { it.includes(key) }
                        cell.add(text(day.toString(), 12,
                            when {
                                isSel -> Color.WHITE
                                hasUnavail -> Color.rgb(239, 68, 68)
                                else -> navy
                            }, bold = isSel || hasUnavail, centered = true).apply {
                            if (isSel) background = bg(purple, 13)
                            else if (hasUnavail) background = bg(Color.rgb(254, 226, 226), 13)
                        }, -1, 26)
                        cell.isClickable = true; cell.isFocusable = true; cell.contentDescription = key
                        cell.setOnClickListener { calendarDay = day; navigate("availability") }
                    } else if (day <= 0) {
                        val priorDay = prevMax + day
                        cell.add(text(priorDay.toString(), 12, Color.rgb(180, 185, 195), centered = true), -1, 26)
                    } else {
                        val nextDay = day - count
                        cell.add(text(nextDay.toString(), 12, Color.rgb(180, 185, 195), centered = true), -1, 26)
                    }
                    add(cell, 0, 26, 1f)
                }
            })
        }
        content.add(calContainer)

        // Bottom dark navy/midnight container matching uploaded design exactly (media_1792410432342_45846c86.png)
        var startIso = calendarKey(calendarDay)
        var endIso = calendarKey(calendarDay)

        val darkCard = vertical().apply {
            setPadding(dp(16), dp(20), dp(16), dp(24))
            val gradient = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(21, 29, 56), Color.rgb(46, 62, 120))).apply {
                cornerRadii = floatArrayOf(dp(32).toFloat(), dp(32).toFloat(), dp(32).toFloat(), dp(32).toFloat(), 0f, 0f, 0f, 0f)
            }
            background = gradient
        }

        darkCard.add(text("Reason", 14, Color.WHITE, bold = false)); darkCard.space(6)
        val reasonInput = EditText(this).apply {
            hint = "Family bonding"
            setHintTextColor(Color.rgb(115, 115, 115))
            setTextColor(Color.rgb(20, 27, 52))
            textSize = 14f
            background = bg(Color.rgb(217, 217, 217), 18)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            filters = arrayOf(InputFilter.LengthFilter(ValidationUtils.MAX_REASON_LENGTH))
            gravity = Gravity.TOP or Gravity.START
        }
        darkCard.add(reasonInput, -1, 72); darkCard.space(12)

        darkCard.add(text("Starts", 14, Color.WHITE, bold = false)); darkCard.space(6)
        val startDisplay = text(formatDisplayDate(startIso), 14, Color.rgb(20, 27, 52))
        val endDisplay = text(formatDisplayDate(endIso), 14, Color.rgb(20, 27, 52))
        val startRow = horizontal().apply {
            gravity = Gravity.CENTER_VERTICAL
            background = bg(Color.rgb(217, 217, 217), 21)
            setPadding(dp(16), 0, dp(16), 0)
            add(startDisplay, 0, -2, 1f)
            add(Symbol(this@MemberActivity, "calendar", Color.rgb(20, 27, 52)), 20, 20)
            isClickable = true; isFocusable = true; contentDescription = "Choose start date"
            setOnClickListener {
                val parts = startIso.split("-").map { it.toInt() }
                android.app.DatePickerDialog(this@MemberActivity, { _, year, month, day ->
                    startIso = "%04d-%02d-%02d".format(java.util.Locale.US, year, month + 1, day)
                    startDisplay.text = formatDisplayDate(startIso)
                    if (endIso < startIso) {
                        endIso = startIso
                        endDisplay.text = formatDisplayDate(endIso)
                    }
                }, parts[0], parts[1] - 1, parts[2]).show()
            }
        }
        darkCard.add(startRow, -1, 42); darkCard.space(12)

        darkCard.add(text("Ends", 14, Color.WHITE, bold = false)); darkCard.space(6)
        val endRow = horizontal().apply {
            gravity = Gravity.CENTER_VERTICAL
            background = bg(Color.rgb(217, 217, 217), 21)
            setPadding(dp(16), 0, dp(16), 0)
            add(endDisplay, 0, -2, 1f)
            isClickable = true; isFocusable = true; contentDescription = "Choose end date"
            setOnClickListener {
                val parts = endIso.split("-").map { it.toInt() }
                android.app.DatePickerDialog(this@MemberActivity, { _, year, month, day ->
                    endIso = "%04d-%02d-%02d".format(java.util.Locale.US, year, month + 1, day)
                    endDisplay.text = formatDisplayDate(endIso)
                }, parts[0], parts[1] - 1, parts[2]).show()
            }
        }
        darkCard.add(endRow, -1, 42); darkCard.space(18)

        val saveBtn = Button(this).apply {
            text = "Save"
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT
            background = bg(Color.rgb(20, 27, 52), 20)
            stateListAnimator = null
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                val rText = reasonInput.text.toString().trim().ifBlank { "Family bonding" }
                val currentRanges = TeamRepository.getMemberUnavailableRanges(uid, preferences, emailStr, fullName.ifBlank { displayName })
                when {
                    endIso < startIso -> toast("End date must be on or after the start date.")
                    currentRanges.size >= 100 -> toast("Maximum 100 unavailable ranges reached.")
                    else -> {
                        val range = UnavailableRange(
                            id = "range_${System.currentTimeMillis()}",
                            startDate = startIso,
                            endDate = endIso,
                            reason = rText
                        )
                        TeamRepository.addUnavailableRange(uid, range, preferences)
                        val parts = startIso.split("-").mapNotNull { it.toIntOrNull() }
                        if (parts.size == 3) {
                            calendarYear = parts[0]
                            calendarMonth = parts[1] - 1
                            calendarDay = parts[2]
                        }
                        toast("Unavailable range added")
                        navigate("calendar")
                    }
                }
            }
        }
        darkCard.add(saveBtn, -1, 40)

        if (activeRanges.isNotEmpty()) {
            darkCard.space(24)
            darkCard.add(text("Scheduled Unavailabilities", 14, Color.WHITE, bold = true))
            darkCard.space(10)
            activeRanges.forEach { r ->
                val p = panel(Color.rgb(30, 41, 59)).apply {
                    val row = horizontal().apply { gravity = Gravity.CENTER_VERTICAL }
                    row.add(vertical().apply {
                        add(text("📅 ${r.startDate} – ${r.endDate}", 13, Color.WHITE, true))
                        space(2)
                        add(text("Reason: ${r.reason}", 12, Color.rgb(203, 213, 225)))
                    }, 0, -2, 1f)
                    val delBtn = text("✕", 18, Color.rgb(248, 113, 113), bold = true, centered = true).apply {
                        setPadding(dp(8), dp(4), dp(8), dp(4))
                        isClickable = true; isFocusable = true
                        contentDescription = "Remove unavailability"
                        setOnClickListener {
                            TeamRepository.removeUnavailableRange(uid, r.id, preferences)
                            toast("Removed unavailability")
                            navigate("availability")
                        }
                    }
                    row.add(delBtn, 40, 40)
                    add(row)
                }
                darkCard.add(p); darkCard.space(8)
            }
        }

        content.add(darkCard, -1, -2, 1f)
        root.add(scroll, -1, 0, 1f)
        return root
    }
    private fun serviceView(): View = memberPage("Service", serviceReturnPage, "Schedule") { col ->
        col.add(panel(null).apply {
            background = bg(Color.rgb(192, 221, 245), 18)
            add(text(selectedService, 16, navy, true)); space(8)
            add(text(preferences.getString("serviceDate", "SEPT 6").orEmpty(), 12, navy)); space(4)
            add(text(preferences.getString("serviceTime", "6:00AM – 11:00AM").orEmpty(), 13, navy))
            space(4); add(text(preferences.getString("serviceVenue", if (selectedService == "Team Meeting") "Fellowship Hall" else "Main Sanctuary").orEmpty(), 13, navy))
        }); col.space(16)
        col.add(dualButtons("Songs", { songsReturnPage = "service"; navigate("songs") }, "Song Lineups", { navigate("lineup") }))
        col.space(24)
        col.add(text("Assigned Members and their Roles", 13, navy)); col.space(12)
        val currentSheetService = selectedSheetService?.let { sel ->
            val sid = sel.optString("id")
            storedServices().find { it.optString("id") == sid } ?: sel
        } ?: selectedSheetService
        val assignments = currentSheetService?.optJSONArray("assignments")
        if (assignments != null && assignments.length() > 0) {
            repeat(assignments.length()) { index ->
                val item = assignments.getJSONObject(index)
                val name = item.optString("name", "Member")
                val role = item.optString("role", "Musician")
                val uid = item.optString("uid")
                val photoUri = item.optString("photoUri").ifBlank { findMemberPhotoUri(uid, name) }
                val isLeaderUser = item.optString("role").equals("Worship Leader", true)
                val badge = if (isLeaderUser) "Leader" else null
                col.add(memberRow(name, role, badge, photoUri = photoUri, uid = uid))
                col.space(8)
            }
        } else {
            ministryMembers.take(4).forEach { (name, role, badge) ->
                val photoUri = findMemberPhotoUri(name = name)
                col.add(memberRow(name, role, badge, photoUri = photoUri))
            }
        }
        val sDate = selectedSheetService?.optString("date")?.ifBlank {
            preferences.getString("serviceDate", "")
        } ?: preferences.getString("serviceDate", "").orEmpty()
        val todayDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val isServicePast = sDate.isNotBlank() && sDate < todayDate

        var isUserAssigned = false
        val currentUid = com.example.syncedn.data.auth.AuthManager.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: preferences.getString("userId", "").orEmpty()
        val currentName = fullName.ifBlank { displayName }
        if (assignments != null) {
            for (i in 0 until assignments.length()) {
                val a = assignments.getJSONObject(i)
                val aUid = a.optString("uid").ifBlank { a.optString("userId") }
                val aName = a.optString("name")
                if ((currentUid.isNotBlank() && aUid == currentUid) ||
                    (currentName.isNotBlank() && aName.equals(currentName, ignoreCase = true))) {
                    isUserAssigned = true
                    break
                }
            }
        }

        col.space(28)
        if (isServicePast) {
            col.add(text("Service has ended • Substitution closed", 12, Color.GRAY, centered = true))
        } else {
            val label = if (isUserAssigned) "Request Exclusion (Find Cover)" else "Request Inclusion (Step-In / Swap)"
            col.add(memberButton(label) {
                substitutionType = if (isUserAssigned) "Exclusion" else "Inclusion"
                navigate("substitution")
            }, -1, 48)
        }
    }
    private fun substitutionView(): View = memberPage(if (substitutionType == "Inclusion") "Request Inclusion" else "Request Exclusion", "service") { col ->
        val activeCode = preferences.getString(SessionManager.KEY_TEAM_CODE, "").orEmpty().ifBlank { preferences.getString("teamCode", "").orEmpty() }
        val currentUid = com.example.syncedn.data.auth.AuthManager.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: preferences.getString(SessionManager.KEY_USER_ID, "").orEmpty().ifBlank {
                preferences.getString("userId", "").orEmpty()
            }
        val currentName = preferences.getString(SessionManager.KEY_FULL_NAME, "").orEmpty().ifBlank {
            preferences.getString(SessionManager.KEY_USERNAME, "").orEmpty()
        }.ifBlank {
            preferences.getString("fullName", "").orEmpty().ifBlank {
                preferences.getString("username", "Member").orEmpty()
            }
        }
        val userMinistryRole = preferences.getString(SessionManager.KEY_MINISTRY_ROLE, "").orEmpty()
            .ifBlank { preferences.getString("ministryRole", "").orEmpty() }

        var assignedRole = ""
        val selectedServiceObj = selectedSheetService
        val serviceTitle = selectedServiceObj?.optString("title")?.ifBlank { selectedService } ?: selectedService
        val serviceDate = selectedServiceObj?.optString("date")?.ifBlank {
            preferences.getString("serviceDate", "Upcoming Service")
        } ?: preferences.getString("serviceDate", "Upcoming Service").orEmpty()
        val serviceVenue = selectedServiceObj?.optString("venue")?.ifBlank {
            preferences.getString("serviceVenue", if (selectedService == "Team Meeting") "Fellowship Hall" else "Main Sanctuary")
        } ?: preferences.getString("serviceVenue", "Main Sanctuary").orEmpty()
        val serviceId = selectedServiceObj?.optString("id").orEmpty()

        val assignments = selectedServiceObj?.optJSONArray("assignments")
        if (assignments != null) {
            for (i in 0 until assignments.length()) {
                val a = assignments.getJSONObject(i)
                val aUid = a.optString("uid").ifBlank { a.optString("userId") }
                val aName = a.optString("name")
                if ((currentUid.isNotBlank() && aUid == currentUid) ||
                    (currentName.isNotBlank() && aName.equals(currentName, ignoreCase = true))) {
                    assignedRole = a.optString("role")
                    break
                }
            }
        }
        if (assignedRole.isBlank()) {
            assignedRole = userMinistryRole.ifBlank { "Team Member" }
        }

        val phtZone = java.util.TimeZone.getTimeZone("Asia/Manila")
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = phtZone }.format(java.util.Date())
        val isServicePast = serviceDate.isNotBlank() && serviceDate < todayStr
        if (isServicePast) {
            col.add(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = bg(Color.rgb(254, 242, 242), 12, Color.rgb(239, 68, 68))
                setPadding(dp(14), dp(12), dp(14), dp(12))
                val warnIcon = text("⚠️", 16, centered = true)
                addView(warnIcon, dp(24), dp(24))
                space(10)
                addView(text("This service has already taken place ($serviceDate). Substitution requests cannot be submitted for past services.", 12, Color.rgb(185, 28, 28)), LinearLayout.LayoutParams(0, -2, 1f))
            }); col.space(16)
        }

        val isInclusion = (substitutionType == "Inclusion")
        val assignmentHeader = if (isInclusion) "Service Lineup to Join" else "Current Assignment"
        val roleHeadline = if (isInclusion) "Volunteer Role: ${userMinistryRole.ifBlank { assignedRole }}" else assignedRole

        col.add(text(assignmentHeader, 13, purple, true)); col.space(8)
        col.add(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg(Color.WHITE, 14, Color.rgb(226, 232, 240))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            add(text(roleHeadline, 16, purple, true))
            space(4)
            add(text(serviceTitle, 13, navy, true))
            space(2)
            val timeDisplay = if (serviceDate.isNotBlank()) serviceDate else "Sunday 6:00AM - 11:00AM"
            add(text(timeDisplay, 12, Color.rgb(90, 90, 95)))
            space(2)
            add(text(serviceVenue, 12, Color.rgb(90, 90, 95)))
        }); col.space(18)

        col.add(text("Reason", 13, purple, true)); col.space(8)
        val defaultReasonHint = if (isInclusion) "Available to serve and eager to support lineup" else "Unavailable / Schedule Conflict"
        val reason = EditText(this).apply {
            hint = defaultReasonHint
            setHintTextColor(Color.rgb(160, 160, 165))
            setTextColor(navy)
            textSize = 13f
            background = bg(Color.rgb(250, 250, 250), 12, Color.rgb(226, 232, 240))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            filters = arrayOf(android.text.InputFilter.LengthFilter(500))
        }
        col.add(reason, -1, 50); col.space(18)

        val candidateSectionTitle = if (isInclusion) "Select Member to Step In For" else "Select Substitute"
        col.add(text(candidateSectionTitle, 13, purple, true)); col.space(8)

        data class CandidateItem(
            val uid: String,
            val name: String,
            val role: String,
            val isSameRole: Boolean,
            val conflictReason: String?,
            val isAssigned: Boolean
        )
        val candidatesList = mutableListOf<CandidateItem>()

        if (isInclusion) {
            // For Inclusion: candidate must be an ASSIGNED member in this service lineup (excluding current user)
            if (assignments != null) {
                for (i in 0 until assignments.length()) {
                    val a = assignments.getJSONObject(i)
                    val aUid = a.optString("uid").ifBlank { a.optString("userId") }
                    val aName = a.optString("name")
                    val aRole = a.optString("role")
                    val isSelf = (currentUid.isNotBlank() && aUid == currentUid) ||
                                 (currentName.isNotBlank() && aName.equals(currentName, ignoreCase = true))
                    if (!isSelf && aName.isNotBlank()) {
                        val isSame = aRole.equals(userMinistryRole, ignoreCase = true) || (userMinistryRole.isNotBlank() && aRole.contains(userMinistryRole, ignoreCase = true))
                        candidatesList.add(CandidateItem(aUid, aName, aRole, isSame, null, true))
                    }
                }
            }
            candidatesList.sortByDescending { it.isSameRole }
        } else {
            // For Exclusion: candidate must NOT be already assigned in this service lineup (and not current user)
            val assignedUids = mutableSetOf<String>()
            val assignedNames = mutableSetOf<String>()
            if (assignments != null) {
                for (i in 0 until assignments.length()) {
                    val a = assignments.getJSONObject(i)
                    val aUid = a.optString("uid").ifBlank { a.optString("userId") }
                    val aName = a.optString("name")
                    if (aUid.isNotBlank()) assignedUids.add(aUid)
                    if (aName.isNotBlank()) assignedNames.add(aName.lowercase())
                }
            }
            val activeRoster = getActiveRoster().ifEmpty { TeamRepository.getCachedRoster(activeCode) }
            val rosterCandidates = activeRoster.filter { member ->
                val mName = member.fullName.ifBlank { member.username }
                val isSelf = (currentUid.isNotBlank() && member.uid == currentUid) ||
                             (currentName.isNotBlank() && mName.equals(currentName, ignoreCase = true))
                val isAlreadyAssigned = (member.uid.isNotBlank() && assignedUids.contains(member.uid)) ||
                                        (mName.isNotBlank() && assignedNames.contains(mName.lowercase()))
                !isSelf && !isAlreadyAssigned
            }
            val transferred = preferences.getBoolean("transferred_leadership_$activeCode", false)
            for (member in rosterCandidates) {
                val mName = member.fullName.ifBlank { member.username }
                val isLeader = member.role.equals("Leader", true) || (!transferred && (member.email.trim().equals("ryanlloydgenturo@gmail.com", true) || mName.contains("Ryan Lloyd", true)) && !member.role.equals("Member", true))
                val mRole = if (isLeader) {
                    if (member.ministryRole.isBlank() || member.ministryRole.equals("Member", true)) "Worship Leader" else member.ministryRole
                } else {
                    member.ministryRole.ifBlank { member.role }
                }
                val isSame = mRole.equals(assignedRole, ignoreCase = true) || (assignedRole.isNotBlank() && mRole.contains(assignedRole, ignoreCase = true))
                val isSelfCand = member.uid == currentUserId() || mName.equals(currentName, true)
                val memberRanges = if (member.unavailableRanges.isNotEmpty()) {
                    member.unavailableRanges
                } else if (isSelfCand) {
                    TeamRepository.getMemberUnavailableRanges(member.uid, preferences, member.email, mName)
                } else {
                    emptyList()
                }
                val conflictRange = if (serviceDate.isNotBlank()) memberRanges.find { it.includes(serviceDate) } else null
                candidatesList.add(CandidateItem(member.uid, mName, mRole, isSame, conflictRange?.reason ?: (if (conflictRange != null) "Schedule Conflict" else null), false))
            }
            if (candidatesList.isEmpty()) {
                ministryMembers.filter { (name, _, _) -> !name.equals(currentName, true) && !assignedNames.contains(name.lowercase()) }.forEach { (name, role, _) ->
                    val isSame = role.equals(assignedRole, ignoreCase = true)
                    candidatesList.add(CandidateItem("seed_${name.hashCode()}", name, role, isSame, null, false))
                }
            }
            candidatesList.sortWith(compareByDescending<CandidateItem> { it.isSameRole }.thenBy { it.conflictReason != null })
        }

        val cardViews = mutableListOf<Pair<String, View>>()
        var selectedRoleForSwap = ""

        candidatesList.forEach { cand ->
            val isSelected = (selectedSubstituteUid == cand.uid) || (selectedSubstituteName == cand.name)
            val candidateCard = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = if (isSelected) bg(Color.rgb(250, 245, 255), 14, purple) else bg(Color.WHITE, 14, Color.rgb(226, 232, 240))
                setPadding(dp(12), dp(10), dp(12), dp(10))
                val rosterMember = getActiveRoster().find { it.uid == cand.uid || it.fullName.equals(cand.name, true) || it.username.equals(cand.name, true) }
                val pUri = rosterMember?.photoUri
                addView(memberAvatarView(pUri, 40), LinearLayout.LayoutParams(dp(40), dp(40)))

                val info = LinearLayout(this@MemberActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), 0, dp(8), 0)
                    val nameRow = horizontal().apply { gravity = Gravity.CENTER_VERTICAL }
                    nameRow.addView(text(cand.name, 14, navy, true), LinearLayout.LayoutParams(-2, -2))
                    if (cand.isSameRole) {
                        val recBadge = TextView(this@MemberActivity).apply {
                            text = "⭐ Recommended (Same Role)"
                            setTextColor(Color.rgb(126, 34, 206))
                            textSize = 9.5f
                            typeface = Typeface.DEFAULT_BOLD
                            background = bg(Color.rgb(243, 232, 255), 6)
                            setPadding(dp(8), dp(2), dp(8), dp(2))
                        }
                        val badgeParams = LinearLayout.LayoutParams(-2, -2).apply {
                            leftMargin = dp(8)
                        }
                        nameRow.addView(recBadge, badgeParams)
                    }
                    add(nameRow, -1, -2)
                    val candDisplayRole = if (cand.role.equals("Leader", true) || cand.name.contains("Ryan Lloyd", true)) {
                        if (cand.role.isBlank() || cand.role.equals("Member", true)) "Worship Leader" else cand.role
                    } else cand.role.ifBlank { "Team Member" }
                    add(text(candDisplayRole, 12, Color.rgb(90, 90, 95)), -1, -2)
                    if (cand.conflictReason != null) {
                        add(text("✕ Unavailable • ${cand.conflictReason}", 11, Color.rgb(220, 38, 38), true))
                    } else if (!isInclusion) {
                        add(text("✓ Available", 11, Color.rgb(22, 163, 74), true))
                    } else {
                        add(text("Assigned in Service", 11, Color.rgb(37, 99, 235), true))
                    }
                }
                add(info, 0, -2, 1f)

                isClickable = !isServicePast
                isFocusable = !isServicePast
                setOnClickListener {
                    if (isServicePast) return@setOnClickListener
                    selectedSubstituteUid = cand.uid
                    selectedSubstituteName = cand.name
                    selectedRoleForSwap = cand.role
                    substitute = cand.name
                    cardViews.forEach { (cUid, view) ->
                        val sel = (cUid == cand.uid)
                        view.background = if (sel) bg(Color.rgb(250, 245, 255), 14, purple) else bg(Color.WHITE, 14, Color.rgb(226, 232, 240))
                    }
                }
            }
            cardViews.add(cand.uid to candidateCard)
            col.add(candidateCard); col.space(10)
        }
        col.space(12)

        val submitBtn = Button(this).apply {
            text = if (isServicePast) "Service Completed" else "Submit Request"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            isEnabled = !isServicePast
            background = if (isServicePast) bg(Color.GRAY, 24) else bg(purple, 24)
            setOnClickListener {
                if (isServicePast) {
                    toast("Cannot submit substitution for a past service.")
                    return@setOnClickListener
                }
                val rawReason = reason.text.toString().trim()
                val reasonText = if (rawReason.isBlank()) defaultReasonHint else rawReason
                if (selectedSubstituteName.isBlank() && substitute.isNotBlank() && substitute != "John Doe") {
                    selectedSubstituteName = substitute
                }
                if (selectedSubstituteName.isBlank()) {
                    toast("Please select a teammate.")
                    return@setOnClickListener
                }
                if (selectedSubstituteUid.isBlank()) {
                    selectedSubstituteUid = "sub_${selectedSubstituteName.lowercase().replace(" ", "_")}"
                }
                if (currentUid.isNotBlank() && selectedSubstituteUid == currentUid) {
                    toast("You cannot select yourself.")
                    return@setOnClickListener
                }

                val prefix = if (substitutionType == "Inclusion") "[Inclusion] " else "[Exclusion] "
                val formattedReason = if (reasonText.startsWith("[Inclusion]") || reasonText.startsWith("[Exclusion]")) reasonText else (prefix + reasonText)
                val targetRole = if (substitutionType == "Inclusion") selectedRoleForSwap.ifBlank { assignedRole } else assignedRole

                val newSub = SubstitutionData(
                    id = java.util.UUID.randomUUID().toString(),
                    teamCode = activeCode,
                    serviceId = serviceId,
                    serviceTitle = serviceTitle,
                    serviceDate = serviceDate,
                    requesterUid = currentUid,
                    requesterName = currentName,
                    role = targetRole,
                    substituteUid = selectedSubstituteUid.ifBlank { "sub_${selectedSubstituteName.hashCode()}" },
                    substituteName = selectedSubstituteName,
                    reason = formattedReason,
                    type = substitutionType,
                    status = "Pending",
                    createdBy = currentUid,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                TeamRepository.createSubstitutionRequest(newSub, preferences) { result ->
                    runOnUiThread {
                        if (result.isSuccess) {
                            toast("Substitution request submitted")
                            com.example.syncedn.data.auth.SyncedMessagingService.dispatchSystemNotification(
                                this@MemberActivity,
                                preferences,
                                if (substitutionType == "Inclusion") "Inclusion Request Submitted" else "Substitution Request Submitted",
                                "${newSub.requesterName} requested substitution with ${newSub.substituteName} for ${newSub.role} in ${newSub.serviceTitle}."
                            )
                        } else {
                            toast("Saved offline: ${result.exceptionOrNull()?.message ?: ""}")
                        }
                        navigate("notifications")
                    }
                }
            }
        }
        col.add(submitBtn, -1, 48)
    }

    private fun formatAnnouncementDate(timestamp: Long): String {
        return try {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            when {
                diff < 60_000L -> "Just now"
                diff < 3600_000L -> "${(diff / 60_000L).coerceAtLeast(1)}m ago"
                diff < 86400_000L -> {
                    val phtZone = java.util.TimeZone.getTimeZone("Asia/Manila")
                    val calNow = java.util.Calendar.getInstance(phtZone)
                    val calThen = java.util.Calendar.getInstance(phtZone).apply { timeInMillis = timestamp }
                    val timeStr = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).apply {
                        timeZone = phtZone
                    }.format(java.util.Date(timestamp))
                    if (calNow.get(java.util.Calendar.DAY_OF_YEAR) == calThen.get(java.util.Calendar.DAY_OF_YEAR)) {
                        "Today at $timeStr"
                    } else {
                        "Yesterday at $timeStr"
                    }
                }
                diff < 86400_000L * 7 -> "${diff / 86400_000L} days ago"
                else -> java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US).format(java.util.Date(timestamp))
            }
        } catch (_: Exception) {
            "Recent"
        }
    }

    private fun leaderPosts(col: LinearLayout) {
        val postsCol = vertical()
        col.addView(postsCol)
        val activeCode = preferences.getString("teamCode", "").orEmpty()
        // Migrate legacy local demo posts if needed
        val legacyJson = preferences.getString("leaderPosts", null)
        val cached = TeamRepository.getCachedAnnouncements(activeCode, preferences)
        if (!legacyJson.isNullOrBlank() && cached.isEmpty()) {
            try {
                val posts = org.json.JSONArray(legacyJson)
                val migrated = mutableListOf<AnnouncementData>()
                for (i in 0 until posts.length()) {
                    val p = posts.getJSONObject(i)
                    migrated.add(AnnouncementData(
                        id = "legacy_ann_$i",
                        teamCode = activeCode,
                        title = p.optString("title", "Announcement"),
                        message = p.optString("message", ""),
                        urgent = p.optBoolean("urgent", false),
                        author = p.optString("author", "Leader"),
                        createdBy = "",
                        createdAt = System.currentTimeMillis() - (posts.length() - i) * 60_000L
                    ))
                }
                if (migrated.isNotEmpty()) {
                    TeamRepository.saveAnnouncementsLocally(activeCode, migrated, preferences)
                    preferences.edit().remove("leaderPosts").apply()
                }
            } catch (_: Throwable) {}
        }

        fun populateList() {
            postsCol.removeAllViews()
            val list = TeamRepository.getCachedAnnouncements(activeCode, preferences)
            if (list.isEmpty()) {
                postsCol.add(panel().apply {
                    add(text("No announcements yet.", 13, Color.GRAY, centered = true))
                }); postsCol.space(16)
                return
            }
            for (post in list) {
                postsCol.add(panel().apply {
                    val headerRow = horizontal().apply { gravity = Gravity.CENTER_VERTICAL }
                    headerRow.add(text(post.title, 15, purple, bold = true), 0, -2, 1f)
                    if (post.urgent) {
                        val urgentBadge = text("Urgent", 11, Color.rgb(192, 57, 43), bold = true).apply {
                            background = bg(Color.rgb(250, 219, 216), 10)
                            setPadding(dp(10), dp(3), dp(10), dp(3))
                        }
                        headerRow.add(urgentBadge, -2, -2)
                    }
                    add(headerRow)
                    space(8); add(text(post.message, 13, navy))
                    val rawAuthor = if (post.author.isNotBlank()) post.author else "Ministry Leader"
                    val authorLabel = if (rawAuthor.contains("(")) rawAuthor else "$rawAuthor (Ministry Leader)"
                    val dateLabel = formatAnnouncementDate(post.createdAt)
                    space(12)
                    val footerRow = horizontal().apply { gravity = Gravity.CENTER_VERTICAL }
                    footerRow.add(text("By $authorLabel", 11, purple), 0, -2, 1f)
                    footerRow.add(text(dateLabel, 11, Color.GRAY), -2, -2)
                    add(footerRow)
                }); postsCol.space(16)
            }
        }

        populateList()

        if (activeCode.isNotBlank()) {
            TeamRepository.getAnnouncements(activeCode, false, preferences) { result ->
                runOnUiThread {
                    if (result.isSuccess && page == "announcements") {
                        populateList()
                    }
                }
            }
        }
    }

    private fun substitutionFeed(col: LinearLayout) {
        val subCol = vertical()
        col.addView(subCol)
        subFeedCol = subCol

        val activeCode = preferences.getString(SessionManager.KEY_TEAM_CODE, "").orEmpty().ifBlank { preferences.getString("teamCode", "").orEmpty() }
        val currentUid = preferences.getString(SessionManager.KEY_USER_ID, "").orEmpty().ifBlank { preferences.getString("userId", "").orEmpty() }.ifBlank { AuthManager.currentUser?.uid.orEmpty() }
        val currentName = preferences.getString(SessionManager.KEY_FULL_NAME, "").orEmpty().ifBlank {
            preferences.getString(SessionManager.KEY_USERNAME, "").orEmpty()
        }.ifBlank {
            preferences.getString("fullName", "").orEmpty().ifBlank {
                preferences.getString("username", "").orEmpty()
            }
        }

        // Migrate legacy demo substitution if needed
        val legacyServiceName = preferences.getString("requestServiceName", null)
        val cached = TeamRepository.getCachedSubstitutions(activeCode, preferences)
        if (!legacyServiceName.isNullOrBlank() && cached.isEmpty()) {
            val legacySub = SubstitutionData(
                id = "legacy_demo_sub",
                teamCode = activeCode,
                serviceTitle = legacyServiceName,
                requesterUid = "demo_requester",
                requesterName = preferences.getString("requestRequester", "Angelo Daniel").orEmpty(),
                role = preferences.getString("requestRole", "Guitarist").orEmpty(),
                substituteUid = "demo_substitute",
                substituteName = preferences.getString("requestSubstitute", "Ryan Lloyd Genturo").orEmpty(),
                reason = preferences.getString("requestReason", "Family emergency out of town").orEmpty(),
                status = preferences.getString("requestStatus", "Waiting for substitute").orEmpty().let {
                    if (it == "Waiting for substitute") "Pending" else it
                },
                createdBy = "demo_requester"
            )
            TeamRepository.saveSubstitutionLocally(activeCode, legacySub, preferences)
        }

        fun populateSubstitutions() {
            subCol.removeAllViews()
            val list = TeamRepository.getCachedSubstitutions(activeCode, preferences)
            val myRole = preferences.getString(SessionManager.KEY_MINISTRY_ROLE, "").orEmpty()
                .ifBlank { preferences.getString("ministryRole", "").orEmpty() }
            val isSubLeader = myRole.equals("Substitute Leader", ignoreCase = true)

            val visibleList = list.filter { sub ->
                val isTarget = (currentUid.isNotBlank() && sub.substituteUid == currentUid) ||
                        (currentName.isNotBlank() && sub.substituteName.equals(currentName, ignoreCase = true))
                val isReq = (currentUid.isNotBlank() && sub.requesterUid == currentUid) ||
                        (currentName.isNotBlank() && sub.requesterName.equals(currentName, ignoreCase = true))
                isTarget || isReq || isSubLeader
            }

            if (visibleList.isEmpty()) {
                subCol.add(panel().apply {
                    add(text("No substitution requests.", 13, Color.GRAY, centered = true))
                }); subCol.space(24)
                return
            }

            for (sub in visibleList) {
                val cardWrapper = vertical()
                val cardPanel = panel().apply {
                    val headerRow = horizontal().apply { gravity = Gravity.CENTER_VERTICAL }
                    headerRow.add(text(sub.serviceTitle.ifBlank { "Sunday Worship Service" }, 15, Color.rgb(80, 5, 104), bold = true), 0, -2, 1f)

                    val isPending = sub.status.equals("Pending", ignoreCase = true)
                    val isSubAccepted = sub.status.equals("Substitute Accepted", ignoreCase = true)
                    val isAccepted = sub.status.equals("Accepted", ignoreCase = true) || sub.status.equals("Approved", ignoreCase = true)
                    val isDeclined = sub.status.equals("Declined", ignoreCase = true)
                    val isCancelled = sub.status.equals("Cancelled", ignoreCase = true)

                    val statusBadge = text(
                        if (isPending) "WAITING FOR SUBSTITUTE"
                        else if (isSubAccepted) "SUBSTITUTE ACCEPTED"
                        else if (isAccepted) "APPROVED"
                        else if (isDeclined) "DECLINED"
                        else if (isCancelled) "CANCELLED"
                        else sub.status.uppercase(),
                        9,
                        if (isAccepted) Color.rgb(46, 125, 50)
                        else if (isDeclined || isCancelled) Color.rgb(198, 40, 40)
                        else if (isSubAccepted) Color.rgb(14, 116, 144)
                        else Color.rgb(192, 38, 211),
                        bold = true
                    ).apply {
                        background = bg(
                            if (isAccepted) Color.rgb(232, 245, 233)
                            else if (isDeclined || isCancelled) Color.rgb(255, 235, 238)
                            else if (isSubAccepted) Color.rgb(224, 242, 254)
                            else Color.rgb(245, 208, 254),
                            8
                        )
                        setPadding(dp(8), dp(4), dp(8), dp(4))
                    }
                    headerRow.add(statusBadge, -2, -2)
                    val isInclusion = sub.reason.startsWith("[Inclusion]")
                    val isExclusion = sub.reason.startsWith("[Exclusion]")
                    if (isInclusion || isExclusion) {
                        val typeBadge = text(
                            if (isInclusion) "INCLUSION" else "EXCLUSION",
                            9,
                            if (isInclusion) Color.rgb(13, 148, 136) else Color.rgb(234, 88, 12),
                            bold = true
                        ).apply {
                            background = bg(
                                if (isInclusion) Color.rgb(204, 251, 241) else Color.rgb(255, 237, 213),
                                8
                            )
                            setPadding(dp(6), dp(4), dp(6), dp(4))
                        }
                        headerRow.add(View(this@MemberActivity), 6, 1)
                        headerRow.add(typeBadge, -2, -2)
                    }
                    val delBtn = text("✕", 14, Color.GRAY, bold = true, centered = true).apply {
                        setPadding(dp(8), dp(2), dp(2), dp(2))
                        isClickable = true; isFocusable = true
                        contentDescription = "Dismiss substitution request"
                        setOnClickListener {
                            TeamRepository.deleteSubstitution(activeCode, sub.id, preferences)
                            subCol.removeView(cardWrapper)
                            if (subCol.childCount == 0) {
                                subCol.add(panel().apply {
                                    add(text("No substitution requests.", 13, Color.GRAY, centered = true))
                                }); subCol.space(24)
                            }
                            toast("Request removed")
                        }
                    }
                    headerRow.add(delBtn, -2, -2)
                    add(headerRow); space(6)
                    val matchedSvc = storedServices().find { it.optString("id") == sub.serviceId }
                    val dStr = sub.serviceDate.ifBlank { matchedSvc?.optString("date").orEmpty() }
                    val tStr = matchedSvc?.let {
                        val s = it.optString("start"); val e = it.optString("end")
                        if (s.isNotBlank() && e.isNotBlank()) "${formatServiceTime(s)} – ${formatServiceTime(e)}" else if (s.isNotBlank()) formatServiceTime(s) else ""
                    }.orEmpty()
                    val vStr = matchedSvc?.optString("venue").orEmpty()
                    val metaList = mutableListOf<String>()
                    if (dStr.isNotBlank()) metaList.add("📅 $dStr")
                    if (tStr.isNotBlank()) metaList.add("⏰ $tStr")
                    if (vStr.isNotBlank()) metaList.add("📍 $vStr")
                    if (metaList.isNotEmpty()) {
                        add(text(metaList.joinToString("  •  "), 11, Color.rgb(100, 116, 139)))
                        space(6)
                    }

                    add(text("Requesting: ${sub.requesterName} (${sub.role})", 12, Color.rgb(30, 41, 59))); space(4)
                    add(text("Proposed Substitute: ${sub.substituteName}", 12, Color.BLACK, bold = true)); space(4)
                    add(text("Reason: ${sub.reason}", 12, Color.rgb(30, 41, 59)))

                    val isTargetSubstitute = (currentUid.isNotBlank() && sub.substituteUid == currentUid) ||
                            (currentName.isNotBlank() && sub.substituteName.equals(currentName, ignoreCase = true))
                    val isRequester = (currentUid.isNotBlank() && sub.requesterUid == currentUid) ||
                            (currentName.isNotBlank() && sub.requesterName.equals(currentName, ignoreCase = true))

                    if (isPending && isRequester) {
                        space(12)
                        val cancelBtn = Button(this@MemberActivity).apply {
                            text = "Cancel Request"
                            isAllCaps = false
                            background = bg(Color.WHITE, 8, Color.rgb(220, 38, 38))
                            setTextColor(Color.rgb(220, 38, 38))
                            minimumHeight = dp(38)
                            textSize = 12f
                            setTypeface(typeface, Typeface.BOLD)
                            setOnClickListener {
                                AlertDialog.Builder(this@MemberActivity)
                                    .setTitle("Cancel request?")
                                    .setMessage("Are you sure you want to cancel this substitution request?")
                                    .setPositiveButton("Cancel Request") { _, _ ->
                                        TeamRepository.updateSubstitutionStatus(activeCode, sub.id, "Cancelled", preferences) { res ->
                                            runOnUiThread {
                                                if (res.isSuccess) toast("Request cancelled.")
                                                populateSubstitutions()
                                            }
                                        }
                                    }
                                    .setNegativeButton("Back", null)
                                    .show()
                            }
                        }
                        add(cancelBtn, -1, 38)
                    }

                    if (isPending && (isTargetSubstitute || isSubLeader)) {
                        space(16)
                        val actions = horizontal()
                        val acceptBtn = Button(this@MemberActivity).apply {
                            text = "Accept Substitution"
                            isAllCaps = false
                            background = bg(Color.rgb(80, 5, 104), 8)
                            setTextColor(Color.WHITE)
                            minimumHeight = dp(38)
                            textSize = 12f
                            setTypeface(typeface, Typeface.BOLD)
                            setOnClickListener {
                                AlertDialog.Builder(this@MemberActivity).setTitle("Accept substitution?")
                                    .setMessage("Do you agree to substitute for ${sub.role} in ${sub.serviceTitle}? This will be sent to the Team Leader for final approval.")
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton("Accept") { _, _ ->
                                        TeamRepository.updateSubstitutionStatus(activeCode, sub.id, "Substitute Accepted", preferences) { res ->
                                            runOnUiThread {
                                                if (res.isSuccess) {
                                                    toast("Substitution accepted! Waiting for Leader's approval.")
                                                    com.example.syncedn.data.auth.SyncedMessagingService.dispatchSystemNotification(
                                                        this@MemberActivity,
                                                        preferences,
                                                        "Substitute Accepted",
                                                        "${sub.substituteName} accepted substitution for ${sub.role} in ${sub.serviceTitle}. Awaiting Leader's approval."
                                                    )
                                                }
                                                else toast("Failed to accept substitution: ${res.exceptionOrNull()?.message ?: "Unknown error"}")
                                                populateSubstitutions()
                                            }
                                        }
                                    }.show()
                            }
                        }
                        val declineBtn = Button(this@MemberActivity).apply {
                            text = "Decline"
                            isAllCaps = false
                            background = bg(Color.WHITE, 8, Color.rgb(217, 70, 239))
                            setTextColor(Color.rgb(217, 70, 239))
                            minimumHeight = dp(38)
                            textSize = 12f
                            setTypeface(typeface, Typeface.BOLD)
                            setOnClickListener {
                                AlertDialog.Builder(this@MemberActivity).setTitle("Decline substitution?")
                                    .setMessage("Are you sure you want to decline this substitution request?")
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton("Decline") { _, _ ->
                                        TeamRepository.updateSubstitutionStatus(activeCode, sub.id, "Declined", preferences) { res ->
                                            runOnUiThread {
                                                if (res.isSuccess) {
                                                    toast("Substitution declined.")
                                                    com.example.syncedn.data.auth.SyncedMessagingService.dispatchSystemNotification(
                                                        this@MemberActivity,
                                                        preferences,
                                                        "Substitution Declined",
                                                        "${sub.substituteName} declined substitution for ${sub.role} in ${sub.serviceTitle}."
                                                    )
                                                }
                                                else toast("Failed to decline substitution: ${res.exceptionOrNull()?.message ?: "Unknown error"}")
                                                populateSubstitutions()
                                            }
                                        }
                                    }.show()
                            }
                        }
                        actions.add(acceptBtn, 0, 38, 1.25f)
                        actions.add(View(this@MemberActivity), 10, 1)
                        actions.add(declineBtn, 0, 38, 0.75f)
                        add(actions)
                    } else if (isSubAccepted && isSubLeader) {
                        space(16)
                        val approveBtn = Button(this@MemberActivity).apply {
                            text = "Approve & Update Lineup"
                            isAllCaps = false
                            background = bg(Color.rgb(46, 125, 50), 8)
                            setTextColor(Color.WHITE)
                            minimumHeight = dp(38)
                            textSize = 12f
                            setTypeface(typeface, Typeface.BOLD)
                            setOnClickListener {
                                AlertDialog.Builder(this@MemberActivity).setTitle("Approve substitution?")
                                    .setMessage("As Substitute Leader, do you approve this substitution for ${sub.role}? Lineup will be updated.")
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton("Approve") { _, _ ->
                                        TeamRepository.acceptSubstitutionAndUpdateService(activeCode, sub, preferences) { res ->
                                            runOnUiThread {
                                                if (res.isSuccess) {
                                                    toast("Substitution approved & lineup updated!")
                                                    refreshTeamServices(force = true)
                                                } else toast("Failed: ${res.exceptionOrNull()?.message ?: "Error"}")
                                                populateSubstitutions()
                                            }
                                        }
                                    }.show()
                            }
                        }
                        add(approveBtn, -1, 38)
                    } else if (isSubAccepted && isTargetSubstitute) {
                        space(12)
                        add(text("✓ You accepted this request. Waiting for Leader's final approval.", 11, Color.rgb(14, 116, 144)))
                    } else if (isSubAccepted && isRequester) {
                        space(12)
                        add(text("✓ ${sub.substituteName} accepted! Waiting for Leader's final approval.", 11, Color.rgb(14, 116, 144)))
                    }
                }
                cardWrapper.add(cardPanel)
                cardWrapper.space(16)
                subCol.add(cardWrapper)
            }
            subCol.space(8)
        }

        populateSubstitutions()

        if (activeCode.isNotBlank()) {
            TeamRepository.getSubstitutions(activeCode, true, preferences) { result ->
                runOnUiThread {
                    if (result.isSuccess && (page == "notifications" || page == "announcements")) {
                        populateSubstitutions()
                    }
                }
            }
        }
    }

    private fun announcementsView(): View = memberPage("Announcement", "home") { col ->
        leaderPosts(col)
    }

    private var subFeedCol: LinearLayout? = null
    private var recentNotifCol: LinearLayout? = null
    private fun notificationsView(): View = memberPage("Notification", "home") { col ->
        val activeCode = preferences.getString(SessionManager.KEY_TEAM_CODE, "").orEmpty()
            .ifBlank { preferences.getString("teamCode", "").orEmpty() }
            .ifBlank { FirestoreManager.findCodeByTeamName(teamName).orEmpty() }
        val subHeader = horizontal().apply { gravity = Gravity.CENTER_VERTICAL }
        subHeader.add(text("Substitution Request", 15, purple, bold = true), 0, -2, 1f)
        val clearSubBtn = TextView(this@MemberActivity).apply {
            text = "Clear All"
            textSize = 12f
            setTextColor(Color.rgb(180, 50, 50))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                AlertDialog.Builder(this@MemberActivity)
                    .setTitle("Clear Substitution Requests")
                    .setMessage("Are you sure you want to clear all substitution request notifications?")
                    .setPositiveButton("Clear All") { _, _ ->
                        TeamRepository.clearAllSubstitutions(activeCode, preferences, isLeader = false)
                        subFeedCol?.removeAllViews()
                        subFeedCol?.add(panel().apply {
                            add(text("No substitution requests.", 13, Color.GRAY, centered = true))
                        })
                        subFeedCol?.space(24)
                        toast("Substitution requests cleared")
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        subHeader.add(clearSubBtn, -2, -2)
        col.add(subHeader); col.space(12)
        substitutionFeed(col)
        col.space(16)

        val notifHeader = horizontal().apply { gravity = Gravity.CENTER_VERTICAL }
        notifHeader.add(text("Recent Notifications", 15, purple, bold = true), 0, -2, 1f)
        val clearBtn = TextView(this@MemberActivity).apply {
            text = "Clear All"
            textSize = 12f
            setTextColor(Color.rgb(180, 50, 50))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                AlertDialog.Builder(this@MemberActivity)
                    .setTitle("Clear Notifications")
                    .setMessage("Are you sure you want to clear all recent notifications?")
                    .setPositiveButton("Clear All") { _, _ ->
                        TeamRepository.clearAllNotifications(activeCode, preferences)
                        recentNotifCol?.removeAllViews()
                        recentNotifCol?.add(panel().apply {
                            add(text("No recent notifications yet.", 12, Color.GRAY, centered = true))
                        })
                        recentNotifCol?.space(12)
                        toast("Notifications cleared")
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        notifHeader.add(clearBtn, -2, -2)
        col.add(notifHeader); col.space(12)
        recentNotificationsList(col)
    }

    private fun recentNotificationsList(col: LinearLayout) {
        val activeCode = preferences.getString(SessionManager.KEY_TEAM_CODE, "").orEmpty()
            .ifBlank { preferences.getString("teamCode", "").orEmpty() }
            .ifBlank { FirestoreManager.findCodeByTeamName(teamName).orEmpty() }
        val dynamicItems = TeamRepository.getRecentTeamNotifications(activeCode, preferences)
        val notifCol = vertical()
        col.addView(notifCol)
        recentNotifCol = notifCol

        if (dynamicItems.isEmpty()) {
            notifCol.add(panel().apply {
                add(text("No recent notifications yet.", 12, Color.GRAY, centered = true))
            })
            notifCol.space(12)
            return
        }
        for (notif in dynamicItems) {
            val cardWrapper = vertical()
            val cardPanel = panel().apply {
                val row = horizontal().apply { gravity = Gravity.CENTER_VERTICAL }
                val iconBox = FrameLayout(this@MemberActivity).apply {
                    addView(Symbol(this@MemberActivity, notif.iconName, purple), dp(26), dp(26))
                }
                row.add(iconBox, 36, 36)
                row.space(12)
                val textCol = vertical()
                textCol.add(text(notif.title, 13, purple, bold = true))
                textCol.space(2)
                textCol.add(text(notif.message, 11, navy))
                textCol.space(2)
                textCol.add(text(notif.timeLabel, 10, Color.GRAY))
                row.add(textCol, 0, -2, 1f)
                val delBtn = TextView(this@MemberActivity).apply {
                    text = "✕"
                    textSize = 13f
                    setTextColor(Color.rgb(156, 163, 175))
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                    isClickable = true
                    isFocusable = true
                    contentDescription = "Delete notification"
                    setOnClickListener {
                        TeamRepository.deleteNotification(notif.id, preferences)
                        notifCol.removeView(cardWrapper)
                        if (notifCol.childCount == 0) {
                            notifCol.add(panel().apply {
                                add(text("No recent notifications yet.", 12, Color.GRAY, centered = true))
                            })
                            notifCol.space(12)
                        }
                    }
                }
                row.add(delBtn, -2, -2)
                add(row)
            }
            cardWrapper.add(cardPanel)
            cardWrapper.space(12)
            notifCol.add(cardWrapper)
        }
    }
    private val youtubeThumbnailCache = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()

    private fun songCover(title: String, referenceUrl: String = ""): View {
        val videoId = com.example.syncedn.data.model.SongData.extractYoutubeVideoId(referenceUrl)
        val cached = if (videoId != null) youtubeThumbnailCache[videoId] else null

        val source = (image("song_covers_source", "$title cover").drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        val defaultBitmap = if (source != null && !source.isRecycled) {
            val coverY = when (title) { "Praise" -> 210; "Trust in God" -> 320; "Way Maker" -> 430; else -> 540 }
            bitmaps.getOrPut("song_cover_$title") {
                Bitmap.createBitmap(source, 29, coverY, 120, 66)
            }
        } else null

        val img = ImageView(this).apply {
            contentDescription = "$title cover"
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = bg(navy, 6)
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            if (cached != null && !cached.isRecycled) {
                setImageBitmap(cached)
            } else if (defaultBitmap != null && !defaultBitmap.isRecycled) {
                setImageBitmap(defaultBitmap)
            }
        }

        if (videoId != null && (cached == null || cached.isRecycled)) {
            img.tag = videoId
            Thread {
                try {
                    val thumbUrl = com.example.syncedn.data.model.SongData.getYoutubeThumbnailUrl(referenceUrl)
                    if (thumbUrl != null) {
                        val url = java.net.URL(thumbUrl)
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 4000
                        conn.readTimeout = 4000
                        conn.instanceFollowRedirects = true
                        conn.connect()
                        if (conn.responseCode == 200) {
                            val stream = conn.inputStream
                            val downloaded = BitmapFactory.decodeStream(stream)
                            stream.close()
                            if (downloaded != null) {
                                youtubeThumbnailCache[videoId] = downloaded
                                runOnUiThread {
                                    if (img.tag == videoId) {
                                        img.setImageBitmap(downloaded)
                                    }
                                }
                            }
                        }
                        conn.disconnect()
                    }
                } catch (_: Exception) {}
            }.start()
        }
        return img
    }
    private fun songCatalog(): List<Pair<String, String>> {
        val activeCode = preferences.getString("teamCode", "").orEmpty()
        val songs = TeamRepository.getCachedSongs(activeCode, preferences)
        return songs.map { it.title to it.artist }
    }
    private fun songsView(): View = memberPage("Songs", songsReturnPage) { col ->
        val search = EditText(this).apply {
            hint = "Search by title or artist"
            setHintTextColor(Color.rgb(107, 114, 128))
            setTextColor(Color.rgb(30, 41, 59))
            textSize = 14f
            background = null
            setSingleLine(true)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        }
        val searchBar = horizontal().apply {
            background = bg(Color.rgb(217, 217, 217), 21)
            setPadding(dp(14), 0, dp(14), 0)
            add(Symbol(this@MemberActivity, "search", Color.rgb(75, 85, 99)), 20, 20)
            space(8)
            add(search, 0, -1, 1f)
        }
        col.add(searchBar, -1, 42); col.space(20)
        val results = vertical(); col.add(results)
        val activeCode = preferences.getString("teamCode", "").orEmpty()
        fun populate(query: String) {
            results.removeAllViews()
            val songs = songCatalog()
            val matches = songs.filter { (title, artist) -> "$title $artist".contains(query, true) }
            if (matches.isEmpty()) results.add(text("No songs found.", 14, Color.GRAY))
            matches.forEach { (title, artist) ->
                val currentSong = TeamRepository.getCachedSongs(activeCode, preferences).find { it.title.equals(title, ignoreCase = true) }
                val refUrl = currentSong?.referenceUrl.orEmpty().ifBlank {
                    try { org.json.JSONObject(preferences.getString("songData_$title", "{}").orEmpty()).optString("link") } catch (_: Exception) { "" }
                }
                results.add(panel().apply {
                    val row = horizontal()
                    row.add(songCover(title, refUrl), 104, 60)
                    row.add(vertical().apply {
                        setPadding(dp(14), 0, dp(8), 0)
                        add(text(title, 14, navy, true)); space(5); add(text(artist, 11, Color.GRAY))
                    }, 0, -2, 1f)
                    row.add(Symbol(this@MemberActivity, "arrow", Color.GRAY), 18, 24)
                    add(row); isClickable = true; isFocusable = true
                    setOnClickListener { songDetailsBack = "songs"; selectedSong = title; navigate("songDetails") }
                }); results.space(16)
            }
        }
        populate("")
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { populate(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        if (activeCode.isNotBlank()) {
            TeamRepository.getSongs(activeCode, false, preferences) { _ ->
                runOnUiThread { populate(search.text?.toString().orEmpty()) }
            }
        }
    }
    private fun sampleSheetService(): org.json.JSONObject {
        val rows = org.json.JSONArray()
        ministryMembers.take(4).forEach { (name,role,_) ->
            rows.put(org.json.JSONObject().put("name",name).put("role",role))
        }
        return org.json.JSONObject().put("assignments",rows)
    }
    private fun selectSheetService(record: org.json.JSONObject) {
        selectedSheetService = org.json.JSONObject(record.toString())
        record.optJSONArray("songs")?.let { songs ->
            lineupSongs.clear()
            repeat(songs.length()) { idx ->
                val item = songs.get(idx)
                val unpacked = com.example.syncedn.data.model.ServiceSong.unpack(item)
                if (unpacked.title.isNotBlank()) lineupSongs.add(unpacked.title)
            }
        }
    }
    private fun songAssignedRoles(): String {
        val uid = preferences.getString("userId", "").orEmpty()
        val email = preferences.getString("email", "").orEmpty()
        val name = preferences.getString("fullName", "").orEmpty()
        val username = preferences.getString("username", displayName).orEmpty()
        val selected = selectedSheetService
        if (selected != null) {
            // Refresh a published record so reassignment is reflected when the sheet reopens.
            val current = storedServices().find { record ->
                if(selected.optString("id").isNotBlank()) record.optString("id") == selected.optString("id")
                else selected.optString("title").isNotBlank() &&
                    record.optString("title") == selected.optString("title") &&
                    record.optString("date") == selected.optString("date") &&
                    record.optString("start") == selected.optString("start") &&
                    record.optString("venue") == selected.optString("venue")
            } ?: selected
            val assignments = current.optJSONArray("assignments") ?: org.json.JSONArray()
            val rows = (0 until assignments.length()).map { index ->
                val row=assignments.getJSONObject(index)
                mapOf("uid" to row.optString("uid").ifBlank { row.optString("userId").ifBlank { row.optString("memberId") } },
                    "email" to row.optString("email"), "name" to row.optString("name"),
                    "role" to row.optString("role"))
            }
            // An explicit service assignment (or lack of one) wins over a general team role.
            return ChordSheet.assignedRoles(uid,email,name,username,rows)
        }
        val roles = try { org.json.JSONObject(preferences.getString("memberRoles","{}").orEmpty()) }
            catch(_:Exception) { org.json.JSONObject() }
        val identities = listOf(uid,email,name,username).filter { it.isNotBlank() }
        val rows = roles.keys().asSequence().map { key -> key to roles.optString(key) }.toList()
        return identities.firstNotNullOfOrNull { identity ->
            rows.find { it.first.trim().equals(identity.trim(),true) }?.second?.takeIf { it.isNotBlank() }
        }.orEmpty()
    }
    private fun songDetailsView(): View = memberPage("Song Details", songDetailsBack, footer = { sheetAutoScroll }) { col ->
        val activeCode = preferences.getString("teamCode", "").orEmpty()
        val currentSong = TeamRepository.getCachedSongs(activeCode, preferences).find { it.title.equals(selectedSong, ignoreCase = true) }
        val savedSong = try { org.json.JSONObject(preferences.getString("songData_$selectedSong", "{}").orEmpty()) }
            catch (_: Exception) { org.json.JSONObject() }
        val originalKey = currentSong?.key?.ifBlank { savedSong.optString("key", "C") } ?: savedSong.optString("key", "C")
        val basePitch = ChordSheet.keyPitch(originalKey).coerceAtLeast(0)
        val roots = listOf("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
        val minor = originalKey.endsWith("m") || originalKey.endsWith(" minor")
        val seedMatch = TeamRepository.getDefaultSeedSongs(activeCode).find { it.title.equals(selectedSong, ignoreCase = true) }
        val candidateSheet = (currentSong?.sheet?.ifBlank { currentSong.lyrics } ?: savedSong.optString("sheet", savedSong.optString("lyrics"))).ifBlank {
            seedMatch?.sheet.orEmpty().ifBlank {
                "[Verse]\n[C]Add your licensed lyrics here.\n\n[Chorus]\n[Am]Add chorus lyrics here [F][G]"
            }
        }
        val sheetRaw = if (!ChordSheet.hasChords(candidateSheet) && seedMatch != null && ChordSheet.hasChords(seedMatch.sheet)) {
            seedMatch.sheet
        } else {
            candidateSheet
        }
        val sheet = ChordSheet.formatStanzas(sheetRaw)
        val assignedRole = songAssignedRoles().ifBlank { preferences.getString("ministryRole", "").orEmpty() }
        val isVocalist = assignedRole.contains("vocal", ignoreCase = true) ||
                assignedRole.contains("singer", ignoreCase = true) ||
                (assignedRole.isNotBlank() && !ChordSheet.isInstrumentalist(assignedRole) && !assignedRole.contains("leader", ignoreCase = true))
        val hasChordsInSheet = ChordSheet.hasChords(sheet)
        val showChords = hasChordsInSheet && !isVocalist
        val tempo = currentSong?.bpm?.toString() ?: savedSong.optString("bpm", "74")
        var offset = ((preferences.getInt("chord_transpose_$selectedSong", 0) % 12) + 12) % 12
        val key = text("", 13, purple, true)
        val sheetText = text("", 13, navy).apply {
            if (showChords) typeface = Typeface.MONOSPACE
            includeFontPadding = true
            setLineSpacing(0f, 1.15f)
            setTextIsSelectable(true)
            isSingleLine = false
            setHorizontallyScrolling(false)
        }
        sheetAutoScroll = SheetAutoScrollControls(
            this@MemberActivity, preferences, selectedSong, col.parent as StableScrollView, sheetText
        )
        val keyButtons = mutableListOf<Pair<Int, TextView>>()
        fun renderTarget(target: Int) {
            offset = (target - basePitch + 12) % 12
            val root = roots[target]
            key.text = "Key: $root${if(minor) "m" else ""}    •    $tempo BPM"
            sheetText.text = ChordSheet.render(sheet, if (isVocalist) 0 else offset, !showChords, root.contains('b') || root == "F")
            keyButtons.forEach { (pitch, button) ->
                val selected = pitch == target
                button.setTextColor(if(selected) Color.WHITE else navy)
                button.background = SongControlStyle.background(resources, selected)
                button.isSelected = selected
            }
            preferences.edit().putInt("chord_transpose_$selectedSong", offset).apply()
        }
        
        col.add(panel().apply {
            add(text(selectedSong, 18, navy, true)); space(6)
            val defaultArtist = if (selectedSong == "Way Maker") "Sinach" else if (selectedSong == "Gratitude") "Brandon Lake" else "Elevation Worship"
            val artistDisplay = currentSong?.artist?.ifBlank { savedSong.optString("artist", seedMatch?.artist ?: defaultArtist) } ?: seedMatch?.artist ?: savedSong.optString("artist", defaultArtist)
            add(text("By $artistDisplay", 13, Color.GRAY))
            space(16)
            add(key)
            if (!isVocalist) {
                space(16)
                key.contentDescription = "Song key. Tap to choose any of twelve keys."
                key.isFocusable = true
                key.setOnClickListener {
                    AlertDialog.Builder(this@MemberActivity).setTitle("Transpose to key")
                        .setSingleChoiceItems(roots.map { it + if(minor) "m" else "" }.toTypedArray(),
                            (basePitch + offset) % 12) { dialog, position ->
                            renderTarget(position); dialog.dismiss()
                        }.setNeutralButton("Original") { _,_ -> renderTarget(basePitch) }
                        .setNegativeButton("Cancel",null).show()
                }
                add(text("Transpose", 12, Color.GRAY)); space(8)
                roots.chunked(6).forEachIndexed { rowIndex, row ->
                    if(rowIndex > 0) space(8)
                    add(horizontal().apply {
                        row.forEach { chord ->
                            val pitch = ChordSheet.pitch(chord)
                            add(text(chord + if(minor) "m" else "", 12, navy, true, true).apply {
                                background = SongControlStyle.background(resources)
                                contentDescription = "Transpose to $text"
                                setOnClickListener { renderTarget(pitch) }
                                isFocusable = true
                                keyButtons.add(pitch to this)
                            }, 0, 48, 1f)
                        }
                    })
                }
                add(text("Original key", 12, purple).apply {
                    isFocusable = true
                    setOnClickListener { renderTarget(basePitch) }
                }, -1, 36)
            }
        }); col.space(24)
        col.add(panel().apply {
            val metronome = MetronomeView(this@MemberActivity, preferences, selectedSong, tempo.toIntOrNull() ?: 74)
            songMetronome = metronome
            add(metronome)
        }); col.space(24)
        val refLink = currentSong?.referenceUrl.orEmpty().ifBlank { savedSong.optString("link") }
        val playerView = com.example.syncedn.ui.player.YouTubePlayerHelper.buildHybridPlayerView(
            context = this@MemberActivity,
            referenceUrl = refLink,
            selectedSongTitle = selectedSong,
            navyColor = navy,
            dp = { dp(it) }
        )
        if (playerView != null) {
            col.add(playerView)
            col.space(12)
        }

        col.add(text(if(showChords) "Lyrics & Chords Sheet" else "Lyrics", 13, navy, true)); col.space(10)
        col.add(panel().apply {
            add(sheetText, -1, -2)
        }); col.space(24)
        renderTarget((basePitch + offset) % 12)
        col.add(text("Reference Link", 13, navy, true)); col.space(8)
        val reference = currentSong?.referenceUrl.orEmpty().ifBlank { savedSong.optString("link") }
        val displayUrl = if (reference.startsWith("https://") || reference.startsWith("http://")) reference
            else if (reference.isNotBlank()) "https://$reference"
            else "https://www.youtube.com/results?search_query=" + Uri.encode("$selectedSong worship")
        val linkCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = bg(Color.rgb(241, 245, 249), 12, Color.rgb(203, 213, 225))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            isClickable = true
            isFocusable = true
            contentDescription = "Song Reference Link: $displayUrl"

            addView(Symbol(this@MemberActivity, "music", Color.rgb(37, 99, 235)), dp(20), dp(20))
            val urlText = text(displayUrl, 13, Color.rgb(37, 99, 235)).apply {
                paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                setPadding(dp(10), 0, 0, 0)
            }
            addView(urlText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            setOnClickListener {
                try {
                    val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Reference Link", displayUrl)
                    clipboard.setPrimaryClip(clip)
                    toast("Link copied: $displayUrl")
                } catch (_: Exception) {}
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(displayUrl)))
                } catch (_: Exception) {
                    toast("No browser available.")
                }
            }
        }
        col.add(linkCard, -1, -2)
    }

    private fun lineupView(): View = memberPage("Song Lineup", "service") { col ->
        col.add(text(selectedService.ifBlank { "Sunday Worship Service" }, 13, navy, true)); col.space(20)
        val activeCode = preferences.getString("teamCode", "").orEmpty()
        val allCatalogSongs = TeamRepository.getCachedSongs(activeCode, preferences)
        lineupSongs.forEachIndexed { index, rawItem ->
            val unpacked = com.example.syncedn.data.model.ServiceSong.unpack(rawItem)
            val matched = allCatalogSongs.find { it.title.equals(unpacked.title, true) }
            val title = unpacked.title
            val artist = unpacked.artist.ifBlank { matched?.artist.orEmpty() }
            val key = unpacked.key.ifBlank { matched?.key.orEmpty() }.ifBlank { "C" }
            val bpm = if (unpacked.bpm > 0) unpacked.bpm else (matched?.bpm?.takeIf { it > 0 } ?: 74)

            val subtitleParts = mutableListOf<String>()
            if (artist.isNotBlank()) subtitleParts.add(artist)
            if (key.isNotBlank()) subtitleParts.add("Key: $key")
            if (bpm > 0) subtitleParts.add("$bpm BPM")
            val subtitleText = subtitleParts.joinToString(" • ")

            col.add(panel().apply {
                val row = horizontal()
                row.add(text((index + 1).toString() + ".", 14, navy, true), 28, 48)
                row.add(vertical().apply {
                    setPadding(dp(4), 0, dp(8), 0)
                    add(text(title, 14, navy, true))
                    if (subtitleText.isNotBlank()) {
                        space(3)
                        add(text(subtitleText, 11, Color.GRAY))
                    }
                    isClickable = true
                    setOnClickListener {
                        songDetailsBack = "lineup"
                        selectedSong = title
                        navigate("songDetails")
                    }
                }, 0, -2, 1f)
                row.add(text("⋮", 24, navy, centered = true).apply {
                    contentDescription = "Reorder $title"; isFocusable = true
                    setOnClickListener {
                        AlertDialog.Builder(this@MemberActivity).setTitle(title)
                            .setItems(arrayOf("Move up", "Move down")) { _, choice ->
                                val target = if (choice == 0) index - 1 else index + 1
                                if (target in lineupSongs.indices) {
                                    java.util.Collections.swap(lineupSongs, index, target); navigate("lineup")
                                } else toast("Already at the edge of the lineup.")
                            }.show()
                    }
                }, 48, 48)
                add(row)
            })
            col.space(12)
        }
    }
    private fun teamHeader(showDetails: Boolean = false): View = FrameLayout(this).apply {
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(8, 7, 32), navy))
        val screenW = resources.displayMetrics.widthPixels
        val exactImageWidth = if (screenW > 0) {
            ((screenW * (if (showDetails) 237f else 130f)) / 360f).toInt()
        } else {
            dp(if (showDetails) 237 else 130)
        }
        val photograph = image("team_sunset", "Sunset and cross").apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        addView(photograph, FrameLayout.LayoutParams(exactImageWidth, -1, Gravity.END))
        addView(View(this@MemberActivity).apply {
            background = bg(Color.argb(70, 0, 0, 20))
        }, FrameLayout.LayoutParams(-1, -1))
        val row = horizontal().apply { setPadding(dp(8), 0, dp(16), 0) }
        row.add(FrameLayout(this@MemberActivity).apply {
            addView(Symbol(this@MemberActivity, "back", Color.WHITE),
                FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
            setOnClickListener { navigate(if (page == "teamDetails") "teams" else "home") }
            contentDescription = if (page == "teamDetails") "Back to Teams" else "Back to Home"
            isFocusable = true
        }, 40, 48)
        row.add(image("team_header_badge", "Team badge"), 56, 56)
        row.add(vertical().apply {
            setPadding(dp(12), 0, dp(8), 0)
            add(text(teamName.ifBlank { "Worship Team" }, 14, Color.WHITE, true).apply {
                maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
            })
            space(4); add(text("Music Ministry", 11, Color.LTGRAY))
        }, 0, -2, 1f)
        row.add(teamLogo(), 76, 64)
        addView(row, FrameLayout.LayoutParams(-1, dp(64)).apply {
            topMargin = dp(if (showDetails) 48 else 12)
        })
        if (showDetails) {
            addView(memberButton("View Details", Color.rgb(21, 28, 50)) { navigate("teamDetails") },
                FrameLayout.LayoutParams(dp(116), dp(32)).apply {
                    leftMargin = dp(120); topMargin = dp(116)
                })
        }
        layoutParams = LinearLayout.LayoutParams(-1, dp(if (showDetails) 156 else 88))
    }
    private fun teamTabs(): View = horizontal().apply {
        setPadding(dp(20), dp(10), dp(12), dp(10))
        listOf("Team Chat", "Members", "Role").forEach { label ->
            val tab = text(label, 13, if (teamTab == label) Color.WHITE else navy, true, true).apply {
                if (teamTab == label) background = bg(Color.rgb(21, 28, 50), 20)
                isFocusable = true; setOnClickListener { teamTab = label; navigate("teams") }
            }
            addView(tab, LinearLayout.LayoutParams(0, dp(32), 1f).apply { rightMargin = dp(8) })
        }
    }
    private fun teamLogo(): View = image("team_brand_logo", "Synced N").apply {
        // Supplied transparent white/gold logo: draw directly, without a color filter.
        scaleType = ImageView.ScaleType.FIT_CENTER
        background = null
        setPadding(0, 0, 0, 0)
        clearColorFilter()
        (drawable as? android.graphics.drawable.BitmapDrawable)?.paint?.apply {
            isFilterBitmap = true
            isAntiAlias = true
        }
    }
    private fun formatChatDateTime(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        return try {
            val phtZone = java.util.TimeZone.getTimeZone("Asia/Manila")
            val calNow = java.util.Calendar.getInstance(phtZone)
            val calThen = java.util.Calendar.getInstance(phtZone).apply { timeInMillis = timestamp }
            val timePart = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).apply { timeZone = phtZone }.format(java.util.Date(timestamp))
            val isSameYear = calNow.get(java.util.Calendar.YEAR) == calThen.get(java.util.Calendar.YEAR)
            val dayDiff = calNow.get(java.util.Calendar.DAY_OF_YEAR) - calThen.get(java.util.Calendar.DAY_OF_YEAR)
            if (isSameYear && dayDiff == 0) {
                "Today at $timePart"
            } else if (isSameYear && dayDiff == 1) {
                "Yesterday at $timePart"
            } else {
                val datePart = java.text.SimpleDateFormat("MMM d", java.util.Locale.US).apply { timeZone = phtZone }.format(java.util.Date(timestamp))
                "$datePart, $timePart"
            }
        } catch (_: Exception) {
            java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.US).format(java.util.Date(timestamp))
        }
    }

    private fun buildChatMessageBubble(
        msg: ChatMessage,
        currentUid: String,
        rosterSnapshot: List<TeamMember>? = null
    ): View {
        val maxBubbleWidth = (resources.displayMetrics.widthPixels * 0.72f).toInt()
        val isSelf = (msg.senderUid.isNotBlank() && msg.senderUid == currentUid) ||
                (msg.senderName.equals(displayName, ignoreCase = true) || msg.senderName.equals(fullName, ignoreCase = true))

        if (msg.isDeleted) {
            val retractedTime = if (msg.deletedAt > 0L) formatChatDateTime(msg.deletedAt) else formatChatDateTime(msg.timestamp)
            val row = horizontal().apply {
                gravity = if (isSelf) Gravity.END else Gravity.START
                setPadding(0, dp(4), 0, dp(4))
            }
            val pill = horizontal().apply {
                background = GradientDrawable().apply {
                    setColor(Color.rgb(245, 246, 248))
                    setStroke(dp(1), Color.rgb(220, 224, 230))
                    cornerRadius = dp(16).toFloat()
                }
                setPadding(dp(12), dp(6), dp(12), dp(6))
                val retractedLabel = if (isSelf) "⊘ You retracted this message" else "⊘ ${msg.senderName.ifBlank { "Member" }} retracted this message"
                add(text("$retractedLabel • $retractedTime", 11, Color.rgb(130, 135, 145)).apply { setTypeface(typeface, Typeface.ITALIC) })
            }
            row.addView(pill)
            return row
        }

        if (isSelf) {
            val row = horizontal().apply {
                gravity = Gravity.END
                setPadding(0, dp(4), 0, dp(4))
            }
            val bubbleContainer = vertical().apply {
                gravity = Gravity.END
            }

            fun showMessageOptionsDialog() {
                val opts = mutableListOf<String>()
                opts.add("Reply")
                if (msg.text.isNotBlank()) opts.add("Edit Message")
                opts.add("Delete Message")
                val options = opts.toTypedArray()
                AlertDialog.Builder(this@MemberActivity)
                    .setTitle("Message Options")
                    .setItems(options) { _, which ->
                        val action = options[which]
                        val curCode = preferences.getString("teamCode", "").orEmpty()
                        if (action == "Reply") {
                            startReplyingTo(msg)
                        } else if (action == "Edit Message") {
                            val input = EditText(this@MemberActivity).apply {
                                setText(msg.text)
                                setSelection(msg.text.length)
                                setPadding(dp(16), dp(12), dp(16), dp(12))
                            }
                            AlertDialog.Builder(this@MemberActivity)
                                .setTitle("Edit Message")
                                .setView(input)
                                .setPositiveButton("Save") { _, _ ->
                                    val newTxt = input.text.toString().trim()
                                    if (newTxt.isNotBlank()) {
                                        TeamRepository.editChatMessage(curCode, msg.id, newTxt, preferences) { res ->
                                            runOnUiThread {
                                                if (res.isSuccess) {
                                                    val idx = chatMessages.indexOfFirst { it.id == msg.id }
                                                    if (idx != -1) chatMessages[idx] = chatMessages[idx].copy(text = newTxt, isEdited = true, editedAt = System.currentTimeMillis())
                                                    navigate("teams")
                                                } else toast("Failed to edit message")
                                            }
                                        }
                                    }
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        } else if (action == "Delete Message") {
                            AlertDialog.Builder(this@MemberActivity)
                                .setTitle("Delete Message?")
                                .setMessage("Are you sure you want to delete this message?")
                                .setPositiveButton("Delete") { _, _ ->
                                    TeamRepository.deleteChatMessage(curCode, msg.id, preferences) { res ->
                                        runOnUiThread {
                                            if (res.isSuccess) {
                                                val idx = chatMessages.indexOfFirst { it.id == msg.id }
                                                if (idx != -1) chatMessages[idx] = chatMessages[idx].copy(isDeleted = true, deletedAt = System.currentTimeMillis(), text = "", mediaUrl = "")
                                                navigate("teams")
                                            } else toast("Failed to delete message")
                                        }
                                    }
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                    }
                    .show()
            }

            val bubble = vertical().apply {
                background = GradientDrawable().apply {
                    setColor(Color.rgb(21, 28, 50))
                    cornerRadii = floatArrayOf(
                        dp(18).toFloat(), dp(18).toFloat(),
                        dp(18).toFloat(), dp(18).toFloat(),
                        dp(4).toFloat(), dp(4).toFloat(),
                        dp(18).toFloat(), dp(18).toFloat()
                    )
                }
                setPadding(dp(14), dp(10), dp(14), dp(10))
                val hasReply = msg.replyToText.isNotBlank() || msg.replyToSender.isNotBlank()
                if (hasReply) {
                    minimumWidth = dp(180)
                    val quoteBox = horizontal().apply {
                        minimumWidth = dp(160)
                        background = GradientDrawable().apply {
                            setColor(Color.argb(45, 255, 255, 255))
                            cornerRadius = dp(8).toFloat()
                        }
                        setPadding(dp(8), dp(6), dp(8), dp(6))
                        val accent = View(this@MemberActivity).apply { setBackgroundColor(Color.WHITE) }
                        addView(accent, LinearLayout.LayoutParams(dp(3), dp(32)))
                        addView(View(this@MemberActivity), LinearLayout.LayoutParams(dp(6), dp(1)))
                        val quoteCol = vertical().apply {
                            val senderTv = text(msg.replyToSender.ifBlank { "Member" }, 11, Color.WHITE, bold = true).apply {
                                maxLines = 1
                                ellipsize = android.text.TextUtils.TruncateAt.END
                            }
                            addView(senderTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                            val quoteTv = text(msg.replyToText.ifBlank { "Message" }, 11, Color.rgb(226, 232, 240)).apply {
                                maxLines = 2
                                ellipsize = android.text.TextUtils.TruncateAt.END
                            }
                            addView(quoteTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                        }
                        addView(quoteCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    }
                    addView(quoteBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                    space(6)
                }
                if (msg.mediaUrl.isNotBlank()) {
                    val iv = ImageView(this@MemberActivity).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        outlineProvider = object : ViewOutlineProvider() {
                            override fun getOutline(view: View, outline: Outline) {
                                outline.setRoundRect(0, 0, view.width, view.height, dp(12).toFloat())
                            }
                        }
                        clipToOutline = true
                    }
                    add(iv, 220, 180)
                    ImageLoader.loadProfilePhoto(iv, msg.mediaUrl, circular = false)
                    iv.isClickable = true
                    iv.isFocusable = true
                    iv.setOnClickListener { showChatImageViewer(msg.mediaUrl) }
                    iv.setOnLongClickListener {
                        showMessageOptionsDialog()
                        true
                    }
                    if (msg.text.isNotBlank()) space(6)
                }
                if (msg.text.isNotBlank()) {
                    val tv = text(msg.text, 14, Color.WHITE).apply {
                        maxWidth = maxBubbleWidth - dp(28)
                        if (hasReply) minWidth = dp(140)
                    }
                    addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                }
            }
            bubbleContainer.addView(bubble, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.END })

            bubble.isClickable = true
            bubble.isFocusable = true
            bubble.setOnLongClickListener {
                showMessageOptionsDialog()
                true
            }

            val sentTimeStr = formatChatDateTime(msg.timestamp)
            val statusLabel = if (msg.isEdited && msg.editedAt > 0L) {
                "$sentTimeStr • (Edited ${formatChatDateTime(msg.editedAt)}) • ✓✓ Delivered"
            } else if (msg.isEdited) {
                "$sentTimeStr • (Edited) • ✓✓ Delivered"
            } else {
                "$sentTimeStr • ✓✓ Delivered"
            }
            val caption = text(statusLabel, 10, Color.rgb(148, 163, 184)).apply {
                setPadding(0, dp(2), dp(4), 0)
            }
            bubbleContainer.addView(caption, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.END })

            row.addView(bubbleContainer, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            return row
        } else {
            val row = horizontal().apply {
                gravity = Gravity.START
                setPadding(0, dp(4), 0, dp(4))
            }
            val pUri = msg.senderPhotoUri.ifBlank {
                (rosterSnapshot ?: getActiveRoster()).find { it.uid == msg.senderUid || it.fullName.equals(msg.senderName, true) || it.username.equals(msg.senderName, true) }?.photoUri ?: ""
            }
            row.add(memberAvatarView(pUri, 32), 32, 32)

            val bubbleContainer = vertical().apply {
                setPadding(dp(8), 0, 0, 0)
                gravity = Gravity.START
            }
            val senderLabel = msg.senderName.ifBlank { "Teammate" } + (if (msg.senderRole.isNotBlank()) " • ${msg.senderRole}" else "")
            bubbleContainer.add(text(senderLabel, 11, Color.rgb(100, 116, 139), bold = true).apply {
                setPadding(dp(4), 0, 0, dp(2))
            })

            fun showOtherMessageOptionsDialog() {
                val opts = arrayOf("Reply")
                AlertDialog.Builder(this@MemberActivity)
                    .setTitle("Message Options")
                    .setItems(opts) { _, which ->
                        val action = opts[which]
                        if (action == "Reply") {
                            startReplyingTo(msg)
                        }
                    }.show()
            }

            val bubble = vertical().apply {
                background = GradientDrawable().apply {
                    setColor(Color.rgb(241, 245, 249))
                    cornerRadii = floatArrayOf(
                        dp(18).toFloat(), dp(18).toFloat(),
                        dp(18).toFloat(), dp(18).toFloat(),
                        dp(18).toFloat(), dp(18).toFloat(),
                        dp(4).toFloat(), dp(4).toFloat()
                    )
                }
                setPadding(dp(14), dp(10), dp(14), dp(10))
                val hasReply = msg.replyToText.isNotBlank() || msg.replyToSender.isNotBlank()
                if (hasReply) {
                    minimumWidth = dp(180)
                    val quoteBox = horizontal().apply {
                        minimumWidth = dp(160)
                        background = GradientDrawable().apply {
                            setColor(Color.rgb(226, 232, 240))
                            cornerRadius = dp(8).toFloat()
                        }
                        setPadding(dp(8), dp(6), dp(8), dp(6))
                        val accent = View(this@MemberActivity).apply { setBackgroundColor(purple) }
                        addView(accent, LinearLayout.LayoutParams(dp(3), dp(32)))
                        addView(View(this@MemberActivity), LinearLayout.LayoutParams(dp(6), dp(1)))
                        val quoteCol = vertical().apply {
                            val senderTv = text(msg.replyToSender.ifBlank { "Member" }, 11, navy, bold = true).apply {
                                maxLines = 1
                                ellipsize = android.text.TextUtils.TruncateAt.END
                            }
                            addView(senderTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                            val quoteTv = text(msg.replyToText.ifBlank { "Message" }, 11, Color.rgb(71, 85, 105)).apply {
                                maxLines = 2
                                ellipsize = android.text.TextUtils.TruncateAt.END
                            }
                            addView(quoteTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                        }
                        addView(quoteCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    }
                    addView(quoteBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                    space(6)
                }
                if (msg.mediaUrl.isNotBlank()) {
                    val iv = ImageView(this@MemberActivity).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        outlineProvider = object : ViewOutlineProvider() {
                            override fun getOutline(view: View, outline: Outline) {
                                outline.setRoundRect(0, 0, view.width, view.height, dp(12).toFloat())
                            }
                        }
                        clipToOutline = true
                    }
                    add(iv, 220, 180)
                    ImageLoader.loadProfilePhoto(iv, msg.mediaUrl, circular = false)
                    iv.isClickable = true
                    iv.isFocusable = true
                    iv.setOnClickListener { showChatImageViewer(msg.mediaUrl) }
                    iv.setOnLongClickListener {
                        showOtherMessageOptionsDialog()
                        true
                    }
                    if (msg.text.isNotBlank()) space(6)
                }
                if (msg.text.isNotBlank()) {
                    val tv = text(msg.text, 14, Color.rgb(21, 28, 50)).apply {
                        maxWidth = maxBubbleWidth - dp(28)
                        if (hasReply) minWidth = dp(140)
                    }
                    addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                }
            }
            bubble.isClickable = true
            bubble.isFocusable = true
            bubble.setOnLongClickListener {
                showOtherMessageOptionsDialog()
                true
            }
            bubbleContainer.addView(bubble, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.START })

            val sentTimeStr = formatChatDateTime(msg.timestamp)
            val statusLabel = if (msg.isEdited && msg.editedAt > 0L) {
                "$sentTimeStr • (Edited ${formatChatDateTime(msg.editedAt)})"
            } else if (msg.isEdited) {
                "$sentTimeStr • (Edited)"
            } else {
                sentTimeStr
            }
            val caption = text(statusLabel, 10, Color.rgb(148, 163, 184)).apply {
                setPadding(dp(4), dp(2), 0, 0)
            }
            bubbleContainer.addView(caption, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.START })

            row.addView(bubbleContainer, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            return row
        }
    }

    private fun appendChatMessageBubble(msg: ChatMessage, currentUid: String) {
        val col = chatMessagesCol ?: return
        chatEmptyView?.let {
            col.removeView(it)
            chatEmptyView = null
        }
        col.addView(buildChatMessageBubble(msg, currentUid))
        chatScrollView?.removeCallbacks(chatScrollRunnable)
        chatScrollView?.postDelayed(chatScrollRunnable, 50)
    }

    private fun teamsView(): View {
        if (preferences.getBoolean("leftTeam", false)) return memberPage("Teams", "home", "Teams") { col ->
            col.add(text("You have no active team.", 16, navy, true)); col.space(20)
            col.add(memberButton("Create or Join Team") { navigate("teamChoice") }, -1, 48)
        }
        val currentCode = preferences.getString("teamCode", "").orEmpty()
        if (currentCode.isNotBlank()) {
            if (chatMessages.isEmpty()) {
                val cached = TeamRepository.getCachedChatMessages(currentCode, preferences)
                if (cached.isNotEmpty()) chatMessages.addAll(cached)
            }
            if (chatListener == null) {
                chatListener = TeamRepository.listenTeamMessages(currentCode, preferences) { msgs ->
                    runOnUiThread {
                        if (page == "teams" && teamTab == "Team Chat") {
                            val existingIds = chatMessages.map { it.id }.toSet()
                            val newIds = msgs.map { it.id }.toSet()
                            val hasDeleted = existingIds.any { it !in newIds }
                            val incoming = msgs.filter { it.id !in existingIds }
                            val hasEditedOrUpdated = msgs.any { m ->
                                val cur = chatMessages.find { it.id == m.id }
                                cur != null && TeamRepository.hasChatMessageChanged(cur, m)
                            }
                            if (hasDeleted || hasEditedOrUpdated) {
                                chatMessages.clear()
                                chatMessages.addAll(msgs)
                                val col = chatMessagesCol
                                if (col != null) {
                                    col.removeAllViews()
                                    chatEmptyView = null
                                    val myUid = AuthManager.currentUser?.uid?.takeIf { it.isNotBlank() }
                                        ?: preferences.getString(SessionManager.KEY_USER_ID, "").orEmpty()
                                    if (chatMessages.isEmpty()) {
                                        val empty = text("No messages yet. Say hello to your team!", 13, Color.rgb(100, 116, 139), centered = true).apply {
                                            setPadding(0, dp(40), 0, dp(40))
                                        }
                                        chatEmptyView = empty
                                        col.add(empty, -1, -2)
                                    } else {
                                        val rosterSnap = getActiveRoster()
                                        chatMessages.forEach { msg ->
                                            col.addView(buildChatMessageBubble(msg, myUid, rosterSnap))
                                        }
                                    }
                                } else {
                                    navigate("teams", triggerRefresh = false)
                                }
                            } else if (incoming.isNotEmpty()) {
                                val map = LinkedHashMap<String, ChatMessage>()
                                chatMessages.forEach { map[it.id] = it }
                                msgs.forEach { map[it.id] = it }
                                chatMessages.clear()
                                chatMessages.addAll(map.values.sortedBy { it.timestamp })

                                val myUid = AuthManager.currentUser?.uid?.takeIf { it.isNotBlank() }
                                    ?: preferences.getString(SessionManager.KEY_USER_ID, "").orEmpty()
                                if (chatMessagesCol != null) {
                                    incoming.sortedBy { it.timestamp }.forEach { msg ->
                                        appendChatMessageBubble(msg, myUid)
                                    }
                                } else {
                                    navigate("teams", triggerRefresh = false)
                                }
                            }
                        } else {
                            chatMessages.clear()
                            chatMessages.addAll(msgs.sortedBy { it.timestamp })
                        }
                    }
                }
            }
        }
        val root = vertical().apply { setBackgroundColor(Color.WHITE) }
        root.add(teamHeader(showDetails = true), -1, 156)
        root.add(teamTabs())
        val scroll = StableScrollView(this).apply { isFillViewport = true; clipToPadding = true }
        val col = vertical().apply { setPadding(dp(20), dp(16), dp(20), dp(20)) }
        scroll.addView(col, FrameLayout.LayoutParams(-1, -2))
        val swipeRefresh = androidx.swiperefreshlayout.widget.SwipeRefreshLayout(this).apply {
            setColorSchemeColors(navy, Color.rgb(167, 0, 213))
            addView(scroll, ViewGroup.LayoutParams(-1, -1))
            setOnRefreshListener {
                val activeCode = preferences.getString("teamCode", "").orEmpty()
                when (teamTab) {
                    "Team Chat" -> {
                        if (activeCode.isNotBlank()) {
                            TeamRepository.refreshTeamMessages(activeCode, preferences) { res ->
                                runOnUiThread {
                                    isRefreshing = false
                                    res.getOrNull()?.let { msgs ->
                                        chatMessages.clear()
                                        chatMessages.addAll(msgs)
                                        chatMessagesCol?.removeAllViews()
                                        val myUid = AuthManager.currentUser?.uid?.takeIf { it.isNotBlank() }
                                            ?: preferences.getString(SessionManager.KEY_USER_ID, "").orEmpty()
                                        if (msgs.isEmpty()) {
                                            val empty = text("No messages yet. Say hello to your team!", 13, Color.rgb(100, 116, 139), centered = true).apply {
                                                setPadding(0, dp(40), 0, dp(40))
                                            }
                                            chatEmptyView = empty
                                            chatMessagesCol?.add(empty, -1, -2)
                                        } else {
                                            msgs.forEach { m -> appendChatMessageBubble(m, myUid) }
                                        }
                                    }
                                }
                            }
                        } else {
                            isRefreshing = false
                        }
                    }
                    "Members", "Role" -> {
                        if (activeCode.isNotBlank()) {
                            TeamRepository.getTeamRoster(activeCode, force = true) { res ->
                                runOnUiThread {
                                    isRefreshing = false
                                    res.getOrNull()?.let { teamRoster = it }
                                    navigate("teams")
                                }
                            }
                        } else {
                            isRefreshing = false
                        }
                    }
                    else -> {
                        isRefreshing = false
                    }
                }
            }
        }
        root.add(swipeRefresh, -1, 0, 1f)
        when (teamTab) {
            "Members" -> {
                val roster = getActiveRoster()
                val activeCode = preferences.getString("teamCode", "").orEmpty()
                val transferred = preferences.getBoolean("transferred_leadership_$activeCode", false)
                val designatedLeaderUid = preferences.getString("team_leader_uid_$activeCode", "").orEmpty().trim()
                val designatedLeader = preferences.getString("team_leader_email_$activeCode", "").orEmpty().trim().lowercase()
                roster.forEach { member ->
                    val name = member.fullName.ifBlank { member.username.ifBlank { "Member" } }
                    val isLeaderBadge = (designatedLeaderUid.isNotBlank() && member.uid == designatedLeaderUid) ||
                                        (designatedLeader.isNotBlank() && member.email.equals(designatedLeader, ignoreCase = true)) ||
                                        (!transferred && designatedLeader.isBlank() && designatedLeaderUid.isBlank() && (member.email.equals("ryanlloydgenturo@gmail.com", ignoreCase = true) || member.fullName.equals("Ryan Lloyd Genturo", ignoreCase = true)) && !preferences.getString("email_role_${activeCode}_${member.email.trim().lowercase()}", "").equals("Member", true)) ||
                                        member.ministryRole.equals("Worship Leader", ignoreCase = true) ||
                                        (member.ministryRole.equals("Substitute Leader", ignoreCase = true) && !member.role.equals("Member", true))
                    val badge = if (isLeaderBadge) "Leader" else "Member"
                    val role = if (badge == "Member" && member.ministryRole.equals("Substitute Leader", ignoreCase = true)) {
                        "Member"
                    } else {
                        member.ministryRole.ifBlank { if (member.role.equals("Leader", true)) "Worship Leader" else "Member" }
                    }
                    col.add(memberRow(name, role, badge, photoUri = member.photoUri))
                    col.space(4)
                }
            }
            "Role" -> {
                col.setPadding(0, dp(24), 0, 0)
                val roster = getActiveRoster()
                val activeCode = preferences.getString("teamCode", "").orEmpty()
                val transferred = preferences.getBoolean("transferred_leadership_$activeCode", false)
                val designatedLeaderUid = preferences.getString("team_leader_uid_$activeCode", "").orEmpty().trim()
                val designatedLeader = preferences.getString("team_leader_email_$activeCode", "").orEmpty().trim().lowercase()
                val currentUid = preferences.getString(SessionManager.KEY_USER_ID, "").orEmpty()
                val baseRoles = TeamRepository.getCachedCustomRoles(currentCode)
                val activeRoles = (listOf("Worship Leader") + baseRoles + roster.map { m ->
                    if (m.role.equals("Member", true) && m.ministryRole.equals("Substitute Leader", true)) "Member" else m.ministryRole
                }).filter {
                    it.isNotBlank() && (!it.equals("Substitute Leader", true) || roster.any { m -> m.ministryRole.equals("Substitute Leader", true) && !m.role.equals("Member", true) })
                }.distinct()
                val roles = activeRoles
                roles.forEach { role ->
                    val people = roster.filter {
                        val isLead = TeamRepository.isWorshipLeader(it.ministryRole, it.role) ||
                                     (designatedLeaderUid.isNotBlank() && it.uid == designatedLeaderUid) ||
                                     (designatedLeader.isNotBlank() && it.email.trim().lowercase() == designatedLeader) ||
                                     (!transferred && designatedLeader.isBlank() && designatedLeaderUid.isBlank() && (it.email.trim().lowercase() == "ryanlloydgenturo@gmail.com" || it.fullName.equals("Ryan Lloyd Genturo", ignoreCase = true)) && !preferences.getString("email_role_${activeCode}_${it.email.trim().lowercase()}", "").equals("Member", true))
                        if (role.equals("Worship Leader", ignoreCase = true)) {
                            isLead
                        } else {
                            val mRole = if (it.role.equals("Member", true) && it.ministryRole.equals("Substitute Leader", true)) "Member" else it.ministryRole
                            mRole.equals(role, ignoreCase = true) && !isLead
                        }
                    }
                    val isInitiallyExpanded = expandedRole == role
                    val arrowView = Symbol(this@MemberActivity, "arrow", Color.rgb(65, 80, 100)).apply {
                        rotation = if (isInitiallyExpanded) 90f else 0f
                    }
                    val membersBox = vertical().apply {
                        visibility = if (isInitiallyExpanded) View.VISIBLE else View.GONE
                        setPadding(0, dp(8), 0, dp(12))
                        val card = vertical().apply {
                            setPadding(dp(20), dp(20), dp(20), dp(20))
                            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                                intArrayOf(Color.rgb(21, 28, 50), Color.rgb(57, 79, 150))).apply {
                                cornerRadius = dp(16).toFloat()
                            }
                            if (people.isEmpty()) {
                                add(text("No members assigned to this role yet", 13, Color.LTGRAY))
                            } else {
                                people.forEach { member ->
                                    val memberName = member.fullName.ifBlank { member.username.ifBlank { "Member" } }
                                    add(horizontal().apply {
                                        setPadding(0, dp(8), 0, dp(8))
                                        val pUri = member.photoUri.ifBlank {
                                            if (member.uid == currentUid || memberName.equals(displayName, ignoreCase = true) || memberName.equals(fullName, ignoreCase = true)) {
                                                preferences.getString("photoUri", null) ?: ""
                                            } else ""
                                        }
                                        add(memberAvatarView(pUri, 44), 44, 44)
                                        add(vertical().apply {
                                            setPadding(dp(14), 0, 0, 0)
                                            add(text(memberName, 14, Color.WHITE, true)); space(2)
                                            add(text(role, 12, Color.LTGRAY))
                                        }, 0, -2, 1f)
                                    })
                                }
                            }
                        }
                        add(card, -1, -2)
                    }

                    val rowHeader = horizontal().apply {
                        setPadding(dp(40), 0, dp(20), 0)
                        add(Symbol(this@MemberActivity, "music", Color.BLACK), 56, 52)
                        add(vertical().apply {
                            setPadding(dp(20), 0, dp(8), 0)
                            add(text(role, 14, navy, true)); space(4)
                            add(text("${people.size} member${if (people.size == 1) "" else "s"}", 12, Color.BLACK))
                        }, 0, -2, 1f)
                        add(arrowView, 28, 28)
                        isFocusable = true; contentDescription = "$role, ${people.size} members"
                        setOnClickListener {
                            val willExpand = membersBox.visibility != View.VISIBLE
                            expandedRole = if (willExpand) role else null
                            membersBox.visibility = if (willExpand) View.VISIBLE else View.GONE
                            arrowView.animate().rotation(if (willExpand) 90f else 0f).setDuration(180).start()
                        }
                    }
                    col.add(rowHeader, -1, 64)
                    col.add(membersBox, -1, -2)
                }
            }
            else -> {
                val currentUid = AuthManager.currentUser?.uid?.takeIf { it.isNotBlank() }
                    ?: preferences.getString(SessionManager.KEY_USER_ID, "").orEmpty()
                chatScrollView = scroll
                chatMessagesCol = col
                chatEmptyView = null
                var initialScrolled = false
                scroll.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    if (!initialScrolled && col.height > 0) {
                        initialScrolled = true
                        scroll.scrollTo(0, col.height)
                    }
                }

                val displayList = if (chatMessages.isNotEmpty()) chatMessages else TeamRepository.getCachedChatMessages(currentCode, preferences)

                if (displayList.isEmpty()) {
                    val empty = text("No messages yet. Say hello to your team!", 13, Color.rgb(100, 116, 139), centered = true).apply {
                        setPadding(0, dp(40), 0, dp(40))
                    }
                    chatEmptyView = empty
                    col.add(empty, -1, -2)
                } else {
                    val rosterSnap = getActiveRoster()
                    displayList.forEach { msg ->
                        col.addView(buildChatMessageBubble(msg, currentUid, rosterSnap))
                    }
                }
            }
        }
        if (teamTab == "Team Chat") {
            val replyBar = horizontal().apply {
                visibility = if (replyingToMessage != null) View.VISIBLE else View.GONE
                setBackgroundColor(Color.rgb(243, 244, 246))
                setPadding(dp(16), dp(6), dp(16), dp(6))
                val accent = View(this@MemberActivity).apply { setBackgroundColor(purple) }
                add(accent, dp(3), -1)
                space(8)
                val textCol = vertical().apply {
                    val repSender = replyingToMessage?.senderName.orEmpty().ifBlank { "Member" }
                    val repSnippet = replyingToMessage?.text?.takeIf { it.isNotBlank() }
                        ?: if (replyingToMessage?.mediaUrl?.isNotBlank() == true) "📷 Photo" else "Message"
                    val senderTv = text("Replying to $repSender", 11, purple, bold = true)
                    val snippetTv = text(repSnippet, 11, Color.rgb(100, 116, 139)).apply {
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                    chatReplySenderText = senderTv
                    chatReplySnippetText = snippetTv
                    add(senderTv)
                    add(snippetTv)
                }
                add(textCol, 0, -2, 1f)
                val cancelBtn = text("✕", 14, Color.GRAY, centered = true).apply {
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        replyingToMessage = null
                        visibility = View.GONE
                    }
                }
                add(cancelBtn, -2, -2)
            }
            chatReplyBar = replyBar
            root.add(replyBar, -1, -2)

            val composer = horizontal().apply { setPadding(dp(12), dp(8), dp(12), dp(12)) }
            fun choosePhoto() {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*" }
                @Suppress("DEPRECATION")
                startActivityForResult(intent, 301)
            }
            val photoBtn = FrameLayout(this).apply {
                background = bg(Color.rgb(241, 245, 249), 22)
                addView(
                    Symbol(this@MemberActivity, "photo", Color.rgb(71, 85, 105)),
                    FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER)
                )
                contentDescription = "Attach photo"
                isFocusable = true
                isClickable = true
                setOnClickListener { choosePhoto() }
            }
            composer.addView(photoBtn, LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                marginEnd = dp(8)
                gravity = Gravity.CENTER_VERTICAL
            })
            val entry = horizontal().apply { background = bg(Color.rgb(217, 217, 217), 24) }
            val message = plainInput("Message").apply { background = null }
            chatMessageInput = message
            entry.add(message, 0, 44, 1f)
            val send = text("➤", 20, Color.rgb(148, 163, 184), centered = true).apply {
                contentDescription = "Send message"; isFocusable = true
                setOnClickListener {
                    val textToSend = message.text.toString().trim()
                    if (textToSend.isNotBlank()) {
                        val repId = replyingToMessage?.id.orEmpty()
                        val repSender = if (repId.isNotBlank()) replyingToMessage?.senderName.orEmpty().ifBlank { "Member" } else ""
                        val repText = if (repId.isNotBlank()) {
                            replyingToMessage?.text?.takeIf { it.isNotBlank() }
                                ?: if (replyingToMessage?.mediaUrl?.isNotBlank() == true) "📷 Photo" else "Message"
                        } else ""
                        replyingToMessage = null
                        chatReplyBar?.visibility = View.GONE

                        if (chatMessages.isEmpty()) {
                            val cached = TeamRepository.getCachedChatMessages(currentCode, preferences)
                            if (cached.isNotEmpty()) chatMessages.addAll(cached)
                        }
                        val currentUid = AuthManager.currentUser?.uid?.takeIf { it.isNotBlank() }
                            ?: preferences.getString(SessionManager.KEY_USER_ID, "").orEmpty()
                        val currentPhoto = preferences.getString("photoUri", "").orEmpty()
                        val newMsg = ChatMessage(
                            id = java.util.UUID.randomUUID().toString(),
                            teamCode = currentCode,
                            senderUid = currentUid,
                            senderName = fullName.ifBlank { displayName.ifBlank { "Member" } },
                            senderRole = preferences.getString("ministryRole", "Member").orEmpty(),
                            senderPhotoUri = currentPhoto,
                            text = textToSend,
                            timestamp = System.currentTimeMillis(),
                            replyToId = repId,
                            replyToSender = repSender,
                            replyToText = repText
                        )
                        chatMessages.add(newMsg)
                        TeamRepository.sendChatMessage(currentCode, newMsg, preferences)
                        message.setText("")
                        if (chatMessagesCol != null) {
                            appendChatMessageBubble(newMsg, currentUid)
                        } else {
                            navigate("teams")
                        }
                    }
                }
            }
            entry.add(send, 40, 44)
            message.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val hasText = !s.isNullOrBlank()
                    send.setTextColor(if (hasText) purple else Color.rgb(148, 163, 184))
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
            composer.add(entry, 0, 44, 1f)
            root.add(composer)
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
        root.add(memberNavigation("Teams"), -1, 64)
        return root
    }
    private fun teamDetailsView(): View {
        val root = vertical().apply { setBackgroundColor(Color.WHITE) }
        root.add(teamHeader(), -1, 88)
        val scroll = StableScrollView(this).apply { isFillViewport = true; clipToPadding = true }
        val col = vertical().apply { setPadding(dp(20), dp(24), dp(20), dp(24)) }
        scroll.addView(col, FrameLayout.LayoutParams(-1, -2)); root.add(scroll, -1, 0, 1f)
        col.add(text("Team Name", 14, Color.BLACK, true)); col.space(8)
        val teamNameBox = horizontal().apply {
            gravity = Gravity.CENTER_VERTICAL
            background = bg(Color.rgb(217, 217, 217), 10)
            setPadding(dp(16), 0, dp(16), 0)
            add(text(teamName.ifBlank { "Worship Team" }, 14, Color.BLACK, bold = true), 0, -2, 1f)
        }
        col.add(teamNameBox, -1, 42); col.space(24)
        val row = horizontal().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        row.add(Symbol(this, "bell", Color.BLACK), 28, 28)
        row.add(text("Unmute Messages", 15, Color.BLACK, true).apply {
            setPadding(dp(16), 0, dp(8), 0)
        }, 0, 48, 1f)
        row.add(Switch(this).apply {
            contentDescription = "Unmute team messages"
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, Color.WHITE))
            trackTintList = ColorStateList(states, intArrayOf(Color.rgb(0, 122, 255), Color.rgb(209, 213, 219)))
            splitTrack = false
            isChecked = preferences.getBoolean("teamUnmuted", true)
            setOnCheckedChangeListener { _, checked ->
                val generalNotif = preferences.getBoolean("notifications", true)
                TeamRepository.saveNotificationPreferences(currentUserId(), generalNotif, checked, preferences)
            }
        }, 52, 48)
        col.add(row); col.space(4)
        col.add(teamDetailAction("warning", "Report") {
            val reason = plainInput("Describe the issue", true)
            AlertDialog.Builder(this).setTitle("Report Issue to Ministry Leadership")
                .setMessage("Submit a confidential report regarding team conduct, attendance, or technical issues. Your report will be safely routed to the Team Leader in the team records.")
                .setView(reason)
                .setNegativeButton("Cancel", null).setPositiveButton("Submit Report", null).create().apply {
                    setOnShowListener {
                        getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                            if (reason.text.isBlank()) invalid(reason, "Enter a reason.")
                            else {
                                val activeCode = preferences.getString("teamCode", "").orEmpty()
                                    .ifBlank { FirestoreManager.findCodeByTeamName(teamName).orEmpty() }
                                val reporterName = fullName.ifBlank { displayName }.ifBlank { "Member" }
                                TeamRepository.submitTeamReport(
                                    teamCode = activeCode,
                                    reporterUid = currentUserId(),
                                    reporterName = reporterName,
                                    reason = reason.text.toString().trim(),
                                    preferences = preferences
                                )
                                toast("Report submitted"); dismiss()
                            }
                        }
                    }
                    show()
                }
        }); col.space(4)
        col.add(teamDetailAction("leave_team", "Leave") {
            AlertDialog.Builder(this).setTitle("Leave Team?")
                .setMessage("Are you sure you want to leave this team?")
                .setNegativeButton("Cancel", null).setPositiveButton("Leave") { _, _ ->
                    val uid = preferences.getString("userId", "").orEmpty()
                    val activeCode = preferences.getString("teamCode", "").orEmpty()
                    if (activeCode.isNotBlank()) {
                        com.example.syncedn.data.auth.SyncedFirebaseMessagingService.unsubscribeFromTeam(activeCode)
                        com.example.syncedn.data.auth.SyncedMessagingService.stopRealtimeTeamWatchers()
                    }
                    if (uid.isNotBlank()) {
                        TeamRepository.leaveTeam(uid) {}
                    }
                    preferences.edit()
                        .putString("role", "")
                        .remove("teamName")
                        .remove("teamCode")
                        .remove("ministryRole")
                        .putBoolean("leftTeam", true)
                        .apply()
                    teamName = ""
                    toast("You left the team.")
                    navigate("teamChoice")
                }.show()
        })
        return root
    }
    private fun teamDetailAction(icon: String, label: String, action: () -> Unit): View = horizontal().apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(4), 0, dp(4))
        add(Symbol(this@MemberActivity, icon, Color.BLACK), 28, 28)
        add(text(label, 15, Color.BLACK, true).apply { setPadding(dp(16), 0, 0, 0) }, 0, 48, 1f)
        isFocusable = true; isClickable = true; contentDescription = label; setOnClickListener { action() }
    }

    private fun memberTile(id: Int, icon: String, label: String, labelSize: Int = 14) = vertical().apply {
        this.id=id;gravity=Gravity.CENTER
        background=bg(Color.WHITE,24,Color.rgb(211,211,211));elevation=dp(4).toFloat()
        isClickable=true;isFocusable=true;contentDescription=label
        add(Symbol(this@MemberActivity,icon,Color.rgb(174,0,234)),28,28)
        space(3);add(text(label,labelSize,navy,true,true))
    }
    private fun hub(id: Int, symbol: String, label: String, backgroundColor: Int, ink: Int) = vertical().apply {
        this.id=id;gravity=Gravity.CENTER;isClickable=true;isFocusable=true;contentDescription=label
        val tile=FrameLayout(this@MemberActivity).apply { background=bg(backgroundColor,20) }
        tile.addView(Symbol(this@MemberActivity,symbol,ink),FrameLayout.LayoutParams(dp(30),dp(30),Gravity.CENTER))
        add(tile,72,72);space(6);add(text(label,14,navy,bold = true,centered = true))
    }

}

