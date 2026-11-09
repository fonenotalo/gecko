package com.gecko.app.integrations

import android.content.Context
import com.gecko.app.model.AppSettings
import com.gecko.app.model.Track
import com.gecko.app.util.md5Hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Minimal Last.fm scrobbler. The user supplies their own API key/secret and a
 * session key, so Gecko never handles passwords. Failed scrobbles are queued
 * and retried later.
 */
class LastFmClient(
    context: Context,
    private val client: OkHttpClient,
) {

    private val pendingFile = File(context.filesDir, "gecko_scrobbles.json")
    private val mutex = Mutex()

    fun isConfigured(settings: AppSettings): Boolean =
        settings.scrobbleEnabled &&
            settings.lastFmApiKey.isNotBlank() &&
            settings.lastFmApiSecret.isNotBlank() &&
            settings.lastFmSessionKey.isNotBlank()

    suspend fun nowPlaying(track: Track, settings: AppSettings) {
        if (!isConfigured(settings)) return
        val params = linkedMapOf(
            "method" to "track.updateNowPlaying",
            "artist" to track.displayArtist,
            "track" to track.title,
            "album" to track.displayAlbum,
            "api_key" to settings.lastFmApiKey,
            "sk" to settings.lastFmSessionKey,
        )
        call(params, settings.lastFmApiSecret)
    }

    suspend fun scrobble(track: Track, timestampSeconds: Long, settings: AppSettings) {
        if (!isConfigured(settings)) return
        if (track.durationMs in 1..29_000) return
        val params = linkedMapOf(
            "method" to "track.scrobble",
            "artist" to track.displayArtist,
            "track" to track.title,
            "album" to track.displayAlbum,
            "timestamp" to timestampSeconds.toString(),
            "api_key" to settings.lastFmApiKey,
            "sk" to settings.lastFmSessionKey,
        )
        val ok = call(params, settings.lastFmApiSecret)
        if (!ok) {
            enqueue(
                Scrobble(
                    artist = track.displayArtist,
                    track = track.title,
                    album = track.displayAlbum,
                    timestamp = timestampSeconds,
                ),
            )
        }
    }

    suspend fun flushPending(settings: AppSettings) {
        if (!isConfigured(settings)) return
        mutex.withLock {
            val pending = loadPending()
            if (pending.isEmpty()) return
            val remaining = mutableListOf<Scrobble>()
            pending.forEach { item ->
                val params = linkedMapOf(
                    "method" to "track.scrobble",
                    "artist" to item.artist,
                    "track" to item.track,
                    "album" to item.album,
                    "timestamp" to item.timestamp.toString(),
                    "api_key" to settings.lastFmApiKey,
                    "sk" to settings.lastFmSessionKey,
                )
                if (!call(params, settings.lastFmApiSecret)) remaining += item
            }
            savePending(remaining)
        }
    }

    suspend fun testSession(settings: AppSettings): String? {
        if (settings.lastFmApiKey.isBlank() ||
            settings.lastFmApiSecret.isBlank() ||
            settings.lastFmSessionKey.isBlank()
        ) {
            return null
        }
        val params = linkedMapOf(
            "method" to "user.getInfo",
            "api_key" to settings.lastFmApiKey,
            "sk" to settings.lastFmSessionKey,
        )
        val body = callForBody(params, settings.lastFmApiSecret) ?: return null
        return runCatching {
            JSONObject(body).getJSONObject("user").optString("name").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private suspend fun call(params: Map<String, String>, secret: String): Boolean {
        val body = callForBody(params, secret) ?: return false
        return runCatching { JSONObject(body).optInt("error", 0) == 0 }.getOrDefault(false)
    }

    private suspend fun callForBody(params: Map<String, String>, secret: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val signatureBase = params.entries
                    .sortedBy { it.key }
                    .joinToString("") { "${it.key}${it.value}" } + secret
                val form = FormBody.Builder()
                params.forEach { (key, value) -> form.add(key, value) }
                form.add("api_sig", md5Hex(signatureBase))
                form.add("format", "json")

                val request = Request.Builder()
                    .url("https://ws.audioscrobbler.com/2.0/")
                    .header("User-Agent", "Gecko/1.0 (Android)")
                    .post(form.build())
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) null else response.body?.string()
                }
            }.getOrNull()
        }

    private fun loadPending(): List<Scrobble> = runCatching {
        if (!pendingFile.exists()) return emptyList()
        val array = JSONArray(pendingFile.readText())
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    Scrobble(
                        artist = item.getString("artist"),
                        track = item.getString("track"),
                        album = item.optString("album"),
                        timestamp = item.getLong("timestamp"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun enqueue(scrobble: Scrobble) {
        val pending = (loadPending() + scrobble).takeLast(200)
        savePending(pending)
    }

    private fun savePending(items: List<Scrobble>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("artist", item.artist)
                    put("track", item.track)
                    put("album", item.album)
                    put("timestamp", item.timestamp)
                },
            )
        }
        runCatching { pendingFile.writeText(array.toString()) }
    }

    private data class Scrobble(
        val artist: String,
        val track: String,
        val album: String,
        val timestamp: Long,
    )
}
