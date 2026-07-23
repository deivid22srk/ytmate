package com.demonc.ytmate.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.demonc.ytmate.data.DownloadItem
import com.demonc.ytmate.data.DownloadStatus
import com.demonc.ytmate.data.DownloadType
import com.demonc.ytmate.data.MainViewModel
import com.demonc.ytmate.data.StreamInfo
import com.demonc.ytmate.data.VideoInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val videoInfo by viewModel.videoInfo.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var url by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("YTMate", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Campo de URL
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Cole o link do YouTube") },
                placeholder = { Text("https://youtube.com/watch?v=...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                trailingIcon = {
                    if (url.isNotBlank()) {
                        FilledTonalButton(
                            onClick = { viewModel.loadUrl(url) },
                            enabled = !isLoading
                        ) { Text("Buscar") }
                    }
                }
            )

            Spacer(Modifier.height(16.dp))

            // Botão primário (usado quando o texto ainda não foi enviado)
            if (url.isNotBlank() && videoInfo == null && !isLoading) {
                Button(
                    onClick = { viewModel.loadUrl(url) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Buscar streams")
                }
                Spacer(Modifier.height(16.dp))
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Extraindo streams…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            videoInfo?.let { info ->
                VideoInfoCard(info)
                QualitySelectionSection(
                    videoInfo = info,
                    onPickVideo = { stream ->
                        viewModel.startDownload(stream, info, DownloadType.VIDEO, context)
                    },
                    onPickAudio = { stream ->
                        viewModel.startDownload(stream, info, DownloadType.AUDIO, context)
                    }
                )
            }

            if (downloads.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    "Downloads",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                downloads.forEach { item ->
                    DownloadItemRow(item = item, onCancel = { viewModel.cancelDownload(item.id, context) })
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun VideoInfoCard(info: VideoInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            if (info.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = info.thumbnailUrl,
                    contentDescription = info.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    info.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(info.uploader, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        formatDuration(info.durationSeconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QualitySelectionSection(
    videoInfo: VideoInfo,
    onPickVideo: (StreamInfo) -> Unit,
    onPickAudio: (StreamInfo) -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(0) } // 0 = vídeo, 1 = áudio
    Spacer(Modifier.height(16.dp))
    Row {
        FilterChip(
            selected = tab == 0,
            onClick = { tab = 0 },
            label = { Text("Vídeo") },
            leadingIcon = { Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        Spacer(Modifier.width(8.dp))
        FilterChip(
            selected = tab == 1,
            onClick = { tab = 1 },
            label = { Text("Áudio") },
            leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
    }
    Spacer(Modifier.height(12.dp))

    if (tab == 0) {
        if (videoInfo.videoStreams.isEmpty()) {
            EmptyStreams("Nenhum stream de vídeo disponível.")
        } else {
            Column {
                videoInfo.videoStreams.forEach { stream ->
                    StreamRow(
                        stream = stream,
                        onClick = { onPickVideo(stream) }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    } else {
        if (videoInfo.audioStreams.isEmpty()) {
            EmptyStreams("Nenhum stream de áudio disponível.")
        } else {
            Column {
                videoInfo.audioStreams.forEach { stream ->
                    StreamRow(
                        stream = stream,
                        onClick = { onPickAudio(stream) }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun StreamRow(stream: StreamInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (stream.isVideo) Icons.Default.VideoLibrary else Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stream.qualityLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${stream.extension.uppercase()} · ${stream.mimeType}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            FilledTonalButton(onClick = onClick) {
                Icon(Icons.Default.Download, contentDescription = "Baixar", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Baixar")
            }
        }
    }
}

@Composable
private fun EmptyStreams(msg: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(msg, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun DownloadItemRow(item: DownloadItem, onCancel: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (item.downloadType == DownloadType.AUDIO)
                        Icons.Default.MusicNote else Icons.Default.VideoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "${item.qualityLabel} · ${item.extension.uppercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                when (item.status) {
                    DownloadStatus.RUNNING, DownloadStatus.PENDING -> {
                        FilledTonalButton(onClick = onCancel) { Text("Cancelar") }
                    }
                    DownloadStatus.COMPLETED -> {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    DownloadStatus.FAILED -> {
                        Text(
                            "Falhou",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {}
                }
            }
            if (item.status == DownloadStatus.RUNNING) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${(item.progress * 100).toInt()}% · ${formatBytes(item.downloadedBytes)} / ${formatBytes(item.totalBytes)}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            item.errorMessage?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "--:--"
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "—"
    val units = arrayOf("B", "KB", "MB", "GB")
    var v = bytes.toDouble()
    var u = 0
    while (v >= 1024 && u < units.lastIndex) { v /= 1024; u++ }
    return "%.1f %s".format(v, units[u])
}
