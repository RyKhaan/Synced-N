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

        val uid = preferences.getString("userId", "").orEmpty()
        val team = preferences.getString("teamName", "").orEmpty()
        val leftTeam = preferences.getBoolean("leftTeam", false)
        val removed = preferences.getBoolean("removedByLeader", false)

        if (savedInstanceState == null && uid.isNotBlank() && team.isNotBlank() && !leftTeam && !removed) {
            val userRole = preferences.getString("role", "Member").orEmpty()
            val userMinistry = preferences.getString("ministryRole", "").orEmpty()
            val userEmail = preferences.getString("email", "").orEmpty()
            if (userRole.equals("Leader", ignoreCase = true) || userMinistry.equals("Substitute Leader", ignoreCase = true) || userEmail.equals("ryanlloydgenturo@gmail.com", ignoreCase = true)) {
                startActivity(Intent(this, AdminActivity::class.java).putExtra("startPage", "home"))
                finish()
            } else {
                startActivity(Intent(this, MemberActivity::class.java).putExtra("startPage", "home"))
                finish()
            }
            return
        } else if (savedInstanceState == null && uid.isNotBlank() && team.isBlank()) {
            navigate("teamChoice")
            return
        }
    }

    override fun navigate(next: String) {
        if (next == "home") {
            val userTeam = preferences.getString("teamName", "").orEmpty()
            if (userTeam.isBlank()) {
                super.navigate("teamChoice")
                return
            }
            val userRole = preferences.getString("role", "Member").orEmpty()
            val userMinistry = preferences.getString("ministryRole", "").orEmpty()
            val userEmail = preferences.getString("email", "").orEmpty()
            if (userRole.equals("Leader", ignoreCase = true) || userMinistry.equals("Substitute Leader", ignoreCase = true) || userEmail.equals("ryanlloydgenturo@gmail.com", ignoreCase = true)) {
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
