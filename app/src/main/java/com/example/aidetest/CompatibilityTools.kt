package com.example.aidetest

import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.net.wifi.WifiManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.io.File
import java.io.InputStream
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

internal fun MainActivity.workspaceRootCompat(): File =
    File(filesDir, "workspaces").apply { mkdirs() }

internal fun MainActivity.stopStaticWebServer() {
    runCatching { server?.close() }
    server = null
}

internal fun MainActivity.generateHostingQr(url: String) {
    val target = hostingQrView ?: return
    runCatching {
        val matrix = MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, 640, 640)
        val bmp = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
        target.setImageBitmap(bmp)
    }.onFailure { toast("QR gagal dibuat: ${it.message}") }
}

internal fun digestStreamCompat(input: InputStream, algorithm: String): String {
    val md = MessageDigest.getInstance(algorithm)
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val n = input.read(buffer)
        if (n <= 0) break
        md.update(buffer, 0, n)
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

internal fun MainActivity.digestStream(input: InputStream, algorithm: String): String =
    digestStreamCompat(input, algorithm)

internal fun markdownToHtml(source: String): String {
    fun esc(s: String) = s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
    return source.lines().joinToString("<br>") { raw ->
        var s = esc(raw)
        s = s.replace(Regex("`([^`]+)`"), "<code>$1</code>")
        s = s.replace(Regex("\\*\\*([^*]+)\\*\\*"), "<strong>$1</strong>")
        s = s.replace(Regex("\\*([^*]+)\\*"), "<em>$1</em>")
        s.replace(Regex("^[-*]\\s+"), "• ")
    }
}

internal fun MainActivity.calcButton(text: String, onClick: () -> Unit): Button =
    button(text, onClick)

internal fun MainActivity.twoFields(
    title: String,
    firstHint: String,
    secondHint: String,
    actionText: String,
    transform: (Double, Double) -> String
) {
    clearPage(title)
    content.addView(label(title, 22f, true))
    val a = edit(firstHint)
    val b = edit(secondHint)
    content.addView(a); content.addView(b)
    content.addView(button(actionText) {
        val av = a.num()
        val bv = b.num()
        output(if (av == null || bv == null) "Input tidak valid" else runCatching {
            transform(av, bv)
        }.getOrElse { "Gagal: ${it.message}" })
    })
}

internal fun gcd(a: Long, b: Long): Long {
    var x = abs(a); var y = abs(b)
    while (y != 0L) { val t = x % y; x = y; y = t }
    return if (x == 0L) 1L else x
}

internal fun MainActivity.convertUnit(value: Double, from: String, to: String): Double {
    if (from == to) return value
    fun toBase(v: Double, u: String): Double = when (u.lowercase(Locale.US)) {
        "meter" -> v
        "kilometer" -> v * 1000.0
        "centimeter" -> v / 100.0
        "milimeter" -> v / 1000.0
        "inch" -> v * 0.0254
        "feet" -> v * 0.3048
        "yard" -> v * 0.9144
        "mile" -> v * 1609.344
        "gram" -> v
        "kilogram" -> v * 1000.0
        "pound" -> v * 453.59237
        "pascal" -> v
        "kpa" -> v * 1000.0
        "bar" -> v * 100000.0
        "psi" -> v * 6894.757293168
        "mps" -> v
        "kmh" -> v / 3.6
        "mph" -> v * 0.44704
        else -> v
    }
    val f = from.lowercase(Locale.US)
    val t = to.lowercase(Locale.US)
    if (f in setOf("celsius","fahrenheit","kelvin","reamur") ||
        t in setOf("celsius","fahrenheit","kelvin","reamur")) {
        val c = when (f) {
            "celsius" -> value
            "fahrenheit" -> (value - 32.0) * 5.0 / 9.0
            "kelvin" -> value - 273.15
            "reamur" -> value * 5.0 / 4.0
            else -> value
        }
        return when (t) {
            "celsius" -> c
            "fahrenheit" -> c * 9.0 / 5.0 + 32.0
            "kelvin" -> c + 273.15
            "reamur" -> c * 4.0 / 5.0
            else -> c
        }
    }
    val base = toBase(value, f)
    return when (t) {
        "meter" -> base
        "kilometer" -> base / 1000.0
        "centimeter" -> base * 100.0
        "milimeter" -> base * 1000.0
        "inch" -> base / 0.0254
        "feet" -> base / 0.3048
        "yard" -> base / 0.9144
        "mile" -> base / 1609.344
        "gram" -> base
        "kilogram" -> base / 1000.0
        "pound" -> base / 453.59237
        "pascal" -> base
        "kpa" -> base / 1000.0
        "bar" -> base / 100000.0
        "psi" -> base / 6894.757293168
        "mps" -> base
        "kmh" -> base * 3.6
        "mph" -> base / 0.44704
        else -> value
    }
}

internal fun cronFieldMeaning(field: String): String =
    when (field.trim()) {
        "*" -> "semua nilai"
        else -> "nilai / rentang / daftar cron"
    }

internal fun scanRoots(): List<File> =
    buildList {
        add(File(System.getProperty("user.home") ?: "/"))
        File("/storage/emulated/0").takeIf { it.isDirectory }?.let(::add)
    }.distinctBy { it.absolutePath }

internal fun scanRootsCompat(): List<File> = scanRoots()

internal fun formatEspJson(raw: String): String =
    runCatching { org.json.JSONObject(raw).toString(2) }
        .recoverCatching { org.json.JSONArray(raw).toString(2) }
        .getOrElse { raw }

internal fun MainActivity.sendGpio(
    base: String, pin: String, state: String, mode: String, pwm: String, outputView: TextView
) {
    val path = "/gpio?pin=${java.net.URLEncoder.encode(pin, "UTF-8")}&mode=${java.net.URLEncoder.encode(mode, "UTF-8")}&state=${java.net.URLEncoder.encode(state, "UTF-8")}&pwm=${java.net.URLEncoder.encode(pwm, "UTF-8")}"
    Thread {
        val result = runCatching {
            val c = java.net.URL(base.trimEnd('/') + path).openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 5000; c.readTimeout = 5000
            val text = (if (c.responseCode in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            "HTTP ${c.responseCode}\n$text"
        }.getOrElse { "GPIO gagal: ${it.message}" }
        runOnUiThread { outputView.text = result }
    }.start()
}

internal fun ipv4(value: Long): String =
    "${(value shr 24) and 255}.${(value shr 16) and 255}.${(value shr 8) and 255}.${value and 255}"

internal fun folderSize(file: File): Long {
    if (file.isFile) return file.length()
    return file.listFiles()?.sumOf { folderSize(it) } ?: 0L
}

internal fun formatDuration(ms: Long): String {
    var s = ms / 1000
    val d = s / 86400; s %= 86400
    val h = s / 3600; s %= 3600
    val m = s / 60; s %= 60
    return "${d}d ${h}h ${m}m ${s}s"
}

internal object Base32 {
    private const val ALPH = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    fun encode(data: ByteArray): String {
        var buffer = 0; var bits = 0
        val out = StringBuilder((data.size * 8 + 4) / 5)
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 255); bits += 8
            while (bits >= 5) { bits -= 5; out.append(ALPH[(buffer shr bits) and 31]) }
        }
        if (bits > 0) out.append(ALPH[(buffer shl (5 - bits)) and 31])
        return out.toString()
    }
    fun decode(input: String): ByteArray {
        var buffer = 0; var bits = 0
        val out = java.io.ByteArrayOutputStream()
        for (c in input.uppercase(Locale.US).filterNot { it.isWhitespace() || it == '=' }) {
            val v = ALPH.indexOf(c); require(v >= 0) { "Invalid Base32" }
            buffer = (buffer shl 5) or v; bits += 5
            if (bits >= 8) { bits -= 8; out.write((buffer shr bits) and 255) }
        }
        return out.toByteArray()
    }
}

internal object JSONObjectLite {
    fun escape(s: String): String =
        s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")
}
