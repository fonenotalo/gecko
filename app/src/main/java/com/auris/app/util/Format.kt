package com.auris.app.util

import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

fun formatTotalDuration(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    return if (minutes >= 60) {
        val hours = minutes / 60
        "%.1f h".format(Locale.US, hours.toDouble())
    } else {
        "$minutes min"
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) {
        "%.1f GB".format(Locale.US, mb / 1024.0)
    } else if (mb >= 10) {
        "%.0f MB".format(Locale.US, mb)
    } else {
        "%.1f MB".format(Locale.US, mb)
    }
}

fun md5Hex(value: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

fun safeFileId(value: String): String =
    value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(96)

fun formatSampleRate(hz: Int?): String? = when {
    hz == null || hz <= 0 -> null
    hz % 1000 == 0 -> "${hz / 1000} kHz"
    else -> "%.1f kHz".format(Locale.US, hz / 1000.0)
}

fun greetingForHour(hour: Int): String = when {
    hour < 5 -> "Boa madrugada"
    hour < 12 -> "Bom dia"
    hour < 18 -> "Boa tarde"
    else -> "Boa noite"
}
