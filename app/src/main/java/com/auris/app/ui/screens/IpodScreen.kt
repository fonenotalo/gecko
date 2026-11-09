package com.auris.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auris.app.model.Album
import com.auris.app.model.Artist
import com.auris.app.model.Track
import com.auris.app.ui.LibraryState
import com.auris.app.ui.PlayerUiState
import com.auris.app.ui.components.EmptyState
import com.auris.app.ui.components.TrackArtwork
import com.auris.app.util.formatDuration
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

private sealed interface IpodPage {
    data object Menu : IpodPage
    data object Songs : IpodPage
    data object Albums : IpodPage
    data object Artists : IpodPage
    data object NowPlaying : IpodPage
    data class AlbumDetail(val album: Album) : IpodPage
    data class ArtistDetail(val artist: Artist) : IpodPage
}

private data class IpodRowItem(val label: String, val subtitle: String? = null)

@Composable
fun IpodScreen(
    library: LibraryState,
    player: PlayerUiState,
    onClose: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onShuffleAll: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    var page by remember { mutableStateOf<IpodPage>(IpodPage.Menu) }
    var selected by remember(page) { mutableIntStateOf(0) }

    val scheme = MaterialTheme.colorScheme
    val track = player.current
    val snapshot = player.snapshot
    val menuItems = buildList {
        if (track != null) add(IpodRowItem("Agora tocando", track.title))
        add(IpodRowItem("Músicas", "${library.tracks.size} músicas"))
        add(IpodRowItem("Álbuns", "${library.albums.size} álbuns"))
        add(IpodRowItem("Artistas", "${library.artists.size} artistas"))
        add(IpodRowItem("Aleatório", "Tocar tudo embaralhado"))
    }
    val rows: List<IpodRowItem> = when (val current = page) {
        IpodPage.Menu -> menuItems
        IpodPage.Songs -> library.tracks.map { IpodRowItem(it.title, it.displayArtist) }
        IpodPage.Albums -> library.albums.map { IpodRowItem(it.title, it.artist) }
        IpodPage.Artists -> library.artists.map { IpodRowItem(it.name, "${it.trackCount} músicas") }
        is IpodPage.AlbumDetail -> current.album.tracks.map {
            IpodRowItem(it.title, it.displayArtist)
        }
        is IpodPage.ArtistDetail -> current.artist.tracks.map {
            IpodRowItem(it.title, it.displayAlbum)
        }
        IpodPage.NowPlaying -> emptyList()
    }
    val pageTitle = when (val current = page) {
        IpodPage.Menu -> "iPod"
        IpodPage.Songs -> "Músicas"
        IpodPage.Albums -> "Álbuns"
        IpodPage.Artists -> "Artistas"
        IpodPage.NowPlaying -> "Tocando agora"
        is IpodPage.AlbumDetail -> current.album.title
        is IpodPage.ArtistDetail -> current.artist.name
    }
    val listPage = page != IpodPage.NowPlaying
    val itemCount = rows.size

    LaunchedEffect(itemCount) {
        if (selected > itemCount - 1) selected = (itemCount - 1).coerceAtLeast(0)
    }

    fun selectCurrent() {
        when (val current = page) {
            IpodPage.Menu -> {
                when (menuItems.getOrNull(selected)?.label) {
                    "Agora tocando" -> page = IpodPage.NowPlaying
                    "Músicas" -> page = IpodPage.Songs
                    "Álbuns" -> page = IpodPage.Albums
                    "Artistas" -> page = IpodPage.Artists
                    "Aleatório" -> {
                        onShuffleAll()
                        page = IpodPage.NowPlaying
                    }
                }
            }
            IpodPage.Songs -> {
                val chosen = library.tracks.getOrNull(selected)
                if (chosen != null) {
                    onPlayTrack(chosen, library.tracks)
                    page = IpodPage.NowPlaying
                }
            }
            IpodPage.Albums -> {
                library.albums.getOrNull(selected)?.let { page = IpodPage.AlbumDetail(it) }
            }
            IpodPage.Artists -> {
                library.artists.getOrNull(selected)?.let { page = IpodPage.ArtistDetail(it) }
            }
            is IpodPage.AlbumDetail -> {
                val chosen = current.album.tracks.getOrNull(selected)
                if (chosen != null) {
                    onPlayTrack(chosen, current.album.tracks)
                    page = IpodPage.NowPlaying
                }
            }
            is IpodPage.ArtistDetail -> {
                val chosen = current.artist.tracks.getOrNull(selected)
                if (chosen != null) {
                    onPlayTrack(chosen, current.artist.tracks)
                    page = IpodPage.NowPlaying
                }
            }
            IpodPage.NowPlaying -> onTogglePlay()
        }
    }

    fun goBack() {
        page = when (val current = page) {
            IpodPage.Menu -> {
                onClose()
                IpodPage.Menu
            }
            IpodPage.Songs, IpodPage.Albums, IpodPage.Artists, IpodPage.NowPlaying -> IpodPage.Menu
            is IpodPage.AlbumDetail -> IpodPage.Albums
            is IpodPage.ArtistDetail -> IpodPage.Artists
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.surfaceContainerLowest),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { goBack() }, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        contentDescription = "Voltar",
                        tint = scheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = "AURIS · iPod",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    ),
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Icon(
                    Icons.Rounded.BatteryFull,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(14.dp),
                color = scheme.surface,
                border = BorderStroke(1.5.dp, scheme.outline.copy(alpha = 0.6f)),
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(scheme.surfaceContainer)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = pageTitle,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = scheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (itemCount > 0) {
                            Text(
                                text = "${(selected + 1).coerceAtMost(itemCount)}/$itemCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(color = scheme.outlineVariant)

                    if (page == IpodPage.NowPlaying) {
                        IpodNowPlaying(
                            player = player,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    } else if (rows.isEmpty()) {
                        EmptyState(
                            title = "Vazio",
                            message = if (library.tracks.isEmpty()) {
                                "Adicione músicas à biblioteca para navegar no iPod."
                            } else {
                                "Nada aqui por enquanto."
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            icon = {
                                Icon(
                                    Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = scheme.primary,
                                )
                            },
                        )
                    } else {
                        IpodList(
                            rows = rows,
                            selectedIndex = selected,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            ClickWheel(
                isPlaying = snapshot.isPlaying,
                onMenu = { goBack() },
                onPrevious = onPrevious,
                onNext = onNext,
                onPlayPause = onTogglePlay,
                onSelect = { selectCurrent() },
                onScroll = { delta ->
                    if (listPage && itemCount > 0) {
                        selected = (selected + delta).coerceIn(0, itemCount - 1)
                    }
                },
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun IpodList(
    rows: List<IpodRowItem>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in rows.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        itemsIndexed(rows) { index, item ->
            val selected = index == selectedIndex
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (selected) scheme.primary else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) scheme.onPrimary else scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.subtitle != null) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) {
                            scheme.onPrimary.copy(alpha = 0.78f)
                        } else {
                            scheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (index != rows.lastIndex) {
                HorizontalDivider(
                    color = scheme.outlineVariant.copy(alpha = 0.55f),
                    thickness = 0.5.dp,
                )
            }
        }
    }
}

@Composable
private fun IpodNowPlaying(
    player: PlayerUiState,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val track = player.current
    val snapshot = player.snapshot
    if (track == null) {
        EmptyState(
            title = "Nada tocando",
            message = "Escolha uma música no menu do iPod.",
            modifier = modifier,
            icon = {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = scheme.primary)
            },
        )
        return
    }
    val fraction = if (snapshot.durationMs > 0) {
        (snapshot.positionMs.toFloat() / snapshot.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(
        modifier = modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TrackArtwork(
            track = track,
            modifier = Modifier.size(150.dp),
            corner = 6,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = track.displayArtist,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = track.displayAlbum,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = scheme.primary,
            trackColor = scheme.surfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(snapshot.positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
            Text(
                text = if (snapshot.isPlaying) "▶ Reproduzindo" else "❚❚ Pausado",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
            Text(
                text = formatDuration(snapshot.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun ClickWheel(
    isPlaying: Boolean,
    onMenu: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
    onSelect: () -> Unit,
    onScroll: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val stepPx = with(density) { 20.dp.toPx() }
    var dragAccum by remember { mutableFloatStateOf(0f) }
    val wheelSize = 236.dp

    Box(
        modifier = Modifier
            .size(wheelSize)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val dx = offset.x - cx
                    val dy = offset.y - cy
                    val distance = sqrt(dx * dx + dy * dy)
                    val outer = size.width / 2f
                    if (distance <= outer * 0.54f) {
                        onSelect()
                    } else {
                        val angle = atan2(dy, dx) * 180f / PI.toFloat()
                        when {
                            angle in -135f..-45f -> onMenu()
                            angle in 45f..135f -> onPlayPause()
                            angle > 135f || angle < -135f -> onPrevious()
                            else -> onNext()
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { dragAccum = 0f },
                    onDragCancel = { dragAccum = 0f },
                ) { change, dragAmount ->
                    change.consume()
                    dragAccum += dragAmount.y
                    while (dragAccum >= stepPx) {
                        dragAccum -= stepPx
                        onScroll(1)
                    }
                    while (dragAccum <= -stepPx) {
                        dragAccum += stepPx
                        onScroll(-1)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val outer = size.minDimension / 2f
            drawCircle(color = scheme.surfaceContainerHigh, radius = outer)
            drawCircle(
                color = Color.White.copy(alpha = 0.05f),
                radius = outer * 0.96f,
                style = Stroke(width = 2f),
            )
            drawCircle(color = scheme.surfaceContainerLowest, radius = outer * 0.60f)
            drawCircle(
                color = scheme.outline.copy(alpha = 0.55f),
                radius = outer * 0.60f,
                style = Stroke(width = 1.5f),
            )
            drawCircle(
                color = scheme.primary.copy(alpha = 0.10f),
                radius = outer * 0.53f,
            )
        }
        Text(
            text = "MENU",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            ),
            color = scheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 15.dp),
        )
        Icon(
            Icons.Rounded.SkipPrevious,
            contentDescription = "Anterior",
            tint = scheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp)
                .size(22.dp),
        )
        Icon(
            Icons.Rounded.SkipNext,
            contentDescription = "Próxima",
            tint = scheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 20.dp)
                .size(22.dp),
        )
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
            tint = scheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 15.dp)
                .size(24.dp),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "AURIS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                ),
                color = scheme.primary,
            )
            Text(
                text = "selecionar",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = scheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
        }
    }
}
