package com.auris.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.auris.app.data.LyricLine
import com.auris.app.data.Lyrics
import com.auris.app.model.AppSettings
import com.auris.app.model.Track
import com.auris.app.playback.AudioQualityInfo
import com.auris.app.ui.PlayerUiState
import com.auris.app.ui.components.EmptyState
import com.auris.app.ui.components.QualityBadge
import com.auris.app.ui.components.QualityDetails
import com.auris.app.ui.components.TrackActions
import com.auris.app.ui.components.TrackArtwork
import com.auris.app.ui.components.TrackRow
import com.auris.app.ui.theme.accentColor
import com.auris.app.util.formatDuration

/**
 * Full screen "now playing". The background is tinted by the palette extracted
 * from the album art, so every record feels like its own space. Swipe the top
 * handle down (or use the arrow) to go back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    player: PlayerUiState,
    settings: AppSettings,
    onClose: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onDownload: (Track) -> Unit,
    onOpenKaraoke: () -> Unit,
    onImportLyrics: (Uri) -> Unit,
    onRemoveLyrics: () -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onSeekToQueueItem: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val accent = player.palette?.accentColor(dark) ?: scheme.primary
    val track = player.current
    val snapshot = player.snapshot

    var showQuality by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onImportLyrics) }

    val qualitySheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val queueSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lyricsSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to accent.copy(alpha = if (dark) 0.38f else 0.30f),
                        0.48f to accent.copy(alpha = if (dark) 0.10f else 0.07f),
                        1f to scheme.background,
                    ),
                ),
        )

        if (track == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PlayerTopBar(
                    subtitle = "Auris",
                    onClose = onClose,
                    onOpenKaraoke = onOpenKaraoke,
                )
                Spacer(Modifier.height(24.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        title = "Nada tocando ainda",
                        message = "Escolha uma música na biblioteca para começar. O Auris mostra aqui a qualidade real do áudio.",
                        actionLabel = "Fechar",
                        onAction = onClose,
                    )
                }
            }
            return@Box
        }

        val fraction = if (snapshot.durationMs > 0) {
            (snapshot.positionMs.toFloat() / snapshot.durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DragHandle(
                color = scheme.onSurfaceVariant.copy(alpha = 0.45f),
                onClose = onClose,
            )
            PlayerTopBar(
                subtitle = buildString {
                    append(track.source.label)
                    if (player.infiniteMix) append("  •  Mix infinito")
                },
                onClose = onClose,
                onOpenKaraoke = onOpenKaraoke,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                TrackArtwork(
                    track = track,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .padding(horizontal = 4.dp),
                    corner = 28,
                    elevated = true,
                )
                if (snapshot.isBuffering) {
                    CircularProgressIndicator(
                        color = scheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(42.dp),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = buildString {
                            append(track.displayArtist)
                            if (track.displayAlbum.isNotBlank()) append("  •  ")
                            if (track.displayAlbum.isNotBlank()) append(track.displayAlbum)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (track.source.isRemote) {
                    val downloaded = track.isDownloaded
                    IconButton(
                        onClick = { if (!downloaded) onDownload(track) },
                        enabled = !downloaded,
                    ) {
                        Icon(
                            imageVector = if (downloaded) {
                                Icons.Rounded.DownloadDone
                            } else {
                                Icons.Rounded.Download
                            },
                            contentDescription = if (downloaded) {
                                "Disponível offline"
                            } else {
                                "Baixar para ouvir offline"
                            },
                            tint = if (downloaded) {
                                scheme.primary
                            } else {
                                scheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            SeekBar(
                fraction = fraction,
                positionMs = snapshot.positionMs,
                durationMs = snapshot.durationMs,
                onSeekToFraction = onSeekToFraction,
                trackId = track.id,
            )

            Spacer(Modifier.height(4.dp))
            PlaybackControls(
                isPlaying = snapshot.isPlaying,
                shuffle = snapshot.shuffle,
                repeatMode = snapshot.repeatMode,
                onTogglePlay = onTogglePlay,
                onNext = onNext,
                onPrevious = onPrevious,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
            )

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerAction(
                    icon = {
                        QualityBadge(player.snapshot.quality, label = true)
                    },
                    label = "Qualidade",
                    onClick = { showQuality = true },
                )
                PlayerAction(
                    icon = {
                        Icon(
                            Icons.Rounded.Mic,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    label = "Karaokê",
                    onClick = onOpenKaraoke,
                )
                PlayerAction(
                    icon = {
                        Icon(
                            Icons.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    label = "Fila • ${player.queue.size}",
                    onClick = { showQueue = true },
                )
                PlayerAction(
                    icon = {
                        Icon(
                            Icons.Rounded.Subtitles,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    label = if (player.lyrics == null) "Letra" else "Letra ✓",
                    onClick = { showLyrics = true },
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }

    if (showQuality) {
        track?.let { qualityTrack ->
            ModalBottomSheet(
                onDismissRequest = { showQuality = false },
                sheetState = qualitySheet,
            ) {
                QualitySheetContent(
                    track = qualityTrack,
                    quality = snapshot.quality,
                    settings = settings,
                    onDownload = {
                        showQuality = false
                        onDownload(qualityTrack)
                    },
                )
            }
        }
    }

    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            sheetState = queueSheet,
        ) {
            QueueSheetContent(
                player = player,
                onSeekToQueueItem = { index ->
                    showQueue = false
                    onSeekToQueueItem(index)
                },
                onRemove = onRemoveFromQueue,
            )
        }
    }

    if (showLyrics) {
        ModalBottomSheet(
            onDismissRequest = { showLyrics = false },
            sheetState = lyricsSheet,
        ) {
            LyricsSheetContent(
                lyrics = player.lyrics,
                positionMs = snapshot.positionMs,
                onImport = {
                    showLyrics = false
                    importLauncher.launch(arrayOf("*/*"))
                },
                onRemove = onRemoveLyrics,
            )
        }
    }
}

@Composable
private fun DragHandle(color: Color, onClose: () -> Unit) {
    var accumulated by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (accumulated > 110f) onClose()
                        accumulated = 0f
                    },
                    onDragCancel = { accumulated = 0f },
                ) { _, dragAmount ->
                    accumulated += dragAmount
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 42.dp, height = 4.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

@Composable
private fun PlayerTopBar(
    subtitle: String,
    onClose: () -> Unit,
    onOpenKaraoke: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Fechar player",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(30.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "TOCANDO AGORA",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onOpenKaraoke) {
            Icon(
                Icons.Rounded.Mic,
                contentDescription = "Abrir karaokê",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SeekBar(
    fraction: Float,
    positionMs: Long,
    durationMs: Long,
    trackId: String,
    onSeekToFraction: (Float) -> Unit,
) {
    var dragFraction by remember(trackId) { mutableStateOf<Float?>(null) }
    val displayed = dragFraction ?: fraction
    val scheme = MaterialTheme.colorScheme

    Slider(
        value = displayed,
        onValueChange = { dragFraction = it },
        onValueChangeFinished = {
            dragFraction?.let(onSeekToFraction)
            dragFraction = null
        },
        colors = SliderDefaults.colors(
            thumbColor = scheme.primary,
            activeTrackColor = scheme.primary,
            inactiveTrackColor = scheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = formatDuration(
                if (dragFraction != null && durationMs > 0) {
                    (durationMs * dragFraction!!).toLong()
                } else {
                    positionMs
                },
            ),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        Text(
            text = formatDuration(durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    shuffle: Boolean,
    repeatMode: Int,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggleShuffle) {
            Icon(
                Icons.Rounded.Shuffle,
                contentDescription = if (shuffle) "Desativar aleatório" else "Aleatório",
                tint = if (shuffle) scheme.primary else scheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onPrevious, modifier = Modifier.size(52.dp)) {
            Icon(
                Icons.Rounded.SkipPrevious,
                contentDescription = "Anterior",
                tint = scheme.onSurface,
                modifier = Modifier.size(36.dp),
            )
        }
        FilledIconButton(
            onClick = onTogglePlay,
            modifier = Modifier.size(76.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = scheme.primary,
                contentColor = scheme.onPrimary,
            ),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                modifier = Modifier.size(40.dp),
            )
        }
        IconButton(onClick = onNext, modifier = Modifier.size(52.dp)) {
            Icon(
                Icons.Rounded.SkipNext,
                contentDescription = "Próxima",
                tint = scheme.onSurface,
                modifier = Modifier.size(36.dp),
            )
        }
        IconButton(onClick = onCycleRepeat) {
            Icon(
                imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) {
                    Icons.Rounded.RepeatOne
                } else {
                    Icons.Rounded.Repeat
                },
                contentDescription = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> "Repetir faixa"
                    Player.REPEAT_MODE_ALL -> "Repetir fila"
                    else -> "Repetição desligada"
                },
                tint = if (repeatMode == Player.REPEAT_MODE_OFF) {
                    scheme.onSurfaceVariant.copy(alpha = 0.5f)
                } else {
                    scheme.primary
                },
            )
        }
    }
}

@Composable
private fun PlayerAction(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.height(22.dp),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun QualitySheetContent(
    track: Track,
    quality: AudioQualityInfo?,
    settings: AppSettings,
    onDownload: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 28.dp),
    ) {
        Text(
            text = "Qualidade do áudio",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))
        QualityDetails(
            track = track,
            quality = quality,
            preferred = settings.preferredQuality,
            losslessFirst = settings.losslessFirst,
        )
        if (track.source.isRemote && !track.isDownloaded) {
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Baixar em ${settings.downloadQuality.title}")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun QueueSheetContent(
    player: PlayerUiState,
    onSeekToQueueItem: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = "Fila de reprodução",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${player.queue.size} faixas" +
                    if (player.infiniteMix) "  •  mix infinito" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        if (player.queue.isEmpty()) {
            EmptyState(
                title = "Fila vazia",
                message = "Toque em uma música para montar uma fila.",
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    bottom = 12.dp,
                ),
            ) {
                itemsIndexed(player.queue) { index, track ->
                    TrackRow(
                        track = track,
                        isCurrent = index == player.snapshot.index,
                        isPlaying = player.snapshot.isPlaying,
                        actions = TrackActions(
                            onRemove = { onRemove(index) },
                        ),
                        onClick = { onSeekToQueueItem(index) },
                    )
                }
            }
        }
    }
}

private fun currentLineIndex(lines: List<LyricLine>, positionMs: Long): Int =
    lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)

@Composable
private fun LyricsSheetContent(
    lyrics: Lyrics?,
    positionMs: Long,
    onImport: () -> Unit,
    onRemove: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val synced = lyrics?.synced == true
    val currentIndex = if (synced && lyrics != null) {
        currentLineIndex(lyrics.lines, positionMs)
    } else {
        -1
    }
    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && lyrics != null) {
            listState.animateScrollToItem(currentIndex.coerceIn(0, lyrics.lines.lastIndex))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Letra",
                    style = MaterialTheme.typography.headlineSmall,
                    color = scheme.onSurface,
                )
                if (lyrics != null) {
                    Text(
                        text = lyrics.source,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onImport) { Text("Importar .lrc") }
            if (lyrics != null) {
                TextButton(onClick = onRemove) { Text("Remover") }
            }
        }
        Spacer(Modifier.height(6.dp))
        if (lyrics == null) {
            EmptyState(
                title = "Sem letra para esta faixa",
                message = "Importe um arquivo .lrc para cante junto com a sincronia certa. " +
                    "O arquivo fica salvo apenas neste aparelho.",
                actionLabel = "Importar letra",
                onAction = onImport,
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 26.dp,
                    end = 26.dp,
                    top = 10.dp,
                    bottom = 20.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(lyrics.lines) { index, line ->
                    val active = index == currentIndex
                    Text(
                        text = line.text,
                        style = if (active) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                        color = if (active) {
                            scheme.primary
                        } else {
                            scheme.onSurfaceVariant.copy(alpha = 0.7f)
                        },
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
