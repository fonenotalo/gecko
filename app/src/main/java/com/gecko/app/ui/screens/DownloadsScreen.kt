package com.gecko.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gecko.app.data.DownloadEntry
import com.gecko.app.data.DownloadStatus
import com.gecko.app.model.Track
import com.gecko.app.ui.PlayerUiState
import com.gecko.app.ui.components.GeckoCard
import com.gecko.app.ui.components.EmptyState
import com.gecko.app.ui.components.InfoBanner
import com.gecko.app.ui.components.SectionHeader
import com.gecko.app.ui.components.TrackActions
import com.gecko.app.ui.components.TrackArtwork
import com.gecko.app.ui.components.TrackRow
import com.gecko.app.util.formatBytes

@Composable
fun DownloadsScreen(
    downloads: List<DownloadEntry>,
    player: PlayerUiState,
    wifiOnly: Boolean,
    smartDownloads: Boolean,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onRetry: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDeleteAll: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var confirmDeleteAll by remember { mutableStateOf(false) }

    val active = downloads.filter {
        it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING
    }
    val failed = downloads.filter { it.status == DownloadStatus.FAILED }
    val completed = downloads.filter { it.status == DownloadStatus.COMPLETED }
    val completedTracks = completed.map { it.track }
    val totalBytes = completed.sumOf { it.bytes }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Downloads",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "${completed.size} faixas offline  •  ${formatBytes(totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (downloads.isNotEmpty()) {
                    IconButton(onClick = { confirmDeleteAll = true }) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Excluir todos os downloads",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            GeckoCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenSettings)
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Downloads inteligentes",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = buildString {
                                append(if (smartDownloads) "Ativo" else "Desligado")
                                append("  •  ")
                                append(if (wifiOnly) "somente Wi-Fi" else "Wi-Fi e dados")
                                append("  •  qualidade lossless")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.Rounded.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        if (downloads.isEmpty()) {
            item {
                GeckoCard {
                    EmptyState(
                        title = "Nada baixado ainda",
                        message = "Baixe faixas das suas fontes remotas para ouvir offline. " +
                            "O Gecko guarda tudo no armazenamento privado do app, na qualidade escolhida.",
                        icon = {
                            Icon(
                                Icons.Rounded.DownloadDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                    )
                    Button(
                        onClick = onOpenLibrary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 20.dp),
                    ) {
                        Icon(Icons.Rounded.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Explorar biblioteca")
                    }
                }
            }
        }

        if (active.isNotEmpty()) {
            item { SectionHeader(title = "Baixando agora") }
            items(active, key = { it.track.id }) { entry ->
                ActiveDownloadCard(
                    entry = entry,
                    onDelete = { onDelete(entry.track.id) },
                )
            }
        }

        if (failed.isNotEmpty()) {
            item { SectionHeader(title = "Precisa de atenção") }
            items(failed, key = { it.track.id }) { entry ->
                FailedDownloadCard(
                    entry = entry,
                    onRetry = { onRetry(entry.track.id) },
                    onDelete = { onDelete(entry.track.id) },
                )
            }
        }

        if (completed.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Disponíveis offline",
                    action = "Ajustes",
                    onAction = onOpenSettings,
                )
            }
            items(completed, key = { it.track.id }) { entry ->
                val track = entry.track
                TrackRow(
                    track = track,
                    isCurrent = player.current?.id == track.id,
                    isPlaying = player.snapshot.isPlaying,
                    actions = TrackActions(
                        onRemove = { onDelete(track.id) },
                        removeLabel = "Excluir download",
                    ),
                    onClick = { onPlayTrack(track, completedTracks) },
                )
            }
            item {
                InfoBanner(
                    text = "Os arquivos baixados ficam disponíveis mesmo sem internet. " +
                        "Ao excluir, o Gecko remove o áudio do aparelho e mantém a faixa na biblioteca " +
                        "apenas como fonte online.",
                )
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Excluir todos os downloads?") },
            text = {
                Text(
                    "Os áudios offline serão removidos do aparelho. " +
                        "As faixas continuam disponíveis nas fontes online.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteAll = false
                        onDeleteAll()
                    },
                ) {
                    Text("Excluir tudo", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun ActiveDownloadCard(entry: DownloadEntry, onDelete: () -> Unit) {
    GeckoCard {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrackArtwork(
                    track = entry.track,
                    modifier = Modifier.size(46.dp),
                    corner = 12,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = entry.track.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append(entry.track.displayArtist)
                            if (entry.quality.isNotBlank()) append("  •  ${entry.quality}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Remover download",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { entry.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(7.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = when (entry.status) {
                        DownloadStatus.QUEUED -> "Na fila"
                        DownloadStatus.DOWNLOADING -> "Baixando…"
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = buildString {
                        if (entry.bytes > 0) append(formatBytes(entry.bytes))
                        if (entry.progress > 0f) {
                            if (entry.bytes > 0) append("  •  ")
                            append("${(entry.progress * 100).toInt()}%")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FailedDownloadCard(entry: DownloadEntry, onRetry: () -> Unit, onDelete: () -> Unit) {
    GeckoCard {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(21.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = entry.track.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = entry.error ?: "O download não terminou.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tentar de novo")
                }
                TextButton(onClick = onDelete) {
                    Text("Descartar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
