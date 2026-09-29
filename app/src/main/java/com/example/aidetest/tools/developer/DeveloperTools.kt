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


fun MainActivity.editor(file: File?, forcedMode: String? = null) {
        editorLanding = file == null && forcedMode == null && editorExternalTarget == null
        clearPage("Editor")
        editorFile = file
        file?.let { recordRecentFile(it) }
        editorMode = forcedMode ?: detectEditorMode(file?.name)
        if (editorLanding) renderEditorHome() else renderEditorPage()
    }

fun MainActivity.githubZipTool() {
        clearPage("GitHub Publisher")
        ghUserValue = prefs.getString("gh_user", null) ?: "username183728"
        ghRepoValue = prefs.getString("gh_repo", null) ?: "B1"
        ghBranch = prefs.getString("gh_branch", null) ?: "main"
        ghSaveToken = prefs.getBoolean("gh_save_token", true)
        ghPrivateRepo = prefs.getBoolean("gh_private", true)
        ghTokenValue = if (ghSaveToken) prefs.getString("gh_token_enc", null)?.let { GithubTokenVault.decrypt(it) }.orEmpty() else ""
        ghCommitValue = prefs.getString("gh_commit", null) ?: "Upload project via GITLS"
        ghStage = FrameLayout(this)
        content.addView(ghStage, LinearLayout.LayoutParams(-1, -2))
        ghShow(ghSettingsScreen(), "Pengaturan GitHub")
    }

fun MainActivity.restApiClientTool() {
        clearPage("REST / API Client")
        toolWorkspace("REST / API Client", "Kirim request HTTP dan periksa status, header, serta body respons.", "api")
        toolWorkspaceSection("REQUEST", "Tentukan method dan endpoint terlebih dahulu.")
        val method = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("GET","POST","PUT","PATCH","DELETE","HEAD")) }
        val url = edit("https://example.com/api")
        val headers = edit("Headers (satu per baris: Name: Value)")
        headers.minLines = 3
        val body = edit("Request body (JSON/text)")
        body.minLines = 5
        content.addView(method); content.addView(url); content.addView(headers); content.addView(body)
        val status = toolStatus("Siap", false); content.addView(status)
        val send = button("Kirim Request") {}
        content.addView(send)
        send.setOnClickListener {
            val target = url.text.toString().trim()
            if (target.isBlank()) { toast("URL wajib diisi"); return@setOnClickListener }
            send.isEnabled = false; status.text = "Mengirim…"
            thread {
                val result = runCatching {
                    val conn = URL(target).openConnection() as HttpURLConnection
                    conn.requestMethod = method.selectedItem.toString()
                    conn.connectTimeout = 12000; conn.readTimeout = 15000
                    conn.instanceFollowRedirects = true
                    headers.text.toString().lines().forEach { line ->
                        val i = line.indexOf(':')
                        if (i > 0) conn.setRequestProperty(line.substring(0,i).trim(), line.substring(i+1).trim())
                    }
                    val m = conn.requestMethod
                    if (m in setOf("POST","PUT","PATCH","DELETE")) {
                        conn.doOutput = true
                        conn.outputStream.use { it.write(body.text.toString().toByteArray(StandardCharsets.UTF_8)) }
                    }
                    val code = conn.responseCode
                    val stream = if (code >= 400) conn.errorStream else conn.inputStream
                    val responseBody = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""
                    val hs = conn.headerFields.entries.filter { it.key != null }.joinToString("\n") { (k,v) -> "$k: ${v?.joinToString("; ") ?: ""}" }
                    conn.disconnect()
                    "HTTP $code\n\nHeaders:\n$hs\n\nBody:\n${responseBody.take(50000)}"
                }.getOrElse { "Request gagal: ${it.javaClass.simpleName}: ${it.message ?: "unknown error"}" }
                runOnUiThread { send.isEnabled = true; status.text = if (result.startsWith("Request gagal")) "Gagal" else "Selesai"; output(result) }
            }
        }
    }

fun MainActivity.webSocketClientTool() {
        clearPage("WebSocket Client")
        toolWorkspace("WebSocket Client", "Hubungkan endpoint WebSocket, kirim pesan, terima frame, dan pantau log.", "connection")
        toolWorkspaceSection("CONNECTION", "Gunakan ws:// atau wss:// lalu kontrol koneksi dari bawah.")
        val url = edit("ws://echo.websocket.events")
        val message = edit("Pesan")
        val log = edit("Log")
        log.isEnabled = false; log.minLines = 8; log.setTextIsSelectable(true)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val connect = button("Connect") {}; val send = button("Send") {}; val receive = button("Receive") {}; val close = button("Close") {}
        row.addView(connect, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin=dp(3) })
        row.addView(send, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin=dp(2); rightMargin=dp(2) })
        row.addView(receive, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin=dp(2); rightMargin=dp(2) })
        row.addView(close, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin=dp(2) })
        content.addView(url); content.addView(message); content.addView(row); content.addView(log)
        fun appendLog(t:String) { log.append((if(log.text.isNotEmpty()) "\n" else "") + t) }
        connect.setOnClickListener {
            val raw=url.text.toString().trim(); if(!raw.startsWith("ws://") && !raw.startsWith("wss://")){toast("Gunakan ws:// atau wss://");return@setOnClickListener}
            connect.isEnabled=false
            thread {
                val r=runCatching{ openWebSocket(raw) }.getOrElse{"ERROR: ${it.message}"}
                runOnUiThread{appendLog(r); connect.isEnabled=true}
            }
        }
        send.setOnClickListener {
            val msg=message.text.toString(); if(msg.isBlank()){toast("Pesan kosong");return@setOnClickListener}
            thread{val r=runCatching{writeWsText(msg); "TX: $msg"}.getOrElse{"ERROR: ${it.message}"};runOnUiThread{appendLog(r)}}
        }
        receive.setOnClickListener { thread { val r=runCatching{readWsText()}.getOrElse{"ERROR: ${it.message}"}; runOnUiThread{appendLog(if(r.startsWith("ERROR")) r else "RX: $r")} } }
        close.setOnClickListener { thread { runCatching{closeWebSocket()}; runOnUiThread{appendLog("Closed")} } }
    }

fun MainActivity.webProjectBuilder() {
        clearPage("Web Project Builder")
        content.setPadding(dp(12), dp(8), dp(12), dp(18))

        fun sectionTitle(textValue: String, icon: String): View = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(12), dp(4), dp(8))
            addView(MdiIconView(this@MainActivity).apply {
                setIconName(icon); setIconSize(20f); setTextColor(textMain)
                layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(6) }
            })
            addView(TextView(this@MainActivity).apply {
                text = textValue; textSize = 13f; setTextColor(textMain)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
        }

        val intro = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = bg(if (isDarkTheme) panel else Color.rgb(246,248,250), 18, if (isDarkTheme) line else Color.rgb(225,230,234))
        }
        val introRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        introRow.addView(MdiIconView(this@MainActivity).apply {
            setIconName("web"); setIconSize(30f); setTextColor(textMain)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(10) }
        })
        val introText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        introText.addView(TextView(this@MainActivity).apply {
            text = "Web Project Builder"; textSize = 17f; setTextColor(textMain)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        introText.addView(TextView(this@MainActivity).apply {
            text = "HTML + CSS + JavaScript → Build → Preview → Host"
            textSize = 11.5f; setTextColor(textMuted); setPadding(0, dp(3), 0, 0)
        })
        introRow.addView(introText, LinearLayout.LayoutParams(0,-2,1f))
        intro.addView(introRow)
        content.addView(intro, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        val name = edit("Nama Project", false).apply { hint = "my-website" }
        content.addView(sectionTitle("PROJECT", "folder-outline"))
        content.addView(name)

        val html = edit("HTML", true)
        val css = edit("CSS", true)
        val js = edit("JavaScript", true)
        val files = listOf(
            Triple("HTML", "language-html5", html),
            Triple("CSS", "language-css3", css),
            Triple("JavaScript", "language-javascript", js)
        )
        files.forEach { (labelText, iconName, editorTarget) ->
            editorTarget.visibility = View.GONE
            val row = settingRowClickable(labelText, "Editor ${labelText.lowercase()}", "Edit source $labelText", iconName) {
                webCodeEditor(labelText, editorTarget)
            }
            content.addView(row, LinearLayout.LayoutParams(-1, dp(62)).apply { bottomMargin = dp(6) })
        }

        content.addView(sectionTitle("IMPORT", "file-import-outline"))
        val importRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 3f }
        listOf("HTML" to WEB_HTML_PICK_REQUEST, "CSS" to WEB_CSS_PICK_REQUEST, "JS" to WEB_JS_PICK_REQUEST).forEach { (labelText, request) ->
            val b = button(labelText) {
                webImportTarget = when (request) {
                    WEB_HTML_PICK_REQUEST -> html
                    WEB_CSS_PICK_REQUEST -> css
                    else -> js
                }
                val type = when (request) {
                    WEB_HTML_PICK_REQUEST -> "text/html"
                    WEB_CSS_PICK_REQUEST -> "text/css"
                    else -> "text/javascript"
                }
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { this.type = type; addCategory(Intent.CATEGORY_OPENABLE) }, request)
            }
            importRow.addView(b, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
        }
        content.addView(importRow)

        content.addView(sectionTitle("BUILD PIPELINE", "source-branch-check"))
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = bg(if (isDarkTheme) panel2 else Color.WHITE, 16, if (isDarkTheme) line else Color.rgb(225,230,234))
        }
        val statusIcon = MdiIconView(this).apply {
            setIconName("circle-outline"); setIconSize(24f); setTextColor(textMuted)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(8) }
        }
        statusCard.addView(statusIcon)
        val status = TextView(this).apply {
            text = "BELUM BUILD"
            textSize = 13f; setTextColor(textMain); setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        webBuildStatusView = status
        statusCard.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(statusCard, LinearLayout.LayoutParams(-1, dp(58)))

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f }
        val buildButton = button("Build") {
            buildWebProject(name.text.toString(), html.text.toString(), css.text.toString(), js.text.toString())
        }
        webHostButton = button("Host Wi-Fi") { hostHomeWifiProject() }.apply { isEnabled = false; alpha = 0.45f }
        actions.addView(buildButton, LinearLayout.LayoutParams(0, dp(54), 1f).apply { rightMargin = dp(5); topMargin = dp(8) })
        actions.addView(webHostButton, LinearLayout.LayoutParams(0, dp(54), 1f).apply { leftMargin = dp(5); topMargin = dp(8) })
        content.addView(actions)

        content.addView(sectionTitle("OUTPUT", "monitor-dashboard"))
        val outputRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f }
        outputRow.addView(button("Preview") { previewWebProject() }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { rightMargin = dp(5) })
        outputRow.addView(button("Project Files") { openWebFolder() }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { leftMargin = dp(5) })
        content.addView(outputRow)
        content.addView(subLabel("Build membuat folder project lokal. Setelah status SUCCESS, Preview dan Host Wi-Fi dapat digunakan.", 11f).apply { setPadding(dp(3), dp(7), dp(3), 0) })
    }

fun MainActivity.markdownViewerTool() {
        clearPage("Markdown Viewer")
        addToolHeader("Markdown Viewer", "Tulis Markdown dan lihat preview HTML sederhana secara lokal.", "MD")
        val source = edit("Markdown", true)
        source.setText("# MyTools\n\n**Bold**, *italic*, `code`\n\n- Item satu\n- Item dua")
        content.addView(source)
        content.addView(button("Preview") {
            val html = markdownToHtml(source.text.toString())
            previewHtmlText("<!doctype html><html><meta name='viewport' content='width=device-width,initial-scale=1'><body style='font-family:sans-serif;padding:18px'>$html</body></html>", "HTML")
        })
        content.addView(button("Salin HTML") { copyText(markdownToHtml(source.text.toString())) })
    }

fun MainActivity.sqlToolsTool() {
        clearPage("SQL Tools")
        addToolHeader("SQL Tools", "Formatter dan pemeriksa dasar SQL untuk query developer.", "SQL")
        val input = edit("SELECT * FROM users WHERE id = 1;", true)
        content.addView(input)
        content.addView(button("Format SQL") {
            var q = input.text.toString().trim().replace(Regex("\\s+"), " ")
            val keywords = listOf("SELECT","FROM","WHERE","GROUP BY","ORDER BY","HAVING","LIMIT","VALUES","SET","JOIN","LEFT JOIN","RIGHT JOIN","INNER JOIN","INSERT INTO","UPDATE","DELETE FROM")
            keywords.sortedByDescending { it.length }.forEach { k ->
                q = q.replace(Regex("(?i)\\b${Regex.escape(k)}\\b"), "\n$k")
            }
            output(q.replace(Regex("\n "), "\n").trim())
        })
        content.addView(button("Inspect") {
            val q=input.text.toString().trim()
            val warnings=mutableListOf<String>()
            if(q.isBlank()) warnings.add("Query kosong")
            if(q.contains("SELECT",true) && !q.contains("FROM",true)) warnings.add("SELECT biasanya membutuhkan FROM")
            if(q.count{it=='('} != q.count{it==')'}) warnings.add("Kurung tidak seimbang")
            output(if(warnings.isEmpty()) "Pemeriksaan dasar: OK" else warnings.joinToString("\n"))
        })
    }

fun MainActivity.yamlFormatterTool() {
        clearPage("YAML Formatter")
        addToolHeader("YAML Formatter", "Normalisasi whitespace dan pemeriksaan struktur dasar YAML.", "YAML")
        val input = edit("key: value", true)
        content.addView(input)
        content.addView(button("Normalize / Inspect") {
            val lines = input.text.toString().lines().map { it.trimEnd() }.filter { it.isNotBlank() }
            val bad = lines.filter {
                val t=it.trimStart()
                !t.startsWith("-") && !t.startsWith("#") && !t.contains(":")
            }
            output((if (bad.isEmpty()) "Struktur dasar terlihat valid.\n\n" else "Baris yang perlu diperiksa:\n${bad.joinToString("\n")}\n\n") + lines.joinToString("\n"))
        })
    }

fun MainActivity.tomlInspectorTool() {
        clearPage("TOML Inspector")
        addToolHeader("TOML Inspector", "Pemeriksa section, key=value, komentar dan struktur dasar TOML.", "TOML")
        val input = edit("[server]\nport = 8080", true)
        content.addView(input)
        content.addView(button("Inspect") {
            val errors=mutableListOf<String>()
            input.text.toString().lines().forEachIndexed { i,line ->
                val t=line.trim()
                if(t.isBlank() || t.startsWith("#") || (t.startsWith("[") && t.endsWith("]"))) return@forEachIndexed
                if(!t.contains("=")) errors.add("Baris ${i+1}: tidak memiliki '='")
            }
            output(if(errors.isEmpty()) "TOML dasar terlihat valid." else errors.joinToString("\n"))
        })
    }

fun MainActivity.cronHelperTool() {
        clearPage("Cron Helper")
        addToolHeader("Cron Helper", "Baca 5 field cron dan jelaskan arti sederhananya.", "CRON")
        val input = edit("*/5 * * * *").apply { setText("*/5 * * * *") }
        content.addView(input)
        val fields = listOf("Menit","Jam","Hari Bulan","Bulan","Hari Minggu")
        content.addView(button("Parse") {
            val p=input.text.toString().trim().split(Regex("\\s+"))
            if(p.size!=5){output("Cron harus memiliki 5 field.");return@button}
            output(fields.indices.joinToString("\n") { i -> "${fields[i]}: ${cronFieldMeaning(p[i])}" })
        })
    }

fun MainActivity.jsonTool() {
        clearPage("JSON Tools")
        addToolHeader("JSON Tools", "Validasi, rapikan, kecilkan, atau escape JSON.", "{}")
        content.addView(toolSection("INPUT", "Tempel JSON yang ingin diproses."))
        val e = edit("Tempel JSON di sini", true); content.addView(e)
        content.addView(toolSection("ACTIONS"))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Validasi", "Pretty", "Minify").forEachIndexed { i, txt ->
            val b = button(txt) {
                val s=e.text.toString().trim()
                when(i) {
                    0 -> output(runCatching {
                        if (s.startsWith("{")) JSONObject(s) else if (s.startsWith("[")) JSONArray(s) else error("JSON harus dimulai dengan { atau [")
                        "JSON valid."
                    }.getOrElse { "JSON tidak valid: ${it.message}" })
                    1 -> output(runCatching { prettyJson(e.text.toString()) }.getOrElse { "JSON tidak valid: ${it.message}" })
                    else -> output(runCatching { minifyJson(e.text.toString()) }.getOrElse { "JSON tidak valid: ${it.message}" })
                }
            }
            row.addView(b, LinearLayout.LayoutParams(0, dp(50), 1f).apply { if(i>0) leftMargin=dp(5) })
        }
        content.addView(row)
        content.addView(button("Escape String") { output(JSONObjectLite.escape(e.text.toString())) })
    }

fun MainActivity.xmlFormatTool() {
        clearPage("XML Formatter")
        val e=edit("XML",true); content.addView(e)
        content.addView(button("Format XML") {
            output(runCatching {
                val f=javax.xml.transform.TransformerFactory.newInstance().newTransformer().apply {
                    setOutputProperty(javax.xml.transform.OutputKeys.INDENT,"yes")
                    setOutputProperty("{http://xml.apache.org/xslt}indent-amount","2")
                }
                val sw=StringWriter(); f.transform(javax.xml.transform.stream.StreamSource(StringReader(e.text.toString())), javax.xml.transform.stream.StreamResult(sw)); sw.toString()
            }.getOrElse { "XML error: ${it.message}" })
        })
    }
