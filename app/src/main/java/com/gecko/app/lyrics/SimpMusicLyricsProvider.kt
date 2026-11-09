package com.gecko.app.lyrics

import com.gecko.app.model.LyricsProviderId
import com.gecko.app.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Provedor baseado na API pública do SimpMusic Lyrics.
 *
 * A documentação indica `https://api-lyrics.simpmusic.org/v1` como base e
 * menciona um limite de 30 requisições por minuto por IP. O serviço aceita o
 * cliente quando o cabeçalho `Origin` aponta para o site oficial, então esses
 * valores são incluídos nas requisições.
 */
class SimpMusicLyricsProvider(
    client: OkHttpClient,
) : LyricsProvider {

    private val httpClient = client.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    override val id: LyricsProviderId = LyricsProviderId.SIMPMUSIC

    override suspend fun resolve(track: Track): LyricsProviderResult? {
        val videoId = track.remoteId?.trim().takeUnless { it.isNullOrBlank() }
        if (videoId != null) {
            val direct = byVideoId(videoId)
            if (direct != null) return direct
        }

        return search(track)
    }

    private suspend fun byVideoId(videoId: String): LyricsProviderResult? {
        val root = getJson("$BASE_URL/${URLEncoder.encode(videoId, "UTF-8")}") ?: return null
        val candidates = lyricArray(root)
        val selected = candidates.bestByVote() ?: return null
        return selected.toResult()
    }

    private suspend fun search(track: Track): LyricsProviderResult? {
        val title = track.title.trim()
        if (title.isBlank()) return null
        val query = when (val artist = track.displayArtist.trim()) {
            "Artista desconhecido" -> title
            else -> "$artist $title"
        }
        val encoded = URLEncoder.encode(query, "UTF-8")
        val root = getJson("$BASE_URL/search?q=$encoded&limit=10&offset=0") ?: return null
        val candidates = lyricArray(root)
        val selected = candidates.bestMatch(track) ?: return null
        return selected.toResult()
    }

    private suspend fun getJson(url: String): JSONObject? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(25_000L) {
            runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("Origin", "https://lyrics.simpmusic.org")
                    .header("Referer", "https://lyrics.simpmusic.org/")
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 16; Pixel 9) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/140.0.0.0 Mobile Safari/537.36",
                    )
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        null
                    } else {
                        val body = response.body?.string().orEmpty()
                        if (body.isBlank()) null else JSONObject(body)
                    }
                }
            }.getOrNull()?.takeIf { it.optBoolean("success", true) }
        }
    }

    private fun lyricArray(root: JSONObject): List<JSONObject> {
        val data = root.opt("data")
        if (data is JSONArray) {
            return (0 until data.length()).mapNotNull { data.optJSONObject(it) }
        }
        if (data is JSONObject) return listOf(data)
        return emptyList()
    }

    private fun List<JSONObject>.bestByVote(): JSONObject? =
        filter { it.hasUsableLyrics() }
            .maxWithOrNull(
                compareBy<JSONObject> { it.optInt("vote", 0) }
                    .thenBy { if (it.optString("syncedLyrics").isNotBlank()) 1 else 0 },
            )

    private fun List<JSONObject>.bestMatch(track: Track): JSONObject? {
        val expectedTitle = normalize(track.title)
        val expectedArtist = normalize(track.displayArtist)
        val expectedAlbum = normalize(track.displayAlbum)
        return filter { it.hasUsableLyrics() }
            .mapNotNull { candidate ->
                val title = normalize(candidate.optString("songTitle"))
                val artist = normalize(candidate.optString("artistName"))
                val album = normalize(candidate.optString("albumName"))
                var score = 0
                if (title == expectedTitle) score += 140
                if (expectedTitle in title) score += 80
                if (title in expectedTitle) score += 80
                if (artist == expectedArtist) score += 100
                if (expectedArtist in artist) score += 70
                if (artist in expectedArtist) score += 70
                if (expectedAlbum.isNotBlank() && album == expectedAlbum) score += 15
                val durationSeconds = candidate.optInt("durationSeconds", 0)
                if (durationSeconds > 0 && track.durationMs > 0L) {
                    val delta = abs(durationSeconds.toLong() - (track.durationMs / 1000L))
                    if (delta <= 8L) score += 25
                    if (delta <= 20L) score += 10
                }
                score += candidate.optInt("vote", 0).coerceIn(0, 20)
                candidate to score
            }
            .filter { (_, score) -> score >= 90 }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }

    private fun JSONObject.hasUsableLyrics(): Boolean =
        optString("syncedLyrics").isNotBlank() || optString("plainLyric").isNotBlank()

    private fun JSONObject.toResult(): LyricsProviderResult? {
        val synced = optString("syncedLyrics").trim()
        val plain = optString("plainLyric").trim()
        val raw = synced.ifBlank { plain }
        return if (raw.isBlank()) {
            null
        } else {
            LyricsProviderResult(
                provider = id,
                rawText = raw,
            )
        }
    }

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed
            .replace(COMBINATION_MARKS, "")
            .replace(NON_ALPHANUMERIC, " ")
            .trim()
    }

    private companion object {
        const val BASE_URL = "https://api-lyrics.simpmusic.org/v1"
        val COMBINATION_MARKS = Regex("\\p{Mn}+")
        val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
    }
}
