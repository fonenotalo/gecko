package com.auris.app.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import com.auris.app.util.formatSampleRate
import kotlin.math.roundToInt

/**
 * Quality information read from the actual decoder output. Never inferred from a
 * file name, so what the player shows is what the player is decoding.
 */
data class AudioQualityInfo(
    val codecLabel: String,
    val codec: String?,
    val sampleRateHz: Int?,
    val bitrateKbps: Int?,
    val channels: Int?,
    val bitsPerSample: Int?,
    val isLossless: Boolean,
    val isHiRes: Boolean,
) {
    val label: String
        get() = when {
            isHiRes -> "Hi-Res Lossless"
            isLossless -> "Lossless"
            (bitrateKbps ?: 0) >= 256 -> "Alta"
            (bitrateKbps ?: 0) > 0 -> "Padrão"
            else -> "Áudio"
        }

    val badge: String
        get() = when {
            isHiRes -> "HI-RES"
            isLossless -> "LOSSLESS"
            (bitrateKbps ?: 0) > 0 -> "${bitrateKbps}k"
            codecLabel.isNotBlank() -> codecLabel
            else -> "AUDIO"
        }

    val bitrateText: String?
        get() = when {
            bitrateKbps == null || bitrateKbps <= 0 -> null
            isLossless -> "%.1f Mbps".format(bitrateKbps / 1000.0)
            else -> "$bitrateKbps kbps"
        }

    val channelText: String?
        get() = when (channels) {
            null, 0 -> null
            1 -> "Mono"
            2 -> "Estéreo"
            else -> "$channels canais"
        }

    val detail: String
        get() = listOfNotNull(
            codecLabel.takeIf { it.isNotBlank() },
            bitsPerSample?.let { "$it-bit" },
            formatSampleRate(sampleRateHz),
            channelText,
            bitrateText,
        ).joinToString(" • ")

    val technicalDetail: String
        get() = listOfNotNull(
            codec,
            if (isLossless) "sem perdas" else "com perdas",
        ).joinToString(" • ")

    companion object {
        private val losslessMimes = setOf(
            "audio/flac",
            "audio/x-flac",
            "audio/wav",
            "audio/x-wav",
            "audio/wave",
            "audio/raw",
            "audio/alac",
            "audio/x-alac",
            "audio/x-ape",
        )

        private val losslessCodecs = setOf(
            "flac",
            "alac",
            "wav",
            "lpcm",
            "pcm_s16le",
            "pcm_s24le",
            "pcm_s32le",
            "pcm_s16be",
            "pcm_s24be",
            "pcm_s32be",
            "pcm_f32le",
            "pcm_f64le",
        )

        fun fromFormat(format: Format): AudioQualityInfo {
            val codecRaw = format.codecs
            val mime = format.sampleMimeType?.lowercase()
            val codec = codecRaw?.lowercase()
            val lossless = (mime != null && mime in losslessMimes) ||
                (codec != null && (codec in losslessCodecs || codec.startsWith("pcm_")))
            val channels = format.channelCount.takeIf { it > 0 }
            val sampleRate = format.sampleRate.takeIf { it > 0 }

            val pcmBits = when (format.pcmEncoding) {
                C.ENCODING_PCM_8BIT -> 8
                C.ENCODING_PCM_16BIT -> 16
                C.ENCODING_PCM_24BIT -> 24
                C.ENCODING_PCM_32BIT -> 32
                C.ENCODING_PCM_FLOAT -> 32
                else -> null
            }

            var bits = pcmBits
            if (bits == null && lossless) {
                val estimate = if (format.bitrate > 0 && sampleRate != null && channels != null) {
                    format.bitrate.toDouble() / (sampleRate * channels)
                } else {
                    null
                }
                bits = estimate?.roundToInt()?.let { value ->
                    when {
                        value in 14..18 -> 16
                        value in 22..26 -> 24
                        value in 30..34 -> 32
                        else -> null
                    }
                }
            }

            var bitrate = format.bitrate.takeIf { it > 0 }
            if (bitrate == null && lossless && sampleRate != null && channels != null && bits != null) {
                bitrate = sampleRate * channels * bits
            }
            val bitrateKbps = bitrate?.let { (it / 1000.0).roundToInt() }?.takeIf { it > 0 }

            val codecLabel = codecLabel(codecRaw, mime)
            val hiRes = lossless && (
                (bits != null && bits >= 24) ||
                    (sampleRate != null && sampleRate >= 88_200) ||
                    (bitrateKbps != null && bitrateKbps >= 1_411)
                )

            return AudioQualityInfo(
                codecLabel = codecLabel,
                codec = codecRaw,
                sampleRateHz = sampleRate,
                bitrateKbps = bitrateKbps,
                channels = channels,
                bitsPerSample = bits,
                isLossless = lossless,
                isHiRes = hiRes,
            )
        }

        private fun codecLabel(codec: String?, mime: String?): String {
            val value = (codec ?: mime).orEmpty().lowercase()
            return when {
                value.contains("flac") -> "FLAC"
                value.contains("alac") -> "ALAC"
                value.startsWith("pcm") || value == "audio/raw" -> "PCM"
                value.contains("opus") -> "Opus"
                value.contains("vorbis") || value.contains("ogg") -> "Vorbis"
                value.contains("aac") || value.contains("mp4a") -> "AAC"
                value.contains("mp3") || value.contains("mpeg") -> "MP3"
                value.contains("ac3") -> "AC-3"
                value.contains("eac3") -> "E-AC-3"
                value.contains("dts") -> "DTS"
                value.isBlank() -> "Áudio"
                else -> codec?.uppercase() ?: mime?.substringAfter('/')?.uppercase().orEmpty()
            }
        }
    }
}
