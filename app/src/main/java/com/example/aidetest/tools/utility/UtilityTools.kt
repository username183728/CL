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


fun MainActivity.hashTool() {
        clearPage("Hash Generator")
        addToolHeader("Hash Generator", "Buat hash teks dengan algoritma yang kamu pilih.", "#")
        content.addView(toolSection("INPUT")); val e=edit("Teks yang akan di-hash"); content.addView(e)
        content.addView(toolSection("ALGORITHM"))
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        listOf("MD5","SHA-1","SHA-256","SHA-512").forEachIndexed { i,alg ->
            val b=button(alg){output(digest(alg,e.text.toString().toByteArray()))}
            row.addView(b,LinearLayout.LayoutParams(0,dp(50),1f).apply{if(i>0)leftMargin=dp(5)})
        }; content.addView(row)
    }

fun MainActivity.simpleTransform(name: String, a: String, b: String) {
        clearPage(name)
        addToolHeader(name, "Proses input dengan dua mode utama dan lihat hasil tanpa meninggalkan halaman.", "↔")
        content.addView(toolSection("INPUT", "Masukkan teks atau data yang akan diproses."))
        val e = edit("Teks", true)
        content.addView(e)
        content.addView(toolSection("ACTIONS"))
        content.addView(button(a) { output(Base64.getEncoder().encodeToString(e.text.toString().toByteArray())) })
        content.addView(button(b) {
            output(runCatching { String(Base64.getDecoder().decode(e.text.toString()), StandardCharsets.UTF_8) }
                .getOrElse { "Input Base64 tidak valid" })
        })
    }

fun MainActivity.simpleResultTool(name: String, fn: () -> String) {
        clearPage(name)
        addToolHeader(name, toolDescription(name), "•")
        content.addView(toolSection("ACTION", "Jalankan fungsi utama; hasil otomatis tersedia untuk Salin/Bagikan."))
        content.addView(button("Generate") { output(fn()) })
    }

fun MainActivity.urlTool() {
        clearPage("URL Tools")
        addToolHeader("URL Tools", "Encode atau decode teks URL tanpa keluar dari halaman.", "↗")
        content.addView(toolSection("VALUE")); val e=edit("Masukkan URL atau teks"); content.addView(e)
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        row.addView(button("Encode"){output(java.net.URLEncoder.encode(e.text.toString(),"UTF-8"))},LinearLayout.LayoutParams(0,dp(50),1f))
        row.addView(button("Decode"){output(runCatching{java.net.URLDecoder.decode(e.text.toString(),"UTF-8")}.getOrDefault("URL tidak valid"))},LinearLayout.LayoutParams(0,dp(50),1f).apply{leftMargin=dp(6)})
        content.addView(row)
    }

fun MainActivity.regexTool() {
        clearPage("Regex Tester")
        addToolHeader("Regex Tester", "Uji pattern dan lihat hasil match secara langsung.", ".*")
        content.addView(toolSection("PATTERN")); val p=edit("Contoh: \\d+"); content.addView(p)
        content.addView(toolSection("TEST TEXT")); val t=edit("Teks yang diuji",true); content.addView(t)
        val status=toolStatus("Belum diuji"); content.addView(status)
        content.addView(button("Test Pattern") { runCatching { val matches=Regex(p.text.toString()).findAll(t.text).map{it.value}.toList(); status.text="●  ${matches.size} match ditemukan"; output(if(matches.isEmpty())"Tidak ada match" else matches.joinToString("\n")) }.onFailure{status.text="●  Regex error"; output("Regex error: ${it.message}")} })
    }

fun MainActivity.randomTool() {
        clearPage("Random Bytes")
        val n=edit("Jumlah byte"); content.addView(n)
        content.addView(button("Generate") {
            val size=runCatching { n.text.toString().toInt() }.getOrDefault(32).coerceIn(1,4096)
            output(randomBytes(size))
        })
    }

fun MainActivity.checksumTool() {
        clearPage("Checksum File")
        content.addView(button("Pilih file") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type="*/*"; addCategory(Intent.CATEGORY_OPENABLE)
            }, 1003)
        })
        content.addView(label("Pilih file lalu checksum dihitung di perangkat."))
    }

fun MainActivity.hexTool() {
        clearPage("Hex Converter")
        val e=edit("Teks atau HEX"); content.addView(e)
        content.addView(button("Text → Hex") { output(e.text.toString().toByteArray().joinToString("") { "%02x".format(it) }) })
        content.addView(button("Hex → Text") {
            output(runCatching {
                e.text.toString().replace("\\s".toRegex(),"").chunked(2).map { it.toInt(16).toByte() }.toByteArray().toString(StandardCharsets.UTF_8)
            }.getOrElse { "HEX tidak valid" })
        })
    }

fun MainActivity.base32Tool() {
        clearPage("Base32")
        val e=edit("Teks"); content.addView(e)
        content.addView(button("Encode") { output(Base32.encode(e.text.toString().toByteArray())) })
        content.addView(button("Decode") { output(runCatching { String(Base32.decode(e.text.toString())) }.getOrElse { "Base32 tidak valid" }) })
    }

fun MainActivity.timestampTool() {
        clearPage("Timestamp Converter")
        val e=edit("Unix timestamp atau tanggal ISO"); content.addView(e)
        content.addView(button("Sekarang") { output("Unix seconds: ${System.currentTimeMillis()/1000}\nUnix millis: ${System.currentTimeMillis()}\nLocal: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}") })
        content.addView(button("Timestamp → Tanggal") {
            output(runCatching {
                val raw=e.text.toString().trim(); val ms=if(raw.length>10) raw.toLong() else raw.toLong()*1000
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(ms))
            }.getOrElse { "Timestamp tidak valid" })
        })
        content.addView(button("Tanggal → Timestamp") {
            output(runCatching {
                val formats=listOf("yyyy-MM-dd HH:mm:ss","yyyy-MM-dd HH:mm:ss.SSS","yyyy-MM-dd'T'HH:mm:ss","yyyy-MM-dd")
                val d=formats.asSequence().mapNotNull { f -> runCatching { SimpleDateFormat(f, Locale.getDefault()).apply { isLenient=false }.parse(e.text.toString().trim()) }.getOrNull() }.firstOrNull() ?: error("Format tidak dikenali")
                "Unix seconds: ${d.time/1000}\nUnix millis: ${d.time}"
            }.getOrElse { "Tanggal tidak valid: ${it.message}" })
        })
    }

fun MainActivity.unicodeTool() {
        clearPage("Unicode Inspector")
        val e=edit("Teks", true); content.addView(e)
        content.addView(button("Inspect") {
            val s=e.text.toString(); val sb=StringBuilder()
            var offset = 0
            var index = 0
            while (offset < s.length) {
                val cp = s.codePointAt(offset)
                val ch = String(Character.toChars(cp))
                sb.append(index).append("  ").append(ch).append("  U+")
                    .append(cp.toString(16).toUpperCase(Locale.getDefault()).padStart(4,'0'))
                    .append("  ").append(Character.getName(cp) ?: "UNKNOWN").append('\n')
                offset += Character.charCount(cp)
                index++
            }
            output(if(sb.isEmpty()) "Tidak ada karakter." else sb.toString())
        })
        content.addView(button("Text → \\uXXXX") {
            val s = e.text.toString()
            val sb = StringBuilder()
            var offset = 0
            while (offset < s.length) {
                val cp = s.codePointAt(offset)
                sb.append("\\u").append(String.format(Locale.US, "%04X", cp))
                offset += Character.charCount(cp)
            }
            output(sb.toString())
        })
    }

fun MainActivity.urlParserTool() {
        clearPage("URL Parser")
        val e=edit("https://example.com/path?a=1#section"); content.addView(e)
        content.addView(button("Parse") {
            output(runCatching {
                val u=URL(e.text.toString().trim())
                "Protocol: ${u.protocol}\nHost: ${u.host}\nPort: ${if(u.port==-1) "default" else u.port}\nPath: ${u.path}\nQuery: ${u.query ?: ""}\nFragment: ${u.ref ?: ""}\nUserInfo: ${u.userInfo ?: ""}"
            }.getOrElse { "URL tidak valid: ${it.message}" })
        })
    }

fun MainActivity.mimeTool() {
        clearPage("MIME Type Lookup")
        val e=edit("nama file, contoh photo.png"); content.addView(e)
        content.addView(button("Lookup") {
            val ext=e.text.toString().substringAfterLast('.',"").toLowerCase(Locale.getDefault())
            output(if(ext.isEmpty()) "Ekstensi tidak ditemukan" else "Extension: .$ext\nMIME: ${MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"}")
        })
    }

fun MainActivity.uuidBatchTool() {
        clearPage("UUID Batch Generator")
        val n=edit("Jumlah UUID (1-100)"); n.setText("10"); content.addView(n)
        content.addView(button("Generate") {
            val count=(n.text.toString().toIntOrNull() ?: 10).coerceIn(1,100)
            output((1..count).joinToString("\n") { UUID.randomUUID().toString() })
        })
    }

fun MainActivity.base64FileTool() {
        clearPage("Base64 File Tool")
        content.addView(button("Encode File → Base64") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="*/*"; addCategory(Intent.CATEGORY_OPENABLE) }, 1010)
        })
        content.addView(label("Pilih file untuk membaca Base64. File besar diproses dengan batas 8 MB untuk menjaga RAM."))
    }

fun MainActivity.httpHeadersTool() {
        clearPage("HTTP Headers")
        val e=edit("https://example.com"); content.addView(e)
        content.addView(button("GET Headers") {
            thread {
                val result=runCatching {
                    val c=(URL(e.text.toString()).openConnection() as HttpURLConnection).apply { requestMethod="HEAD"; connectTimeout=7000; readTimeout=7000; instanceFollowRedirects=true }
                    c.connect(); val sb=StringBuilder("Status: ${c.responseCode} ${c.responseMessage}\n")
                    c.headerFields.forEach { (k,v) -> if(k!=null) sb.append(k).append(": ").append(v.joinToString(", ")).append('\n') }
                    c.disconnect(); sb.toString()
                }.getOrElse { "HTTP error: ${it.message}" }
                runOnUiThread { output(result) }
            }
        })
    }

fun MainActivity.textReplaceTool() {
        clearPage("Find & Replace")
        val text=edit("Teks",true); val find=edit("Cari"); val repl=edit("Ganti dengan")
        content.addView(text); content.addView(find); content.addView(repl)
        content.addView(button("Replace All") { output(text.text.toString().replace(find.text.toString(),repl.text.toString())) })
    }

fun MainActivity.wordFrequencyTool() {
        clearPage("Word Frequency")
        val e=edit("Teks",true); content.addView(e)
        content.addView(button("Analyze") {
            val map=e.text.toString().toLowerCase(Locale.getDefault()).split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.isNotBlank() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
            output(if(map.isEmpty()) "Tidak ada kata." else map.take(100).joinToString("\n") { "${it.key}: ${it.value}" })
        })
    }

fun MainActivity.textStatTool() {
        clearPage("Statistik Teks")
        addToolHeader("Statistik Teks", "Hitung karakter, kata, dan baris dari teks dengan cepat.", "format-letter-case")
        content.addView(toolSection("INPUT", "Masukkan teks yang ingin dianalisis."))
        val e=edit("Teks", true); content.addView(e)
        content.addView(button("Hitung") {
            val s=e.text.toString()
            output("Karakter: ${s.length}\nKata: ${s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size}\nBaris: ${if(s.isEmpty()) 0 else s.lines().size}")
        })
    }

fun MainActivity.caseTool() {
        clearPage("Case Converter")
        addToolHeader("Case Converter", "Ubah kapitalisasi teks tanpa meninggalkan halaman.", "format-letter-case")
        content.addView(toolSection("INPUT", "Masukkan teks yang ingin diubah."))
        val e=edit("Teks", true); content.addView(e)
        content.addView(button("UPPER") { output(e.text.toString().toUpperCase(Locale.getDefault())) })
        content.addView(button("lower") { output(e.text.toString().toLowerCase(Locale.getDefault())) })
        content.addView(button("Title") { output(e.text.toString().split(Regex("\\s+")).joinToString(" ") { it.substring(0, 1).toUpperCase(Locale.getDefault()) + it.substring(1) }) })
    }

fun MainActivity.dedupeTool() {
        clearPage("Hapus Baris Duplikat")
        addToolHeader("Hapus Baris Duplikat", "Bersihkan item yang berulang dari daftar teks.", "content-duplicate")
        content.addView(toolSection("INPUT", "Gunakan satu baris untuk setiap item."))
        val e=edit("Satu baris per item", true); content.addView(e)
        content.addView(button("Hapus Duplikat") { output(e.text.toString().lines().distinct().joinToString("\n")) })
    }

fun MainActivity.compareTool() {
        clearPage("Bandingkan Teks")
        addToolHeader("Bandingkan Teks", "Bandingkan dua teks dan tampilkan baris yang berbeda.", "compare")
        content.addView(toolSection("INPUT A"))
        val a=edit("Teks A", true); content.addView(a)
        content.addView(toolSection("INPUT B"))
        val b=edit("Teks B", true); content.addView(b)
        content.addView(button("Bandingkan") {
            val aa=a.text.toString().lines(); val bb=b.text.toString().lines()
            val max=maxOf(aa.size,bb.size); val sb=StringBuilder()
            for(i in 0 until max) if((aa.getOrNull(i)?:"") != (bb.getOrNull(i)?:""))
                sb.append("- ").append(aa.getOrNull(i)?:"").append("\n+ ").append(bb.getOrNull(i)?:"").append("\n")
            output(if(sb.isEmpty()) "Tidak ada perbedaan." else sb.toString())
        })
    }

fun MainActivity.slugTool() {
        clearPage("Slug Generator")
        addToolHeader("Slug Generator", "Ubah judul menjadi slug URL yang bersih.", "link-variant")
        content.addView(toolSection("INPUT"))
        val e=edit("Judul"); content.addView(e)
        content.addView(button("Buat Slug") { output(e.text.toString().toLowerCase(Locale.getDefault()).replace(Regex("[^a-z0-9]+"), "-").trim('-')) })
    }

fun MainActivity.loremTool() {
        clearPage("Lorem Ipsum")
        addToolHeader("Lorem Ipsum", "Buat teks placeholder untuk desain, prototipe, dan layout.", "text-box")
        content.addView(toolSection("GENERATE", "Hasil dapat langsung disalin atau dibagikan."))
        content.addView(button("Buat 100 kata") {
            val words="lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor incididunt ut labore et dolore magna aliqua".split(" ")
            output((0 until 100).joinToString(" ") { words[it % words.size] })
        })
    }

fun MainActivity.passwordTool() {
        clearPage("Password Generator")
        val n=edit("Panjang, contoh 20"); content.addView(n)
        content.addView(button("Generate") {
            val len=runCatching { n.text.toString().toInt() }.getOrDefault(20).coerceIn(4,128)
            output(randomString(len, "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#\$%&*"))
        })
    }

fun MainActivity.tokenTool() {
        clearPage("Token Acak")
        content.addView(button("32 byte HEX") { output(randomBytes(32)) })
        content.addView(button("64 byte Base64URL") { output(Base64.getUrlEncoder().withoutPadding().encodeToString(SecureRandom().generateSeed(64))) })
    }

fun MainActivity.jwtTool() {
        clearPage("JWT Decoder")
        val e=edit("JWT"); content.addView(e)
        content.addView(button("Decode") {
            val p=e.text.toString().split(".")
            if(p.size<2) output("JWT tidak valid")
            else output("HEADER:\n${decodeB64Url(p[0])}\n\nPAYLOAD:\n${decodeB64Url(p[1])}")
        })
    }

fun MainActivity.hmacTool() {
        clearPage("HMAC Generator")
        val key=edit("Secret key"); val msg=edit("Message", true); content.addView(key); content.addView(msg)
        content.addView(button("HMAC-SHA256") {
            val mac=Mac.getInstance("HmacSHA256"); mac.init(SecretKeySpec(key.text.toString().toByteArray(), "HmacSHA256"))
            output(mac.doFinal(msg.text.toString().toByteArray()).joinToString("") { "%02x".format(it) })
        })
    }

fun MainActivity.totpTool() {
        clearPage("TOTP Generator")
        val secret=edit("Base32 secret"); content.addView(secret)
        val out=label("",22f,true); content.addView(out)
        content.addView(button("Generate sekarang") {
            out.text=totp(secret.text.toString(), System.currentTimeMillis()/1000/30)
        })
    }

fun MainActivity.aesTool() {
        clearPage("AES-256-GCM")
        val key=edit("Password/key"); val text=edit("Plaintext / encrypted text", true)
        content.addView(key); content.addView(text)
        content.addView(button("Encrypt") {
            output(aesEncrypt(key.text.toString(), text.text.toString()))
        })
        content.addView(button("Decrypt") {
            output(runCatching { aesDecrypt(key.text.toString(), text.text.toString()) }.getOrElse { "Data/key tidak valid" })
        })
    }
