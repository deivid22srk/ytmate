package com.demonc.ytmate.data

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.demonc.ytmate.service.DownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = YouTubeRepository()

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _videoInfo = MutableLiveData<VideoInfo?>()
    val videoInfo: LiveData<VideoInfo?> = _videoInfo

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val _downloads = MutableLiveData<List<DownloadItem>>(emptyList())
    val downloads: LiveData<List<DownloadItem>> = _downloads

    private var downloadBinder: DownloadService.DownloadBinder? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            downloadBinder = service as? DownloadService.DownloadBinder
            downloadBinder?.getService()?.observeDownloads { items ->
                _downloads.postValue(items)
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            downloadBinder = null
        }
    }

    fun bindDownloadService(context: Context) {
        val intent = Intent(context, DownloadService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun unbindDownloadService(context: Context) {
        try { context.unbindService(serviceConnection) } catch (_: Exception) {}
    }

    fun loadUrl(url: String) {
        if (!YouTubeRepository.isYouTubeUrl(url)) {
            _error.value = "URL do YouTube inválida. Cole um link válido."
            return
        }
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val info = repo.extract(url)
                withContext(Dispatchers.Main) {
                    if (info.videoStreams.isEmpty() && info.audioStreams.isEmpty()) {
                        _error.value = "Nenhum stream disponível para este vídeo."
                        _videoInfo.value = null
                    } else {
                        _videoInfo.value = info
                    }
                    _isLoading.value = false
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    _error.value = "Erro ao extrair vídeo: ${t.message ?: "desconhecido"}"
                    _isLoading.value = false
                }
            }
        }
    }

    fun startDownload(stream: StreamInfo, info: VideoInfo, type: DownloadType, context: Context) {
        val item = DownloadItem(
            id = "${System.currentTimeMillis()}_${stream.formatId}",
            title = info.title,
            thumbnailUrl = info.thumbnailUrl,
            streamUrl = stream.url,
            mimeType = stream.mimeType,
            extension = stream.extension,
            downloadType = type,
            qualityLabel = stream.qualityLabel,
            totalBytes = stream.sizeBytes
        )
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START
            putExtra(DownloadService.EXTRA_ITEM_ID, item.id)
            putExtra(DownloadService.EXTRA_TITLE, item.title)
            putExtra(DownloadService.EXTRA_URL, item.streamUrl)
            putExtra(DownloadService.EXTRA_EXT, item.extension)
            putExtra(DownloadService.EXTRA_TYPE, type.name)
            putExtra(DownloadService.EXTRA_QUALITY, item.qualityLabel)
        }
        androidx.core.content.ContextCompat.startForegroundService(context, intent)
    }

    fun cancelDownload(id: String, context: Context) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_CANCEL
            putExtra(DownloadService.EXTRA_ITEM_ID, id)
        }
        context.startService(intent)
    }

    fun clearError() { _error.value = null }
    fun clearVideoInfo() { _videoInfo.value = null }
}
