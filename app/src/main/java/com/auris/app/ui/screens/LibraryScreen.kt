package com.auris.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.auris.app.model.Album
import com.auris.app.model.Artist
import com.auris.app.model.Track
import com.auris.app.ui.LibraryState
import com.auris.app.ui.PlayerUiState
import com.auris.app.ui.SearchState
import com.auris.app.ui.components.ArtworkImage
import com.auris.app.ui.components.EmptyState
import com.auris.app.ui.components.InfoBanner
import com.auris.app.ui.components.TrackActions
import com.auris.app.ui.components.TrackRow
import com.auris.app.util.formatTotalDuration

private enum class LibraryTab(val label: String) {
    SONGS("Músicas"),
    ALBUMS("Álbuns"),
    ARTISTS("Artistas"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    library: LibraryState,
    player: PlayerUiState,
    search: SearchState,
    remoteEnabled: Boolean,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onPlayAlbum: (Album) -> Unit,
    onShuffleAlbum: (Album) -> Unit,
    onPlayArtist: (Artist) -> Unit,
    onShuffleArtist: (Artist) -> Unit,
    onOpenDownloads: () -> Unit,
    onGenerateDemo: () -> Unit,
    onRefresh: () -> Unit,
) {
    var tab by remember { mutableStateOf(LibraryTab.SONGS) }
    var selectedAlbum by remember { mutableStateOf<Album?>(null) }
    var selectedArtist by remember { mutableStateOf<Artist?>(null) }
    val searching = search.query.isNotBlank()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 30.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Biblioteca",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = buildString {
                            append("${library.tracks.size} músicas")
                            if (library.albums.isNotEmpty()) append("  •  ${library.albums.size} álbuns")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = onOpenDownloads,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Offline")
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(
                value = search.query,
                onValueChange = onSearch,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                placeholder = { Text("Buscar músicas, álbuns e artistas") },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (search.query.isNotBlank()) {
                        IconButton(onClick = onClearSearch) {
                            Icon(Icons.Rounded.Close, contentDescription = "Limpar busca")
                        }
                    }
                },
            )
        }

        if (!searching) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                TabRow(
                    selectedTabIndex = tab.ordinal,
                    containerColor = Color.Transparent,
                    divider = {},
                ) {
                    LibraryTab.entries.forEach { entry ->
                        Tab(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            text = {
                                Text(
                                    text = entry.label,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            },
                        )
                    }
                }
            }

            when (tab) {
                LibraryTab.SONGS -> {
                    if (library.tracks.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LibraryEmptyState(
                                hasPermission = library.hasPermission,
                                loading = library.loading,
                                onGenerateDemo = onGenerateDemo,
                                onRefresh = onRefresh,
                            )
                        }
                    } else {
                        gridItems(
                            items = library.tracks,
                            key = { it.id },
                            span = { GridItemSpan(maxLineSpan) },
                        ) { track ->
                            LibraryTrackRow(
                                track = track,
                                player = player,
                                onPlayTrack = onPlayTrack,
                                onPlayNext = onPlayNext,
                                onAddToQueue = onAddToQueue,
                                onDownload = onDownload,
                                queue = library.tracks,
                            )
                        }
                    }
                }

                LibraryTab.ALBUMS -> {
                    if (library.albums.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(
                                title = "Nenhum álbum por aqui",
                                message = "Os álbuns aparecem automaticamente a partir das tags das suas músicas.",
                                icon = {
                                    Icon(Icons.Rounded.Album, contentDescription = null)
                                },
                            )
                        }
                    } else {
                        gridItems(library.albums, key = { it.key }) { album ->
                            AlbumCard(album = album, onClick = { selectedAlbum = album })
                        }
                    }
                }

                LibraryTab.ARTISTS -> {
                    if (library.artists.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(
                                title = "Nenhum artista ainda",
                                message = "Artistas são agrupados pelas tags de artista das faixas.",
                                icon = {
                                    Icon(Icons.Rounded.Person, contentDescription = null)
                                },
                            )
                        }
                    } else {
                        gridItems(library.artists, key = { it.key }) { artist ->
                            ArtistCard(artist = artist, onClick = { selectedArtist = artist })
                        }
                    }
                }
            }
        } else {
            if (search.local.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "No aparelho",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                gridItems(
                    items = search.local,
                    key = { "local-${it.id}" },
                    span = { GridItemSpan(maxLineSpan) },
                ) { track ->
                    LibraryTrackRow(
                        track = track,
                        player = player,
                        onPlayTrack = onPlayTrack,
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onDownload = onDownload,
                        queue = search.local,
                    )
                }
            }

            if (remoteEnabled) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Nas suas fontes",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        if (search.loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
                if (search.remote.isEmpty() && !search.loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "Nada encontrado nas fontes conectadas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
                gridItems(
                    items = search.remote,
                    key = { "remote-${it.id}" },
                    span = { GridItemSpan(maxLineSpan) },
                ) { track ->
                    LibraryTrackRow(
                        track = track,
                        player = player,
                        onPlayTrack = onPlayTrack,
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onDownload = onDownload,
                        queue = search.remote,
                    )
                }
            }

            if (search.local.isEmpty() && search.remote.isEmpty() && !search.loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = "Nada encontrado",
                        message = if (remoteEnabled) {
                            "Tente outro termo ou confira a conexão com suas fontes."
                        } else {
                            "A busca remota fica disponível quando você conecta o YouTube Music " +
                                "ou instala um complemento lossless nos ajustes."
                        },
                        icon = {
                            Icon(Icons.Rounded.Search, contentDescription = null)
                        },
                    )
                }
            }
        }
    }

    selectedAlbum?.let { album ->
        AlbumSheet(
            album = album,
            player = player,
            onDismiss = { selectedAlbum = null },
            onPlayAlbum = {
                selectedAlbum = null
                onPlayAlbum(album)
            },
            onShuffleAlbum = {
                selectedAlbum = null
                onShuffleAlbum(album)
            },
            onPlayTrack = { track ->
                selectedAlbum = null
                onPlayTrack(track, album.tracks)
            },
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onDownload = onDownload,
        )
    }

    selectedArtist?.let { artist ->
        ArtistSheet(
            artist = artist,
            player = player,
            onDismiss = { selectedArtist = null },
            onPlayArtist = {
                selectedArtist = null
                onPlayArtist(artist)
            },
            onShuffleArtist = {
                selectedArtist = null
                onShuffleArtist(artist)
            },
            onPlayTrack = { track ->
                selectedArtist = null
                onPlayTrack(track, artist.tracks)
            },
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onDownload = onDownload,
        )
    }
}

@Composable
private fun LibraryTrackRow(
    track: Track,
    player: PlayerUiState,
    queue: List<Track>,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onDownload: (Track) -> Unit,
) {
    TrackRow(
        track = track,
        isCurrent = player.current?.id == track.id,
        isPlaying = player.snapshot.isPlaying,
        actions = TrackActions(
            onPlayNext = { onPlayNext(track) },
            onAddToQueue = { onAddToQueue(track) },
            onDownload = if (track.source.isRemote && !track.isDownloaded) {
                { onDownload(track) }
            } else {
                null
            },
        ),
        onClick = { onPlayTrack(track, queue) },
    )
}

@Composable
private fun LibraryEmptyState(
    hasPermission: Boolean,
    loading: Boolean,
    onGenerateDemo: () -> Unit,
    onRefresh: () -> Unit,
) {
    EmptyState(
        title = when {
            loading -> "Lendo sua biblioteca…"
            !hasPermission -> "Sem acesso às músicas"
            else -> "Nenhuma música local ainda"
        },
        message = when {
            loading -> "Só um instante."
            !hasPermission -> "Autorize o acesso aos arquivos de áudio nos ajustes do Android " +
                "ou experimente a biblioteca de demonstração em lossless."
            else -> "Copie arquivos de áudio para o aparelho ou experimente a demonstração em lossless."
        },
        icon = {
            Icon(Icons.Rounded.MusicNote, contentDescription = null)
        },
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Button(
            onClick = onGenerateDemo,
            modifier = Modifier.weight(1f),
        ) {
            Text("Usar demonstração")
        }
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier.weight(1f),
        ) {
            Text("Atualizar")
        }
    }
}

@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(3.dp),
    ) {
        ArtworkImage(
            uri = album.artworkUri,
            seed = album.title + album.artist,
            contentDescription = "Capa de ${album.title}",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            corner = 20,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${album.artist}  •  ${album.tracks.size} faixas",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ArtistCard(artist: Artist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtworkImage(
            uri = artist.artworkUri,
            seed = artist.name,
            contentDescription = "Foto de ${artist.name}",
            modifier = Modifier
                .size(132.dp)
                .clip(CircleShape),
            corner = 66,
        )
        Spacer(Modifier.height(9.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${artist.trackCount} músicas",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumSheet(
    album: Album,
    player: PlayerUiState,
    onDismiss: () -> Unit,
    onPlayAlbum: () -> Unit,
    onShuffleAlbum: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onDownload: (Track) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetHeader(
            artworkUri = album.artworkUri,
            seed = album.title + album.artist,
            title = album.title,
            subtitle = album.artist,
            detail = "${album.tracks.size} faixas  •  ${formatTotalDuration(album.durationMs)}",
            onPlay = onPlayAlbum,
            onShuffle = onShuffleAlbum,
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
        ) {
            items(album.tracks, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    isCurrent = player.current?.id == track.id,
                    isPlaying = player.snapshot.isPlaying,
                    actions = TrackActions(
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) },
                        onDownload = if (track.source.isRemote && !track.isDownloaded) {
                            { onDownload(track) }
                        } else {
                            null
                        },
                    ),
                    onClick = { onPlayTrack(track) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistSheet(
    artist: Artist,
    player: PlayerUiState,
    onDismiss: () -> Unit,
    onPlayArtist: () -> Unit,
    onShuffleArtist: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onDownload: (Track) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetHeader(
            artworkUri = artist.artworkUri,
            seed = artist.name,
            title = artist.name,
            subtitle = "Artista",
            detail = "${artist.trackCount} músicas",
            onPlay = onPlayArtist,
            onShuffle = onShuffleArtist,
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
        ) {
            items(artist.tracks, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    isCurrent = player.current?.id == track.id,
                    isPlaying = player.snapshot.isPlaying,
                    actions = TrackActions(
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) },
                        onDownload = if (track.source.isRemote && !track.isDownloaded) {
                            { onDownload(track) }
                        } else {
                            null
                        },
                    ),
                    onClick = { onPlayTrack(track) },
                )
            }
        }
    }
}

@Composable
private fun SheetHeader(
    artworkUri: android.net.Uri?,
    seed: String,
    title: String,
    subtitle: String,
    detail: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArtworkImage(
                uri = artworkUri,
                seed = seed,
                contentDescription = "Capa de $title",
                modifier = Modifier.size(76.dp),
                corner = 18,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onPlay,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Tocar")
            }
            OutlinedButton(
                onClick = onShuffle,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Aleatório")
            }
        }
        Spacer(Modifier.height(8.dp))
        if (artworkUri == null) {
            InfoBanner(
                text = "Sem capa embutida nas tags. O Auris gera uma capa a partir do nome do álbum.",
            )
        }
    }
}
