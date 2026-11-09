package com.gecko.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import com.gecko.app.model.Track
import com.gecko.app.model.TrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Generates a tiny, self-contained lossless demo library (PCM WAV + artwork +
 * synced lyrics) so the player can be experienced without any local files.
 */
class DemoContent(private val context: Context) {

    private val dir = File(context.filesDir, "gecko_demo").apply { mkdirs() }
    private val manifestFile = File(dir, "manifest.json")

    fun isGenerated(): Boolean = manifestFile.exists()

    fun load(): List<Track> {
        if (!manifestFile.exists()) return emptyList()
        return runCatching {
            val root = JSONObject(manifestFile.readText())
            val array = root.optJSONArray("tracks") ?: JSONArray()
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val file = File(item.getString("file"))
                    if (!file.exists()) continue
                    add(
                        Track(
                            id = item.getString("id"),
                            title = item.getString("title"),
                            artist = item.getString("artist"),
                            album = item.getString("album"),
                            durationMs = item.getLong("durationMs"),
                            mimeType = "audio/wav",
                            uri = Uri.fromFile(file),
                            artworkUri = File(item.getString("art")).takeIf { it.exists() }?.let(Uri::fromFile),
                            source = TrackSource.DEMO,
                            isLosslessHint = true,
                            qualityHint = "WAV 16/44.1",
                            isDownloaded = true,
                            isDemo = true,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun generate(lyricsRepository: LyricsRepository): List<Track> = withContext(Dispatchers.IO) {
        if (isGenerated()) return@withContext load()
        dir.mkdirs()

        val specs = listOf(
            DemoTrackSpec(
                id = "demo_aurora",
                title = "Aurora de Vidro",
                fileName = "aurora_de_vidro.wav",
                artFileName = "aurora_de_vidro.jpg",
                hue = 28f,
                seed = 0,
                lyrics = LYRICS_AURORA,
            ),
            DemoTrackSpec(
                id = "demo_ima_lunar",
                title = "Ímã Lunar",
                fileName = "ima_lunar.wav",
                artFileName = "ima_lunar.jpg",
                hue = 196f,
                seed = 2,
                lyrics = LYRICS_IMA,
            ),
        )

        val root = JSONObject()
        val tracks = JSONArray()
        val durationMs = DEMO_SECONDS * 1000L

        specs.forEach { spec ->
            val audioFile = File(dir, spec.fileName)
            val artFile = File(dir, spec.artFileName)
            if (!audioFile.exists() || audioFile.length() < 1000) {
                writeWav(audioFile, DEMO_SECONDS, spec.seed)
            }
            if (!artFile.exists()) {
                writeArtwork(artFile, spec.hue, spec.seed)
            }
            lyricsRepository.save(spec.id, spec.lyrics)

            tracks.put(
                JSONObject().apply {
                    put("id", spec.id)
                    put("title", spec.title)
                    put("artist", "Gecko Studio")
                    put("album", "Amostras Gecko")
                    put("durationMs", durationMs)
                    put("file", audioFile.absolutePath)
                    put("art", artFile.absolutePath)
                },
            )
        }

        root.put("tracks", tracks)
        manifestFile.writeText(root.toString())
        load()
    }

    fun remove() {
        dir.deleteRecursively()
        dir.mkdirs()
    }

    private fun writeWav(file: File, seconds: Int, seed: Int, sampleRate: Int = 44_100) {
        val channels = 2
        val bitsPerSample = 16
        val totalFrames = seconds * sampleRate
        val dataSize = totalFrames * channels * bitsPerSample / 8
        val bytes = ByteArray(dataSize)

        val progression = listOf(
            doubleArrayOf(220.00, 261.63, 329.63),
            doubleArrayOf(174.61, 220.00, 261.63),
            doubleArrayOf(261.63, 329.63, 392.00),
            doubleArrayOf(196.00, 246.94, 293.66),
        )
        val chordLength = seconds.toDouble() / progression.size

        var byteIndex = 0
        for (frame in 0 until totalFrames) {
            val time = frame.toDouble() / sampleRate
            val chordIndex = min((time / chordLength).toInt(), progression.lastIndex)
            val chord = progression[(chordIndex + seed) % progression.size]
            val local = time - chordIndex * chordLength
            val chordEnvelope = envelope(local, chordLength)

            var left = 0.0
            var right = 0.0

            chord.forEachIndexed { noteIndex, frequency ->
                val amplitude = 0.052 / (1.0 + noteIndex * 0.35)
                left += sin(2.0 * PI * frequency * 0.9985 * time) * amplitude * chordEnvelope
                right += sin(2.0 * PI * frequency * 1.0015 * time) * amplitude * chordEnvelope
            }

            // Sub bass
            val bassFrequency = chord[0] / 2.0
            left += sin(2.0 * PI * bassFrequency * time) * 0.045 * chordEnvelope
            right += sin(2.0 * PI * bassFrequency * time) * 0.045 * chordEnvelope

            // Gentle stereo arpeggio
            val stepLength = 0.5
            val step = (time / stepLength).toInt()
            val stepTime = time - step * stepLength
            val arpFrequency = chord[(step + seed) % chord.size] * 2.0
            val arpEnvelope = exp(-stepTime * 3.2) * 0.040
            val pan = if (step % 2 == 0) 0.25 else 0.75
            val arp = sin(2.0 * PI * arpFrequency * time) * arpEnvelope
            left += arp * (1.0 - pan)
            right += arp * pan

            val fade = min(
                1.0,
                min(time / 1.8, (seconds - time) / 3.0),
            ).coerceAtLeast(0.0)
            left = (left * fade).coerceIn(-1.0, 1.0)
            right = (right * fade).coerceIn(-1.0, 1.0)

            val leftSample = (left * Short.MAX_VALUE).toInt()
            val rightSample = (right * Short.MAX_VALUE).toInt()
            bytes[byteIndex++] = (leftSample and 0xFF).toByte()
            bytes[byteIndex++] = ((leftSample shr 8) and 0xFF).toByte()
            bytes[byteIndex++] = (rightSample and 0xFF).toByte()
            bytes[byteIndex++] = ((rightSample shr 8) and 0xFF).toByte()
        }

        FileOutputStream(file).use { output ->
            val header = ByteArrayOutputStream(44)
            val data = DataOutputStream(header)
            data.writeBytes("RIFF")
            data.writeIntLe(36 + dataSize)
            data.writeBytes("WAVE")
            data.writeBytes("fmt ")
            data.writeIntLe(16)
            data.writeShortLe(1)
            data.writeShortLe(channels)
            data.writeIntLe(sampleRate)
            data.writeIntLe(sampleRate * channels * bitsPerSample / 8)
            data.writeShortLe(channels * bitsPerSample / 8)
            data.writeShortLe(bitsPerSample)
            data.writeBytes("data")
            data.writeIntLe(dataSize)
            output.write(header.toByteArray())
            output.write(bytes)
        }
    }

    private fun envelope(local: Double, length: Double): Double {
        val attack = 1.4
        val release = 1.6
        val attackGain = (local / attack).coerceIn(0.0, 1.0)
        val releaseGain = ((length - local) / release).coerceIn(0.0, 1.0)
        return attackGain * releaseGain
    }

    private fun writeArtwork(file: File, hue: Float, seed: Int) {
        val size = 640
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val top = Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.28f))
        val bottom = Color.HSVToColor(floatArrayOf((hue + 44f) % 360f, 0.62f, 0.72f))
        paint.shader = LinearGradient(
            0f, 0f, size.toFloat(), size.toFloat(),
            intArrayOf(top, bottom),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        paint.shader = null
        paint.color = Color.HSVToColor(90, floatArrayOf((hue + 170f) % 360f, 0.45f, 0.95f))
        repeat(5) { index ->
            val radius = size * (0.12f + index * 0.055f)
            val cx = size * (0.30f + 0.09f * ((index + seed) % 4))
            val cy = size * (0.62f - 0.07f * index)
            canvas.drawCircle(cx, cy, radius, paint)
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = Color.argb(110, 255, 255, 255)
        repeat(9) { index ->
            val y = size * (0.12f + index * 0.085f)
            canvas.drawLine(size * 0.08f, y, size * 0.92f, y, paint)
        }

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(210, 20, 20, 24)
        canvas.drawCircle(size * 0.80f, size * 0.20f, size * 0.045f, paint)
        paint.color = Color.argb(220, 245, 230, 200)
        canvas.drawCircle(size * 0.80f, size * 0.20f, size * 0.018f, paint)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        bitmap.recycle()
    }

    private fun DataOutputStream.writeIntLe(value: Int) {
        write(byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte(),
        ))
    }

    private fun DataOutputStream.writeShortLe(value: Int) {
        write(byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
        ))
    }

    private data class DemoTrackSpec(
        val id: String,
        val title: String,
        val fileName: String,
        val artFileName: String,
        val hue: Float,
        val seed: Int,
        val lyrics: String,
    )

    private companion object {
        const val DEMO_SECONDS = 30

        val LYRICS_AURORA = """
            [ar:Gecko Studio]
            [al:Amostras Gecko]
            [ti:Aurora de Vidro]
            [00:00.00]♪
            [00:02.00]Luz que atravessa o vidro
            [00:05.20]pinta a manhã de marfim
            [00:08.40]tudo que era silêncio
            [00:11.60]aprende a cantar por mim
            [00:15.00]Se o mundo gira devagar
            [00:18.20]eu deixo o tempo passar
            [00:21.40]aurora de vidro no ar
            [00:24.60]um novo dia pra começar
            [00:27.80]♪
        """.trimIndent()

        val LYRICS_IMA = """
            [ar:Gecko Studio]
            [al:Amostras Gecko]
            [ti:Ímã Lunar]
            [00:00.00]♪
            [00:02.20]A noite puxa o meu nome
            [00:05.60]como um ímã lunar
            [00:09.00]cada batida responde
            [00:12.40]ao que eu quero alcançar
            [00:15.80]Baixa a maré, sobe o som
            [00:19.20]deixa o silêncio no chão
            [00:22.60]ímã lunar, coração
            [00:26.00]girando na escuridão
            [00:28.60]♪
        """.trimIndent()
    }
}
