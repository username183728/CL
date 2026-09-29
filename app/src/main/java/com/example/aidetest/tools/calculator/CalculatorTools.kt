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


fun MainActivity.calculatorHub(selected: String = calculatorSelectedMode) {
        calculatorSelectedMode = calculatorModes.firstOrNull { it.id == selected }?.id ?: "basiccalc"
        clearPage("Kalkulator Lengkap", false)
        content.setPadding(dp(12), dp(8), dp(12), dp(12))

        // Header ringkas: satu layar, satu selector. Tidak ada daftar kartu yang membuat pengguna
        // harus keluar-masuk tool.
        content.addView(label("Kalkulator", 24f, true))
        content.addView(subLabel("Semua hitungan ada di satu tempat. Pilih fungsi tanpa meninggalkan halaman.", 12f))

        val selector = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(10), 0)
            background = bg(panel2, 16, line)
            isClickable = true
        }
        val selectedLabel = TextView(this).apply {
            text = calculatorModes.first { it.id == calculatorSelectedMode }.name
            textSize = 16f
            setTextColor(textMain)
            gravity = Gravity.CENTER_VERTICAL
        }
        val selectedGroup = TextView(this).apply {
            text = "  •  ${calculatorModes.first { it.id == calculatorSelectedMode }.group}"
            textSize = 12f
            setTextColor(textMuted)
            gravity = Gravity.CENTER_VERTICAL
        }
        val arrow = TextView(this).apply {
            asMdi("chevron-down")
            textSize = 22f
            setTextColor(textMuted)
            gravity = Gravity.CENTER
        }
        selector.addView(selectedLabel, LinearLayout.LayoutParams(0, dp(58), 1f))
        selector.addView(selectedGroup, LinearLayout.LayoutParams(0, dp(58), 1f))
        selector.addView(arrow, LinearLayout.LayoutParams(dp(36), dp(58)))
        selector.setOnClickListener { showCalculatorModePicker() }
        content.addView(selector, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(10) })

        val quick = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        listOf(
            "basiccalc" to "Dasar",
            "scicalc" to "Ilmiah",
            "percentcalc" to "%",
            "unitcalc" to "Konversi"
        ).forEach { (id, text) ->
            val b = Button(this).apply {
                this.text = text
                textSize = 12f
                setTextColor(if (id == calculatorSelectedMode) Color.WHITE else textMain)
                background = bg(if (id == calculatorSelectedMode) Color.rgb(35,35,39) else panel2, 14, line)
                setStateListAnimator(null)
                setOnClickListener { calculatorHub(id) }
            }
            quick.addView(b, LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        content.addView(quick, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) })

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), 0, dp(2), dp(20))
        }
        content.addView(body, LinearLayout.LayoutParams(-1, -2))

        embeddedCalculatorRender = true
        val previousContent = content
        try {
            content = body
            when (calculatorSelectedMode) {
                "basiccalc" -> calculatorTool(false)
                "scicalc" -> calculatorTool(true)
                "percentcalc" -> percentCalculator()
                "fractioncalc" -> fractionCalculator()
                "ratiocalc" -> ratioCalculator()
                "unitcalc" -> unitCalculator()
                "areacalc" -> areaCalculator()
                "volumecalc" -> volumeCalculator()
                "speedcalc" -> speedCalculator()
                "timecalc" -> timeCalculator()
                "datecalc" -> dateCalculator()
                "loancalc" -> loanCalculator()
                "fuelcalc" -> fuelCalculator()
                "pivotcalc" -> pivotPointCalculator()
                "dividercalc" -> voltageDividerCalculator()
                "dcacalc" -> dcaCalculator()
                "pwmcalc" -> pwmCalculator()
                "spritecalc" -> spriteSheetCalculator()
                "installcalc" -> installmentComparisonCalculator()
                    "powercalc" -> powerConsumptionCalculator()
                "aspectcalc" -> aspectRatioCalculator()
                "pphcalc" -> ppnPphCalculator()
                "riskcalc" -> riskRewardCalculator()
                "compoundcalc" -> compoundCalculator()
                "margincalc" -> marginTaxCalculator()
                "discountcalc" -> tieredDiscountCalculator()
                "datacalc" -> dataUnitCalculator()
                "pressurecalc" -> pressureCalculator()
                "worktimecalc" -> workTimeCalculator()
                "basecalc" -> baseCalculator()
                "equationcalc" -> equationCalculator()
            }
        } finally {
            content = previousContent
            embeddedCalculatorRender = false
        }
    }

fun MainActivity.calculatorTool(scientific: Boolean) {
        // Calculator gets its own edge-to-edge content area: no search bar and no bottom navigation.
        clearPage(if (scientific) "Kalkulator Ilmiah" else "Kalkulator Dasar")
        val calcTitle = if (scientific) "Kalkulator Ilmiah" else "Kalkulator Dasar"
        content.setPadding(dp(8), dp(4), dp(8), dp(10))
        addToolHeader(calcTitle, if (scientific) "Perhitungan ilmiah dengan keypad responsif." else "Perhitungan cepat dengan keypad yang nyaman di layar sentuh.", "calculator")
        content.setPadding(0, 0, 0, dp(10))

        val display = calcDisplay()
        display.layoutParams = LinearLayout.LayoutParams(-1, dp(118)).apply {
            setMargins(dp(10), dp(4), dp(10), dp(8))
        }
        display.textSize = if (scientific) 30f else 36f
        display.setPadding(dp(18), dp(10), dp(18), dp(10))
        content.addView(display)

        val mode = TextView(this).apply {
            text = if (scientific) "MODE ILMIAH • DEG" else "MODE DASAR"
            textSize = 11f
            setTextColor(textMuted)
            setPadding(dp(14), 0, dp(14), dp(8))
        }
        content.addView(mode)

        val grid = GridLayout(this).apply {
            columnCount = if (scientific) 5 else 4
            useDefaultMargins = false
            setPadding(dp(8), 0, dp(8), 0)
        }

        // Basic mode follows the reference calculator layout:
        // AC, +/-, %, ÷
        // 7, 8, 9, ×
        // 4, 5, 6, −
        // 1, 2, 3, =
        // 0, ., DEL, C
        //
        // Most importantly, "=" is a real button and is wired to evaluateExpression().
        val keys = if (scientific) {
            listOf(
                "sin","cos","tan","log","ln",
                "√","x²","xʸ","(",")",
                "7","8","9","÷","DEL",
                "4","5","6","×","C",
                "1","2","3","−","=",
                "0",".","%","+","π"
            )
        } else {
            listOf(
                "AC","±","%","÷",
                "7","8","9","×",
                "4","5","6","−",
                "1","2","3","=",
                "0",".","DEL","C"
            )
        }

        keys.forEach { key ->
            val keyButton = calcButton(key) {
                when (key) {
                    "C", "AC" -> display.setText("")

                    "DEL" -> {
                        if (display.text.isNotEmpty()) {
                            display.setText(display.text.dropLast(1))
                            display.setSelection(display.text.length)
                        }
                    }

                    "±" -> {
                        val current = display.text.toString()
                        if (current.isBlank()) {
                            display.setText("-")
                        } else if (current.startsWith("-")) {
                            display.setText(current.substring(1))
                        } else {
                            display.setText("-$current")
                        }
                        display.setSelection(display.text.length)
                    }

                    "=" -> {
                        val result = runCatching {
                            evaluateExpression(display.text.toString(), scientific)
                        }.getOrElse {
                            "Error: ${it.message ?: "input"}"
                        }
                        display.setText(result)
                        display.setSelection(display.text.length)
                    }

                    "sin","cos","tan","log","ln","x²" -> display.append(key + "(")
                    "√" -> display.append("sqrt(")
                    "xʸ" -> display.append("^")
                    "×" -> display.append("*")
                    "÷" -> display.append("/")
                    "−" -> display.append("-")
                    "π" -> display.append("pi")
                    else -> display.append(key)
                }
            }

            // The reference uses a high-contrast equals key.
            if (!scientific && key == "=") {
                keyButton.setTextColor(Color.BLACK)
                keyButton.background = bg(Color.rgb(245, 245, 247), 18)
            } else if (!scientific && (key == "÷" || key == "×" || key == "−" || key == "%")) {
                keyButton.setTextColor(Color.rgb(245, 245, 247))
            }

            grid.addView(keyButton)
        }

        content.addView(grid, LinearLayout.LayoutParams(-1, if (embeddedCalculatorRender) -2 else 0).apply {
            if (!embeddedCalculatorRender) weight = 1f
        })

        if (scientific) {
            content.addView(
                subLabel(
                    "Mendukung + − × ÷ %, kurung, pangkat, √, sin, cos, tan, log, ln, π.",
                    11f
                ).apply {
                    setPadding(dp(14), dp(6), dp(14), 0)
                }
            )
        }
    }

fun MainActivity.percentCalculator() {
        clearPage("Persentase")
        content.addView(label("Kalkulator Persentase",22f,true))
        val a=edit("Nilai"); val p=edit("Persen (%)"); content.addView(a); content.addView(p)
        content.addView(button("Hitung X% dari nilai") { val x=a.num(); val y=p.num(); output(if(x==null||y==null) "Input tidak valid" else "${fmt(x*y/100)}") })
        content.addView(button("Berapa % X dari Y") { val x=a.num(); val y=p.num(); output(if(x==null||y==null||y==0.0) "Input tidak valid" else "${fmt(x/y*100)}%") })
        content.addView(button("Tambah X% ke nilai") { val x=a.num(); val y=p.num(); output(if(x==null||y==null) "Input tidak valid" else "${fmt(x*(1+y/100))}") })
        content.addView(button("Kurangi X% dari nilai") { val x=a.num(); val y=p.num(); output(if(x==null||y==null) "Input tidak valid" else "${fmt(x*(1-y/100))}") })
    }

fun MainActivity.fractionCalculator() {
        clearPage("Pecahan")
        content.addView(label("Operasi Pecahan",22f,true))
        val a=edit("Pecahan A, contoh 3/4"); val b=edit("Pecahan B, contoh 1/2"); content.addView(a); content.addView(b)
        listOf("+","−","×","÷").forEach { op -> content.addView(button("A $op B") { output(fractionOp(a.text.toString(), b.text.toString(), op)) }) }
    }

fun MainActivity.ratioCalculator() { twoFields("Rasio & Proporsi","A","B","Sederhanakan rasio") { a,b -> val scale=1000000.0; val ai=kotlin.math.round(a*scale).toLong(); val bi=kotlin.math.round(b*scale).toLong(); val g=gcd(kotlin.math.abs(ai),kotlin.math.abs(bi)); "${ai/g} : ${bi/g}" } }

fun MainActivity.unitCalculator() {
        clearPage("Konverter Satuan")
        content.addView(label("Konverter Satuan",22f,true))
        val input=edit("Nilai"); content.addView(input)
        val from=Spinner(this); val to=Spinner(this)
        val units=arrayOf("meter","kilometer","centimeter","milimeter","inch","feet","yard","mile","gram","kilogram","pound","celsius","fahrenheit","kelvin","reamur","mps","kmh","mph","pascal","kpa","bar","psi")
        listOf(from,to).forEach { it.adapter=ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, units); content.addView(it, LinearLayout.LayoutParams(-1,dp(50)).apply{bottomMargin=dp(7)}) }
        content.addView(button("Konversi") { val v=input.num(); output(if(v==null)"Input tidak valid" else "${fmt(convertUnit(v,from.selectedItem.toString(),to.selectedItem.toString()))} ${to.selectedItem}") })
    }

fun MainActivity.areaCalculator() {
        clearPage("Luas & Keliling")
        content.addView(label("Luas & Keliling",22f,true))
        val shape=Spinner(this); val shapes=arrayOf("Persegi","Persegi panjang","Segitiga","Lingkaran")
        shape.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,shapes); content.addView(shape)
        val a=edit("Sisi / panjang"); val b=edit("Lebar / tinggi (jika perlu)"); content.addView(a); content.addView(b)
        content.addView(button("Hitung") { val x=a.num(); val y=b.num(); if(x==null) output("Input tidak valid") else when(shape.selectedItemPosition){0->output("Luas=${fmt(x*x)} • Keliling=${fmt(4*x)}");1->if(y==null)output("Masukkan lebar")else output("Luas=${fmt(x*y)} • Keliling=${fmt(2*(x+y))}");2->if(y==null)output("Masukkan tinggi")else output("Luas=${fmt(.5*x*y)}");3->output("Luas=${fmt(Math.PI*x*x)} • Keliling=${fmt(2*Math.PI*x)}") } })
    }

fun MainActivity.volumeCalculator() {
        clearPage("Volume")
        content.addView(label("Kalkulator Volume",22f,true))
        val shape = Spinner(this)
        shape.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("Kubus","Balok","Tabung","Bola"))
        content.addView(shape)
        val a = edit("Ukuran / radius")
        val b = edit("Lebar / tinggi")
        val c = edit("Panjang / tinggi")
        content.addView(a)
        content.addView(b)
        content.addView(c)
        content.addView(button("Hitung Volume") {
            val x = a.num()
            val y = b.num()
            val z = c.num()
            val result = when (shape.selectedItemPosition) {
                0 -> if (x == null) "Input tidak valid" else fmt(x * x * x)
                1 -> if (x == null || y == null || z == null) "Butuh 3 ukuran" else fmt(x * y * z)
                2 -> if (x == null || y == null) "Butuh radius + tinggi" else fmt(Math.PI * x * x * y)
                else -> if (x == null) "Input tidak valid" else fmt(4.0 / 3.0 * Math.PI * x * x * x)
            }
            output(result)
        })
    }

fun MainActivity.speedCalculator() {
        clearPage("Kecepatan")
        content.addView(label("Jarak • Waktu • Kecepatan",22f,true))
        val d = edit("Jarak")
        val t = edit("Waktu (jam)")
        content.addView(d)
        content.addView(t)
        content.addView(button("Hitung kecepatan") {
            val x = d.num()
            val y = t.num()
            output(if (x == null || y == null || y == 0.0) "Input tidak valid" else "Kecepatan = ${fmt(x / y)} unit/jam")
        })
        content.addView(button("Hitung jarak dari kecepatan × waktu") {
            val x = d.num()
            val y = t.num()
            output(if (x == null || y == null) "Input tidak valid" else "Jarak = ${fmt(x * y)} unit")
        })
    }

fun MainActivity.timeCalculator() {
        clearPage("Waktu & Durasi")
        content.addView(label("Konversi Durasi",22f,true))
        val v = edit("Detik")
        content.addView(v)
        content.addView(button("Konversi") {
            val x = v.num()
            if (x == null || x < 0) {
                output("Input tidak valid")
            } else {
                val sec = x.toLong()
                val h = sec / 3600
                val m = (sec % 3600) / 60
                val ss = sec % 60
                output("$h jam $m menit $ss detik")
            }
        })
    }

fun MainActivity.dateCalculator() {
        clearPage("Selisih Tanggal")
        content.addView(label("Selisih dua tanggal",22f,true))
        val a=edit("Tanggal 1: YYYY-MM-DD"); val b=edit("Tanggal 2: YYYY-MM-DD")
        content.addView(a); content.addView(b)
        content.addView(button("Hitung hari") {
            output(runCatching {
                val f=SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient=false }
                val d1=f.parse(a.text.toString().trim()) ?: error("tanggal")
                val d2=f.parse(b.text.toString().trim()) ?: error("tanggal")
                "${kotlin.math.abs((d2.time-d1.time)/86400000L)} hari"
            }.getOrElse{"Format tanggal: YYYY-MM-DD"})
        })
        content.addView(button("Hitung umur dari Tanggal 1") {
            output(runCatching {
                val f=SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient=false }; val birth=f.parse(a.text.toString().trim())!!; val now=Calendar.getInstance(); val dob=Calendar.getInstance().apply{time=birth}; var years=now.get(Calendar.YEAR)-dob.get(Calendar.YEAR); if(now.get(Calendar.DAY_OF_YEAR)<dob.get(Calendar.DAY_OF_YEAR)) years--; val days=((now.timeInMillis-birth.time)/86400000L).coerceAtLeast(0); "Umur sekitar $years tahun\nTotal hari hidup: $days"
            }.getOrElse{"Format tanggal: YYYY-MM-DD"})
        })
    }

fun MainActivity.loanCalculator() { clearPage("Cicilan Pinjaman"); content.addView(label("Kalkulator cicilan",22f,true)); val principal=edit("Pokok pinjaman"); val rate=edit("Bunga tahunan (%)"); val months=edit("Tenor (bulan)"); content.addView(principal);content.addView(rate);content.addView(months); content.addView(button("Hitung cicilan") {val p=principal.num();val r=rate.num();val n=months.num(); if(p==null||r==null||n==null||n<=0)output("Input tidak valid")else{val m=r/100/12; val pay=if(m==0.0)p/n else p*m*Math.pow(1+m,n)/(Math.pow(1+m,n)-1); output("Cicilan ≈ ${fmt(pay)} per bulan\nTotal ≈ ${fmt(pay*n)}")}}) }

fun MainActivity.fuelCalculator() {
        clearPage("Konsumsi BBM")
        content.addView(label("Konsumsi BBM", 22f, true))
        val distance = edit("Jarak (km)")
        val fuel = edit("BBM (liter)")
        val price = edit("Harga per liter (opsional)")
        content.addView(distance)
        content.addView(fuel)
        content.addView(price)
        content.addView(button("Hitung") {
            val d = distance.num()
            val f = fuel.num()
            val p = price.num()
            if (d == null || f == null || f <= 0) {
                output("Input tidak valid")
            } else {
                val kmpl = d / f
                val l100 = f / d * 100
                val cost = if (p == null) "" else "\nBiaya ≈ ${fmt(f * p)}"
                output("${fmt(kmpl)} km/l\n${fmt(l100)} L/100 km$cost")
            }
        })
    }

fun MainActivity.pivotPointCalculator() {
        clearPage("Pivot Point")
        content.addView(label("Pivot Point • Standard",22f,true))
        content.addView(subLabel("Level Support S1-S3 dan Resistance R1-R3 dari High, Low, Close.",12f))
        val h=edit("High"); val l=edit("Low"); val c=edit("Close")
        content.addView(h); content.addView(l); content.addView(c)
        content.addView(button("Hitung Pivot") {
            val high=h.num(); val low=l.num(); val close=c.num()
            if(high==null||low==null||close==null||high<low) output("High/Low tidak valid.") else {
                val p=(high+low+close)/3.0
                val r1=2*p-low; val s1=2*p-high
                val r2=p+(high-low); val s2=p-(high-low)
                val r3=high+2*(p-low); val s3=low-2*(high-p)
                output("Pivot P = ${fmt(p)}\nS1 = ${fmt(s1)}\nS2 = ${fmt(s2)}\nS3 = ${fmt(s3)}\nR1 = ${fmt(r1)}\nR2 = ${fmt(r2)}\nR3 = ${fmt(r3)}")
            }
        })
    }

fun MainActivity.voltageDividerCalculator() {
        clearPage("Voltage Divider")
        content.addView(label("Pembagi Tegangan",22f,true))
        content.addView(subLabel("Vout = Vin × R2 / (R1 + R2)",12f))
        val vin=edit("Vin (V)"); val r1=edit("R1 (ohm)"); val r2=edit("R2 (ohm)"); val target=edit("Target Vout (V)")
        listOf(vin,r1,r2,target).forEach{content.addView(it)}
        content.addView(button("Hitung Vout") {
            val v=vin.num(); val a=r1.num(); val b=r2.num()
            if(v==null||a==null||b==null||v<0||a<=0||b<=0) output("Input tidak valid.")
            else output("Vout = ${fmt(v*b/(a+b))} V\nArus divider = ${fmt(v/(a+b)*1000)} mA")
        })
        content.addView(button("Cari R2 untuk Target Vout") {
            val v=vin.num(); val a=r1.num(); val t=target.num()
            if(v==null||a==null||t==null||a<=0||t<=0||t>=v) output("Vin, R1 dan target Vout tidak valid.")
            else output("R2 ≈ ${fmt(t*a/(v-t))} ohm")
        })
        content.addView(button("Cari R1 untuk Target Vout") {
            val v=vin.num(); val b=r2.num(); val t=target.num()
            if(v==null||b==null||t==null||b<=0||t<=0||t>=v) output("Vin, R2 dan target Vout tidak valid.")
            else output("R1 ≈ ${fmt(b*(v/t-1))} ohm")
        })
    }

fun MainActivity.dcaCalculator() {
        clearPage("Averaging Down & DCA")
        content.addView(label("Averaging Down & DCA",22f,true))
        content.addView(subLabel("Hitung harga rata-rata dan tambahan modal untuk target rata-rata.",12f))
        val oldPrice=edit("Harga posisi lama"); val oldQty=edit("Jumlah/unit lama"); val newPrice=edit("Harga pembelian baru"); val newQty=edit("Jumlah/unit baru")
        listOf(oldPrice,oldQty,newPrice,newQty).forEach{content.addView(it)}
        content.addView(button("Hitung rata-rata baru") {
            val p1=oldPrice.num(); val q1=oldQty.num(); val p2=newPrice.num(); val q2=newQty.num()
            if(p1==null||q1==null||p2==null||q2==null||q1<=0||q2<0) output("Input tidak valid.")
            else { val avg=(p1*q1+p2*q2)/(q1+q2); output("Total unit = ${fmt(q1+q2)}\nModal total = ${fmt(p1*q1+p2*q2)}\nHarga rata-rata = ${fmt(avg)}") }
        })
        val target=edit("Target harga rata-rata"); content.addView(target)
        content.addView(button("Cari tambahan unit & modal") {
            val p1=oldPrice.num(); val q1=oldQty.num(); val p2=newPrice.num(); val t=target.num()
            if(p1==null||q1==null||p2==null||t==null||q1<=0||p2<=0) output("Input tidak valid.")
            else {
                val denom=t-p2
                if(kotlin.math.abs(denom)<1e-12) output("Target sama dengan harga pembelian baru; jumlah unit teoritis tidak terbatas.")
                else { val q2=(p1-t)*q1/denom; if(q2<0) output("Target tidak dapat dicapai dengan harga pembelian baru ini.") else output("Tambahan unit ≈ ${fmt(q2)}\nTambahan modal ≈ ${fmt(q2*p2)}\nRata-rata target = ${fmt(t)}") }
            }
        })
    }

fun MainActivity.pwmCalculator() {
        clearPage("PWM & Duty Cycle")
        content.addView(label("PWM & Duty Cycle",22f,true))
        val supply=edit("Tegangan supply (V)"); val duty=edit("Duty cycle (%)"); val freq=edit("Frekuensi (Hz)")
        listOf(supply,duty,freq).forEach{content.addView(it)}
        content.addView(button("Hitung PWM") {
            val v=supply.num(); val d=duty.num(); val f=freq.num()
            if(v==null||d==null||f==null||d<0||d>100||f<=0) output("Input tidak valid.")
            else { val avg=v*d/100; val periodUs=1_000_000.0/f; output("Tegangan rata-rata ≈ ${fmt(avg)} V\nFrekuensi = ${fmt(f)} Hz\nPeriode ≈ ${fmt(periodUs)} µs\nHIGH time ≈ ${fmt(periodUs*d/100)} µs") }
        })
        val target=edit("Target tegangan rata-rata (V)"); content.addView(target)
        content.addView(button("Hitung Duty dari Target") { val v=supply.num(); val t=target.num(); if(v==null||t==null||v<=0||t<0||t>v) output("Target harus 0 sampai Vin.") else output("Duty cycle ≈ ${fmt(t/v*100)}%") })
    }

fun MainActivity.spriteSheetCalculator() {
        clearPage("Sprite Sheet Grid")
        content.addView(label("Sprite Sheet / Grid",22f,true))
        content.addView(subLabel("Hitung ukuran frame dan jumlah baris/kolom secara tepat.",12f))
        val sheetW=edit("Lebar sprite sheet (px)"); val sheetH=edit("Tinggi sprite sheet (px)"); val cols=edit("Jumlah kolom"); val rows=edit("Jumlah baris")
        listOf(sheetW,sheetH,cols,rows).forEach{content.addView(it)}
        content.addView(button("Hitung frame") { val w=sheetW.num();val h=sheetH.num();val c=cols.num();val r=rows.num(); if(w==null||h==null||c==null||r==null||w<=0||h<=0||c<=0||r<=0) output("Input tidak valid.") else output("Frame = ${fmt(w/c)} × ${fmt(h/r)} px\nTotal frame = ${fmt(c*r)}\nGrid = ${fmt(c)} kolom × ${fmt(r)} baris") })
        val frameW=edit("Lebar frame (px)"); val frameH=edit("Tinggi frame (px)"); content.addView(frameW);content.addView(frameH)
        content.addView(button("Hitung grid dari frame") { val w=sheetW.num();val h=sheetH.num();val fw=frameW.num();val fh=frameH.num(); if(w==null||h==null||fw==null||fh==null||fw<=0||fh<=0) output("Input tidak valid.") else output("Kolom = ${fmt(w/fw)}\nBaris = ${fmt(h/fh)}\nTotal frame = ${fmt(w/fw*h/fh)}") })
    }

fun MainActivity.installmentComparisonCalculator() {
        clearPage("Flat vs Efektif / Anuitas")
        content.addView(label("Bunga Flat vs Efektif/Anuitas",22f,true))
        val principal=edit("Pokok pinjaman"); val rate=edit("Bunga tahunan (%)"); val months=edit("Tenor (bulan)")
        listOf(principal,rate,months).forEach{content.addView(it)}
        content.addView(button("Bandingkan") { val p=principal.num();val annual=rate.num();val n=months.num(); if(p==null||annual==null||n==null||p<=0||n<=0||annual<0) output("Input tidak valid.") else { val flatInterest=p*(annual/100)/12; val flatPay=p/n+flatInterest; val flatTotal=flatPay*n; val m=annual/100/12; val annPay=if(m==0.0)p/n else p*m*Math.pow(1+m,n)/(Math.pow(1+m,n)-1); val annTotal=annPay*n; output("FLAT\nCicilan/bulan ≈ ${fmt(flatPay)}\nTotal bayar ≈ ${fmt(flatTotal)}\nTotal bunga ≈ ${fmt(flatTotal-p)}\n\nEFEKTIF/ANUITAS\nCicilan bulanan ≈ ${fmt(annPay)}\nTotal bayar ≈ ${fmt(annTotal)}\nTotal bunga ≈ ${fmt(annTotal-p)}") } })
    }

fun MainActivity.powerConsumptionCalculator() {
        clearPage("Konsumsi Listrik")
        content.addView(label("Konsumsi Listrik & Biaya",22f,true))
        val watts=edit("Daya perangkat (W) total"); val hours=edit("Jam pemakaian per hari"); val days=edit("Hari per bulan"); val tariff=edit("Tarif listrik per kWh")
        listOf(watts,hours,days,tariff).forEach{content.addView(it)}
        content.addView(button("Hitung") { val w=watts.num();val h=hours.num();val d=days.num();val t=tariff.num(); if(w==null||h==null||d==null||t==null||w<0||h<0||d<0||t<0) output("Input tidak valid.") else { val kwhDay=w*h/1000; val kwhMonth=kwhDay*d; output("Energi/hari = ${fmt(kwhDay)} kWh\nEnergi/bulan = ${fmt(kwhMonth)} kWh\nBiaya/hari ≈ ${fmt(kwhDay*t)}\nBiaya/bulan ≈ ${fmt(kwhMonth*t)}") } })
    }

fun MainActivity.aspectRatioCalculator() {
        clearPage("Aspect Ratio")
        content.addView(label("Aspect Ratio & Skala Resolusi",22f,true))
        val w=edit("Lebar (px)"); val h=edit("Tinggi (px)"); content.addView(w);content.addView(h)
        content.addView(button("Hitung rasio") { val a=w.num();val b=h.num(); if(a==null||b==null||a<=0||b<=0) output("Input tidak valid.") else { val ai=kotlin.math.round(a).toLong();val bi=kotlin.math.round(b).toLong();val g=gcd(kotlin.math.abs(ai),kotlin.math.abs(bi)); output("Aspect ratio ≈ ${fmt(a/b)}\nRasio sederhana = ${ai/g}:${bi/g}") } })
        val ratioW=edit("Rasio lebar, contoh 16"); val ratioH=edit("Rasio tinggi, contoh 9"); val known=edit("Ukuran yang diketahui (px)"); content.addView(ratioW);content.addView(ratioH);content.addView(known)
        val mode=Spinner(this); mode.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,arrayOf("Diketahui lebar → cari tinggi","Diketahui tinggi → cari lebar")); content.addView(mode,LinearLayout.LayoutParams(-1,dp(50)).apply{bottomMargin=dp(7)})
        content.addView(button("Hitung ukuran proporsional") { val rw=ratioW.num();val rh=ratioH.num();val k=known.num(); if(rw==null||rh==null||k==null||rw<=0||rh<=0||k<=0) output("Input tidak valid.") else if(mode.selectedItemPosition==0) output("Resolusi = ${fmt(k)} × ${fmt(k*rh/rw)} px") else output("Resolusi = ${fmt(k*rw/rh)} × ${fmt(k)} px") })
    }

fun MainActivity.ppnPphCalculator() {
        clearPage("PPN & PPh Final")
        content.addView(label("PPN & PPh Final",22f,true))
        content.addView(subLabel("Masukkan tarif pajak sendiri agar sesuai aturan/kontrak yang berlaku.",12f))
        val gross=edit("Nilai bruto / DPP"); val ppn=edit("PPN (%)"); val pph=edit("PPh Final (%)")
        listOf(gross,ppn,pph).forEach{content.addView(it)}
        content.addView(button("Hitung invoice") { val g=gross.num();val pv=ppn.num();val ph=pph.num(); if(g==null||pv==null||ph==null||g<0||pv<0||ph<0) output("Input tidak valid.") else { val ppnVal=g*pv/100; val pphVal=g*ph/100; val invoice=g+ppnVal; val nett=g+ppnVal-pphVal; output("DPP = ${fmt(g)}\nPPN = ${fmt(ppnVal)}\nTotal invoice = ${fmt(invoice)}\nPPh Final = ${fmt(pphVal)}\nNett setelah PPh = ${fmt(nett)}") } })
    }

fun MainActivity.riskRewardCalculator() {
        clearPage("Risk-Reward & Position Sizing")
        content.addView(label("Risk-Reward & Position Sizing",22f,true))
        val capital=edit("Total modal"); val risk=edit("Risiko (%) contoh 1-2"); val entry=edit("Harga entry"); val stop=edit("Stop Loss"); val target=edit("Target harga (opsional)"); val lot=edit("Ukuran 1 lot (opsional, default 1)")
        listOf(capital,risk,entry,stop,target,lot).forEach{content.addView(it)}
        content.addView(button("Hitung posisi") {
            val c=capital.num();val r=risk.num();val e=entry.num();val sl=stop.num();val t=target.num();val ls=lot.num()?:1.0
            if(c==null||r==null||e==null||sl==null||r<=0||e==sl||ls<=0) output("Input tidak valid.") else {
                val riskMoney=c*r/100; val riskUnit=kotlin.math.abs(e-sl); val qty=riskMoney/riskUnit; val lots=qty/ls
                val rr=if(t==null) null else kotlin.math.abs(t-e)/riskUnit
                output("Modal risiko: ${fmt(riskMoney)}\nRisiko/unit: ${fmt(riskUnit)}\nUkuran posisi: ${fmt(qty)} unit\nLot: ${fmt(lots)}${if(rr!=null) "\nRisk-Reward: 1 : ${fmt(rr)}" else ""}")
            }
        })
    }

fun MainActivity.compoundCalculator() {
        clearPage("Compound & Target Tabungan")
        content.addView(label("Compound Interest & Target Tabungan",22f,true))
        val initial=edit("Modal awal");val contribution=edit("Setoran berkala");val rate=edit("Bunga/return tahunan (%)");val periods=edit("Jumlah periode (bulan)")
        listOf(initial,contribution,rate,periods).forEach{content.addView(it)}
        val freq=Spinner(this);freq.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,arrayOf("Bulanan","Mingguan"));content.addView(freq)
        content.addView(button("Proyeksikan") {
            val p=initial.num();val add=contribution.num();val annual=rate.num();val months=periods.num()
            if(p==null||add==null||annual==null||months==null||months<0) output("Input tidak valid.") else {
                val n=if(freq.selectedItemPosition==0) months.toInt() else kotlin.math.round(months*52.0/12.0).toInt(); val ratePer=if(freq.selectedItemPosition==0) annual/100/12 else annual/100/52
                val fv=if(ratePer==0.0) p+add*n else p*Math.pow(1.0+ratePer,n.toDouble())+add*((Math.pow(1.0+ratePer,n.toDouble())-1.0)/ratePer)
                output("Periode: $n\nProyeksi akhir: ${fmt(fv)}\nTotal setoran: ${fmt(p+add*n)}\nPertumbuhan: ${fmt(fv-(p+add*n))}")
            }
        })
        val target=edit("Target nominal (opsional)"); content.addView(target)
        content.addView(button("Hitung setoran bulanan ke target") {
            val tar=target.num();val p=initial.num();val annual=rate.num();val m=periods.num()
            if(tar==null||p==null||annual==null||m==null||m<=0) output("Isi target, modal awal, return tahunan, dan periode.") else {
                val rr=annual/100/12; val n=m.toInt(); val need=if(rr==0.0)(tar-p)/n else (tar-p*Math.pow(1.0+rr,n.toDouble()))*rr/(Math.pow(1.0+rr,n.toDouble())-1.0); output("Setoran bulanan yang diperlukan: ${fmt(kotlin.math.max(0.0,need))}")
            }
        })
    }

fun MainActivity.marginTaxCalculator() {
        clearPage("Margin & PPN/Pajak")
        content.addView(label("Harga Jual • Margin • Pajak",22f,true))
        val cogs=edit("COGS / modal barang");val margin=edit("Target margin (%)");val tax=edit("PPN / pajak (%)")
        listOf(cogs,margin,tax).forEach{content.addView(it)}
        content.addView(button("Hitung harga jual") {
            val c=cogs.num();val m=margin.num();val t=tax.num()?:0.0
            if(c==null||m==null||m<0||m>=100||t<0) output("Input tidak valid. Margin harus 0-99.99%.") else {
                val before=c/(1-m/100); val taxMoney=before*t/100; output("Harga sebelum pajak: ${fmt(before)}\nPajak: ${fmt(taxMoney)}\nHarga akhir: ${fmt(before+taxMoney)}\nLaba kotor: ${fmt(before-c)}")
            }
        })
    }

fun MainActivity.tieredDiscountCalculator() {
        clearPage("Diskon Bertingkat")
        content.addView(label("Diskon Bertingkat",22f,true))
        val price=edit("Harga awal");val d1=edit("Diskon 1 (%)");val d2=edit("Diskon 2 (%)");val d3=edit("Diskon 3 (%) opsional");listOf(price,d1,d2,d3).forEach{content.addView(it)}
        content.addView(button("Hitung harga akhir") {
            val p=price.num();val a=d1.num();val b=d2.num();val c=d3.num()?:0.0
            if(p==null||a==null||b==null||a<0||b<0||c<0||a>100||b>100||c>100) output("Input diskon tidak valid.") else { val end=p*(1-a/100)*(1-b/100)*(1-c/100); output("Harga akhir: ${fmt(end)}\nTotal diskon efektif: ${fmt((1-end/p)*100)}%\nHemat: ${fmt(p-end)}") }
        })
    }

fun MainActivity.dataUnitCalculator() {
        clearPage("Ukuran Data Digital")
        content.addView(label("Byte • KB • MB • GB • TB",22f,true))
        val input=edit("Nilai");content.addView(input);val from=Spinner(this);val to=Spinner(this);val units=arrayOf("Byte","KB","MB","GB","TB");from.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,units);to.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,units);content.addView(from);content.addView(to)
        content.addView(button("Konversi") { val v=input.num(); output(if(v==null)"Input tidak valid" else "${fmt(v*Math.pow(1024.0,from.selectedItemPosition-to.selectedItemPosition.toDouble()))} ${to.selectedItem}") })
    }

fun MainActivity.pressureCalculator() {
        clearPage("Konverter Tekanan")
        content.addView(label("Konverter Tekanan",22f,true))
        val input=edit("Nilai"); content.addView(input)
        val units=arrayOf("Pa","kPa","bar","psi")
        val from=Spinner(this); val to=Spinner(this)
        from.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,units); to.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,units)
        content.addView(from); content.addView(to)
        content.addView(button("Konversi") {
            val v=input.num(); output(if(v==null) "Input tidak valid" else {
                val base=v*when(from.selectedItemPosition){0->1.0;1->1000.0;2->100000.0;else->6894.757293}
                val result=base/when(to.selectedItemPosition){0->1.0;1->1000.0;2->100000.0;else->6894.757293}
                "${fmt(result)} ${to.selectedItem}"
            })
        })
    }

fun MainActivity.workTimeCalculator() {
        clearPage("Jam Kerja")
        content.addView(label("Durasi Jam Kerja",22f,true))
        val start=edit("Mulai HH:mm");val end=edit("Selesai HH:mm");val breakMin=edit("Istirahat (menit)",false);listOf(start,end,breakMin).forEach{content.addView(it)}
        content.addView(button("Hitung durasi") {
            output(runCatching { val f=SimpleDateFormat("HH:mm",Locale.US).apply{isLenient=false}; val s=f.parse(start.text.toString())!!.time; var e=f.parse(end.text.toString())!!.time; if(e<s)e+=86400000; val br=breakMin.num()?:0.0; val mins=((e-s)/60000.0-br).coerceAtLeast(0.0); "Durasi kerja: ${fmt(mins/60)} jam\n${mins.toLong()} menit" }.getOrElse{"Format waktu harus HH:mm"})
        })
    }

fun MainActivity.baseCalculator() {
        clearPage("Basis Angka")
        val e = edit("Masukkan angka, mis. 101101 atau FF")
        content.addView(e)
        val from = Spinner(this)
        from.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("2", "8", "10", "16"))
        content.addView(from)
        content.addView(button("Konversi ke semua basis") {
            try {
                val radix = from.selectedItem.toString().toInt()
                val raw = e.text.toString().trim()
                val n = raw.toLong(radix)
                val result = "BIN  " + n.toString(2) + "\n" +
                        "OCT  " + n.toString(8) + "\n" +
                        "DEC  " + n.toString(10) + "\n" +
                        "HEX  " + n.toString(16).toUpperCase(Locale.getDefault())
                output(result)
            } catch (ex: Exception) {
                output("Angka tidak valid untuk basis yang dipilih.")
            }
        })
    }

fun MainActivity.equationCalculator() { clearPage("Persamaan Linear"); content.addView(label("ax + b = c",22f,true)); val a=edit("a"); val b=edit("b"); val c=edit("c"); content.addView(a);content.addView(b);content.addView(c); content.addView(button("Cari x") {val aa=a.num();val bb=b.num();val cc=c.num();output(if(aa==null||bb==null||cc==null||aa==0.0)"Input tidak valid / a tidak boleh 0" else "x = ${fmt((cc-bb)/aa)}")}) }
