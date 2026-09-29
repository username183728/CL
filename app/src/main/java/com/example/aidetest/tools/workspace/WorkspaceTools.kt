package com.example.aidetest


import android.Manifest
import android.app.*
import android.app.usage.StorageStatsManager
import android.os.StatFs
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.*
import android.provider.Settings
import android.provider.MediaStore
import android.media.ImageReader
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.text.InputType
import android.view.*
import android.widget.*
import android.webkit.MimeTypeMap
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.MultiFormatReader
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.NotFoundException
import com.google.zxing.common.HybridBinarizer
import java.io.*
import java.net.*
import java.nio.charset.StandardCharsets
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.*
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread
import kotlin.math.min
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlin.math.roundToInt
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions


fun MainActivity.workspaceCenterTool() {
        clearPage("Workspace Center")
        val dirs = workspaceRoot().listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.lastModified() } ?: emptyList()
        content.addView(toolHeader("Workspace Center", "Project lokal • ${dirs.size} workspace", "view-dashboard-outline"), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        content.addView(subLabel("Satu tempat untuk project Web, kode, data, dan file kerja.", 11f))

        val create = button("+  Workspace Baru") {
            val name = edit("Nama workspace", false).apply { hint = "contoh: GameProject" }
            AlertDialog.Builder(this).setTitle("Workspace Baru").setView(name)
                .setNegativeButton("Batal", null).setPositiveButton("Buat") { _, _ ->
                    val n = name.text.toString().trim()
                    if (n.isBlank()) { toast("Masukkan nama workspace"); return@setPositiveButton }
                    val safe = n.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().replace(" ", "_")
                    val dir = File(workspaceRoot(), safe)
                    if (!dir.mkdirs() && !dir.isDirectory) { toast("Workspace gagal dibuat"); return@setPositiveButton }
                    File(dir, "workspace.json").writeText(JSONObject().apply { put("name", n); put("createdAt", System.currentTimeMillis()); put("version", 1) }.toString(2), StandardCharsets.UTF_8)
                    prefs.edit().putString("last_workspace", dir.absolutePath).apply(); toast("Workspace dibuat: $safe"); workspaceCenterTool()
                }.show()
        }
        content.addView(create)

        if (dirs.isEmpty()) {
            val empty = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(20), dp(30), dp(20), dp(30)); background = bg(panel2, 18, line) }
            empty.addView(MdiIconView(this).apply { setIconName("folder-plus-outline"); setIconSize(38f); setTextColor(textMuted); layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)).apply { gravity = Gravity.CENTER } })
            empty.addView(label("Belum ada workspace", 16f, true).apply { gravity = Gravity.CENTER })
            empty.addView(subLabel("Buat project pertama untuk mulai bekerja.", 11f).apply { gravity = Gravity.CENTER })
            content.addView(empty)
            return
        }

        content.addView(toolSection("PROJECTS", "Workspace terbaru muncul di atas."))
        dirs.forEach { dir -> content.addView(workspaceCard(dir)) }
    }

fun MainActivity.pluginCenterTool() {
        clearPage("Plugin Center")
        content.addView(label("Plugin Center", 22f, true))
        content.addView(subLabel("Plugin lokal berbasis manifest JSON. Plugin tidak dijalankan otomatis dan tidak diberi akses khusus.", 12f))
        val root = File(filesDir, "plugins").apply { mkdirs() }
        content.addView(button("Buat Template Plugin") {
            val f = File(root, "plugin_${System.currentTimeMillis()}.json")
            f.writeText(JSONObject().apply {
                put("id", f.nameWithoutExtension); put("name", "My Plugin"); put("version", "1.0");
                put("description", "Local tool manifest"); put("enabled", false); put("entry", "")
            }.toString(2), StandardCharsets.UTF_8)
            toast("Template plugin dibuat"); pluginCenterTool()
        })
        val files = root.listFiles()?.filter { it.extension.equals("json", true) } ?: emptyList()
        if (files.isEmpty()) content.addView(subLabel("Belum ada manifest plugin.", 13f))
        files.forEach { f ->
            val j = runCatching { JSONObject(f.readText(StandardCharsets.UTF_8)) }.getOrNull()
            val name = j?.optString("name", f.nameWithoutExtension) ?: f.nameWithoutExtension
            val ver = j?.optString("version", "?") ?: "?"
            content.addView(settingRowClickable(name, "v$ver", f.absolutePath, "tools") { output(f.readText(StandardCharsets.UTF_8)) })
        }
    }

fun MainActivity.toolCustomizationTool() {
        clearPage("Tool Customization")
        content.addView(label("Tool Customization", 22f, true))
        content.addView(subLabel("Pin tool ke Beranda atau sembunyikan tool tertentu. Pengaturan disimpan lokal.", 12f))
        val pinned = prefs.getStringSet("pinned_tools", emptySet()) ?: emptySet()
        content.addView(button("Reset Kustomisasi") { prefs.edit().remove("pinned_tools").remove("hidden_tools").apply(); toolCustomizationTool() })
        homeTools.forEach { (id, name) ->
            val isPinned = pinned.contains(id)
            val row = settingRowClickable(name, if (isPinned) "Pinned" else "Tidak dipin", "ID: $id", "tools") {
                val now = prefs.getStringSet("pinned_tools", emptySet())?.toMutableSet() ?: mutableSetOf()
                if (now.contains(id)) now.remove(id) else now.add(id)
                prefs.edit().putStringSet("pinned_tools", now).apply(); toolCustomizationTool()
            }
            content.addView(row)
        }
    }

fun MainActivity.studioCenterTool() {
        clearPage("Studio Center")
        content.addView(label("Studio Center", 22f, true))
        content.addView(subLabel("Workspace terpadu untuk File, Network, Developer, System, Finance, Utility, dan Web.", 12f))
        val studios = listOf(
            "File Studio" to "filestudio", "Network Studio" to "networkstudio", "Developer Studio" to "developerstudio",
            "System Studio" to "systemstudio", "Finance Studio" to "financestudio", "Utility Studio" to "utilitystudio",
            "Web Project Builder" to "webproject", "Workspace Center" to "workspace"
        )
        studios.forEach { (n,id) -> content.addView(settingRowClickable(n, "Buka workspace", "Studio terpadu", iconFor(id)) { openTool(id) }) }
    }

fun MainActivity.networkStudioTool(){ studioHub("Network Studio","Semua alat jaringan dalam satu workspace.",listOf("Ping" to "ping","Port Checker" to "port","DNS Lookup" to "dns","Reverse DNS" to "rdns","Whois" to "whois","Traceroute" to "traceroute","HTTP Headers" to "httpheaders","SSL Certificate" to "ssl","Network Scanner" to "netscanner","Subnet Calculator" to "subnetcalc")) }

fun MainActivity.developerStudioTool(){ studioHub("Developer Studio","Editor dan formatter untuk developer.",listOf("Web Project Builder" to "webproject","JSON Formatter" to "jsonformat","XML Formatter" to "xmlformat","Regex Tester" to "regex","Timestamp Converter" to "timestamp","Base64" to "base64","JWT Decoder" to "jwt","UUID Generator" to "uuid","Hash Generator" to "hash")) }

fun MainActivity.fileStudioTool(){ studioHub("File Studio","Kelola, cari dan analisis file.",listOf("File Manager" to "filemanager","File Search" to "filesearch","Duplicate Finder" to "dedupe","ZIP / UNZIP" to "zip","File Converter" to "fileconvert","Checksum File" to "checksum","Storage Analyzer" to "storage")) }

fun MainActivity.systemStudioTool(){ studioHub("System Studio","Informasi perangkat dan sistem.",listOf("Device Info" to "deviceinfo","Battery Info" to "battery","Storage Analyzer" to "storage","System Info" to "system","Network Info" to "network","App Manager" to "apps")) }

fun MainActivity.utilityStudioTool(){ studioHub("Utility Studio","Utilitas sehari-hari.",listOf("Calculator" to "number","Clipboard Manager" to "clipboard","Unit Converter" to "unitconverter","QR Scanner" to "qr","OCR" to "ocr","Password Generator" to "password","Notes / Notifikasi" to "reminder")) }
