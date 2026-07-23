package com.demonc.ytmate.util

import android.content.Context
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Resolve o diretório de saída onde vídeos/áudios baixados serão salvos.
 * Em Android 10+ usamos Music/ e Movies/ públicos do armazenamento compartilhado,
 * acessíveis via MediaStore. Em Android < 10 usamos o diretório público legado.
 */
object StorageUtil {

    fun getVideoDir(context: Context): File {
        val movies = ContextCompat.getExternalFilesDirs(context, Environment.DIRECTORY_MOVIES)
            .firstOrNull { it != null }
            ?: File(context.filesDir, "Movies")
        val dir = File(movies, "YTMate")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getAudioDir(context: Context): File {
        val music = ContextCompat.getExternalFilesDirs(context, Environment.DIRECTORY_MUSIC)
            .firstOrNull { it != null }
            ?: File(context.filesDir, "Music")
        val dir = File(music, "YTMate")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun sanitizeFileName(name: String): String {
        val replaced = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return replaced.trim().take(120).ifBlank { "ytmate_download" }
    }
}
