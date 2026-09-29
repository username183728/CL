package com.example.aidetest

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.text.style.RelativeSizeSpan
import android.widget.TextView

/** Mengubah simbol/emoji di awal teks menjadi ikon vektor MDI (bukan emoji). */
object MdiText {
    private val SYMBOLS: Map<String, String> = mapOf(
        "💡" to "lightbulb-outline",
        "🔎" to "magnify",
        "📡" to "radar",
        "🎛" to "tune-variant",
        "📊" to "chart-line",
        "📶" to "wifi",
        "🔄" to "refresh",
        "🌐" to "web",
        "📬" to "send",
        "🔌" to "usb-port",
        "🖥" to "console",
        "📦" to "package-variant-closed",
        "🔘" to "radiobox-marked",
        "⚠" to "alert-outline",
        "✓" to "check",
        "✎" to "pencil-outline",
        "☰" to "menu",
        "⌫" to "backspace-outline",
        "⌕" to "magnify",
        "✦" to "auto-fix",
        "ϟ" to "flash-outline",
        "⌗" to "qrcode-scan",
        "⛶" to "fullscreen",
        "⌃" to "chevron-up",
        "⌄" to "chevron-down",
        "⌂" to "home-outline",
        "▣" to "content-save-outline",
        "▱" to "folder-open-outline",
        "▢" to "content-copy",
        "▤" to "file-document-outline",
        "▧" to "image-outline",
        "♪" to "music-note",
        "▶" to "play",
        "▾" to "download",
        "↔" to "swap-horizontal",
        "×" to "close",
        "★" to "star",
        "☆" to "star-outline",
        "◍" to "circle-slice-8",
    )
    private val REGEX = Regex("^(" + SYMBOLS.keys.joinToString("|") { Regex.escape(it) } + ")\\uFE0F?\\s*")

    private var face: Typeface? = null
    fun typeface(context: Context): Typeface =
        face ?: Typeface.createFromAsset(context.applicationContext.assets, "fonts/materialdesignicons-webfont.ttf").also { face = it }

    /** Hilangkan simbol awal; dipakai untuk contentDescription. */
    fun plain(text: String): String = REGEX.replaceFirst(text, "")

    /** "💡 Judul" -> [ikon] + "  Judul"; teks tanpa simbol dikembalikan apa adanya. */
    fun iconize(context: Context, text: String): CharSequence {
        val m = REGEX.find(text) ?: return text
        val name = SYMBOLS[m.groupValues[1]] ?: return text
        val sb = SpannableStringBuilder(MdiGlyphs.glyph(name))
        val tf = typeface(context)
        sb.setSpan(MdiSpan(tf), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(RelativeSizeSpan(1.3f), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.append("  ").append(text.substring(m.value.length))
        return sb
    }
}

private fun TypefaceSpan(tf: Typeface) = object : android.text.style.MetricAffectingSpan() {
    override fun updateDrawState(p: android.text.TextPaint) { p.typeface = tf }
    override fun updateMeasureState(p: android.text.TextPaint) { p.typeface = tf }
}

/** Jadikan TextView menampilkan satu ikon MDI saja. */
fun TextView.asMdi(name: String) {
    typeface = MdiText.typeface(context)
    text = MdiGlyphs.glyph(name)
    includeFontPadding = false
}

/** Span yang menerapkan typeface font ikon MDI pada teks. */
class MdiSpan(private val typeface: Typeface) : MetricAffectingSpan() {
    override fun updateDrawState(paint: TextPaint) { paint.typeface = typeface }
    override fun updateMeasureState(paint: TextPaint) { paint.typeface = typeface }
}
