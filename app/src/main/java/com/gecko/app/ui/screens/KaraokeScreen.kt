package com.gecko.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gecko.app.data.Lyrics
import com.gecko.app.ui.PlayerUiState
import com.gecko.app.ui.components.EmptyState
import com.gecko.app.ui.theme.accentColor
import com.gecko.app.util.formatDuration

@Composable
fun KaraokeScreen(
    player: PlayerUiState,
    onClose: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
    onImportLyrics: (Uri) -> Unit,
    onRemoveLyrics: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val accent = player.palette?.accentColor(dark) ?: scheme.tertiary
    val track = player.current
    val lyrics = player.lyrics
    val lyricsLoading = player.lyricsLoading
    val snapshot = player.snapshot

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onImportLyrics) }

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
                        0f to accent.copy(alpha = if (dark) 0.30f else 0.24f),
                        0.55f to scheme.background,
                        1f to scheme.background,
                    ),
                ),
        )
        StageGlow(accent = accent)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Fechar karaokê",
                        tint = scheme.onSurface,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "KARAOKÊ",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp,
                        ),
                        color = accent,
                    )
                    Text(
                        text = if (lyrics == null) "Sem letra" else lyrics.source,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                    Icon(
                        Icons.Rounded.Subtitles,
                        contentDescription = "Importar letra .lrc",
                        tint = scheme.onSurfaceVariant,
                    )
                }
                if (lyrics != null) {
                    IconButton(onClick = onRemoveLyrics) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Remover letra",
                            tint = scheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (track == null) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        title = "Escolha uma música",
                        message = "O karaokê mostra a letra sincronizada da faixa em reprodução. " +
                            "Comece pela biblioteca e volte aqui.",
                        icon = {
                            Icon(Icons.Rounded.Mic, contentDescription = null, tint = accent)
                        },
                        actionLabel = "Fechar",
                        onAction = onClose,
                    )
                }
                return@Column
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = track.title,
                style = MaterialTheme.typography.headlineMedium,
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = track.displayArtist,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (lyrics == null) {
                    if (lyricsLoading) {
                        EmptyState(
                            title = "Buscando letra…",
                            message = "O Gecko está consultando os provedores ativos para esta faixa.",
                            icon = {
                                Icon(Icons.Rounded.Mic, contentDescription = null, tint = accent)
                            },
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        EmptyState(
                            title = "Cante junto",
                            message = "Nenhum provedor ativo encontrou letra. " +
                                "Importe um arquivo .lrc com os tempos da letra; ele fica salvo " +
                                "apenas neste aparelho.",
                            icon = {
                                Icon(Icons.Rounded.Mic, contentDescription = null, tint = accent)
                            },
                            actionLabel = "Importar .lrc",
                            onAction = { importLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                } else {
                    KaraokeLyrics(
                        lyrics = lyrics,
                        positionMs = snapshot.positionMs,
                        accent = accent,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            KaraokeSeekBar(
                positionMs = snapshot.positionMs,
                durationMs = snapshot.durationMs,
                trackId = track.id,
                onSeekToFraction = onSeekToFraction,
                accent = accent,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious, modifier = Modifier.size(54.dp)) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = scheme.onSurface,
                        modifier = Modifier.size(38.dp),
                    )
                }
                FilledIconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(76.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = accent,
                        contentColor = scheme.surface,
                    ),
                ) {
                    Icon(
                        imageVector = if (snapshot.isPlaying) {
                            Icons.Rounded.Pause
                        } else {
                            Icons.Rounded.PlayArrow
                        },
                        contentDescription = if (snapshot.isPlaying) "Pausar" else "Reproduzir",
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.size(54.dp)) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Próxima",
                        tint = scheme.onSurface,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (player.infiniteMix) {
                    "Mix infinito ativo  •  cante sem parar"
                } else {
                    "Letra sincronizada  •  foco na voz"
                },
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun StageGlow(accent: Color) {
    val transition = rememberInfiniteTransition(label = "karaoke-stage")
    val pulse by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "stage-pulse",
    )
    val drift by transition.animateFloat(
        initialValue = -0.06f,
        targetValue = 0.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(6800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "stage-drift",
    )
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            color = accent.copy(alpha = 0.16f),
            radius = size.minDimension * 0.52f * pulse,
            center = Offset(size.width * (0.5f + drift), size.height * 0.34f),
        )
        drawCircle(
            color = accent.copy(alpha = 0.08f),
            radius = size.width * 0.44f * (2f - pulse),
            center = Offset(size.width * (0.22f - drift), size.height * 0.66f),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.03f),
            radius = size.width * 0.30f * pulse,
            center = Offset(size.width * 0.82f, size.height * 0.52f),
        )
    }
}

@Composable
private fun KaraokeLyrics(
    lyrics: Lyrics,
    positionMs: Long,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val currentIndex = if (lyrics.synced && lyrics.lines.isNotEmpty()) {
        lyrics.lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
    } else {
        -1
    }
    LaunchedEffect(currentIndex, lyrics) {
        if (currentIndex >= 0) {
            listState.animateScrollToItem((currentIndex - 2).coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 90.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            val active = index == currentIndex
            val sung = currentIndex >= 0 && index < currentIndex
            Text(
                text = line.text,
                style = when {
                    active -> MaterialTheme.typography.displaySmall
                    lyrics.synced -> MaterialTheme.typography.titleLarge
                    else -> MaterialTheme.typography.bodyLarge
                },
                color = when {
                    active -> accent
                    sung -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                },
                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun KaraokeSeekBar(
    positionMs: Long,
    durationMs: Long,
    trackId: String,
    onSeekToFraction: (Float) -> Unit,
    accent: Color,
) {
    var dragFraction by remember(trackId) { mutableStateOf<Float?>(null) }
    val fraction = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val displayed = dragFraction ?: fraction

    Slider(
        value = displayed,
        onValueChange = { dragFraction = it },
        onValueChangeFinished = {
            dragFraction?.let(onSeekToFraction)
            dragFraction = null
        },
        colors = SliderDefaults.colors(
            thumbColor = accent,
            activeTrackColor = accent,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatDuration(durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
