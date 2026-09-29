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


fun MainActivity.systemInfo() {
        clearPage("Sistem")
        output(
            "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
            "ABIs: ${Build.SUPPORTED_ABIS.joinToString()}\n" +
            "App storage: ${filesDir.absolutePath}\n" +
            "Free storage: ${filesDir.freeSpace / 1024 / 1024} MB\n" +
            "Package: $packageName"
        )
        content.addView(button("Buka Pengaturan Aplikasi") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
    }

fun MainActivity.deviceInfoTool() {
        clearPage("Device Info")
        toolWorkspace("Device Info", "Ringkasan perangkat Android, layar, ABI, RAM, dan build.", "cellphone-information")
        toolWorkspaceSection("DEVICE", "Data dibaca langsung dari sistem perangkat.")
        val dm = resources.displayMetrics
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        infoRow("Model", "${Build.MANUFACTURER} ${Build.MODEL}")
        infoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        infoRow("CPU ABI", Build.SUPPORTED_ABIS.joinToString(", "))
        infoRow("RAM", "${bytesText(mem.totalMem)} total • ${bytesText(mem.availMem)} tersedia")
        infoRow("Layar", "${dm.widthPixels} × ${dm.heightPixels} px • density ${dm.density}")
        infoRow("Build", Build.DISPLAY)
    }

fun MainActivity.storageAnalyzerTool() {
        clearPage("Storage Analyzer")
        toolWorkspace("Storage Analyzer", "Pantau penggunaan penyimpanan dan ukuran data MyTools.", "database")
        toolWorkspaceSection("STORAGE", "Ukuran filesystem utama dan folder aplikasi.")
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val free = stat.availableBytes
        val used = total - free
        infoRow("Internal", "${bytesText(used)} digunakan dari ${bytesText(total)} (${if (total > 0) used * 100 / total else 0}%)")
        infoRow("Tersedia", bytesText(free))
        val app = filesDir
        val appSize = folderSize(app)
        infoRow("Data MyTools", bytesText(appSize))
        content.addView(button("Hitung ulang") { storageAnalyzerTool() })
    }

fun MainActivity.appManagerTool() {
        clearPage("App Manager", true)
        content.addView(label("Aplikasi terpasang", 22f, true))
        content.addView(subLabel("Pilih aplikasi untuk membuka halaman App Info Android.", 12f))
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .sortedBy { pm.getApplicationLabel(it).toString().toLowerCase(Locale.getDefault()) }
        apps.forEach { app ->
            val name = pm.getApplicationLabel(app).toString()
            val pkg = app.packageName
            val b = button("$name\n$pkg") {
                val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                startActivity(i)
            }
            content.addView(b)
        }
    }

fun MainActivity.batteryInfoTool() {
        clearPage("Battery Info")
        toolWorkspace("Battery Info", "Status baterai, suhu, tegangan, dan kondisi pengisian.", "battery-high")
        toolWorkspaceSection("BATTERY", "Informasi dibaca dari BatteryManager Android.")
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent == null) { infoRow("Status", "Tidak tersedia"); return }
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val statusText = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Mengisi"
            BatteryManager.BATTERY_STATUS_FULL -> "Penuh"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Tidak mengisi"
            else -> "Tidak diketahui"
        }
        infoRow("Level", if (scale > 0) "${level * 100 / scale}%" else "Tidak diketahui")
        infoRow("Status", statusText)
        infoRow("Suhu", String.format(Locale.getDefault(), "%.1f °C", temp))
        infoRow("Tegangan", "$voltage mV")
    }

fun MainActivity.clipboardManagerTool() {
        clearPage("Clipboard Manager")
        content.addView(label("Clipboard Manager", 22f, true))
        content.addView(subLabel("Riwayat clipboard disimpan lokal di perangkat. Android membatasi akses clipboard di background; monitor aktif hanya saat tool ini dibuka.", 12f))
        val current = clipboardText()
        if (current != null) {
            content.addView(label("Clipboard saat ini", 13f, true))
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)); background = bg(panel2, 14, line) }
            card.addView(label(current.take(4000), 14f))
            card.addView(button("Salin lagi") { copyText(current) })
            content.addView(card)
            saveClipboard(current)
        }
        content.addView(button("Ambil Clipboard Sekarang") {
            val value = clipboardText()
            if (value == null) toast("Clipboard kosong atau bukan teks") else { saveClipboard(value); clipboardManagerTool() }
        })
        content.addView(button("Hapus Riwayat Clipboard") { prefs.edit().remove("clipboard_history").apply(); clipboardManagerTool() })
        content.addView(label("Riwayat", 15f, true))
        val arr = runCatching { JSONArray(prefs.getString("clipboard_history", "[]") ?: "[]") }.getOrElse { JSONArray() }
        if (arr.length() == 0) content.addView(subLabel("Belum ada riwayat clipboard.", 12f))
        for (i in 0 until arr.length()) {
            val value = arr.optString(i)
            if (value.isBlank()) continue
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)); background = bg(panel2, 14, line)
                setOnClickListener { copyText(value) }
            }
            card.addView(label(value.take(700), 13f))
            card.addView(subLabel("Tap untuk menyalin • ${value.length} karakter", 10f))
            content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
        }
        startClipboardMonitor()
    }

fun MainActivity.systemCenterTool() {
        clearPage("System Center")
        content.addView(label("System Center",22f,true)); content.addView(subLabel("Ringkasan CPU, RAM, storage, baterai, uptime dan konfigurasi Android.",12f))
        val am=getSystemService(ACTIVITY_SERVICE) as ActivityManager; val mem=ActivityManager.MemoryInfo().also{am.getMemoryInfo(it)}
        val stat=StatFs(Environment.getDataDirectory().path)
        infoRow("Device","${Build.MANUFACTURER} ${Build.MODEL}"); infoRow("Android","${Build.VERSION.RELEASE} • API ${Build.VERSION.SDK_INT}")
        infoRow("ABI",Build.SUPPORTED_ABIS.joinToString(", ")); infoRow("CPU cores",Runtime.getRuntime().availableProcessors().toString())
        infoRow("RAM","${bytesText(mem.availMem)} tersedia / ${bytesText(mem.totalMem)} total")
        infoRow("Storage","${bytesText(stat.availableBytes)} tersedia / ${bytesText(stat.totalBytes)} total")
        infoRow("Uptime",formatDuration(SystemClock.elapsedRealtime())); infoRow("Build",Build.DISPLAY)
        val b=registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED)); if(b!=null){val l=b.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);val sc=b.getIntExtra(BatteryManager.EXTRA_SCALE,100);infoRow("Battery",if(sc>0)"${l*100/sc}%" else "?")}
        content.addView(button("Refresh"){systemCenterTool()})
    }
