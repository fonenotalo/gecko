package com.auris.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auris.app.model.Track
import com.auris.app.util.formatDuration

data class TrackActions(
    val onPlayNext: (() -> Unit)? = null,
    val onAddToQueue: (() -> Unit)? = null,
    val onDownload: (() -> Unit)? = null,
    val onRemove: (() -> Unit)? = null,
    val onLyrics: (() -> Unit)? = null,
    val removeLabel: String = "Remover da fila",
) {
    val hasActions: Boolean
        get() = onPlayNext != null || onAddToQueue != null || onDownload != null ||
            onRemove != null || onLyrics != null
}

@Composable
fun TrackRow(
    track: Track,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    actions: TrackActions = TrackActions(),
    showArtwork: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showArtwork) {
            TrackArtwork(
                track = track,
                modifier = Modifier.size(52.dp),
                corner = 13,
            )
            Spacer(Modifier.width(12.dp))
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.isDownloaded && track.source.isRemote) {
                    Icon(
                        imageVector = Icons.Rounded.DownloadDone,
                        contentDescription = "Disponível offline",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                        modifier = Modifier
                            .size(12.dp)
                            .padding(end = 0.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = track.displayArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                val hint = track.qualityHint
                if (hint != null) {
                    Text(
                        text = "  ·  $hint",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (track.isLosslessHint) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        },
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        when {
            isCurrent -> PlayingBars(
                active = isPlaying,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .width(18.dp)
                    .height(16.dp),
            )

            track.durationMs > 0 -> Text(
                text = formatDuration(track.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (actions.hasActions) {
            TrackMenu(track = track, actions = actions)
        }
    }
}

@Composable
private fun TrackMenu(track: Track, actions: TrackActions) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Mais opções de ${track.title}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            actions.onPlayNext?.let { action ->
                DropdownMenuItem(
                    text = { Text("Tocar em seguida") },
                    leadingIcon = {
                        Icon(Icons.Rounded.SkipNext, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        action()
                    },
                )
            }
            actions.onAddToQueue?.let { action ->
                DropdownMenuItem(
                    text = { Text("Adicionar à fila") },
                    leadingIcon = {
                        Icon(Icons.Rounded.QueueMusic, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        action()
                    },
                )
            }
            actions.onDownload?.let { action ->
                DropdownMenuItem(
                    text = { Text("Baixar para ouvir offline") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Download, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        action()
                    },
                )
            }
            actions.onLyrics?.let { action ->
                DropdownMenuItem(
                    text = { Text("Abrir letra") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Subtitles, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        action()
                    },
                )
            }
            actions.onRemove?.let { action ->
                DropdownMenuItem(
                    text = { Text(actions.removeLabel) },
                    leadingIcon = {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        action()
                    },
                )
            }
            if (!actions.hasActions) {
                DropdownMenuItem(
                    text = { Text("Nenhuma ação") },
                    leadingIcon = {
                        Icon(Icons.Rounded.PlaylistPlay, contentDescription = null)
                    },
                    onClick = { expanded = false },
                )
            }
        }
    }
}

@Composable
fun PlayingBars(
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "playing-bars")
    val levels = List(3) { index ->
        transition.animateFloat(
            initialValue = if (active) 0.28f else 0.18f,
            targetValue = if (active) 1f else 0.18f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 380 + index * 130,
                    easing = FastOutSlowInEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bar-$index",
        )
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
    ) {
        levels.forEach { level ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(level.value.coerceIn(0.1f, 1f))
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
