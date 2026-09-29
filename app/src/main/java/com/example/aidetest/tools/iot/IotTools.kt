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


fun MainActivity.espTools() {
        clearPage("ESP Tools")
        content.addView(label("ESP Tools", 22f, true))
        content.addView(subLabel("Toolkit untuk ESP32 / ESP8266: hitung nilai, cek pin, dan siapkan parameter proyek.", 12f))

        sectionTitle("Hardware")
        content.addView(button("GPIO Reference") {
            output("ESP32 GPIO reference:\n\nGPIO 0  • Boot/strapping\nGPIO 1  • UART0 TX\nGPIO 3  • UART0 RX\nGPIO 6–11 • Umumnya terhubung flash internal — hindari\nGPIO 34–39 • Input only\n\nCatatan: fungsi pin dapat berbeda menurut board. Periksa pinout board sebelum memasang hardware.")
        })
        content.addView(button("Pinout ESP32 / ESP8266") {
            output("ESP32 umum: GPIO0–39 (beberapa GPIO tidak tersedia pada semua board).\nESP8266 NodeMCU: D0=GPIO16, D1=GPIO5, D2=GPIO4, D3=GPIO0, D4=GPIO2, D5=GPIO14, D6=GPIO12, D7=GPIO13, D8=GPIO15.\n\nBoot pins dan pin flash memiliki batasan khusus.")
        })
        content.addView(button("LED Resistor Calculator") {
            espLedResistorCalculator()
        })
        content.addView(button("💡 LED Canvas + Animation Studio") {
            espLedStudio()
        })
        content.addView(button("Voltage Divider Calculator") {
            openTool("dividercalc")
        })

        sectionTitle("ADC / PWM")
        content.addView(button("ADC → Voltage") {
            espAdcCalculator()
        })
        content.addView(button("PWM / Duty Cycle") {
            openTool("pwmcalc")
        })

        sectionTitle("Serial / Network")
        content.addView(button("UART / Serial Settings") {
            output("Baud rate umum:\n9600 • 19200 • 38400 • 57600 • 115200\n\nFormat umum: 8 data bit, No parity, 1 stop bit (8N1).\n\nPastikan baud rate ESP dan perangkat lawan sama.")
        })
        content.addView(button("Wi-Fi Info") {
            openTool("wifi")
        })
        content.addView(button("Power / Current Helper") {
            openTool("powercalc")
        })

        sectionTitle("ESP Control & Network")
        content.addView(button("🔎 Auto-Discovery ESP (mDNS/NSD)") { espAutoDiscovery() })
        content.addView(button("📡 Device Manager") { espDeviceManager() })
        content.addView(button("🎛 GPIO Controller") { espGpioController() })
        content.addView(button("📊 Sensor Dashboard") { espSensorDashboard() })
        content.addView(button("📶 Wi-Fi Manager") { espWifiManager() })
        content.addView(button("🔄 OTA Firmware") { espOtaFirmware() })
        content.addView(button("🌐 HTTP / API Tester") { espHttpApiTester() })
        content.addView(button("📬 MQTT Client") { espMqttClient() })
        content.addView(button("🔌 USB / OTG Info") { espUsbInfo() })
        content.addView(button("🖥 TCP Serial Monitor") { espTcpSerialMonitor() })

        sectionTitle("Quick Notes")
        val notes = listOf(
            "⚠ 3.3V logic: jangan langsung memberi 5V ke GPIO ESP32.",
            "⚠ GPIO 34–39 pada ESP32 klasik adalah input-only.",
            "⚠ Hindari GPIO strapping saat boot jika rangkaian eksternal mengubah levelnya.",
            "✓ Gunakan resistor seri untuk LED dan pembagi tegangan untuk input analog yang melebihi batas ADC."
        )
        notes.forEach { content.addView(subLabel(it, 13f).apply { setPadding(dp(6), dp(5), dp(6), dp(5)) }) }
    }

fun MainActivity.espAutoDiscovery() {
        clearPage("ESP Auto Discovery")
        addToolHeader("ESP Auto Discovery", "Temukan perangkat ESP yang mengiklankan layanan mDNS di jaringan lokal.", "⌁")
        content.addView(toolSection("DISCOVERY", "Hasil perangkat akan muncul di bawah."))
        val result = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(result)
        val nsd = getSystemService(Context.NSD_SERVICE) as NsdManager
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) { runOnUiThread { result.addView(subLabel("Memindai $serviceType …", 12f)) } }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        runOnUiThread {
                            val host = info.host?.hostAddress ?: "?"
                            result.addView(label("${info.serviceName} • $host:${info.port}", 13f, true))
                        }
                    }
                })
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { runOnUiThread { result.addView(subLabel("Discovery gagal: $errorCode", 12f)) } }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        nsdDiscoveryManager = nsd
        nsdDiscoveryListener = listener
        content.addView(button("Mulai scan HTTP") { runCatching { nsd.discoverServices("_http._tcp.", NsdManager.PROTOCOL_DNS_SD, listener) }.onFailure { toast("NSD tidak tersedia: ${it.message}") } })
        content.addView(subLabel("ESP yang menjalankan mDNS/Bonjour HTTP dapat muncul otomatis. Perangkat tanpa mDNS tetap dapat dimasukkan melalui Device Manager.", 11f))
    }

fun MainActivity.espLedStudio() {
        stopLedPlayback()
        clearPage("ESP LED Studio")
        content.addView(label("ESP LED Studio", 22f, true))
        content.addView(subLabel("Buat pola LED, pilih susunan, atur jarak dan animasi, lalu kirim langsung ke ESP32.", 12f))

        // Jumlah LED — kontrol +/− lebih cepat daripada spinner.
        val countCard = ledSectionCard("Jumlah LED", "Maksimum 50 LED", "💡")
        val countRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        val minus = Button(this).apply {
            text = "−"; textSize = 22f; setTextColor(textMain); background = bg(panel, 14, line); setStateListAnimator(null)
            setOnClickListener { setLedCount(ledCount - 1) }
        }
        ledCountLabel = TextView(this).apply {
            text = ledCount.toString(); textSize = 22f; gravity = Gravity.CENTER; setTextColor(textMain)
        }
        val plus = Button(this).apply {
            text = "+"; textSize = 22f; setTextColor(textMain); background = bg(panel, 14, line); setStateListAnimator(null)
            setOnClickListener { setLedCount(ledCount + 1) }
        }
        countRow.addView(minus, LinearLayout.LayoutParams(dp(52), dp(48)))
        countRow.addView(ledCountLabel, LinearLayout.LayoutParams(0, dp(48), 1f))
        countRow.addView(plus, LinearLayout.LayoutParams(dp(52), dp(48)))
        countCard.addView(countRow)
        content.addView(countCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        // Bentuk/susunan tidak lagi mengambil ruang besar. Tap kartu untuk membuka pilihan.
        val layoutCard = ledSectionCard("Bentuk / Susunan LED", "Tap untuk memilih pola susunan", "▦")
        ledLayoutLabel = TextView(this).apply {
            text = "Grid"
            textSize = 14f
            setTextColor(textMain)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            background = bg(panel, 13, line)
        }
        val layoutRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
            addView(ledLayoutLabel, LinearLayout.LayoutParams(0, dp(48), 1f))
            addView(TextView(this@MainActivity).apply {
                text = "›"; textSize = 28f; gravity = Gravity.CENTER; setTextColor(textMuted)
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            setOnClickListener { showLedLayoutPicker() }
        }
        layoutCard.addView(layoutRow)
        content.addView(layoutCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        // Preview utama.
        val previewCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
            background = bg(panel2, 18, line)
        }
        val previewTitle = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        previewTitle.addView(label("Preview LED", 15f, true), LinearLayout.LayoutParams(0, dp(38), 1f))
        previewTitle.addView(TextView(this).apply {
            text = "LIVE"; textSize = 11f; gravity = Gravity.CENTER; setTextColor(textMain); background = bg(panel, 12, line)
            setPadding(dp(12), 0, dp(12), 0)
        }, LinearLayout.LayoutParams(dp(64), dp(34)))
        previewCard.addView(previewTitle)
        ledCanvas = LedCanvasView(this).apply {
            setLedConfig(ledCount, ledLayout)
            onLedClicked = { index ->
                val frame = ledFrames.getOrNull(ledFrameIndex)
                if (frame != null && index in frame.states.indices) {
                    frame.states[index] = !frame.states[index]
                    setStates(frame.states)
                    updateLedFrameInfo()
                    renderLedFrames()
                }
            }
        }
        previewCard.addView(ledCanvas, LinearLayout.LayoutParams(-1, dp(330)).apply { topMargin = dp(6) })
        content.addView(previewCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        // Jarak LED.
        val gapCard = ledSectionCard("Jarak antar LED", "0 dp = paling rapat", "↔")
        ledGapSeek = SeekBar(this).apply {
            max = 20; progress = ledGapDp
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    ledGapDp = progress; ledCanvas?.setLedGap(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        gapCard.addView(ledGapSeek, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(8) })
        content.addView(gapCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        // Pattern.
        val patternCard = ledSectionCard("Pattern", "Simpan pola dan gunakan lagi kapan saja", "◉")
        ledNameEdit = edit("Nama pattern, contoh LOVE")
        patternCard.addView(ledNameEdit, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) })
        ledFrameInfo = label("Frame 1 / 1 • 0/${ledCount} LED menyala", 12f, true)
        patternCard.addView(ledFrameInfo, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        content.addView(patternCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        // Timeline frame dan durasi.
        val animCard = ledSectionCard("Animasi", "Buat beberapa frame dan atur kecepatan", "◷")
        val frameActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        frameActions.addView(button("+ Frame") { addLedFrame() }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(4) })
        frameActions.addView(button("Duplikat") { duplicateLedFrame() }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(4); rightMargin = dp(4) })
        frameActions.addView(button("Hapus") { deleteLedFrame() }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(4) })
        animCard.addView(frameActions)
        ledFrameStrip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val frameScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(ledFrameStrip)
        }
        animCard.addView(frameScroll, LinearLayout.LayoutParams(-1, dp(62)).apply { topMargin = dp(6) })
        ledSpeedInfo = subLabel("Durasi frame: 300 ms", 11f)
        animCard.addView(ledSpeedInfo)
        ledSpeedSeek = SeekBar(this).apply {
            max = 1950; progress = 250
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val ms = (progress + 50).toLong()
                    ledFrames.getOrNull(ledFrameIndex)?.durationMs = ms
                    ledSpeedInfo?.text = "Durasi frame: ${ms} ms"
                    updateLedFrameInfo()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        animCard.addView(ledSpeedSeek, LinearLayout.LayoutParams(-1, dp(42)))
        val playRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        playRow.addView(button("▶ Putar") { playLedAnimation() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(4) })
        playRow.addView(button("■ Stop") { stopLedPlayback(); updateLedFrameInfo() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(4) })
        animCard.addView(playRow)
        content.addView(animCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        // Simpan / reset.
        val saveRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        saveRow.addView(button("Simpan Pattern") { saveLedPattern() }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { rightMargin = dp(4) })
        saveRow.addView(button("Reset") { resetLedFrames(); renderLedFrames() }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { leftMargin = dp(4) })
        content.addView(saveRow)

        content.addView(label("Pattern tersimpan", 15f, true).apply { setPadding(0, dp(14), 0, dp(6)) })
        renderSavedLedPatterns()

        // Upload tetap pada halaman yang sama.
        val uploadTitle = label("Upload ke ESP32", 15f, true).apply {
            setPadding(0, dp(14), 0, dp(5)); tag = "led_upload_title"
        }
        content.addView(uploadTitle)
        content.addView(subLabel("Endpoint HTTP POST ESP32. Contoh: http://192.168.4.1/api/led/pattern", 11f))
        ledEndpointEdit = edit("URL endpoint ESP32")
        ledEndpointEdit?.setText(prefs.getString("led_endpoint", "http://192.168.4.1/api/led/pattern") ?: "")
        content.addView(ledEndpointEdit)
        content.addView(button("Upload Pattern") { uploadLedPattern() })
        content.addView(button("Salin JSON Pattern") { copyText(buildLedPatternJson().toString(2)) })

        resetLedFrames()
        renderLedFrames()
    }

fun MainActivity.espDeviceManager() {
        clearPage("ESP Device Manager")
        addToolHeader("ESP Device Manager", "Simpan endpoint ESP dan cek status perangkat dari satu panel.", "ESP")
        content.addView(toolSection("DEVICE"))
        val base = espBaseUrlField(); content.addView(base)
        val info = label("Belum dicek", 13f); info.setPadding(dp(10), dp(12), dp(10), dp(12)); info.background = bg(panel2, 14, line); content.addView(info)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button("🔎 CHECK STATUS") {
            val url = normalizeEspUrl(base.text.toString()) + "/status"
            prefs.edit().putString("esp_base_url", normalizeEspUrl(base.text.toString())).apply()
            info.text = "Menghubungkan…"
            thread {
                val r = runCatching { httpRequest("GET", url) }.getOrElse { -1 to (it.message ?: "error") }
                runOnUiThread {
                    info.text = if (r.first in 200..299) formatEspJson(r.second) else "HTTP ${r.first}\n${r.second.ifBlank { "Tidak ada response" }}"
                }
            }
        }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { rightMargin = dp(4) })
        row.addView(button("COPY URL") { copyText(normalizeEspUrl(base.text.toString())) }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { leftMargin = dp(4) })
        content.addView(row)
        content.addView(button("GET /health") { espSimpleGet(base.text.toString(), "/health") })
        content.addView(button("GET /info") { espSimpleGet(base.text.toString(), "/info") })
        content.addView(subLabel("ESP dapat mengembalikan JSON seperti {\"chip\":\"ESP32\",\"ip\":\"192.168.4.1\",\"rssi\":-48,\"uptime\":1234,\"free_heap\":200000,\"firmware\":\"1.0.0\"}.", 11f))
    }

fun MainActivity.espGpioController() {
        clearPage("ESP GPIO Controller")
        addToolHeader("ESP GPIO Controller", "Kontrol pin ESP dengan panel HIGH/LOW yang lebih jelas.", "GPIO")
        content.addView(toolSection("CONNECTION"))
        val base = espBaseUrlField(); content.addView(base)
        val pinEdit = edit("GPIO, contoh 2"); pinEdit.setText("2"); content.addView(pinEdit)
        content.addView(toolSection("MODE & PWM"))
        val mode = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("OUTPUT", "INPUT", "PWM")) }
        content.addView(mode, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(8) })
        val pwm = edit("PWM duty 0-255 (untuk PWM)"); pwm.setText("128"); content.addView(pwm)
        val state = toolStatus("Siap"); content.addView(state)
        content.addView(toolSection("ACTIONS"))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button("HIGH / ON") { sendGpio(base.text.toString(), pinEdit.text.toString(), "HIGH", mode.selectedItem.toString(), pwm.text.toString(), state) }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { rightMargin = dp(4) })
        row.addView(button("LOW / OFF") { sendGpio(base.text.toString(), pinEdit.text.toString(), "LOW", mode.selectedItem.toString(), pwm.text.toString(), state) }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { leftMargin = dp(4) })
        content.addView(row)
        content.addView(button("READ GPIO STATUS") { espSimpleGet(base.text.toString(), "/gpio") })
        content.addView(subLabel("Request JSON: {gpio:2, mode:\"OUTPUT\", state:\"HIGH\", pwm:128}", 11f))
    }

fun MainActivity.espSensorDashboard() {
        clearPage("ESP Sensor Dashboard")
        addToolHeader("ESP Sensor Dashboard", "Pantau data sensor ESP secara berkala tanpa mengubah tema aplikasi.", "◌")
        content.addView(toolSection("DEVICE"))
        val base = espBaseUrlField(); content.addView(base)
        val box = label("Belum ada data", 14f); box.setPadding(dp(14), dp(16), dp(14), dp(16)); box.background = bg(panel2, 16, line); content.addView(box)
        content.addView(toolSection("POLLING"))
        val interval = edit("Interval polling (ms)"); interval.setText("1000"); content.addView(interval)
        content.addView(button("▶ START / REFRESH") {
            val delay = (interval.text.toString().toLongOrNull() ?: 1000L).coerceIn(250L, 60000L)
            startEspSensorPolling(base, box, delay)
        })
        content.addView(button("■ STOP") { stopEspSensorPolling() })
        content.addView(subLabel("Gunakan minimal 250 ms agar ESP tidak dibanjiri request.", 11f))
    }

fun MainActivity.espWifiManager() {
        clearPage("ESP Wi-Fi Manager")
        addToolHeader("ESP Wi-Fi Manager", "Kelola konfigurasi Wi-Fi ESP dengan status koneksi yang jelas.", "WiFi")
        content.addView(toolSection("CONNECTION"))
        val base = espBaseUrlField(); content.addView(base)
        val ssid = edit("SSID Wi-Fi"); content.addView(ssid)
        val pass = edit("Password Wi-Fi"); pass.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; content.addView(pass)
        val result = label("Siap", 13f); content.addView(result)
        content.addView(button("📶 SEND WI-FI CONFIG") {
            if (ssid.text.toString().isBlank()) { toast("SSID kosong"); return@button }
            val json = JSONObject().apply { put("ssid", ssid.text.toString()); put("password", pass.text.toString()) }
            result.text = "Mengirim…"
            thread {
                val r = runCatching { httpRequest("POST", normalizeEspUrl(base.text.toString()) + "/wifi/config", json.toString()) }.getOrElse { -1 to (it.message ?: "error") }
                runOnUiThread { result.text = "HTTP ${r.first}\n${r.second.ifBlank { "OK" }}" }
            }
        })
        content.addView(button("GET CURRENT STATUS") { espSimpleGet(base.text.toString(), "/wifi/status") })
    }

fun MainActivity.espOtaFirmware() {
        clearPage("ESP OTA Firmware")
        content.addView(label("ESP OTA Firmware", 22f, true))
        content.addView(subLabel("Pilih firmware .bin lalu upload sebagai raw application/octet-stream ke endpoint OTA. Default /update.", 12f))
        val base = espBaseUrlField(); content.addView(base)
        val endpoint = edit("OTA endpoint path, contoh /update"); endpoint.setText("/update"); content.addView(endpoint)
        val status = label("Belum ada firmware", 13f); content.addView(status)
        content.addView(button("📦 PILIH .BIN & UPLOAD") {
            pendingOtaEndpoint = normalizeEspUrl(base.text.toString()) + (endpoint.text.toString().trim().let { if (it.startsWith("/")) it else "/$it" })
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "application/octet-stream"; addCategory(Intent.CATEGORY_OPENABLE) }, 1030)
            status.text = "Menunggu file…"
        })
        content.addView(button("GET /version") { espSimpleGet(base.text.toString(), "/version") })
        content.addView(subLabel("Implementasi ini memakai POST raw. Endpoint ESP harus menerima body binary dan melakukan validasi firmware sebelum reboot.", 11f))
    }

fun MainActivity.espHttpApiTester() {
        clearPage("ESP HTTP/API Tester")
        addToolHeader("ESP HTTP / API Tester", "Uji endpoint ESP dengan method, body, dan response dalam satu workspace.", "API")
        content.addView(toolSection("REQUEST"))
        val method = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("GET", "POST", "PUT", "DELETE", "PATCH")) }
        content.addView(method, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(8) })
        val url = edit("http://192.168.4.1/status"); content.addView(url)
        val body = edit("JSON body (opsional)", true); content.addView(body)
        content.addView(button("SEND REQUEST") {
            val u = url.text.toString().trim(); if (u.isBlank()) { toast("URL kosong"); return@button }
            thread {
                val start = System.currentTimeMillis()
                val r = runCatching { httpRequest(method.selectedItem.toString(), u, body.text.toString().takeIf { it.isNotBlank() }) }.getOrElse { -1 to (it.message ?: "error") }
                val ms = System.currentTimeMillis() - start
                runOnUiThread { output("${method.selectedItem} $u\nHTTP ${r.first}\nResponse time: ${ms} ms\n\n${r.second}") }
            }
        })
    }

fun MainActivity.espMqttClient() {
        clearPage("ESP MQTT Client")
        addToolHeader("ESP MQTT Client", "Publish atau subscribe pesan MQTT dengan panel koneksi yang ringkas.", "MQ")
        content.addView(toolSection("BROKER"))
        val host = edit("Broker host, contoh 192.168.1.10"); content.addView(host)
        val port = edit("Port"); port.setText("1883"); content.addView(port)
        val clientId = edit("Client ID"); clientId.setText("MyTools-" + (System.currentTimeMillis() % 100000)); content.addView(clientId)
        val topic = edit("Topic, contoh esp32/led"); content.addView(topic)
        val message = edit("Message"); content.addView(message)
        val result = label("Disconnected", 13f); content.addView(result)
        content.addView(button("PUBLISH") {
            val h = host.text.toString().trim(); val p = port.text.toString().toIntOrNull() ?: 1883
            thread {
                val r = runCatching { mqttPublish(h, p, clientId.text.toString(), topic.text.toString(), message.text.toString()) }.getOrElse { it.message ?: "MQTT error" }
                runOnUiThread { result.text = r }
            }
        })
        content.addView(button("SUBSCRIBE (5 detik)") {
            val h = host.text.toString().trim(); val p = port.text.toString().toIntOrNull() ?: 1883
            thread {
                val r = runCatching { mqttSubscribe(h, p, clientId.text.toString(), topic.text.toString()) }.getOrElse { it.message ?: "MQTT error" }
                runOnUiThread { output(r) }
            }
        })
        content.addView(subLabel("Broker tanpa TLS/auth didukung pada mode dasar ini. Jangan mengirim kredensial sensitif melalui jaringan terbuka.", 11f))
    }

fun MainActivity.espUsbInfo() {
        clearPage("ESP USB / OTG Info")
        content.addView(label("ESP USB / OTG Info", 22f, true))
        content.addView(subLabel("Membaca perangkat USB yang terdeteksi Android. Untuk serial USB, Android harus mengizinkan akses perangkat terlebih dahulu.", 12f))
        val usb = getSystemService(Context.USB_SERVICE) as android.hardware.usb.UsbManager
        val devices = usb.deviceList.values.toList()
        if (devices.isEmpty()) content.addView(subLabel("Tidak ada perangkat USB yang terdeteksi. Sambungkan ESP melalui OTG.", 13f))
        devices.forEach { d ->
            val info = "${d.deviceName}\nVID: ${d.vendorId} • PID: ${d.productId}\nInterfaces: ${d.interfaceCount}\nClass: ${d.deviceClass}"
            content.addView(label(info, 13f).apply { setPadding(dp(12), dp(12), dp(12), dp(12)); background = bg(panel2, 14, line) })
        }
        content.addView(button("REFRESH") { espUsbInfo() })
    }

fun MainActivity.espTcpSerialMonitor() {
        clearPage("ESP TCP Serial Monitor")
        content.addView(label("ESP TCP Serial Monitor", 22f, true))
        content.addView(subLabel("Monitor serial yang diekspos ESP sebagai TCP socket, misalnya firmware bridge di port 23/3333. Ini bukan driver USB serial.", 12f))
        val host = edit("ESP IP / host"); content.addView(host)
        val port = edit("TCP port"); port.setText("3333"); content.addView(port)
        val command = edit("Kirim command (opsional)"); content.addView(command)
        val log = edit("Log", true); log.isEnabled = false; content.addView(log)
        content.addView(button("CONNECT / READ 5 DETIK") {
            val h = host.text.toString().trim(); val p = port.text.toString().toIntOrNull() ?: 3333
            thread {
                val r = runCatching {
                    Socket(h, p).use { socket ->
                        socket.soTimeout = 1000
                        if (command.text.toString().isNotBlank()) socket.getOutputStream().apply { write((command.text.toString() + "\n").toByteArray(StandardCharsets.UTF_8)); flush() }
                        val start = System.currentTimeMillis(); val bytes = ByteArrayOutputStream(); val buf = ByteArray(1024)
                        while (System.currentTimeMillis() - start < 5000) { try { val n = socket.getInputStream().read(buf); if (n > 0) bytes.write(buf, 0, n) } catch (_: SocketTimeoutException) {} }
                        bytes.toString("UTF-8")
                    }
                }.getOrElse { "Serial TCP error: ${it.message}" }
                runOnUiThread { log.isEnabled = true; log.setText(r.ifBlank { "Tidak ada data selama 5 detik." }); log.isEnabled = false }
            }
        })
    }

fun MainActivity.modularIotDashboard() {
        // Kunci landscape sebelum membangun canvas agar tidak sempat kembali ke Home
        // saat Activity menerima perubahan orientasi.
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        clearPage("IoT Dynamic Topology")
        action.text = "+"
        action.textSize = 28f
        action.setOnClickListener { showStudioWidgetPicker() }
        title.text = "IOT STUDIO"
        content.setBackgroundColor(Color.BLACK)
        content.setPadding(0, 0, 0, 0)
        scroll.isFillViewport = true
        scroll.isVerticalScrollBarEnabled = false
        content.layoutParams = content.layoutParams.apply { height = ViewGroup.LayoutParams.MATCH_PARENT }

        studioWidgets = loadStudioWidgets()
        studioLinks = loadStudioLinks()
        studioSelectedLinkId = null
        studioCanvas = StudioCanvasView(this)
        studioCanvas?.setBackgroundColor(Color.BLACK)
        content.removeAllViews()
        content.addView(studioCanvas, LinearLayout.LayoutParams(-1, -1))
        studioCanvas?.setLinks(studioLinks)
        studioCanvas?.setWidgets(studioWidgets)
    }
