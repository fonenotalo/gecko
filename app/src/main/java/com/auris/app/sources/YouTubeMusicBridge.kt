package com.auris.app.sources

import com.auris.app.model.AudioQuality
import com.auris.app.model.Track
import com.auris.app.model.TrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Optional bridge to a self-hosted YouTube Music connector. Auris deliberately
 * does not ship account scraping or DRM bypasses: playback requires a bridge
 * that is authorised to serve the streams.
 *
 * Contract:
 *   GET {base}/api/health
 *   GET {base}/api/search?q={query}   -> {"tracks":[{id,title,artist,album,durationMs,artworkUrl,lossless}]}
 *   GET {base}/api/stream/{id}?quality=lossless -> {"url":"https://..."}
 */
class YouTubeMusicBridge(private val client: OkHttpClient) {

    suspend fun search(baseUrl: String, token: String, query: String): List<Track> {
        val base = normalize(baseUrl) ?: return emptyList()
        val url = "$base/api/search?q=" + URLEncoder.encode(query, "UTF-8")
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = request(url, token)
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    val body = response.body?.string().orEmpty()
                    RemoteJson.parseTracks(
                        body = body,
                        source = TrackSource.YOUTUBE_MUSIC,
                        providerId = null,
                        baseUrl = base,
                    )
                }
            }.getOrDefault(emptyList())
        }
    }

    suspend fun resolve(
        baseUrl: String,
        token: String,
        track: Track,
        quality: AudioQuality,
    ): String? {
        val base = normalize(baseUrl) ?: return null
        val remoteId = track.remoteId ?: return null
        val url = "$base/api/stream/" + URLEncoder.encode(remoteId, "UTF-8") +
            "?quality=" + quality.storageKey
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(20_000L) {
                runCatching {
                    val request = request(url, token)
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            null
                        } else {
                            val body = response.body?.string().orEmpty()
                            RemoteJson.streamUrl(body, base)
                        }
                    }
                }.getOrNull()
            }
        }
    }

    suspend fun ping(baseUrl: String, token: String): Boolean {
        val base = normalize(baseUrl) ?: return false
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(12_000L) {
                runCatching {
                    client.newCall(request("$base/api/health", token)).execute().use { it.isSuccessful }
                }.getOrDefault(false)
            } ?: false
        }
    }

    private fun request(url: String, token: String): Request {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "Auris/1.0 (Android)")
            .header("Accept", "application/json")
        if (token.isNotBlank()) {
            builder.header("Authorization", "Bearer $token")
            builder.header("X-API-Key", token)
        }
        return builder.build()
    }

    private fun normalize(value: String): String? {
        val trimmed = value.trim().trimEnd('/')
        if (trimmed.isBlank()) return null
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return null
        return trimmed
    }
}
