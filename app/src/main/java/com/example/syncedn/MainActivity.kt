package com.example.syncedn

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Common welcome/login entry. Uses the existing member welcome/login layouts. */
class MainActivity : MemberActivity() {
    override val initialPage: String = "start"

    override fun navigate(next: String) {
        if (next != "home") {
            super.navigate(next)
            return
        }
        showRolePicker()
    }

    private fun showRolePicker() {
        val density = resources.displayMetrics.density
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#80000000"))
            isClickable = true
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * density).toInt()
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 20 * density
            }
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "Choose Demo Dashboard"
            textSize = 20f
            setTextColor(Color.rgb(16, 17, 63))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        card.addView(title, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = (12 * density).toInt()
        })

        val subtitle = TextView(this).apply {
            text = "Select a view to explore ministry features:"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
        }
        card.addView(subtitle, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = (20 * density).toInt()
        })

        fun createButton(label: String, color: Int, onClick: () -> Unit): Button {
            return Button(this).apply {
                text = label
                textSize = 15f
                setTextColor(Color.WHITE)
                isAllCaps = false
                stateListAnimator = null
                background = GradientDrawable().apply {
                    setColor(color)
                    cornerRadius = 24 * density
                }
                setOnClickListener { onClick() }
            }
        }

        val memberBtn = createButton("Member Dashboard", Color.rgb(80, 5, 104)) {
            startActivity(Intent(this, MemberActivity::class.java).putExtra("startPage", "home"))
            finish()
        }
        card.addView(memberBtn, LinearLayout.LayoutParams(-1, (48 * density).toInt()).apply {
            bottomMargin = (12 * density).toInt()
        })

        val adminBtn = createButton("Admin / Leader Dashboard", Color.rgb(0, 103, 96)) {
            startActivity(Intent(this, AdminActivity::class.java).putExtra("startPage", "home"))
            finish()
        }
        card.addView(adminBtn, LinearLayout.LayoutParams(-1, (48 * density).toInt()).apply {
            bottomMargin = (16 * density).toInt()
        })

        val cancelBtn = TextView(this).apply {
            text = "Cancel"
            textSize = 14f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            isClickable = true
            setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
            setOnClickListener {
                navigate("login")
            }
        }
        card.addView(cancelBtn, LinearLayout.LayoutParams(-2, -2))

        val cardParams = FrameLayout.LayoutParams((320 * density).toInt(), -2, Gravity.CENTER)
        root.addView(card, cardParams)

        setContentView(root)
    }
}
