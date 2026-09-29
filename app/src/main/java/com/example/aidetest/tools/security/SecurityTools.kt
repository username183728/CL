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


fun MainActivity.securityCenterTool() {
        clearPage("Security Center")
        content.addView(label("Security Center", 24f, true))
        content.addView(subLabel("Ringkasan keamanan dan privasi aplikasi.", 12f))
        content.addView(settingRow("Notification Access", "Tidak digunakan", "MyTools tidak memakai NotificationListenerService dan tidak meminta BIND_NOTIFICATION_LISTENER_SERVICE."))
        content.addView(settingRow("Data keuangan", "Lokal", "Database FinanceDb berada di penyimpanan aplikasi; tidak ada pembacaan notifikasi untuk pencatatan."))
        content.addView(settingRow("Akses jaringan", "INTERNET + status Wi-Fi", "Diperlukan untuk tool jaringan/ESP. Jangan masukkan kredensial sensitif ke log atau payload."))
        content.addView(settingRow("Komponen internal", "FileProvider non-exported", "Berbagi file laporan menggunakan URI permission melalui FileProvider."))
        content.addView(settingRow("Backup", "Manual", "Backup/restore finance dilakukan saat pengguna memintanya."))
        content.addView(button("Hapus seluruh data keuangan") { confirmClearFinance(FinanceDb(this)) })
        content.addView(subLabel("Security Tools", 14f))
        listOf("HelpBot Offline" to "helpbot", "File Encryption" to "fileencryption", "Steganography" to "steganography", "Password Strength Analyzer" to "passwordanalyzer", "Data Breach Checker" to "breachchecker", "Secure Notes" to "securenotes", "2FA Manager (TOTP)" to "totpvault", "PGP Encrypt / Decrypt" to "pgp", "SSH Key Generator" to "sshkeygen", "Certificate Viewer" to "certviewer", "Virus Scanner" to "virusscanner", "URL Safety Checker" to "urlsafety").forEach { (n,id) -> content.addView(settingRowClickable(n, "Buka tool", "", "shield-key-outline") { openTool(id) }) }
        content.addView(button("Buka pengaturan aplikasi Android") { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) })
        content.addView(subLabel("Catatan: halaman ini adalah pemeriksaan konfigurasi aplikasi, bukan audit keamanan perangkat secara menyeluruh.", 11f))
    }

fun MainActivity.passwordStrengthTool() {
        clearPage("Password Strength")
        addToolHeader("Password Strength", "Pemeriksaan lokal panjang dan keragaman karakter.", "SEC")
        val input=edit("Password")
        input.inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        content.addView(input)
        val result=label("Belum diperiksa",15f,true);content.addView(result)
        content.addView(button("Periksa") {
            val p=input.text.toString()
            val score=(if(p.length>=8)1 else 0)+(if(p.length>=12)1 else 0)+(if(p.any(Char::isUpperCase))1 else 0)+(if(p.any(Char::isLowerCase))1 else 0)+(if(p.any(Char::isDigit))1 else 0)+(if(p.any{!it.isLetterOrDigit()})1 else 0)
            val level=when(score){0,1->"Sangat lemah";2,3->"Lemah";4->"Sedang";5->"Kuat";else->"Sangat kuat"}
            result.text="$level • skor $score/6\nPanjang: ${p.length}\nHuruf besar: ${p.any(Char::isUpperCase)} • kecil: ${p.any(Char::isLowerCase)} • angka: ${p.any(Char::isDigit)} • simbol: ${p.any{!it.isLetterOrDigit()}}"
        })
    }

fun MainActivity.passwordStrengthAnalyzerTool(){
        clearPage("Password Strength Analyzer"); addToolHeader("Password Strength Analyzer","Analisis kekuatan, entropi dan estimasi brute-force secara lokal.","SEC")
        val e=edit("Password");e.inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD;content.addView(e);val out=label("Belum dianalisis",15f);content.addView(out)
        content.addView(button("Analisis") { val p=e.text.toString();val pool=(if(p.any{it.isLowerCase()})26 else 0)+(if(p.any{it.isUpperCase()})26 else 0)+(if(p.any{it.isDigit()})10 else 0)+(if(p.any{!it.isLetterOrDigit()})33 else 0);val entropy=if(pool>0)p.length*kotlin.math.log(pool.toDouble(), 2.0) else 0.0;val guesses=if(entropy>62)1e18 else Math.pow(2.0,entropy);val sec=guesses/1e10;val time=when{sec<60->"${sec.roundToInt()} detik";sec<3600->"${(sec/60).roundToInt()} menit";sec<86400->"${(sec/3600).roundToInt()} jam";sec<31557600->"${(sec/86400).roundToInt()} hari";else->"${(sec/31557600).roundToInt()} tahun+"};out.text="Panjang: ${p.length}\nPool karakter: $pool\nEntropi: %.1f bit\nEstimasi brute-force @10¹⁰ tebakan/detik: $time".format(Locale.US,entropy) })
    }

fun MainActivity.dataBreachCheckerTool(){
        clearPage("Data Breach Checker");addToolHeader("Data Breach Checker","Periksa email melalui API Have I Been Pwned. API key diperlukan.","HIBP");val email=edit("Email");val key=edit("HIBP API key");content.addView(email);content.addView(key);content.addView(button("Cek Breach") {val e=email.text.toString().trim();val k=key.text.toString().trim();if(!android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches()){toast("Email tidak valid");return@button};if(k.isBlank()){toast("Masukkan API key HIBP");return@button};thread{val r=runCatching{val u=URL("https://haveibeenpwned.com/api/v3/breachedaccount/"+URLEncoder.encode(e,"UTF-8")+"?truncateResponse=false");val c=u.openConnection() as HttpURLConnection;c.requestMethod="GET";c.setRequestProperty("hibp-api-key",k);c.setRequestProperty("user-agent","MyTools/2.20");c.connectTimeout=10000;c.readTimeout=10000;val code=c.responseCode;if(code==404)"Tidak ditemukan dalam breach yang dilaporkan HIBP." else if(code==200)c.inputStream.bufferedReader().use{it.readText()} else "HTTP $code: ${c.errorStream?.bufferedReader()?.use{it.readText()} ?: ""}"}.getOrElse{"Gagal: ${it.message}"};runOnUiThread{output(r)}} })
    }

fun MainActivity.secureNotesTool(){
        clearPage("Secure Notes");addToolHeader("Secure Notes","Catatan disimpan terenkripsi AES-GCM di perangkat.","NOTE");val title=edit("Judul");val note=edit("Catatan",true);val pass=edit("Master password");pass.inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD;content.addView(title);content.addView(note);content.addView(pass);content.addView(button("Simpan terenkripsi"){if(title.text.isBlank()||pass.text.isBlank()){toast("Judul dan password wajib");return@button};val data="${title.text}\n${note.text}";val enc=aesEncrypt(pass.text.toString(),data);prefs.edit().putString("secure_note_${title.text}",enc).apply();toast("Catatan terenkripsi disimpan")});content.addView(button("Buka catatan"){val enc=prefs.getString("secure_note_${title.text}",null)?:run{toast("Catatan tidak ditemukan");return@button};output(runCatching{aesDecrypt(pass.text.toString(),enc)}.getOrElse{"Password salah atau data rusak"})})
    }

fun MainActivity.totpVaultTool(){
        clearPage("2FA Manager (TOTP)");addToolHeader("2FA Manager","Simpan secret TOTP secara terenkripsi dan buat kode 6 digit.","2FA");val labelE=edit("Nama akun");val secret=edit("Base32 secret");val pass=edit("Vault password");pass.inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD;content.addView(labelE);content.addView(secret);content.addView(pass);val out=label("Belum ada kode",28f,true);content.addView(out);content.addView(button("Simpan ke Vault"){if(labelE.text.isBlank()||secret.text.isBlank()||pass.text.isBlank()){toast("Lengkapi semua field");return@button};prefs.edit().putString("totp_vault_${labelE.text}",aesEncrypt(pass.text.toString(),secret.text.toString())).apply();toast("Secret tersimpan terenkripsi")});content.addView(button("Generate Kode"){val enc=prefs.getString("totp_vault_${labelE.text}",null)?:run{toast("Akun belum tersimpan");return@button};out.text=runCatching{totp(aesDecrypt(pass.text.toString(),enc),System.currentTimeMillis()/1000/30)}.getOrElse{"Password salah / secret rusak"}})
    }

fun MainActivity.pgpTool(){
        clearPage("PGP Encrypt / Decrypt");addToolHeader("PGP Encrypt / Decrypt","OpenPGP memerlukan keyring dan library OpenPGP. MyTools menyediakan ruang kerja untuk armor/key input.","PGP");val key=edit("ASCII-armored public/private key",true);val text=edit("Pesan / armored PGP",true);content.addView(key);content.addView(text);content.addView(button("Validasi format PGP"){val s=key.text.toString();output(if(s.contains("-----BEGIN PGP")&&s.contains("-----END PGP"))"Armor PGP terdeteksi. Untuk operasi kriptografi penuh, gunakan keyring OpenPGP yang kompatibel." else "Format ASCII armor PGP belum terdeteksi.")})
    }

fun MainActivity.sshKeyGeneratorTool(){
        clearPage("SSH Key Generator");addToolHeader("SSH Key Generator","Generate RSA atau Ed25519 key pair lokal.","SSH");val type=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,arrayOf("RSA 3072","Ed25519"))};content.addView(type);val out=label("Belum dibuat",13f);content.addView(out);content.addView(button("Generate") {thread{val r=runCatching{val alg=if(type.selectedItem.toString().startsWith("RSA"))"RSA" else "Ed25519";val gen=KeyPairGenerator.getInstance(alg);if(alg=="RSA")gen.initialize(3072);val kp=gen.generateKeyPair();val priv=Base64.getMimeEncoder(64,"\n".toByteArray()).encodeToString(kp.private.encoded);val pub=Base64.getMimeEncoder(64,"\n".toByteArray()).encodeToString(kp.public.encoded);"PRIVATE KEY (PKCS#8):\n-----BEGIN PRIVATE KEY-----\n$priv\n-----END PRIVATE KEY-----\n\nPUBLIC KEY (X.509):\n-----BEGIN PUBLIC KEY-----\n$pub\n-----END PUBLIC KEY-----"}.getOrElse{"Gagal: ${it.message}"};runOnUiThread{out.text=r}}})}

fun MainActivity.certificateViewerTool(){clearPage("Certificate Viewer");addToolHeader("Certificate Viewer","Lihat detail sertifikat X.509 dari file.","CERT");content.addView(button("Pilih Sertifikat") {startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="application/x-x509-ca-cert";addCategory(Intent.CATEGORY_OPENABLE)},CERT_PICK)});content.addView(label("Mendukung sertifikat X.509/DER/PEM yang dapat diparse Android."))}

fun MainActivity.virusScannerTool(){clearPage("Virus Scanner (VirusTotal)");addToolHeader("Virus Scanner","Gunakan VirusTotal API untuk lookup hash file atau scan URL. API key milik pengguna diperlukan.","VT");val key=edit("VirusTotal API key");val target=edit("URL atau SHA-256 file");content.addView(key);content.addView(target);content.addView(button("Scan / Lookup") {val k=key.text.toString().trim();val t=target.text.toString().trim();if(k.isBlank()||t.isBlank()){toast("API key dan target wajib");return@button};thread{val r=runCatching{val endpoint=if(Regex("^[A-Fa-f0-9]{64}$").matches(t))"https://www.virustotal.com/api/v3/files/$t" else "https://www.virustotal.com/api/v3/urls/${Base64.getUrlEncoder().withoutPadding().encodeToString(t.toByteArray())}";val c=URL(endpoint).openConnection() as HttpURLConnection;c.setRequestProperty("x-apikey",k);c.connectTimeout=10000;c.readTimeout=10000;"HTTP ${c.responseCode}\n"+(if(c.responseCode in 200..299)c.inputStream else c.errorStream).bufferedReader().use{it.readText()}}.getOrElse{"Gagal: ${it.message}"};runOnUiThread{output(r)}}})}

fun MainActivity.urlSafetyTool(){clearPage("URL Safety Checker");addToolHeader("URL Safety Checker","Pemeriksaan heuristik lokal untuk indikasi URL mencurigakan.","SAFE");val e=edit("URL");content.addView(e);content.addView(button("Periksa") {val raw=e.text.toString().trim();val r=runCatching{val u=URL(if(raw.startsWith("http://")||raw.startsWith("https://"))raw else "https://$raw");val flags=mutableListOf<String>();if(u.protocol!="https")flags.add("Tidak menggunakan HTTPS");if(u.userInfo!=null)flags.add("Memiliki userinfo sebelum host");if(u.host.length>63)flags.add("Host sangat panjang");if(u.host.contains("xn--"))flags.add("Punycode/IDN terdeteksi");if(Regex("(login|verify|secure|account|wallet|gift|update)[-_].{0,12}(support|verify|login)?",RegexOption.IGNORE_CASE).containsMatchIn(u.path+u.query))flags.add("Path/query memakai kata yang sering digunakan pada halaman phishing");"Host: ${u.host}\nSkema: ${u.protocol}\n${if(flags.isEmpty())"Tidak ada indikator heuristik umum yang terdeteksi." else flags.joinToString("\n• ",prefix="Indikator:\n• ")}"}.getOrElse{"URL tidak valid: ${it.message}"};output(r)})}

fun MainActivity.steganographyTool() {
        clearPage("Steganography")
        addToolHeader("Steganography", "Sembunyikan pesan teks di bit warna gambar PNG. Proses lokal.", "STG")
        val msg=edit("Pesan yang disembunyikan",true); content.addView(msg)
        content.addView(button("Pilih Gambar → Sembunyikan") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},STEGO_ENCODE_PICK) })
        content.addView(button("Sembunyikan Pesan") {
            val uri=stegoImageUri ?: run{toast("Pilih gambar dulu");return@button}; val text=msg.text.toString(); if(text.isEmpty()){toast("Pesan kosong");return@button}
            thread { val result=runCatching{encodeStego(uri,text)}.getOrElse{"Gagal: ${it.message}"};runOnUiThread{output(result)} }
        })
        content.addView(button("Pilih Gambar → Baca Pesan") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},STEGO_DECODE_PICK) })
    }
