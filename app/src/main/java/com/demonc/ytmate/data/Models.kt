package com.demonc.ytmate.data

/**
 * Modelos de dados do app.
 */

data class VideoInfo(
    val title: String,
    val uploader: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val videoStreams: List<StreamInfo>,
    val audioStreams: List<StreamInfo>
)

data class StreamInfo(
    val url: String,
    val mimeType: String,
    val qualityLabel: String,            // ex.: "720p", "128kbps"
    val formatId: String,
    val isVideo: Boolean,
    val sizeBytes: Long = 0L,
    val extension: String,
    val bitrate: Int = 0,
    val height: Int = 0
)

enum class DownloadType { VIDEO, AUDIO }

enum class DownloadStatus { PENDING, RUNNING, PAUSED, COMPLETED, FAILED, CANCELED }

data class DownloadItem(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val streamUrl: String,
    val mimeType: String,
    val extension: String,
    val downloadType: DownloadType,
    val qualityLabel: String,
    val totalBytes: Long,
    val downloadedBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val filePath: String? = null,
    val errorMessage: String? = null
) {
    val progress: Float
        get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
}
