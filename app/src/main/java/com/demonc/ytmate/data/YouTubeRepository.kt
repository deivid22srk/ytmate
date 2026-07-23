package com.demonc.ytmate.data

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Extrai informações de streams (vídeo/áudio) diretamente da página pública
 * do YouTube, sem depender de bibliotecas externas (NewPipe etc.).
 *
 * Estratégia:
 *   1. GET https://www.youtube.com/watch?v=ID com headers de navegador
 *   2. Localiza o bloco JavaScript `ytInitialPlayerResponse = {...}`
 *   3. Parseia como JSON (Gson)
 *   4. Lê `streamingData.formats` (vídeo+áudio combinados) e
 *      `streamingData.adaptiveFormats` (streams separados)
 *   5. Traduz para [VideoInfo] com [StreamInfo] em cada lista
 *
 * Observação: o YouTube pode servir streams com `signatureCipher` (streams
 * protegidos por cifra) — esses não são baixáveis diretamente sem a função
 * de decifração que mora no player JS. Filtramos apenas streams cuja URL
 * esteja presente em `url` (não-cifrados). Para a maioria dos vídeos
 * públicos isso cobre os formatos áudio (m4a/webm/opus) e alguns vídeos.
 */
class YouTubeRepository {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val gson = Gson()

    fun extract(url: String): VideoInfo {
        val videoId = extractVideoId(url)
            ?: throw IllegalArgumentException("Não consegui extrair o ID do vídeo da URL: $url")

        val html = fetchWatchHtml(videoId)
        val playerResponseJson = extractPlayerResponseJson(html)
            ?: throw IllegalStateException("Resposta do YouTube não contém ytInitialPlayerResponse. O vídeo pode ser privado, idade-restrito, ou o YouTube mudou a estrutura da página.")

        val player = gson.fromJson(playerResponseJson, JsonObject::class.java)
        val playability = player.getAsJsonObject("playabilityStatus")
        val status = playability?.get("status")?.asString
        if (status != null && status != "OK") {
            val reason = playability.get("reason")?.asString
                ?: playability.getAsJsonObject("errorScreen")?.getAsJsonObject("playerErrorMessageRenderer")?.get("subreason")?.asString
                ?: status
            throw IllegalStateException("Vídeo não disponível: $reason")
        }

        val videoDetails = player.getAsJsonObject("videoDetails")
            ?: throw IllegalStateException("Sem videoDetails na resposta do YouTube.")
        val title = videoDetails.get("title")?.asString ?: "Vídeo sem título"
        val author = videoDetails.get("author")?.asString ?: "Desconhecido"
        val lengthSeconds = videoDetails.get("lengthSeconds")?.asString?.toLongOrNull() ?: 0L
        val thumbnails = videoDetails.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")
        val thumbnailUrl = thumbnails?.lastOrNull()?.asObject?.get("url")?.asString ?: ""

        val streamingData = player.getAsJsonObject("streamingData")
            ?: throw IllegalStateException("Sem streamingData na resposta do YouTube.")

        val combinedFormats = streamingData.getAsJsonArray("formats")?.toList().orEmpty()
        val adaptiveFormats = streamingData.getAsJsonArray("adaptiveFormats")?.toList().orEmpty()

        val videoStreams = mutableListOf<StreamInfo>()
        val audioStreams = mutableListOf<StreamInfo>()

        for (f in adaptiveFormats) {
            val obj = f.asObject
            val mimeType = obj.get("mimeType")?.asString ?: continue
            val itag = obj.get("itag")?.asString ?: "0"
            val streamUrl = obj.get("url")?.asString ?: continue  // ignora signatureCipher
            val ext = guessExtension(mimeType)
            val bitrate = obj.get("bitrate")?.asInt ?: 0
            val contentLength = obj.get("contentLength")?.asString?.toLongOrNull() ?: 0L

            if (mimeType.startsWith("video/", true)) {
                val height = obj.get("height")?.asInt ?: 0
                val qualityLabel = if (height > 0) "${height}p" else obj.get("quality")?.asString ?: "video"
                videoStreams.add(
                    StreamInfo(
                        url = streamUrl,
                        mimeType = mimeType,
                        qualityLabel = qualityLabel,
                        formatId = itag,
                        isVideo = true,
                        sizeBytes = contentLength,
                        extension = ext,
                        bitrate = bitrate,
                        height = height
                    )
                )
            } else if (mimeType.startsWith("audio/", true)) {
                val kbps = if (bitrate > 0) (bitrate / 1000).toInt() else 0
                val qualityLabel = if (kbps > 0) "${kbps}kbps" else "audio"
                audioStreams.add(
                    StreamInfo(
                        url = streamUrl,
                        mimeType = mimeType,
                        qualityLabel = qualityLabel,
                        formatId = itag,
                        isVideo = false,
                        sizeBytes = contentLength,
                        extension = ext,
                        bitrate = bitrate
                    )
                )
            }
        }

        // Adiciona também os formats combinados (vídeo+áudio em um stream), úteis para MP4 360p/720p
        for (f in combinedFormats) {
            val obj = f.asObject
            val mimeType = obj.get("mimeType")?.asString ?: continue
            val itag = obj.get("itag")?.asString ?: "0"
            val streamUrl = obj.get("url")?.asString ?: continue
            val ext = guessExtension(mimeType)
            val qualityLabel = obj.get("qualityLabel")?.asString
                ?: obj.get("quality")?.asString
                ?: "video"
            val height = obj.get("height")?.asInt ?: 0
            val contentLength = obj.get("contentLength")?.asString?.toLongOrNull() ?: 0L
            videoStreams.add(
                StreamInfo(
                    url = streamUrl,
                    mimeType = mimeType,
                    qualityLabel = qualityLabel,
                    formatId = itag,
                    isVideo = true,
                    sizeBytes = contentLength,
                    extension = ext,
                    bitrate = obj.get("bitrate")?.asInt ?: 0,
                    height = height
                )
            )
        }

        // Ordena: vídeo por altura desc, áudio por bitrate desc
        videoStreams.sortByDescending { it.height }
        audioStreams.sortByDescending { it.bitrate }

        // Remove duplicatas por qualidade
        val dedupVideo = videoStreams.distinctBy { it.height.toString() + "_" + it.mimeType.substringBefore(';') }
        val dedupAudio = audioStreams.distinctBy { (it.bitrate / 1000).toString() + "_" + it.mimeType.substringBefore(';') }

        return VideoInfo(
            title = title,
            uploader = author,
            thumbnailUrl = thumbnailUrl,
            durationSeconds = lengthSeconds,
            videoStreams = dedupVideo,
            audioStreams = dedupAudio
        )
    }

    private fun fetchWatchHtml(videoId: String): String {
        val req = Request.Builder()
            .url("https://www.youtube.com/watch?v=$videoId&bpctr=9999999999&has_verified=1")
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("DNT", "1")
            .header("Upgrade-Insecure-Requests", "1")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code} ao acessar o YouTube.")
            return resp.body?.string() ?: throw IllegalStateException("Resposta vazia do YouTube.")
        }
    }

    private fun extractPlayerResponseJson(html: String): String? {
        // Localiza `ytInitialPlayerResponse = {...};`
        val markers = listOf(
            "ytInitialPlayerResponse = ",
            "ytInitialPlayerResponse="
        )
        for (m in markers) {
            val startIdx = html.indexOf(m)
            if (startIdx < 0) continue
            val jsonStart = startIdx + m.length
            val json = readBalancedJson(html, jsonStart) ?: continue
            if (json.isNotBlank()) return json
        }
        return null
    }

    /**
     * Lê um JSON balanceado a partir de [start], parando quando chaves/colchetes
     * fecharem. Lida com strings escapadas (incluindo aspas dentro de strings).
     */
    private fun readBalancedJson(s: String, start: Int): String? {
        if (start >= s.length) return null
        val sb = StringBuilder()
        var depth = 0
        var inString = false
        var escape = false
        var i = start
        // Pula espaços iniciais
        while (i < s.length && s[i].isWhitespace()) i++
        if (i >= s.length || s[i] != '{') return null
        while (i < s.length) {
            val c = s[i]
            sb.append(c)
            if (escape) {
                escape = false
            } else if (c == '\\') {
                escape = true
            } else if (c == '"') {
                inString = !inString
            } else if (!inString) {
                when (c) {
                    '{', '[' -> depth++
                    '}', ']' -> {
                        depth--
                        if (depth == 0) {
                            return sb.toString()
                        }
                    }
                }
            }
            i++
        }
        return null
    }

    private fun guessExtension(mimeType: String): String {
        val lower = mimeType.lowercase()
        return when {
            "mp4" in lower -> "mp4"
            "webm" in lower -> "webm"
            "opus" in lower -> "opus"
            "aac" in lower -> "aac"
            "flac" in lower -> "flac"
            "mp3" in lower -> "mp3"
            "ogg" in lower -> "ogg"
            "wav" in lower -> "wav"
            "m4a" in lower -> "m4a"
            else -> "bin"
        }
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
            val regex = Regex(
                "https?://(?:www\\.|m\\.)?(?:youtube\\.com|youtu\\.be|music\\.youtube\\.com)/\\S+",
                RegexOption.IGNORE_CASE
            )
            return regex.find(text)?.value?.trim()
                ?.let { if (it.endsWith(')') || it.endsWith(']')) it.dropLast(1) else it }
        }

        fun extractVideoId(url: String): String? {
            // Padrões comuns do YouTube
            val patterns = listOf(
                Regex("[?&]v=([a-zA-Z0-9_-]{11})"),
                Regex("youtu\\.be/([a-zA-Z0-9_-]{11})"),
                Regex("youtube\\.com/shorts/([a-zA-Z0-9_-]{11})"),
                Regex("youtube\\.com/embed/([a-zA-Z0-9_-]{11})"),
                Regex("music\\.youtube\\.com/watch\\?v=([a-zA-Z0-9_-]{11})")
            )
            for (p in patterns) {
                val m = p.find(url) ?: continue
                if (m.groupValues.size >= 2) return m.groupValues[1]
            }
            // Última tentativa: 11 caracteres alfanuméricos na URL
            val lastResort = Regex("([a-zA-Z0-9_-]{11})").find(url)?.value
            return lastResort
        }
    }
}
