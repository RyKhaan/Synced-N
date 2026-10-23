@file:Suppress("NewApi")

package com.example.syncedn

import com.example.syncedn.data.auth.AuthManager
import com.example.syncedn.data.auth.EmailOtpService
import com.example.syncedn.data.auth.SessionManager
import com.example.syncedn.data.database.FirestoreManager
import com.example.syncedn.data.database.TeamRepository
import com.example.syncedn.data.model.ChatMessage
import com.example.syncedn.data.model.TeamMember
import com.example.syncedn.data.storage.CloudinaryUploader
import com.example.syncedn.data.storage.ImageLoader
import com.example.syncedn.data.model.UnavailableRange
import com.example.syncedn.data.model.SongData
import com.example.syncedn.data.model.AnnouncementData
import com.example.syncedn.data.model.SubstitutionData
import com.example.syncedn.data.util.ValidationUtils
import com.google.firebase.firestore.ListenerRegistration
import android.text.InputFilter
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.Switch
import android.content.res.ColorStateList
import android.graphics.Outline
import android.view.ViewOutlineProvider
import android.view.ViewGroup
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.graphics.withScale

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
 * ADMIN / LEADER VERSION: ONE-FILE Synced N front-end. Create an Empty Views Activity project in Kotlin.
 * Package: com.example.syncedn. Add this alongside MainActivity.kt and SharedUi.kt.
 * No custom XML, drawable files, CardView, server or extra dependencies required.
 * If your package differs, change the first line. Keep the generated manifest.
 * Screens are built below in Kotlin; artwork is embedded at the end of this file.
 * Demo login: any nonempty email/mobile + at least 5 password characters.
 * Demo OTP: 123456. Google sign-in and team actions are not real authentication.
 * Member pages: calendar, availability, schedules, service assignments, substitution,
 * announcements, songs/details/lineup, team chat/members/roles and team settings.
 * Requests, reports and availability are local demo data; messages are not sent.
 * This separate version always opens the Admin/Leader dashboard.
 * Admin publishing, assignments and team management operate only on this phone.
 */
class AdminActivity : AppCompatActivity() {
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

    private var page = "start"
    private var displayName = ""
    private var fullName = ""
    private var profileReturnPage = "settings"
    private val preferences by lazy { getSharedPreferences("synced_profile", MODE_PRIVATE) }
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
                                displayName = profile?.username ?: account.displayName ?: "Leader"
                                val emailStr = account.email.orEmpty().trim()
                                val uid = profile?.uid.orEmpty()
                                val (userRole, userTeam, userTeamCode) = resolveUserTeamSession(profile, emailStr)
                                teamName = userTeam
                                SessionManager.saveSessionProfile(preferences, profile, displayName, emailStr)
                                fullName = preferences.getString(SessionManager.KEY_FULL_NAME, "").orEmpty()
                                displayName = preferences.getString(SessionManager.KEY_USERNAME, displayName).orEmpty()
                                if (userTeam.isNotBlank()) {
                                    saveUserTeamState(userRole, userTeam, userTeamCode)
                                    FirestoreManager.updateUserRoleAndTeam(uid, userRole, userTeam, userTeamCode, emailStr)
                                } else {
                                    preferences.edit {
                                        putString("role", "")
                                        putString("teamName", "")
                                        putString("teamCode", "")
                                    }
                                }
                                toast("Welcome, $displayName!")
                                if (userTeam.isBlank()) {
                                    navigate("teamChoice")
                                } else if (userRole.equals("Leader", ignoreCase = true)) {
                                    navigate("home")
                                } else {
                                    startActivity(Intent(this@AdminActivity, MemberActivity::class.java).putExtra("startPage", "home"))
                                    finish()
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

    private fun resolveUserTeamSession(
        profile: com.example.syncedn.data.model.UserProfile?,
        email: String
    ): Triple<String, String, String> {
        syncLocalTeamsToMemory()
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
            if (role.equals("Leader", ignoreCase = true) && cleanTeamLower.isNotBlank() && emailLower.isNotBlank()) {
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

    private fun showTeamCreatedDialog(generatedCode: String) {
        if (generatedCode.isBlank()) return
        try {
            AlertDialog.Builder(this)
                .setTitle("Team Created!")
                .setMessage("Your team \"$teamName\" is ready.\n\nTeam Invite Code: $generatedCode\n\nShare this 6-character code with your members so they can join your team. You can also view it anytime in Teams > View Details.")
                .setPositiveButton("Copy Code") { _, _ ->
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(
                        android.content.ClipData.newPlainText("Team Invite Code", generatedCode)
                    )
                    toast("Team invite code copied: $generatedCode")
                }
                .show()
        } catch (_: Exception) {
            toast("Team Invite Code: $generatedCode")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        syncLocalTeamsToMemory()
        checkAndRequestNotificationPermission()
        displayName = savedInstanceState?.getString("displayName") ?: preferences.getString(SessionManager.KEY_USERNAME, "").orEmpty()
        fullName = preferences.getString(SessionManager.KEY_FULL_NAME, "").orEmpty()
        profileReturnPage = savedInstanceState?.getString("profileReturnPage") ?: "settings"
        roleMember = savedInstanceState?.getString("roleMember") ?: "Angelo Daniel"
        roleMemberUid = savedInstanceState?.getString("roleMemberUid").orEmpty()
        roleMemberEmail = savedInstanceState?.getString("roleMemberEmail").orEmpty()
        teamName = savedInstanceState?.getString("teamName") ?: preferences.getString("teamName", "").orEmpty()
        val uid = preferences.getString("userId", "").orEmpty()
        val emailLower = preferences.getString("email", "").orEmpty().trim().lowercase()
        val activeCode = preferences.getString("teamCode", "").orEmpty()
            .ifBlank { preferences.getString("email_code_$emailLower", "").orEmpty() }
            .ifBlank { preferences.getString("user_code_$uid", "").orEmpty() }
            .ifBlank { preferences.getString("team_leader_code_${teamName.trim().lowercase()}", "").orEmpty() }
            .ifBlank { FirestoreManager.findCodeByTeamName(teamName).orEmpty() }
        if (teamName.isNotBlank()) {
            val userMinistry = preferences.getString("ministryRole", "").orEmpty()
            val userRole = preferences.getString("role", if (isLeader) "Leader" else "Member").orEmpty()
            val effectiveRole = if (emailLower == "ryanlloydgenturo@gmail.com" || userMinistry.equals("Substitute Leader", true) || userRole.equals("Leader", true)) "Leader" else "Member"
            saveUserTeamState(effectiveRole, teamName, activeCode)
        }
        successFlow = savedInstanceState?.getString("successFlow") ?: "register"
        savedInstanceState?.let { state ->
            scheduleFilter = state.getString("scheduleFilter") ?: "Upcoming"
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
        initializeLeaderDemo()
        if (activeCode.isNotBlank()) {
            servicesListener = TeamRepository.subscribeToServices(activeCode, isLeader = true, preferences) { list ->
                runOnUiThread {
                    localServices.clear()
                    list.forEach { srv ->
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
                        localServices.add(obj)
                    }
                    if (page in listOf("home", "schedule")) {
                        navigate(page, triggerRefresh = false)
                    }
                }
            }
            substitutionsListener = TeamRepository.subscribeToSubstitutions(activeCode, preferences) { list ->
                runOnUiThread {
                    if (page in listOf("notifications", "announcements")) {
                        navigate(page, triggerRefresh = false)
                    }
                }
            }
            rosterListener = TeamRepository.subscribeToRoster(activeCode, preferences) { list ->
                runOnUiThread {
                    teamRoster = list
                    updateRosterViews()

                    val currentEmail = preferences.getString("email", "").orEmpty().trim().lowercase()
                    val currentUid = com.example.syncedn.data.auth.AuthManager.currentUser?.uid?.takeIf { it.isNotBlank() }
                        ?: preferences.getString(SessionManager.KEY_USER_ID, "").orEmpty()
                    val isPrimaryLeader = currentEmail == "ryanlloydgenturo@gmail.com" ||
                                          preferences.getString("role", "").equals("Leader", true)
                    if (!isPrimaryLeader) {
                        val myEntry = list.find { m ->
                            (currentUid.isNotBlank() && m.uid == currentUid) ||
                            (currentEmail.isNotBlank() && m.email.trim().lowercase() == currentEmail)
                        }
                        if (myEntry != null && !myEntry.ministryRole.equals("Substitute Leader", ignoreCase = true)) {
                            val newRole = myEntry.ministryRole.ifBlank { "Member" }
                            preferences.edit {
                                putString("ministryRole", newRole)
                                putString(SessionManager.KEY_MINISTRY_ROLE, newRole)
                            }
                            toast("Your Substitute Leader assignment has ended.")
                            val intent = Intent(this@AdminActivity, MemberActivity::class.java)
                                .putExtra("startPage", "home")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            startActivity(intent)
                            finish()
                        }
                    }
                }
            }
        }

        val myAdminUid = com.example.syncedn.data.auth.AuthManager.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: preferences.getString(SessionManager.KEY_USER_ID, "").orEmpty().ifBlank {
                preferences.getString("userId", "").orEmpty()
            }
        val myAdminEmail = preferences.getString("email", "").orEmpty().trim().lowercase()
        val isAdminPrimary = myAdminEmail == "ryanlloydgenturo@gmail.com" ||
                             preferences.getString("role", "").equals("Leader", true)
        if (!isAdminPrimary && myAdminUid.isNotBlank()) {
            userMembershipListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(myAdminUid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    val ministryRoleSnap = snapshot.getString("ministryRole").orEmpty()
                    if (!ministryRoleSnap.equals("Substitute Leader", ignoreCase = true)) {
                        preferences.edit {
                            putString("ministryRole", ministryRoleSnap.ifBlank { "Member" })
                            putString(SessionManager.KEY_MINISTRY_ROLE, ministryRoleSnap.ifBlank { "Member" })
                        }
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                toast("Your Substitute Leader assignment has ended.")
                                val intent = Intent(this@AdminActivity, MemberActivity::class.java)
                                    .putExtra("startPage", "home")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                startActivity(intent)
                                finish()
                            }
                        }
                    }
                }
        }

        navigate(savedInstanceState?.getString("page") ?: intent.getStringExtra("startPage") ?: "home")
        val newTeamCode = intent.getStringExtra("newTeamCode").orEmpty()
        if (newTeamCode.isNotBlank()) {
            intent.removeExtra("newTeamCode")
            showTeamCreatedDialog(newTeamCode)
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (activeTeamSheet != null) {
                    activeTeamSheet = null
                    navigate("teamDetails")
                    return
                }
                if (page == "home") {
                    AlertDialog.Builder(this@AdminActivity)
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
                    "otp" -> "reset"
                    "success" -> "login"
                    "teamChoice" -> "login"
                    "createTeam", "joinTeam" -> "teamChoice"
                    "home" -> null
                    "settings" -> "home"
                    "profile" -> profileReturnPage
                    "schedule", "calendar", "announcements", "notifications", "teams" -> "home"
                    "availability" -> "calendar"
                    "service" -> serviceReturnPage
                    "lineup" -> "service"
                    "songs" -> songsReturnPage
                    "songDetails" -> songDetailsBack
                    "teamDetails", "roleEdit" -> "teams"
                    "teamRoles" -> "teamDetails"
                    "createService" -> "home"
                    "memberAvailability" -> "createService"
                    "serviceAssignments" -> if (serviceWizard) "memberAvailability" else "service"
                    "adminLineup" -> if (serviceWizard) "serviceAssignments" else "service"
                    "scheduleRehearsal" -> if (serviceWizard) "adminLineup" else "service"
                    "reviewService" -> "scheduleRehearsal"
                    "songEdit" -> if (selectedSong.isBlank()) "songs" else "songDetails"
                    "announcementEdit" -> {
                        editingAnnouncement = null
                        "announcements"
                    }
                    else -> null
                }
                if (previous != null) navigate(previous)
                else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        saveLeaderDraft()
        outState.putString("page", page)
        outState.putString("profileReturnPage", profileReturnPage)
        outState.putString("roleMember", roleMember)
        outState.putString("roleMemberUid", roleMemberUid)
        outState.putString("roleMemberEmail", roleMemberEmail)
        outState.putString("displayName", displayName)
        outState.putString("teamName", teamName)
        outState.putString("successFlow", successFlow)
        outState.putString("scheduleFilter", scheduleFilter)
        outState.putString("teamTab", teamTab)
        outState.putString("expandedRole", expandedRole)
        outState.putString("selectedSong", selectedSong)
        outState.putString("songDetailsBack", songDetailsBack)
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

    override fun onDestroy() {
        servicesListener?.remove()
        substitutionsListener?.remove()
        rosterListener?.remove()
        userMembershipListener?.remove()
        servicesListener = null
        substitutionsListener = null
        rosterListener = null
        userMembershipListener = null
        super.onDestroy()
    }

    private fun navigate(next: String, triggerRefresh: Boolean = true) {
        if (next in listOf("start", "login")) {
            startActivity(Intent(this, MainActivity::class.java).putExtra("startPage", next))
            finish()
            return
        }
        sheetAutoScroll?.close()
        sheetAutoScroll = null
        songMetronome?.close()
        songMetronome = null
        page = next
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
            "start" -> click(UI.getStartedButton) { navigate("login") }
            "login" -> setupLogin()
            "register" -> setupRegister()
            "reset" -> setupReset()
            "otp" -> setupOtp()
            "success" -> setupSuccess()
            "teamChoice" -> {
                click(UI.createTeamChoiceButton) { navigate("createTeam") }
                click(UI.joinTeamChoiceButton) { navigate("joinTeam") }
            }
            "createTeam" -> click(UI.createTeamButton) {
                val name = input(UI.teamName)
                if (name.text.isBlank()) invalid(name, "Enter a team name.")
                else {
                    val enteredTeamName = name.text.toString().trim()
                    val createBtn = findViewById<Button>(UI.createTeamButton)
                    createBtn?.isEnabled = false
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
                                navigate("home")
                                showTeamCreatedDialog(generatedCode)
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
                    FirestoreManager.getTeamByCode(enteredCode) { result ->
                        runOnUiThread {
                            joinBtn?.isEnabled = true
                            if (result.isSuccess) {
                                val (validCode, foundTeamName) = result.getOrNull()!!
                                teamName = foundTeamName
                                val uid = preferences.getString("userId", "").orEmpty()
                                    .ifBlank { com.example.syncedn.data.auth.AuthManager.currentUser?.uid.orEmpty() }
                                    .ifBlank { "user_${displayName.hashCode()}" }
                                val emailStr = preferences.getString("email", "").orEmpty()
                                    .ifBlank { com.example.syncedn.data.auth.AuthManager.currentUser?.email.orEmpty() }
                                preferences.edit {
                                    putString("userId", uid)
                                    putString("email", emailStr)
                                }
                                saveUserTeamState("Member", foundTeamName, validCode)
                                FirestoreManager.updateUserRoleAndTeam(uid, "Member", foundTeamName, validCode, emailStr)
                                val memberName = fullName.ifBlank { displayName }
                                val newMember = com.example.syncedn.data.model.TeamMember(
                                    uid = uid,
                                    fullName = memberName,
                                    username = displayName,
                                    email = emailStr,
                                    role = "Member",
                                    ministryRole = preferences.getString("ministryRole", "Member").orEmpty().ifBlank { "Member" },
                                    teamCode = validCode
                                )
                                TeamRepository.cacheMember(newMember, preferences)
                                toast("Joined team: $foundTeamName")
                                startActivity(Intent(this@AdminActivity, MemberActivity::class.java).putExtra("startPage", "home"))
                                finish()
                            } else {
                                val err = result.exceptionOrNull()?.localizedMessage ?: "Invalid team code. Please check with your Team Leader."
                                invalid(code, err)
                                toast(err)
                            }
                        }
                    }
                }
            }
            "home" -> {
                if (triggerRefresh) {
                    refreshTeamRoster()
                    refreshTeamServices()
                    val activeCode = preferences.getString("teamCode", "").orEmpty()
                        .ifBlank { FirestoreManager.findCodeByTeamName(teamName).orEmpty() }
                    if (activeCode.isNotBlank()) {
                        TeamRepository.getAnnouncements(activeCode, false, preferences)
                    }
                }
                setupHome()
            }
            "schedule" -> {
                if (triggerRefresh) refreshTeamServices()
            }
            "teams" -> if (triggerRefresh) refreshTeamRoster()
            "createService", "memberAvailability", "serviceAssignments", "reviewService" -> {
                if (triggerRefresh) refreshTeamRoster(force = true)
            }
            "calendar" -> {
                if (triggerRefresh) {
                    refreshTeamRoster(force = true)
                    refreshTeamServices()
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
            when {
                emailStr.isBlank() -> invalid(email, "Enter your email address.")
                !Patterns.EMAIL_ADDRESS.matcher(emailStr).matches() -> invalid(email, "Enter a valid email address.")
                passStr.length < 6 -> invalid(password, "Password must be at least 6 characters.")
                else -> {
                    val loginBtn = findViewById<Button>(UI.loginButton)
                    loginBtn?.isEnabled = false
                    toast("Signing in...")
                    AuthManager.loginUser(emailStr, passStr) { result ->
                        runOnUiThread {
                            loginBtn?.isEnabled = true
                            if (result.isSuccess) {
                                val profile = result.getOrNull()
                                val uid = com.example.syncedn.data.auth.AuthManager.currentUser?.uid?.takeIf { it.isNotBlank() }
                                    ?: profile?.uid.orEmpty()
                                displayName = profile?.username?.ifBlank { emailStr.substringBefore("@") } ?: "Leader"
                                val (userRole, userTeam, userTeamCode) = resolveUserTeamSession(profile, emailStr)
                                teamName = userTeam
                                SessionManager.clearPreferencesOnly(preferences)
                                SessionManager.saveSessionProfile(preferences, profile, displayName, emailStr)
                                fullName = preferences.getString(SessionManager.KEY_FULL_NAME, "").orEmpty()
                                displayName = preferences.getString(SessionManager.KEY_USERNAME, displayName).orEmpty()
                                if (userTeam.isNotBlank()) {
                                    saveUserTeamState(userRole, userTeam, userTeamCode)
                                    FirestoreManager.updateUserRoleAndTeam(uid, userRole, userTeam, userTeamCode, emailStr)
                                } else {
                                    preferences.edit {
                                        putString("role", "")
                                        putString("teamName", "")
                                        putString("teamCode", "")
                                    }
                                }
                                toast("Welcome, $displayName!")
                                if (userTeam.isBlank()) {
                                    navigate("teamChoice")
                                } else if (userRole.equals("Leader", ignoreCase = true)) {
                                    navigate("home")
                                } else {
                                    startActivity(Intent(this@AdminActivity, MemberActivity::class.java).putExtra("startPage", "home"))
                                    finish()
                                }
                            } else {
                                val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Login failed. Check your email and password."
                                toast(errorMsg)
                                invalid(password, "Invalid credentials.")
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
            when {
                nameStr.isBlank() -> invalid(name, "Enter your username.")
                !Patterns.EMAIL_ADDRESS.matcher(emailStr).matches() -> invalid(email, "Enter a valid email.")
                passStr.length < 6 -> invalid(password, "Password must be at least 6 characters.")
                passStr != confirm.text.toString() -> invalid(confirm, "Passwords do not match.")
                else -> {
                    val regBtn = findViewById<Button>(UI.registerButton)
                    regBtn?.isEnabled = false
                    toast("Creating account...")
                    AuthManager.registerUser(nameStr, emailStr, passStr) { result ->
                        runOnUiThread {
                            regBtn?.isEnabled = true
                            if (result.isSuccess) {
                                val registeredProfile = result.getOrNull()
                                displayName = nameStr
                                fullName = ""
                                SessionManager.clearPreferencesOnly(preferences)
                                SessionManager.saveSessionProfile(preferences, registeredProfile, displayName, emailStr)
                                preferences.edit {
                                    putString("role", "")
                                    putString("teamName", "")
                                    putString("teamCode", "")
                                }
                                successFlow = "register"
                                toast("Account created successfully!")
                                navigate("success")
                            } else {
                                val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Registration failed."
                                toast(errorMsg)
                                invalid(email, errorMsg)
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

            when {
                emailStr.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(emailStr).matches() ->
                    invalid(email, "Enter a valid email address.")
                passStr.length < 6 ->
                    invalid(newPass, "New password must be at least 6 characters.")
                passStr != confirmStr ->
                    invalid(confirmPass, "Passwords do not match.")
                else -> {
                    val sendBtn = findViewById<Button>(UI.sendCodeButton)
                    sendBtn?.isEnabled = false
                    toast("Sending 6-digit code to $emailStr...")
                    preferences.edit().putString("resetEmail", emailStr).putString("resetPendingPassword", passStr).apply()
                    EmailOtpService.sendOtp(emailStr, pendingPassword = passStr) { result ->
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
                if (record != null && record.pendingPassword.isNotEmpty()) {
                    FirestoreManager.savePasswordReset(record.email, record.pendingPassword)
                }
                toast("Verification successful! Password reset.")
                successFlow = "reset"
                navigate("success")
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
        findViewById<TextView>(UI.welcomeText).text = displayName.ifBlank { "Leader" }
        click(UI.notificationsButton) { navigate("notifications") }
        applyProfilePhoto(UI.profileButton)
        click(UI.profileButton) { openProfile("home") }
        click(UI.worshipCard) {
            openHomeUpcoming()
        }
        findViewById<TextView>(UI.teamTitle).apply {
            text = teamName.ifBlank { "Worship\nTeam" }
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        click(UI.teamHubButton) { navigate("teams") }
        click(UI.announcementsButton) { navigate("announcements") }
        click(UI.calendarButton) { navigate("calendar") }
        click(UI.songsButton) { songsReturnPage = "home"; navigate("songs") }
        click(UI.scheduleCard) { openHomeSecondUpcoming() }
        click(UI.homeTab) { toast("You are on Home.") }
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
    private fun LinearLayout.space(height: Int) { add(View(this@AdminActivity), 1, height) }
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
            val cached = if (uri != null) ImageLoader.getCachedProfileBitmap(this@AdminActivity, uri) else null
            cached ?: bitmaps.getOrPut(name) {
                val resId = resources.getIdentifier(name, "drawable", packageName)
                if (resId != 0) {
                    val opts = BitmapFactory.Options().apply { inScaled = false }
                    BitmapFactory.decodeResource(resources, resId, opts)
                } else {
                    val bytes = Base64.decode(Art.data(name), Base64.DEFAULT)
                    requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                }
            }
        } else {
            bitmaps.getOrPut(name) {
                val resId = resources.getIdentifier(name, "drawable", packageName)
                if (resId != 0) {
                    val opts = BitmapFactory.Options().apply { inScaled = false }
                    BitmapFactory.decodeResource(resources, resId, opts)
                } else {
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
        "service" -> leaderServiceView()
        "announcements" -> announcementsView()
        "notifications" -> notificationsView()
        "songs" -> songsView()
        "songDetails" -> songDetailsView()
        "lineup" -> adminLineupView()
        "teams" -> teamsView()
        "teamDetails" -> teamDetailsView()
        "createService" -> createServiceView()
        "memberAvailability" -> memberAvailabilityView()
        "serviceAssignments" -> serviceAssignmentsView()
        "adminLineup" -> adminLineupView()
        "scheduleRehearsal" -> rehearsalView()
        "reviewService" -> reviewServiceView()
        "songEdit" -> songEditView()
        "announcementEdit" -> announcementEditView()
        "roleEdit" -> roleEditView()
        "teamRoles" -> teamRolesView()
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
                    divider.add(View(this@AdminActivity).apply { setBackgroundColor(Color.DKGRAY) },0,1,1f)
                    divider.add(text("or",centered=true),44,30)
                    divider.add(View(this@AdminActivity).apply { setBackgroundColor(Color.DKGRAY) },0,1,1f)
                    add(divider); space(16)
                    val google = horizontal().apply { id=UI.googleButton; gravity=Gravity.CENTER; background=bg(Color.rgb(217,217,217),10); isClickable=true; isFocusable=true; contentDescription="Continue with Google" }
                    google.add(image("google_mark"),26,26)
                    google.add(text("  Continue with Google",14,navy,true),-2,44)
                    add(google,-1,44); space(16)
                    action(UI.openRegisterButton,"Don’t have an account?   Sign Up",-1,true); space(20)
                }
            }
            "register" -> {
                col.space(15); col.section().apply {
                    add(heading("Register")); space(16)
                    add(field(UI.registerName,"Username")); space(10)
                    add(field(UI.registerPassword,"Password","At least 5 characters",password=true,toggle=UI.registerPasswordToggle)); space(10)
                    add(field(UI.registerConfirm,"Re-Enter Password","Re-Enter Password",password=true,toggle=UI.registerConfirmToggle)); space(10)
                    add(field(UI.registerEmail,"Email",email=true)); space(28)
                    action(UI.registerButton,"Register"); space(24)
                }
            }
            "reset" -> {
                col.space(22); col.section().apply {
                    add(heading("Reset Password")); space(16)
                    add(field(UI.resetEmail,"Email",email=true)); space(16)
                    add(field(UI.resetPassword,"New Password","At least 5 characters",password=true,toggle=UI.resetPasswordToggle)); space(16)
                    add(field(UI.resetConfirm,"Re-Enter Password","Re-Enter Password",password=true,toggle=UI.resetConfirmToggle)); space(34)
                    action(UI.sendCodeButton,"Send Code"); space(24)
                }
            }
            "otp" -> {
                col.space(50); col.section().apply {
                    add(text("OTP Verification Code", 16, navy, bold = true, centered = true)); space(20)
                    add(otpInputGroup(UI.otpCode)); space(16)
                    val resendRow = horizontal().apply {
                        gravity = Gravity.CENTER
                        add(text("Didn't receive a code? ", 13, Color.rgb(100, 100, 110)), -2, -2)
                        add(text("Resend", 13, purple, bold = true).apply {
                            isClickable = true; isFocusable = true
                            setOnClickListener {
                                val emailStr = preferences.getString("resetEmail", "").orEmpty()
                                val passStr = preferences.getString("resetPendingPassword", "").orEmpty()
                                if (emailStr.isNotBlank()) {
                                    toast("Resending verification code to $emailStr...")
                                    EmailOtpService.sendOtp(emailStr, passStr) { res ->
                                        runOnUiThread {
                                            if (res.isSuccess) toast("Verification code sent to $emailStr")
                                            else toast("Failed to resend code: ${res.exceptionOrNull()?.localizedMessage}")
                                        }
                                    }
                                } else {
                                    toast("Please request a code from the reset screen.")
                                }
                            }
                        })
                    }
                    add(resendRow); space(28)
                    action(UI.verifyOtpButton, "Reset Password"); space(24)
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

        // The supplied branded artwork matches the reference's large logo and lettering.
        // Only its top artwork is used; the button and caption remain native controls.
        val brandedTop = image("welcome_artwork", "Synced N").apply {
            val original = (drawable as android.graphics.drawable.BitmapDrawable).bitmap
            val scaleX = original.width.toFloat() / 360f
            val scaleY = original.height.toFloat() / 590f
            val cropHeight = (280 * scaleY).toInt().coerceAtMost(original.height)
            setImageBitmap(bitmaps.getOrPut("landing_branded_top") {
                Bitmap.createBitmap(original, 0, 0, original.width, cropHeight)
            })
            scaleType = ImageView.ScaleType.FIT_XY
        }
        addView(brandedTop, FrameLayout.LayoutParams(-1, dp(280), Gravity.TOP or Gravity.CENTER_HORIZONTAL))

        val footer = vertical().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            add(button(UI.getStartedButton, "Get Started", 180).apply {
                background = bg(Color.rgb(103, 5, 103), 24)
            }, 180, 40)
            space(16)
            add(text("Church Ministry Management", 15, Color.WHITE, centered = true))
            space(20)
            add(landingIndicator(), 32, 32)
        }
        addView(footer, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(48)
        })
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val availableWidth = width - paddingLeft - paddingRight
            val availableHeight = height - paddingTop - paddingBottom
            if (availableWidth > 0 && availableHeight > 0) {
                val scale = maxOf(availableWidth / 360f, availableHeight / 800f)
                val photoTop = (availableHeight - 800f * scale) / 2f
                val brandWidth = (360f * scale).toInt()
                val brandHeight = (280f * scale).toInt()
                val brandPosition = brandedTop.layoutParams as FrameLayout.LayoutParams
                if (brandPosition.width != brandWidth || brandPosition.height != brandHeight || brandPosition.topMargin != photoTop.toInt()) {
                    brandedTop.post {
                        brandedTop.layoutParams = brandPosition.apply {
                            width = brandWidth; height = brandHeight; topMargin = photoTop.toInt()
                        }
                    }
                }
            }
        }
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
        val published = localServices.filter { it.optString("status") == "Published" }
        val upcoming = published.filter { it.optString("date") >= today }.sortedWith(compareBy({ it.optString("date") }, { it.optString("start") }))
        val rec = if (upcoming.isNotEmpty()) upcoming.first() else published.maxByOrNull { it.optString("date") + it.optString("start") }
        if (rec != null) {
            val draftId = leaderDraft.optString("id")
            val draftCover = leaderDraft.optString("coverImageUrl")
            if (draftCover.isNotBlank() && (draftId.isBlank() || draftId == rec.optString("id"))) {
                rec.put("coverImageUrl", draftCover)
            }
        }
        return rec
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

        val sTime = if (!start.isNullOrBlank()) leaderTime(start) else ""
        return if (sTime.isNotBlank()) "$formattedDate - $sTime" else formattedDate
    }

    private fun homeUpcomingSecondRecord(): org.json.JSONObject? {
        val phtZone = java.util.TimeZone.getTimeZone("Asia/Manila")
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = phtZone }.format(java.util.Date())
        val published = localServices.filter { it.optString("status") == "Published" }
        val upcoming = published.filter { it.optString("date") >= today }.sortedWith(compareBy({ it.optString("date") }, { it.optString("start") }))
        if (upcoming.size > 1) {
            return upcoming[1]
        }
        return null
    }
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
        if (record != null) {
            selectedService = record.optString("title")
            preferences.edit {
                putString("serviceDate", record.optString("date"))
                putString("serviceTime", leaderTime(record.optString("start")) + " – " + leaderTime(record.optString("end")))
                putString("serviceVenue", record.optString("venue"))
            }
            if (isLeader) {
                leaderDraft = org.json.JSONObject(record.toString())
                activeServiceIndex = localServices.indexOf(record); serviceWizard = false; saveLeaderDraft()
            }
        } else {
            selectedService = "Sunday Worship Service"
            preferences.edit {
                putString("serviceDate", "May 18, 2025")
                putString("serviceTime", "6:00AM – 11:00AM")
                putString("serviceVenue", "Main Sanctuary")
            }
            if (isLeader) loadSampleLeaderService(selectedService, "2025-05-18", "6:00AM – 11:00AM")
        }
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
                add(Symbol(this@AdminActivity, "warning", Color.rgb(220, 38, 38)), 14, 14)
                addView(View(this@AdminActivity), LinearLayout.LayoutParams(dp(6), 1))
                add(text("Urgent Notice", 11, Color.rgb(185, 28, 28), bold = true))
            }
            row.addView(iconBadge)
            row.addView(View(this@AdminActivity), LinearLayout.LayoutParams(dp(10), 1))
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
        val greeting=vertical().apply { add(text("Welcome to Ministry",14,Color.WHITE)); space(4); add(text(displayName.ifBlank { "Leader" },23,Color.WHITE,id=UI.welcomeText)) }
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
            val imgView = ImageView(this@AdminActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                if (coverUrl.isNotBlank()) {
                    com.example.syncedn.data.storage.ImageLoader.loadProfilePhoto(this, coverUrl, placeholderResId = R.drawable.worship_hero_bg, circular = false, maxDimension = 800)
                } else {
                    setImageResource(R.drawable.worship_hero_bg)
                }
            }
            addView(imgView, FrameLayout.LayoutParams(-1, -1))

            val overlay = View(this@AdminActivity).apply {
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

                    val titleTv = TextView(this@AdminActivity).apply {
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

                    val subTv = TextView(this@AdminActivity).apply {
                        text = formattedSubtitle
                        setTextColor(Color.rgb(203, 213, 225))
                        textSize = 13f
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                    addView(subTv, LinearLayout.LayoutParams(-1, -2))
                    space(14)

                    val venuePill = FrameLayout(this@AdminActivity).apply {
                        background = bg(Color.argb(175, 26, 32, 44), 16, Color.argb(90, 255, 255, 255))
                        setPadding(dp(14), dp(6), dp(14), dp(6))
                        val vTv = TextView(this@AdminActivity).apply {
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

                val chevronBtn = FrameLayout(this@AdminActivity).apply {
                    background = bg(Color.argb(135, 30, 41, 59), 23, Color.argb(60, 255, 255, 255))
                    val arrow = TextView(this@AdminActivity).apply {
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
            val team=FrameLayout(this@AdminActivity).apply {
                id=UI.teamHubButton
                background=bg(Color.WHITE,24,Color.rgb(211,211,211))
                elevation=dp(4).toFloat();isClickable=true;isFocusable=true
                contentDescription="Worship Team, 10 members, view team"
            }
            val teamInfo=vertical().apply {
                setPadding(dp(13),dp(32),dp(12),dp(12))
                val titleRow=horizontal()
                titleRow.add(ImageView(this@AdminActivity).apply {
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
            val teamArrow=FrameLayout(this@AdminActivity).apply {background=bg(Color.WHITE,24,navy)}
            teamArrow.addView(Symbol(this@AdminActivity,"arrow",navy),FrameLayout.LayoutParams(dp(20),dp(20),Gravity.CENTER))
            team.addView(teamArrow,FrameLayout.LayoutParams(dp(40),dp(40),Gravity.BOTTOM or Gravity.END).apply {rightMargin=dp(23);bottomMargin=dp(20)})
            upper.add(team,0,170,0.63f)
            upper.add(View(this@AdminActivity),24,1)
            val rightColumn=vertical().apply {
                add(memberTile(UI.calendarButton,"calendar","Calendar"),-1,76);space(18)
                add(memberTile(UI.songsButton,"music","Songs"),-1,76)
            }
            upper.add(rightColumn,0,-2,0.37f)
            add(upper);space(18)
            if (isLeader) {
                add(horizontal().apply {
                    add(memberTile(View.NO_ID,"add","Create Service",11).apply {
                        setOnClickListener { beginLeaderService() }
                    }, 0, 56, 0.63f)
                    add(View(this@AdminActivity),24,1)
                    add(announcementHomeTile(),0,92,0.37f)
                }); space(18)
            }
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
                    val timeLabel = if(start.isNotBlank() && end.isNotBlank()) leaderTime(start) + " – " + leaderTime(end) else if (start.isNotBlank()) leaderTime(start) else ""
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
                    add(Symbol(this@AdminActivity, "arrow", Color.GRAY), 14, 20)
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
            upcoming.add(service,0,-2,if(isLeader) 1f else 0.63f)
            if (!isLeader) {
                upcoming.add(View(this@AdminActivity),24,1)
                upcoming.add(announcementHomeTile(),0,104,0.37f)
            }
            add(upcoming);space(44)
        }
        val nav=horizontal().apply { elevation=dp(8).toFloat();setBackgroundColor(Color.WHITE) }
        for ((id,symbol,label) in listOf(Triple(UI.homeTab,"home","Home"),Triple(UI.scheduleTab,"calendar","Schedule"),Triple(UI.teamsTab,"teams","Teams"),Triple(UI.settingsTab,"settings","Settings"))) {
            val c=if(id==UI.homeTab) Color.rgb(214,0,242) else navy
            val tab=vertical().apply { this.id=id;gravity=Gravity.CENTER;isClickable=true;isFocusable=true;contentDescription=label;add(Symbol(this@AdminActivity,symbol,c),24,24);space(2);add(text(label,9,c,centered=true)) }
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
                add(Symbol(this@AdminActivity,icon,color),24,24);space(2);add(text(label,9,color,centered=true))
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
        val col=vertical();scroll.addView(col,FrameLayout.LayoutParams(-1,-2));root.add(scroll,-1,0,1f)
        col.space(24)
        col.section(18).apply {
            val userRow=horizontal()
            userRow.add(image("profile_photo","Profile photo").apply {id=UI.settingsAvatar},78,78)
            val info=vertical().apply {
                setPadding(dp(20),0,0,0)
                add(text(fullName,14,Color.BLACK,true));space(8)
                add(text(displayName,12).apply {background=bg(Color.rgb(217,217,217),14);setPadding(dp(10),0,dp(10),0);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END},-1,24)
                space(10)
                add(text("Profile", 13, Color.BLACK, bold = true, centered = true, id = UI.openProfileButton).apply {
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
            notification.add(Symbol(this@AdminActivity,"bell",navy),30,30)
            notification.add(text("Notification",14,Color.BLACK,true).apply {setPadding(dp(10),0,0,0)},0,48,1f)
            notification.add(Switch(this@AdminActivity).apply {
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
        add(Symbol(this@AdminActivity,icon,Color.BLACK),30,30)
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
                    "1. Schedule & Lineups: Leaders can create services, assign team members, and build song lineups. Click any song to view lyrics, chords, transpose keys, and watch rehearsal videos.\n\n" +
                    "2. Availability: Set your unavailable dates in Calendar to help leaders schedule around your conflicts.\n\n" +
                    "3. Substitutions: If unable to serve or if you want to join a service, tap Request Substitution (Exclusion/Inclusion) on the service card.\n\n" +
                    "4. Team Chat: Tap Teams to chat with your ministry teammates, share rehearsal pictures, and manage roles.\n\n" +
                    "5. Notifications: Turn on Push Notifications in Settings to receive instant alerts on service changes and announcements."
                )
                .setPositiveButton("Close", null)
                .show()
        }
        click(UI.homeTab) {navigate("home")};click(UI.scheduleTab) {schedule()}
        click(UI.teamsTab) {navigate("teams")};click(UI.settingsTab) {toast("You are on Settings.")}
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
        col.add(text(fullName.ifBlank { displayName.ifBlank { "Leader" } }, 14, Color.BLACK, bold = true, centered = true)); col.space(3)
        col.add(text("Ministry Leader",12,Color.GRAY,centered=true));col.space(26)
        col.section(11).apply {
            add(text("Profile",15,Color.BLACK,true));space(12)
            add(profileField(UI.profileName,"Full Name",fullName,hint="Add your full name",maxLength=ValidationUtils.MAX_NAME_LENGTH));space(8)
            add(profileField(UI.profileUsername,"Username",displayName,hint="Add your username",maxLength=ValidationUtils.MAX_USERNAME_LENGTH));space(8)
            add(profileField(UI.profilePhone,"Phone Number",preferences.getString(SessionManager.KEY_PHONE,"").orEmpty(),"Add your phone number",InputType.TYPE_CLASS_PHONE,maxLength=ValidationUtils.MAX_PHONE_LENGTH));space(8)
            add(profileField(UI.profileEmail,"Email",preferences.getString(SessionManager.KEY_EMAIL,"").orEmpty(),kind=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,maxLength=ValidationUtils.MAX_EMAIL_LENGTH));space(16)
            space(32)
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
                    val activeMinistry = preferences.getString(SessionManager.KEY_MINISTRY_ROLE, "Worship Leader").orEmpty().ifBlank { "Worship Leader" }
                    val activePhoto = preferences.getString(SessionManager.KEY_PHOTO_URI, "").orEmpty()
                    val profileObj = com.example.syncedn.data.model.UserProfile(
                        uid = uid,
                        username = displayName,
                        email = emailStr,
                        role = "Leader",
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

            val localMsg = ChatMessage(
                id = localMsgId,
                teamCode = currentCode,
                senderUid = currentUid,
                senderName = fullName.ifBlank { displayName.ifBlank { "Leader" } },
                senderRole = "Worship Leader",
                senderPhotoUri = currentPhoto,
                mediaUrl = uri.toString(),
                timestamp = System.currentTimeMillis()
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
        if (requestCode == 302 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}

            val progress = @Suppress("DEPRECATION") android.app.ProgressDialog(this).apply {
                setMessage("Uploading cover photo to Cloudinary...")
                setCancelable(false)
                show()
            }

            CloudinaryUploader.uploadProfileImage(this, uri, maxDimension = 1024) { result ->
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        try { progress.dismiss() } catch (_: Throwable) {}
                    }
                    result.onSuccess { remoteUrl ->
                        leaderDraft.put("coverImageUrl", remoteUrl)
                        val srvId = leaderDraft.optString("id").ifBlank { "srv_" + System.currentTimeMillis() }
                        leaderDraft.put("id", srvId)
                        val record = org.json.JSONObject(leaderDraft.toString())
                        val existingIdx = localServices.indexOfFirst { it.optString("id") == srvId }
                        if (existingIdx >= 0) {
                            localServices[existingIdx] = record
                            activeServiceIndex = existingIdx
                        } else {
                            localServices.add(record)
                            activeServiceIndex = localServices.lastIndex
                        }
                        val records = org.json.JSONArray()
                        localServices.forEach { records.put(it) }
                        preferences.edit().putString("leaderServices", records.toString()).apply()
                        saveLeaderDraft()
                        storeService(leaderDraft.optString("status").ifBlank { "Published" }, navigateAfter = false)
                        toast("Cover photo uploaded to Cloudinary!")
                        if (page == "createService" || page == "reviewService" || page == "service" || page == "home") {
                            navigate(page)
                        }
                    }.onFailure { err ->
                        toast("Upload failed: ${err.message ?: "Network error"}. Please try again.")
                    }
                }
            }
            return
        }
        if(requestCode==300 && resultCode==RESULT_OK) {
            val uri=data?.data ?: return
            try {
                try { contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch(_: Exception) {}
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
                        val activeMinistry = preferences.getString(SessionManager.KEY_MINISTRY_ROLE, "Worship Leader").orEmpty().ifBlank { "Worship Leader" }
                        val currentProfile = com.example.syncedn.data.model.UserProfile(
                            uid = uid,
                            username = displayName,
                            email = currentEmail,
                            role = "Leader",
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
    // ---- ADMIN / LEADER DEMO: editable local data, with the same native layout helpers. ----
    private val isLeader = true
    private var serviceWizard = false
    private var servicesListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var substitutionsListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var rosterListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var userMembershipListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var activeServiceIndex = -1
    private var roleMember = "Angelo Daniel"
    private var roleMemberUid = ""
    private var roleMemberEmail = ""
    private var teamRoster: List<TeamMember> = emptyList()

    private fun getActiveRoster(): List<TeamMember> {
        val currentCode = preferences.getString("teamCode", "").orEmpty()
            .ifBlank { FirestoreManager.findCodeByTeamName(teamName).orEmpty() }
        val memberRolesMap = try {
            org.json.JSONObject(preferences.getString(SessionManager.KEY_MEMBER_ROLES, "{}").orEmpty())
        } catch (_: Exception) { org.json.JSONObject() }

        fun applyOverrides(list: List<TeamMember>): List<TeamMember> {
            val selfPhoto = preferences.getString("photoUri", null)?.takeIf { it.isNotBlank() }
            val selfUid = preferences.getString(SessionManager.KEY_USER_ID, "").orEmpty()
            return list.map { m ->
                val emailLower = m.email.trim().lowercase()
                val override = memberRolesMap.optString(m.uid).ifBlank {
                    memberRolesMap.optString(emailLower).ifBlank {
                        memberRolesMap.optString(m.fullName).ifBlank {
                            memberRolesMap.optString(m.username).ifBlank {
                                preferences.getString("email_ministry_$emailLower", "").orEmpty()
                            }
                        }
                    }
                }
                var updated = if (override.isNotBlank()) {
                    val isOverrideLeader = override.equals("Substitute Leader", ignoreCase = true) ||
                                           override.equals("Worship Leader", ignoreCase = true) ||
                                           emailLower == "ryanlloydgenturo@gmail.com" ||
                                           m.fullName.equals("Ryan Lloyd Genturo", ignoreCase = true)
                    m.copy(ministryRole = override, role = if (isOverrideLeader) "Leader" else "Member")
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
                val mergedRanges = (updated.unavailableRanges + TeamRepository.getMemberUnavailableRanges(
                    uid = updated.uid,
                    preferences = preferences,
                    email = emailLower,
                    name = updated.fullName.ifBlank { updated.username }
                )).distinctBy { it.id.ifBlank { "${it.startDate}_${it.endDate}_${it.reason}" } }
                if (mergedRanges != updated.unavailableRanges) {
                    updated = updated.copy(unavailableRanges = mergedRanges)
                }
                updated
            }
        }

        val cached = TeamRepository.getCachedRoster(currentCode, preferences)
        if (cached.isNotEmpty()) return applyOverrides(cached)
        if (teamRoster.isNotEmpty()) return applyOverrides(teamRoster)
        val currentUid = preferences.getString("userId", "").orEmpty()
        val currentName = fullName.ifBlank { displayName }
        val userRole = preferences.getString("role", if (isLeader) "Leader" else "Member").orEmpty()
        val ministry = preferences.getString("ministryRole", if (isLeader) "Worship Leader" else "Member").orEmpty()
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
            return applyOverrides(listOf(self))
        }
        return applyOverrides(ministryMembers.map { (name, role, badge) ->
            TeamMember(
                uid = name.lowercase().replace(" ", "_"),
                fullName = name,
                username = name.split(" ").firstOrNull() ?: name,
                email = "${name.lowercase().replace(" ", "")}@syncedn.org",
                role = if (badge == "Admin") "Leader" else "Member",
                ministryRole = role,
                teamCode = currentCode
            )
        })
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
        } else if ((page == "teams" && teamTab != "Team Chat") || page in listOf("memberAvailability", "serviceAssignments", "reviewService", "calendar")) {
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
        TeamRepository.getTeamCustomRoles(currentCode) { result ->
            runOnUiThread {
                if (!isFinishing && !isDestroyed && activeTeamCode() == currentCode) {
                    result.getOrNull()?.takeIf { it.isNotEmpty() }?.let { roles ->
                        leaderRoles.clear()
                        leaderRoles.addAll(roles)
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

        TeamRepository.getServices(currentCode, isLeader = true, preferences) { result ->
            isRefreshingServices = false
            result.getOrNull()?.let { list ->
                if (list.isNotEmpty()) {
                    val newRecords = list.map { srv ->
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
                    runOnUiThread {
                        val changed = (newRecords.toString() != localServices.toString())
                        localServices.clear()
                        localServices.addAll(newRecords)
                        if (changed) {
                            if (page in listOf("home", "schedule")) {
                                navigate(page, triggerRefresh = false)
                            }
                        }
                    }
                }
            }
        }
    }

    private var leaderDraft = org.json.JSONObject()
    private val localServices = mutableListOf<org.json.JSONObject>()
    private val leaderRoles = mutableListOf("Worship Leader", "Substitute Leader", "Vocalist", "Guitarist", "Keyboardist", "Bassist", "Drummer", "Sound Operator")
    private fun initializeLeaderDemo() {
        serviceWizard = preferences.getBoolean("serviceWizard", false)
        activeServiceIndex = preferences.getInt("activeServiceIndex", -1)
        leaderDraft = try { org.json.JSONObject(preferences.getString("leaderDraft", "{}").orEmpty()) }
        catch (_: Exception) { org.json.JSONObject() }
        localServices.clear()
        val currentCode = preferences.getString("teamCode", "").orEmpty()
            .ifBlank { FirestoreManager.findCodeByTeamName(teamName).orEmpty() }
        val cached = TeamRepository.getCachedServices(currentCode, preferences)
        if (cached.isNotEmpty()) {
            cached.forEach { srv ->
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
                localServices.add(obj)
            }
        } else {
            try {
                val records = org.json.JSONArray(preferences.getString("leaderServices", "[]"))
                repeat(records.length()) { localServices.add(records.getJSONObject(it)) }
            } catch (_: Exception) {}
        }
        try {
            val roles = org.json.JSONArray(preferences.getString("leaderRoles", "[]"))
            if (roles.length() > 0) { leaderRoles.clear(); repeat(roles.length()) { leaderRoles.add(roles.getString(it)) } }
        } catch (_: Exception) { toast("Could not restore some local demo data.") }
        if (leaderDraft.length() == 0) resetLeaderDraft()
    }
    private fun resetLeaderDraft() {
        activeServiceIndex = -1
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        leaderDraft = org.json.JSONObject().put("title", "").put("date", today)
            .put("start", "09:00").put("end", "10:00").put("venue", "")
            .put("assignments", org.json.JSONArray())
            .put("songs", org.json.JSONArray(listOf("Amazing Grace", "Way Maker", "Goodness of God", "Build My Life", "Praise")))
        saveLeaderDraft()
    }
    private fun saveLeaderDraft() {
        preferences.edit().putString("leaderDraft", leaderDraft.toString())
            .putBoolean("serviceWizard", serviceWizard).putInt("activeServiceIndex", activeServiceIndex).apply()
    }
    private fun beginLeaderService() {
        serviceWizard = true; resetLeaderDraft(); navigate("createService")
    }
    private fun loadSampleLeaderService(title: String, date: String, time: String) {
        serviceWizard = false; activeServiceIndex = -1
        leaderDraft = org.json.JSONObject().put("title", title).put("date", date)
            .put("start", if (time.contains("7:00PM")) "19:00" else if(time.contains("9:00AM")) "09:00" else "06:00")
            .put("end", if (time.contains("7:00PM")) "21:00" else if(time.contains("9:00AM")) "10:00" else "11:00")
            .put("venue", if (title == "Team Meeting") "Fellowship Hall" else "Main Sanctuary")
            .put("songs", org.json.JSONArray(lineupSongs))
        val assigned = org.json.JSONArray()
        ministryMembers.take(4).forEach { (name, role, _) ->
            assigned.put(org.json.JSONObject().put("name", name).put("role", role))
        }
        leaderDraft.put("assignments", assigned); saveLeaderDraft()
    }
    private fun draftAssignments(): org.json.JSONArray = leaderDraft.optJSONArray("assignments") ?: org.json.JSONArray().also {
        leaderDraft.put("assignments", it)
    }
    private fun draftSongs(): org.json.JSONArray = leaderDraft.optJSONArray("songs") ?: org.json.JSONArray().also {
        leaderDraft.put("songs", it)
    }
    data class MemberAvailabilityItem(
        val member: TeamMember,
        val name: String,
        val role: String,
        val status: String,
        val conflictReason: String = "",
        val conflictRange: String = ""
    )

    private fun leaderMemberAvailability(): List<MemberAvailabilityItem> {
        val roster = getActiveRoster()
        val removed = preferences.getStringSet("removedMembers", emptySet()).orEmpty()
        val date = leaderDraft.optString("date")
        val legacyStart = preferences.getString("unavailableStart", "").orEmpty()
        val legacyEnd = preferences.getString("unavailableEnd", "").orEmpty()
        val legacyReason = preferences.getString("unavailableReason", "").orEmpty()

        return roster.filter { !it.removedByLeader }.map { member ->
            val name = member.fullName.ifBlank { member.username.ifBlank { "Member" } }
            val role = member.ministryRole.ifBlank { if (member.role.equals("Leader", true)) "Worship Leader" else "Member" }
            val ranges = (member.unavailableRanges + TeamRepository.getMemberUnavailableRanges(
                uid = member.uid,
                preferences = preferences,
                email = member.email,
                name = name
            )).distinctBy { it.id.ifBlank { "${it.startDate}_${it.endDate}_${it.reason}" } }
            val conflict = ranges.find { it.includes(date) }
            val isLegacyUnavailable = (name == fullName || member.uid == preferences.getString("userId", "")) &&
                    legacyStart.isNotBlank() && date >= legacyStart && date <= legacyEnd
            val isUnavailable = conflict != null || isLegacyUnavailable
            val status = if (isUnavailable) "Unavailable" else "Available"
            val reason = conflict?.reason?.ifBlank { "Unavailable" } ?: if (isLegacyUnavailable) legacyReason.ifBlank { "Unavailable" } else ""
            val rangeStr = if (conflict != null) "${conflict.startDate} – ${conflict.endDate}" else if (isLegacyUnavailable) "$legacyStart – $legacyEnd" else ""

            MemberAvailabilityItem(
                member = member,
                name = name,
                role = role,
                status = status,
                conflictReason = reason,
                conflictRange = rangeStr
            )
        }
    }

    private fun leaderRoster(): List<Triple<String, String, String>> {
        return leaderMemberAvailability().map { Triple(it.name, it.role, it.status) }
    }
    private fun leaderTime(value: String): String = try {
        val pieces = value.split(":"); val hour = pieces[0].toInt()
        "${if (hour % 12 == 0) 12 else hour % 12}:${pieces[1]}${if (hour < 12) "AM" else "PM"}"
    } catch (_: Exception) { value }
    private fun timeField(initial: String): TextView = text(initial, 13, navy).apply {
        background = bg(Color.rgb(235, 235, 240), 10); setPadding(dp(12), 0, dp(12), 0)
        contentDescription = "Choose time"; isFocusable = true
        setOnClickListener {
            val parts = text.toString().split(":")
            android.app.TimePickerDialog(this@AdminActivity, { _, hour, minute ->
                text = "%02d:%02d".format(java.util.Locale.US, hour, minute)
            }, parts[0].toInt(), parts[1].toInt(), false).show()
        }
    }
    private fun serviceSummary(canEdit: Boolean = true): View = panel().apply {
        val coverUrl = leaderDraft.optString("coverImageUrl").orEmpty()
        if (coverUrl.isNotBlank()) {
            val heroImg = ImageView(this@AdminActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = bg(Color.rgb(240, 240, 245), 12)
                clipToOutline = true
                outlineProvider = ViewOutlineProvider.BACKGROUND
                com.example.syncedn.data.storage.ImageLoader.loadProfilePhoto(this, coverUrl, placeholderResId = R.drawable.worship_hero_bg, circular = false, maxDimension = 800)
            }
            add(heroImg, -1, dp(140))
            space(12)
        }
        add(horizontal().apply {
            add(vertical().apply {
                add(text(leaderDraft.optString("title", "Sunday Worship Service").ifBlank { "New Service" }, 15, navy, true))
                space(6); add(text(leaderDraft.optString("date"), 12, navy))
                space(4); add(text(leaderTime(leaderDraft.optString("start")) + " – " + leaderTime(leaderDraft.optString("end")), 12, navy))
                space(4); add(text(leaderDraft.optString("venue"), 12, navy))
            }, 0, -2, 1f)
            if (canEdit) {
                add(text("Edit Details", 12, purple, bold = true, centered = true).apply {
                    background = bg(Color.rgb(243, 232, 255), 8)
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        serviceWizard = false
                        saveLeaderDraft()
                        navigate("createService")
                    }
                }, -2, -2)
            }
        })
    }
    private fun stepActions(col: LinearLayout, back: () -> Unit, nextLabel: String = "Next", next: () -> Unit) {
        col.space(24)
        col.add(dualButtons("Back", back, nextLabel, next))
    }
    private fun createServiceView(): View = memberPage("New Service", if (serviceWizard) "home" else "service") { col ->
        col.space(16)
        val title = plainInput("Service title").apply { setText(leaderDraft.optString("title")) }
        val date = dateField(leaderDraft.optString("date", "2026-10-11"))
        val start = timeField(leaderDraft.optString("start", "09:00"))
        val end = timeField(leaderDraft.optString("end", "10:00"))
        val venue = plainInput("Venue").apply { setText(leaderDraft.optString("venue")) }
        col.add(text("Service Title*", 12, navy)); col.space(8); col.add(title, -1, 48); col.space(20)
        col.add(text("Date*", 12, navy)); col.space(8); col.add(date, -1, 48); col.space(20)
        col.add(horizontal().apply {
            add(vertical().apply { add(text("Start Time*", 12, navy)); space(8); add(start, -1, 48) }, 0, -2, 1f)
            add(View(this@AdminActivity), 16, 1)
            add(vertical().apply { add(text("End Time*", 12, navy)); space(8); add(end, -1, 48) }, 0, -2, 1f)
        }); col.space(20)
        col.add(text("Venue*", 12, navy)); col.space(8); col.add(venue, -1, 48)
        col.space(20)
        col.add(text("Cover Photo (Optional)", 12, navy)); col.space(8)
        val coverContainer = horizontal().apply {
            gravity = Gravity.CENTER_VERTICAL
            background = bg(Color.WHITE, 12, Color.rgb(226, 232, 240))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            val currentCover = leaderDraft.optString("coverImageUrl").orEmpty()
            val previewImg = ImageView(this@AdminActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = bg(Color.rgb(240, 240, 245), 8)
                clipToOutline = true
                outlineProvider = ViewOutlineProvider.BACKGROUND
                if (currentCover.isNotBlank()) {
                    ImageLoader.loadProfilePhoto(this, currentCover, circular = false, maxDimension = 200)
                } else {
                    val fallback = (image("hero_container").drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    if (fallback != null) setImageBitmap(fallback)
                }
            }
            add(previewImg, 48, 48)
            space(12)
            val labelCol = vertical().apply {
                add(text(if (currentCover.isNotBlank()) "Cover Photo Attached" else "No Cover Photo Selected", 13, navy, bold = true))
                space(2)
                add(text("Tap to choose image from gallery", 11, Color.rgb(100, 116, 139)))
            }
            add(labelCol, 0, -2, 1f)
            val pickBtn = Button(this@AdminActivity).apply {
                text = if (currentCover.isNotBlank()) "Change" else "Choose"
                textSize = 12f
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = bg(purple, 8)
                setOnClickListener {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "image/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    leaderDraft.put("title", title.text.toString().trim()).put("date", date.text.toString())
                        .put("start", start.text.toString()).put("end", end.text.toString())
                        .put("venue", venue.text.toString().trim())
                    saveLeaderDraft()
                    startActivityForResult(intent, 302)
                }
            }
            add(pickBtn, -2, 36)
        }
        col.add(coverContainer, -1, -2)
        stepActions(col, { navigate(if (serviceWizard) "home" else "service") }, if (serviceWizard) "Next" else "Save") {
            when {
                title.text.isBlank() -> invalid(title, "Enter a service title.")
                venue.text.isBlank() -> invalid(venue, "Enter a venue.")
                start.text.toString() >= end.text.toString() -> toast("End time must be after start time.")
                else -> {
                    leaderDraft.put("title", title.text.toString().trim()).put("date", date.text.toString())
                        .put("start", start.text.toString()).put("end", end.text.toString())
                        .put("venue", venue.text.toString().trim())
                    if (serviceWizard) {
                        saveLeaderDraft(); navigate("memberAvailability")
                    } else {
                        saveLeaderDraft()
                        storeService(leaderDraft.optString("status").ifBlank { "Published" }, navigateAfter = false)
                        toast("Service details updated.")
                        navigate("service")
                    }
                }
            }
        }
    }
    private fun memberAvailabilityView(): View = memberPage("Member Availability", "createService") { col ->
        col.add(serviceSummary()); col.space(16)
        val items = leaderMemberAvailability()
        val availableCount = items.count { it.status == "Available" }
        val unavailableCount = items.count { it.status == "Unavailable" }
        col.add(horizontal().apply {
            add(panel().apply {
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(14), dp(16), dp(14))
                add(text(availableCount.toString(), 26, Color.rgb(22, 163, 74), bold = true, centered = true))
                space(4)
                add(text("Available", 13, Color.rgb(22, 163, 74), bold = true, centered = true))
            }, 0, -2, 1f)
            add(View(this@AdminActivity), 16, 1)
            add(panel().apply {
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(14), dp(16), dp(14))
                add(text(unavailableCount.toString(), 26, Color.rgb(220, 38, 38), bold = true, centered = true))
                space(4)
                add(text("Unavailable", 13, Color.rgb(220, 38, 38), bold = true, centered = true))
            }, 0, -2, 1f)
        }); col.space(20)
        col.add(text("Team Member Responses", 13, navy, true)); col.space(12)
        if (items.isEmpty()) {
            col.add(text("No members in team roster yet.", 13, Color.GRAY)); col.space(12)
        } else {
            items.sortedBy { if (it.status == "Available") 0 else 1 }.forEach { item ->
                val isAvail = item.status == "Available"
                val card = panel(Color.rgb(226, 232, 240)).apply {
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    val row = horizontal().apply { gravity = Gravity.CENTER_VERTICAL }
                    val pUri = getActiveRoster().find { it.fullName.equals(item.name, true) || it.username.equals(item.name, true) }?.photoUri
                    row.add(memberAvatarView(pUri, 44), 44, 44)
                    val info = vertical().apply {
                        setPadding(dp(12), 0, dp(8), 0)
                        add(text(item.name, 15, navy, bold = true))
                        space(2)
                        add(text(if (isAvail) "Available" else "Unavailable${if (item.conflictReason.isNotBlank()) ": ${item.conflictReason}" else ""}",
                            13, if (isAvail) Color.rgb(22, 163, 74) else Color.rgb(100, 116, 139)))
                    }
                    row.add(info, 0, -2, 1f)
                    val pillBadge = text(if (isAvail) "Available" else "Unavailable", 12,
                        if (isAvail) Color.rgb(22, 163, 74) else Color.rgb(185, 28, 28), bold = true, centered = true).apply {
                        background = if (isAvail) bg(Color.rgb(220, 252, 231), 14, Color.rgb(22, 163, 74))
                        else bg(Color.rgb(254, 226, 226), 14, Color.rgb(220, 38, 38))
                        setPadding(dp(12), dp(6), dp(12), dp(6))
                    }
                    row.add(pillBadge, -2, -2)
                    add(row)
                }
                col.add(card)
                col.space(10)
            }
        }
        col.space(16)
        val assignBtn = Button(this).apply {
            text = "Assign Roles"
            isAllCaps = false
            background = bg(purple, 24)
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            minimumHeight = dp(48)
            setOnClickListener { navigate("serviceAssignments") }
        }
        col.add(assignBtn, -1, 48)
    }
    private fun chooseAssignment() {
        val assigned = draftAssignments()
        val names = (0 until assigned.length()).map { assigned.getJSONObject(it).optString("name") }
        val choices = leaderRoster().filter { it.third == "Available" && it.first !in names }
        if (choices.isEmpty()) { toast("All available members are already assigned."); return }
        AlertDialog.Builder(this).setTitle("Assign available member")
            .setItems(choices.map { it.first }.toTypedArray()) { _, position ->
                val member = choices[position]
                chooseRole(member.first, member.second) { role ->
                    val found = getActiveRoster().find { it.fullName.equals(member.first, true) || it.username.equals(member.first, true) }
                    assigned.put(org.json.JSONObject()
                        .put("name", member.first)
                        .put("role", role)
                        .put("uid", found?.uid.orEmpty())
                        .put("photoUri", found?.photoUri.orEmpty()))
                    saveLeaderDraft(); navigate("serviceAssignments")
                }
            }.setNegativeButton("Cancel", null).show()
    }
    private fun chooseRole(name: String, initial: String, onSave: (String) -> Unit) {
        var selected = initial.takeIf { it in leaderRoles } ?: leaderRoles.first()
        AlertDialog.Builder(this).setTitle("Role for $name")
            .setSingleChoiceItems(leaderRoles.toTypedArray(), leaderRoles.indexOf(selected)) { _, position -> selected = leaderRoles[position] }
            .setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ -> onSave(selected) }.show()
    }
    private fun serviceAssignmentsView(): View = memberPage("Service Assignments",
        if (serviceWizard) "memberAvailability" else "service") { col ->
        
        // Top right plus icon header action bar matching Figma Image 3
        col.add(horizontal().apply {
            setPadding(0, 0, 0, dp(4))
            add(View(this@AdminActivity), 0, 1, 1f)
            val plusAction = FrameLayout(this@AdminActivity).apply {
                val icon = text("+", 32, Color.rgb(15, 23, 42), bold = false, centered = true)
                addView(icon, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
                setOnClickListener { chooseAssignment() }
                contentDescription = "Assign Member"
                isClickable = true
                isFocusable = true
            }
            add(plusAction, 44, 44)
        })
        col.space(8)

        val assigned = draftAssignments()
        if (assigned.length() == 0) {
            val emptyCard = vertical().apply {
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(14).toFloat()
                    setStroke(dp(1), Color.rgb(226, 232, 240))
                }
                setPadding(dp(20), dp(24), dp(20), dp(24))
                gravity = Gravity.CENTER
                add(text("No members assigned yet.", 14, Color.rgb(100, 116, 139), centered = true))
                space(6)
                add(text("Tap + in the top right to assign members.", 12, Color.rgb(148, 163, 184), centered = true))
            }
            col.add(emptyCard, -1, -2)
            col.space(16)
        }

        val availabilityMap = leaderMemberAvailability().associateBy { it.name }
        repeat(assigned.length()) { index ->
            val member = assigned.getJSONObject(index)
            val name = member.optString("name")
            val role = member.optString("role")
            val uid = member.optString("uid")
            val photoUri = member.optString("photoUri").ifBlank { findMemberPhotoUri(uid, name) }
            val status = availabilityMap[name]?.status ?: "Available"
            val isUnavailable = status == "Unavailable"

            val card = FrameLayout(this@AdminActivity).apply {
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(14).toFloat()
                    setStroke(dp(1), if (isUnavailable) Color.RED else Color.rgb(226, 232, 240))
                }
                elevation = dp(3).toFloat()
                outlineProvider = ViewOutlineProvider.BACKGROUND
            }
            val content = horizontal().apply {
                setPadding(dp(16), dp(12), dp(14), dp(12))
                add(memberAvatarView(photoUri, 48), 48, 48)
                space(14)
                val info = vertical().apply {
                    add(text(name, 15, Color.rgb(15, 23, 42), bold = true))
                    space(3)
                    val roleLabel = if (isUnavailable) "$role • Conflict on service date" else role
                    add(text(roleLabel, 13, if (isUnavailable) Color.RED else Color.rgb(100, 116, 139)))
                }
                add(info, 0, -2, 1f)

                val dotsBtn = FrameLayout(this@AdminActivity).apply {
                    val dots = text("⋮", 22, Color.rgb(15, 23, 42), bold = true, centered = true)
                    addView(dots, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        AlertDialog.Builder(this@AdminActivity).setTitle(name).setItems(arrayOf("Change Role", "Remove Assignment")) { _, choice ->
                            if (choice == 0) chooseRole(name, role) { newRole ->
                                member.put("role", newRole); saveLeaderDraft(); navigate("serviceAssignments")
                            } else {
                                assigned.remove(index); saveLeaderDraft(); navigate("serviceAssignments")
                            }
                        }.show()
                    }
                }
                add(dotsBtn, 40, 40)
            }
            card.addView(content)
            col.add(card, -1, -2)
            col.space(12)
        }

        col.space(8)
        stepActions(col, { navigate(if (serviceWizard) "memberAvailability" else "service") },
            if (serviceWizard) "Next" else "Save") {
            if (assigned.length() == 0) toast("Assign at least one member.")
            else { saveLeaderDraft(); navigate(if (serviceWizard) "adminLineup" else "service") }
        }
    }
    private fun leaderServiceView(): View = memberPage("Service", serviceReturnPage, "Schedule") { col ->
        col.add(serviceSummary()); col.space(16)
        col.add(dualButtons("Songs", { songsReturnPage = "service"; navigate("songs") }, "Song Lineups", { serviceWizard = false; saveLeaderDraft(); navigate("adminLineup") }))
        col.space(24)
        col.add(horizontal().apply {
            add(text("Assigned Members and their Roles", 12, navy, true), 0, -2, 1f)
            add(text("Edit", 13, purple, bold = true, centered = true).apply {
                setOnClickListener { serviceWizard = false; saveLeaderDraft(); navigate("serviceAssignments") }
            }, 48, 48)
        })
        val assigned = draftAssignments()
        val activeRoster = getActiveRoster()
        repeat(assigned.length()) {
            val member = assigned.getJSONObject(it)
            val name = member.optString("name")
            val role = member.optString("role")
            val uid = member.optString("uid")
            val photoUri = member.optString("photoUri").ifBlank { findMemberPhotoUri(uid, name) }
            val rosterMatch = activeRoster.find { m ->
                (uid.isNotBlank() && m.uid == uid) ||
                m.fullName.equals(name, ignoreCase = true) ||
                m.username.equals(name, ignoreCase = true)
            }
            val isLeaderBadge = rosterMatch?.ministryRole.equals("Worship Leader", ignoreCase = true) ||
                                rosterMatch?.ministryRole.equals("Substitute Leader", ignoreCase = true) ||
                                name.equals("Ryan Lloyd Genturo", ignoreCase = true) ||
                                rosterMatch?.email.equals("ryanlloydgenturo@gmail.com", ignoreCase = true)
            col.add(memberRow(name, role, if (isLeaderBadge) "Leader" else "Member", photoUri = photoUri, uid = uid))
        }
        col.space(20)
        col.add(memberButton("Schedule Rehearsal", navy) { navigate("scheduleRehearsal") }, -1, 48); col.space(12)
        col.add(memberButton("Review / Save Changes") { navigate("reviewService") }, -1, 48); col.space(12)
        col.add(text("Delete Service", 13, Color.rgb(220, 38, 38), bold = true, centered = true).apply {
            background = bg(Color.rgb(254, 242, 242), 12, Color.rgb(252, 165, 165))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                AlertDialog.Builder(this@AdminActivity)
                    .setTitle("Delete Service")
                    .setMessage("Are you sure you want to delete this service schedule? This cannot be undone.")
                    .setPositiveButton("Delete") { _, _ ->
                        val activeCode = preferences.getString("teamCode", "").orEmpty()
                        val sid = leaderDraft.optString("id", "")
                        if (sid.isNotBlank()) {
                            TeamRepository.deleteService(activeCode, sid, preferences) {
                                runOnUiThread { refreshTeamServices(force = true) }
                            }
                            val iter = localServices.iterator()
                            while (iter.hasNext()) {
                                if (iter.next().optString("id") == sid) iter.remove()
                            }
                        }
                        toast("Service deleted.")
                                navigate("schedule")
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }, -1, 44)
    }
    private fun addLineupSong() {
        val available = (songCatalog().map { it.first } + listOf("Amazing Grace", "Goodness of God", "Build My Life")).distinct()
        AlertDialog.Builder(this).setTitle("Add Song").setItems(available.toTypedArray()) { _, index ->
            val songName = available[index]
            val songs = draftSongs()
            val alreadyIn = (0 until songs.length()).any { idx ->
                val itm = songs.get(idx)
                val t = if (itm is org.json.JSONObject) itm.optString("title") else itm.toString()
                t.equals(songName, ignoreCase = true)
            }
            if (alreadyIn) {
                toast("This song is already in the lineup.")
            } else {
                val activeCode = preferences.getString("teamCode", "").orEmpty()
                val cachedSong = TeamRepository.getCachedSongs(activeCode, preferences).find { it.title.equals(songName, true) }
                val newObj = org.json.JSONObject().put("title", songName)
                    .put("artist", cachedSong?.artist ?: songCatalog().find { it.first == songName }?.second ?: "")
                    .put("key", cachedSong?.key ?: "C")
                    .put("bpm", cachedSong?.bpm ?: 74)
                songs.put(newObj)
                saveLeaderDraft()
                navigate("adminLineup")
            }
        }.setNegativeButton("Cancel", null).show()
    }
    private fun adminLineupView(): View = memberPage("Song Lineup", if (serviceWizard) "serviceAssignments" else "service") { col ->
        col.add(text(leaderDraft.optString("title"), 13, navy, true)); col.space(20)
        val activeCode = preferences.getString("teamCode", "").orEmpty()
        val allCatalogSongs = TeamRepository.getCachedSongs(activeCode, preferences)
        val songs = draftSongs()
        repeat(songs.length()) { index ->
            val rawItem = songs.get(index)
            val unpacked = com.example.syncedn.data.model.ServiceSong.unpack(rawItem)
            val matchedSong = allCatalogSongs.find { it.title.equals(unpacked.title, true) }
            val songTitle = unpacked.title
            val songArtist = unpacked.artist.ifBlank { matchedSong?.artist.orEmpty() }
            val songKey = unpacked.key.ifBlank { matchedSong?.key.orEmpty() }.ifBlank { "C" }
            val songBpm = if (unpacked.bpm > 0) unpacked.bpm else (matchedSong?.bpm?.takeIf { it > 0 } ?: 74)

            // Auto-heal stringified JSON in draftSongs in memory
            if (rawItem is String && rawItem.contains("\"title\"")) {
                try {
                    songs.put(index, org.json.JSONObject()
                        .put("title", songTitle)
                        .put("artist", songArtist)
                        .put("key", songKey)
                        .put("bpm", songBpm))
                    saveLeaderDraft()
                } catch (_: Exception) {}
            }

            val subtitleParts = mutableListOf<String>()
            if (songArtist.isNotBlank()) subtitleParts.add(songArtist)
            if (songKey.isNotBlank()) subtitleParts.add("Key: $songKey")
            if (songBpm > 0) subtitleParts.add("$songBpm BPM")
            val subtitleText = subtitleParts.joinToString(" • ")

            col.add(panel().apply {
                add(horizontal().apply {
                    add(text("${index + 1}.", 14, navy, true), 28, 48)
                    add(vertical().apply {
                        setPadding(dp(4), 0, dp(8), 0)
                        add(text(songTitle, 14, navy, true))
                        if (subtitleText.isNotBlank()) {
                            space(3)
                            add(text(subtitleText, 11, Color.GRAY))
                        }
                        isClickable = true
                        setOnClickListener {
                            songDetailsBack = "adminLineup"
                            selectedSong = songTitle
                            navigate("songDetails")
                        }
                    }, 0, -2, 1f)
                    add(text("⋮", 24, navy, centered = true).apply {
                        contentDescription = "Edit $songTitle"
                        setOnClickListener {
                            val menuOptions = arrayOf("Move to Position...", "Move Up", "Move Down", "Remove")
                                    AlertDialog.Builder(this@AdminActivity).setTitle(songTitle)
                                        .setItems(menuOptions) { _, choice ->
                                            when (choice) {
                                                0 -> {
                                                    val positions = (1..songs.length()).map { "Position #$it" }.toTypedArray()
                                                    AlertDialog.Builder(this@AdminActivity)
                                                        .setTitle("Move '$songTitle' to:")
                                                        .setItems(positions) { _, posIdx ->
                                                            if (posIdx != index) {
                                                                val item = songs.get(index)
                                                                songs.remove(index)
                                                                val newArray = org.json.JSONArray()
                                                                for (p in 0 until songs.length()) {
                                                                    if (p == posIdx) newArray.put(item)
                                                                    newArray.put(songs.get(p))
                                                                }
                                                                if (posIdx >= songs.length()) newArray.put(item)
                                                                leaderDraft.put("songs", newArray)
                                                                saveLeaderDraft()
                                                                navigate("adminLineup")
                                                            }
                                                        }.show()
                                                }
                                                1 -> {
                                                    if (index > 0) {
                                                        val currentItem = songs.get(index)
                                                        val otherItem = songs.get(index - 1)
                                                        songs.put(index - 1, currentItem)
                                                        songs.put(index, otherItem)
                                                        saveLeaderDraft(); navigate("adminLineup")
                                                    }
                                                }
                                                2 -> {
                                                    if (index < songs.length() - 1) {
                                                        val currentItem = songs.get(index)
                                                        val otherItem = songs.get(index + 1)
                                                        songs.put(index + 1, currentItem)
                                                        songs.put(index, otherItem)
                                                        saveLeaderDraft(); navigate("adminLineup")
                                                    }
                                                }
                                                3 -> {
                                                    songs.remove(index)
                                                    saveLeaderDraft(); navigate("adminLineup")
                                                }
                                            }
                                        }.show()
                        }
                    }, 48, 48)
                })
            }); col.space(12)
        }
        col.space(20); col.add(memberButton("+ Add Song") { addLineupSong() }, -1, 48)
        stepActions(col, { navigate(if (serviceWizard) "serviceAssignments" else "service") }, if (serviceWizard) "Next" else "Save") {
            if (songs.length() == 0) toast("Add at least one song.")
            else { saveLeaderDraft(); navigate(if (serviceWizard) "scheduleRehearsal" else "service") }
        }
    }
    private fun rehearsalView(): View = memberPage("Schedule Rehearsal", if (serviceWizard) "adminLineup" else "service") { col ->
        val date = dateField(leaderDraft.optString("rehearsalDate", leaderDraft.optString("date", "2026-10-11")))
        val start = timeField(leaderDraft.optString("rehearsalStart", "19:00"))
        val end = timeField(leaderDraft.optString("rehearsalEnd", "21:00"))
        val venue = plainInput("Venue").apply { setText(leaderDraft.optString("rehearsalVenue", leaderDraft.optString("venue"))) }
        col.add(text("Date*", 12, navy)); col.space(8); col.add(date, -1, 48); col.space(20)
        col.add(horizontal().apply {
            add(vertical().apply { add(text("Start Time*", 12, navy)); space(8); add(start, -1, 48) }, 0, -2, 1f)
            add(View(this@AdminActivity), 16, 1)
            add(vertical().apply { add(text("End Time*", 12, navy)); space(8); add(end, -1, 48) }, 0, -2, 1f)
        }); col.space(20)
        col.add(text("Venue*", 12, navy)); col.space(8); col.add(venue, -1, 48); col.space(20)
        col.add(text("Associated Worship Service", 12, navy)); col.space(8)
        col.add(panel().apply { add(text(leaderDraft.optString("title"), 14, navy, true)) })
        stepActions(col, { navigate(if (serviceWizard) "adminLineup" else "service") }) {
            when {
                venue.text.isBlank() -> invalid(venue, "Enter a venue.")
                start.text.toString() >= end.text.toString() -> toast("End time must be after start time.")
                else -> {
                    leaderDraft.put("rehearsalDate", date.text.toString()).put("rehearsalStart", start.text.toString())
                        .put("rehearsalEnd", end.text.toString()).put("rehearsalVenue", venue.text.toString().trim())
                    saveLeaderDraft(); navigate("reviewService")
                }
            }
        }
    }
    private fun serviceConflicts(): List<String> {
        val conflicts = mutableListOf<String>()
        localServices.forEachIndexed { index, service ->
            if (index != activeServiceIndex && service.optString("date") == leaderDraft.optString("date") &&
                service.optString("venue").equals(leaderDraft.optString("venue"), true) &&
                service.optString("start") < leaderDraft.optString("end") && service.optString("end") > leaderDraft.optString("start"))
                conflicts.add("Venue overlaps with " + service.optString("title"))
        }
        val roster = leaderRoster().associate { it.first to it.third }
        val assigned = draftAssignments()
        repeat(assigned.length()) {
            val name = assigned.getJSONObject(it).optString("name")
            if (roster[name] != "Available") conflicts.add("$name is unavailable or no longer on the team.")
        }
        return conflicts
    }
    private fun storeService(status: String, navigateAfter: Boolean = true) {
        leaderDraft.put("status", status)
        val srvId = leaderDraft.optString("id").ifBlank { "srv_" + System.currentTimeMillis() }
        leaderDraft.put("id", srvId)
        val record = org.json.JSONObject(leaderDraft.toString())
        val existingIdx = localServices.indexOfFirst { it.optString("id") == srvId }
        if (existingIdx >= 0) { localServices[existingIdx] = record; activeServiceIndex = existingIdx }
        else if (activeServiceIndex in localServices.indices) localServices[activeServiceIndex] = record
        else { localServices.add(record); activeServiceIndex = localServices.lastIndex }
        val records = org.json.JSONArray(); localServices.forEach { records.put(it) }
        preferences.edit().putString("leaderServices", records.toString()).apply()
        serviceWizard = false; saveLeaderDraft()

        val activeCode = preferences.getString("teamCode", "").orEmpty()
            .ifBlank { preferences.getString(SessionManager.KEY_TEAM_CODE, "").orEmpty() }
            .ifBlank { FirestoreManager.findCodeByTeamName(teamName).orEmpty() }
        val activeUid = preferences.getString(SessionManager.KEY_USER_ID, "").orEmpty()
            .ifBlank { preferences.getString("userId", "").orEmpty() }
            .ifBlank { AuthManager.currentUser?.uid.orEmpty() }

        if (activeCode.isNotBlank()) {
            val assignmentsList = mutableListOf<com.example.syncedn.data.model.ServiceAssignment>()
            val draftAssigned = draftAssignments()
            repeat(draftAssigned.length()) { idx ->
                val a = draftAssigned.getJSONObject(idx)
                assignmentsList.add(com.example.syncedn.data.model.ServiceAssignment(
                    uid = a.optString("uid"),
                    email = a.optString("email"),
                    name = a.optString("name"),
                    role = a.optString("role")
                ))
            }
            val rehearsalData = com.example.syncedn.data.model.RehearsalData(
                date = leaderDraft.optString("rehearsalDate"),
                start = leaderDraft.optString("rehearsalStart"),
                end = leaderDraft.optString("rehearsalEnd"),
                venue = leaderDraft.optString("rehearsalVenue"),
                notes = leaderDraft.optString("rehearsalNotes")
            )
            val songsList = mutableListOf<com.example.syncedn.data.model.ServiceSong>()
            val draftSongsArr = draftSongs()
            val allCachedSongs = TeamRepository.getCachedSongs(activeCode, preferences)
            repeat(draftSongsArr.length()) { idx ->
                val raw = draftSongsArr.get(idx)
                val unpacked = com.example.syncedn.data.model.ServiceSong.unpack(raw)
                val songTitle = unpacked.title
                val cachedSong = allCachedSongs.find { it.title.equals(songTitle, true) }
                val songData = try { org.json.JSONObject(preferences.getString("songData_$songTitle", "{}").orEmpty()) } catch (_: Exception) { org.json.JSONObject() }
                songsList.add(com.example.syncedn.data.model.ServiceSong(
                    title = songTitle,
                    artist = unpacked.artist.ifBlank { cachedSong?.artist.orEmpty() }.ifBlank { songData.optString("artist", songCatalog().find { it.first == songTitle }?.second ?: "") },
                    key = unpacked.key.ifBlank { cachedSong?.key.orEmpty() }.ifBlank { songData.optString("key", "C") }.ifBlank { "C" },
                    bpm = if (unpacked.bpm > 0) unpacked.bpm else (cachedSong?.bpm ?: songData.optInt("bpm", 74))
                ))
            }
            val draftCreatedAt = leaderDraft.optLong("createdAt", 0L)
            val existingCreatedAt = if (draftCreatedAt > 0L) draftCreatedAt
            else (localServices.find { it.optString("id") == srvId }?.optLong("createdAt", 0L)?.takeIf { it > 0L } ?: System.currentTimeMillis())
            val existingCreatedBy = leaderDraft.optString("createdBy").ifBlank {
                localServices.find { it.optString("id") == srvId }?.optString("createdBy").orEmpty().ifBlank { activeUid }
            }
            leaderDraft.put("createdAt", existingCreatedAt)
            leaderDraft.put("createdBy", existingCreatedBy)
            leaderDraft.put("updatedAt", System.currentTimeMillis())

            val serviceObj = com.example.syncedn.data.model.ServiceData(
                id = srvId,
                teamCode = activeCode,
                title = leaderDraft.optString("title"),
                date = leaderDraft.optString("date"),
                start = leaderDraft.optString("start"),
                end = leaderDraft.optString("end"),
                venue = leaderDraft.optString("venue"),
                coverImageUrl = leaderDraft.optString("coverImageUrl"),
                status = status,
                assignments = assignmentsList,
                rehearsal = rehearsalData,
                songs = songsList,
                createdBy = existingCreatedBy,
                createdAt = existingCreatedAt,
                updatedAt = System.currentTimeMillis()
            )
            TeamRepository.saveService(activeCode, serviceObj, preferences)
            if (status == "Published") {
                com.example.syncedn.data.auth.SyncedMessagingService.dispatchSystemNotification(
                    this,
                    preferences,
                    "New Service Scheduled",
                    "${serviceObj.title} scheduled for ${serviceObj.date}."
                )
            }
        }

        if (navigateAfter) {
            toast(if (status == "Published") "Schedule published." else "Draft saved.")
            navigate("schedule")
        }

    }
    private fun reviewServiceView(): View = memberPage("Review Service", "scheduleRehearsal") { col ->
        col.add(serviceSummary()); col.space(16)
        col.add(panel().apply {
            add(text("${draftAssignments().length()} roles assigned", 14, Color.rgb(30, 130, 20), true)); space(10)
            repeat(draftAssignments().length()) { index ->
                val assigned = draftAssignments().getJSONObject(index)
                add(text(assigned.optString("name") + " • " + assigned.optString("role"), 12, navy)); space(6)
            }
        }); col.space(16)
        col.add(panel().apply {
            add(text("Song Lineup", 14, navy, true)); space(8)
            repeat(draftSongs().length()) { index ->
                val song = com.example.syncedn.data.model.ServiceSong.unpack(draftSongs().get(index))
                add(text("${index + 1}. ${song.title}", 12, navy)); space(6)
            }
        }); col.space(16)
        col.add(panel().apply {
            add(text("Rehearsal", 14, navy, true)); space(8)
            add(text(if (leaderDraft.has("rehearsalDate")) leaderDraft.optString("rehearsalDate") + " • " +
                    leaderTime(leaderDraft.optString("rehearsalStart")) + " – " + leaderTime(leaderDraft.optString("rehearsalEnd")) +
                    "\n" + leaderDraft.optString("rehearsalVenue") else "Not scheduled", 12, navy))
        }); col.space(20)
        val conflicts = serviceConflicts()
        col.add(text(if (conflicts.isEmpty()) "✓ No local conflicts detected" else conflicts.joinToString("\n"),
            13, if (conflicts.isEmpty()) Color.rgb(30, 130, 20) else Color.RED, true))
        col.add(dualButtons("Save Draft", { storeService("Draft") }, "Publish Schedule", {
            when {
                leaderDraft.optString("title").isBlank() || leaderDraft.optString("venue").isBlank() -> toast("Complete the service details first.")
                draftAssignments().length() == 0 -> toast("Assign at least one member.")
                draftSongs().length() == 0 -> toast("Add at least one song.")
                conflicts.isNotEmpty() -> toast("Resolve the listed conflicts first.")
                else -> storeService("Published")
            }
        }))
        col.space(12)
        col.add(text("Delete Service", 13, Color.rgb(220, 38, 38), bold = true, centered = true).apply {
            background = bg(Color.rgb(254, 242, 242), 12, Color.rgb(252, 165, 165))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                AlertDialog.Builder(this@AdminActivity)
                    .setTitle("Delete Service")
                    .setMessage("Are you sure you want to delete this service schedule? This cannot be undone.")
                    .setPositiveButton("Delete") { _, _ ->
                        val activeCode = preferences.getString("teamCode", "").orEmpty()
                        val sid = leaderDraft.optString("id", "")
                        if (sid.isNotBlank()) {
                            TeamRepository.deleteService(activeCode, sid, preferences) {
                                runOnUiThread { refreshTeamServices(force = true) }
                            }
                            val iter = localServices.iterator()
                            while (iter.hasNext()) {
                                if (iter.next().optString("id") == sid) iter.remove()
                            }
                        }
                        toast("Service deleted.")
                                navigate("schedule")
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }, -1, 44)
    }
    private fun formatBadgeDate(rawDate: String): String = try {
        if (rawDate.contains("\n")) rawDate
        else {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val parsed = sdf.parse(rawDate)
            if (parsed != null) {
                val cal = java.util.Calendar.getInstance().apply { time = parsed }
                val month = java.text.SimpleDateFormat("MMM", java.util.Locale.US).format(cal.time).uppercase()
                val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
                "$month\n$day"
            } else rawDate
        }
    } catch (_: Exception) { rawDate }

    private fun leaderScheduleCards(col: LinearLayout): Int {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        var renderedCount = 0
        localServices.forEachIndexed { index, record ->
            val date = record.optString("date")
            val isPublished = record.optString("status") == "Published"
            val isPast = date.isNotBlank() && date < today && isPublished
            if (scheduleFilter == "Past" && !isPast) return@forEachIndexed
            if (scheduleFilter == "Upcoming" && isPast) return@forEachIndexed
            renderedCount++

            val title = record.optString("title", "Sunday Worship Service")
            val badgeDate = formatBadgeDate(date)
            val start = record.optString("start")
            val end = record.optString("end")
            val time = if (start.isNotBlank() && end.isNotBlank()) "${leaderTime(start)} – ${leaderTime(end)}"
                       else if (start.isNotBlank()) leaderTime(start) else "6:00AM – 11:00AM"
            val venue = record.optString("venue", "Main Sanctuary")
            val status = record.optString("status")

            val row = horizontal().apply {
                background = bg(Color.WHITE, 20, if (preferences.getString("serviceDate", "") == date) purple else Color.rgb(230, 230, 235))
                elevation = dp(3).toFloat(); setPadding(dp(8), dp(10), dp(12), dp(10))
                add(text(badgeDate, 15, Color.WHITE, bold = true, centered = true).apply {
                    background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        intArrayOf(purple, Color.rgb(167, 0, 214))).apply { cornerRadius = dp(14).toFloat() }
                }, 60, 66)
                add(vertical().apply {
                    setPadding(dp(12), 0, dp(8), 0)
                    add(text(title, 13, navy, true)); space(4); add(text(time, 11, navy))
                    space(3); add(text("$venue • $status", 11, if (status == "Published") purple else Color.rgb(100, 100, 110), true))
                }, 0, -2, 1f)
                add(Symbol(this@AdminActivity, "arrow", Color.GRAY), 18, 24)
                isClickable = true; isFocusable = true
                setOnClickListener {
                    leaderDraft = org.json.JSONObject(record.toString())
                    activeServiceIndex = index
                    serviceWizard = false; serviceReturnPage = "schedule"; saveLeaderDraft(); navigate("service")
                }
            }
            col.add(row); col.space(16)
        }
        return renderedCount
    }
    private fun songCatalog(): List<Pair<String, String>> {
        val activeCode = preferences.getString("teamCode", "").orEmpty()
        val songs = TeamRepository.getCachedSongs(activeCode, preferences)
        return songs.map { it.title to it.artist }
    }
    private fun songEditView(): View = memberPage(if (selectedSong.isBlank()) "Add Song" else "Edit Song",
        if (selectedSong.isBlank()) "songs" else "songDetails") { col ->
        val activeCode = preferences.getString("teamCode", "").orEmpty()
        val currentSong = TeamRepository.getCachedSongs(activeCode, preferences).find { it.title.equals(selectedSong, ignoreCase = true) }
        val fallbackJson = try { org.json.JSONObject(preferences.getString("songData_$selectedSong", "{}").orEmpty()) } catch (_: Exception) { org.json.JSONObject() }
        
        val title = plainInput("Song title").apply { setText(currentSong?.title ?: selectedSong) }
        val artist = plainInput("Artist").apply { setText(currentSong?.artist ?: fallbackJson.optString("artist", songCatalog().find { it.first == selectedSong }?.second ?: "")) }
        val key = plainInput("Key, e.g. C").apply { setText(currentSong?.key ?: fallbackJson.optString("key", "C")) }
        val bpm = plainInput("BPM").apply { inputType = InputType.TYPE_CLASS_NUMBER; setText(currentSong?.bpm?.toString() ?: fallbackJson.optString("bpm", "74")) }
        val lyrics = plainInput("Your licensed lyrics / chords", true, chordSheet = true).apply { 
            setHorizontallyScrolling(true)
            setText(currentSong?.sheet?.ifBlank { currentSong.lyrics } ?: fallbackJson.optString("sheet", fallbackJson.optString("lyrics"))) 
        }
        val link = plainInput("Reference URL").apply { setText(currentSong?.referenceUrl ?: fallbackJson.optString("link")) }
        listOf("Title*" to title, "Artist*" to artist, "Key" to key, "BPM" to bpm).forEach { (label, field) ->
            col.add(text(label, 12, navy, true)); col.space(8); col.add(field, -1, 48); col.space(16)
        }

        // Section Builder & Quick Chord Toolbar
        val initialRaw = currentSong?.sheet?.ifBlank { currentSong.lyrics } ?: fallbackJson.optString("sheet", fallbackJson.optString("lyrics"))
        val initialSections = ChordSheet.splitSections(initialRaw)

        data class SectionEntry(var header: String, val input: EditText, val cardView: View)
        val sectionList = mutableListOf<SectionEntry>()
        val sectionsContainer = vertical()
        var focusedSectionInput: EditText? = null

        // Transpose Preview views & function
        val previewRoots = listOf("C", "Db", "D", "Eb", "E", "F")
        var currentPreviewKey = key.text.toString().trim().ifBlank { "C" }
        val previewButtons = mutableMapOf<String, TextView>()
        var activePreviewChord = previewRoots.find { it.equals(currentPreviewKey, true) } ?: "C"

        val previewSheetText = text("", 12, navy).apply {
            typeface = Typeface.MONOSPACE
            setLineSpacing(dp(4).toFloat(), 1.18f)
            isSingleLine = false
            setHorizontallyScrolling(false)
        }

        fun getFullAssembledSheet(): String {
            val pairs = sectionList.map { Pair(it.header, it.input.text.toString()) }
            return ChordSheet.assembleSections(pairs)
        }

        fun updateTransposePreview(targetChord: String) {
            activePreviewChord = targetChord
            val origKey = key.text.toString().trim().ifBlank { "C" }
            val basePitch = ChordSheet.keyPitch(origKey).coerceAtLeast(0)
            val targetPitch = ChordSheet.pitch(targetChord).coerceAtLeast(0)
            val offset = (targetPitch - basePitch + 12) % 12
            val sourceText = getFullAssembledSheet().ifBlank {
                "[Verse 1]\n[C]Amazing grace how [G]sweet the sound\n[Am]That saved a wretch [F]like me\n\n[Chorus]\n[C]My chains are gone [G]I've been set free"
            }
            previewSheetText.text = ChordSheet.render(sourceText, offset, false, targetChord.contains('b') || targetChord == "F")
            previewButtons.forEach { (chord, tv) ->
                val isActive = (chord == targetChord)
                tv.background = bg(if (isActive) navy else Color.rgb(228, 220, 235), 8)
                tv.setTextColor(if (isActive) Color.WHITE else navy)
            }
        }

        // Quick Chord Accessory Toolbar (Diatonic chords for current key)
        val chordPaletteRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(4))
        }
        val chordPaletteScroll = android.widget.HorizontalScrollView(this).apply {
            isFillViewport = false
            isHorizontalScrollBarEnabled = false
            addView(chordPaletteRow)
        }

        fun refreshChordPalette() {
            chordPaletteRow.removeAllViews()
            val activeKey = key.text.toString().trim().ifBlank { "C" }
            val chords = ChordSheet.getChordPalette(activeKey)
            chords.forEachIndexed { idx, chord ->
                val btn = text(chord, 12, navy, true, true).apply {
                    background = bg(Color.rgb(228, 220, 235), 8)
                    setPadding(dp(14), dp(6), dp(14), dp(6))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        val target = focusedSectionInput ?: sectionList.firstOrNull()?.input
                        target?.let { et ->
                            val start = et.selectionStart.coerceAtLeast(0)
                            val end = et.selectionEnd.coerceAtLeast(0)
                            val tag = "[$chord]"
                            et.text.replace(minOf(start, end), maxOf(start, end), tag)
                            et.setSelection(minOf(start, end) + tag.length)
                            et.requestFocus()
                        }
                    }
                }
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)).apply {
                    if (idx > 0) leftMargin = dp(6)
                }
                chordPaletteRow.addView(btn, lp)
            }
        }

        fun addSectionCard(headerName: String, initialContent: String = "") {
            var currentHeader = headerName.trim()
            if (!currentHeader.startsWith("[")) currentHeader = "[$currentHeader"
            if (!currentHeader.endsWith("]")) currentHeader = "$currentHeader]"

            val sectionInput = plainInput("Enter lyrics & chords for this section (e.g. [Dm]lyrics...)", true).apply {
                setText(initialContent)
                minLines = 3
                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) focusedSectionInput = this
                }
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: android.text.Editable?) {
                        updateTransposePreview(activePreviewChord)
                    }
                })
            }

            lateinit var cardPanel: View
            val headerText = text(currentHeader, 13, purple, bold = true)

            cardPanel = panel().apply {
                add(horizontal().apply {
                    add(headerText, 0, -2, 1f)
                    add(text("Remove", 12, Color.RED).apply {
                        setPadding(dp(8), dp(4), dp(8), dp(4))
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            sectionsContainer.removeView(cardPanel)
                            sectionList.removeAll { it.cardView == cardPanel }
                            if (focusedSectionInput == sectionInput) {
                                focusedSectionInput = sectionList.firstOrNull()?.input
                            }
                            updateTransposePreview(activePreviewChord)
                        }
                    }, -2, -2)
                })
                space(8)
                add(sectionInput, -1, -2)
            }

            val entry = SectionEntry(currentHeader, sectionInput, cardPanel)
            sectionList.add(entry)
            sectionsContainer.addView(cardPanel)
            sectionsContainer.addView(View(this).apply { minimumHeight = dp(12) })
            focusedSectionInput = sectionInput
            updateTransposePreview(activePreviewChord)
        }

        // Section Picker Chips
        val standardSections = listOf("Verse 1", "Chorus", "Verse 2", "Bridge", "Pre-Chorus", "Outro")
        val sectionPickerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(4))
        }
        val sectionPickerScroll = android.widget.HorizontalScrollView(this).apply {
            isFillViewport = false
            isHorizontalScrollBarEnabled = false
            addView(sectionPickerRow)
        }

        standardSections.forEachIndexed { idx, secName ->
            val chip = text("+ $secName", 12, purple, true, true).apply {
                background = bg(Color.rgb(242, 237, 248), 8, purple)
                setPadding(dp(10), dp(6), dp(10), dp(6))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    addSectionCard(secName)
                }
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)).apply {
                if (idx > 0) leftMargin = dp(6)
            }
            sectionPickerRow.addView(chip, lp)
        }

        val customChip = text("+ Custom", 12, navy, true, true).apply {
            background = bg(Color.rgb(230, 230, 235), 8, navy)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val input = EditText(this@AdminActivity).apply { hint = "e.g. Refrain, Tag, Vamp" }
                AlertDialog.Builder(this@AdminActivity)
                    .setTitle("Add Custom Section")
                    .setView(input)
                    .setPositiveButton("Add") { _, _ ->
                        val customName = input.text.toString().trim()
                        if (customName.isNotBlank()) {
                            addSectionCard(customName)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        val customLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)).apply {
            leftMargin = dp(6)
        }
        sectionPickerRow.addView(customChip, customLp)

        // UI Header for Song Sections
        col.add(horizontal().apply {
            add(text("Song Sections", 12, navy, true), 0, -2, 1f)
            add(text("Paste Full Lyrics", 12, purple, bold = true).apply {
                setPadding(dp(8), dp(4), dp(8), dp(4))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val pasteInput = EditText(this@AdminActivity).apply {
                        hint = "Paste lyrics with Verse/Chorus headers..."
                        minLines = 6
                        isSingleLine = false
                    }
                    AlertDialog.Builder(this@AdminActivity)
                        .setTitle("Paste Full Lyrics")
                        .setMessage("Sections will be auto-detected and split into cards.")
                        .setView(pasteInput)
                        .setPositiveButton("Import") { _, _ ->
                            val pasted = pasteInput.text.toString()
                            if (pasted.isNotBlank()) {
                                sectionsContainer.removeAllViews()
                                sectionList.clear()
                                val parsed = ChordSheet.splitSections(pasted)
                                if (parsed.isEmpty()) {
                                    addSectionCard("Verse 1", pasted)
                                } else {
                                    parsed.forEach { (h, body) ->
                                        addSectionCard(h, body)
                                    }
                                }
                                toast("Imported ${sectionList.size} sections")
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }, -2, -2)
        })
        col.space(6)
        col.add(sectionPickerScroll, -1, dp(42))
        col.space(12)

        // Quick Insert Chords Header & Bar
        col.add(text("Quick Insert Chord (Tap to insert at cursor)", 12, navy, true))
        col.space(6)
        col.add(chordPaletteScroll, -1, dp(42))
        col.space(16)

        // Add initial section cards
        if (initialSections.isNotEmpty()) {
            initialSections.forEach { (h, body) ->
                addSectionCard(h, body)
            }
        } else {
            addSectionCard("Verse 1")
            addSectionCard("Chorus")
        }

        col.add(sectionsContainer)
        col.space(16)

        col.add(text("Reference Link", 12, navy, true)); col.space(8); col.add(link, -1, 48); col.space(20)

        // Dynamic Transpose Preview in Add/Edit Song
        col.add(text("Transpose Preview", 12, navy, true)); col.space(8)
        val previewRow = horizontal().apply {
            previewRoots.forEachIndexed { idx, chord ->
                val btn = text(chord, 12, navy, true, true).apply {
                    background = bg(Color.rgb(228, 220, 235), 8)
                    setOnClickListener { updateTransposePreview(chord) }
                    isFocusable = true
                }
                previewButtons[chord] = btn
                val lp = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                    if (idx > 0) leftMargin = dp(6)
                }
                addView(btn, lp)
            }
        }
        col.add(previewRow); col.space(12)

        col.add(panel().apply {
            add(previewSheetText, -1, -2)
        }); col.space(24)

        refreshChordPalette()
        updateTransposePreview(activePreviewChord)

        key.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                refreshChordPalette()
                updateTransposePreview(activePreviewChord)
            }
        })

        col.add(memberButton("Save Song") {
            val name = title.text.toString().trim()
            val artistName = artist.text.toString().trim()
            val tempo = bpm.text.toString().toIntOrNull()
            val songKey = key.text.toString().trim()
            val sheetContent = getFullAssembledSheet()

            val refUrl = link.text.toString().trim()
            val normalizedRef = SongData.normalizeReferenceUrl(refUrl)
            when {
                name.isBlank() -> invalid(title, "Enter a title.")
                artistName.isBlank() -> invalid(artist, "Enter an artist.")
                ChordSheet.keyPitch(songKey) < 0 -> invalid(key, "Enter a key such as C, Eb, F# or Am.")
                tempo == null || tempo !in 1..400 -> invalid(bpm, "Enter BPM from 1 to 400.")
                else -> {
                    val songId = currentSong?.id?.ifBlank { "song_${System.currentTimeMillis()}" } ?: "song_${System.currentTimeMillis()}"
                    val currentUid = preferences.getString("userId", "").orEmpty().ifBlank { AuthManager.currentUser?.uid.orEmpty() }
                    val creatorUid = SongData.resolveCreatorUid(currentSong?.createdBy, currentUid)
                    val songObj = SongData(
                        id = songId,
                        teamCode = activeCode,
                        title = name,
                        artist = artistName,
                        key = songKey,
                        bpm = tempo,
                        sheet = sheetContent,
                        lyrics = sheetContent,
                        referenceUrl = normalizedRef,
                        createdBy = creatorUid,
                        createdAt = currentSong?.createdAt ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    TeamRepository.saveSong(activeCode, songObj, preferences)
                    preferences.edit().putString("songData_$name", org.json.JSONObject().put("artist", artistName)
                        .put("key", songKey).put("bpm", tempo).put("lyrics", sheetContent).put("sheet", sheetContent)
                        .put("link", normalizedRef).toString()).apply()
                    selectedSong = name
                    toast("Song saved")
                    navigate("songDetails")
                }
            }
        }, -1, 48)

        if (selectedSong.isNotBlank()) {
            col.space(12)
            col.add(memberButton("Delete Song", Color.rgb(180, 40, 40)) {
                AlertDialog.Builder(this)
                    .setTitle("Delete Song?")
                    .setMessage("Are you sure you want to delete '$selectedSong' from the team library?")
                    .setPositiveButton("Delete") { _, _ ->
                        val songId = currentSong?.id ?: "song_${selectedSong.lowercase().replace(" ", "_")}"
                        TeamRepository.deleteSong(activeCode, songId, preferences)
                        toast("Song deleted")
                        selectedSong = ""
                        navigate("songs")
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }, -1, 48)
        }
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

    private fun announcementEditView(): View = memberPage(if (editingAnnouncement != null) "Edit Announcement" else "Announcement", "announcements") { col ->
        col.space(16)
        val title = plainInput("", false).apply {
            editingAnnouncement?.let { setText(it.title) }
            background = bg(Color.rgb(217, 217, 217), 12)
            filters = arrayOf(InputFilter.LengthFilter(120))
        }
        val charCounter = text("${editingAnnouncement?.message?.length ?: 0} / 250 characters", 11, Color.GRAY)
        val message = plainInput("", true).apply {
            editingAnnouncement?.let { setText(it.message) }
            background = bg(Color.rgb(217, 217, 217), 14)
            maxLines = 5
            val lineFilter = InputFilter { source, start, end, dest, dstart, dend ->
                val resultingText = StringBuilder(dest).replace(dstart, dend, source.subSequence(start, end).toString()).toString()
                val newlineCount = resultingText.count { it == '\n' }
                if (newlineCount > 4) "" else null
            }
            filters = arrayOf(InputFilter.LengthFilter(250), lineFilter)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    charCounter.text = "${s?.length ?: 0} / 250 characters"
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        col.add(text("Announcement Title*", 12, navy, bold = true)); col.space(8); col.add(title, -1, 52); col.space(20)
        col.add(horizontal().apply {
            add(text("Message*", 12, navy, bold = true), 0, -2, 1f)
            add(charCounter, -2, -2)
        }); col.space(8); col.add(message, -1, 220); col.space(24)
        val urgent = android.widget.CheckBox(this).apply {
            text = "Mark as Urgent Alert"
            textSize = 13f
            setTextColor(navy)
            isChecked = editingAnnouncement?.urgent ?: false
        }
        col.add(urgent)
        val actionLabel = if (editingAnnouncement != null) "Save Changes" else "Publish"
        col.space(32)
        col.add(horizontal().apply {
            val backBtn = Button(this@AdminActivity).apply {
                text = "Back"
                isAllCaps = false
                background = bg(Color.WHITE, 12, Color.rgb(217, 70, 239))
                setTextColor(Color.rgb(217, 70, 239))
                minimumHeight = dp(48)
                setOnClickListener {
                    editingAnnouncement = null
                    navigate("announcements")
                }
            }
            val publishBtn = Button(this@AdminActivity).apply {
                text = actionLabel
                isAllCaps = false
                background = bg(purple, 12)
                setTextColor(Color.WHITE)
                minimumHeight = dp(48)
                setOnClickListener {
                    when {
                        title.text.isBlank() -> invalid(title, "Enter a title.")
                        title.text.length > 120 -> invalid(title, "Title cannot exceed 120 characters.")
                        message.text.isBlank() -> invalid(message, "Enter a message.")
                        message.text.length > 500 -> invalid(message, "Message cannot exceed 500 characters.")
                        else -> {
                            val activeCode = preferences.getString("teamCode", "").orEmpty()
                            val uid = preferences.getString("userId", "").orEmpty()
                            val authorName = fullName.ifBlank { displayName.ifBlank { "Leader" } }
                            val existing = editingAnnouncement
                            val announcementId = existing?.id ?: "ann_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}"
                            val newAnnouncement = AnnouncementData(
                                id = announcementId,
                                teamCode = activeCode,
                                title = title.text.toString().trim(),
                                message = message.text.toString().trim(),
                                urgent = urgent.isChecked,
                                author = authorName,
                                createdBy = existing?.createdBy?.ifBlank { uid } ?: uid,
                                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )
                            TeamRepository.saveAnnouncement(activeCode, newAnnouncement, preferences) { result ->
                                runOnUiThread {
                                    if (!result.isSuccess) {
                                        toast("Saved locally (sync pending)")
                                    }
                                }
                            }
                            editingAnnouncement = null
                            com.example.syncedn.data.auth.SyncedMessagingService.dispatchSystemNotification(
                                this@AdminActivity,
                                preferences,
                                if (newAnnouncement.urgent) "🚨 URGENT MINISTRY ALERT" else "New Announcement",
                                "${newAnnouncement.title}: ${newAnnouncement.message}"
                            )
                            toast(if (existing != null) "Announcement updated" else "Announcement published")
                            navigate("announcements")
                        }
                    }
                }
            }
            add(backBtn, 0, 48, 1f)
            add(View(this@AdminActivity), 16, 1)
            add(publishBtn, 0, 48, 1f)
        })
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
                        author = p.optString("author", fullName.ifBlank { displayName.ifBlank { "Leader" } }),
                        createdBy = preferences.getString("userId", "").orEmpty(),
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
                        headerRow.add(View(this@AdminActivity), 6, 1)
                    }
                    if (isLeader) {
                        val moreBtn = TextView(this@AdminActivity).apply {
                            text = "⋮"
                            textSize = 20f
                            setTextColor(navy)
                            gravity = Gravity.CENTER
                            setPadding(dp(6), 0, dp(4), 0)
                            isClickable = true
                            isFocusable = true
                            contentDescription = "Options for ${post.title}"
                            setOnClickListener {
                                val options = arrayOf("Edit", "Delete")
                                AlertDialog.Builder(this@AdminActivity)
                                    .setItems(options) { _, which ->
                                        when (which) {
                                            0 -> {
                                                editingAnnouncement = post
                                                navigate("announcementEdit")
                                            }
                                            1 -> {
                                                AlertDialog.Builder(this@AdminActivity)
                                                    .setTitle("Delete Announcement?")
                                                    .setMessage("Are you sure you want to delete this announcement?")
                                                    .setNegativeButton("Cancel", null)
                                                    .setPositiveButton("Delete") { _, _ ->
                                                        TeamRepository.deleteAnnouncement(activeCode, post.id, preferences) { result ->
                                                            runOnUiThread {
                                                                if (result.isSuccess) {
                                                                    toast("Announcement deleted")
                                                                } else {
                                                                    toast("Deleted locally (sync pending)")
                                                                }
                                                            }
                                                        }
                                                        populateList()
                                                    }
                                                    .show()
                                            }
                                        }
                                    }
                                    .show()
                            }
                        }
                        headerRow.add(moreBtn, -2, -2)
                    }
                    add(headerRow)
                    space(8); add(text(post.message, 13, navy))
                    val rawAuthor = if (post.author.isNotBlank()) post.author else fullName.ifBlank { displayName.ifBlank { "Ministry Leader" } }
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
    private fun removeTeamMember() {
        val currentUid = preferences.getString("userId", "").orEmpty()
        val currentName = fullName.ifBlank { displayName }
        val roster = getActiveRoster().filter { it.uid != currentUid && it.fullName != currentName }
        if (roster.isEmpty()) {
            toast("No members to remove.")
            return
        }
        val names = roster.map { it.fullName.ifBlank { it.username.ifBlank { "Member" } } }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Remove Member").setItems(names) { _, index ->
            val selectedMember = roster[index]
            val name = names[index]
            AlertDialog.Builder(this).setTitle("Remove $name?")
                .setMessage("Are you sure you want to remove $name from the team?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove") { _, _ ->
                    val activeCode = preferences.getString("teamCode", "").orEmpty()
                    val removed = preferences.getStringSet("removedMembers", emptySet()).orEmpty().toMutableSet()
                    removed.add(name)
                    if (selectedMember.uid.isNotBlank()) removed.add(selectedMember.uid)
                    if (selectedMember.email.isNotBlank()) removed.add(selectedMember.email.trim().lowercase())
                    preferences.edit().putStringSet("removedMembers", removed).apply()
                    if (selectedMember.uid.isNotBlank()) {
                        TeamRepository.removeMemberFromTeam(selectedMember.uid, activeCode, selectedMember.email, preferences) { result ->
                            runOnUiThread {
                                if (result.isSuccess) {
                                    toast("$name removed from team.")
                                    refreshTeamRoster(force = true)
                                    navigate("teamDetails")
                                } else {
                                    val err = result.exceptionOrNull()?.localizedMessage ?: "Failed to remove member."
                                    toast(err)
                                    navigate("teamDetails")
                                }
                            }
                        }
                    } else {
                        toast("$name removed.")
                        navigate("teamDetails")
                    }
                }.show()
        }.setNegativeButton("Cancel", null).show()
    }
    private fun roleEditView(): View {
        val root = vertical().apply { setBackgroundColor(Color.WHITE) }
        root.add(teamHeader(showDetails = true), -1, 156)
        teamTab = "Role"
        root.add(teamTabs())

        val bottomContainer = vertical().apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(21, 29, 56), Color.rgb(46, 62, 120))
            ).apply {
                cornerRadii = floatArrayOf(
                    dp(32).toFloat(), dp(32).toFloat(),
                    dp(32).toFloat(), dp(32).toFloat(),
                    0f, 0f,
                    0f, 0f
                )
            }
        }

        val scroll = StableScrollView(this).apply { isFillViewport = true; clipToPadding = true }
        val col = vertical().apply {
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        scroll.addView(col, FrameLayout.LayoutParams(-1, -2))
        bottomContainer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bottomContainer, LinearLayout.LayoutParams(-1, 0, 1f))

        val activeMember = getActiveRoster().find {
            (roleMemberUid.isNotBlank() && it.uid == roleMemberUid) ||
            (roleMemberEmail.isNotBlank() && it.email.equals(roleMemberEmail, ignoreCase = true)) ||
            it.fullName == roleMember || it.username == roleMember
        }
        val currentSavedRole = activeMember?.ministryRole?.takeIf { it.isNotBlank() }
            ?: try {
                val map = org.json.JSONObject(preferences.getString(SessionManager.KEY_MEMBER_ROLES, "{}").orEmpty())
                map.optString(roleMemberUid).ifBlank {
                    map.optString(roleMemberEmail.lowercase()).ifBlank {
                        map.optString(activeMember?.email.orEmpty().lowercase()).ifBlank {
                            map.optString(roleMember)
                        }
                    }
                }
            } catch (_: Exception) { "" }
        val defaultRole = currentSavedRole.ifBlank { leaderRoles.firstOrNull() ?: "Guitarist" }
        var selectedRole = defaultRole

        val targetPhotoUri = activeMember?.photoUri?.takeIf { it.isNotBlank() }
            ?: (if (roleMember.equals(displayName, ignoreCase = true) || roleMember.equals(fullName, ignoreCase = true)) preferences.getString("photoUri", null) else null)

        val memberHeader = horizontal().apply {
            gravity = Gravity.CENTER_VERTICAL
            add(memberAvatarView(targetPhotoUri, 56), 56, 56)
            val info = vertical().apply {
                setPadding(dp(14), 0, 0, 0)
                add(text(roleMember.ifBlank { "Member" }, 18, Color.WHITE, bold = true))
                space(3)
                add(text(currentSavedRole.ifBlank { "Member" }, 13, Color.rgb(203, 213, 225)))
            }
            add(info, 0, -2, 1f)
        }
        col.add(memberHeader)
        col.space(20)

        val rolesContainer = vertical()
        col.addView(rolesContainer)

        val myMinistryRole = preferences.getString(SessionManager.KEY_MINISTRY_ROLE, "").orEmpty()
            .ifBlank { preferences.getString("ministryRole", "").orEmpty() }
        val isSubLeader = myMinistryRole.equals("Substitute Leader", ignoreCase = true)
        val assignableRoles = if (isSubLeader) {
            leaderRoles.filter {
                !it.equals("Substitute Leader", ignoreCase = true) &&
                !it.equals("Worship Leader", ignoreCase = true) &&
                !it.equals("Leader", ignoreCase = true)
            }
        } else {
            leaderRoles
        }

        fun renderRoles() {
            rolesContainer.removeAllViews()
            assignableRoles.forEach { role ->
                val isSelected = role.equals(selectedRole, ignoreCase = true)
                val item = horizontal().apply {
                    gravity = Gravity.CENTER_VERTICAL
                    background = if (isSelected) {
                        bg(Color.rgb(45, 67, 121), 8)
                    } else {
                        bg(Color.rgb(217, 217, 217), 8)
                    }
                    setPadding(dp(16), 0, dp(16), 0)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        selectedRole = role
                        renderRoles()
                    }
                    add(text(role, 14, if (isSelected) Color.WHITE else Color.BLACK, bold = true), 0, -2, 1f)
                }
                rolesContainer.add(item, -1, 44)
                rolesContainer.space(8)
            }
        }
        renderRoles()

        col.space(16)
        val saveBtnRow = horizontal().apply { gravity = Gravity.CENTER }
        val saveBtn = TextView(this).apply {
            text = "Save"
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = bg(Color.rgb(0, 153, 255), 20)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (selectedRole.isBlank()) {
                    toast("Choose a role.")
                    return@setOnClickListener
                }
                val targetMember = getActiveRoster().find {
                    (roleMemberUid.isNotBlank() && it.uid == roleMemberUid) ||
                    (roleMemberEmail.isNotBlank() && it.email.equals(roleMemberEmail, ignoreCase = true)) ||
                    it.fullName == roleMember || it.username == roleMember
                }
                val targetUid = roleMemberUid.ifBlank { targetMember?.uid.orEmpty() }
                val targetEmail = roleMemberEmail.ifBlank { targetMember?.email.orEmpty() }.trim().lowercase()

                val map = try {
                    org.json.JSONObject(preferences.getString(SessionManager.KEY_MEMBER_ROLES, "{}").orEmpty())
                } catch (_: Exception) { org.json.JSONObject() }
                map.put(roleMember, selectedRole)
                if (roleMemberUid.isNotBlank()) map.put(roleMemberUid, selectedRole)
                if (targetUid.isNotBlank()) map.put(targetUid, selectedRole)
                if (targetEmail.isNotBlank()) map.put(targetEmail, selectedRole)
                if (targetMember?.fullName.orEmpty().isNotBlank()) map.put(targetMember!!.fullName, selectedRole)
                if (targetMember?.username.orEmpty().isNotBlank()) map.put(targetMember!!.username, selectedRole)

                val isTargetLeader = selectedRole.equals("Substitute Leader", ignoreCase = true) ||
                                     selectedRole.equals("Worship Leader", ignoreCase = true) ||
                                     targetEmail == "ryanlloydgenturo@gmail.com" ||
                                     roleMember.equals("Ryan Lloyd Genturo", ignoreCase = true)
                val targetRole = if (isTargetLeader) "Leader" else "Member"

                preferences.edit {
                    putString(SessionManager.KEY_MEMBER_ROLES, map.toString())
                    if (targetEmail.isNotBlank()) {
                        putString("email_ministry_$targetEmail", selectedRole)
                        putString("email_role_$targetEmail", targetRole)
                    }
                    if (targetUid.isNotBlank()) {
                        putString("user_ministry_$targetUid", selectedRole)
                        putString("user_role_$targetUid", targetRole)
                    }
                }

                teamRoster = teamRoster.map {
                    if ((targetUid.isNotBlank() && it.uid == targetUid) ||
                        (targetEmail.isNotBlank() && it.email.equals(targetEmail, ignoreCase = true)) ||
                        it.fullName == roleMember || it.username == roleMember) {
                        it.copy(ministryRole = selectedRole, role = targetRole)
                    } else it
                }

                TeamRepository.updateMemberMinistryRole(
                    memberUid = targetUid,
                    newRole = selectedRole,
                    memberName = roleMember,
                    memberEmail = targetEmail,
                    preferences = preferences
                ) {
                    runOnUiThread { refreshTeamRoster(force = true) }
                }
                teamTab = "Members"
                toast("Member role updated to $selectedRole")
                navigate("teams")
            }
        }
        saveBtnRow.addView(saveBtn, dp(140), dp(40))
        col.addView(saveBtnRow)

        return root
    }
    private fun teamRolesView(): View {
        activeTeamSheet = "addRole"
        return teamDetailsView()
    }

    // ---- MEMBER PAGES: shared 20dp margins, flexible rows, scrollable content. ----
    private var activeTeamSheet: String? = null
    private var scheduleFilter = "Upcoming"
    private var teamTab = "Team Chat"
    private var expandedRole: String? = null
    private var selectedSong = "Trust in God"
    private var editingAnnouncement: AnnouncementData? = null
    private var sheetAutoScroll: SheetAutoScrollControls? = null
    private var songMetronome: MetronomeView? = null
    private var songDetailsBack = "songs"
    private var selectedService = "Sunday Worship Service"
    private var serviceReturnPage = "schedule"
    private var calendarYear = 2026
    private var calendarMonth = 9 // Calendar months start at zero.
    private var calendarDay = 4
    private var substitute = "John Doe"
    private var songsReturnPage = "home"
    private val chatMessages = mutableListOf<ChatMessage>()
    private var chatListener: ListenerRegistration? = null
    private var chatScrollView: StableScrollView? = null
    private val chatScrollRunnable = Runnable { chatScrollView?.fullScroll(View.FOCUS_DOWN) }
    private var chatMessagesCol: LinearLayout? = null
    private var chatEmptyView: View? = null
    private val lineupSongs = mutableListOf("Amazing Grace", "Way Maker", "Goodness of God", "Build My Life")
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
            add(View(this@AdminActivity), 16, 1)
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
        onRoleClick: (() -> Unit)? = null,
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
            val roleView = text(role, 12, if (onRoleClick != null) purple else Color.DKGRAY, bold = (onRoleClick != null)).apply {
                if (onRoleClick != null) {
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { onRoleClick() }
                }
            }
            add(roleView)
        }
        add(info, 0, -2, 1f)

        if (badge != null) {
            val badgeView = text(badge, 11, navy, bold = true, centered = true).apply {
                if (badge == "Leader" || badge == "Admin") background = bg(Color.rgb(225, 225, 228), 14)
                if (onRoleClick != null) {
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { onRoleClick() }
                }
            }
            add(badgeView, 64, 30)
        }
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
            if (isLeader) {
                add(memberButton("+ Create Service") { beginLeaderService() }, -1, 48); space(12)
            }
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
        val renderedCount = if (isLeader) leaderScheduleCards(col) else 0
        val activeCode = preferences.getString("teamCode", "").orEmpty()
        if (localServices.isNotEmpty() || activeCode.isNotBlank()) {
            if (renderedCount == 0) {
                col.add(panel().apply {
                    add(text("No ${scheduleFilter.lowercase()} services scheduled yet.", 13, Color.GRAY, centered = true))
                })
                col.space(16)
            }
        } else {
            col.add(text("Sample schedule • dates from your design", 11, Color.GRAY)); col.space(12)
            entries.forEach { (date, title, time) ->
                val row = horizontal().apply {
                    background = bg(Color.WHITE, 20, if (preferences.getString("serviceDate", "") == date.replace("\n", " ")) purple else Color.rgb(230, 230, 235))
                    elevation = dp(3).toFloat(); setPadding(dp(8), dp(10), dp(12), dp(10))
                    add(text(date, 15, Color.WHITE, bold = true, centered = true).apply {
                        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                            intArrayOf(purple, Color.rgb(167, 0, 214))).apply { cornerRadius = dp(14).toFloat() }
                    }, 60, 66)
                    add(vertical().apply {
                        setPadding(dp(12), 0, dp(8), 0)
                        add(text(title, 13, navy, true)); space(4); add(text(time, 11, navy))
                        space(3); add(text(if (title == "Team Meeting") "Fellowship Hall" else "Main Sanctuary", 11, navy))
                    }, 0, -2, 1f)
                    add(Symbol(this@AdminActivity, "arrow", Color.GRAY), 18, 24)
                    isClickable = true; isFocusable = true
                    setOnClickListener {
                        selectedService = title; serviceReturnPage = "schedule"
                        if (isLeader) {
                            val parts = date.split("\n")
                            val iso = "2026-" + (if (parts[0] == "AUG") "08" else "09") + "-" + parts[1].padStart(2, '0')
                            loadSampleLeaderService(title, iso, time)
                        }
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
                        val hasService = localServices.any { it.optString("date") == key }
                        val hasRehearsal = localServices.any {
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
        val matchingServiceIndex = localServices.indexOfFirst { it.optString("date") == selectedKey }
        val matchingService = if (matchingServiceIndex >= 0) localServices[matchingServiceIndex] else null
        val isService = matchingService != null && !isUnavailable
        val matchingRehearsalService = localServices.find {
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
                    "${leaderTime(srv.optString("start"))} - ${leaderTime(srv.optString("end"))}"
                } else "8:00AM - 11:00AM"
                "${calendarDay} ${shortMonthName(calendarMonth)}, $timeStr"
            }
            isRehearsal -> {
                val rehObj = matchingRehearsalService?.optJSONObject("rehearsal")
                val rStart = rehObj?.optString("start").orEmpty().ifBlank { matchingRehearsalService?.optString("rehearsalStart").orEmpty() }
                val rEnd = rehObj?.optString("end").orEmpty().ifBlank { matchingRehearsalService?.optString("rehearsalEnd").orEmpty() }
                val timeStr = if (rStart.isNotBlank() && rEnd.isNotBlank()) "${leaderTime(rStart)} - ${leaderTime(rEnd)}"
                              else if (rStart.isNotBlank()) leaderTime(rStart)
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
            if (isLeader && matchingService != null) {
                isClickable = true; isFocusable = true
                setOnClickListener {
                    leaderDraft = org.json.JSONObject(matchingService.toString())
                    activeServiceIndex = matchingServiceIndex
                    serviceWizard = false
                    serviceReturnPage = "calendar"
                    saveLeaderDraft()
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
            android.app.DatePickerDialog(this@AdminActivity, { _, year, month, day ->
                text = "%04d-%02d-%02d".format(java.util.Locale.US, year, month + 1, day)
            }, parts[0], parts[1] - 1, parts[2]).show()
        }
    }
    private fun plainInput(hintText: String, multiline: Boolean = false, chordSheet: Boolean = false) =
        (if (chordSheet) ChordSheetInput(this) else EditText(this)).apply {
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
            add(Symbol(this@AdminActivity, "calendar", Color.rgb(20, 27, 52)), 20, 20)
            isClickable = true; isFocusable = true; contentDescription = "Choose start date"
            setOnClickListener {
                val parts = startIso.split("-").map { it.toInt() }
                android.app.DatePickerDialog(this@AdminActivity, { _, year, month, day ->
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
                android.app.DatePickerDialog(this@AdminActivity, { _, year, month, day ->
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

    private fun substitutionFeed(col: LinearLayout) {
        val subCol = vertical()
        col.addView(subCol)

        val activeCode = preferences.getString(SessionManager.KEY_TEAM_CODE, "").orEmpty().ifBlank { preferences.getString("teamCode", "").orEmpty() }

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
            if (list.isEmpty()) {
                subCol.add(panel().apply {
                    add(text("No substitution requests.", 13, Color.GRAY, centered = true))
                }); subCol.space(24)
                return
            }

            for (sub in list) {
                subCol.add(panel().apply {
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
                        headerRow.add(View(this@AdminActivity), 6, 1)
                        headerRow.add(typeBadge, -2, -2)
                    }

                    if (isLeader) {
                        val delBtn = text("✕", 14, Color.GRAY, bold = true, centered = true).apply {
                            setPadding(dp(8), dp(2), dp(2), dp(2))
                            isClickable = true; isFocusable = true
                            contentDescription = "Delete substitution request"
                            setOnClickListener {
                                AlertDialog.Builder(this@AdminActivity).setTitle("Delete request?")
                                    .setMessage("Are you sure you want to delete this substitution record?")
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton("Delete") { _, _ ->
                                        TeamRepository.deleteSubstitution(activeCode, sub.id, preferences) { res ->
                                            runOnUiThread {
                                                if (res.isSuccess) toast("Request removed")
                                                populateSubstitutions()
                                            }
                                        }
                                    }.show()
                            }
                        }
                        headerRow.add(delBtn, -2, -2)
                    }
                    add(headerRow); space(6)
                    val matchedSvc = localServices.find { it.optString("id") == sub.serviceId }
                    val dStr = sub.serviceDate.ifBlank { matchedSvc?.optString("date").orEmpty() }
                    val tStr = matchedSvc?.let {
                        val s = it.optString("start"); val e = it.optString("end")
                        if (s.isNotBlank() && e.isNotBlank()) "${leaderTime(s)} – ${leaderTime(e)}" else if (s.isNotBlank()) leaderTime(s) else ""
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

                    if ((isSubAccepted || isPending) && isLeader) {
                        space(16)
                        val actions = horizontal()
                        val approveBtn = Button(this@AdminActivity).apply {
                            text = if (isSubAccepted) "Approve & Update Lineup" else "Direct Approve & Update"
                            isAllCaps = false
                            background = bg(Color.rgb(46, 125, 50), 8)
                            setTextColor(Color.WHITE)
                            minimumHeight = dp(38)
                            textSize = 12f
                            setTypeface(typeface, Typeface.BOLD)
                            setOnClickListener {
                                AlertDialog.Builder(this@AdminActivity).setTitle("Approve substitution?")
                                    .setMessage("As Team Leader, do you approve this substitution for ${sub.role} (${sub.substituteName} replacing ${sub.requesterName})? The service lineup will be updated immediately.")
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton("Approve") { _, _ ->
                                        TeamRepository.acceptSubstitutionAndUpdateService(activeCode, sub, preferences) { res ->
                                            runOnUiThread {
                                                if (res.isSuccess) {
                                                    toast("Substitution approved and lineup updated!")
                                                    com.example.syncedn.data.auth.SyncedMessagingService.dispatchSystemNotification(
                                                        this@AdminActivity,
                                                        preferences,
                                                        "Substitution Approved",
                                                        "Leader approved substitution for ${sub.role} in ${sub.serviceTitle}."
                                                    )
                                                    refreshTeamServices(force = true)
                                                }
                                                else toast("Failed to approve substitution: ${res.exceptionOrNull()?.message ?: "Unknown error"}")
                                                populateSubstitutions()
                                            }
                                        }
                                    }.show()
                            }
                        }
                        val declineBtn = Button(this@AdminActivity).apply {
                            text = "Decline"
                            isAllCaps = false
                            background = bg(Color.WHITE, 8, Color.rgb(220, 38, 38))
                            setTextColor(Color.rgb(220, 38, 38))
                            minimumHeight = dp(38)
                            textSize = 12f
                            setTypeface(typeface, Typeface.BOLD)
                            setOnClickListener {
                                AlertDialog.Builder(this@AdminActivity).setTitle("Decline substitution?")
                                    .setMessage("Do you want to decline this request?")
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton("Decline") { _, _ ->
                                        TeamRepository.updateSubstitutionStatus(activeCode, sub.id, "Declined", preferences) { res ->
                                            runOnUiThread {
                                                if (res.isSuccess) {
                                                    toast("Substitution declined.")
                                                    com.example.syncedn.data.auth.SyncedMessagingService.dispatchSystemNotification(
                                                        this@AdminActivity,
                                                        preferences,
                                                        "Substitution Declined",
                                                        "Substitution for ${sub.role} in ${sub.serviceTitle} was declined."
                                                    )
                                                }
                                                else toast("Failed to decline substitution: ${res.exceptionOrNull()?.message ?: "Unknown error"}")
                                                populateSubstitutions()
                                            }
                                        }
                                    }.show()
                            }
                        }
                        actions.add(approveBtn, 0, 38, 1.35f)
                        actions.add(View(this@AdminActivity), 10, 1)
                        actions.add(declineBtn, 0, 38, 0.65f)
                        add(actions)
                        if (isPending) {
                            space(6)
                            add(text("⏳ Waiting for confirmation from ${sub.substituteName}, or tap above to approve directly.", 10, Color.rgb(100, 116, 139)))
                        }
                    }
                }); subCol.space(16)
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
        if (isLeader) {
            val topBar = horizontal().apply {
                gravity = Gravity.END
                setPadding(0, 0, 0, dp(16))
            }
            val postBtn = TextView(this@AdminActivity).apply {
                text = "+ Post"
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                background = bg(purple, 20)
                gravity = Gravity.CENTER
                setPadding(dp(22), dp(10), dp(22), dp(10))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    editingAnnouncement = null
                    navigate("announcementEdit")
                }
            }
            topBar.addView(postBtn, -2, -2)
            col.addView(topBar)
        }
        leaderPosts(col)
    }

    private fun notificationsView(): View = memberPage("Notification", "home") { col ->
        val activeCode = preferences.getString(SessionManager.KEY_TEAM_CODE, "").orEmpty()
            .ifBlank { preferences.getString("teamCode", "").orEmpty() }
            .ifBlank { FirestoreManager.findCodeByTeamName(teamName).orEmpty() }

        if (isLeader) {
            col.add(text("Re-join Requests", 15, purple, bold = true)); col.space(12)
            rejoinRequestsFeed(col)
            col.space(16)
        }

        col.add(text("Substitution Request", 15, purple, bold = true)); col.space(12)
        substitutionFeed(col)
        col.space(16)

        val notifHeader = horizontal().apply { gravity = Gravity.CENTER_VERTICAL }
        notifHeader.add(text("Recent Notifications", 15, purple, bold = true), 0, -2, 1f)
        val clearBtn = TextView(this@AdminActivity).apply {
            text = "Clear All"
            textSize = 12f
            setTextColor(Color.rgb(180, 50, 50))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                AlertDialog.Builder(this@AdminActivity)
                    .setTitle("Clear Notifications")
                    .setMessage("Are you sure you want to clear all recent notifications?")
                    .setPositiveButton("Clear All") { _, _ ->
                        TeamRepository.clearAllNotifications(activeCode, preferences)
                        navigate("notifications")
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        notifHeader.add(clearBtn, -2, -2)
        col.add(notifHeader); col.space(12)
        recentNotificationsList(col)
    }

    private fun rejoinRequestsFeed(col: LinearLayout) {
        val activeCode = preferences.getString(SessionManager.KEY_TEAM_CODE, "").orEmpty()
            .ifBlank { preferences.getString("teamCode", "").orEmpty() }
            .ifBlank { FirestoreManager.findCodeByTeamName(teamName).orEmpty() }
        if (activeCode.isBlank()) return

        val feedContainer = vertical()
        col.add(feedContainer)

        fun populateRejoinRequests() {
            feedContainer.removeAllViews()
            val list = TeamRepository.getCachedRejoinRequests(activeCode).filter { it.status == "Pending" }
            if (list.isEmpty()) {
                feedContainer.add(panel().apply {
                    add(text("No pending re-join requests.", 12, Color.GRAY, centered = true))
                })
                feedContainer.space(8)
                return
            }

            for (req in list) {
                feedContainer.add(panel().apply {
                    val headerRow = horizontal().apply { gravity = Gravity.CENTER_VERTICAL }
                    val iconBox = FrameLayout(this@AdminActivity).apply {
                        addView(Symbol(this@AdminActivity, "profile", purple), dp(24), dp(24))
                    }
                    headerRow.add(iconBox, 32, 32)
                    headerRow.space(10)
                    val titleCol = vertical()
                    titleCol.add(text(req.userName.ifBlank { "Team Member" }, 14, navy, true))
                    if (req.userEmail.isNotBlank()) {
                        titleCol.space(2)
                        titleCol.add(text(req.userEmail, 11, Color.GRAY))
                    }
                    headerRow.add(titleCol, 0, -2, 1f)

                    val statusBadge = text("Re-join Request", 10, Color.WHITE, bold = true, centered = true).apply {
                        background = bg(Color.rgb(217, 119, 6), 6)
                        setPadding(dp(8), dp(4), dp(8), dp(4))
                    }
                    headerRow.add(statusBadge, -2, -2)
                    add(headerRow); space(10)

                    val reasonText = if (req.reason.isNotBlank()) req.reason else "Requesting to re-join the team."
                    add(text(reasonText, 12, Color.rgb(66, 82, 114))); space(14)

                    val actions = horizontal().apply { gravity = Gravity.CENTER_VERTICAL }
                    val approveBtn = Button(this@AdminActivity).apply {
                        text = "Approve Re-join"
                        isAllCaps = false
                        background = bg(Color.rgb(46, 125, 50), 8)
                        setTextColor(Color.WHITE)
                        minimumHeight = dp(38)
                        textSize = 12f
                        setTypeface(typeface, Typeface.BOLD)
                        setOnClickListener {
                            AlertDialog.Builder(this@AdminActivity)
                                .setTitle("Approve Re-join?")
                                .setMessage("Allow ${req.userName} to re-join the team as an active member?")
                                .setPositiveButton("Approve") { _, _ ->
                                    TeamRepository.approveRejoinRequest(teamCode = activeCode, req = req, preferences = preferences) { res ->
                                        runOnUiThread {
                                            if (res.isSuccess) {
                                                toast("${req.userName} re-joined the team!")
                                                TeamRepository.getTeamRoster(teamCode = activeCode, force = true, preferences = preferences) {
                                                    runOnUiThread {
                                                        homeMemberCount?.text = "${getActiveRoster().size} Members"
                                                    }
                                                }
                                                populateRejoinRequests()
                                            } else {
                                                toast("Failed: ${res.exceptionOrNull()?.message ?: "Error"}")
                                            }
                                        }
                                    }
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                    }
                    val declineBtn = Button(this@AdminActivity).apply {
                        text = "Decline"
                        isAllCaps = false
                        background = bg(Color.rgb(243, 244, 246), 8, Color.rgb(209, 213, 219))
                        setTextColor(Color.rgb(180, 50, 50))
                        minimumHeight = dp(38)
                        textSize = 12f
                        setTypeface(typeface, Typeface.BOLD)
                        setOnClickListener {
                            AlertDialog.Builder(this@AdminActivity)
                                .setTitle("Decline Request?")
                                .setMessage("Decline re-join request for ${req.userName}?")
                                .setPositiveButton("Decline") { _, _ ->
                                    TeamRepository.declineRejoinRequest(activeCode, req.id) { res ->
                                        runOnUiThread {
                                            if (res.isSuccess) {
                                                toast("Re-join request declined.")
                                                populateRejoinRequests()
                                            } else {
                                                toast("Failed: ${res.exceptionOrNull()?.message ?: "Error"}")
                                            }
                                        }
                                    }
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                    }
                    actions.add(approveBtn, 0, 38, 1f)
                    actions.add(View(this@AdminActivity), 10, 1)
                    actions.add(declineBtn, 0, 38, 0.75f)
                    add(actions)
                }); feedContainer.space(12)
            }
        }

        populateRejoinRequests()

        TeamRepository.getRejoinRequests(activeCode) { res ->
            runOnUiThread {
                if (res.isSuccess && page == "notifications") {
                    populateRejoinRequests()
                }
            }
        }
    }

    private fun recentNotificationsList(col: LinearLayout) {
        val activeCode = preferences.getString(SessionManager.KEY_TEAM_CODE, "").orEmpty()
            .ifBlank { preferences.getString("teamCode", "").orEmpty() }
            .ifBlank { FirestoreManager.findCodeByTeamName(teamName).orEmpty() }
            .ifBlank { FirestoreManager.findCodeByTeamName(teamName).orEmpty() }
        val dynamicItems = TeamRepository.getRecentTeamNotifications(activeCode, preferences)
        if (dynamicItems.isEmpty()) {
            col.add(panel().apply {
                add(text("No recent notifications yet.", 12, Color.GRAY, centered = true))
            })
            col.space(12)
            return
        }
        for (notif in dynamicItems) {
            col.add(panel().apply {
                val row = horizontal().apply { gravity = Gravity.CENTER_VERTICAL }
                val iconBox = FrameLayout(this@AdminActivity).apply {
                    addView(Symbol(this@AdminActivity, notif.iconName, purple), dp(26), dp(26))
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
                val delBtn = TextView(this@AdminActivity).apply {
                    text = "✕"
                    textSize = 13f
                    setTextColor(Color.rgb(156, 163, 175))
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                    isClickable = true
                    isFocusable = true
                    contentDescription = "Delete notification"
                    setOnClickListener {
                        TeamRepository.deleteNotification(notif.id, preferences)
                        navigate("notifications")
                    }
                }
                row.add(delBtn, -2, -2)
                add(row)
            }); col.space(12)
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
    private fun songsView(): View = memberPage("Songs", songsReturnPage) { col ->
        val search = EditText(this).apply {
            hint = "Search by title or artist"
            setHintTextColor(Color.rgb(107, 114, 128))
            setTextColor(Color.rgb(30, 41, 59))
            textSize = 14f
            background = null
            setSingleLine(true)
            minWidth = 0
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        }
        val searchRow = horizontal().apply {
            val searchBar = horizontal().apply {
                background = bg(Color.rgb(217, 217, 217), 21)
                setPadding(dp(14), 0, dp(14), 0)
                add(Symbol(this@AdminActivity, "search", Color.rgb(75, 85, 99)), 20, 20)
                addView(View(this@AdminActivity), LinearLayout.LayoutParams(dp(8), 1))
                add(search, 0, -1, 1f)
            }
            addView(searchBar, LinearLayout.LayoutParams(0, dp(42), 1f))
            if (isLeader) {
                val plusBtn = FrameLayout(this@AdminActivity).apply {
                    val icon = Symbol(this@AdminActivity, "circle_plus", Color.BLACK)
                    addView(icon, FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER))
                    isClickable = true; isFocusable = true
                    contentDescription = "Add new song"
                    setOnClickListener { selectedSong = ""; navigate("songEdit") }
                }
                val plusLp = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                    leftMargin = dp(12)
                    gravity = Gravity.CENTER_VERTICAL
                }
                addView(plusBtn, plusLp)
            }
        }
        col.addView(searchRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        col.space(20)
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
                    row.add(Symbol(this@AdminActivity, "arrow", Color.GRAY), 18, 24)
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
    private fun songDetailsView(): View = memberPage("Song Details", songDetailsBack, footer = { sheetAutoScroll }) { col ->
        val activeCode = preferences.getString("teamCode", "").orEmpty()
        val currentSong = TeamRepository.getCachedSongs(activeCode, preferences).find { it.title.equals(selectedSong, ignoreCase = true) }
        val savedSong = try { org.json.JSONObject(preferences.getString("songData_$selectedSong", "{}").orEmpty()) }
            catch (_: Exception) { org.json.JSONObject() }
        val originalKey = currentSong?.key?.ifBlank { savedSong.optString("key", "C") } ?: savedSong.optString("key", "C")
        val basePitch = ChordSheet.keyPitch(originalKey).coerceAtLeast(0)
        val roots = listOf("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
        val minor = originalKey.endsWith("m") || originalKey.endsWith(" minor")
        val sheetRaw = (currentSong?.sheet?.ifBlank { currentSong.lyrics } ?: savedSong.optString("sheet", savedSong.optString("lyrics"))).ifBlank {
            "[Verse]\n[C]Add your licensed lyrics here.\n\n[Chorus]\n[Am]Add chorus lyrics here [F][G]"
        }
        val sheet = ChordSheet.formatStanzas(sheetRaw)
        val hasChordsInSheet = ChordSheet.hasChords(sheet)
        val showChords = hasChordsInSheet
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
            this@AdminActivity, preferences, selectedSong, col.parent as StableScrollView, sheetText
        )
        val keyButtons = mutableListOf<Pair<Int, TextView>>()
        fun renderTarget(target: Int) {
            offset = (target - basePitch + 12) % 12
            val root = roots[target]
            key.text = "Key: $root${if(minor) "m" else ""}    •    $tempo BPM"
            sheetText.text = ChordSheet.render(sheet, offset, !showChords, root.contains('b') || root == "F")
            keyButtons.forEach { (pitch, button) ->
                val selected = pitch == target
                button.setTextColor(if(selected) Color.WHITE else navy)
                button.background = SongControlStyle.background(resources, selected)
                button.isSelected = selected
            }
            preferences.edit().putInt("chord_transpose_$selectedSong", offset).apply()

        }
        if (isLeader) {
            col.add(memberButton("Edit Song", navy) { navigate("songEdit") }, -1, 48); col.space(16)
        }
        col.add(panel().apply {
            add(text(selectedSong, 18, navy, true)); space(6)
            val defaultArtist = if (selectedSong == "Way Maker") "Sinach" else if (selectedSong == "Gratitude") "Brandon Lake" else "Elevation Worship"
            val artistDisplay = currentSong?.artist?.ifBlank { savedSong.optString("artist", defaultArtist) } ?: savedSong.optString("artist", defaultArtist)
            add(text("By $artistDisplay", 13, Color.GRAY))
            space(16)
            add(key); space(16)
            if(showChords) {
                key.contentDescription = "Song key. Tap to choose any of twelve keys."
                key.isFocusable = true
                key.setOnClickListener {
                    AlertDialog.Builder(this@AdminActivity).setTitle("Transpose to key")
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
            val metronome = MetronomeView(this@AdminActivity, preferences, selectedSong, tempo.toIntOrNull() ?: 74)
            songMetronome = metronome
            add(metronome)
        }); col.space(24)
        val refLink = currentSong?.referenceUrl.orEmpty().ifBlank { savedSong.optString("link") }
        val ytVidId = extractYouTubeVideoId(refLink)
        if (ytVidId != null) {
            col.add(text("Rehearsal Video", 13, navy, true)); col.space(8)
            val webView = android.webkit.WebView(this@AdminActivity).apply {
                android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                try { settings.userAgentString = settings.userAgentString.replace("; wv", "") } catch (_: Throwable) {}
                webChromeClient = android.webkit.WebChromeClient()
                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <style>
                            body { margin: 0; background-color: #000; display: flex; align-items: center; justify-content: center; height: 100vh; }
                            iframe { width: 100%; height: 100%; border: 0; }
                        </style>
                    </head>
                    <body>
                        <iframe src="https://www.youtube.com/embed/$ytVidId?enablejsapi=1&origin=https://www.youtube.com&playsinline=1&rel=0" allowfullscreen></iframe>
                    </body>
                    </html>
                """.trimIndent()
                loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
            }
            col.add(panel().apply {
                setPadding(dp(4), dp(4), dp(4), dp(4))
                add(webView, -1, 200)
            }); col.space(8)
            col.add(memberButton("Watch on YouTube App", navy) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$ytVidId")).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$ytVidId")))
                }
            }, -1, 44); col.space(16)
        }

        col.add(text(if(showChords) "Lyrics & Chords Sheet" else "Lyrics", 13, navy, true)); col.space(10)
        col.add(panel().apply {
            add(sheetText, -1, -2)
        }); col.space(24)
        renderTarget((basePitch + offset) % 12)
        col.add(text("Reference Link", 13, navy, true)); col.space(10)
        col.add(memberButton("Open in YouTube App", navy) {
            val reference = currentSong?.referenceUrl.orEmpty().ifBlank { savedSong.optString("link") }
            val url = if (reference.startsWith("https://") || reference.startsWith("http://")) reference
                else "https://www.youtube.com/results?search_query=" + Uri.encode("$selectedSong worship")
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            catch (_: Exception) { toast("No browser available.") }
        }, -1, 48)
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
        addView(View(this@AdminActivity).apply {
            background = bg(Color.argb(70, 0, 0, 20))
        }, FrameLayout.LayoutParams(-1, -1))
        val row = horizontal().apply { setPadding(dp(8), 0, dp(16), 0) }
        row.add(FrameLayout(this@AdminActivity).apply {
            addView(Symbol(this@AdminActivity, "back", Color.WHITE),
                FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
            setOnClickListener {
                if (activeTeamSheet != null) {
                    activeTeamSheet = null
                    navigate("teamDetails")
                } else {
                    navigate(if (page in listOf("teamDetails", "roleEdit")) "teams" else "home")
                }
            }
            contentDescription = if (page == "teamDetails") "Back to Teams" else "Back to Home"
            isFocusable = true
        }, 40, 48)
        row.add(image("team_header_badge", "Team badge"), 56, 56)
        val currentCode = preferences.getString("teamCode", "").orEmpty()
            .ifBlank { FirestoreManager.findCodeByTeamName(teamName).orEmpty() }
        val subtitleText = if (currentCode.isNotBlank()) "Music Ministry • Code: $currentCode" else "Music Ministry"
        row.add(vertical().apply {
            setPadding(dp(12), 0, dp(8), 0)
            add(text(teamName.ifBlank { "Worship Team" }, 14, Color.WHITE, true).apply {
                maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
            })
            space(4); add(text(subtitleText, 11, Color.LTGRAY))
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
        addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            val w = right - left
            if (w > 0) {
                val imageWidth = (w * (if (showDetails) 237f else 130f) / 360f).toInt()
                if (photograph.layoutParams.width != imageWidth) {
                    photograph.layoutParams = photograph.layoutParams.apply { width = imageWidth }
                }
            }
        }
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
    private fun buildChatMessageBubble(msg: ChatMessage, currentUid: String): View {
        val maxBubbleWidth = (resources.displayMetrics.widthPixels * 0.72f).toInt()
        val timeFormat = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Manila")
        }
        val isSelf = (msg.senderUid.isNotBlank() && msg.senderUid == currentUid) ||
                (msg.senderName.equals(displayName, ignoreCase = true) || msg.senderName.equals(fullName, ignoreCase = true))

        if (isSelf) {
            val row = horizontal().apply {
                gravity = Gravity.END
                setPadding(0, dp(4), 0, dp(4))
            }
            val bubbleContainer = vertical().apply {
                gravity = Gravity.END
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
                if (msg.mediaUrl.isNotBlank()) {
                    val iv = ImageView(this@AdminActivity).apply {
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
                    if (msg.text.isNotBlank()) space(6)
                }
                if (msg.text.isNotBlank()) {
                    add(text(msg.text, 14, Color.WHITE).apply {
                        maxWidth = maxBubbleWidth - dp(28)
                    })
                }
            }
            bubbleContainer.addView(bubble, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.END })

            bubble.isClickable = true
            bubble.isFocusable = true
            bubble.setOnLongClickListener {
                val options = if (msg.text.isNotBlank()) arrayOf("Edit Message", "Delete Message") else arrayOf("Delete Message")
                AlertDialog.Builder(this@AdminActivity)
                    .setTitle("Message Options")
                    .setItems(options) { _, which ->
                        val action = options[which]
                        val curCode = preferences.getString("teamCode", "").orEmpty()
                        if (action == "Edit Message") {
                            val input = EditText(this@AdminActivity).apply {
                                setText(msg.text)
                                setSelection(msg.text.length)
                                setPadding(dp(16), dp(12), dp(16), dp(12))
                            }
                            AlertDialog.Builder(this@AdminActivity)
                                .setTitle("Edit Message")
                                .setView(input)
                                .setPositiveButton("Save") { _, _ ->
                                    val newTxt = input.text.toString().trim()
                                    if (newTxt.isNotBlank()) {
                                        TeamRepository.editChatMessage(curCode, msg.id, newTxt, preferences) { res ->
                                            runOnUiThread {
                                                if (res.isSuccess) {
                                                    val idx = chatMessages.indexOfFirst { it.id == msg.id }
                                                    if (idx != -1) chatMessages[idx] = chatMessages[idx].copy(text = newTxt)
                                                    navigate("teams")
                                                } else toast("Failed to edit message")
                                            }
                                        }
                                    }
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        } else if (action == "Delete Message") {
                            AlertDialog.Builder(this@AdminActivity)
                                .setTitle("Delete Message?")
                                .setMessage("Are you sure you want to delete this message?")
                                .setPositiveButton("Delete") { _, _ ->
                                    TeamRepository.deleteChatMessage(curCode, msg.id, preferences) { res ->
                                        runOnUiThread {
                                            if (res.isSuccess) {
                                                chatMessages.removeAll { it.id == msg.id }
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
                true
            }

            val timeStr = timeFormat.format(java.util.Date(msg.timestamp))
            val caption = text("$timeStr  ✓✓ Delivered", 10, Color.rgb(148, 163, 184)).apply {
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
                getActiveRoster().find { it.uid == msg.senderUid || it.fullName.equals(msg.senderName, true) || it.username.equals(msg.senderName, true) }?.photoUri ?: ""
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
                if (msg.mediaUrl.isNotBlank()) {
                    val iv = ImageView(this@AdminActivity).apply {
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
                    if (msg.text.isNotBlank()) space(6)
                }
                if (msg.text.isNotBlank()) {
                    add(text(msg.text, 14, Color.rgb(21, 28, 50)).apply {
                        maxWidth = maxBubbleWidth - dp(28)
                    })
                }
            }
            if (isLeader) {
                bubble.isClickable = true
                bubble.isFocusable = true
                bubble.setOnLongClickListener {
                    val curCode = preferences.getString("teamCode", "").orEmpty()
                    AlertDialog.Builder(this@AdminActivity)
                        .setTitle("Admin Actions")
                        .setItems(arrayOf("Delete Message")) { _, _ ->
                            AlertDialog.Builder(this@AdminActivity)
                                .setTitle("Delete Message?")
                                .setMessage("As Leader, delete this message for everyone?")
                                .setPositiveButton("Delete") { _, _ ->
                                    TeamRepository.deleteChatMessage(curCode, msg.id, preferences) { res ->
                                        runOnUiThread {
                                            if (res.isSuccess) {
                                                chatMessages.removeAll { it.id == msg.id }
                                                navigate("teams")
                                            } else toast("Failed to delete message")
                                        }
                                    }
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }.show()
                    true
                }
            }
            bubbleContainer.addView(bubble, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.START })

            val timeStr = timeFormat.format(java.util.Date(msg.timestamp))
            val caption = text(timeStr, 10, Color.rgb(148, 163, 184)).apply {
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
        if (teamTab == "Team Chat" && currentCode.isNotBlank()) {
            if (chatMessages.isEmpty()) {
                val cached = TeamRepository.getCachedChatMessages(currentCode, preferences)
                if (cached.isNotEmpty()) chatMessages.addAll(cached)
            }
            if (chatListener == null) {
                chatListener = TeamRepository.listenTeamMessages(currentCode, preferences) { msgs ->
                    runOnUiThread {
                        if (page == "teams" && teamTab == "Team Chat") {
                            val existingIds = chatMessages.map { it.id }.toSet()
                            val incoming = msgs.filter { it.id !in existingIds }
                            val mediaUpdated = msgs.filter { m ->
                                val cur = chatMessages.find { it.id == m.id }
                                cur != null && cur.mediaUrl != m.mediaUrl && m.mediaUrl.isNotBlank()
                            }
                            if (incoming.isNotEmpty()) {
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
                                    navigate("teams")
                                }
                            } else if (mediaUpdated.isNotEmpty()) {
                                val map = LinkedHashMap<String, ChatMessage>()
                                chatMessages.forEach { map[it.id] = it }
                                msgs.forEach { map[it.id] = it }
                                chatMessages.clear()
                                chatMessages.addAll(map.values.sortedBy { it.timestamp })
                                navigate("teams")
                            }
                        }
                    }
                }
            }
        } else {
            chatListener?.remove()
            chatListener = null
        }
        val root = vertical().apply { setBackgroundColor(Color.WHITE) }
        root.add(teamHeader(showDetails = true), -1, 156)
        root.add(teamTabs())
        val scroll = StableScrollView(this).apply { isFillViewport = true; clipToPadding = true }
        val col = vertical().apply { setPadding(dp(20), dp(16), dp(20), dp(20)) }
        scroll.addView(col, FrameLayout.LayoutParams(-1, -2))
        root.add(scroll, -1, 0, 1f)
        when (teamTab) {
            "Members" -> {
                val roster = getActiveRoster()
                roster.forEach { member ->
                    val name = member.fullName.ifBlank { member.username.ifBlank { "Member" } }
                    val role = member.ministryRole.ifBlank { if (member.role.equals("Leader", true)) "Worship Leader" else "Member" }
                    val isLeaderBadge = member.email.equals("ryanlloydgenturo@gmail.com", ignoreCase = true) ||
                                        member.fullName.equals("Ryan Lloyd Genturo", ignoreCase = true) ||
                                        member.ministryRole.equals("Worship Leader", ignoreCase = true) ||
                                        member.ministryRole.equals("Substitute Leader", ignoreCase = true)
                    val badge = if (isLeaderBadge) "Leader" else "Member"
                    col.add(memberRow(name, role, badge, if (isLeader) ({
                        roleMember = name
                        roleMemberUid = member.uid
                        roleMemberEmail = member.email
                        navigate("roleEdit")
                    }) else null, member.photoUri))
                    col.space(4)
                }
            }
            "Role" -> {
                col.setPadding(0, dp(24), 0, 0)
                val roster = getActiveRoster()
                val currentUid = preferences.getString(SessionManager.KEY_USER_ID, "").orEmpty()
                val baseRoles = if (isLeader) leaderRoles else listOf("Worship Leader", "Keyboardist", "Bassist", "Vocalist", "Guitarist")
                val activeRoles = (baseRoles + roster.map { it.ministryRole }).filter { it.isNotBlank() }.distinct()
                val roles = activeRoles
                roles.forEach { role ->
                    val people = roster.filter { it.ministryRole.equals(role, ignoreCase = true) }
                    col.add(horizontal().apply {
                        setPadding(dp(40), 0, dp(20), 0)
                        add(Symbol(this@AdminActivity, "music", Color.BLACK), 56, 52)
                        add(vertical().apply {
                            setPadding(dp(20), 0, dp(8), 0)
                            add(text(role, 14, navy, true)); space(4)
                            add(text("${people.size} member${if (people.size == 1) "" else "s"}", 12, Color.BLACK))
                        }, 0, -2, 1f)
                        add(Symbol(this@AdminActivity, "arrow", Color.rgb(65, 80, 100)).apply {
                            if (expandedRole == role) rotation = 90f
                        }, 28, 28)
                        isFocusable = true; contentDescription = "$role, ${people.size} members"
                        setOnClickListener { expandedRole = if (expandedRole == role) null else role; navigate("teams") }
                    }, -1, 64)
                    if (expandedRole == role) {
                        col.space(8)
                        val members = vertical().apply {
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
                                        if (isLeader) setOnClickListener {
                                            roleMember = memberName
                                            roleMemberUid = member.uid
                                            roleMemberEmail = member.email
                                            navigate("roleEdit")
                                        }
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
                        col.add(members, -1, -2)
                        col.space(12)
                    }
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
                    displayList.forEach { msg ->
                        col.addView(buildChatMessageBubble(msg, currentUid))
                    }
                }
            }
        }
        if (teamTab == "Team Chat") {
            val composer = horizontal().apply { setPadding(dp(12), dp(8), dp(12), dp(12)) }
            fun choosePhoto() {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*" }
                @Suppress("DEPRECATION")
                startActivityForResult(intent, 301)
            }
            composer.add(text("+", 28, Color.BLACK, centered = true).apply {
                contentDescription = "Add attachment"; isFocusable = true
                setOnClickListener { choosePhoto() }
            }, 36, 44)
            composer.add(FrameLayout(this).apply {
                addView(Symbol(this@AdminActivity, "photo", Color.BLACK),
                    FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER))
                contentDescription = "Choose chat photo"; isFocusable = true
                setOnClickListener { choosePhoto() }
            }, 36, 44)
            val entry = horizontal().apply { background = bg(Color.rgb(217, 217, 217), 24) }
            val message = plainInput("Message").apply { background = null }
            entry.add(message, 0, 44, 1f)
            val send = text("☺", 24, Color.BLACK, centered = true).apply {
                contentDescription = "Insert emoji or send message"; isFocusable = true
                setOnClickListener {
                    if (message.text.isBlank()) {
                        message.setText("🙂")
                        message.setSelection(message.text.length)
                    } else {
                        val textToSend = message.text.toString().trim()
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
                            senderName = fullName.ifBlank { displayName.ifBlank { "Leader" } },
                            senderRole = "Worship Leader",
                            senderPhotoUri = currentPhoto,
                            text = textToSend,
                            timestamp = System.currentTimeMillis()
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
                    send.text = if (s.isNullOrBlank()) "☺" else "➤"
                    send.contentDescription = if (s.isNullOrBlank()) "Insert emoji" else "Send message"
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
        val root = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }

        val mainCol = vertical().apply { setBackgroundColor(Color.WHITE) }
        mainCol.add(teamHeader(), -1, 88)
        val scroll = StableScrollView(this).apply { isFillViewport = true; clipToPadding = true }
        val col = vertical().apply { setPadding(dp(20), dp(24), dp(20), dp(24)) }
        scroll.addView(col, FrameLayout.LayoutParams(-1, -2))
        mainCol.add(scroll, -1, 0, 1f)
        root.addView(mainCol, FrameLayout.LayoutParams(-1, -1))

        col.add(text("Team Name", 14, Color.BLACK, true)); col.space(8)
        val teamNameBox = horizontal().apply {
            gravity = Gravity.CENTER_VERTICAL
            background = bg(Color.rgb(217, 217, 217), 10)
            setPadding(dp(16), 0, dp(16), 0)
            add(text(teamName.ifBlank { "Worship Team" }, 14, Color.BLACK, bold = true), 0, -2, 1f)
        }
        col.add(teamNameBox, -1, 42); col.space(20)

        if (isLeader) {
            val emailLower = preferences.getString("email", "").orEmpty().trim().lowercase()
            val uid = preferences.getString("userId", "").orEmpty()
            val activeCode = preferences.getString("teamCode", "").orEmpty()
                .ifBlank { preferences.getString("email_code_$emailLower", "").orEmpty() }
                .ifBlank { preferences.getString("user_code_$uid", "").orEmpty() }
                .ifBlank { preferences.getString("team_leader_code_${teamName.trim().lowercase()}", "").orEmpty() }
                .ifBlank { FirestoreManager.findCodeByTeamName(teamName).orEmpty() }
                .ifBlank { "Generating..." }
            if (activeCode != "Generating...") {
                saveUserTeamState("Leader", teamName, activeCode)
            }
            col.add(text("Invite", 14, Color.BLACK, true)); col.space(8)
            val inviteCodeText = text(activeCode, 14, Color.BLACK, bold = true)
            val inviteRow = horizontal().apply {
                gravity = Gravity.CENTER_VERTICAL
                background = bg(Color.rgb(217, 217, 217), 10)
                setPadding(dp(16), 0, dp(16), 0)
                isClickable = true
                isFocusable = true
                contentDescription = "Copy team invite code"
                add(inviteCodeText, 0, -2, 1f)
                add(Symbol(this@AdminActivity, "copy", Color.BLACK), 22, 22)
                setOnClickListener {
                    val currentCode = preferences.getString("teamCode", "").orEmpty()
                    if (currentCode.isNotBlank()) {
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("Team Invite Code", currentCode)
                        )
                        toast("Team invite code copied: $currentCode")
                    } else {
                        toast("Generating team invite code, please wait...")
                    }
                }
            }
            col.add(inviteRow, -1, 42); col.space(24)

            if (activeCode == "Generating..." && teamName.isNotBlank()) {
                val emailStr = preferences.getString("email", "").orEmpty()
                FirestoreManager.createTeamWithCode(uid, displayName, teamName) { res ->
                    runOnUiThread {
                        res.getOrNull()?.let { newCode ->
                            saveUserTeamState("Leader", teamName, newCode)
                            FirestoreManager.updateUserRoleAndTeam(uid, "Leader", teamName, newCode, emailStr)
                            inviteCodeText.text = newCode
                        }
                    }
                }
            }

            col.add(teamDetailAction("remove_member", "Remove member") {
                activeTeamSheet = "removeMember"
                navigate("teamDetails")
            })
            col.space(4)
            col.add(teamDetailAction("musical_notes", "Add Role") {
                activeTeamSheet = "addRole"
                navigate("teamDetails")
            })
            col.space(4)
        }

        val unmuteRow = horizontal().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        unmuteRow.add(Symbol(this, "bell", Color.BLACK), 28, 28)
        unmuteRow.add(text("Unmute Messages", 15, Color.BLACK, true).apply {
            setPadding(dp(16), 0, dp(8), 0)
        }, 0, 48, 1f)
        unmuteRow.add(Switch(this).apply {
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
        col.add(unmuteRow); col.space(4)

        col.add(teamDetailAction("chat", "Team Reports") {
            val activeCode = preferences.getString("teamCode", "").orEmpty()
                .ifBlank { FirestoreManager.findCodeByTeamName(teamName).orEmpty() }
            val progress = android.app.ProgressDialog(this).apply {
                setMessage("Loading team reports...")
                setCancelable(false)
                show()
            }
            TeamRepository.getTeamReports(activeCode, preferences) { reports ->
                runOnUiThread {
                    progress.dismiss()
                    if (reports.isEmpty()) {
                        AlertDialog.Builder(this)
                            .setTitle("Team Reports")
                            .setMessage("No reports have been submitted for this team.")
                            .setPositiveButton("OK", null)
                            .show()
                    } else {
                        val reportItems = reports.map { rep ->
                            val reporter = rep["reporterName"]?.toString() ?: "Member"
                            val reason = rep["reason"]?.toString() ?: ""
                            val ts = (rep["createdAt"] as? Number)?.toLong() ?: 0L
                            val dateStr = if (ts > 0) {
                                java.text.SimpleDateFormat("MMM d, yyyy h:mm a", java.util.Locale.US).apply {
                                    timeZone = java.util.TimeZone.getTimeZone("Asia/Manila")
                                }.format(java.util.Date(ts))
                            } else "Recent"
                            "From: $reporter ($dateStr)\n$reason"
                        }.toTypedArray()

                        AlertDialog.Builder(this)
                            .setTitle("Team Reports (${reports.size})")
                            .setItems(reportItems) { _, which ->
                                val selected = reports[which]
                                val reporter = selected["reporterName"]?.toString() ?: "Member"
                                val reason = selected["reason"]?.toString() ?: ""
                                val ts = (selected["createdAt"] as? Number)?.toLong() ?: 0L
                                val dateStr = if (ts > 0) {
                                    java.text.SimpleDateFormat("MMM d, yyyy h:mm a", java.util.Locale.US).apply {
                                        timeZone = java.util.TimeZone.getTimeZone("Asia/Manila")
                                    }.format(java.util.Date(ts))
                                } else "Recent"
                                AlertDialog.Builder(this)
                                    .setTitle("Report from $reporter")
                                    .setMessage("Date: $dateStr\n\nIssue Details:\n$reason")
                                    .setPositiveButton("Close", null)
                                    .show()
                            }
                            .setPositiveButton("Close", null)
                            .show()
                    }
                }
            }
        }); col.space(4)

        col.add(teamDetailAction("warning", "Report") {
            val reason = plainInput("Describe the issue", true)
            AlertDialog.Builder(this).setTitle("Report Team").setView(reason)
                .setNegativeButton("Cancel", null).setPositiveButton("Submit Report", null).create().apply {
                    setOnShowListener {
                        getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                            if (reason.text.isBlank()) invalid(reason, "Enter a reason.")
                            else {
                                val activeCode = preferences.getString("teamCode", "").orEmpty()
                                    .ifBlank { FirestoreManager.findCodeByTeamName(teamName).orEmpty() }
                                val reporterName = fullName.ifBlank { displayName }.ifBlank { "Leader" }
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

        // Overlay Sheet (Add Role or Remove Member)
        if (activeTeamSheet != null) {
            val scrim = View(this).apply {
                setBackgroundColor(Color.argb(120, 0, 0, 0))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    activeTeamSheet = null
                    navigate("teamDetails")
                }
            }
            root.addView(scrim, FrameLayout.LayoutParams(-1, -1))

            val sheetContainer = vertical().apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.rgb(21, 29, 56), Color.rgb(46, 62, 120))
                ).apply {
                    cornerRadii = floatArrayOf(
                        dp(32).toFloat(), dp(32).toFloat(),
                        dp(32).toFloat(), dp(32).toFloat(),
                        0f, 0f,
                        0f, 0f
                    )
                }
                setPadding(dp(20), dp(24), dp(20), dp(28))
            }

            if (activeTeamSheet == "addRole") {
                // View Details (1).png
                val sheetScroll = StableScrollView(this).apply { isFillViewport = true }
                val sheetCol = vertical()
                sheetScroll.addView(sheetCol, FrameLayout.LayoutParams(-1, -2))

                sheetCol.add(text("Existing Roles", 18, Color.WHITE, bold = true, centered = true))
                sheetCol.space(20)

                val rolesCol = vertical()
                sheetCol.addView(rolesCol)

                leaderRoles.forEach { role ->
                    val roleBox = horizontal().apply {
                        gravity = Gravity.CENTER_VERTICAL
                        background = bg(Color.rgb(217, 217, 217), 8)
                        setPadding(dp(16), 0, dp(16), 0)
                        add(text(role, 14, Color.BLACK, bold = true), 0, -2, 1f)
                    }
                    rolesCol.add(roleBox, -1, 44)
                    rolesCol.space(8)
                }

                val newRoleInput = plainInput("New Role").apply {
                    background = bg(Color.rgb(217, 217, 217), 8)
                    setPadding(dp(16), 0, dp(16), 0)
                    setTextColor(Color.BLACK)
                    setHintTextColor(Color.rgb(117, 117, 117))
                    textSize = 14f
                }
                sheetCol.add(newRoleInput, -1, 44)
                sheetCol.space(24)

                val saveBtnRow = horizontal().apply { gravity = Gravity.CENTER }
                val saveBtn = TextView(this).apply {
                    text = "Save"
                    textSize = 14f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    background = bg(Color.rgb(0, 153, 255), 20)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        val entered = newRoleInput.text.toString().trim()
                        if (entered.isNotBlank() && !leaderRoles.any { it.equals(entered, ignoreCase = true) }) {
                            leaderRoles.add(entered)
                            preferences.edit().putString("leaderRoles", org.json.JSONArray(leaderRoles).toString()).apply()
                            val currentCode = preferences.getString("teamCode", "").orEmpty()
                                .ifBlank { FirestoreManager.findCodeByTeamName(teamName).orEmpty() }
                            if (currentCode.isNotBlank()) {
                                TeamRepository.saveTeamCustomRoles(currentCode, leaderRoles) { }
                            }
                            toast("Role added: $entered")
                        }
                        activeTeamSheet = null
                        navigate("teamDetails")
                    }
                }
                saveBtnRow.addView(saveBtn, dp(140), dp(40))
                sheetCol.addView(saveBtnRow)

                sheetContainer.addView(sheetScroll, LinearLayout.LayoutParams(-1, -2))
            } else if (activeTeamSheet == "removeMember") {
                // Group 107.png
                val sheetScroll = StableScrollView(this).apply { isFillViewport = true }
                val sheetCol = vertical()
                sheetScroll.addView(sheetCol, FrameLayout.LayoutParams(-1, -2))

                val currentUid = preferences.getString("userId", "").orEmpty()
                val currentName = fullName.ifBlank { displayName }
                val roster = getActiveRoster().filter { it.uid != currentUid && it.fullName != currentName }

                if (roster.isEmpty()) {
                    sheetCol.add(text("No members to remove.", 14, Color.WHITE, centered = true))
                    sheetCol.space(16)
                } else {
                    roster.forEach { member ->
                        val mName = member.fullName.ifBlank { member.username.ifBlank { "Member" } }
                        val mRole = member.ministryRole.ifBlank { "Member" }
                        val memberRow = horizontal().apply {
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(dp(4), dp(10), dp(4), dp(10))
                            isClickable = true
                            isFocusable = true
                            add(memberAvatarView(member.photoUri, 56), 56, 56)
                            val info = vertical().apply {
                                setPadding(dp(14), 0, 0, 0)
                                add(text(mName, 16, Color.WHITE, bold = true))
                                space(3)
                                add(text(mRole, 13, Color.rgb(203, 213, 225)))
                            }
                            add(info, 0, -2, 1f)
                            setOnClickListener {
                                AlertDialog.Builder(this@AdminActivity).setTitle("Remove $mName?")
                                    .setMessage("Are you sure you want to remove $mName from the team?")
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton("Remove") { _, _ ->
                                        val activeCode = preferences.getString("teamCode", "").orEmpty()
                                        val removed = preferences.getStringSet("removedMembers", emptySet()).orEmpty().toMutableSet()
                                        removed.add(mName)
                                        if (member.uid.isNotBlank()) removed.add(member.uid)
                                        if (member.email.isNotBlank()) removed.add(member.email.trim().lowercase())
                                        preferences.edit().putStringSet("removedMembers", removed).apply()
                                        if (member.uid.isNotBlank()) {
                                            TeamRepository.removeMemberFromTeam(member.uid, activeCode, member.email, preferences) { result ->
                                                runOnUiThread {
                                                    if (result.isSuccess) {
                                                        toast("$mName removed from team.")
                                                        refreshTeamRoster(force = true)
                                                        activeTeamSheet = null
                                                        navigate("teamDetails")
                                                    } else {
                                                        val err = result.exceptionOrNull()?.localizedMessage ?: "Failed to remove member."
                                                        toast(err)
                                                        activeTeamSheet = null
                                                        navigate("teamDetails")
                                                    }
                                                }
                                            }
                                        } else {
                                            toast("$mName removed.")
                                            activeTeamSheet = null
                                            navigate("teamDetails")
                                        }
                                    }.show()
                            }
                        }
                        sheetCol.add(memberRow)
                        sheetCol.space(4)
                    }
                }
                sheetContainer.addView(sheetScroll, LinearLayout.LayoutParams(-1, -2))
            }

            val sheetLp = FrameLayout.LayoutParams(-1, -2).apply {
                gravity = Gravity.BOTTOM
            }
            root.addView(sheetContainer, sheetLp)
        }

        return root
    }
    private fun teamDetailAction(icon: String, label: String, action: () -> Unit): View = horizontal().apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(4), 0, dp(4))
        add(Symbol(this@AdminActivity, icon, Color.BLACK), 28, 28)
        add(text(label, 15, Color.BLACK, true).apply { setPadding(dp(16), 0, 0, 0) }, 0, 48, 1f)
        isFocusable = true; isClickable = true; contentDescription = label; setOnClickListener { action() }
    }

    private fun memberTile(id: Int, icon: String, label: String, labelSize: Int = 14) = vertical().apply {
        this.id=id;gravity=Gravity.CENTER
        background=bg(Color.WHITE,24,Color.rgb(211,211,211));elevation=dp(4).toFloat()
        isClickable=true;isFocusable=true;contentDescription=label
        add(Symbol(this@AdminActivity,icon,Color.rgb(174,0,234)),28,28)
        space(3);add(text(label,labelSize,navy,true,true))
    }
}

