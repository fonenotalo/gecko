package com.gecko.app.integrations

import com.gecko.app.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Discord does not expose Rich Presence to third-party Android apps. Gecko
 * therefore publishes a small JSON "now playing" payload to a bridge the user
 * controls (for example a companion process on a PC that owns a Discord IPC
 * connection). Without a bridge the setting stays off.
 */
class DiscordPresenceClient(private val client: OkHttpClient) {

    suspend fun update(
        bridgeUrl: String,
        track: Track?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
    ): Boolean {
        val url = bridgeUrl.trim()
        if (url.isBlank()) return false
        return withContext(Dispatchers.IO) {
            runCatching {
                val json = JSONObject().apply {
                    put("app", "Gecko")
                    put("active", track != null)
                    if (track != null) {
                        put("title", track.title)
                        put("artist", track.displayArtist)
                        put("album", track.displayAlbum)
                        put("source", track.source.name)
                        put("isPlaying", isPlaying)
                        put("positionMs", positionMs)
                        put("durationMs", durationMs)
                        put("artworkUrl", track.artworkUrl.orEmpty())
                        val now = System.currentTimeMillis()
                        val start = now - positionMs.coerceAtLeast(0L)
                        put("startTimestamp", start)
                        if (durationMs > 0) put("endTimestamp", start + durationMs)
                    }
                }
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Gecko/1.0 (Android)")
                    .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                client.newCall(request).execute().use { it.isSuccessful }
            }.getOrDefault(false)
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
