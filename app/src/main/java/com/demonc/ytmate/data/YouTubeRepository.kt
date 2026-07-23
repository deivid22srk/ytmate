package com.demonc.ytmate.data

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Extrai informações de streams (vídeo/áudio) de URLs do YouTube usando NewPipe Extractor.
 */
class YouTubeRepository {

    init {
        // Inicializa o NewPipe com user-agent correto
        NewPipe.init(
            object : org.schabi.newpipe.extractor.downloader.Downloader() {
                private val client = okhttp3.OkHttpClient.Builder()
                    .followRedirects(true)
                    .build()

                override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): org.schabi.newpipe.extractor.downloader.Response {
                    val builder = okhttp3.Request.Builder()
                        .url(request.url)
                        .method(request.httpMethod, null)
                    request.headers.forEach { (key, values) ->
                        values.forEach { v -> builder.header(key, v) }
                    }
                    // User-Agent obrigatório
                    if (request.headers["User-Agent"] == null) {
                        builder.header("User-Agent", USER_AGENT)
                    }
                    client.newCall(builder.build()).execute().use { resp ->
                        val body = resp.body?.string() ?: ""
                        val headersMap = mutableMapOf<String, List<String>>()
                        resp.headers.forEach { header ->
                            val existing = headersMap[header.first].orEmpty()
                            headersMap[header.first] = existing + header.second
                        }
                        return org.schabi.newpipe.extractor.downloader.Response(
                            resp.code,
                            resp.message,
                            headersMap,
                            body,
                            resp.request.url.toString()
                        )
                    }
                }
            }
        )
    }

    fun extract(url: String): VideoInfo {
        val service: StreamingService = ServiceList.YouTube
        val linkHandler = service.getStreamLHFactory().fromUrl(url)
        val extractor: StreamExtractor = service.getStreamExtractor(linkHandler)
        extractor.fetchPage()

        val title = extractor.name ?: "Vídeo sem título"
        val uploader = extractor.uploaderName ?: "Desconhecido"
        val thumbnailUrl = extractor.thumbnailUrl ?: ""
        val duration = extractor.lengthInSeconds()

        val videoStreams: List<VideoStream> = extractor.videoStreams
            .filter { it.url != null && it.url.isNotBlank() }
            .distinctBy { it.height.toString() + it.deliveryMethod }

        val audioStreams: List<AudioStream> = extractor.audioStreams
            .filter { it.url != null && it.url.isNotBlank() }

        val videoInfos = videoStreams
            .filter { it.height > 0 }
            .sortedByDescending { it.height }
            .map { vs ->
                StreamInfo(
                    url = vs.url,
                    mimeType = vs.mimeType ?: "video/mp4",
                    qualityLabel = "${vs.height}p",
                    formatId = vs.formatId?.toString() ?: "",
                    isVideo = true,
                    extension = vs.format?.suffix?.removePrefix(".") ?: "mp4",
                    bitrate = vs.averageBitrate.coerceAtLeast(0).toInt(),
                    height = vs.height
                )
            }
            .distinctBy { it.height }

        val audioInfos = audioStreams
            .sortedByDescending { it.averageBitrate }
            .map { as_ ->
                StreamInfo(
                    url = as_.url,
                    mimeType = as_.mimeType ?: "audio/mp4",
                    qualityLabel = "${(as_.averageBitrate / 1000).toInt()}kbps",
                    formatId = as_.formatId?.toString() ?: "",
                    isVideo = false,
                    extension = as_.format?.suffix?.removePrefix(".") ?: "m4a",
                    bitrate = as_.averageBitrate.coerceAtLeast(0).toInt()
                )
            }
            .distinctBy { it.bitrate / 1000 }

        return VideoInfo(
            title = title,
            uploader = uploader,
            thumbnailUrl = thumbnailUrl,
            durationSeconds = duration,
            videoStreams = videoInfos,
            audioStreams = audioInfos
        )
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        fun isYouTubeUrl(text: String?): Boolean {
            if (text.isNullOrBlank()) return false
            val patterns = listOf(
                "youtube.com/watch?v=",
                "youtu.be/",
                "youtube.com/shorts/",
                "m.youtube.com/watch?v=",
                "music.youtube.com/watch?v="
            )
            return patterns.any { text.contains(it, ignoreCase = true) }
        }

        fun extractYouTubeUrl(text: String?): String? {
            if (text.isNullOrBlank()) return null
            // Tenta encontrar uma URL http(s) que contenha YouTube
            val regex = Regex(
                "https?://(?:www\\.|m\\.)?(?:youtube\\.com|youtu\\.be|music\\.youtube\\.com)/\\S+",
                RegexOption.IGNORE_CASE
            )
            return regex.find(text)?.value?.substringBeforeLast('&')?.trim()
                ?.let { if (it.endsWith(')') || it.endsWith(']')) it.dropLast(1) else it }
        }
    }
}
