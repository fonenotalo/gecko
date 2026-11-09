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
 * Tenta o cliente `ANDROID_MUSIC` primeiro (mais confiável para áudio),
 * com fallback para `WEB_REMIX` quando o YouTube não libera o stream sem
 * autenticação/PO token.
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
    @Volatile private var config: ApiConfig? = null

    // Cache do decipher de assinatura (base.js)
    private val cipherLock = Mutex()
    @Volatile private var cipherOps: List<CipherOp>? = null
    @Volatile private var cipherFetchedAt = 0L
    private val CIPHER_TTL_MS = 12 * 60 * 60 * 1000L

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

    suspend fun playback(
        baseUrl: String,
        videoId: String,
        quality: AudioQuality = AudioQuality.HIGH,
    ): YouTubePlayback? {
        if (videoId.isBlank()) return null
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(30_000L) {
                // Tenta ANDROID_MUSIC primeiro
                var result = tryPlayback(baseUrl, videoId, quality, clientName = "ANDROID_MUSIC", clientVersion = cfgVersionOrFallback())
                if (result == null || result.streamUrl.isNullOrBlank()) {
                    // Fallback para WEB_REMIX (funciona sem poToken em muitos casos)
                    result = tryPlayback(baseUrl, videoId, quality, clientName = "WEB_REMIX", clientVersion = cfgVersionOrFallback())
                }
                result
            }
        }
    }

    private suspend fun tryPlayback(
        baseUrl: String,
        videoId: String,
        quality: AudioQuality,
        clientName: String,
        clientVersion: String?,
    ): YouTubePlayback? {
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
        val root = postWithClient(baseUrl, "player", extra, clientName, clientVersion ?: "") ?: return null
        val details = root.optJSONObject("videoDetails")
        val status = root.optJSONObject("playabilityStatus")?.optString("status")
        val streaming = root.optJSONObject("streamingData")
        return YouTubePlayback(
            title = details?.optString("title").orEmpty(),
            artist = details?.optString("author").orEmpty(),
            durationMs = (details?.optString("lengthSeconds")?.toLongOrNull() ?: 0L) * 1000L,
            artworkUrl = details?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")
                ?.let(::bestThumbnail),
            streamUrl = streaming?.let { resolveAudioUrl(it, quality) },
            status = status,
        )
    }

    private suspend fun cfgVersionOrFallback(): String {
        val cfg = config()
        return cfg.version.takeIf { it.isNotBlank() } ?: FALLBACK_CLIENT_VERSION
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
            put("context", contextJson(current, "WEB_REMIX"))
            val keys = extra.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, extra.opt(key))
            }
        }
        return execute(endpointUrl(baseUrl, endpoint, current.key), payload, current)
    }

    private suspend fun postWithClient(
        baseUrl: String,
        endpoint: String,
        extra: JSONObject,
        clientName: String,
        clientVersion: String,
    ): JSONObject? {
        val current = config()
        val version = clientVersion.takeIf { it.isNotBlank() } ?: current.version
        val payload = JSONObject().apply {
            put("context", contextJson(current.copy(version = version), clientName))
            val keys = extra.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, extra.opt(key))
            }
        }
        return executeWithClient(
            endpointUrl(baseUrl, endpoint, current.key),
            payload,
            current,
            clientName,
        )
    }

    private fun execute(url: String, payload: JSONObject, cfg: ApiConfig): JSONObject? {
        return executeWithClient(url, payload, cfg, "WEB_REMIX")
    }

    private fun executeWithClient(
        url: String,
        payload: JSONObject,
        cfg: ApiConfig,
        clientName: String,
    ): JSONObject? {
        val clientNameId = when (clientName) {
            "ANDROID_MUSIC" -> "21"
            "WEB_REMIX" -> "67"
            else -> "67"
        }
        val userAgent = when (clientName) {
            "ANDROID_MUSIC" -> ANDROID_USER_AGENT
            else -> WEB_USER_AGENT
        }
        val builder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .header("X-YouTube-Client-Name", clientNameId)
            .header("X-YouTube-Client-Version", cfg.version)
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("User-Agent", userAgent)
        if (cfg.visitorData.isNotBlank()) {
            builder.header("X-Goog-Visitor-Id", cfg.visitorData)
        }
        if (cfg.poToken.isNotBlank() && clientName == "ANDROID_MUSIC") {
            builder.header("X-Goog-Page-Id", cfg.poToken)
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
            poToken = "",
            fetchedAt = System.currentTimeMillis(),
        )
        val html = runCatching {
            val request = Request.Builder()
                .url(HOME_URL)
                .header("User-Agent", WEB_USER_AGENT)
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
            poToken = "",
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

    private fun contextJson(cfg: ApiConfig, clientName: String): JSONObject = JSONObject().apply {
        put(
            "client",
            JSONObject().apply {
                put("clientName", clientName)
                put("clientVersion", cfg.version)
                put("hl", "pt-BR")
                put("gl", "BR")
                if (clientName == "ANDROID_MUSIC") {
                    put("androidSdkVersion", 33)
                    put("device", "generic_x86_64")
                }
                if (cfg.visitorData.isNotBlank()) put("visitorData", cfg.visitorData)
            },
        )
        if (cfg.poToken.isNotBlank() && clientName == "ANDROID_MUSIC") {
            put("thirdParty", JSONObject().put("poToken", cfg.poToken))
        }
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
        val twoRowTracks = collectObjects(root, "musicTwoRowItemRenderer")
            .mapNotNull(::parseTwoRowTrack)
        val items = (collectObjects(root, "musicResponsiveListItemRenderer")
            .mapNotNull(::parseResponsiveBrowseItem) +
            collectObjects(root, "musicTwoRowItemRenderer").mapNotNull(::parseTwoRowBrowseItem))
            .filter { it.browseId.isNotBlank() }
            .distinctBy { it.browseId }
        return BrowsePage(
            title = title,
            subtitle = browseSubtitle(root),
            tracks = (tracks + twoRowTracks).distinctBy { it.remoteId },
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

    private fun parseTwoRowTrack(item: JSONObject): Track? {
        val watch = item.optJSONObject("navigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?: return null
        val videoId = watch.optString("videoId").trim()
        if (videoId.isBlank()) return null
        val title = runText(textRuns(item.optJSONObject("title"))).trim()
            .ifBlank { return null }
        val artist = runText(textRuns(item.optJSONObject("subtitle"))).trim()
        return Track(
            id = "yt:$videoId",
            title = title,
            artist = artist,
            album = "",
            durationMs = 0L,
            artworkUrl = thumbnailUrl(item),
            source = TrackSource.YOUTUBE_MUSIC,
            remoteId = videoId,
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

    /**
     * Escolhe o melhor formato de áudio. Quando só há `signatureCipher` (sem `url` direta),
     * tenta o decipher igual o NewPipe.
     */
    private suspend fun resolveAudioUrl(streaming: JSONObject, quality: AudioQuality): String? {
        val formats = mutableListOf<JSONObject>()
        for (key in listOf("adaptiveFormats", "formats")) {
            val array = streaming.optJSONArray(key) ?: continue
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let(formats::add)
            }
        }
        val audio = formats.filter {
            val mime = it.optString("mimeType")
            mime.startsWith("audio/") && (it.optString("url").isNotBlank() || it.optString("signatureCipher").isNotBlank())
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

        val directUrl = chosen.optString("url").takeIf { it.isNotBlank() }
        if (directUrl != null) return directUrl

        val cipher = chosen.optString("signatureCipher").takeIf { it.isNotBlank() } ?: return null
        return decipherSignature(cipher)
    }

    /**
     * Decifra uma `signatureCipher` do YouTube.
     */
    private suspend fun decipherSignature(cipher: String): String? {
        val params = cipher.split("&").associate { val p = it.split("=", limit = 2); p.first() to p.last() }
        val url = params["url"] ?: return null
        val sp = params["sp"] ?: "sig"
        val s = params["s"] ?: return null

        val ops = loadCipherOps() ?: return null
        var signature = s
        for (op in ops) {
            signature = op.apply(signature)
        }

        return "$url&$sp=$signature"
    }

    /**
     * Carrega as operações de cipher do `base.js` do YouTube, cacheando por TTL.
     */
    private suspend fun loadCipherOps(): List<CipherOp>? {
        val now = System.currentTimeMillis()
        if (cipherOps != null && now - cipherFetchedAt < CIPHER_TTL_MS) {
            return cipherOps
        }
        return cipherLock.withLock {
            if (cipherOps != null && now - cipherFetchedAt < CIPHER_TTL_MS) {
                cipherOps
            } else {
                val ops = fetchCipherOps().also {
                    cipherOps = it
                    cipherFetchedAt = System.currentTimeMillis()
                }
                ops
            }
        }
    }

    private suspend fun fetchCipherOps(): List<CipherOp>? = withContext(Dispatchers.IO) {
        val html = runCatching {
            val request = Request.Builder()
                .url("https://www.youtube.com/")
                .header("User-Agent", WEB_USER_AGENT)
                .build()
            apiClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string().orEmpty() else ""
            }
        }.getOrDefault("")
        val playerVersion = PLAYER_VERSION_PATTERN.find(html)?.groupValues?.getOrNull(1)
        if (playerVersion.isNullOrBlank()) return@withContext null

        val jsUrl = "https://www.youtube.com/s/player/$playerVersion/base.js"
        val js = runCatching {
            val request = Request.Builder()
                .url(jsUrl)
                .header("User-Agent", WEB_USER_AGENT)
                .build()
            apiClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string().orEmpty() else ""
            }
        }.getOrDefault("")
        if (js.isBlank()) return@withContext null

        val cipherFn = CIPHER_FUNCTION_PATTERN.find(js)?.groupValues?.getOrNull(1) ?: return@withContext null

        val ops = mutableListOf<CipherOp>()
        val opRegex = Regex("(?:\\)\\.)?(reverse|swap|slice)\\(\\s*(\\d+)?\\s*\\)")
        for (m in opRegex.findAll(cipherFn)) {
            when (m.groupValues[1]) {
                "reverse" -> ops.add(CipherOp.Reverse)
                "swap" -> {
                    val pos = m.groupValues[2].toIntOrNull() ?: 0
                    ops.add(CipherOp.Swap(pos))
                }
                "slice" -> {
                    val pos = m.groupValues[2].toIntOrNull() ?: 0
                    ops.add(CipherOp.Slice(pos))
                }
            }
        }
        return@withContext ops.takeIf { it.isNotEmpty() } ?: null
    }

    // --------------------------------------------------------------- cipher ops

    sealed class CipherOp {
        object Reverse : CipherOp()
        data class Swap(val pos: Int) : CipherOp()
        data class Slice(val pos: Int) : CipherOp()
    }

    private fun CipherOp.apply(s: String): String = when (this) {
        is CipherOp.Reverse -> s.reversed()
        is CipherOp.Swap -> {
            val chars = s.toCharArray()
            if (chars.size > pos) {
                val tmp = chars[0]
                chars[0] = chars[pos]
                chars[pos] = tmp
            }
            chars.joinToString("")
        }
        is CipherOp.Slice -> {
            if (pos >= s.length) "" else s.substring(pos)
        }
    }

    // --------------------------------------------------------------- parsers (cont.)

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
        val poToken: String,
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
        private const val WEB_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        private const val ANDROID_USER_AGENT =
            "com.google.android.apps.youtube.music/6.31 (Linux; U; Android 13) gzip"
        private const val FALLBACK_API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
        private const val FALLBACK_CLIENT_VERSION = "1.20261104.00.00"
        private const val CONFIG_TTL_MS = 6 * 60 * 60 * 1000L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val API_KEY_PATTERN = Regex("\"INNERTUBE_API_KEY\":\"([^\"]+)\"")
        private val CLIENT_VERSION_PATTERN = Regex("\"INNERTUBE_CLIENT_VERSION\":\"([^\"]+)\"")
        private val VISITOR_DATA_PATTERN = Regex("\"VISITOR_DATA\":\"([^\"]+)\"")
        private val POTOKEN_PATTERN = Regex("\"PO_TOKEN\":\"([^\"]+)\"")
        private val PLAYER_VERSION_PATTERN = Regex("\"playerVersion\":\"([^\"]+)\"")
        private val CIPHER_FUNCTION_PATTERN = Regex("function\\s*\\w+\\s*\\(\\s*a\\s*\\)\\s*\\{([^}]+)\\}")
        private val DURATION_PATTERN = Regex("^(?:(\\d+):)?(\\d{1,2}):(\\d{2})$")
        private val DURATION_PLAIN_PATTERN = Regex("^(?:(\\d+):)?\\d{1,2}:\\d{2}$")
        private val MUSIC_LABELS = setOf(
            "Música", "Vídeo", "Álbum", "Single", "EP", "Artista", "Playlist", "Podcast",
        )
    }
}
