package com.gecko.app.sources

import com.gecko.app.model.AudioQuality
import com.gecko.app.model.Track
import com.gecko.app.model.TrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Cliente da API pública do YouTube Music (InnerTube).
 *
 * Usa os endpoints `search`, `browse`, `player`, `next`, `music/get_queue` e
 * `music/get_search_suggestions`. A chave e a versão do cliente são descobertas
 * na página inicial do YouTube Music e renovadas periodicamente; se a descoberta
 * falhar, valores de fallback mantêm a busca funcionando até a próxima tentativa.
 *
 * O app não faz decipher de assinatura, não usa PO token e não contorna DRM.
 * Quando o endpoint `player` devolve uma URL de áudio utilizável, ela é usada;
 * caso contrário, a reprodução pode ser feita por uma ponte configurada.
 */
class YouTubeMusicApi(private val client: OkHttpClient) {

    enum class BrowseKind(val label: String) {
        ALBUM("Álbum"),
        ARTIST("Artista"),
        PLAYLIST("Playlist"),
        OTHER("Coleção"),
    }

    data class BrowseItem(
        val title: String,
        val subtitle: String,
        val browseId: String,
        val params: String?,
        val kind: BrowseKind,
        val pageType: String,
        val artworkUrl: String?,
    )

    /**
     * Página de um álbum, artista ou playlist decomposta em faixas e itens
     * relacionados. [items] permite continuar navegando por álbuns, artistas e
     * playlists encontrados nessa página.
     */
    data class BrowsePage(
        val title: String?,
        val subtitle: String?,
        val tracks: List<Track>,
        val items: List<BrowseItem>,
    )

    data class SearchResult(
        val tracks: List<Track>,
        val browseItems: List<BrowseItem>,
    )

    private val apiClient = client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private val configLock = Mutex()

    @Volatile
    private var config: ApiConfig? = null

    suspend fun search(baseUrl: String, query: String): SearchResult {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return SearchResult(emptyList(), emptyList())
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(30_000L) {
                val root = post(
                    baseUrl = baseUrl,
                    endpoint = "search",
                    extra = JSONObject().put("query", trimmed),
                ) ?: return@withTimeoutOrNull SearchResult(emptyList(), emptyList())
                parseSearchResult(root)
            } ?: SearchResult(emptyList(), emptyList())
        }
    }

    suspend fun suggestions(baseUrl: String, input: String): List<String> {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(20_000L) {
                val root = post(
                    baseUrl = baseUrl,
                    endpoint = "music/get_search_suggestions",
                    extra = JSONObject().put("input", trimmed),
                ) ?: return@withTimeoutOrNull emptyList()
                collectObjects(root, "searchSuggestionRenderer")
                    .mapNotNull { item ->
                        runText(textRuns(item.optJSONObject("suggestion")))
                            .trim()
                            .takeIf { it.isNotBlank() }
                    }
                    .distinct()
            } ?: emptyList()
        }
    }

    suspend fun browse(
        baseUrl: String,
        browseId: String,
        params: String? = null,
    ): BrowsePage? {
        if (browseId.isBlank()) return null
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(30_000L) {
                val extra = JSONObject().put("browseId", browseId)
                if (!params.isNullOrBlank()) extra.put("params", params)
                val root = post(baseUrl, "browse", extra) ?: return@withTimeoutOrNull null
                parseBrowsePage(root)
            }
        }
    }

    /**
     * Rádio/mix a partir de uma faixa. O YouTube Music usa `RDAMVM{videoId}`
     * como playlist de rádio quando nenhum playlistId é informado.
     */
    suspend fun next(
        baseUrl: String,
        videoId: String,
        playlistId: String? = null,
    ): List<Track> {
        if (videoId.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(30_000L) {
                val extra = JSONObject().apply {
                    put("videoId", videoId)
                    put("playlistId", playlistId?.takeIf { it.isNotBlank() } ?: "RDAMVM$videoId")
                    put("isAudioOnly", true)
                }
                val root = post(baseUrl, "next", extra) ?: return@withTimeoutOrNull emptyList()
                collectObjects(root, "playlistPanelVideoRenderer")
                    .mapNotNull(::parsePlaylistPanelItem)
                    .distinctBy { it.remoteId }
            } ?: emptyList()
        }
    }

    suspend fun queue(
        baseUrl: String,
        videoIds: List<String>,
        playlistId: String? = null,
    ): List<Track> {
        val ids = videoIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(30_000L) {
                val extra = JSONObject().apply {
                    put("videoIds", JSONArray().apply { ids.forEach(::put) })
                    if (!playlistId.isNullOrBlank()) put("playlistId", playlistId)
                }
                val root = post(baseUrl, "music/get_queue", extra) ?: return@withTimeoutOrNull emptyList()
                collectObjects(root, "playlistPanelVideoRenderer")
                    .mapNotNull(::parsePlaylistPanelItem)
                    .distinctBy { it.remoteId }
            } ?: emptyList()
        }
    }

    /**
     * Consulta o endpoint `player`. Devolve metadados mesmo quando o vídeo está
     * indisponível; [YouTubePlayback.streamUrl] fica nulo se o YouTube não
     * entregar uma URL de áudio direta (situação comum sem login/PO token).
     */
    suspend fun playback(
        baseUrl: String,
        videoId: String,
        quality: AudioQuality = AudioQuality.HIGH,
    ): YouTubePlayback? {
        if (videoId.isBlank()) return null
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(30_000L) {
                val extra = JSONObject().apply {
                    put("videoId", videoId)
                    put("contentCheckOk", true)
                    put("racyCheckOk", true)
                    put(
                        "playbackContext",
                        JSONObject().put(
                            "contentPlaybackContext",
                            JSONObject().put("html5Preference", "HTML5_PREF_WANTS"),
                        ),
                    )
                }
                val root = post(baseUrl, "player", extra) ?: return@withTimeoutOrNull null
                val details = root.optJSONObject("videoDetails")
                val status = root.optJSONObject("playabilityStatus")?.optString("status")
                val streaming = root.optJSONObject("streamingData")
                YouTubePlayback(
                    title = details?.optString("title").orEmpty(),
                    artist = details?.optString("author").orEmpty(),
                    durationMs = (details?.optString("lengthSeconds")?.toLongOrNull() ?: 0L) * 1000L,
                    artworkUrl = details?.optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")
                        ?.let(::bestThumbnail),
                    streamUrl = streaming?.let { bestAudioUrl(it, quality) },
                    status = status,
                )
            }
        }
    }

    suspend fun streamUrl(
        baseUrl: String,
        videoId: String,
        quality: AudioQuality,
    ): String? = playback(baseUrl, videoId, quality)?.streamUrl

    suspend fun ping(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        withTimeoutOrNull(20_000L) {
            val root = post(
                baseUrl = baseUrl,
                endpoint = "search",
                extra = JSONObject().put("query", "música"),
            )
            root != null
        } ?: false
    }

    // --------------------------------------------------------------- transport

    private suspend fun post(
        baseUrl: String,
        endpoint: String,
        extra: JSONObject,
    ): JSONObject? {
        val current = config()
        val payload = JSONObject().apply {
            put("context", contextJson(current))
            val keys = extra.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, extra.opt(key))
            }
        }
        return execute(endpointUrl(baseUrl, endpoint, current.key), payload, current)
    }

    private fun execute(url: String, payload: JSONObject, cfg: ApiConfig): JSONObject? {
        val builder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .header("X-YouTube-Client-Name", CLIENT_NAME_ID)
            .header("X-YouTube-Client-Version", cfg.version)
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("User-Agent", USER_AGENT)
        if (cfg.visitorData.isNotBlank()) {
            builder.header("X-Goog-Visitor-Id", cfg.visitorData)
        }
        return runCatching {
            apiClient.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    null
                } else {
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) null else JSONObject(body)
                }
            }
        }.getOrNull()
    }

    private suspend fun config(): ApiConfig {
        val cached = config
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < CONFIG_TTL_MS) {
            return cached
        }
        return configLock.withLock {
            val latest = config
            if (latest != null && System.currentTimeMillis() - latest.fetchedAt < CONFIG_TTL_MS) {
                latest
            } else {
                discoverConfig().also { config = it }
            }
        }
    }

    private suspend fun discoverConfig(): ApiConfig = withContext(Dispatchers.IO) {
        val fallback = ApiConfig(
            key = FALLBACK_API_KEY,
            version = FALLBACK_CLIENT_VERSION,
            visitorData = "",
            fetchedAt = System.currentTimeMillis(),
        )
        val html = runCatching {
            val request = Request.Builder()
                .url(HOME_URL)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
                .build()
            apiClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string().orEmpty() else ""
            }
        }.getOrDefault("")
        if (html.isBlank()) return@withContext fallback

        val key = API_KEY_PATTERN.find(html)?.groupValues?.getOrNull(1)
        val version = CLIENT_VERSION_PATTERN.find(html)?.groupValues?.getOrNull(1)
        val visitor = VISITOR_DATA_PATTERN.find(html)?.groupValues?.getOrNull(1)
        val decodedVisitor = visitor
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
            .orEmpty()

        ApiConfig(
            key = key?.takeIf { it.isNotBlank() } ?: FALLBACK_API_KEY,
            version = version?.takeIf { it.isNotBlank() } ?: FALLBACK_CLIENT_VERSION,
            visitorData = decodedVisitor,
            fetchedAt = System.currentTimeMillis(),
        )
    }

    private fun endpointUrl(baseUrl: String, endpoint: String, apiKey: String): String {
        val base = baseUrl.trim().trimEnd('/')
        val root = when {
            base.isBlank() -> DEFAULT_API_ROOT
            base.contains("/youtubei/v1", ignoreCase = true) ->
                base.substringBefore("/youtubei/v1") + "/youtubei/v1"
            else -> "$base/youtubei/v1"
        }
        return "$root/${endpoint.trimStart('/')}?key=$apiKey&prettyPrint=false"
    }

    private fun contextJson(cfg: ApiConfig): JSONObject = JSONObject().apply {
        put(
            "client",
            JSONObject().apply {
                put("clientName", "WEB_REMIX")
                put("clientVersion", cfg.version)
                put("hl", "pt-BR")
                put("gl", "BR")
                if (cfg.visitorData.isNotBlank()) put("visitorData", cfg.visitorData)
            },
        )
    }

    // ----------------------------------------------------------------- parsers

    private fun parseTracks(root: JSONObject): List<Track> {
        val responsive = collectObjects(root, "musicResponsiveListItemRenderer")
            .mapNotNull(::parseResponsiveItem)
        val panel = collectObjects(root, "playlistPanelVideoRenderer")
            .mapNotNull(::parsePlaylistPanelItem)
        return (responsive + panel).distinctBy { it.remoteId }
    }

    private fun parseSearchResult(root: JSONObject): SearchResult {
        val responsiveItems = collectObjects(root, "musicResponsiveListItemRenderer")
        val tracks = responsiveItems.mapNotNull(::parseResponsiveItem)
        val panelTracks = collectObjects(root, "playlistPanelVideoRenderer")
            .mapNotNull(::parsePlaylistPanelItem)
        val browseItems = responsiveItems.mapNotNull(::parseResponsiveBrowseItem) +
            collectObjects(root, "musicTwoRowItemRenderer").mapNotNull(::parseTwoRowBrowseItem)

        return SearchResult(
            tracks = (tracks + panelTracks).distinctBy { it.remoteId },
            browseItems = browseItems
                .distinctBy { it.browseId }
                .take(40),
        )
    }

    private fun parseBrowsePage(root: JSONObject): BrowsePage {
        val title = browseTitle(root)
        val tracks = parseTracks(root)
        val items = (collectObjects(root, "musicResponsiveListItemRenderer")
            .mapNotNull(::parseResponsiveBrowseItem) +
            collectObjects(root, "musicTwoRowItemRenderer").mapNotNull(::parseTwoRowBrowseItem))
            .filter { it.browseId.isNotBlank() }
            .distinctBy { it.browseId }
        return BrowsePage(
            title = title,
            subtitle = browseSubtitle(root),
            tracks = tracks.distinctBy { it.remoteId },
            items = items.take(60),
        )
    }

    private fun parseResponsiveBrowseItem(item: JSONObject): BrowseItem? {
        val navigation = item.optJSONObject("navigationEndpoint") ?: return null
        val browse = navigation.optJSONObject("browseEndpoint") ?: return null
        val browseId = browse.optString("browseId").trim()
        if (browseId.isBlank()) return null
        val pageType = browseEndpointPageType(browse)
        val kind = browseKind(pageType) ?: return null

        val columns = mutableListOf<List<JSONObject>>()
        val flexColumns = item.optJSONArray("flexColumns")
        if (flexColumns != null) {
            for (index in 0 until flexColumns.length()) {
                val text = flexColumns.optJSONObject(index)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")
                columns += textRuns(text)
            }
        }
        val title = runText(columns.firstOrNull().orEmpty()).trim()
            .ifBlank { return null }
        val meta = columns.drop(1).flatten()
        val metaTexts = meta.map { it.optString("text").trim() }
            .filter { it.isNotBlank() && it != "•" && it != " • " }
        val subtitle = metaTexts
            .filter { it !in MUSIC_LABELS }
            .joinToString(" • ")
            .trim()

        return BrowseItem(
            title = title,
            subtitle = subtitle,
            browseId = browseId,
            params = browse.optString("params").takeIf { it.isNotBlank() },
            kind = kind,
            pageType = pageType,
            artworkUrl = thumbnailUrl(item),
        )
    }

    private fun parseTwoRowBrowseItem(item: JSONObject): BrowseItem? {
        val navigation = item.optJSONObject("navigationEndpoint") ?: return null
        val browse = navigation.optJSONObject("browseEndpoint") ?: return null
        val browseId = browse.optString("browseId").trim()
        if (browseId.isBlank()) return null
        val pageType = browseEndpointPageType(browse)
        val kind = browseKind(pageType) ?: BrowseKind.OTHER
        if (kind == BrowseKind.OTHER && pageType.isNotBlank()) return null

        val title = runText(textRuns(item.optJSONObject("title"))).trim()
            .ifBlank { return null }
        val subtitle = runText(textRuns(item.optJSONObject("subtitle"))).trim()
        return BrowseItem(
            title = title,
            subtitle = subtitle,
            browseId = browseId,
            params = browse.optString("params").takeIf { it.isNotBlank() },
            kind = kind,
            pageType = pageType,
            artworkUrl = thumbnailUrl(item),
        )
    }

    private fun browseEndpointPageType(browse: JSONObject): String =
        browse.optJSONObject("browseEndpointContextSupportedConfigs")
            ?.optJSONObject("browseEndpointContextMusicConfig")
            ?.optString("pageType")
            .orEmpty()

    private fun browseKind(pageType: String): BrowseKind? = when (pageType) {
        "MUSIC_PAGE_TYPE_ALBUM" -> BrowseKind.ALBUM
        "MUSIC_PAGE_TYPE_ARTIST" -> BrowseKind.ARTIST
        "MUSIC_PAGE_TYPE_PLAYLIST" -> BrowseKind.PLAYLIST
        else -> null
    }

    private fun browseTitle(root: JSONObject): String? {
        val header = root.optJSONObject("header")
        val candidates = listOf(
            header?.optJSONObject("musicDetailHeaderRenderer")?.optJSONObject("title"),
            header?.optJSONObject("musicHeaderRenderer")?.optJSONObject("title"),
            header?.optJSONObject("musicImmersiveHeaderRenderer")?.optJSONObject("title"),
            header?.optJSONObject("musicCardHeaderRenderer")?.optJSONObject("headerText"),
        )
        for (candidate in candidates) {
            val title = runText(textRuns(candidate)).trim()
            if (title.isNotBlank()) return title
        }
        val microformat = root.optJSONObject("microformat")
            ?.optJSONObject("microformatDataRenderer")
            ?.optString("title")
            .orEmpty()
        return microformat.trim().takeIf { it.isNotBlank() }
    }

    private fun browseSubtitle(root: JSONObject): String? {
        val header = root.optJSONObject("header")
        val candidates = listOf(
            header?.optJSONObject("musicDetailHeaderRenderer")?.optJSONObject("subtitle"),
            header?.optJSONObject("musicHeaderRenderer")?.optJSONObject("subtitle"),
            header?.optJSONObject("musicHeaderRenderer")?.optJSONObject("strapi"),
        )
        for (candidate in candidates) {
            val subtitle = runText(textRuns(candidate)).trim()
            if (subtitle.isNotBlank()) return subtitle
        }
        return null
    }

    private fun parseResponsiveItem(item: JSONObject): Track? {
        val videoId = item.optJSONObject("playlistItemData")
            ?.optString("videoId")
            ?.takeIf { it.isNotBlank() }
            ?: firstWatchVideoId(item)
            ?: return null

        val columns = mutableListOf<List<JSONObject>>()
        val flexColumns = item.optJSONArray("flexColumns")
        if (flexColumns != null) {
            for (index in 0 until flexColumns.length()) {
                val text = flexColumns.optJSONObject(index)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")
                columns += textRuns(text)
            }
        }

        val title = runText(columns.firstOrNull().orEmpty()).trim()
            .ifBlank { "Faixa sem título" }
        val metaRuns = columns.drop(1).flatten()
        val artistRuns = metaRuns.filter { pageType(it) == "MUSIC_PAGE_TYPE_ARTIST" }
            .map { it.optString("text").trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val artist = artistRuns.joinToString(", ").ifBlank {
            metaRuns.map { it.optString("text") }
                .firstOrNull(::looksLikeArtist)
                .orEmpty()
        }
        val album = metaRuns.firstOrNull { pageType(it) == "MUSIC_PAGE_TYPE_ALBUM" }
            ?.optString("text")
            .orEmpty()
        val duration = metaRuns.asSequence()
            .map { parseDuration(it.optString("text")) }
            .firstOrNull { it > 0L }
            ?: 0L

        return Track(
            id = "yt:$videoId",
            title = title,
            artist = artist,
            album = album,
            durationMs = duration,
            artworkUrl = thumbnailUrl(item),
            source = TrackSource.YOUTUBE_MUSIC,
            remoteId = videoId,
        )
    }

    private fun parsePlaylistPanelItem(item: JSONObject): Track? {
        val videoId = item.optString("videoId")
            .takeIf { it.isNotBlank() }
            ?: firstWatchVideoId(item)
            ?: return null
        val title = runText(textRuns(item.optJSONObject("title")))
            .ifBlank { item.optString("title") }
            .ifBlank { "Faixa sem título" }
        val artist = runText(textRuns(item.optJSONObject("shortBylineText")))
            .ifBlank { runText(textRuns(item.optJSONObject("longBylineText"))) }
        val album = textRuns(item.optJSONObject("longBylineText"))
            .firstOrNull { pageType(it) == "MUSIC_PAGE_TYPE_ALBUM" }
            ?.optString("text")
            .orEmpty()
        val duration = parseDuration(
            item.optJSONObject("lengthText")?.optString("simpleText").orEmpty(),
        ).takeIf { it > 0L } ?: textRuns(item.optJSONObject("lengthText"))
            .asSequence()
            .map { parseDuration(it.optString("text")) }
            .firstOrNull { it > 0L }
            ?: 0L

        return Track(
            id = "yt:$videoId",
            title = title,
            artist = artist,
            album = album,
            durationMs = duration,
            artworkUrl = thumbnailUrl(item),
            source = TrackSource.YOUTUBE_MUSIC,
            remoteId = videoId,
        )
    }

    private fun bestAudioUrl(streaming: JSONObject, quality: AudioQuality): String? {
        val formats = mutableListOf<JSONObject>()
        for (key in listOf("adaptiveFormats", "formats")) {
            val array = streaming.optJSONArray(key) ?: continue
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let(formats::add)
            }
        }
        val audio = formats.filter {
            it.optString("mimeType").startsWith("audio/") &&
                it.optString("url").isNotBlank()
        }
        if (audio.isEmpty()) return null

        val mp4 = audio.filter { it.optString("mimeType").startsWith("audio/mp4") }
        val pool = mp4.ifEmpty { audio }
        val target = when (quality) {
            AudioQuality.DATA_SAVER -> 64_000
            AudioQuality.NORMAL -> 128_000
            AudioQuality.HIGH -> 256_000
            AudioQuality.LOSSLESS, AudioQuality.HI_RES -> Int.MAX_VALUE
        }
        val chosen = pool.minByOrNull {
            abs(it.optInt("bitrate", 0).toLong() - target.toLong())
        } ?: pool.first()
        return chosen.optString("url").takeIf { it.isNotBlank() }
    }

    private fun textRuns(obj: JSONObject?): List<JSONObject> {
        val array = obj?.optJSONArray("runs") ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    private fun runText(runs: List<JSONObject>): String =
        runs.joinToString("") { it.optString("text") }

    private fun pageType(run: JSONObject): String? =
        run.optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")
            ?.optJSONObject("browseEndpointContextSupportedConfigs")
            ?.optJSONObject("browseEndpointContextMusicConfig")
            ?.optString("pageType")
            ?.takeIf { it.isNotBlank() }

    private fun firstWatchVideoId(root: Any?): String? {
        var found: String? = null
        fun walk(value: Any?) {
            if (found != null) return
            when (value) {
                is JSONObject -> {
                    val id = value.optJSONObject("watchEndpoint")
                        ?.optString("videoId")
                        .orEmpty()
                    if (id.isNotBlank()) {
                        found = id
                        return
                    }
                    val keys = value.keys()
                    while (keys.hasNext()) walk(value.opt(keys.next()))
                }
                is JSONArray -> {
                    for (index in 0 until value.length()) walk(value.opt(index))
                }
            }
        }
        walk(root)
        return found
    }

    private fun thumbnailUrl(item: JSONObject): String? {
        val thumbnail = item.optJSONObject("thumbnail")
        val direct = thumbnail
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
        val plain = thumbnail?.optJSONArray("thumbnails")
        val renderer = item.optJSONObject("thumbnailRenderer")
        val rendererDirect = renderer
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
        val rendererPlain = renderer?.optJSONArray("thumbnails")
        return bestThumbnail(direct)
            ?: bestThumbnail(plain)
            ?: bestThumbnail(rendererDirect)
            ?: bestThumbnail(rendererPlain)
    }

    private fun bestThumbnail(array: JSONArray?): String? {
        if (array == null) return null
        var bestUrl: String? = null
        var bestArea = -1
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val url = item.optString("url").takeIf { it.isNotBlank() } ?: continue
            val area = item.optInt("width", 0) * item.optInt("height", 0)
            if (area >= bestArea) {
                bestUrl = url
                bestArea = area
            }
        }
        return bestUrl
    }

    private fun parseDuration(value: String): Long {
        val text = value.trim()
        val match = DURATION_PATTERN.find(text) ?: return 0L
        val groups = match.groupValues
        val hours = groups.getOrNull(1)?.toLongOrNull() ?: 0L
        val minutes = groups.getOrNull(2)?.toLongOrNull() ?: 0L
        val seconds = groups.getOrNull(3)?.toLongOrNull() ?: 0L
        return ((hours * 60L + minutes) * 60L + seconds) * 1000L
    }

    private fun looksLikeArtist(value: String): Boolean {
        val text = value.trim()
        if (text.isEmpty() || text == "•") return false
        if (text in MUSIC_LABELS) return false
        if (text.matches(DURATION_PLAIN_PATTERN)) return false
        if (text.startsWith("Tocou ")) return false
        if (text.endsWith(" visualizações") || text.endsWith(" visualização")) return false
        if (text.endsWith(" ouvintes mensais")) return false
        return true
    }

    private fun collectObjects(root: Any?, key: String): List<JSONObject> {
        val found = mutableListOf<JSONObject>()
        fun walk(value: Any?) {
            when (value) {
                is JSONObject -> {
                    value.optJSONObject(key)?.let(found::add)
                    val keys = value.keys()
                    while (keys.hasNext()) walk(value.opt(keys.next()))
                }
                is JSONArray -> {
                    for (index in 0 until value.length()) walk(value.opt(index))
                }
            }
        }
        walk(root)
        return found
    }

    private data class ApiConfig(
        val key: String,
        val version: String,
        val visitorData: String,
        val fetchedAt: Long,
    )

    data class YouTubePlayback(
        val title: String,
        val artist: String,
        val durationMs: Long,
        val artworkUrl: String?,
        val streamUrl: String?,
        val status: String?,
    )

    companion object {
        const val DEFAULT_API_ROOT = "https://music.youtube.com/youtubei/v1"

        fun isDirectApi(value: String): Boolean {
            val text = value.trim()
            return text.isBlank() || text.contains("/youtubei/v1", ignoreCase = true)
        }

        private const val HOME_URL = "https://music.youtube.com/"
        private const val CLIENT_NAME_ID = "67"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        private const val FALLBACK_API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
        private const val FALLBACK_CLIENT_VERSION = "1.20261104.00.00"
        private const val CONFIG_TTL_MS = 6 * 60 * 60 * 1000L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val API_KEY_PATTERN = Regex("\"INNERTUBE_API_KEY\":\"([^\"]+)\"")
        private val CLIENT_VERSION_PATTERN = Regex("\"INNERTUBE_CLIENT_VERSION\":\"([^\"]+)\"")
        private val VISITOR_DATA_PATTERN = Regex("\"VISITOR_DATA\":\"([^\"]+)\"")
        private val DURATION_PATTERN = Regex("^(?:(\\d+):)?(\\d{1,2}):(\\d{2})$")
        private val DURATION_PLAIN_PATTERN = Regex("^(?:(\\d+):)?\\d{1,2}:\\d{2}$")
        private val MUSIC_LABELS = setOf(
            "Música",
            "Vídeo",
            "Álbum",
            "Single",
            "EP",
            "Artista",
            "Playlist",
            "Podcast",
        )
    }
}
