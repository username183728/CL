package com.example.aidetest

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/** Reusable loading UX: preloader, indeterminate loader/throbber, determinate progress and skeleton. */
class LoadingUi(private val context: Context) {
    private var overlay: FrameLayout? = null
    private var pulse: ValueAnimator? = null

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).roundToInt()
    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    fun attach(root: ViewGroup, title: String = "GITLS", subtitle: String = "Menyiapkan aplikasi…") {
        if (overlay != null) return
        val host = FrameLayout(context).apply {
            setBackgroundColor(Color.WHITE)
            isClickable = true
            isFocusable = true
            elevation = 1000f
        }
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
        }
        val logo = TextView(context).apply {
            text = "M"
            gravity = Gravity.CENTER
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = rounded(Color.rgb(18,18,20), 18)
        }
        box.addView(logo, LinearLayout.LayoutParams(dp(64), dp(64)))
        box.addView(TextView(context).apply {
            text = title
            textSize = 22f
            setTextColor(Color.rgb(20,21,24))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        })
        box.addView(TextView(context).apply {
            text = subtitle
            textSize = 13f
            setTextColor(Color.rgb(120,126,135))
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(16))
        })
        val spinner = ProgressBar(context).apply { isIndeterminate = true }
        box.addView(spinner, LinearLayout.LayoutParams(dp(32), dp(32)))
        host.addView(box, FrameLayout.LayoutParams(-1, -1))
        root.addView(host, FrameLayout.LayoutParams(-1, -1))
        overlay = host
    }

    fun dismiss(root: ViewGroup, delayMs: Long = 120L) {
        val v = overlay ?: return
        v.animate().alpha(0f).setDuration(220L).withEndAction {
            (v.parent as? ViewGroup)?.removeView(v)
            overlay = null
        }.setStartDelay(delayMs).start()
    }

    fun showIndeterminate(root: ViewGroup, message: String = "Memproses…") {
        val old = overlay
        if (old != null) {
            old.findViewWithTag<TextView>("loading_message")?.text = message
            return
        }
        val host = FrameLayout(context).apply {
            setBackgroundColor(0x66000000)
            isClickable = true
            isFocusable = true
            elevation = 900f
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = rounded(Color.WHITE, 18)
        }
        val p = ProgressBar(context).apply { isIndeterminate = true }
        card.addView(p, LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin = dp(12) })
        card.addView(TextView(context).apply {
            tag = "loading_message"
            text = message
            textSize = 14f
            setTextColor(Color.rgb(25,26,30))
        }, LinearLayout.LayoutParams(-2, -2))
        host.addView(card, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        root.addView(host, FrameLayout.LayoutParams(-1, -1))
        overlay = host
    }

    fun hide(root: ViewGroup) {
        overlay?.let { v ->
            v.animate().alpha(0f).setDuration(160L).withEndAction {
                (v.parent as? ViewGroup)?.removeView(v)
                overlay = null
            }.start()
        }
    }

    fun skeleton(parent: ViewGroup, rows: Int = 3): LinearLayout {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = rounded(Color.rgb(246,247,249), 18)
        }
        repeat(rows) { i ->
            val row = View(context).apply {
                background = rounded(Color.rgb(226,229,233), 10)
                alpha = if (i == 0) 0.9f else 0.7f
            }
            box.addView(row, LinearLayout.LayoutParams(if (i == 0) dp(180) else dp(240), dp(14)).apply {
                bottomMargin = dp(10)
            })
        }
        parent.addView(box, LinearLayout.LayoutParams(-1, -2))
        return box
    }
}

private fun Float.roundToInt(): Int = kotlin.math.round(this).toInt()
