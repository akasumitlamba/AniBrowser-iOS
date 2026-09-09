/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import mozilla.components.concept.toolbar.Toolbar

class TabCountAction(private val openTabs: () -> Unit) : Toolbar.Action {
    private var label: TextView? = null
    private var count = 0
    override fun createView(parent: ViewGroup): View {
        val density = parent.resources.displayMetrics.density
        return FrameLayout(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams((48 * density).toInt(), (48 * density).toInt())
            isClickable = true
            isFocusable = true
            setOnClickListener { openTabs() }
            val badge = TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(Color.parseColor("#DDEBFF"))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = GradientDrawable().apply {
                    cornerRadius = 9 * density
                    setColor(Color.parseColor("#21334F"))
                    setStroke((density).toInt().coerceAtLeast(1), Color.parseColor("#6986B0"))
                }
            }
            addView(badge, FrameLayout.LayoutParams((30 * density).toInt(), (30 * density).toInt(), Gravity.CENTER))
            label = badge
            update(count)
        }
    }
    override fun bind(view: View) { update(count) }
    fun update(value: Int) {
        count = value
        label?.text = if (value > 99) "99+" else value.toString()
        (label?.parent as? View)?.contentDescription = "$value open tabs. Show tabs"
    }
}
