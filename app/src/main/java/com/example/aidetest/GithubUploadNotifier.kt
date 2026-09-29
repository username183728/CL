package com.example.aidetest

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Notifikasi progres + hasil upload GitHub, agar tetap terlihat saat aplikasi ditinggalkan. */
object GithubUploadNotifier {
    const val CH_PROGRESS = "gh_upload_progress"
    const val CH_DONE = "gh_upload_done"
    const val ID_PROGRESS = 7101
    const val ID_DONE = 7102
    const val EXTRA_OPEN = "open_github_publisher"
    private var lastPostAt = 0L
    private var lastKey = ""

    private fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH_PROGRESS, "Proses upload GitHub", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Progres upload project ke GitHub"
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_DONE, "Upload GitHub selesai", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Pemberitahuan saat upload GitHub selesai atau gagal"
        })
    }

    private fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openIntent(ctx: Context, requestCode: Int): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            ctx, requestCode, i,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
    }

    fun buildProgress(ctx: Context, text: String, percent: Int): Notification {
        ensureChannels(ctx)
        return NotificationCompat.Builder(ctx, CH_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Mengunggah ke GitHub")
            .setContentText(text)
            .setProgress(100, percent.coerceIn(0, 100), percent <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openIntent(ctx, 7201))
            .build()
    }

    /** Perbarui notifikasi progres (dibatasi agar tidak spam). */
    fun progress(ctx: Context, text: String, percent: Int) {
        val key = "$text|$percent"
        val now = System.currentTimeMillis()
        if (key == lastKey || now - lastPostAt < 700L) return
        lastKey = key; lastPostAt = now
        if (!canNotify(ctx)) return
        runCatching { NotificationManagerCompat.from(ctx).notify(ID_PROGRESS, buildProgress(ctx, text, percent)) }
    }

    fun done(ctx: Context, success: Boolean, title: String, text: String) {
        if (!canNotify(ctx)) return
        ensureChannels(ctx)
        val n = NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(if (success) android.R.drawable.stat_sys_upload_done else android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(openIntent(ctx, 7202))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(ID_DONE, n) }
    }

    fun cancelDone(ctx: Context) {
        runCatching { NotificationManagerCompat.from(ctx).cancel(ID_DONE) }
    }

    fun startKeepAlive(ctx: Context) {
        lastKey = ""; lastPostAt = 0L
        runCatching {
            val i = Intent(ctx, GithubUploadService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
    }

    fun stopKeepAlive(ctx: Context) {
        runCatching { ctx.stopService(Intent(ctx, GithubUploadService::class.java)) }
        runCatching { NotificationManagerCompat.from(ctx).cancel(ID_PROGRESS) }
    }
}

/**
 * Service foreground ringan: hanya menjaga proses tetap hidup selama upload berjalan
 * saat pengguna keluar dari aplikasi. Logika upload tetap di MainActivity.
 */
class GithubUploadService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = GithubUploadNotifier.buildProgress(this, "Menyiapkan upload…", 0)
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(GithubUploadNotifier.ID_PROGRESS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(GithubUploadNotifier.ID_PROGRESS, n)
            }
        }
        return START_NOT_STICKY
    }
}
