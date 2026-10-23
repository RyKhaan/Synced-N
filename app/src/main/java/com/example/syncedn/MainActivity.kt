package com.example.syncedn

import android.content.Intent

/** Common welcome/login entry. Uses the existing member welcome/login layouts. */
class MainActivity : MemberActivity() {
    override val initialPage: String = "start"

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val requestedStart = intent.getStringExtra("startPage")
        if (!requestedStart.isNullOrBlank() && requestedStart in listOf("login", "start", "register")) {
            navigate(requestedStart)
            return
        }

        val effectiveUid = com.example.syncedn.data.auth.AuthManager.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: preferences.getString("userId", "").orEmpty().ifBlank {
                preferences.getString(com.example.syncedn.data.auth.SessionManager.KEY_USER_ID, "").orEmpty()
            }
        val userEmail = preferences.getString("email", "").orEmpty().trim().lowercase()
        val isLeadUser = userEmail == "ryanlloydgenturo@gmail.com"
        val team = preferences.getString("teamName", "").orEmpty()
        val teamCode = preferences.getString("teamCode", "").orEmpty()
        val leftTeam = if (isLeadUser) false else preferences.getBoolean("leftTeam", false)
        val removedTeamCode = preferences.getString("removedTeamCode", "").orEmpty()
        val removed = if (isLeadUser) false else (preferences.getBoolean("removedByLeader", false) && (removedTeamCode.isBlank() || teamCode.isBlank() || removedTeamCode.equals(teamCode, ignoreCase = true)))

        if (savedInstanceState == null && effectiveUid.isNotBlank() && team.isNotBlank() && !leftTeam && !removed) {
            val userRole = preferences.getString("role", "Member").orEmpty()
            val userMinistry = preferences.getString("ministryRole", "").orEmpty()
            if (userRole.equals("Leader", ignoreCase = true) || userMinistry.equals("Substitute Leader", ignoreCase = true) || isLeadUser) {
                startActivity(Intent(this, AdminActivity::class.java).putExtra("startPage", "home"))
                finish()
            } else {
                startActivity(Intent(this, MemberActivity::class.java).putExtra("startPage", "home"))
                finish()
            }
            return
        } else if (savedInstanceState == null && effectiveUid.isNotBlank() && (team.isBlank() || removed)) {
            navigate("teamChoice")
            return
        }
    }

    override fun navigate(next: String) {
        if (next == "home") {
            val userEmail = preferences.getString("email", "").orEmpty().trim().lowercase()
            val isLeadUser = userEmail == "ryanlloydgenturo@gmail.com"
            val userTeam = preferences.getString("teamName", "").orEmpty()
            val teamCode = preferences.getString("teamCode", "").orEmpty()
            val removedTeamCode = preferences.getString("removedTeamCode", "").orEmpty()
            val removed = if (isLeadUser) false else (preferences.getBoolean("removedByLeader", false) && (removedTeamCode.isBlank() || teamCode.isBlank() || removedTeamCode.equals(teamCode, ignoreCase = true)))
            if (userTeam.isBlank() || removed) {
                super.navigate("teamChoice")
                return
            }
            val userRole = preferences.getString("role", "Member").orEmpty()
            val userMinistry = preferences.getString("ministryRole", "").orEmpty()
            if (userRole.equals("Leader", ignoreCase = true) || userMinistry.equals("Substitute Leader", ignoreCase = true) || isLeadUser) {
                startActivity(Intent(this, AdminActivity::class.java).putExtra("startPage", "home"))
                finish()
            } else {
                startActivity(Intent(this, MemberActivity::class.java).putExtra("startPage", "home"))
                finish()
            }
            return
        }
        super.navigate(next)
    }
}
