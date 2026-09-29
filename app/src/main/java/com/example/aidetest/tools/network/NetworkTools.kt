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


fun MainActivity.wifiInfo() {
        clearPage("Wi-Fi Info")
        val out=label("Membaca Wi-Fi...",14f); content.addView(out)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            out.text = "Izin lokasi diperlukan untuk membaca SSID Wi-Fi."
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 2001)
            return
        }
        val wm=applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val i=wm.connectionInfo
        out.text="SSID: ${i.ssid}\nBSSID: ${i.bssid}\nRSSI: ${i.rssi} dBm\nLink speed: ${i.linkSpeed} Mbps\nFrequency: ${i.frequency} MHz"
    }

fun MainActivity.dnsTool() {
        clearPage("DNS Lookup")
        val e=edit("domain"); content.addView(e)
        content.addView(button("Lookup") {
            thread { val r=runCatching { InetAddress.getAllByName(e.text.toString()).joinToString("\n") { it.hostAddress ?: "" } }.getOrElse { it.message ?: "error" }; runOnUiThread { output(r) } }
        })
    }

fun MainActivity.reverseDnsTool() {
        clearPage("Reverse DNS")
        val e=edit("IP"); content.addView(e)
        content.addView(button("Lookup") {
            thread { val r=runCatching { InetAddress.getByName(e.text.toString()).canonicalHostName }.getOrElse { it.message ?: "error" }; runOnUiThread { output(r) } }
        })
    }

fun MainActivity.portTool() {
        clearPage("Port Checker")
        val host=edit("Host"); val port=edit("Port"); content.addView(host); content.addView(port)
        content.addView(button("Check") {
            thread {
                val r=runCatching { Socket().use { it.connect(InetSocketAddress(host.text.toString(), port.text.toString().toInt()), 2500); "OPEN" } }.getOrElse { "CLOSED / ERROR: ${it.message}" }
                runOnUiThread { output(r) }
            }
        })
    }

fun MainActivity.publicIpTool() {
        clearPage("IP Publik")
        content.addView(button("Get IP") {
            thread {
                val r=runCatching { URL("https://api.ipify.org").readText() }.getOrElse { it.message ?: "error" }
                runOnUiThread { output(r) }
            }
        })
    }

fun MainActivity.pingTool() {
        clearPage("Ping")
        val e=edit("Host"); content.addView(e)
        content.addView(button("Ping") {
            thread {
                val r=runCatching {
                    val p=Runtime.getRuntime().exec(arrayOf("ping","-c","1","-W","2",e.text.toString()))
                    p.inputStream.bufferedReader().readText()
                }.getOrElse { "Ping error: ${it.message}" }
                runOnUiThread { output(r) }
            }
        })
    }

fun MainActivity.ipInfoTool() {
        clearPage("IP Address Info")
        val e=edit("IP"); content.addView(e)
        content.addView(button("Analyze") {
            output(runCatching {
                val ip=InetAddress.getByName(e.text.toString())
                val raw=ip.address
                "Host: ${ip.hostAddress}\nLoopback: ${ip.isLoopbackAddress}\nLink-local: ${ip.isLinkLocalAddress}\nSite-local: ${ip.isSiteLocalAddress}\nBytes: ${raw.joinToString(".") { (it.toInt() and 255).toString() }}"
            }.getOrElse { "IP tidak valid" })
        })
    }

fun MainActivity.sslTool() {
        clearPage("SSL Certificate")
        val e=edit("example.com:443"); content.addView(e)
        content.addView(button("Check") {
            thread {
                val r=runCatching {
                    val p=e.text.toString().split(":")
                    val host=p[0]; val port=p.getOrNull(1)?.toIntOrNull() ?: 443
                    val ctx=javax.net.ssl.SSLContext.getDefault()
                    val sock=ctx.socketFactory.createSocket() as javax.net.ssl.SSLSocket
                    sock.connect(InetSocketAddress(host,port),5000); sock.startHandshake()
                    val cert=sock.session.peerCertificates.firstOrNull()
                    sock.close()
                    cert?.toString() ?: "No certificate"
                }.getOrElse { "SSL error: ${it.message}" }
                runOnUiThread { output(r) }
            }
        })
    }

fun MainActivity.networkInfoTool() {
        clearPage("Network Info")
        toolWorkspace("Network Info", "Interface dan alamat jaringan yang tersedia di perangkat.", "network")
        toolWorkspaceSection("INTERFACES", "Daftar interface aktif dan alamatnya.")
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            interfaces?.asSequence()?.filter { it.isUp && !it.isLoopback }?.forEach { ni ->
                val addresses = ni.inetAddresses.asSequence().map { it.hostAddress ?: "" }.filter { it.isNotBlank() }.toList()
                infoRow(ni.displayName ?: ni.name, addresses.joinToString(" • "))
            }
        } catch (e: Exception) {
            infoRow("Error", e.message ?: "Tidak dapat membaca interface")
        }
        val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val ip = wm.connectionInfo.ipAddress
        val ipText = if (ip == 0) "Tidak terhubung" else listOf(ip and 255, ip shr 8 and 255, ip shr 16 and 255, ip shr 24 and 255).joinToString(".")
        infoRow("Wi-Fi IP", ipText)
    }

fun MainActivity.networkScannerTool() {
        clearPage("Network Scanner")
        toolWorkspace("Network Scanner", "Cari host dan port TCP terbuka pada subnet lokal.", "magnify-scan")
        toolWorkspaceSection("SCAN CONFIG", "Tentukan subnet dan daftar port sebelum memulai scan.")
        val subnet = edit("Contoh 192.168.1.0/24")
        val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val ip = wm.connectionInfo.ipAddress
        val defaultSubnet = if (ip != 0) {
            val a = ip and 255; val b = ip shr 8 and 255; val c = ip shr 16 and 255
            "$a.$b.$c.0/24"
        } else "192.168.1.0/24"
        subnet.setText(defaultSubnet)
        content.addView(subnet)
        val ports = edit("Port: 80,443,8080,22,21,53,139,445")
        ports.setText("80,443,8080,22,21,53,139,445")
        content.addView(ports)
        val status = label("Siap", 13f, true); content.addView(status)
        content.addView(button("Mulai Scan") {
            val range = parseCidr24(subnet.text.toString().trim())
            if (range == null) { toast("Gunakan format x.x.x.0/24"); return@button }
            val portList = ports.text.toString().split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..65535 }.distinct().take(12)
            if (portList.isEmpty()) { toast("Port tidak valid"); return@button }
            networkScanStop.set(false)
            status.text = "Scanning..."
            val resultBox = label("", 12f)
            content.addView(resultBox)
            thread {
                val found = Collections.synchronizedList(mutableListOf<String>())
                val pool = Executors.newFixedThreadPool(24)
                val jobs = (1..254).map { host ->
                    pool.submit {
                        if (networkScanStop.get()) return@submit
                        val hostIp = "${range.first}.$host"
                        for (port in portList) {
                            if (networkScanStop.get()) break
                            try {
                                Socket().use { s ->
                                    s.connect(InetSocketAddress(hostIp, port), 350)
                                    found.add("$hostIp:$port OPEN")
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
                jobs.forEach { runCatching { it.get() } }
                pool.shutdownNow()
                runOnUiThread {
                    status.text = if (networkScanStop.get()) "Dihentikan" else "Selesai"
                    resultBox.text = if (found.isEmpty()) "Tidak ditemukan port terbuka pada port yang dipilih." else found.distinct().sorted().joinToString("\n")
                }
            }
        })
        content.addView(button("Hentikan Scan") { networkScanStop.set(true) })
    }

fun MainActivity.whoisTool(){ clearPage("Whois"); val e=edit("Domain",false);e.hint="example.com";content.addView(e);content.addView(button("Lookup"){val d=e.text.toString().trim().removePrefix("https://").removePrefix("http://").substringBefore('/');if(d.isBlank()){toast("Masukkan domain");return@button};thread{runCatching{val s=Socket("whois.iana.org",43);s.soTimeout=6000;s.getOutputStream().write((d+"\\r\\n").toByteArray());val out=s.getInputStream().bufferedReader().readText().take(12000);s.close();runOnUiThread{output(out)}}.onFailure{runOnUiThread{toast("Whois gagal: ${it.message}")}}}})}

fun MainActivity.tracerouteTool(){ clearPage("Traceroute");val e=edit("Host",false);e.setText("8.8.8.8");content.addView(e);content.addView(button("Start"){val h=e.text.toString().trim();thread{val cmds=listOf(arrayOf("traceroute","-m","12","-w","1",h),arrayOf("/system/bin/traceroute","-m","12","-w","1",h));var done=false;for(c in cmds){runCatching{val p=ProcessBuilder(*c).redirectErrorStream(true).start();val o=p.inputStream.bufferedReader().readText().take(16000);p.waitFor();runOnUiThread{output(o)};done=true}.onFailure{}};if(!done)runOnUiThread{toast("Traceroute tidak tersedia di perangkat")}}})}

fun MainActivity.subnetCalculatorTool(){clearPage("Subnet Calculator");val ip=edit("IPv4",false);ip.setText("192.168.1.10");val pre=edit("Prefix",false);pre.setText("24");content.addView(ip);content.addView(pre);content.addView(button("Hitung"){val parts=ip.text.toString().split('.').mapNotNull{it.toIntOrNull()};val p=pre.text.toString().toIntOrNull();if(parts.size!=4||p==null||p !in 0..32){toast("IPv4/prefix tidak valid");return@button};val mask=if(p==0)0L else (0xffffffffL shl (32-p)) and 0xffffffffL;val addr=((parts[0].toLong() shl 24) or (parts[1].toLong() shl 16) or (parts[2].toLong() shl 8) or parts[3].toLong());val net=addr and mask;val broad=net or (0xffffffffL xor mask);output("Network: ${ipv4(net)}\\nBroadcast: ${ipv4(broad)}\\nSubnet Mask: ${ipv4(mask)}\\nPrefix: /$p\\nTotal alamat: ${if(p==32)1L else 1L shl (32-p)}")})}

fun MainActivity.networkCenterTool() {
        clearPage("Network Center")
        toolWorkspace("Network Center", "Ringkasan koneksi, interface, internet, dan alamat jaringan perangkat.", "lan-connect")
        toolWorkspaceSection("NETWORK STATUS", "Informasi dibaca langsung dari sistem Android.")
        val cm=getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val n=cm.activeNetwork; val caps=if(n!=null)cm.getNetworkCapabilities(n) else null
        infoRow("Status", if(n!=null) "Terhubung" else "Tidak terhubung")
        infoRow("Transport", when { caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)==true -> "Wi‑Fi"; caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)==true -> "Seluler"; caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)==true -> "Ethernet"; else -> "Lainnya / tidak diketahui" })
        infoRow("Internet", if(caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true) "Terverifikasi" else "Belum terverifikasi")
        runCatching{NetworkInterface.getNetworkInterfaces().asSequence().filter{it.isUp&&!it.isLoopback}.forEach{ni->val a=ni.inetAddresses.asSequence().mapNotNull{it.hostAddress}.distinct().joinToString(", ");infoRow(ni.displayName?:ni.name,a)}}
        content.addView(button("Refresh"){networkCenterTool()})
    }
