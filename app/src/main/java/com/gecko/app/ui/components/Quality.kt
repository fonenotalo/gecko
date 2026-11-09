package com.gecko.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gecko.app.model.AudioQuality
import com.gecko.app.model.Track
import com.gecko.app.playback.AudioQualityInfo
import com.gecko.app.util.formatSampleRate

@Composable
fun QualityBadge(
    quality: AudioQualityInfo?,
    modifier: Modifier = Modifier,
    label: Boolean = false,
) {
    if (quality == null) return
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accent = when {
        quality.isHiRes -> if (dark) Color(0xFFE8C27D) else Color(0xFF6E4C02)
        quality.isLossless -> if (dark) Color(0xFF8FE3A0) else Color(0xFF1F6B31)
        (quality.bitrateKbps ?: 0) >= 256 ->
            if (dark) Color(0xFF9EC8FF) else Color(0xFF1958A8)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(accent.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.GraphicEq,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(11.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (label) quality.label else quality.badge,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 0.5.sp,
            ),
            color = accent,
        )
    }
}

@Composable
fun StaticQualityPill(text: String, modifier: Modifier = Modifier, lossless: Boolean = false) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accent = when {
        !lossless -> MaterialTheme.colorScheme.onSurfaceVariant
        dark -> Color(0xFF8FE3A0)
        else -> Color(0xFF1F6B31)
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = accent,
        )
    }
}

@Composable
fun QualityDetails(
    track: Track,
    quality: AudioQualityInfo?,
    preferred: AudioQuality,
    losslessFirst: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = quality?.label ?: "Analisando fluxo…",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (track.isDownloaded) "Arquivo baixado • ${track.source.label}" else track.source.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (quality?.isLossless == true) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        DetailRow("Codec", quality?.codecLabel ?: "—")
        DetailRow("Taxa de amostragem", formatSampleRate(quality?.sampleRateHz) ?: "—")
        DetailRow("Profundidade", quality?.bitsPerSample?.let { "$it-bit" } ?: "—")
        DetailRow("Canais", quality?.channelText ?: "—")
        DetailRow("Bitrate", quality?.bitrateText ?: "—")

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip("Preferência: ${preferred.title}")
            if (losslessFirst) InfoChip("Lossless primeiro")
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Esta é a qualidade que o decoder está entregando agora, lida diretamente do stream. " +
                "O selo lossless significa que nenhuma compressão com perdas foi aplicada nesta reprodução.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun InfoChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
    )
}
