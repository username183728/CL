package com.example.aidetest

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class MdiIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    companion object {
        private const val FONT = "fonts/materialdesignicons-webfont.ttf"
        private val TYPEFACE_CACHE = mutableMapOf<String, Typeface>()
    }

    private var glyphName: String = ""

    init {
        typeface = mdiTypeface(context)
        gravity = android.view.Gravity.CENTER
        includeFontPadding = false
        setTextIsSelectable(false)
    }

    fun setIconName(name: String) {
        glyphName = name
        text = MdiGlyphs.glyph(name)
    }

    fun setIconSize(sizeSp: Float) {
        textSize = sizeSp
    }

    private fun mdiTypeface(context: Context): Typeface {
        return TYPEFACE_CACHE.getOrPut(FONT) {
            Typeface.createFromAsset(context.assets, FONT)
        }
    }

    /** Ganti ikon dengan animasi "pop" halus (dipakai untuk favorit dan navigasi). */
    fun popTo(name: String, overshoot: Float = 1.25f) {
        if (glyphName == name) return
        setIconName(name)
        animate().cancel()
        scaleX = 0.6f; scaleY = 0.6f
        animate().scaleX(1f).scaleY(1f).setDuration(320L)
            .setInterpolator(android.view.animation.OvershootInterpolator(overshoot)).start()
    }

    /** Efek membal singkat tanpa mengganti ikon. */
    fun bounce() {
        animate().cancel()
        animate().scaleX(1.18f).scaleY(1.18f).setDuration(90L).withEndAction {
            animate().scaleX(1f).scaleY(1f).setDuration(220L)
                .setInterpolator(android.view.animation.OvershootInterpolator(2.2f)).start()
        }.start()
    }
}
