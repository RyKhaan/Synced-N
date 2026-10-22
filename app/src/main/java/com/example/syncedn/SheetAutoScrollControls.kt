package com.example.syncedn

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/** Fixed song-page footer: controls stay reachable while lyrics or chords scroll. */
internal class SheetAutoScrollControls(
    context: Context,
    private val preferences: SharedPreferences,
    song: String,
    private var viewport: StableScrollView?,
    private val sheet: TextView
) : LinearLayout(context) {
    private val fontKey = "sheet_font_size_$song"
    private var fontSize = preferences.getInt(fontKey, 13).coerceIn(12, 18)
    private lateinit var smaller: Button
    private lateinit var larger: Button
    private val speedKey = "sheet_scroll_speed_$song"
    private var speed = SheetScrollClock.clampSpeed(preferences.getInt(speedKey, 3))
    private val clock = SheetScrollClock()
    private var running = false
    private var closed = false
    private var lastFrame = 0L
    private val sheetLocation = IntArray(2)
    private val scrollLocation = IntArray(2)
    private val navy = Color.rgb(16,17,63)
    private val fontLabel = label("${fontSize}sp", 12)
    private val status = label("Ready", 11)
    private val speedLabel = label("Speed $speed", 12)
    private val toggle = Button(context).apply {
        text = "Start scroll"; textSize = 12f; isAllCaps = false
        setSingleLine(true); ellipsize = android.text.TextUtils.TruncateAt.END
        setTextColor(Color.WHITE)
        background = SongControlStyle.background(resources, true)
        typeface = SongControlStyle.font
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(0,0,0,0); stateListAnimator = null
        setOnClickListener { if (running) pause() else start() }
    }
    private val manualScroll: () -> Unit = { pause() }
    private val frame = object : Runnable {
        override fun run() {
            val scroll = viewport
            if (!running || closed || !isAttachedToWindow || scroll == null) return
            val now = SystemClock.uptimeMillis()
            val maximum = maximumOffset(scroll)
            val nextY = clock.advance(now - lastFrame, speed, resources.displayMetrics.density, maximum)
            scroll.scrollTo(0, nextY)
            lastFrame = now
            if (nextY >= maximum) pause("End of sheet")
            else postOnAnimation(this)
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.WHITE)
        setPadding(dp(16),dp(4),dp(16),dp(4))
        elevation = dp(1).toFloat()
        sheet.textSize = fontSize.toFloat()
        preferences.edit().putInt(fontKey, fontSize).apply()
        clipChildren = true; clipToPadding = true
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(toggle, LayoutParams(dp(88),dp(48)))
            addView(speedLabel, LayoutParams(dp(64),dp(48)))
            addView(SeekBar(context).apply {
                max = 9; progress = speed - 1
                contentDescription = "Auto-scroll speed, 1 slow to 10 fast"
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                        speed = value + 1
                        speedLabel.text = "Speed $speed"
                        if (running) status.text = "Scrolling (Speed $speed)"
                    }
                    override fun onStartTrackingTouch(bar: SeekBar?) {}
                    override fun onStopTrackingTouch(bar: SeekBar?) {
                        preferences.edit().putInt(speedKey, speed).apply()
                    }
                })
            }, LayoutParams(0,dp(48),1f))
        }, LayoutParams(-1,-2))
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(label("Font size", 12).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }, LayoutParams(0,dp(48),1f))
            smaller = fontButton("A−", "Decrease lyrics and chords font size") { changeFont(-1) }
            larger = fontButton("A+", "Increase lyrics and chords font size") { changeFont(1) }
            addView(smaller, LayoutParams(dp(48),dp(48)))
            addView(fontLabel, LayoutParams(dp(56),dp(48)))
            addView(larger, LayoutParams(dp(48),dp(48)))
        }, LayoutParams(-1,-2))
        addView(status.apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), dp(4))
        }, LayoutParams(-1,-2))
        updateFontButtons()
        viewport?.onManualScroll = manualScroll
    }

    private fun fontButton(title: String, description: String, action: () -> Unit) = Button(context).apply {
        text = title; textSize = 13f; isAllCaps = false
        setSingleLine(true); ellipsize = android.text.TextUtils.TruncateAt.END
        contentDescription = description; typeface = SongControlStyle.font
        setTextColor(navy); background = SongControlStyle.background(resources)
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(0,0,0,0); stateListAnimator = null
        setOnClickListener { action() }
    }

    private fun changeFont(delta: Int) {
        pause()
        fontSize = (fontSize + delta).coerceIn(12, 18)
        sheet.textSize = fontSize.toFloat()
        fontLabel.text = "${fontSize}sp"
        preferences.edit().putInt(fontKey, fontSize).apply()
        updateFontButtons()
    }

    private fun updateFontButtons() {
        smaller.isEnabled = fontSize > 12; larger.isEnabled = fontSize < 18
        smaller.alpha = if (smaller.isEnabled) 1f else 0.35f
        larger.alpha = if (larger.isEnabled) 1f else 0.35f
        fontLabel.contentDescription = "Lyrics and chords font size $fontSize sp"
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun label(value: String, size: Int) = TextView(context).apply {
        text = value; textSize = size.toFloat(); setTextColor(navy)
        includeFontPadding = true; gravity = Gravity.CENTER
        setSingleLine(true); ellipsize = android.text.TextUtils.TruncateAt.END
    }

    private fun sheetTop(scroll: StableScrollView): Int {
        sheet.getLocationInWindow(sheetLocation)
        scroll.getLocationInWindow(scrollLocation)
        return (sheetLocation[1] - scrollLocation[1] + scroll.scrollY).coerceAtLeast(0)
    }

    private fun maximumOffset(scroll: StableScrollView): Int {
        val visibleHeight = (scroll.height - scroll.paddingBottom).coerceAtLeast(1)
        val contentBottom = scroll.getChildAt(0)?.bottom ?: 0
        return (contentBottom - visibleHeight).coerceAtLeast(0)
    }

    private fun start() {
        val scroll = viewport ?: return
        if (closed || !isAttachedToWindow || sheet.height == 0 || scroll.height == 0) return
        // Cancel any native fling before beginning paced scrolling.
        scroll.fling(0)
        val top = sheetTop(scroll)
        val maximum = maximumOffset(scroll)
        val start = if (scroll.scrollY >= maximum - dp(8)) 0 else scroll.scrollY
        scroll.scrollTo(0, start)
        if (maximum <= 0) { pause("Entire sheet is visible"); return }
        clock.reset(start); lastFrame = SystemClock.uptimeMillis()
        running = true; keepScreenOn = true
        toggle.text = "Pause"; status.text = "Scrolling"
        removeCallbacks(frame); postOnAnimation(frame)
    }

    fun pause(message: String = "Paused") {
        running = false; keepScreenOn = false; removeCallbacks(frame)
        toggle.text = "Start scroll"; status.text = message
    }

    fun close() {
        pause(); closed = true
        preferences.edit().putInt(speedKey, speed).apply()
        viewport?.let { if (it.onManualScroll === manualScroll) it.onManualScroll = null }
        viewport = null
    }

    override fun onDetachedFromWindow() { close(); super.onDetachedFromWindow() }
}
