package com.example.syncedn

import android.content.Intent

/** Common welcome/login entry. Uses the existing member welcome/login layouts. */
class MainActivity : MemberActivity() {
    override val initialPage: String = "start"

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val uid = preferences.getString("userId", "").orEmpty()
        val team = preferences.getString("teamName", "").orEmpty()
        if (savedInstanceState == null && uid.isNotBlank() && team.isNotBlank()) {
            navigate("home")
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
            if (userRole.equals("Leader", ignoreCase = true)) {
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
