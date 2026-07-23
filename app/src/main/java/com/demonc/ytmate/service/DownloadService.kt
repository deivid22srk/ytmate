package com.demonc.ytmate.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.demonc.ytmate.MainActivity
import com.demonc.ytmate.R
import com.demonc.ytmate.data.DownloadItem
import com.demonc.ytmate.data.DownloadStatus
import com.demonc.ytmate.data.DownloadType
import com.demonc.ytmate.util.StorageUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DownloadService : Service() {

    companion object {
        const val ACTION_START = "com.demonc.ytmate.action.START"
        const val ACTION_CANCEL = "com.demonc.ytmate.action.CANCEL"
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_EXT = "extra_ext"
        const val EXTRA_TYPE = "extra_type"
        const val EXTRA_QUALITY = "extra_quality"

        private const val CHANNEL_ID = "ytmate_download_channel"
        private const val NOTIF_ID_BASE = 1000
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder().build()
    private val downloadsMap = ConcurrentHashMap<String, DownloadItem>()
    private val jobsMap = ConcurrentHashMap<String, Job>()
    private val _downloadsFlow = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloadsFlow get() = _downloadsFlow

    inner class DownloadBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }
    private val binder = DownloadBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    fun observeDownloads(cb: (List<DownloadItem>) -> Unit) {
        scope.launch {
            downloadsFlow.collect { cb(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val id = intent.getStringExtra(EXTRA_ITEM_ID) ?: UUID.randomUUID().toString()
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Download"
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val ext = intent.getStringExtra(EXTRA_EXT) ?: "mp4"
                val type = intent.getStringExtra(EXTRA_TYPE)
                    ?.let { runCatching { DownloadType.valueOf(it) }.getOrNull() }
                    ?: DownloadType.VIDEO
                val quality = intent.getStringExtra(EXTRA_QUALITY) ?: ""

                startForeground(NOTIF_ID_BASE, buildNotification(title, 0, 0, id))

                val item = DownloadItem(
                    id = id,
                    title = title,
                    thumbnailUrl = "",
                    streamUrl = url,
                    mimeType = if (type == DownloadType.AUDIO) "audio/*" else "video/*",
                    extension = ext,
                    downloadType = type,
                    qualityLabel = quality,
                    totalBytes = 0L,
                    status = DownloadStatus.RUNNING
                )
                putItem(item)

                val job = scope.launch {
                    runDownload(id, title, url, ext, type)
                }
                jobsMap[id] = job
            }
            ACTION_CANCEL -> {
                val id = intent.getStringExtra(EXTRA_ITEM_ID) ?: return START_NOT_STICKY
                jobsMap[id]?.cancel()
                jobsMap.remove(id)
                val cur = downloadsMap[id]
                if (cur != null) {
                    putItem(cur.copy(status = DownloadStatus.CANCELED))
                    // apaga arquivo parcial
                    cur.filePath?.let { File(it).delete() }
                }
                // Se não há mais downloads rodando, encerra o serviço
                if (jobsMap.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun runDownload(
        id: String,
        title: String,
        url: String,
        ext: String,
        type: DownloadType
    ) {
        try {
            val request = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                .header("Accept", "*/*")
                .header("Referer", "https://www.youtube.com/")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    fail(id, "HTTP ${response.code}")
                    return
                }
                val total = response.body?.contentLength() ?: -1L
                val safeTitle = StorageUtil.sanitizeFileName(title)
                val dir = if (type == DownloadType.AUDIO)
                    StorageUtil.getAudioDir(this) else StorageUtil.getVideoDir(this)
                val outFile = File(dir, "${safeTitle}_${id.take(6)}.$ext")

                updateItem(id) { it.copy(totalBytes = total, filePath = outFile.absolutePath) }

                response.body?.byteStream()?.use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var read: Int
                        var downloaded = 0L
                        var lastNotif = 0L
                        while (true) {
                            read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            updateItem(id) { it.copy(downloadedBytes = downloaded) }
                            val now = System.currentTimeMillis()
                            if (now - lastNotif > 800) {
                                lastNotif = now
                                notifyProgress(id, title, downloaded, total)
                            }
                        }
                    }
                }

                updateItem(id) {
                    it.copy(status = DownloadStatus.COMPLETED, filePath = outFile.absolutePath)
                }
                notifyCompleted(id, title)
            }
        } catch (t: Throwable) {
            fail(id, t.message ?: "Erro desconhecido")
        } finally {
            jobsMap.remove(id)
            if (jobsMap.isEmpty()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun fail(id: String, message: String) {
        updateItem(id) {
            it.copy(status = DownloadStatus.FAILED, errorMessage = message)
        }
        notifyFailed(id, downloadsMap[id]?.title ?: "Download", message)
    }

    private fun putItem(item: DownloadItem) {
        downloadsMap[item.id] = item
        _downloadsFlow.value = downloadsMap.values.toList()
    }

    private fun updateItem(id: String, transform: (DownloadItem) -> DownloadItem) {
        val cur = downloadsMap[id] ?: return
        val updated = transform(cur)
        downloadsMap[id] = updated
        _downloadsFlow.value = downloadsMap.values.toList()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads YTMate",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progresso de downloads de vídeo/áudio"
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        title: String,
        progress: Int,
        totalKb: Int,
        id: String
    ): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val cancelIntent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_ITEM_ID, id)
        }
        val cancelPi = PendingIntent.getService(
            this, id.hashCode(), cancelIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Baixando: $title")
            .setContentText("$progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, totalKb <= 0)
            .setOngoing(true)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancelar", cancelPi)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun notifyProgress(id: String, title: String, downloaded: Long, total: Long) {
        val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_BASE + (id.hashCode() and 0x3ff), buildNotification(title, percent, (total / 1024).toInt(), id))
    }

    private fun notifyCompleted(id: String, title: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download concluído")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()
        nm.notify(NOTIF_ID_BASE + (id.hashCode() and 0x3ff) + 5000, n)
    }

    private fun notifyFailed(id: String, title: String, msg: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Falha no download")
            .setContentText("$title — $msg")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()
        nm.notify(NOTIF_ID_BASE + (id.hashCode() and 0x3ff) + 9000, n)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
