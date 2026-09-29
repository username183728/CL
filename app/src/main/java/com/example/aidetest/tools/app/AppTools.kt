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


fun MainActivity.apkInspector() {
        clearPage("APK Inspector")
        content.addView(button("Pilih APK") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type="application/vnd.android.package-archive"; addCategory(Intent.CATEGORY_OPENABLE)
            },1002)
        })
        content.addView(label("Menampilkan daftar isi APK/ZIP. Parsing AndroidManifest binary XML penuh memerlukan parser tambahan."))
    }

fun MainActivity.apkAnalyzerTool() {
        clearPage("APK Analyzer Lengkap")
        toolWorkspace("APK Analyzer Lengkap", "Periksa package, SDK, permission, DEX, native library, signature, dan isi ZIP.", "android-studio")
        toolWorkspaceSection("ANALYSIS", "Pilih APK lalu jalankan analisis. Hasil muncul di bawah.")
        content.addView(button("Pilih APK") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "application/vnd.android.package-archive"; addCategory(Intent.CATEGORY_OPENABLE)
            }, 1021)
        })
        content.addView(button("Analisis APK terakhir") { pendingApkUri?.let { analyzeApk(it) } ?: toast("Pilih APK terlebih dahulu") })
        pendingApkOutput?.let { content.addView(it) }
    }

fun MainActivity.apkCompareTool(){
        clearPage("APK Compare"); content.addView(label("APK Compare",22f,true)); content.addView(subLabel("Bandingkan metadata dan isi dua APK tanpa menginstalnya.",12f))
        content.addView(button("Pilih APK A") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="application/vnd.android.package-archive";addCategory(Intent.CATEGORY_OPENABLE)},1301) })
        content.addView(button("Pilih APK B") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="application/vnd.android.package-archive";addCategory(Intent.CATEGORY_OPENABLE)},1302) })
    }

fun MainActivity.unitConverterProTool() {
        clearPage("Unit Converter")
        addToolHeader("Unit Converter", "Konversi nilai dengan pasangan satuan yang jelas dan cepat.", "↔")
        val categories = arrayOf("Panjang", "Berat", "Suhu", "Luas", "Volume", "Waktu", "Kecepatan", "Tekanan", "Data", "Energi")
        val from = Spinner(this); val to = Spinner(this); val value = edit("Nilai")
        val category = Spinner(this)
        category.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        content.addView(category, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(7) })
        content.addView(value)
        content.addView(from, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(7) })
        content.addView(to, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(7) })
        val result = label("Hasil akan tampil di sini", 17f, true)
        content.addView(result)
        fun setUnits(index: Int) {
            val units = when(index) {
                0 -> arrayOf("Meter (m)", "Kilometer (km)", "Centimeter (cm)", "Millimeter (mm)", "Inch (in)", "Feet (ft)", "Yard (yd)", "Mile (mi)")
                1 -> arrayOf("Gram (g)", "Kilogram (kg)", "Milligram (mg)", "Pound (lb)", "Ounce (oz)")
                2 -> arrayOf("Celsius (°C)", "Fahrenheit (°F)", "Kelvin (K)")
                3 -> arrayOf("m²", "km²", "cm²", "ft²", "acre")
                4 -> arrayOf("Liter (L)", "Milliliter (mL)", "m³", "cm³", "gallon US")
                5 -> arrayOf("Second", "Minute", "Hour", "Day")
                6 -> arrayOf("m/s", "km/h", "mph", "knot")
                7 -> arrayOf("Pa", "kPa", "bar", "psi", "atm")
                8 -> arrayOf("Byte", "KB", "MB", "GB", "TB")
                else -> arrayOf("Joule (J)", "Kilojoule (kJ)", "calorie (cal)", "kWh", "Wh")
            }
            from.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, units)
            to.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, units)
            if (units.size > 1) to.setSelection(1)
        }
        setUnits(0)
        category.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { setUnits(position) }
        }
        content.addView(button("Konversi") {
            val x = value.text.toString().replace(',', '.').toDoubleOrNull()
            if (x == null) { result.text = "Nilai tidak valid"; return@button }
            val cat = category.selectedItemPosition
            val a = from.selectedItemPosition; val b = to.selectedItemPosition
            val out = convertUnits(x, cat, a, b)
            result.text = "${fmt(out)} ${to.selectedItem}"
        })
        content.addView(button("Tukar Satuan") {
            val old = from.selectedItemPosition; from.setSelection(to.selectedItemPosition); to.setSelection(old)
        })
    }

fun MainActivity.helpBotTool() {
        clearPage("HelpBot Offline")
        addToolHeader("HelpBot Offline", "Chat bantuan lokal untuk memahami fungsi, permission, dan cara memakai tool MyTools. Tidak membutuhkan API/AI.", "HELP")
        val q = edit("Contoh: apa fungsi pipet warna?", false)
        content.addView(q)
        val out = label("Tanyakan fungsi atau cara memakai tool.", 14f)
        content.addView(out)

        val answers = listOf(
            Triple(listOf("pipet", "eyedropper", "warna layar", "ambil warna"), "Pipet Warna / Screen Eyedropper", "Mengambil warna langsung dari layar. Output utama: HEX dan RGB. Fitur ini memakai izin MediaProjection karena Android meminta persetujuan sebelum aplikasi membaca isi layar."),
            Triple(listOf("wifi", "wi-fi", "wlan"), "Wi-Fi Info", "Menampilkan informasi jaringan Wi-Fi yang tersedia bagi aplikasi. Pada Android modern, beberapa operasi Wi-Fi membutuhkan izin Nearby Wi-Fi dan/atau lokasi tergantung API yang digunakan."),
            Triple(listOf("hash", "sha256", "checksum"), "Hash / Checksum", "Menghasilkan sidik jari data seperti SHA-256. Berguna untuk memverifikasi apakah file yang diterima sama dengan file sumber."),
            Triple(listOf("apk analyzer", "apk", "aplikasi analyzer"), "APK Analyzer", "Membaca metadata APK seperti package, versi, SDK, permission, sertifikat, dan komponen yang dapat membantu pemeriksaan teknis."),
            Triple(listOf("encrypt", "enkripsi", "file encryption"), "File Encryption", "Mengenkripsi file agar isi tidak mudah dibaca tanpa kunci. Jangan menghapus file asli sebelum memastikan hasil enkripsi dapat dibuka kembali."),
            Triple(listOf("totp", "2fa", "otp"), "2FA Manager", "Membuat kode OTP berbasis waktu untuk akun yang mendukung TOTP. Secret harus dijaga seperti password."),
            Triple(listOf("server", "hosting", "wifi hosting"), "HTTP Server / HTML Hosting", "Membuat server lokal di jaringan perangkat. Gunakan hanya pada jaringan yang dipercaya dan hentikan server setelah selesai."),
            Triple(listOf("url safety", "url", "phishing"), "URL Safety Checker", "Memeriksa beberapa indikator heuristik seperti HTTPS, punycode, userinfo, dan pola URL. Hasil 'tidak ada indikator' bukan jaminan bahwa situs aman."),
            Triple(listOf("network scanner", "scanner jaringan", "lan scanner"), "Network Scanner", "Mendeteksi host/port pada jaringan yang sedang digunakan. Gunakan hanya pada jaringan/perangkat yang kamu miliki atau punya izin untuk diuji."),
            Triple(listOf("help", "bantuan", "cara", "fungsi"), "HelpBot", "Saya bisa menjelaskan fungsi tool, permission yang dibutuhkan, contoh penggunaan, dan masalah umum secara offline." )
        )

        fun answer(raw: String): String {
            val text = raw.trim().lowercase(Locale.getDefault())
            if (text.isBlank()) return "Tulis pertanyaan terlebih dahulu."
            val hit = answers.firstOrNull { row -> row.first.any { key -> text.contains(key) } }
            return if (hit != null) "${hit.second}\n\n${hit.third}" else "Tool belum cocok dengan pertanyaan itu. Coba sebut nama tool, misalnya: pipet warna, APK Analyzer, hash, Wi-Fi, TOTP, enkripsi, atau Network Scanner."
        }

        content.addView(button("Tanya") { out.text = answer(q.text.toString()) })
        content.addView(button("Apa fungsi MyTools?") { out.text = "MyTools adalah kumpulan utility untuk file, jaringan, developer, keamanan, warna, perangkat, dan produktivitas. HelpBot menjelaskan fungsi tool secara offline." })
        content.addView(button("Cara aman memakai Security Tools") { out.text = "Gunakan tool jaringan hanya pada jaringan yang kamu miliki/izinkan. Jangan membagikan password, token, secret TOTP, atau API key. Untuk server lokal, hentikan server setelah selesai." })
    }
