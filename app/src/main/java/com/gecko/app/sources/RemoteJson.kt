package com.gecko.app.sources

import com.gecko.app.model.Track
import com.gecko.app.model.TrackSource
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal object RemoteJson {

    fun parseTracks(
        body: String,
        source: TrackSource,
        providerId: String?,
        baseUrl: String,
    ): List<Track> {
        val root = runCatching { JSONTokener(body).nextValue() }.getOrNull() ?: return emptyList()
        val array = when (root) {
            is JSONArray -> root
            is JSONObject -> root.optJSONArray("tracks")
                ?: root.optJSONArray("results")
                ?: root.optJSONArray("items")
                ?: JSONArray()
            else -> JSONArray()
        }

        val result = mutableListOf<Track>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val remoteId = item.optString("id")
                .ifBlank { item.optString("videoId") }
                .ifBlank { item.optString("trackId") }
            if (remoteId.isBlank()) continue

            val lossless = item.optBoolean("lossless", false)
            val artwork = item.optString("artworkUrl")
                .ifBlank { item.optString("artwork") }
                .ifBlank { item.optString("thumbnail") }
                .let { path -> absolutize(path, baseUrl) }

            result += Track(
                id = when (source) {
                    TrackSource.YOUTUBE_MUSIC -> "yt:$remoteId"
                    TrackSource.PLUGIN -> "plugin:${providerId.orEmpty()}:$remoteId"
                    else -> remoteId
                },
                title = item.optString("title").ifBlank { "Faixa sem título" },
                artist = item.optString("artist").ifBlank { item.optString("uploader") },
                album = item.optString("album"),
                durationMs = item.optLong("durationMs", 0L),
                mimeType = item.optString("mimeType").takeIf { it.isNotBlank() },
                artworkUrl = artwork,
                source = source,
                providerId = providerId,
                remoteId = remoteId,
                isLosslessHint = lossless,
                qualityHint = item.optString("quality").takeIf { it.isNotBlank() }
                    ?: if (lossless) "Lossless" else null,
            )
        }
        return result
    }

    fun streamUrl(
        body: String,
        baseUrl: String,
    ): String? {
        val trimmed = body.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        val root = runCatching { JSONTokener(trimmed).nextValue() }.getOrNull() ?: return null
        if (root is JSONObject) {
            val value = root.optString("url")
                .ifBlank { root.optString("streamUrl") }
                .ifBlank { root.optString("stream") }
                .ifBlank { root.optString("src") }
            if (value.isNotBlank()) return absolutize(value, baseUrl)
        }
        return null
    }

    private fun absolutize(path: String, baseUrl: String): String? {
        if (path.isBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return baseUrl.trimEnd('/') + "/" + path.trimStart('/')
    }
}
