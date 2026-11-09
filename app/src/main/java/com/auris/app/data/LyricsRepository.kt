package com.auris.app.data

import android.content.Context
import android.net.Uri
import com.auris.app.util.safeFileId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class LyricLine(
    val timeMs: Long,
    val text: String,
)

data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
    val source: String,
)

class LyricsRepository(private val context: Context) {

    private val dir = File(context.filesDir, "lyrics").apply { mkdirs() }

    fun load(trackId: String): Lyrics? {
        val file = fileFor(trackId)
        if (!file.exists()) return null
        return runCatching { parse(file.readText()) }.getOrNull()
    }

    fun save(trackId: String, text: String) {
        fileFor(trackId).writeText(text)
    }

    suspend fun import(trackId: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: return@withContext false
            if (text.isBlank()) return@withContext false
            save(trackId, text)
            true
        }.getOrDefault(false)
    }

    fun delete(trackId: String) {
        fileFor(trackId).delete()
    }

    fun parse(raw: String): Lyrics {
        val timestamped = mutableListOf<LyricLine>()
        val plain = mutableListOf<String>()
        var offsetMs = 0L
        val timeTag = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")

        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach

            if (trimmed.startsWith("[offset:", ignoreCase = true)) {
                offsetMs = trimmed.substringAfter(':').substringBefore(']').trim().toLongOrNull() ?: 0L
                return@forEach
            }

            val matches = timeTag.findAll(trimmed).toList()
            if (matches.isEmpty()) {
                if (!trimmed.startsWith("[")) plain += trimmed
                return@forEach
            }

            val text = trimmed.substring(matches.last().range.last + 1).trim()
            matches.forEach { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                val fractionRaw = match.groupValues[3]
                val fraction = when (fractionRaw.length) {
                    0 -> 0L
                    1 -> fractionRaw.toLong() * 100L
                    2 -> fractionRaw.toLong() * 10L
                    else -> fractionRaw.take(3).toLong()
                }
                timestamped += LyricLine(
                    timeMs = (minutes * 60_000L + seconds * 1000L + fraction + offsetMs)
                        .coerceAtLeast(0L),
                    text = text.ifBlank { "♪" },
                )
            }
        }

        return if (timestamped.isNotEmpty()) {
            Lyrics(
                lines = timestamped.sortedBy { it.timeMs },
                synced = true,
                source = "LRC sincronizado",
            )
        } else {
            Lyrics(
                lines = plain.map { LyricLine(0L, it) },
                synced = false,
                source = "Letra sem sincronia",
            )
        }
    }

    private fun fileFor(trackId: String): File = File(dir, "${safeFileId(trackId)}.lrc")
}
