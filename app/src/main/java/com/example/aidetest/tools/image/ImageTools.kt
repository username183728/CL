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


fun MainActivity.colorTool() {
        clearPage("Color Tools")
        content.setPadding(dp(12), dp(8), dp(12), dp(16))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(8))
        }
        header.addView(label("Color Tools", 24f, true))
        header.addView(subLabel("Pilih warna, ekstrak palet dari foto, atau ambil warna langsung dari layar.", 12f))
        content.addView(header)

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = bg(Color.rgb(244, 246, 248), 18, Color.rgb(226, 230, 234))
        }
        val tabPhoto = colorTab("Foto", true)
        val tabPicker = colorTab("Pipet Layar", false)
        val tabConvert = colorTab("Converter", false)
        tabs.addView(tabPhoto, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(3) })
        tabs.addView(tabPicker, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(3) })
        tabs.addView(tabConvert, LinearLayout.LayoutParams(0, dp(44), 1f))
        content.addView(tabs, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        val workspace = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(workspace, LinearLayout.LayoutParams(-1, -2))

        fun selectTab(selected: Int) {
            listOf(tabPhoto, tabPicker, tabConvert).forEachIndexed { i, v ->
                val active = i == selected
                v.setTextColor(if (active) Color.WHITE else textMain)
                v.background = bg(if (active) Color.rgb(15, 15, 16) else Color.TRANSPARENT, 14)
            }
            workspace.removeAllViews()
            when (selected) {
                0 -> buildPhotoColorWorkspace(workspace)
                1 -> buildScreenPickerWorkspace(workspace)
                else -> buildColorConverterWorkspace(workspace)
            }
        }
        tabPhoto.setOnClickListener { selectTab(0) }
        tabPicker.setOnClickListener { selectTab(1) }
        tabConvert.setOnClickListener { selectTab(2) }
        selectTab(0)
    }

fun MainActivity.uiColorPickerTool() {
        clearPage("UI Color")
        content.addView(label("Pipet Warna Layar", 24f, true))
        content.addView(subLabel("Ambil warna langsung dari layar, termasuk saat membuka aplikasi lain.", 12f))

        val preview = FrameLayout(this).apply {
            background = bg(Color.rgb(235, 238, 242), 24, Color.rgb(220, 225, 230))
        }
        val swatch = View(this).apply { background = bg(Color.rgb(90, 120, 220), 22) }
        val marker = TextView(this).apply {
            text = "•"
            gravity = Gravity.CENTER
            textSize = 28f
            setTextColor(Color.WHITE)
            background = bg(Color.rgb(90, 120, 220), 30, Color.WHITE)
        }
        preview.addView(swatch, FrameLayout.LayoutParams(dp(92), dp(92), Gravity.CENTER))
        preview.addView(marker, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.TOP or Gravity.END).apply { topMargin = dp(16); rightMargin = dp(16) })
        content.addView(preview, LinearLayout.LayoutParams(-1, dp(190)).apply { bottomMargin = dp(12) })

        val status = label("Pipet belum aktif", 15f, true)
        status.gravity = Gravity.CENTER
        content.addView(status, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) })

        val activate = button("AKTIFKAN PIPET") { activateColorPicker() }
        activate.background = bg(Color.rgb(25, 25, 27), 16)
        activate.setTextColor(Color.WHITE)
        content.addView(activate, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(10) })

        val hex = label("HEX  —  Belum ada warna", 16f, true)
        val rgb = subLabel("RGB  —  -", 13f)
        val hsl = subLabel("HSL  —  -", 13f)
        val copy = button("SALIN HEX") {
            val value = hex.text.toString().substringAfter("HEX  —  ").trim()
            if (value.startsWith("#")) copyText(value) else toast("Belum ada warna")
        }
        copy.background = bg(panel2, 14, line)
        content.addView(hex)
        content.addView(rgb)
        content.addView(hsl)
        content.addView(copy, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })

        val note = subLabel("Cara kerja: aktifkan pipet → izinkan tangkapan layar → buka aplikasi apa pun → geser lingkaran pipet ke warna yang diinginkan → tekan tombol pipet.", 11f)
        note.setPadding(0, dp(14), 0, 0)
        content.addView(note)

        colorPickerUiUpdater = { color ->
            swatch.setBackgroundColor(color)
            marker.setTextColor(Color.WHITE)
            marker.background = bg(color, 30, Color.WHITE)
            val r = Color.red(color); val g = Color.green(color); val b = Color.blue(color)
            val hsv = FloatArray(3); Color.colorToHSV(color, hsv)
            val hslValue = rgbToHsl(r, g, b)
            val hx = "#%02X%02X%02X".format(Locale.US, r, g, b)
            hex.text = "HEX  —  $hx"
            rgb.text = "RGB  —  $r, $g, $b"
            hsl.text = "HSL  —  ${fmt(hslValue[0])}°, ${fmt(hslValue[1])}%, ${fmt(hslValue[2])}%"
            status.text = "Pipet aktif  •  $hx"
        }
    }

fun MainActivity.imageInfoTool() {
        clearPage("Image Metadata")
        addToolHeader("Image Metadata", "Baca ukuran, format dan informasi dasar gambar secara lokal.", "IMG")
        val result=label("Belum ada gambar",13f);content.addView(result)
        content.addView(button("Pilih Gambar") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},1210)
        })
        imageInfoResult = { uri ->
            thread {
                val r=runCatching{
                    val opts=BitmapFactory.Options().apply{inJustDecodeBounds=true}
                    contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,opts)}
                    "Mime: ${opts.outMimeType ?: "-"}\nUkuran: ${opts.outWidth} × ${opts.outHeight}"
                }.getOrElse{"Gagal: ${it.message}"}
                runOnUiThread{result.text=r}
            }
        }
    }

fun MainActivity.imageToolsTool() {
        clearPage("Image Resize / Compress")
        addToolHeader("Image Resize / Compress", "Ubah ukuran hingga 2048px dan kompres sebagai JPEG.", "IMG")
        val result=label("Belum ada gambar",13f);content.addView(result)
        val quality=SeekBar(this).apply{max=100;progress=80}
        content.addView(label("Kualitas JPEG",13f));content.addView(quality)
        val processImageButton = button("Pilih & Proses", {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(intent, 1211)
        })
        content.addView(processImageButton)
        imageToolsResult={uri->
            thread{
                val r=runCatching{
                    val bmp=contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it)}?:error("Gambar tidak bisa dibuka")
                    val maxSide=2048
                    val largest=if(bmp.width>=bmp.height) bmp.width else bmp.height
                    val scale=min(1f,maxSide.toFloat()/largest)
                    val outBmp=if(scale<1f) Bitmap.createScaledBitmap(bmp,(bmp.width*scale).roundToInt(),(bmp.height*scale).roundToInt(),true) else bmp
                    val dir=File(filesDir,"image_exports").apply{mkdirs()}
                    val f=File(dir,"mytools_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(f).use{outBmp.compress(Bitmap.CompressFormat.JPEG,quality.progress,it)}
                    "Tersimpan: ${f.absolutePath}\n${outBmp.width}×${outBmp.height}\nQuality ${quality.progress}%"
                }.getOrElse{"Gagal: ${it.message}"}
                runOnUiThread { result.text = r }
            }
        }
    }

fun MainActivity.ocrTool() {
        clearPage("OCR Text Scanner")
        addToolHeader("OCR Text Scanner", "Ambil teks dari gambar lalu edit, salin, atau bagikan hasilnya.", "OCR")
        content.addView(toolSection("PREVIEW"))
        val preview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = bg(panel2, 14, line)
            layoutParams = LinearLayout.LayoutParams(-1, dp(230)).apply { bottomMargin = dp(8) }
        }
        content.addView(preview)
        val resultBox = edit("Hasil OCR", true)
        content.addView(resultBox)
        content.addView(button("Pilih Gambar") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE)
            }, 1020)
        })
        content.addView(button("OCR Gambar Terpilih") {
            val uri = pendingOcrUri
            if (uri == null) { toast("Pilih gambar terlebih dahulu"); return@button }
            runOcr(uri) { text ->
                resultBox.setText(text)
                if (text.isBlank()) toast("Tidak ada teks yang terdeteksi")
            }
        })
        // Keep a lightweight callback reference for the ActivityResult handler.
        pendingOcrView = resultBox
        pendingOcrPreview = preview
    }

fun MainActivity.qrTool() {
        clearPage("QR Scanner")
        qrSourceExpanded = false
        qrSelectedSource = null
        qrPickedUri = null
        qrPickedName = null
        qrScanBusy = false
        qrScanResult = null
        renderQrScanner()
    }
