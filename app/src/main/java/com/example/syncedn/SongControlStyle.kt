package com.example.syncedn

import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable

/** Compact visual surfaces with full-size touch targets. */
internal object SongControlStyle {
    val purple = Color.rgb(80, 5, 104)
    val ink = Color.rgb(16, 17, 63)
    val font = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    fun background(resources: Resources, primary: Boolean = false): InsetDrawable {
        val density = resources.displayMetrics.density
        val shape = GradientDrawable().apply {
            setColor(if (primary) purple else Color.rgb(245, 245, 248))
            cornerRadius = 8 * density
        }
        val mask = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = 8 * density }
        val ripple = RippleDrawable(ColorStateList.valueOf(Color.argb(35, 150, 100, 170)), shape, mask)
        return InsetDrawable(ripple, (2 * density).toInt(), (6 * density).toInt(), (2 * density).toInt(), (6 * density).toInt())
    }
}
