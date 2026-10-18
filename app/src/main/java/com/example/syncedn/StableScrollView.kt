package com.example.syncedn

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.ScrollView

/** Native fling physics without edge stretching; data refreshes wait until scrolling settles. */
internal class StableScrollView(context: Context) : ScrollView(context) {
    var onManualScroll: (() -> Unit)? = null
    private var touching = false
    private var lastMotion = 0L
    private var idleAction: (() -> Unit)? = null
    private val idleCheck = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow) { idleAction = null; return }
            if (touching || SystemClock.uptimeMillis() - lastMotion < 200L) {
                postDelayed(this, 80L)
            } else {
                val action = idleAction
                idleAction = null
                action?.invoke()
            }
        }
    }

    init {
        isFillViewport = true
        overScrollMode = OVER_SCROLL_NEVER
        isSmoothScrollingEnabled = true
        isVerticalScrollBarEnabled = false
        clipChildren = true
        clipToPadding = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // The existing page containers allow shadows outside their bounds.
        // Explicitly clip this viewport so translated list content cannot paint over siblings.
        clipBounds = Rect(0, 0, w, h)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touching = true
                onManualScroll?.invoke()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touching = false
        }
        lastMotion = SystemClock.uptimeMillis()
        return super.dispatchTouchEvent(event)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        lastMotion = SystemClock.uptimeMillis()
    }

    fun whenIdle(action: () -> Unit) {
        idleAction = action
        removeCallbacks(idleCheck)
        post(idleCheck)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(idleCheck)
        idleAction = null
        touching = false
        super.onDetachedFromWindow()
    }
}
