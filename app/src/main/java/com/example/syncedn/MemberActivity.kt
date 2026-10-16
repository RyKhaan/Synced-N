package com.example.syncedn

import com.example.syncedn.data.auth.AuthManager
import com.example.syncedn.data.auth.EmailOtpService
import com.example.syncedn.data.auth.SessionManager
import com.example.syncedn.data.database.FirestoreManager
import com.example.syncedn.data.database.TeamRepository
import com.example.syncedn.data.model.TeamMember
import com.example.syncedn.data.model.UnavailableRange
import com.example.syncedn.data.util.ValidationUtils
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
import android.widget.ScrollView
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
    protected open val initialPage: String = "home"
    private var page = "start"
    private var displayName = ""
    private var fullName = ""
    private var profileReturnPage = "settings"
    protected val preferences by lazy { getSharedPreferences("synced_profile", MODE_PRIVATE) }
    private var teamName = ""
    private var successFlow = "register"

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

    private fun resolveUserTeamSession(
        profile: com.example.syncedn.data.model.UserProfile?,
        email: String
    ): Triple<String, String, String> {
        syncLocalTeamsToMemory()
        val uid = profile?.uid.orEmpty()
        val emailLower = email.trim().lowercase()

        val savedRole = preferences.getString("email_role_$emailLower", "").orEmpty()
            .ifBlank { preferences.getString("user_role_$uid", "").orEmpty() }
        val savedTeam = preferences.getString("email_team_$emailLower", "").orEmpty()
            .ifBlank { preferences.getString("user_team_$uid", "").orEmpty() }
        val savedCode = preferences.getString("email_code_$emailLower", "").orEmpty()
            .ifBlank { preferences.getString("user_code_$uid", "").orEmpty() }

        val remoteRole = profile?.role.orEmpty()
        val remoteTeam = profile?.teamName.orEmpty()
        val remoteCode = profile?.teamCode.orEmpty()

        val finalTeam = savedTeam.ifBlank { remoteTeam }
        val finalCode = savedCode
            .ifBlank { remoteCode }
            .ifBlank { preferences.getString("team_leader_code_${finalTeam.trim().lowercase()}", "").orEmpty() }
            .ifBlank { FirestoreManager.findCodeByTeamName(finalTeam).orEmpty() }

        val finalRole = when {
            savedRole.equals("Leader", ignoreCase = true) || remoteRole.equals("Leader", ignoreCase = true) -> "Leader"
            savedRole.isNotBlank() -> savedRole
            finalTeam.isNotBlank() && preferences.getString("team_leader_email_${finalTeam.trim().lowercase()}", "") == emailLower -> "Leader"
            remoteRole.isNotBlank() -> remoteRole
            else -> "Member"
        }

        return Triple(finalRole, finalTeam, finalCode)
    }

    private fun saveUserTeamState(role: String, team: String, code: String) {
        val uid = preferences.getString("userId", "").orEmpty()
            .ifBlank { com.example.syncedn.data.auth.AuthManager.currentUser?.uid.orEmpty() }
        val emailLower = preferences.getString("email", "").orEmpty().trim().lowercase()
        val cleanTeamLower = team.trim().lowercase()
        if (code.isNotBlank() && team.isNotBlank()) {
            FirestoreManager.registerLocalTeam(code, team)
            val currentName = fullName.ifBlank { displayName }
            val ministry = preferences.getString("ministryRole", if (role.equals("Leader", true)) "Worship Leader" else "Member").orEmpty()
            val member = com.example.syncedn.data.model.TeamMember(
                uid = uid.ifBlank { "user_${emailLower.hashCode()}" },
                fullName = currentName,
                username = displayName,
                email = emailLower,
                role = role,
                ministryRole = ministry,
                teamCode = code
            )
            TeamRepository.cacheMember(member, preferences)
        }
        FirestoreManager.cacheUserRoleAndTeam(uid, emailLower, role, team, code)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_MyApplication)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        syncLocalTeamsToMemory()
        displayName = savedInstanceState?.getString("displayName") ?: preferences.getString(SessionManager.KEY_USERNAME, "").orEmpty()
        fullName = preferences.getString(SessionManager.KEY_FULL_NAME, "").orEmpty()
        profileReturnPage = savedInstanceState?.getString("profileReturnPage") ?: "settings"
        teamName = savedInstanceState?.getString("teamName") ?: preferences.getString("teamName", "").orEmpty()
        successFlow = savedInstanceState?.getString("successFlow") ?: "register"
        savedInstanceState?.let { state ->
            scheduleFilter = state.getString("scheduleFilter", "Upcoming") ?: "Upcoming"
            teamTab = state.getString("teamTab", "Team Chat") ?: "Team Chat"
            expandedRole = state.getString("expandedRole")
            selectedSong = state.getString("selectedSong", "Trust in God") ?: "Trust in God"
            selectedService = state.getString("selectedService", "Sunday Worship Service") ?: "Sunday Worship Service"
            serviceReturnPage = state.getString("serviceReturnPage", "schedule") ?: "schedule"
            calendarYear = state.getInt("calendarYear", 2026)
            calendarMonth = state.getInt("calendarMonth", 9)
            calendarDay = state.getInt("calendarDay", 4)
            substitute = state.getString("substitute", "John Doe") ?: "John Doe"
            songsReturnPage = state.getString("songsReturnPage", "home") ?: "home"
            chatMessages.addAll(state.getStringArrayList("chatMessages") ?: emptyList())
            state.getStringArrayList("lineupSongs")?.let { lineupSongs.clear(); lineupSongs.addAll(it) }
        }
        navigate(savedInstanceState?.getString("page") ?: intent.getStringExtra("startPage") ?: initialPage)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val previous = when (page) {
                    "login" -> "start"
                    "register", "reset" -> "login"
                    "otp" -> "reset"
                    "success" -> "login"
                    "teamChoice" -> "login"
                    "createTeam", "joinTeam" -> "teamChoice"
                    "home" -> "login"
                    "settings" -> "home"
                    "profile" -> profileReturnPage
                    "schedule", "calendar", "announcements", "teams" -> "home"
                    "availability" -> "calendar"
                    "service" -> serviceReturnPage
                    "substitution", "lineup" -> "service"
                    "songs" -> songsReturnPage
                    "songDetails" -> "songs"
                    "teamDetails" -> "teams"
                    else -> null
                }
                if (previous != null) navigate(previous)
                else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })
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
        outState.putString("selectedService", selectedService)
        outState.putString("serviceReturnPage", serviceReturnPage)
        outState.putString("substitute", substitute)
        outState.putString("songsReturnPage", songsReturnPage)
        outState.putInt("calendarYear", calendarYear)
        outState.putInt("calendarMonth", calendarMonth)
        outState.putInt("calendarDay", calendarDay)
        outState.putStringArrayList("chatMessages", ArrayList(chatMessages))
        outState.putStringArrayList("lineupSongs", ArrayList(lineupSongs))
        super.onSaveInstanceState(outState)
    }

    protected open fun navigate(next: String) {
        if (next in listOf("start", "login") && javaClass == MemberActivity::class.java) {
            startActivity(Intent(this, MainActivity::class.java).putExtra("startPage", next))
            finish()
            return
        }
        page = next
        val root = buildScreen(page)
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
                    val uid = preferences.getString("userId", "").orEmpty()
                        .ifBlank { com.example.syncedn.data.auth.AuthManager.currentUser?.uid.orEmpty() }
                        .ifBlank { "user_${displayName.hashCode()}" }
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
                                if (javaClass == MainActivity::class.java) {
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
            "home" -> {
                refreshTeamRoster()
                setupHome()
            }
            "teams" -> refreshTeamRoster()
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
                                val uid = profile?.uid.orEmpty()
                                displayName = profile?.username?.ifBlank { emailStr.substringBefore("@") } ?: "Member"
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
                                SessionManager.clearSession(preferences)
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
        findViewById<TextView>(UI.welcomeText).text = displayName.ifBlank { "Member" }
        click(UI.notificationsButton) { navigate("announcements") }
        applyProfilePhoto(UI.profileButton)
        click(UI.profileButton) { openProfile("home") }
        click(UI.worshipCard) {
            selectedService = "Sunday Worship Service"; serviceReturnPage = "home"
            preferences.edit().putString("serviceDate", "May 18, 2025").putString("serviceTime", "9:00AM").putString("serviceVenue", "Main Sanctuary").apply()
            navigate("service")
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
        click(UI.scheduleCard) { openHomeUpcoming() }
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
        val bitmap = bitmaps.getOrPut(name) {
            val resId = resources.getIdentifier(name, "drawable", packageName)
            if (resId != 0) {
                val opts = BitmapFactory.Options().apply { inScaled = false }
                BitmapFactory.decodeResource(resources, resId, opts)
            } else {
                val bytes = Base64.decode(Art.data(name), Base64.DEFAULT)
                requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
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
    private fun form(right: Boolean = false): Pair<FrameLayout, LinearLayout> {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        val scroll = ScrollView(this).apply { isFillViewport = true }
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
                    add(field(UI.loginEmail,hint="Email or Mobile Number",icon="mail")); space(20)
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
                col.space(69); col.section().apply {
                    add(text("OTP Verification Code",centered=true)); space(16)
                    add(field(UI.otpCode,number=true)); space(30)
                    action(UI.verifyOtpButton,"Reset Password"); space(10)
                    add(text("Enter verification code",12,Color.GRAY,centered=true)); space(24)
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
            "createTeam","joinTeam" -> {
                col.space(71); col.section().apply {
                    val create = screen=="createTeam"
                    add(field(if(create) UI.teamName else UI.teamCode,if(create) "Team Name" else "Enter Team’s Code")); space(18)
                    action(if(create) UI.createTeamButton else UI.joinTeamButton,if(create) "Create" else "Enter",-1); space(24)
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
            lastFrameTime = android.os.SystemClock.uptimeMillis()
            postInvalidateOnAnimation()
        }

        override fun onVisibilityChanged(changedView: View, visibility: Int) {
            super.onVisibilityChanged(changedView, visibility)
            if (visibility == View.VISIBLE) {
                lastFrameTime = android.os.SystemClock.uptimeMillis()
                postInvalidateOnAnimation()
            }
        }

        override fun onWindowVisibilityChanged(visibility: Int) {
            super.onWindowVisibilityChanged(visibility)
            if (visibility == View.VISIBLE) {
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

            if (isAttachedToWindow && windowVisibility == View.VISIBLE) {
                postInvalidateOnAnimation()
            }
        }
    }
    private fun homeUpcomingRecord(): org.json.JSONObject? {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        return storedServices().filter { it.optString("status") == "Published" && it.optString("date") >= today }
            .minByOrNull { it.optString("date") + it.optString("start") }
    }
    private fun storedServices(): List<org.json.JSONObject> = try {
        val records = org.json.JSONArray(preferences.getString("leaderServices", "[]"))
        (0 until records.length()).map { records.getJSONObject(it) }
    } catch (_: Exception) { emptyList() }
    private fun formatServiceTime(value: String): String = try {
        val parts=value.split(":");val hour=parts[0].toInt()
        "${if(hour % 12 == 0) 12 else hour % 12}:${parts[1]}${if(hour < 12) "AM" else "PM"}"
    } catch (_: Exception) { value }
    private fun openHomeUpcoming() {
        val record=homeUpcomingRecord();serviceReturnPage="home"
        selectedService=record?.optString("title") ?: "Sunday Worship Service"
        preferences.edit().putString("serviceDate",record?.optString("date") ?: "May 18, 2025")
            .putString("serviceTime", if(record!=null) formatServiceTime(record.optString("start")) + " – " + formatServiceTime(record.optString("end")) else "6:00AM – 11:00AM")
            .putString("serviceVenue",record?.optString("venue") ?: "Main Sanctuary").apply()
        navigate("service")
    }
    private fun announcementHomeTile(): View = memberTile(UI.announcementsButton, "announcement", "Announcements", 11).apply {
        val count = try { org.json.JSONArray(preferences.getString("leaderPosts", "[]")).length() } catch (_: Exception) { 0 }
        if (count > 0) { space(3); add(text("$count local post${if(count == 1) "" else "s"}", 10, purple, centered = true)) }
        setOnClickListener { navigate("announcements") }
    }
    private fun homeView(): View {
        val root=vertical().apply { setBackgroundColor(Color.WHITE) }
        val scroll=ScrollView(this).apply { isFillViewport=true }
        val content=vertical(); scroll.addView(content, FrameLayout.LayoutParams(-1,-2)); root.add(scroll,-1,0,1f)
        val hero=FrameLayout(this)
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
        // Use the earlier supplied artwork without its baked-in outer shadow.
        val card = image("hero_container").apply {
            val artwork = (drawable as android.graphics.drawable.BitmapDrawable).bitmap
            val scaleX = artwork.width.toFloat() / 360f
            val scaleY = artwork.height.toFloat() / 247f
            setImageBitmap(bitmaps.getOrPut("hero_card_clean") {
                Bitmap.createBitmap(artwork, (24 * scaleX).toInt(), (16 * scaleY).toInt(), (312 * scaleX).toInt(), (200 * scaleY).toInt())
            })
            id = UI.worshipCard
            scaleType = ImageView.ScaleType.FIT_CENTER
            (drawable as? android.graphics.drawable.BitmapDrawable)?.apply {
                isFilterBitmap = true
                paint.isAntiAlias = true
            }
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
            contentDescription = "Next Worship Session. Sunday, May 18, 2025, 9:00 AM. Main Sanctuary. Open details."
        }
        hero.clipChildren = false
        hero.clipToPadding = false
        hero.addView(card, FrameLayout.LayoutParams(-1, dp(200)).apply {
            leftMargin = dp(24)
            rightMargin = dp(24)
            topMargin = dp(132)
        })
        hero.addOnLayoutChangeListener { view, left, _, right, _, _, _, _, _ ->
            val cardHeight = ((right - left - dp(48)).coerceAtLeast(1) * 200f / 312f).toInt()
            if (card.layoutParams.height != cardHeight) {
                card.layoutParams = card.layoutParams.apply { height = cardHeight }
            }
            val cardTop = dp(132)
            val desiredHeight = cardTop + cardHeight + dp(16)
            if (view.layoutParams.height != desiredHeight) {
                view.layoutParams = view.layoutParams.apply { height = desiredHeight }
            }
        }
        content.add(hero, -1, 348)
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
                titleRow.add(image("team_badge","Worship Team badge"),50,50)
                titleRow.add(text("Worship\nTeam",18,Color.rgb(66,82,114),id=UI.teamTitle).apply {
                    setPadding(dp(16),0,0,0)
                },0,-2,1f)
                add(titleRow);space(8)
                val memberCount = getActiveRoster().size
                add(text("$memberCount Member${if (memberCount == 1) "" else "s"}",11,Color.rgb(84,91,116),true))
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
            add(text("Upcoming",17,navy,true));space(12)
            val upcoming=horizontal().apply { gravity=Gravity.TOP }
            val service=vertical().apply {
                id=UI.scheduleCard;background=bg(Color.WHITE,24,Color.rgb(211,211,211))
                elevation=dp(4).toFloat();setPadding(dp(10),dp(16),dp(10),dp(10))
                isClickable=true;isFocusable=true;minimumHeight=dp(104)
                val record = homeUpcomingRecord()
                val dateLabel = record?.optString("date") ?: "18 MAY • Sample"
                val serviceTitle = record?.optString("title") ?: "Sunday Worship Service"
                val timeLabel = if(record != null) formatServiceTime(record.optString("start")) + " – " + formatServiceTime(record.optString("end")) else "6:00AM – 11:00AM"
                contentDescription = "$dateLabel, $serviceTitle, $timeLabel. Open service details."
                add(text(dateLabel,13,navy,true));space(8)
                add(text(serviceTitle,13,Color.rgb(66,82,114),true));space(5)
                add(text(timeLabel,11,Color.rgb(66,82,114)))
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
    private fun settingsView(): View {
        val root=vertical().apply {setBackgroundColor(Color.WHITE)}
        root.add(settingsHeader("Settings",false))
        val scroll=ScrollView(this).apply {isFillViewport=true; clipToPadding=true}
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
            team.add(Symbol(this@MemberActivity,"teams",Color.rgb(90,188,248)),36,28)
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
        click(UI.settingsTeam) {navigate("teams")}
        findViewById<Switch>(UI.notificationSwitch).apply {
            isChecked=preferences.getBoolean("notifications",true)
            setOnCheckedChangeListener { _, checked ->
                preferences.edit().putBoolean("notifications",checked).apply()
                toast(if(checked) "Notifications enabled locally" else "Notifications disabled locally")
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
        click(UI.aboutButton) {detail("About Synced N", "Church Ministry Management\nFront-end demonstration. Accounts, teams and notifications are not connected to a server.")}
        click(UI.helpButton) {detail("Help", "Tap Profile to edit your details. Save Profile Changes stores them on this phone. Home, Schedule and Teams are available from the bottom bar.")}
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
        val scroll=ScrollView(this).apply {isFillViewport=true; clipToPadding=true}
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
            add(profileField(UI.profileEmail,"Email",preferences.getString(SessionManager.KEY_EMAIL,"").orEmpty(),kind=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,maxLength=ValidationUtils.MAX_EMAIL_LENGTH));space(16)
            add(text("Preference",15,Color.BLACK,true));space(12)
            add(preferenceRow(UI.countrySpinner,"Country",listOf("Philippines","Singapore","United States"),preferences.getString(SessionManager.KEY_COUNTRY,"Philippines").orEmpty()));space(8)
            add(preferenceRow(UI.languageSpinner,"Language",listOf("English","Filipino"),preferences.getString(SessionManager.KEY_LANGUAGE,"English").orEmpty()));space(48)
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
                    val countryStr = findViewById<Spinner>(UI.countrySpinner).selectedItem.toString()
                    val langStr = findViewById<Spinner>(UI.languageSpinner).selectedItem.toString()
                    val uid = preferences.getString(SessionManager.KEY_USER_ID, "").orEmpty()
                    val activeCode = preferences.getString(SessionManager.KEY_TEAM_CODE, "").orEmpty()
                    val activeRole = preferences.getString(SessionManager.KEY_ROLE, "Member").orEmpty()
                    val activeMinistry = preferences.getString(SessionManager.KEY_MINISTRY_ROLE, "").orEmpty()
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
                        ministryRole = activeMinistry
                    )
                    SessionManager.saveSessionProfile(preferences, profileObj, displayName, emailStr)
                    TeamRepository.updateUserProfile(profileObj)
                    toast("Profile saved successfully");navigate(profileReturnPage)
                }
            }
        }
    }
    private fun applyProfilePhoto(id: Int) {
        val uri=preferences.getString("photoUri",null) ?: return
        try {findViewById<ImageView>(id).setImageURI(Uri.parse(uri))} catch(_: Exception) {preferences.edit().remove("photoUri").apply()}
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int,resultCode: Int,data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (requestCode == 301 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                chatMessages.add("photo:$uri"); teamTab = "Team Chat"; navigate("teams")
            } catch (_: Exception) { toast("Could not use that photo. Try another image.") }
            return
        }
        if(requestCode==300 && resultCode==RESULT_OK) {
            val uri=data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
                preferences.edit().putString("photoUri",uri.toString()).apply()
                if(page=="profile") applyProfilePhoto(UI.profileAvatar)
            } catch(_: Exception) {toast("Could not use that photo. Try another image.")}
        }
    }
    // ---- MEMBER PAGES: shared 20dp margins, flexible rows, scrollable content. ----
    private var scheduleFilter = "Upcoming"
    private var teamTab = "Team Chat"
    private var expandedRole: String? = null
    private var selectedSong = "Trust in God"
    private var selectedService = "Sunday Worship Service"
    private var serviceReturnPage = "schedule"
    private var calendarYear = 2026
    private var calendarMonth = 9 // Calendar months start at zero.
    private var calendarDay = 4
    private var substitute = "John Doe"
    private var songsReturnPage = "home"
    private val chatMessages = mutableListOf<String>()
    private val lineupSongs = mutableListOf("Amazing Grace", "Way Maker", "Goodness of God", "Build My Life")
    private var teamRoster: List<TeamMember> = emptyList()

    private fun getActiveRoster(): List<TeamMember> {
        val currentCode = preferences.getString("teamCode", "").orEmpty()
            .ifBlank { FirestoreManager.findCodeByTeamName(teamName).orEmpty() }
        val memberRolesMap = try {
            org.json.JSONObject(preferences.getString(SessionManager.KEY_MEMBER_ROLES, "{}").orEmpty())
        } catch (_: Exception) { org.json.JSONObject() }

        fun applyOverrides(list: List<TeamMember>): List<TeamMember> {
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
                if (override.isNotBlank()) m.copy(ministryRole = override) else m
            }
        }

        if (currentCode.isNotBlank()) {
            TeamRepository.syncFromPreferences(preferences, currentCode)
        }
        val cached = TeamRepository.getCachedRoster(currentCode)
        if (cached.isNotEmpty()) return applyOverrides(cached)
        if (teamRoster.isNotEmpty()) return applyOverrides(teamRoster)
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
                teamCode = currentCode
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

    private fun refreshTeamRoster() {
        val currentCode = preferences.getString("teamCode", "").orEmpty()
            .ifBlank { FirestoreManager.findCodeByTeamName(teamName).orEmpty() }
        if (currentCode.isBlank()) return
        TeamRepository.syncFromPreferences(preferences, currentCode)
        TeamRepository.getTeamRoster(currentCode) { result ->
            result.getOrNull()?.let { list ->
                if (list.isNotEmpty()) {
                    teamRoster = list
                    runOnUiThread {
                        if (page == "teams" || page == "home") {
                            navigate(page)
                        }
                    }
                }
            }
        }
    }

    private val ministryMembers = listOf(
        Triple("Joshua Lagare", "Worship Leader", "Admin"),
        Triple("Ryan Lloyd Genturo", "Keyboardist", "Member"),
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
    private fun panel(stroke: Int? = Color.rgb(215, 215, 220)) = vertical().apply {
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = bg(Color.WHITE, 16, stroke)
        elevation = dp(3).toFloat()
    }
    private fun memberPage(title: String, back: String, tab: String? = null,
                           body: (LinearLayout) -> Unit): View {
        val root = vertical().apply { setBackgroundColor(Color.WHITE) }
        val header = settingsHeader(title, true)
        header.findViewById<View>(UI.pageBack).setOnClickListener { navigate(back) }
        root.add(header)
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = true }
        val col = vertical().apply { setPadding(dp(20), dp(24), dp(20), dp(24)) }
        scroll.addView(col, FrameLayout.LayoutParams(-1, -2))
        body(col)
        root.add(scroll, -1, 0, 1f)
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
            labels.forEach { label ->
                add(text(label, 12, if (label == active) Color.WHITE else navy,
                    bold = true, centered = true).apply {
                    background = bg(if (label == active) navy else Color.WHITE, 24)
                    setPadding(dp(3), dp(8), dp(3), dp(8))
                    isClickable = true; isFocusable = true
                    setOnClickListener { onSelect(label) }
                }, 0, 48, 1f)
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
    private fun memberRow(name: String, role: String, badge: String? = null,
                          onClick: (() -> Unit)? = null): View = horizontal().apply {
        setPadding(0, dp(8), 0, dp(8))
        add(memberAvatar(), 48, 48)
        val info = vertical().apply {
            setPadding(dp(12), 0, dp(8), 0)
            add(text(name, 13, navy, true)); space(3); add(text(role, 12, Color.DKGRAY))
        }
        add(info, 0, -2, 1f)
        if (badge != null) add(text(badge, 11, navy, true, true).apply {
            if (badge == "Admin") background = bg(Color.rgb(225, 225, 228), 14)
        }, 64, 30)
        onClick?.let { action ->
            isClickable = true; isFocusable = true
            setOnClickListener { action() }
        }
    }
    private fun scheduleView(): View {
        val root = vertical().apply { setBackgroundColor(Color.WHITE) }
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
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = true }
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
                    selectedService = title; serviceReturnPage = "schedule"
                    preferences.edit().putString("serviceTime", time).putString("serviceDate", date.replace("\n", " ")).putString("serviceVenue", if(title == "Team Meeting") "Fellowship Hall" else "Main Sanctuary").apply()
                    navigate("service")
                }
            }
            col.add(row); col.space(16)
        }
        root.add(scroll, -1, 0, 1f)
        root.add(memberNavigation("Schedule"), -1, 64)
        return root
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
                add(text(day, 11, if (i == 0 || i == 6) Color.MAGENTA else navy, centered = true), 0, 36, 1f)
            }
        })
        val first = date.get(java.util.Calendar.DAY_OF_WEEK) - 1
        val count = date.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        val rows = (first + count + 6) / 7
        val uid = preferences.getString(SessionManager.KEY_USER_ID, "").orEmpty()
            .ifBlank { AuthManager.currentUser?.uid.orEmpty() }
        val activeRanges = TeamRepository.getMemberUnavailableRanges(uid, preferences)

        repeat(rows) { week ->
            col.add(horizontal().apply {
                repeat(7) { weekday ->
                    val day = week * 7 + weekday - first + 1
                    val cell = vertical().apply { gravity = Gravity.CENTER }
                    if (day in 1..count) {
                        val key = calendarKey(day)
                        cell.add(text(day.toString(), 12,
                            if (day == calendarDay) Color.WHITE else navy, centered = true).apply {
                            if (day == calendarDay) background = bg(purple, 24)
                        }, -1, 36)
                        val marked = activeRanges.any { it.includes(key) }
                        cell.add(text(if (marked || day in listOf(4, 8, 9, 10)) "•" else "", 12,
                            if (marked) Color.RED else Color.rgb(0, 175, 125), centered = true), -1, 12)
                        cell.isClickable = true; cell.isFocusable = true; cell.contentDescription = key
                        cell.setOnClickListener { calendarDay = day; navigate("calendar") }
                    }
                    add(cell, 0, 56, 1f)
                }
            })
        }
        col.space(20)
        date.set(java.util.Calendar.DAY_OF_MONTH, calendarDay)
        col.add(text(java.text.SimpleDateFormat("EEEE, MMMM d", java.util.Locale.ENGLISH).format(date.time), 12, navy))
        col.space(12)
        val selectedKey = calendarKey()
        val matchingRange = activeRanges.find { it.includes(selectedKey) }
        val unavailable = matchingRange != null
        col.add(panel(if (unavailable) Color.RED else Color.rgb(0, 190, 70)).apply {
            add(text(if (unavailable) "Unavailable" else "Service", 14, navy, true)); space(8)
            add(text(if (unavailable) "${matchingRange?.startDate} – ${matchingRange?.endDate}" else "8:00AM – 11:00AM • Main Sanctuary", 12, Color.DKGRAY))
            if (unavailable) { space(6); add(text(matchingRange?.reason.orEmpty(), 12, Color.GRAY)) }
        })
        col.space(16)
        col.add(memberButton("Manage Availability") { navigate("availability") }, -1, 48)
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
    private fun availabilityView(): View = memberPage("Manage Schedule", "calendar") { col ->
        val uid = preferences.getString(SessionManager.KEY_USER_ID, "").orEmpty()
            .ifBlank { AuthManager.currentUser?.uid.orEmpty() }
        col.add(text("Set your unavailable dates", 18, navy, true)); col.space(8)
        col.add(text("Your unavailable dates sync with your team calendar.", 12, Color.GRAY)); col.space(20)

        val reason = plainInput("Reason, e.g. family bonding", true).apply {
            filters = arrayOf(InputFilter.LengthFilter(ValidationUtils.MAX_REASON_LENGTH))
        }
        col.add(text("Reason", 12, navy, true)); col.space(8); col.add(reason, -1, 80); col.space(16)

        val start = dateField(calendarKey())
        val end = dateField(calendarKey())
        col.add(text("Starts", 12, navy, true)); col.space(8); col.add(start, -1, 48); col.space(16)
        col.add(text("Ends", 12, navy, true)); col.space(8); col.add(end, -1, 48); col.space(24)

        col.add(memberButton("+ Add Unavailable Dates", navy) {
            val rText = reason.text.toString().trim()
            val sText = start.text.toString().trim()
            val eText = end.text.toString().trim()
            val currentRanges = TeamRepository.getMemberUnavailableRanges(uid, preferences)
            when {
                rText.isBlank() -> invalid(reason, "Enter a reason.")
                rText.length > ValidationUtils.MAX_REASON_LENGTH -> invalid(reason, "Reason cannot exceed ${ValidationUtils.MAX_REASON_LENGTH} characters.")
                eText < sText -> toast("End date must be on or after the start date.")
                currentRanges.size >= 100 -> toast("Maximum 100 unavailable ranges reached.")
                else -> {
                    val range = UnavailableRange(
                        id = "range_${System.currentTimeMillis()}",
                        startDate = sText,
                        endDate = eText,
                        reason = rText
                    )
                    TeamRepository.addUnavailableRange(uid, range, preferences)
                    toast("Unavailable range added")
                    navigate("availability")
                }
            }
        }, -1, 48)

        col.space(28)
        col.add(text("Your Scheduled Unavailabilities", 15, navy, true)); col.space(12)
        val currentRanges = TeamRepository.getMemberUnavailableRanges(uid, preferences)
        if (currentRanges.isEmpty()) {
            col.add(text("No unavailable dates set. You are available for services.", 13, Color.GRAY))
        } else {
            currentRanges.forEach { r ->
                col.add(panel().apply {
                    val row = horizontal()
                    row.add(vertical().apply {
                        add(text("📅 ${r.startDate} – ${r.endDate}", 14, navy, true))
                        space(4)
                        add(text("Reason: ${r.reason}", 12, Color.DKGRAY))
                    }, 0, -2, 1f)
                    val delBtn = text("✕", 18, Color.RED, bold = true, centered = true).apply {
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
                })
                col.space(10)
            }
        }
    }
    private fun serviceView(): View = memberPage("Service", serviceReturnPage, "Schedule") { col ->
        col.add(panel(null).apply {
            background = bg(Color.rgb(192, 221, 245), 18)
            add(text(selectedService, 16, navy, true)); space(8)
            add(text(preferences.getString("serviceDate", "SEPT 6").orEmpty(), 12, navy)); space(4)
            add(text(preferences.getString("serviceTime", "6:00AM – 11:00AM").orEmpty(), 13, navy))
            space(4); add(text(preferences.getString("serviceVenue", if (selectedService == "Team Meeting") "Fellowship Hall" else "Main Sanctuary").orEmpty(), 13, navy))
        }); col.space(16)
        col.add(horizontal().apply {
            add(memberButton("Songs", navy) { songsReturnPage = "service"; navigate("songs") }, 0, 48, 1f)
            add(View(this@MemberActivity), 16, 1)
            add(memberButton("Song Lineups") { navigate("lineup") }, 0, 48, 1f)
        }); col.space(24)
        col.add(text("Assigned Members and their Roles", 13, navy)); col.space(12)
        ministryMembers.take(4).forEach { (name, role, badge) -> col.add(memberRow(name, role, badge)) }
        col.space(28)
        col.add(memberButton("Request Substitution") { navigate("substitution") }, -1, 48)
    }
    private fun substitutionView(): View = memberPage("Request Substitution", "service") { col ->
        col.add(text("Current Assignment", 12, purple, true)); col.space(8)
        col.add(panel().apply {
            add(text("Keyboardist", 15, navy, true)); space(5)
            add(text("$selectedService\nMain Sanctuary", 12, navy))
        }); col.space(20)
        col.add(text("Reason", 12, purple, true)); col.space(8)
        val reason = plainInput("e.g. School examination", true)
        col.add(reason, -1, 80); col.space(20)
        col.add(text("Select Substitute", 12, purple, true)); col.space(8)
        val selection = text("Selected: $substitute", 13, navy, true)
        listOf("Kid Gamboa", "John Doe").forEach { name ->
            col.add(panel().apply {
                add(memberRow(name, "Keyboardist • Available", onClick = {
                    substitute = name; selection.text = "Selected: $name"
                }))
            }); col.space(12)
        }
        col.add(selection); col.space(24)
        col.add(memberButton("Submit Request") {
            if (reason.text.isBlank()) invalid(reason, "Enter a reason.")
            else {
                preferences.edit().putString("requestStatus", "Waiting for substitute")
                    .putString("requestReason", reason.text.toString().trim())
                    .putString("requestSubstitute", substitute)
                    .putString("requestServiceName", selectedService)
                    .putString("requestRequester", fullName).putString("requestRole", "Keyboardist").apply()
                toast("Demo request saved locally"); navigate("announcements")
            }
        }, -1, 48)
    }
    private fun leaderPosts(col: LinearLayout) {
        val posts = org.json.JSONArray(preferences.getString("leaderPosts", "[]"))
        for (index in posts.length() - 1 downTo 0) {
            val post = posts.getJSONObject(index)
            col.add(panel().apply {
                add(text(post.optString("title") + if (post.optBoolean("urgent")) " • Urgent" else "", 14, purple, true))
                space(8); add(text(post.optString("message"), 13, navy))
                space(8); add(text("By " + post.optString("author"), 11, Color.GRAY))
            }); col.space(16)
        }
    }
    private fun announcementsView(): View = memberPage("Announcement", "home") { col ->
        leaderPosts(col)
        col.add(text("Substitution Request", 13, purple, true)); col.space(12)
        col.add(panel().apply {
            add(text(preferences.getString("requestServiceName", "Sunday Worship Service").orEmpty(), 14, purple, true)); space(10)
            val status = text(preferences.getString("requestStatus", "Waiting for substitute").orEmpty(), 11, purple, true)
            add(status); space(10)
            add(text("Requesting: " + preferences.getString("requestRequester", "Angelo Daniel") +
                    " (" + preferences.getString("requestRole", "Guitarist") + ")", 12, navy)); space(5)
            add(text("Proposed Substitute: " + preferences.getString("requestSubstitute", "Ryan Lloyd Genturo"), 12, navy, true))
            space(5); add(text("Reason: " + preferences.getString("requestReason", "Family emergency out of town"), 12, navy))
            space(16)
            val actions = horizontal()
            listOf("Accept", "Decline").forEach { label ->
                actions.add(memberButton(label, if (label == "Accept") purple else navy) {
                    AlertDialog.Builder(this@MemberActivity).setTitle("$label substitution?")
                        .setMessage("This updates the demo request on this phone.")
                        .setNegativeButton("Cancel", null).setPositiveButton(label) { _, _ ->
                            val value = if (label == "Accept") "Accepted" else "Declined"
                            preferences.edit().putString("requestStatus", value).apply()
                            status.text = value
                        }.show()
                }, 0, 48, 1f)
                if (label == "Accept") actions.add(View(this@MemberActivity), 12, 1)
            }
            add(actions)
        }); col.space(24)
        col.add(text("Recent Notifications", 13, purple, true)); col.space(12)
        listOf("New Service Scheduled" to "Sunday Worship Service scheduled for Oct 18, 2026 at 8:00AM.",
            "Duty Assigned" to "You are assigned as Keyboardist for Sunday Worship Service on Oct 11, 2026.",
            "Rehearsal Venue Shift" to "Saturday rehearsal starts at 7:00PM in the main Sanctuary. Please prepare songs and chords in advance.",
            "October Rotation Published" to "The team assignments for October have been finalized. Check your respective schedules.").forEach { (title, message) ->
            col.add(panel().apply {
                add(text(title, 13, purple, true)); space(8); add(text(message, 12, navy))
                space(8); add(text("Sample ministry notification", 10, Color.GRAY))
            }); col.space(16)
        }
    }
    private fun songCover(title: String): View = image("song_covers_source", "$title cover").apply {
        val source = (drawable as android.graphics.drawable.BitmapDrawable).bitmap
        val coverY = when (title) { "Praise" -> 210; "Trust in God" -> 320; "Way Maker" -> 430; else -> 540 }
        setImageBitmap(bitmaps.getOrPut("song_cover_$title") {
            Bitmap.createBitmap(source, 29, coverY, 120, 66)
        })
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = bg(navy, 6); outlineProvider = ViewOutlineProvider.BACKGROUND; clipToOutline = true
        (drawable as? android.graphics.drawable.BitmapDrawable)?.setFilterBitmap(true)
    }
    private fun songsView(): View = memberPage("Songs", songsReturnPage) { col ->
        val search = plainInput("Search by title or artist")
        col.add(search, -1, 48); col.space(20)
        val results = vertical(); col.add(results)
        val songs = listOf("Praise" to "Elevation Worship", "Trust in God" to "Elevation Worship",
            "Way Maker" to "Sinach", "Gratitude" to "Brandon Lake")
        fun populate(query: String) {
            results.removeAllViews()
            val matches = songs.filter { (title, artist) -> "$title $artist".contains(query, true) }
            if (matches.isEmpty()) results.add(text("No songs found.", 14, Color.GRAY))
            matches.forEach { (title, artist) ->
                results.add(panel().apply {
                    val row = horizontal()
                    row.add(songCover(title), 104, 60)
                    row.add(vertical().apply {
                        setPadding(dp(14), 0, dp(8), 0)
                        add(text(title, 14, navy, true)); space(5); add(text(artist, 11, Color.GRAY))
                    }, 0, -2, 1f)
                    row.add(Symbol(this@MemberActivity, "arrow", Color.GRAY), 18, 24)
                    add(row); isClickable = true; isFocusable = true
                    setOnClickListener { selectedSong = title; navigate("songDetails") }
                }); results.space(16)
            }
        }
        populate("")
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { populate(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }
    private fun songDetailsView(): View = memberPage("Song Details", "songs") { col ->
        col.add(panel().apply {
            add(text(selectedSong, 18, navy, true)); space(6)
            add(text(if (selectedSong == "Way Maker") "By Sinach" else if (selectedSong == "Gratitude") "By Brandon Lake" else "By Elevation Worship", 13, Color.GRAY))
            space(16)
            val key = text("Key: C    •    74 BPM", 13, purple, true); add(key); space(16)
            add(text("Transpose preview", 12, Color.GRAY)); space(8)
            add(horizontal().apply {
                listOf("C", "Db", "D", "Eb", "E", "F").forEach { chord ->
                    add(text(chord, 12, navy, true, true).apply {
                        background = bg(Color.rgb(224, 215, 230), 6)
                        setOnClickListener { key.text = "Key: $chord    •    74 BPM"; toast("Key preview only; add a chord sheet to transpose music.") }
                        isFocusable = true
                    }, 0, 44, 1f)
                }
            })
        }); col.space(24)
        col.add(text("Lyrics & Chords Sheet", 13, navy, true)); col.space(10)
        col.add(panel().apply {
            add(text("Add your licensed lyrics and chords here.\n\n[Verse]\nC      F      G\n\n[Chorus]\nAm     F      C      G\n\nSample chord sheet for UI preview.", 13, navy))
        }); col.space(24)
        col.add(text("Reference Link", 13, navy, true)); col.space(10)
        col.add(memberButton("Find song on YouTube", navy) {
            val url = "https://www.youtube.com/results?search_query=" + Uri.encode("$selectedSong worship")
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            catch (_: Exception) { toast("No browser available.") }
        }, -1, 48)
    }
    private fun lineupView(): View = memberPage("Song Lineup", "service") { col ->
        col.add(text("Sunday Worship Service", 13, navy, true)); col.space(20)
        lineupSongs.forEachIndexed { index, title ->
            col.add(panel().apply {
                val row = horizontal()
                row.add(text((index + 1).toString() + ".", 14, navy, true), 28, 48)
                row.add(text(title, 14, navy, true), 0, -2, 1f)
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
                }, 48, 48); add(row)
            }); col.space(12)
        }
    }
    private fun teamHeader(showDetails: Boolean = false): View = FrameLayout(this).apply {
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(8, 7, 32), navy))
        val photograph = image("team_sunset", "Sunset and cross").apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        addView(photograph, FrameLayout.LayoutParams(dp(if (showDetails) 237 else 130), -1, Gravity.END))
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
        addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            val imageWidth = ((right - left) * (if (showDetails) 237f else 130f) / 360f).toInt()
            if (photograph.layoutParams.width != imageWidth)
                photograph.layoutParams = photograph.layoutParams.apply { width = imageWidth }
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
    private fun teamsView(): View {
        if (preferences.getBoolean("leftTeam", false)) return memberPage("Teams", "home", "Teams") { col ->
            col.add(text("You have no active team.", 16, navy, true)); col.space(20)
            col.add(memberButton("Create or Join Team") { navigate("teamChoice") }, -1, 48)
        }
        val root = vertical().apply { setBackgroundColor(Color.WHITE) }
        root.add(teamHeader(showDetails = true), -1, 156)
        root.add(teamTabs())
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = true }
        val col = vertical().apply { setPadding(dp(20), dp(16), dp(20), dp(20)) }
        scroll.addView(col, FrameLayout.LayoutParams(-1, -2)); root.add(scroll, -1, 0, 1f)
        when (teamTab) {
            "Members" -> {
                val roster = getActiveRoster()
                roster.forEach { member ->
                    val name = member.fullName.ifBlank { member.username.ifBlank { "Member" } }
                    val role = member.ministryRole.ifBlank { if (member.role.equals("Leader", true)) "Worship Leader" else "Member" }
                    val badge = if (member.role.equals("Leader", true)) "Admin" else "Member"
                    col.add(memberRow(name, role, badge))
                    col.space(4)
                }
            }
            "Role" -> {
                col.setPadding(0, dp(24), 0, 0)
                val roster = getActiveRoster()
                val baseRoles = listOf("Worship Leader", "Keyboardist", "Bassist", "Vocalist", "Guitarist")
                val activeRoles = (baseRoles + roster.map { it.ministryRole }).filter { it.isNotBlank() }.distinct()
                val roles = activeRoles
                roles.forEach { role ->
                    val people = roster.filter { it.ministryRole.equals(role, ignoreCase = true) }
                    col.add(horizontal().apply {
                        setPadding(dp(40), 0, dp(20), 0)
                        add(Symbol(this@MemberActivity, "music", Color.BLACK), 56, 52)
                        add(vertical().apply {
                            setPadding(dp(20), 0, dp(8), 0)
                            add(text(role, 14, navy, true)); space(4)
                            add(text("${people.size} member${if (people.size == 1) "" else "s"}", 12, Color.BLACK))
                        }, 0, -2, 1f)
                        add(Symbol(this@MemberActivity, "arrow", Color.rgb(65, 80, 100)).apply {
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
                                        add(memberAvatar(), 44, 44)
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
                chatMessages.forEach { message ->
                    col.add(panel().apply {
                        add(text(displayName, 11, purple, true)); space(5)
                        if (message.startsWith("photo:")) {
                            add(ImageView(this@MemberActivity).apply {
                                contentDescription = "Attached photo"; scaleType = ImageView.ScaleType.FIT_CENTER
                                try { setImageURI(Uri.parse(message.removePrefix("photo:"))) }
                                catch (_: Exception) { contentDescription = "Photo unavailable" }
                            }, -1, 180)
                        } else add(text(message, 14, navy))
                    })
                    col.space(12)
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
            composer.add(text("⊕", 30, Color.BLACK, centered = true).apply {
                contentDescription = "Add attachment"; isFocusable = true
                setOnClickListener { choosePhoto() }
            }, 36, 44)
            composer.add(FrameLayout(this).apply {
                addView(Symbol(this@MemberActivity, "photo", Color.BLACK),
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
                    if (message.text.isBlank()) { message.setText("🙂"); message.setSelection(message.text.length) }
                    else { chatMessages.add(message.text.toString().trim()); navigate("teams") }
                }
            }
            entry.add(send, 40, 44)
            message.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    send.text = if (s.isNullOrBlank()) "☺" else "➤"
                    send.contentDescription = if (s.isNullOrBlank()) "Insert emoji" else "Send demo message"
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
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = true }
        val col = vertical().apply { setPadding(dp(20), dp(32), dp(20), dp(24)) }
        scroll.addView(col, FrameLayout.LayoutParams(-1, -2)); root.add(scroll, -1, 0, 1f)
        col.add(text("Team Name", 13, Color.BLACK, true)); col.space(8)
        col.add(text(teamName.ifBlank { "Worship Team" }, 14, Color.BLACK, true).apply {
            background = bg(Color.rgb(217, 217, 217), 9)
            setPadding(dp(10), 0, dp(10), 0)
        }, -1, 32); col.space(32)
        val row = horizontal()
        row.add(Symbol(this, "bell", navy), 36, 32)
        row.add(text("Unmute Messages", 14, Color.BLACK, true).apply {
            setPadding(dp(14), 0, dp(8), 0)
        }, 0, 48, 1f)
        row.add(Switch(this).apply {
            contentDescription = "Unmute team messages"
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, Color.WHITE))
            trackTintList = ColorStateList(states, intArrayOf(Color.rgb(0, 124, 224), Color.rgb(248, 35, 63)))
            splitTrack = false
            isChecked = preferences.getBoolean("teamUnmuted", true)
            setOnCheckedChangeListener { _, checked -> preferences.edit().putBoolean("teamUnmuted", checked).apply() }
        }, 52, 48)
        col.add(row); col.space(4)
        col.add(teamDetailAction("warning", "Report") {
            val reason = plainInput("Describe the issue", true)
            AlertDialog.Builder(this).setTitle("Report Team").setView(reason)
                .setNegativeButton("Cancel", null).setPositiveButton("Save Demo Report", null).create().apply {
                    setOnShowListener {
                        getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                            if (reason.text.isBlank()) invalid(reason, "Enter a reason.")
                            else {
                                preferences.edit().putString("teamReport", reason.text.toString().trim()).apply()
                                toast("Demo report saved locally"); dismiss()
                            }
                        }
                    }
                    show()
                }
        }); col.space(4)
        col.add(teamDetailAction("logout", "Leave") {
            AlertDialog.Builder(this).setTitle("Leave Team?")
                .setMessage("Are you sure you want to leave this team?")
                .setNegativeButton("Cancel", null).setPositiveButton("Leave") { _, _ ->
                    val uid = preferences.getString("userId", "").orEmpty()
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
        add(Symbol(this@MemberActivity, icon, if (label == "Report") Color.BLACK else navy), 36, 32)
        add(text(label, 14, Color.BLACK, true).apply { setPadding(dp(14), 0, 0, 0) }, 0, 48, 1f)
        isFocusable = true; contentDescription = label; setOnClickListener { action() }
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

