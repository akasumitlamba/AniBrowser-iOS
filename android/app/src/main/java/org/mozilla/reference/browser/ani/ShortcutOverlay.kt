/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Gesture-triggered floating overlay for shortcuts/WebsiteActivity mode.
 *
 * Shows only when the user swipes up from the bottom edge of the screen.
 * Auto-hides after [AUTO_HIDE_MS] ms of no interaction.
 * Provides: playback speed selector, 10-second seek back/forward, view mode toggle.
 */
class ShortcutOverlay(
    context: Context,
    private val container: ViewGroup,
    private val onSpeedSelected: (Double) -> Unit,
    private val onSeek: (seconds: Int) -> Unit,
    private val onViewModeSelected: (mode: Int) -> Unit,  // 0=Mobile, 1=DeskId/MobileLayout, 2=Desktop
    private val getCurrentSpeed: () -> Double,
    private val getCurrentViewMode: () -> Int,
) {
    companion object {
        private const val AUTO_HIDE_MS = 3500L
        private val SPEEDS = listOf(1.0, 1.25, 1.5, 1.75, 2.0)
        private val VIEW_LABELS = listOf("📱 Mobile", "🖥 Desk+Mobile", "💻 Desktop")
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isVisible = false

    // The pill-shaped overlay panel
    private val panel: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dpToPx(context, 16), dpToPx(context, 12), dpToPx(context, 16), dpToPx(context, 12))
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#CC000000"))  // 80% opaque black
            cornerRadius = dpToPx(context, 20).toFloat()
        }
        elevation = dpToPx(context, 8).toFloat()
        alpha = 0f
        visibility = View.GONE
    }

    init {
        buildPanel(context)
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dpToPx(context, 24)
        }
        container.addView(panel, lp)

        // Touch interceptor on the container to detect upward swipe from bottom edge
        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val dy = e1.y - e2.y
                val screenH = container.height.toFloat()
                // Upward fling starting from bottom third of screen
                if (dy > 80 && e1.y > screenH * 0.6f) {
                    show()
                    return true
                }
                return false
            }
        })
        container.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false  // don't consume — let EngineView also receive touches
        }
    }

    private fun buildPanel(context: Context) {
        // Speed row label
        panel.addView(makeLabel(context, "Playback Speed"))

        // Speed buttons row
        val speedRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        for (speed in SPEEDS) {
            val btn = makeButton(context, "${speed}×") {
                onSpeedSelected(speed)
                refreshSpeedHighlight(speedRow)
                resetAutoHide()
            }
            btn.tag = speed
            speedRow.addView(btn)
        }
        panel.addView(speedRow)

        // Seek row label
        panel.addView(makeLabel(context, "Seek"))

        // Seek buttons row
        val seekRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        seekRow.addView(makeButton(context, "⏪ 10s") { onSeek(-10); resetAutoHide() })
        seekRow.addView(makeButton(context, "⏩ 10s") { onSeek(10); resetAutoHide() })
        panel.addView(seekRow)

        // View mode label
        panel.addView(makeLabel(context, "View Mode"))

        // View mode buttons row
        val modeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        for ((idx, label) in VIEW_LABELS.withIndex()) {
            val btn = makeButton(context, label) {
                onViewModeSelected(idx)
                resetAutoHide()
            }
            modeRow.addView(btn)
        }
        panel.addView(modeRow)

        // Close / dismiss hint
        val closeBtn = makeButton(context, "✕ Hide") {
            hide()
        }
        panel.addView(closeBtn)
    }

    private fun refreshSpeedHighlight(speedRow: LinearLayout) {
        val current = getCurrentSpeed()
        for (i in 0 until speedRow.childCount) {
            val btn = speedRow.getChildAt(i) as? TextView ?: continue
            val isSelected = (btn.tag as? Double) == current
            btn.setTextColor(if (isSelected) Color.parseColor("#FF6B35") else Color.WHITE)
        }
    }

    fun show() {
        if (isVisible) { resetAutoHide(); return }
        isVisible = true
        panel.visibility = View.VISIBLE
        panel.animate()
            .alpha(1f)
            .setDuration(200)
            .setListener(null)
            .start()
        resetAutoHide()
    }

    fun hide() {
        if (!isVisible) return
        isVisible = false
        handler.removeCallbacksAndMessages(null)
        panel.animate()
            .alpha(0f)
            .setDuration(200)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    panel.visibility = View.GONE
                }
            })
            .start()
    }

    private fun resetAutoHide() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ hide() }, AUTO_HIDE_MS)
    }

    private fun makeLabel(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 11f
        setTextColor(Color.parseColor("#AAAAAA"))
        val v = dpToPx(context, 4)
        val h = dpToPx(context, 2)
        setPadding(h, v, h, 0)
    }

    private fun makeButton(context: Context, label: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#44FFFFFF"))
                cornerRadius = dpToPx(context, 8).toFloat()
            }
            val hp = dpToPx(context, 12)
            val vp = dpToPx(context, 6)
            val m = dpToPx(context, 4)
            setPadding(hp, vp, hp, vp)
            (layoutParams as? ViewGroup.MarginLayoutParams)?.setMargins(m, m, m, m)
                ?: run { layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(m, m, m, m) }
                }
            setOnClickListener { onClick() }
        }

    private fun dpToPx(context: Context, dp: Int): Int =
        (dp * context.resources.displayMetrics.density + 0.5f).toInt()
}
