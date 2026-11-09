package com.gecko.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.gecko.app.model.AppSettings
import com.gecko.app.model.Track
import com.gecko.app.ui.LibraryState
import com.gecko.app.ui.LocalAppContainer
import com.gecko.app.ui.PlayerUiState
import com.gecko.app.ui.components.GeckoCard
import com.gecko.app.ui.components.EmptyState
import com.gecko.app.ui.components.InfoBanner
import com.gecko.app.ui.components.SectionHeader
import com.gecko.app.ui.components.TrackActions
import com.gecko.app.ui.components.TrackArtwork
import com.gecko.app.ui.components.TrackRow
import com.gecko.app.util.formatDuration
import com.gecko.app.util.greetingForHour
import java.util.Calendar

@Composable
fun HomeScreen(
    library: LibraryState,
    player: PlayerUiState,
    settings: AppSettings,
    pluginCount: Int,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onStartMix: () -> Unit,
    onShuffleAll: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenKaraoke: () -> Unit,
    onOpenIpod: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    onGenerateDemo: () -> Unit,
    onRefresh: () -> Unit,
    onDownloadTrack: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onRefresh() }

    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "GECKO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = greetingForHour(hour) + ".",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Sua música, do seu jeito.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledIconButton(
                    onClick = onShuffleAll,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = "Tocar tudo aleatoriamente")
                }
            }
        }

        item {
            MixHeroCard(
                recent = library.recent,
                onClick = onStartMix,
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModeCard(
                    title = "Mix infinito",
                    subtitle = "Sem fim",
                    icon = Icons.Rounded.Waves,
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = onStartMix,
                    modifier = Modifier.weight(1f),
                )
                ModeCard(
                    title = "Karaokê",
                    subtitle = "Cante junto",
                    icon = Icons.Rounded.Mic,
                    tint = MaterialTheme.colorScheme.tertiary,
                    onClick = onOpenKaraoke,
                    modifier = Modifier.weight(1f),
                )
                ModeCard(
                    title = "iPod",
                    subtitle = "Clássico",
                    icon = Icons.Rounded.Album,
                    tint = MaterialTheme.colorScheme.secondary,
                    onClick = onOpenIpod,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (library.recent.isNotEmpty()) {
            item {
                SectionHeader(title = "Continuar ouvindo")
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(library.recent.take(8), key = { it.id }) { track ->
                        RecentCard(
                            track = track,
                            isPlaying = player.current?.id == track.id && player.snapshot.isPlaying,
                            onClick = { onPlayTrack(track, library.recent) },
                        )
                    }
                }
            }
        }

        item {
            SourceCard(
                configured = settings.ytMusicUrl.isNotBlank(),
                pluginCount = pluginCount,
                onClick = onOpenSettings,
            )
        }

        item {
            SectionHeader(
                title = "Sua biblioteca",
                action = if (library.tracks.isEmpty()) null else "Ver tudo",
                onAction = onOpenLibrary,
            )
        }

        if (library.loading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Lendo sua biblioteca…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (!library.hasPermission) {
            item {
                PermissionCard(
                    onGrant = { permissionLauncher.launch(container.localMusic.audioPermission) },
                    onOpenSettings = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            ),
                        )
                    },
                    onDemo = onGenerateDemo,
                )
            }
        } else if (library.tracks.isEmpty()) {
            item {
                GeckoCard {
                    EmptyState(
                        title = "Nenhuma música local ainda",
                        message = "Copie arquivos de áudio para o aparelho ou experimente o Gecko com uma biblioteca de demonstração em lossless.",
                        icon = {
                            Icon(
                                Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        actionLabel = null,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = onGenerateDemo,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Ouvir demo")
                        }
                        OutlinedButton(
                            onClick = onRefresh,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Atualizar")
                        }
                    }
                }
            }
        } else {
            items(library.tracks.take(7), key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    isCurrent = player.current?.id == track.id,
                    isPlaying = player.snapshot.isPlaying,
                    actions = TrackActions(
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) },
                        onDownload = if (track.source.isRemote && !track.isDownloaded) {
                            { onDownloadTrack(track) }
                        } else {
                            null
                        },
                    ),
                    onClick = { onPlayTrack(track, library.tracks) },
                )
            }
            if (library.tracks.size > 7) {
                item {
                    TextButton(
                        onClick = onOpenLibrary,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Ver as ${library.tracks.size} músicas")
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        if (library.hasPermission && library.tracks.isNotEmpty()) {
            item {
                InfoBanner(
                    text = "Dica: toque na capa durante a reprodução para ver a qualidade real do áudio, " +
                        "a fonte e baixar em lossless para ouvir offline.",
                    tone = MaterialTheme.colorScheme.secondary,
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Headphones,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Lossless primeiro  •  sem anúncios  •  sem conta",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "As cores acompanham a arte do álbum.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun MixHeroCard(recent: List<Track>, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.linearGradient(
                    listOf(scheme.primaryContainer, scheme.tertiaryContainer),
                ),
            )
            .clickable(onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = size.width * 0.55f,
                center = Offset(size.width * 1.02f, size.height * -0.1f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.07f),
                radius = size.width * 0.42f,
                center = Offset(size.width * 0.18f, size.height * 1.1f),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, end = 140.dp, bottom = 22.dp),
        ) {
            Text(
                text = "MIX PESSOAL INFINITO",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                ),
                color = scheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Feito para\nvocê, sem fim.",
                style = MaterialTheme.typography.headlineLarge,
                color = scheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = scheme.onPrimaryContainer,
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = scheme.primaryContainer,
                        modifier = Modifier
                            .padding(7.dp)
                            .size(20.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Começar mix",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onPrimaryContainer,
                )
            }
        }

        MixArtworkStack(
            tracks = recent.take(3),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 18.dp),
        )
    }
}

@Composable
private fun MixArtworkStack(tracks: List<Track>, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(width = 112.dp, height = 132.dp)) {
        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(96.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Waves,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.size(40.dp),
                )
            }
        } else {
            tracks.forEachIndexed { index, track ->
                TrackArtwork(
                    track = track,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(82.dp)
                        .offset(x = (index * 7).dp, y = ((index - 1) * 16).dp)
                        .graphicsLayer {
                            rotationZ = (index - 1) * 8f
                            alpha = if (index == 0) 1f else 0.95f
                        },
                    corner = 18,
                    elevated = index == 0,
                )
            }
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(104.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RecentCard(track: Track, isPlaying: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box {
            TrackArtwork(
                track = track,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp),
                corner = 20,
            )
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = track.displayArtist,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SourceCard(configured: Boolean, pluginCount: Int, onClick: () -> Unit) {
    GeckoCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
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
                    Icons.Rounded.PlayCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Fontes e streaming",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(
                            if (configured) {
                                "YouTube Music conectado"
                            } else {
                                "YouTube Music opcional"
                            },
                        )
                        if (pluginCount > 0) append("  •  $pluginCount complemento(s)")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (configured) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    ),
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun PermissionCard(
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
    onDemo: () -> Unit,
) {
    GeckoCard {
        EmptyState(
            title = "Gecko precisa das suas músicas",
            message = "Conceda acesso aos arquivos de áudio do aparelho. Nada sai do seu telefone e não é preciso criar conta.",
            icon = {
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) {
                Text("Permitir acesso à biblioteca")
            }
            Spacer(Modifier.height(6.dp))
            Row {
                TextButton(onClick = onOpenSettings) { Text("Abrir ajustes") }
                TextButton(onClick = onDemo) { Text("Usar demonstração") }
            }
        }
    }
}
