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


fun MainActivity.financeReaderTool(){
        clearPage("Pengelola Keuangan")
        val db=FinanceDb(this);db.processDueRecurring()
        content.addView(label("Pengelola Keuangan",22f,true));content.addView(subLabel("Offline-first • pencatatan manual, wallet, anggaran, target, ekspor dan backup lokal.",12f))
        val (from,to)=db.monthRange();val income=db.totalByType("masuk",from,to);val expense=db.totalByType("keluar",from,to);sectionTitle("Ringkasan Bulan Ini");val sum=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};sum.addView(financeSummaryBox("Pemasukan",income,Color.rgb(80, 80, 84)),LinearLayout.LayoutParams(0,-2,1f).apply{rightMargin=dp(5)});sum.addView(financeSummaryBox("Pengeluaran",expense,Color.rgb(120, 120, 124)),LinearLayout.LayoutParams(0,-2,1f).apply{leftMargin=dp(5)});content.addView(sum);content.addView(label("Saldo bersih: ${MoneyFormatter.format(income-expense)}",14f,true).apply{setPadding(dp(2),dp(10),dp(2),dp(4))})
        val anomalyHint=txsForInsight(db); if(anomalyHint.isNotBlank()) content.addView(subLabel(anomalyHint,11f))
        sectionTitle("Wallet");db.wallets().forEach{w->val bal=db.totalByTypeForWallet("masuk",w)-db.totalByTypeForWallet("keluar",w);content.addView(subLabel("$w  •  Rp${fmtRupiah(bal)}",12f))}
        sectionTitle("Pengeluaran per Kategori");val byCat=db.sumByCategory("keluar",from,to);if(byCat.isEmpty())content.addView(subLabel("Belum ada pengeluaran bulan ini.",12f))else{val maxV=byCat.maxOf{it.second};byCat.forEach{(cat,amt)->content.addView(financeCategoryBar(cat,amt,maxV))}}
        sectionTitle("Anggaran Kategori","Atur"){showBudgetDialog(db)};val budgets=db.getBudgets();if(budgets.isEmpty())content.addView(subLabel("Belum ada anggaran.",12f))else budgets.forEach{(cat,limit)->content.addView(financeBudgetRow(cat,byCat.find{it.first==cat}?.second?:0.0,limit))}
        sectionTitle("Target Tabungan","Kelola"){showSavingsGoalDialog(db)};db.goals().forEach{g->val current=db.goalProgress(g[1] as String);val target=g[2] as Double;val pct=(current/target*100).coerceIn(0.0,100.0);content.addView(subLabel("${g[1]} • Rp${fmtRupiah(current)} / Rp${fmtRupiah(target)} • ${pct.toInt()}%",12f))}
        sectionTitle("Transaksi","Filter"){showFinanceFilterDialog(db)};val txs=db.searchTx(financeSearchQuery,financeCategoryFilter,financeWalletFilter);if(txs.isEmpty())content.addView(subLabel("Belum ada transaksi atau filter tidak menemukan hasil.",12f))else txs.take(100).forEach{content.addView(financeTxRow(db,it))}
        content.addView(subLabel("Gunakan + untuk fitur lanjutan. MyTools tidak membaca notifikasi aplikasi lain.",11f))
    }

fun MainActivity.financeDashboardTool() {
        clearPage("Finance Dashboard")
        val db = FinanceDb(this)
        db.processDueRecurring()
        content.addView(label("Finance Dashboard", 24f, true))
        content.addView(subLabel("Ringkasan cepat tanpa membaca notifikasi. Semua data keuangan berasal dari input yang disimpan lokal.", 12f))

        val (from, to) = db.monthRange()
        val income = db.totalByType("masuk", from, to)
        val expense = db.totalByType("keluar", from, to)
        val net = income - expense
        val summary = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        summary.addView(financeSummaryBox("Masuk", income, Color.rgb(80, 80, 84)), LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(5) })
        summary.addView(financeSummaryBox("Keluar", expense, Color.rgb(120, 120, 124)), LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(5) })
        content.addView(summary)
        content.addView(label("Saldo bersih bulan ini: ${MoneyFormatter.format(net)}", 15f, true).apply { setPadding(dp(2), dp(12), dp(2), dp(4)) })

        sectionTitle("Insight lokal")
        val day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
        val daily = expense / day
        val cal = Calendar.getInstance()
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val forecast = daily * daysInMonth
        content.addView(settingRow("Laju pengeluaran", MoneyFormatter.format(daily) + " / hari", "Perkiraan sederhana berdasarkan pengeluaran bulan berjalan."))
        content.addView(settingRow("Proyeksi bulan", MoneyFormatter.format(forecast), "Bukan prediksi pasti; hanya extrapolasi rata-rata harian."))

        val categories = db.sumByCategory("keluar", from, to)
        if (categories.isNotEmpty()) {
            sectionTitle("Kategori terbesar")
            val top = categories.first()
            content.addView(settingRow(top.first, MoneyFormatter.format(top.second), "Kategori dengan pengeluaran terbesar bulan ini."))
            val max = categories.maxOf { it.second }
            categories.take(6).forEach { (cat, amount) -> content.addView(financeCategoryBar(cat, amount, max)) }
        }

        sectionTitle("Anggaran")
        val budgets = db.getBudgets()
        if (budgets.isEmpty()) {
            content.addView(subLabel("Belum ada anggaran. Gunakan + → Atur anggaran.", 12f))
        } else {
            budgets.forEach { (cat, limit) ->
                val spent = categories.find { it.first == cat }?.second ?: 0.0
                val left = (limit - spent).coerceAtLeast(0.0)
                val daysLeft = FinanceInsights.budgetForecastDaysLeft(db, cat, limit)
                content.addView(settingRow(cat, "${MoneyFormatter.format(spent)} / ${MoneyFormatter.format(limit)}", "Sisa ${MoneyFormatter.format(left)}${if (daysLeft != null) " • estimasi ${daysLeft} hari" else ""}"))
            }
        }

        sectionTitle("Anomali")
        val anomaly = db.listTx(100).firstOrNull { FinanceInsights.anomaly(it, db) }
        content.addView(subLabel(anomaly?.let { "Pengeluaran tinggi terdeteksi: ${it.merchant.ifBlank { it.category }} • ${MoneyFormatter.format(it.amount)}" } ?: "Tidak ada anomali sederhana yang terdeteksi.", 12f))

        sectionTitle("Target tabungan")
        val goals = db.goals()
        if (goals.isEmpty()) content.addView(subLabel("Belum ada target tabungan.", 12f))
        goals.take(5).forEach { g ->
            val name = g[1] as String
            val target = g[2] as Double
            val current = db.goalProgress(name)
            val pct = if (target > 0) (current / target * 100.0).coerceIn(0.0, 100.0) else 0.0
            content.addView(settingRow(name, "${MoneyFormatter.format(current)} / ${MoneyFormatter.format(target)}", "Progress ${pct.toInt()}%"))
        }

        sectionTitle("Laporan")
        content.addView(button("Buat & Bagikan PDF Bulanan") {
            runCatching { FinanceReport.share(this, FinanceReport.createPdf(this, db)) }
                .onFailure { toast("PDF gagal: ${it.message}") }
        })
        content.addView(button("Buka Pengelola Keuangan") { financeReaderTool() })
    }

fun MainActivity.financeStudioTool(){ studioHub("Finance Studio","Kalkulator dan dashboard keuangan.",listOf("Kalkulator Lengkap" to "number","Finance Dashboard" to "financedashboard","Pengelola Keuangan" to "financereader","Pivot Point" to "pivotcalc","Averaging Down & DCA" to "dcacalc","Voltage Divider" to "dividercalc","PWM" to "pwmcalc","Konsumsi Listrik" to "powercalc")) }
