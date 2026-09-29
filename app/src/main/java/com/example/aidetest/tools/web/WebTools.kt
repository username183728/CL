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


fun MainActivity.httpServer() {
        clearPage("HTTP Server")
        addToolHeader("HTTP Server", "Server HTTP lokal untuk file/project web.", "WEB")
        val root = File(prefs.getString("last_web_project", "") ?: "")
        val port = edit("Port", false).apply { setText("8080") }
        content.addView(port)
        val path = label(if (root.isDirectory) "Root: ${root.absolutePath}" else "Root belum dipilih", 12f)
        content.addView(path)
        val status = label(if (server != null && !server!!.isClosed) "RUNNING" else "STOPPED", 16f, true)
        content.addView(status)
        content.addView(button("Gunakan Project Web terakhir") {
            val p = File(prefs.getString("last_web_project", "") ?: "")
            if (!p.isDirectory || !File(p, "index.html").isFile) {
                toast("Build Web Project dulu")
                return@button
            }
            path.text = "Root: ${p.absolutePath}"
        })
        content.addView(button("Start Static Server") {
            val p = File(prefs.getString("last_web_project", "") ?: "")
            val prt = port.text.toString().toIntOrNull()
            if (!p.isDirectory || !File(p, "index.html").isFile) {
                toast("Build Web Project dulu")
                return@button
            }
            if (prt == null || prt !in 1024..65535) {
                toast("Port harus 1024-65535")
                return@button
            }
            startStaticWebServer(p, prt, status)
        })
        content.addView(button("Stop Server") {
            stopStaticWebServer()
            status.text = "STOPPED"
        })
        content.addView(subLabel("Melayani index.html, CSS, JS, gambar, font, JSON, SVG, dan file project lain. Path traversal di luar folder project ditolak.", 11f))
    }

fun MainActivity.wifiHtmlHostingTool() {
        clearPage("HTML Hosting Wi-Fi")
        addToolHeader("HTML Hosting Wi-Fi", "Host website di Wi-Fi rumah yang sedang dipakai HP.", "WiFi")
        val project = File(prefs.getString("last_web_project", "") ?: "")
        content.addView(label(if (project.isDirectory && File(project, "index.html").isFile) "Project: ${project.name}" else "Belum ada project", 13f, true))
        val network = label("Jaringan: ${currentWifiSsid()}", 13f)
        content.addView(network)
        val port = edit("Port", false).apply { setText("8080") }
        content.addView(port)
        val status = label(if (server != null && !server!!.isClosed) "RUNNING" else "STOPPED", 16f, true)
        hostingStatusView=status; content.addView(status)
        val credentials=label("SSID: ${currentWifiSsid()}\nPassword Wi-Fi: tidak diperlukan oleh server",13f); hostingCredentialsView=credentials; content.addView(credentials)
        val url=label("URL: -",13f,true); hostingUrlView=url; content.addView(url)
        val qr=ImageView(this).apply { setBackgroundColor(Color.WHITE); visibility=View.GONE; scaleType=ImageView.ScaleType.CENTER_INSIDE; layoutParams=LinearLayout.LayoutParams(dp(220),dp(220)).apply{gravity=Gravity.CENTER_HORIZONTAL;topMargin=dp(10);bottomMargin=dp(10)} }; hostingQrView=qr; content.addView(qr)
        content.addView(button("START HOSTING WI-FI RUMAH") {
            val p=File(prefs.getString("last_web_project","") ?: "")
            if(!p.isDirectory || !File(p,"index.html").isFile || !prefs.getBoolean("last_web_build_ok",false)){toast("Build website sampai SUCCESS dulu");return@button}
            val prt=port.text.toString().toIntOrNull()?.takeIf{it in 1024..65535} ?: run{toast("Port harus 1024-65535");return@button}
            pendingHostingPort=prt; pendingHostingRoot=p; startHomeWifiHosting()
        })
        content.addView(button("STOP HOSTING") { stopWifiHtmlHosting() })
        content.addView(button("COPY URL") { val text=hostingUrlView?.text?.toString()?.substringAfter("URL: ")?.lineSequence()?.firstOrNull()?.trim().orEmpty(); if(text.isBlank()||text=="-") toast("Hosting belum aktif") else copyText(text) })
        content.addView(subLabel("Semua perangkat harus terhubung ke Wi-Fi rumah yang sama. Password Wi-Fi rumah tetap dikelola router/Android dan tidak disimpan MyTools. Hanya file project hasil Build yang dilayani.",11f))
    }
