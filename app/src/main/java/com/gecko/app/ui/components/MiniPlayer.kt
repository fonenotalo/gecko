package com.gecko.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gecko.app.navigation.Routes
import com.gecko.app.ui.PlayerUiState

@Composable
fun MiniPlayer(
    player: PlayerUiState,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = player.current ?: return
    val snapshot = player.snapshot
    var swipeUpAmount by remember { mutableFloatStateOf(0f) }
    val fraction = if (snapshot.durationMs > 0) {
        (snapshot.positionMs.toFloat() / snapshot.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onOpen)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (swipeUpAmount <= -70f) {
                            onOpen()
                        }
                        swipeUpAmount = 0f
                    },
                    onDragCancel = { swipeUpAmount = 0f },
                ) { _, dragAmount ->
                    swipeUpAmount += dragAmount
                }
            },
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
        shadowElevation = 10.dp,
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 6.dp, top = 8.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrackArtwork(
                    track = track,
                    modifier = Modifier.size(48.dp),
                    corner = 13,
                )
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = track.displayArtist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        val quality = snapshot.quality
                        if (quality != null) {
                            Spacer(Modifier.width(6.dp))
                            QualityBadge(quality)
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
                if (snapshot.isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    IconButton(onClick = onTogglePlay) {
                        Icon(
                            imageVector = if (snapshot.isPlaying) {
                                Icons.Rounded.Pause
                            } else {
                                Icons.Rounded.PlayArrow
                            },
                            contentDescription = if (snapshot.isPlaying) "Pausar" else "Reproduzir",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .padding(horizontal = 10.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
fun GeckoBottomBar(
    selectedRoute: String?,
    player: PlayerUiState,
    onSelect: (String) -> Unit,
    onOpenPlayer: () -> Unit,
    onTogglePlay: () -> Unit,
) {
    val items = listOf(
        BottomItem(Routes.HOME, "Início", Icons.Rounded.Home),
        BottomItem(Routes.LIBRARY, "Biblioteca", Icons.Rounded.LibraryMusic),
        BottomItem(Routes.DOWNLOADS, "Downloads", Icons.Rounded.Download),
        BottomItem(Routes.SETTINGS, "Ajustes", Icons.Rounded.Settings),
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        border = BorderStroke(
            0.7.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        ),
        shadowElevation = 24.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 6.dp),
        ) {
            if (player.current != null) {
                MiniPlayer(
                    player = player,
                    onOpen = onOpenPlayer,
                    onTogglePlay = onTogglePlay,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                items.forEach { item ->
                    BottomBarItem(
                        item = item,
                        selected = selectedRoute == item.route,
                        onClick = { onSelect(item.route) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private data class BottomItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
private fun BottomBarItem(
    item: BottomItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    if (selected) scheme.primary.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent,
                )
                .padding(horizontal = 16.dp, vertical = 5.dp),
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = if (selected) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
        )
    }
}
