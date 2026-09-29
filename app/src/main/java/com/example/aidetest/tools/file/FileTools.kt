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


fun MainActivity.fileManager(dir: File) {
        clearPage("File Manager")
        val files = sortFiles(dir.listFiles()?.filter { fileFilterText.isBlank() || it.name.contains(fileFilterText, true) } ?: emptyList())
        val folders = files.count { it.isDirectory }
        val regular = files.size - folders

        content.addView(toolHeader("File Manager", dir.name, "folder-multiple-outline"), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })

        val path = TextView(this).apply {
            text = MdiText.iconize(context, "⌂  ${dir.absolutePath}")
            textSize = 11f
            setTextColor(textMuted)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = bg(panel2, 12, line)
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.START
        }
        content.addView(path, LinearLayout.LayoutParams(-1, dp(42)).apply { bottomMargin = dp(8) })

        val quick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun quickAction(text: String, icon: String, action: () -> Unit) = MdiIconView(this).apply {
            setIconName(icon); setIconSize(20f); setTextColor(textMain); contentDescription = text
            background = bg(panel2, 12, line); isClickable = true; isFocusable = true
            setPadding(dp(10), dp(10), dp(10), dp(10)); setOnClickListener { action() }
        }
        quick.addView(quickAction("Folder baru", "folder-plus-outline") {
            val e = edit("nama folder")
            AlertDialog.Builder(this).setTitle("Folder Baru").setView(e)
                .setPositiveButton("Buat") { _, _ ->
                    safeChildFile(dir, e.text.toString())?.let { target ->
                        if (target.exists() || !target.mkdirs()) toast("Folder gagal dibuat") else fileManager(dir)
                    } ?: toast("Nama folder tidak valid")
                }.setNegativeButton("Batal", null).show()
        }, LinearLayout.LayoutParams(dp(46), dp(46)).apply { rightMargin = dp(6) })
        quick.addView(quickAction("Urutkan", "sort-variant") { showFileSortDialog(dir) }, LinearLayout.LayoutParams(dp(46), dp(46)).apply { rightMargin = dp(6) })
        quick.addView(quickAction("Pilih banyak", "checkbox-multiple-marked-outline") { showMultiSelectDialog(dir) }, LinearLayout.LayoutParams(dp(46), dp(46)).apply { rightMargin = dp(6) })
        quick.addView(quickAction("File Android", "file-import-outline") { pickFileForEditor() }, LinearLayout.LayoutParams(dp(46), dp(46)).apply { rightMargin = dp(10) })
        val count = TextView(this).apply {
            text = "$folders folder  •  $regular file"
            textSize = 11f; setTextColor(textMuted); gravity = Gravity.CENTER_VERTICAL
        }
        quick.addView(count, LinearLayout.LayoutParams(0, dp(46), 1f))
        content.addView(quick, LinearLayout.LayoutParams(-1, dp(46)).apply { bottomMargin = dp(8) })

        val searchBox = edit("Filter nama file / folder").apply { setText(fileFilterText) }
        content.addView(searchBox, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(7) })
        content.addView(button("Terapkan Filter") { fileFilterText = searchBox.text.toString().trim(); fileManager(dir) })
        if (dir != filesDir) content.addView(button("←  Folder sebelumnya") { fileManager(dir.parentFile ?: filesDir) })

        if (files.isEmpty()) {
            val empty = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(20), dp(32), dp(20), dp(32)); background = bg(panel2, 18, line) }
            empty.addView(MdiIconView(this).apply { setIconName("folder-open-outline"); setIconSize(38f); setTextColor(textMuted); layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)).apply { gravity = Gravity.CENTER } })
            empty.addView(label("Folder kosong", 16f, true).apply { gravity = Gravity.CENTER })
            empty.addView(subLabel(if (fileFilterText.isBlank()) "Belum ada file atau folder di sini." else "Tidak ada item yang cocok dengan filter.", 11f).apply { gravity = Gravity.CENTER })
            content.addView(empty)
            return
        }

        content.addView(subLabel("${files.size} item  •  ketuk untuk membuka, tekan ⋮ untuk aksi", 11f))
        files.forEach { f -> content.addView(fileManagerCard(f, dir)) }
    }

fun MainActivity.recentFilesTool() {
        clearPage("Recent Files")
        content.addView(label("Recent Files", 22f, true)); content.addView(subLabel("File yang terakhir dibuka dari File Manager / Editor.", 12f))
        val paths = prefs.getString("recent_files", "")?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
        content.addView(button("Bersihkan Recent Files") { prefs.edit().remove("recent_files").apply(); recentFilesTool() })
        if (paths.isEmpty()) content.addView(subLabel("Belum ada file terbaru.", 13f))
        paths.forEach { path -> val f = File(path); if (f.exists()) content.addView(button(f.name) { recordRecentFile(f); editor(f) }) }
    }

fun MainActivity.backupRestoreTool() {
        clearPage("Backup / Restore")
        content.addView(label("Backup / Restore", 22f, true))
        content.addView(subLabel("Backup data MyTools ke satu file ZIP lokal. Backup tidak dikirim ke server.", 12f))
        content.addView(button("Buat Backup") { createAppBackup() })
        content.addView(button("Restore Backup") { restoreAppBackup() })
        content.addView(subLabel("Isi: preferences aplikasi, riwayat, recent files, dan data lokal yang aman untuk dipulihkan.", 11f))
    }

fun MainActivity.zipTool() {
        clearPage("ZIP / UNZIP")
        toolWorkspace("ZIP / UNZIP", "Kompres atau ekstrak file di penyimpanan aplikasi dengan batas aman.", "zip-box")
        toolWorkspaceSection("COMPRESS", "Masukkan nama file atau folder yang akan dibuat ZIP.")
        val src = edit("Nama file/folder di app storage")
        content.addView(src)
        content.addView(button("Buat ZIP") {
            val f = File(filesDir, src.text.toString().trim())
            if (!f.exists()) toast("File tidak ditemukan") else {
                val out = File(filesDir, f.nameWithoutExtension + ".zip")
                thread {
                    val result = runCatching { zipPath(f, out); "ZIP: ${out.absolutePath}" }
                        .getOrElse { "ZIP error: ${it.message}" }
                    runOnUiThread { output(result) }
                }
            }
        })
        toolWorkspaceSection("EXTRACT", "Masukkan nama ZIP yang berada di app storage.")
        val zip = edit("Nama .zip")
        content.addView(zip)
        content.addView(button("Ekstrak ZIP") {
            val f = File(filesDir, zip.text.toString().trim())
            if (!f.exists()) toast("ZIP tidak ditemukan") else {
                val dest = File(filesDir, f.nameWithoutExtension).apply { mkdirs() }
                thread {
                    val result = runCatching { unzipSafe(f, dest); "Extracted: ${dest.absolutePath}" }
                        .getOrElse { "Extract error: ${it.message}" }
                    runOnUiThread { output(result) }
                }
            }
        })
    }

fun MainActivity.duplicateFinderTool() {
        clearPage("Duplicate Finder")
        content.addView(label("Duplicate Finder", 22f, true))
        content.addView(subLabel("Mencari file yang memiliki ukuran sama lalu mencocokkan SHA-256. Hanya folder yang dapat diakses aplikasi yang dipindai.", 12f))

        val min = edit("Ukuran minimum (KB), default 1")
        content.addView(min)
        val out = toolStatus("Siap", false)
        content.addView(out)

        content.addView(button("Scan Duplicate") {
            val minBytes = (min.text.toString().toLongOrNull() ?: 1L) * 1024L
            out.text = "Memindai…"

            thread {
                val files = mutableListOf<File>()
                scanFiles(scanRoots(), files, 5000)

                val groups = files
                    .filter { it.isFile && it.length() >= minBytes }
                    .groupBy { it.length() }
                    .filter { it.value.size > 1 }

                val matches = mutableListOf<List<File>>()
                for ((_, group) in groups) {
                    val byHash = group.groupBy {
                        runCatching { sha256(it) }.getOrDefault("")
                    }
                    byHash.values
                        .filter { it.size > 1 }
                        .forEach { matches.add(it) }
                }

                runOnUiThread {
                    out.text = "Selesai • ${matches.size} grup"
                    content.addView(label("${matches.size} grup duplikat", 15f, true))
                    matches.take(100).forEach { group ->
                        content.addView(
                            infoCard(
                                "${group.first().length()} bytes",
                                group.joinToString("\n") { it.absolutePath }
                            )
                        )
                    }
                }
            }
        })
    }

fun MainActivity.largeFileFinderTool() {
        clearPage("Large File Finder")
        content.addView(label("Large File Finder", 22f, true))
        content.addView(subLabel("Cari file terbesar pada folder yang dapat diakses aplikasi.", 12f))

        val min = edit("Batas minimum MB, default 50")
        content.addView(min)
        val out = toolStatus("Siap", false)
        content.addView(out)

        content.addView(button("Scan Large Files") {
            val minBytes = (min.text.toString().toLongOrNull() ?: 50L) * 1024L * 1024L
            out.text = "Memindai…"

            thread {
                val files = mutableListOf<File>()
                scanFiles(scanRoots(), files, 10000)

                val top = files
                    .filter { it.isFile && it.length() >= minBytes }
                    .sortedByDescending { it.length() }
                    .take(100)

                runOnUiThread {
                    out.text = "Selesai • ${top.size} file"
                    content.addView(label("File terbesar", 15f, true))
                    top.forEach {
                        content.addView(infoCard(bytesText(it.length()), it.absolutePath))
                    }
                }
            }
        })
    }

fun MainActivity.fileSearchTool() {
        clearPage("File Search")
        toolWorkspace("File Search", "Cari file berdasarkan nama di ruang data aplikasi.", "file-search")
        toolWorkspaceSection("SEARCH", "Pencarian dibatasi ke folder data aplikasi agar cepat.")
        val q = edit("contoh: config.json")
        content.addView(q)
        content.addView(button("Cari") {
            val term = q.text.toString().trim().toLowerCase(Locale.getDefault())
            if (term.isEmpty()) { Toast.makeText(this, "Masukkan nama file", Toast.LENGTH_SHORT).show(); return@button }
            val results = mutableListOf<File>()
            findFiles(filesDir, term, results, 200)
            content.addView(label("Hasil: ${results.size}", 14f, true))
            results.forEach { f -> content.addView(button(f.absolutePath) { editor(f) }) }
        })
    }

fun MainActivity.fileHashCompareTool() {
        clearPage("File Hash Compare")
        addToolHeader("File Hash Compare", "Bandingkan dua file berdasarkan ukuran dan SHA-256.", "HASH")
        val a = label("File A: belum dipilih", 13f)
        val b = label("File B: belum dipilih", 13f)
        content.addView(a); content.addView(b)
        content.addView(button("Pilih File A") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE)
            }, 1201)
        })
        content.addView(button("Pilih File B") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE)
            }, 1202)
        })
        content.addView(button("Bandingkan") {
            val ua = fileHashUriA
            val ub = fileHashUriB
            if (ua == null || ub == null) { toast("Pilih dua file"); return@button }
            thread {
                val r = runCatching {
                    val ha = contentResolver.openInputStream(ua)?.use { digestStream(it, "SHA-256") } ?: error("File A tidak bisa dibuka")
                    val hb = contentResolver.openInputStream(ub)?.use { digestStream(it, "SHA-256") } ?: error("File B tidak bisa dibuka")
                    "SHA-256 A: $ha\nSHA-256 B: $hb\n\nHASIL: ${if (ha.equals(hb, true)) "IDENTIK" else "BERBEDA"}"
                }.getOrElse { "Gagal: ${it.message}" }
                runOnUiThread { output(r) }
            }
        })
        content.addView(subLabel("Semua hash dihitung lokal di perangkat.", 11f))
        fileHashCompareLabelA = a
        fileHashCompareLabelB = b
    }

fun MainActivity.fileEncryptionTool() {
        clearPage("File Encryption")
        addToolHeader("File Encryption", "Enkripsi/dekripsi file dengan AES-256-GCM. File diproses lokal.", "AES")
        val pass = edit("Password", false).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        content.addView(pass)
        val selected = label("Belum ada file", 13f); content.addView(selected)
        content.addView(button("Pilih File") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="*/*"; addCategory(Intent.CATEGORY_OPENABLE) }, SECURITY_FILE_PICK)
        })
        content.addView(button("Enkripsi AES-256-GCM") {
            val uri = securityFileUri ?: run { toast("Pilih file dulu"); return@button }
            if (pass.text.isNullOrBlank()) { toast("Password wajib diisi"); return@button }
            thread {
                val result = runCatching {
                    val src = contentResolver.openInputStream(uri) ?: error("File tidak bisa dibuka")
                    val plain = src.use { it.readBytes() }
                    val salt = ByteArray(16); val iv = ByteArray(12); SecureRandom().nextBytes(salt); SecureRandom().nextBytes(iv)
                    val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, SecretKeySpec(aesKeyV2(pass.text.toString(), salt), "AES"), GCMParameterSpec(128, iv))
                    val enc = cipher.doFinal(plain)
                    val name = (uri.lastPathSegment ?: "file").substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val out = File(filesDir, "${name}.mytools.enc")
                    FileOutputStream(out).use { it.write("MYTOOLS-FILE-AES2".toByteArray(StandardCharsets.US_ASCII)); it.write(salt); it.write(iv); it.write(enc) }
                    "Enkripsi berhasil\n${out.absolutePath}\nUkuran: ${out.length()} byte"
                }.getOrElse { "Gagal: ${it.message}" }
                runOnUiThread { output(result) }
            }
        })
        content.addView(button("Pilih .mytools.enc untuk Dekripsi") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="application/octet-stream"; addCategory(Intent.CATEGORY_OPENABLE) }, SECURITY_FILE_PICK)
        })
        content.addView(button("Dekripsi") {
            val uri = securityFileUri ?: run { toast("Pilih file .enc dulu"); return@button }
            if (pass.text.isNullOrBlank()) { toast("Password wajib diisi"); return@button }
            thread {
                val result = runCatching {
                    val all = (contentResolver.openInputStream(uri) ?: error("File tidak bisa dibuka")).use { it.readBytes() }
                    val head = "MYTOOLS-FILE-AES2".toByteArray(StandardCharsets.US_ASCII); require(all.size > head.size + 28 && all.copyOfRange(0, head.size).contentEquals(head)) { "Format file tidak dikenali" }
                    val salt=all.copyOfRange(head.size,head.size+16); val iv=all.copyOfRange(head.size+16,head.size+28); val enc=all.copyOfRange(head.size+28,all.size)
                    val c=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding"); c.init(javax.crypto.Cipher.DECRYPT_MODE,SecretKeySpec(aesKeyV2(pass.text.toString(),salt),"AES"),GCMParameterSpec(128,iv)); val plain=c.doFinal(enc)
                    val out=File(filesDir,(uri.lastPathSegment ?: "decrypted").removeSuffix(".mytools.enc")+".decrypted")
                    FileOutputStream(out).use{it.write(plain)}; "Dekripsi berhasil\n${out.absolutePath}\nUkuran: ${out.length()} byte"
                }.getOrElse { "Gagal: password salah atau file rusak (${it.message})" }
                runOnUiThread{output(result)}
            }
        })
    }

fun MainActivity.fileConvertTool() {
        clearPage("Konversi File")
        convCategory = null
        convStage = "form"
        convResetSelection()
        renderConv()
    }
