package com.auris.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auris.app.AurisApp
import com.auris.app.data.AlbumPaletteExtractor
import com.auris.app.data.DownloadEntry
import com.auris.app.data.Lyrics
import com.auris.app.model.Album
import com.auris.app.model.AlbumPalette
import com.auris.app.model.AppSettings
import com.auris.app.model.Artist
import com.auris.app.model.AudioQuality
import com.auris.app.model.Track
import com.auris.app.model.TrackSource
import com.auris.app.playback.PlayerSnapshot
import com.auris.app.playback.ResolvedTrack
import com.auris.app.sources.SourcePlugin
import com.auris.app.util.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryState(
    val loading: Boolean = true,
    val hasPermission: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val recent: List<Track> = emptyList(),
    val hasDemo: Boolean = false,
) {
    val isEmpty: Boolean get() = !loading && tracks.isEmpty()
}

data class SearchState(
    val query: String = "",
    val local: List<Track> = emptyList(),
    val remote: List<Track> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

data class PlayerUiState(
    val snapshot: PlayerSnapshot = PlayerSnapshot(),
    val current: Track? = null,
    val queue: List<Track> = emptyList(),
    val lyrics: Lyrics? = null,
    val palette: AlbumPalette? = null,
    val infiniteMix: Boolean = false,
)

data class BridgeStatus(
    val checking: Boolean = false,
    val reachable: Boolean? = null,
    val message: String? = null,
)

class AurisViewModel(private val app: AurisApp) : ViewModel() {

    private val container = app.container

    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val downloads: StateFlow<List<DownloadEntry>> = container.downloads.entries
    val plugins: StateFlow<List<SourcePlugin>> = container.plugins.plugins

    private val _library = MutableStateFlow(LibraryState())
    val library: StateFlow<LibraryState> = _library.asStateFlow()

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()

    private val _queueTracks = MutableStateFlow<List<Track>>(emptyList())
    private val _lyrics = MutableStateFlow<Lyrics?>(null)
    private val _palette = MutableStateFlow<AlbumPalette?>(null)
    private val _infiniteMix = MutableStateFlow(false)

    val player: StateFlow<PlayerUiState> = combine(
        container.player.snapshot,
        _queueTracks,
        _lyrics,
        _palette,
        _infiniteMix,
    ) { snapshot, queue, lyrics, palette, infinite ->
        val current = snapshot.currentMediaId?.let { id -> queue.firstOrNull { it.id == id } }
            ?: queue.getOrNull(snapshot.index)
        PlayerUiState(
            snapshot = snapshot,
            current = current,
            queue = queue,
            lyrics = lyrics,
            palette = palette,
            infiniteMix = infinite,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerUiState())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private val _bridgeStatus = MutableStateFlow(BridgeStatus())
    val bridgeStatus: StateFlow<BridgeStatus> = _bridgeStatus.asStateFlow()

    private var searchJob: Job? = null
    private var lyricsJob: Job? = null
    private var paletteJob: Job? = null
    private var appendingMix = false

    init {
        container.player.connect()
        viewModelScope.launch { refreshLibraryInternal() }
        observePlayer()
        observeDownloads()
        viewModelScope.launch {
            settings.map { it.scrobbleEnabled }.distinctUntilChanged().collect { enabled ->
                if (enabled) container.lastFm.flushPending(settings.value)
            }
        }
    }

    fun refreshLibrary() {
        viewModelScope.launch { refreshLibraryInternal() }
    }

    private suspend fun refreshLibraryInternal() {
        _library.update { it.copy(loading = true) }
        val permission = container.localMusic.hasPermission()
        val local = if (permission) container.localMusic.loadTracks() else emptyList()
        val demo = container.demo.load()
        val downloaded = container.downloads.completedTracks()

        val merged = LinkedHashMap<String, Track>()
        (local + demo + downloaded).forEach { merged[it.id] = it }
        val tracks = merged.values.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { it.title },
        )
        val recent = container.recent.load().mapNotNull { id ->
            tracks.firstOrNull { it.id == id }
        }
        _library.value = LibraryState(
            loading = false,
            hasPermission = permission,
            tracks = tracks,
            albums = buildAlbums(tracks),
            artists = buildArtists(tracks),
            recent = recent,
            hasDemo = container.demo.isGenerated(),
        )
    }

    private fun buildAlbums(tracks: List<Track>): List<Album> =
        tracks.groupBy { "${it.displayAlbum}\u0000${it.displayArtist}" }
            .map { (key, list) ->
                val ordered = list.sortedWith(
                    compareBy<Track> { it.trackNumber.takeIf { number -> number > 0 } ?: 999 }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
                )
                Album(
                    key = key,
                    title = ordered.first().displayAlbum,
                    artist = ordered.first().displayArtist,
                    artworkUri = ordered.firstNotNullOfOrNull { it.artworkUri },
                    tracks = ordered,
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

    private fun buildArtists(tracks: List<Track>): List<Artist> =
        tracks.groupBy { it.displayArtist }
            .map { (name, list) ->
                Artist(
                    key = name.lowercase(),
                    name = name,
                    trackCount = list.size,
                    artworkUri = list.firstNotNullOfOrNull { it.artworkUri },
                    tracks = list,
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    private fun observePlayer() {
        viewModelScope.launch {
            var lastTrackId: String? = null
            var scrobbledTrackId: String? = null
            var lastError: String? = null
            var lastPresenceKey: String? = null

            player.collect { ui ->
                val track = ui.current
                val snapshot = ui.snapshot

                if (track == null && lastTrackId != null) {
                    lastTrackId = null
                    _lyrics.value = null
                    _palette.value = null
                }

                if (track != null && track.id != lastTrackId) {
                    lastTrackId = track.id
                    scrobbledTrackId = null
                    onTrackStarted(track)
                }

                val error = snapshot.error
                if (error != null && error != lastError) {
                    lastError = error
                    _messages.tryEmit(error)
                    container.player.clearError()
                }

                if (track != null && snapshot.isPlaying && scrobbledTrackId != track.id) {
                    val threshold = (track.durationMs / 2).coerceIn(30_000L, 240_000L)
                    if (track.durationMs > 30_000L && snapshot.positionMs >= threshold) {
                        scrobbledTrackId = track.id
                        val currentSettings = settings.value
                        if (container.lastFm.isConfigured(currentSettings)) {
                            viewModelScope.launch {
                                container.lastFm.scrobble(
                                    track = track,
                                    timestampSeconds = System.currentTimeMillis() / 1000L,
                                    settings = currentSettings,
                                )
                            }
                        }
                    }
                }

                val currentSettings = settings.value
                if (currentSettings.discordEnabled && currentSettings.discordBridgeUrl.isNotBlank()) {
                    val key = "${track?.id}:${snapshot.isPlaying}"
                    if (key != lastPresenceKey) {
                        lastPresenceKey = key
                        viewModelScope.launch {
                            container.discord.update(
                                bridgeUrl = currentSettings.discordBridgeUrl,
                                track = track,
                                isPlaying = snapshot.isPlaying,
                                positionMs = snapshot.positionMs,
                                durationMs = snapshot.durationMs,
                            )
                        }
                    }
                }

                if (ui.infiniteMix && snapshot.queueSize > 0 &&
                    snapshot.queueSize - snapshot.index <= 3
                ) {
                    ensureInfiniteTail()
                }
            }
        }
    }

    private fun onTrackStarted(track: Track) {
        container.recent.add(track.id)
        refreshRecent()
        loadLyrics(track)
        loadPalette(track)
        val currentSettings = settings.value
        if (container.lastFm.isConfigured(currentSettings)) {
            viewModelScope.launch { container.lastFm.nowPlaying(track, currentSettings) }
        }
        maybeSmartDownload()
    }

    private fun refreshRecent() {
        _library.update { state ->
            state.copy(
                recent = container.recent.load().mapNotNull { id ->
                    state.tracks.firstOrNull { it.id == id }
                },
            )
        }
    }

    private fun loadLyrics(track: Track) {
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { container.lyrics.load(track.id) }
            if (player.value.current?.id == track.id) _lyrics.value = loaded
        }
    }

    private fun loadPalette(track: Track) {
        paletteJob?.cancel()
        val fallback = AlbumPaletteExtractor.fallback(track.title + track.artist)
        _palette.value = fallback
        paletteJob = viewModelScope.launch {
            val artwork = container.artwork.artworkFor(track)
            val extracted = artwork?.let { AlbumPaletteExtractor.extract(app, it) }
            if (player.value.current?.id == track.id) {
                _palette.value = extracted ?: fallback
            }
        }
    }

    // ---------------------------------------------------------------- playback

    fun playTrack(track: Track, queue: List<Track>? = null, shuffle: Boolean = false) {
        viewModelScope.launch {
            val sourceQueue = queue ?: library.value.tracks
            val resolved = resolveQueue(sourceQueue)
            if (resolved.isEmpty()) {
                _messages.tryEmit("Não foi possível reproduzir. Verifique a conexão ou baixe a faixa.")
                return@launch
            }
            val startIndex = resolved.indexOfFirst { it.track.id == track.id }
                .takeIf { it >= 0 }
                ?: 0
            _queueTracks.value = resolved.map { it.track }
            _infiniteMix.value = false
            container.player.play(resolved, startIndex, shuffle)
        }
    }

    fun playAlbum(album: Album, shuffle: Boolean = false) {
        album.tracks.firstOrNull()?.let { playTrack(it, album.tracks, shuffle) }
    }

    fun playArtist(artist: Artist, shuffle: Boolean = false) {
        artist.tracks.firstOrNull()?.let { playTrack(it, artist.tracks, shuffle) }
    }

    fun shuffleAll() {
        val tracks = library.value.tracks
        if (tracks.isEmpty()) {
            _messages.tryEmit("Sua biblioteca está vazia.")
            return
        }
        playTrack(tracks.random(), tracks, shuffle = true)
    }

    fun playNext(track: Track) {
        viewModelScope.launch {
            val resolved = resolveTrack(track)?.let { ResolvedTrack(track, it) }
            if (resolved == null) {
                _messages.tryEmit("Esta faixa não está disponível agora.")
                return@launch
            }
            container.player.addToQueue(listOf(resolved), playNext = true)
            val insertAt = (player.value.snapshot.index + 1).coerceIn(0, _queueTracks.value.size)
            _queueTracks.update { current ->
                current.toMutableList().apply { add(insertAt, track) }
            }
            _messages.tryEmit("“${track.title}” toca em seguida")
        }
    }

    fun addToQueue(track: Track) {
        viewModelScope.launch {
            val resolved = resolveTrack(track)?.let { ResolvedTrack(track, it) }
            if (resolved == null) {
                _messages.tryEmit("Esta faixa não está disponível agora.")
                return@launch
            }
            container.player.addToQueue(listOf(resolved), playNext = false)
            _queueTracks.update { it + track }
            _messages.tryEmit("“${track.title}” adicionada à fila")
        }
    }

    fun removeFromQueue(index: Int) {
        container.player.removeAt(index)
        _queueTracks.update { queue ->
            if (index in queue.indices) {
                queue.toMutableList().apply { removeAt(index) }
            } else {
                queue
            }
        }
    }

    fun seekToQueueItem(index: Int) {
        container.player.seekToQueueItem(index)
    }

    fun togglePlayPause() = container.player.togglePlayPause()
    fun next() = container.player.next()
    fun previous() = container.player.previous()
    fun seekTo(positionMs: Long) = container.player.seekTo(positionMs)
    fun seekToFraction(fraction: Float) = container.player.seekToFraction(fraction)
    fun toggleShuffle() = container.player.toggleShuffle()
    fun cycleRepeat() = container.player.cycleRepeat()
    fun stop() = container.player.stop()

    fun startInfiniteMix(seed: Track? = null) {
        viewModelScope.launch {
            val mix = buildMixQueue(seed ?: player.value.current ?: library.value.tracks.randomOrNull(), 30)
            if (mix.isEmpty()) {
                _messages.tryEmit("Adicione algumas músicas para criar seu mix.")
                return@launch
            }
            val resolved = resolveQueue(mix)
            if (resolved.isEmpty()) {
                _messages.tryEmit("Não foi possível iniciar o mix agora.")
                return@launch
            }
            val start = seed?.let { selected ->
                resolved.indexOfFirst { it.track.id == selected.id }.takeIf { it >= 0 }
            } ?: 0
            _queueTracks.value = resolved.map { it.track }
            _infiniteMix.value = true
            container.player.play(resolved, start, shuffle = false)
            _messages.tryEmit("Mix pessoal infinito iniciado")
        }
    }

    fun stopInfiniteMix() {
        _infiniteMix.value = false
    }

    fun toggleInfiniteMix() {
        if (_infiniteMix.value) stopInfiniteMix() else startInfiniteMix()
    }

    private fun buildMixQueue(seed: Track?, count: Int): List<Track> {
        val pool = library.value.tracks
        if (pool.isEmpty()) return emptyList()
        val chosen = seed?.let { selected -> pool.firstOrNull { it.id == selected.id } }
            ?: pool.random()
        val similar = pool
            .filter {
                it.id != chosen.id &&
                    (it.artist.equals(chosen.artist, ignoreCase = true) ||
                        it.album.equals(chosen.album, ignoreCase = true))
            }
            .shuffled()
            .take(10)
        val rest = pool
            .filter { candidate -> candidate.id != chosen.id && similar.none { it.id == candidate.id } }
            .shuffled()
        return (listOf(chosen) + similar + rest).distinctBy { it.id }.take(count)
    }

    private fun ensureInfiniteTail() {
        if (appendingMix) return
        appendingMix = true
        viewModelScope.launch {
            try {
                val existingIds = _queueTracks.value.map { it.id }.toSet()
                val fresh = library.value.tracks
                    .filter { it.id !in existingIds }
                    .shuffled()
                    .take(8)
                val picks = fresh.ifEmpty { library.value.tracks.shuffled().take(5) }
                val resolved = resolveQueue(picks)
                if (resolved.isNotEmpty()) {
                    container.player.addToQueue(resolved, playNext = false)
                    _queueTracks.update { it + resolved.map { item -> item.track } }
                }
            } finally {
                appendingMix = false
            }
        }
    }

    private suspend fun resolveQueue(tracks: List<Track>): List<ResolvedTrack> = coroutineScope {
        tracks.map { track ->
            async {
                runCatching { resolveTrack(track) }.getOrNull()
                    ?.let { uri -> ResolvedTrack(track, uri) }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun resolveTrack(track: Track): Uri? {
        if (track.source == TrackSource.LOCAL || track.source == TrackSource.DEMO) {
            return track.uri.takeIf { it != Uri.EMPTY }
        }

        val downloaded = container.downloads.localUri(track.id)
        val currentSettings = settings.value
        val online = container.isOnline()

        if (currentSettings.streamWhenOnline && online) {
            val quality = effectiveStreamQuality(currentSettings)
            val stream = when (track.source) {
                TrackSource.YOUTUBE_MUSIC -> {
                    if (currentSettings.ytMusicUrl.isBlank()) null
                    else container.youTubeMusic.resolve(
                        baseUrl = currentSettings.ytMusicUrl,
                        token = currentSettings.ytMusicToken,
                        track = track,
                        quality = quality,
                    )
                }

                TrackSource.PLUGIN -> container.plugins.resolve(track, quality)
                else -> null
            }
            if (!stream.isNullOrBlank()) return Uri.parse(stream)
        }

        downloaded?.let { return it }
        if (track.uri != Uri.EMPTY) return track.uri
        return null
    }

    private fun effectiveStreamQuality(currentSettings: AppSettings): AudioQuality =
        if (currentSettings.losslessFirst &&
            currentSettings.preferredQuality.rank < AudioQuality.LOSSLESS.rank
        ) {
            AudioQuality.LOSSLESS
        } else {
            currentSettings.preferredQuality
        }

    // ---------------------------------------------------------------- downloads

    private fun observeDownloads() {
        viewModelScope.launch {
            downloads
                .map { entries ->
                    entries.filter { it.status == com.auris.app.data.DownloadStatus.COMPLETED }
                        .map { it.track.id }
                        .toSet()
                }
                .distinctUntilChanged()
                .collect { refreshLibraryInternal() }
        }
    }

    fun downloadTrack(track: Track) {
        if (track.source == TrackSource.LOCAL || track.source == TrackSource.DEMO) {
            _messages.tryEmit("Esta faixa já está no aparelho.")
            return
        }
        if (container.downloads.isDownloaded(track.id)) {
            _messages.tryEmit("Esta faixa já foi baixada.")
            return
        }
        val currentSettings = settings.value
        if (currentSettings.wifiOnlyDownloads && !container.isUnmetered()) {
            _messages.tryEmit("Downloads estão configurados apenas no Wi-Fi.")
            return
        }
        if (!container.isOnline()) {
            _messages.tryEmit("Sem conexão para baixar esta faixa.")
            return
        }
        viewModelScope.launch {
            val quality = currentSettings.downloadQuality
            val url = resolveDownloadUrl(track, quality)
            if (url == null) {
                _messages.tryEmit("A fonte não liberou um link para download.")
                return@launch
            }
            container.downloads.enqueue(track, url, quality.title)
            _messages.tryEmit("Baixando “${track.title}” em ${quality.title}")
        }
    }

    private fun maybeSmartDownload() {
        val currentSettings = settings.value
        if (!currentSettings.smartDownloads) return
        if (!container.isOnline()) return
        if (currentSettings.wifiOnlyDownloads && !container.isUnmetered()) return

        val ui = player.value
        val candidates = ui.queue
            .drop(ui.snapshot.index)
            .take(3)
            .filter { it.source.isRemote }
            .filter { !container.downloads.isDownloaded(it.id) }
            .filter { track ->
                container.downloads.entries.value.none {
                    it.track.id == track.id &&
                        it.status in setOf(
                            com.auris.app.data.DownloadStatus.QUEUED,
                            com.auris.app.data.DownloadStatus.DOWNLOADING,
                        )
                }
            }

        if (candidates.isEmpty()) return
        viewModelScope.launch {
            candidates.forEach { track ->
                val url = resolveDownloadUrl(track, currentSettings.downloadQuality) ?: return@forEach
                container.downloads.enqueue(track, url, currentSettings.downloadQuality.title)
            }
        }
    }

    private suspend fun resolveDownloadUrl(track: Track, quality: AudioQuality): String? {
        if (track.uri.scheme == "http" || track.uri.scheme == "https") return track.uri.toString()
        val currentSettings = settings.value
        return when (track.source) {
            TrackSource.YOUTUBE_MUSIC -> {
                if (currentSettings.ytMusicUrl.isBlank()) null
                else container.youTubeMusic.resolve(
                    baseUrl = currentSettings.ytMusicUrl,
                    token = currentSettings.ytMusicToken,
                    track = track,
                    quality = quality,
                )
            }

            TrackSource.PLUGIN -> container.plugins.resolve(track, quality)
            else -> null
        }
    }

    fun retryDownload(trackId: String) {
        val entry = downloads.value.firstOrNull { it.track.id == trackId } ?: return
        if (entry.remoteUrl.isNullOrBlank()) {
            downloadTrack(entry.track)
        } else {
            container.downloads.retry(trackId)
        }
    }

    fun deleteDownload(trackId: String) = container.downloads.delete(trackId)
    fun deleteAllDownloads() = container.downloads.deleteAll()

    // ----------------------------------------------------------------- metadata

    fun importLyrics(uri: Uri) {
        val track = player.value.current
        if (track == null) {
            _messages.tryEmit("Reproduza uma música antes de importar a letra.")
            return
        }
        viewModelScope.launch {
            val imported = container.lyrics.import(track.id, uri)
            if (imported) {
                _lyrics.value = withContext(Dispatchers.IO) { container.lyrics.load(track.id) }
                _messages.tryEmit("Letra sincronizada importada")
            } else {
                _messages.tryEmit("Não foi possível ler esse arquivo .lrc")
            }
        }
    }

    fun removeLyrics() {
        val track = player.value.current ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.lyrics.delete(track.id) }
            _lyrics.value = null
        }
    }

    fun importPlugin(uri: Uri) {
        viewModelScope.launch {
            val imported = container.plugins.import(uri)
            if (imported) {
                _messages.tryEmit("Complemento instalado")
            } else {
                _messages.tryEmit("Manifesto inválido. Verifique o JSON do complemento.")
            }
        }
    }

    fun removePlugin(plugin: SourcePlugin) {
        container.plugins.remove(plugin)
        _messages.tryEmit("Complemento removido")
    }

    fun testYouTubeMusic() {
        val currentSettings = settings.value
        if (currentSettings.ytMusicUrl.isBlank()) {
            _bridgeStatus.value = BridgeStatus(
                reachable = false,
                message = "Informe a URL da sua ponte do YouTube Music.",
            )
            return
        }
        viewModelScope.launch {
            _bridgeStatus.value = BridgeStatus(checking = true)
            val ok = container.youTubeMusic.ping(
                currentSettings.ytMusicUrl,
                currentSettings.ytMusicToken,
            )
            _bridgeStatus.value = BridgeStatus(
                checking = false,
                reachable = ok,
                message = if (ok) {
                    "Conexão com a ponte estabelecida."
                } else {
                    "Não foi possível conectar. Confira a URL, o token e a rede."
                },
            )
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { container.settings.update(transform) }
    }

    fun testLastFmSession() {
        val currentSettings = settings.value
        if (currentSettings.lastFmApiKey.isBlank() ||
            currentSettings.lastFmApiSecret.isBlank() ||
            currentSettings.lastFmSessionKey.isBlank()
        ) {
            _messages.tryEmit("Preencha a API key, o secret e a session key do Last.fm.")
            return
        }
        viewModelScope.launch {
            _messages.tryEmit("Validando sessão do Last.fm…")
            val user = container.lastFm.testSession(currentSettings)
            _messages.tryEmit(
                if (user != null) {
                    "Last.fm conectado como $user"
                } else {
                    "Não foi possível validar a sessão do Last.fm."
                },
            )
        }
    }

    // ------------------------------------------------------------------- search

    fun search(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        val localMatches = if (trimmed.isBlank()) {
            emptyList()
        } else {
            library.value.tracks
                .filter { track ->
                    track.title.contains(trimmed, ignoreCase = true) ||
                        track.displayArtist.contains(trimmed, ignoreCase = true) ||
                        track.displayAlbum.contains(trimmed, ignoreCase = true)
                }
                .take(60)
        }
        _search.value = SearchState(
            query = query,
            local = localMatches,
            remote = if (trimmed.length < 2) emptyList() else _search.value.remote,
            loading = false,
        )
        if (trimmed.length < 2) return

        val currentSettings = settings.value
        val remoteEnabled = currentSettings.ytMusicUrl.isNotBlank() ||
            container.plugins.plugins.value.isNotEmpty()
        if (!remoteEnabled) return

        searchJob = viewModelScope.launch {
            delay(320L)
            _search.update { it.copy(loading = true, error = null) }
            val remote = coroutineScope {
                val yt = if (currentSettings.ytMusicUrl.isNotBlank()) {
                    async {
                        runCatching {
                            container.youTubeMusic.search(
                                currentSettings.ytMusicUrl,
                                currentSettings.ytMusicToken,
                                trimmed,
                            )
                        }.getOrDefault(emptyList())
                    }
                } else {
                    null
                }
                val pluginsJob = if (container.plugins.plugins.value.isNotEmpty()) {
                    async {
                        runCatching { container.plugins.search(trimmed) }
                            .getOrDefault(emptyList())
                    }
                } else {
                    null
                }
                listOfNotNull(yt, pluginsJob).awaitAll().flatten()
            }
            if (_search.value.query.trim() != trimmed) return@launch
            _search.update {
                it.copy(
                    remote = remote.take(50),
                    loading = false,
                    error = null,
                )
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _search.value = SearchState()
    }

    // --------------------------------------------------------------------- demo

    fun generateDemo() {
        viewModelScope.launch {
            _library.update { it.copy(loading = true) }
            container.demo.generate(container.lyrics)
            refreshLibraryInternal()
            _messages.tryEmit("Biblioteca de demonstração criada em lossless")
        }
    }

    fun removeDemo() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.demo.remove() }
            refreshLibraryInternal()
            _messages.tryEmit("Conteúdo de demonstração removido")
        }
    }

    // -------------------------------------------------------------------- misc

    fun notify(message: String) {
        _messages.tryEmit(message)
    }

    fun formatQueueSubtitle(): String = buildString {
        val current = player.value
        append("${current.queue.size} faixas")
        if (current.snapshot.durationMs > 0) {
            append(" • ")
            append(formatDuration(current.snapshot.durationMs))
        }
    }

    override fun onCleared() {
        container.player.disconnect()
        super.onCleared()
    }

    companion object {
        fun factory(app: AurisApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { AurisViewModel(app) }
        }
    }
}
