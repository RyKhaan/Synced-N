package com.example.syncedn

import android.content.Intent

/** Common welcome/login entry. Uses the existing member welcome/login layouts. */
class MainActivity : MemberActivity() {
    override val initialPage: String = "start"

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)

        // Fresh installation detection: noBackupFilesDir is never backed up by Android OS and is deleted on uninstall.
        // On true first launch after install/reinstall, start on Splash -> Welcome/Login screen and clear residual tokens.
        val installMarker = java.io.File(noBackupFilesDir, "synced_install_v1.marker")
        if (!installMarker.exists()) {
            try {
                preferences.edit().clear().commit()
                com.example.syncedn.data.auth.AuthManager.signOut()
                installMarker.createNewFile()
            } catch (_: Throwable) {}
            navigate("start")
            return
        }

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
        val team = preferences.getString("teamName", "").orEmpty()
        val teamCode = preferences.getString("teamCode", "").orEmpty()
        val userRole = preferences.getString("role", "Member").orEmpty()
        val userMinistry = preferences.getString("ministryRole", "").orEmpty()
        val transferred = preferences.getBoolean("transferred_leadership_$teamCode", false)
        val designatedLeader = preferences.getString("team_leader_email_$teamCode", "").orEmpty().trim().lowercase()
        val isLeadUser = (designatedLeader.isNotBlank() && designatedLeader == userEmail) ||
                         (!transferred && designatedLeader.isBlank() && userEmail == "ryanlloydgenturo@gmail.com" && !userRole.equals("Member", ignoreCase = true) && !userMinistry.equals("Member", ignoreCase = true))
        val leftTeam = preferences.getBoolean("leftTeam", false)
        val removedTeamCode = preferences.getString("removedTeamCode", "").orEmpty()
        val removed = preferences.getBoolean("removedByLeader", false) && (removedTeamCode.isBlank() || teamCode.isBlank() || removedTeamCode.equals(teamCode, ignoreCase = true))

        if (savedInstanceState == null && effectiveUid.isNotBlank() && team.isNotBlank() && !leftTeam && !removed) {
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
            val userTeam = preferences.getString("teamName", "").orEmpty()
            val teamCode = preferences.getString("teamCode", "").orEmpty()
            val userRole = preferences.getString("role", "Member").orEmpty()
            val userMinistry = preferences.getString("ministryRole", "").orEmpty()
            val transferred = preferences.getBoolean("transferred_leadership_$teamCode", false)
            val designatedLeader = preferences.getString("team_leader_email_$teamCode", "").orEmpty().trim().lowercase()
            val isLeadUser = (designatedLeader.isNotBlank() && designatedLeader == userEmail) ||
                             (!transferred && designatedLeader.isBlank() && userEmail == "ryanlloydgenturo@gmail.com" && !userRole.equals("Member", ignoreCase = true) && !userMinistry.equals("Member", ignoreCase = true))
            val removedTeamCode = preferences.getString("removedTeamCode", "").orEmpty()
            val removed = preferences.getBoolean("removedByLeader", false) && (removedTeamCode.isBlank() || teamCode.isBlank() || removedTeamCode.equals(teamCode, ignoreCase = true))
            if (userTeam.isBlank() || removed) {
                super.navigate("teamChoice")
                return
            }
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
