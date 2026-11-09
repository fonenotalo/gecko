package com.auris.app.data

import android.content.Context
import android.net.Uri
import com.auris.app.model.Track
import com.auris.app.model.TrackSource
import com.auris.app.util.safeFileId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
}

data class DownloadEntry(
    val track: Track,
    val status: DownloadStatus,
    val progress: Float = 0f,
    val quality: String = "",
    val bytes: Long = 0L,
    val filePath: String? = null,
    val remoteUrl: String? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Smart offline storage. Remote streams are copied into app-private storage at
 * the chosen download quality. Online playback keeps using the live stream.
 */
class DownloadRepository(
    private val context: Context,
    private val client: OkHttpClient,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val semaphore = Semaphore(2)
    private val dir = File(context.filesDir, "auris_downloads").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")

    private val _entries = MutableStateFlow<List<DownloadEntry>>(emptyList())
    val entries: StateFlow<List<DownloadEntry>> = _entries.asStateFlow()

    init {
        scope.launch { loadIndex() }
    }

    fun localUri(trackId: String): Uri? {
        val entry = _entries.value.firstOrNull {
            it.track.id == trackId && it.status == DownloadStatus.COMPLETED
        } ?: return null
        val path = entry.filePath ?: return null
        val file = File(path)
        return if (file.exists() && file.length() > 0) Uri.fromFile(file) else null
    }

    fun isDownloaded(trackId: String): Boolean = localUri(trackId) != null

    fun completedTracks(): List<Track> = _entries.value
        .filter { it.status == DownloadStatus.COMPLETED && it.filePath != null }
        .mapNotNull { entry ->
            val file = File(entry.filePath!!)
            if (!file.exists()) return@mapNotNull null
            entry.track.copy(
                uri = Uri.fromFile(file),
                isDownloaded = true,
                artworkUri = entry.track.artworkUri,
            )
        }

    fun totalBytes(): Long = _entries.value
        .filter { it.status == DownloadStatus.COMPLETED }
        .sumOf { it.bytes }

    fun enqueue(track: Track, streamUrl: String, qualityLabel: String) {
        if (track.source == TrackSource.LOCAL || track.source == TrackSource.DEMO) return
        val existing = _entries.value.firstOrNull { it.track.id == track.id }
        if (existing != null && existing.status in setOf(
                DownloadStatus.QUEUED,
                DownloadStatus.DOWNLOADING,
                DownloadStatus.COMPLETED,
            )
        ) {
            return
        }

        val entry = DownloadEntry(
            track = track,
            status = DownloadStatus.QUEUED,
            quality = qualityLabel,
            remoteUrl = streamUrl,
        )
        _entries.update { current ->
            listOf(entry) + current.filterNot { it.track.id == track.id }
        }
        scope.launch {
            semaphore.withPermit { perform(entry, streamUrl, qualityLabel) }
        }
    }

    fun retry(trackId: String) {
        val entry = _entries.value.firstOrNull { it.track.id == trackId } ?: return
        val url = entry.remoteUrl ?: return
        enqueue(entry.track, url, entry.quality)
    }

    fun delete(trackId: String) {
        val entry = _entries.value.firstOrNull { it.track.id == trackId }
        entry?.filePath?.let { runCatching { File(it).delete() } }
        entry?.track?.artworkUri?.takeIf { it.scheme == "file" }?.path?.let {
            if (it.startsWith(dir.absolutePath)) runCatching { File(it).delete() }
        }
        _entries.update { current -> current.filterNot { it.track.id == trackId } }
        persistIndex()
    }

    fun deleteAll() {
        dir.listFiles()?.forEach { file ->
            if (file.name != indexFile.name) runCatching { file.deleteRecursively() }
        }
        _entries.value = emptyList()
        persistIndex()
    }

    private fun perform(entry: DownloadEntry, url: String, qualityLabel: String) {
        val track = entry.track
        update(entry.track.id) { it.copy(status = DownloadStatus.DOWNLOADING, progress = 0f, error = null) }

        val request = runCatching {
            Request.Builder()
                .url(url)
                .header("User-Agent", "Auris/1.0 (Android)")
                .build()
        }.getOrElse { error ->
            fail(entry, "URL inválida: ${error.message}")
            return
        }

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    fail(entry, "Servidor respondeu ${response.code}")
                    return
                }
                val body = response.body ?: run {
                    fail(entry, "Resposta vazia")
                    return
                }
                val total = body.contentLength()
                val extension = extensionFor(url, response.header("Content-Type"))
                val target = File(dir, "${safeFileId(track.id)}.$extension")
                val temp = File(dir, "${target.name}.part")

                body.byteStream().use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        var lastUpdate = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            written += read
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 180L || total <= 0L) {
                                lastUpdate = now
                                val progress = if (total > 0) {
                                    (written.toFloat() / total).coerceIn(0f, 0.99f)
                                } else {
                                    0f
                                }
                                update(track.id) {
                                    it.copy(progress = progress, bytes = written)
                                }
                            }
                        }
                        output.fd.sync()
                    }
                }

                if (temp.length() < 4096) {
                    temp.delete()
                    fail(entry, "Arquivo baixado é inválido")
                    return
                }
                if (target.exists()) target.delete()
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }

                val cover = downloadCover(track)
                val finalTrack = track.copy(
                    uri = Uri.fromFile(target),
                    isDownloaded = true,
                    artworkUri = cover ?: track.artworkUri,
                )
                update(track.id) {
                    it.copy(
                        track = finalTrack,
                        status = DownloadStatus.COMPLETED,
                        progress = 1f,
                        bytes = target.length(),
                        filePath = target.absolutePath,
                        quality = qualityLabel,
                        error = null,
                    )
                }
                persistIndex()
            }
        } catch (error: Exception) {
            fail(entry, error.message ?: "Falha no download")
        }
    }

    private fun downloadCover(track: Track): Uri? {
        val artwork = track.artworkUrl?.takeIf { it.startsWith("http") } ?: return track.artworkUri
        return runCatching {
            val request = Request.Builder().url(artwork).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val bytes = response.body?.bytes() ?: return@runCatching null
                if (bytes.size < 256) return@runCatching null
                val file = File(dir, "${safeFileId(track.id)}_cover.jpg")
                file.writeBytes(bytes)
                Uri.fromFile(file)
            }
        }.getOrNull()
    }

    private fun fail(entry: DownloadEntry, message: String) {
        update(entry.track.id) {
            it.copy(
                status = DownloadStatus.FAILED,
                error = message,
                progress = 0f,
            )
        }
    }

    private fun update(trackId: String, transform: (DownloadEntry) -> DownloadEntry) {
        _entries.update { current ->
            current.map { if (it.track.id == trackId) transform(it) else it }
        }
    }

    private fun extensionFor(url: String, contentType: String?): String {
        val fromUrl = url.substringBefore('?').substringAfterLast('.', "").lowercase()
        if (fromUrl in SUPPORTED_EXTENSIONS) return fromUrl
        val type = contentType?.lowercase().orEmpty()
        return when {
            type.contains("flac") -> "flac"
            type.contains("mpeg") || type.contains("mp3") -> "mp3"
            type.contains("mp4") || type.contains("m4a") -> "m4a"
            type.contains("ogg") -> "ogg"
            type.contains("opus") -> "opus"
            type.contains("wav") -> "wav"
            type.contains("aac") -> "aac"
            else -> "audio"
        }
    }

    private fun loadIndex() {
        if (!indexFile.exists()) return
        runCatching {
            val root = JSONObject(indexFile.readText())
            val array = root.optJSONArray("entries") ?: JSONArray()
            val loaded = buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val file = File(item.getString("file"))
                    if (!file.exists()) continue
                    val source = runCatching {
                        TrackSource.valueOf(item.optString("source", TrackSource.YOUTUBE_MUSIC.name))
                    }.getOrDefault(TrackSource.YOUTUBE_MUSIC)
                    val artPath = item.optString("artworkPath").takeIf { it.isNotBlank() }
                    val track = Track(
                        id = item.getString("id"),
                        title = item.getString("title"),
                        artist = item.optString("artist"),
                        album = item.optString("album"),
                        durationMs = item.optLong("durationMs"),
                        mimeType = item.optString("mimeType").takeIf { it.isNotBlank() },
                        uri = Uri.fromFile(file),
                        artworkUri = artPath?.let { path -> File(path).takeIf { it.exists() } }?.let(Uri::fromFile),
                        artworkUrl = item.optString("artworkUrl").takeIf { it.isNotBlank() },
                        source = source,
                        providerId = item.optString("providerId").takeIf { it.isNotBlank() },
                        remoteId = item.optString("remoteId").takeIf { it.isNotBlank() },
                        isLosslessHint = item.optBoolean("lossless"),
                        qualityHint = item.optString("qualityHint").takeIf { it.isNotBlank() },
                        isDownloaded = true,
                    )
                    add(
                        DownloadEntry(
                            track = track,
                            status = DownloadStatus.COMPLETED,
                            progress = 1f,
                            quality = item.optString("quality"),
                            bytes = item.optLong("bytes", file.length()),
                            filePath = file.absolutePath,
                            remoteUrl = item.optString("remoteUrl").takeIf { it.isNotBlank() },
                            createdAt = item.optLong("createdAt"),
                        ),
                    )
                }
            }
            _entries.value = loaded
        }
    }

    private fun persistIndex() {
        scope.launch {
            val completed = _entries.value.filter { it.status == DownloadStatus.COMPLETED }
            val array = JSONArray()
            completed.forEach { entry ->
                val item = entry.track
                array.put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("title", item.title)
                        put("artist", item.artist)
                        put("album", item.album)
                        put("durationMs", item.durationMs)
                        put("mimeType", item.mimeType.orEmpty())
                        put("source", item.source.name)
                        put("providerId", item.providerId.orEmpty())
                        put("remoteId", item.remoteId.orEmpty())
                        put("artworkUrl", item.artworkUrl.orEmpty())
                        put("artworkPath", item.artworkUri?.takeIf { it.scheme == "file" }?.path.orEmpty())
                        put("lossless", item.isLosslessHint)
                        put("qualityHint", item.qualityHint.orEmpty())
                        put("file", entry.filePath.orEmpty())
                        put("quality", entry.quality)
                        put("bytes", entry.bytes)
                        put("remoteUrl", entry.remoteUrl.orEmpty())
                        put("createdAt", entry.createdAt)
                    },
                )
            }
            runCatching {
                indexFile.writeText(JSONObject().put("entries", array).toString())
            }
        }
    }

    private companion object {
        val SUPPORTED_EXTENSIONS = setOf(
            "flac", "mp3", "m4a", "aac", "opus", "ogg", "wav", "alac", "webm",
        )
    }
}
