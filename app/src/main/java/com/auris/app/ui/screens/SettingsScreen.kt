package com.auris.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auris.app.BuildConfig
import com.auris.app.model.AppSettings
import com.auris.app.model.ThemeMode
import com.auris.app.model.ThemeStyle
import com.auris.app.sources.SourcePlugin
import com.auris.app.ui.BridgeStatus
import com.auris.app.ui.components.AurisCard
import com.auris.app.ui.components.GroupDivider
import com.auris.app.ui.components.InfoBanner
import com.auris.app.ui.components.QualityPickerDialog
import com.auris.app.ui.components.SettingSwitchRow
import com.auris.app.ui.components.SettingValueRow
import com.auris.app.ui.components.SettingsGroup

@Composable
fun SettingsScreen(
    settings: AppSettings,
    plugins: List<SourcePlugin>,
    bridgeStatus: BridgeStatus,
    hasDemo: Boolean,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onImportPlugin: (android.net.Uri) -> Unit,
    onRemovePlugin: (SourcePlugin) -> Unit,
    onTestYouTubeMusic: () -> Unit,
    onTestLastFm: () -> Unit,
    onGenerateDemo: () -> Unit,
    onRemoveDemo: () -> Unit,
) {
    var showTheme by remember { mutableStateOf(false) }
    var showStyle by remember { mutableStateOf(false) }
    var showPreferredQuality by remember { mutableStateOf(false) }
    var showDownloadQuality by remember { mutableStateOf(false) }
    var confirmRemoveDemo by remember { mutableStateOf(false) }

    val pluginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onImportPlugin) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Column {
                Text(
                    text = "Ajustes",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Sua música, suas regras. Nada sai do aparelho sem você pedir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SettingsGroup(title = "Aparência") {
                SettingValueRow(
                    title = "Tema",
                    value = when (settings.themeMode) {
                        ThemeMode.SYSTEM -> "Automático"
                        ThemeMode.LIGHT -> "Claro"
                        ThemeMode.DARK -> "Escuro"
                    },
                    subtitle = "Claro, escuro ou seguindo o sistema",
                    onClick = { showTheme = true },
                )
                GroupDivider()
                SettingValueRow(
                    title = "Estilo visual",
                    value = if (settings.themeStyle == ThemeStyle.MODERN) {
                        "Moderno"
                    } else {
                        "iPod clássico"
                    },
                    subtitle = if (settings.themeStyle == ThemeStyle.MODERN) {
                        "Cantos suaves, cores vivas e capa em destaque"
                    } else {
                        "Visual retrô do iPod, com fonte monoespaçada"
                    },
                    onClick = { showStyle = true },
                )
                GroupDivider()
                SettingSwitchRow(
                    title = "Cores da arte do álbum",
                    subtitle = "O tema inteiro acompanha as cores da capa em reprodução",
                    checked = settings.colorFromArtwork,
                    onCheckedChange = { value ->
                        onUpdate { it.copy(colorFromArtwork = value) }
                    },
                )
            }
        }

        item {
            SettingsGroup(title = "Áudio") {
                SettingValueRow(
                    title = "Qualidade preferida",
                    value = settings.preferredQuality.title,
                    subtitle = settings.preferredQuality.subtitle,
                    onClick = { showPreferredQuality = true },
                )
                GroupDivider()
                SettingSwitchRow(
                    title = "Lossless primeiro",
                    subtitle = "Pede FLAC, ALAC ou WAV sempre que a fonte permitir",
                    checked = settings.losslessFirst,
                    onCheckedChange = { value ->
                        onUpdate { it.copy(losslessFirst = value) }
                    },
                )
                GroupDivider()
                SettingSwitchRow(
                    title = "Reproduzir online",
                    subtitle = "Usa a rede para tocar das fontes conectadas. Desligado, toca só o que está offline.",
                    checked = settings.streamWhenOnline,
                    onCheckedChange = { value ->
                        onUpdate { it.copy(streamWhenOnline = value) }
                    },
                )
                GroupDivider()
                Box(Modifier.padding(14.dp)) {
                    InfoBanner(
                        text = "A qualidade exibida no player vem do decoder, não do nome do arquivo. " +
                            "Se a fonte entregar algo abaixo do pedido, o Auris mostra o valor real.",
                        tone = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }

        item {
            SettingsGroup(title = "Downloads") {
                SettingValueRow(
                    title = "Qualidade do download",
                    value = settings.downloadQuality.title,
                    subtitle = "Aplicada a novos downloads das fontes remotas",
                    onClick = { showDownloadQuality = true },
                )
                GroupDivider()
                SettingSwitchRow(
                    title = "Downloads inteligentes",
                    subtitle = "Baixa automaticamente as próximas faixas da fila",
                    checked = settings.smartDownloads,
                    onCheckedChange = { value ->
                        onUpdate { it.copy(smartDownloads = value) }
                    },
                )
                GroupDivider()
                SettingSwitchRow(
                    title = "Somente no Wi-Fi",
                    subtitle = "Evita gastar seus dados móveis",
                    checked = settings.wifiOnlyDownloads,
                    onCheckedChange = { value ->
                        onUpdate { it.copy(wifiOnlyDownloads = value) }
                    },
                )
                GroupDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = "Os downloads ficam no armazenamento privado do app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            SettingsGroup(title = "YouTube Music") {
                Box(Modifier.padding(14.dp)) {
                    InfoBanner(
                        text = "Integração opcional. O Auris não faz scraping nem contorna DRM: " +
                            "a reprodução vem de uma ponte auto-hospedada que você controla, com " +
                            "conteúdo autorizado na sua região.",
                        tone = MaterialTheme.colorScheme.primary,
                    )
                }
                SettingTextField(
                    title = "URL da ponte",
                    value = settings.ytMusicUrl,
                    placeholder = "http://127.0.0.1:8080",
                    onChange = { value ->
                        onUpdate { it.copy(ytMusicUrl = value.trim()) }
                    },
                    helper = "Exemplo de contrato: /api/health, /api/search e /api/stream.",
                )
                SettingTextField(
                    title = "Token de acesso (opcional)",
                    value = settings.ytMusicToken,
                    placeholder = "Enviado como Bearer e X-API-Key",
                    onChange = { value ->
                        onUpdate { it.copy(ytMusicToken = value.trim()) }
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        Text(
                            text = bridgeStatus.message ?: "Conexão ainda não testada.",
                            style = MaterialTheme.typography.bodySmall,
                            color = when (bridgeStatus.reachable) {
                                true -> MaterialTheme.colorScheme.primary
                                false -> MaterialTheme.colorScheme.error
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    if (bridgeStatus.checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        TextButton(
                            onClick = onTestYouTubeMusic,
                            enabled = settings.ytMusicUrl.isNotBlank(),
                        ) {
                            Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Testar")
                        }
                    }
                }
            }
        }

        item {
            SettingsGroup(title = "Complementos lossless") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Fontes adicionais",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Manifestos JSON com catálogo e stream lossless. Nenhum código é executado.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = {
                            pluginLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Importar")
                    }
                }
                if (plugins.isEmpty()) {
                    GroupDivider()
                    Text(
                        text = "Nenhum complemento instalado.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    )
                } else {
                    plugins.forEach { plugin ->
                        GroupDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Extension,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = plugin.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "v${plugin.version}  •  ${plugin.baseUrl}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { onRemovePlugin(plugin) }) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = "Remover ${plugin.name}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            SettingsGroup(title = "Last.fm") {
                SettingSwitchRow(
                    title = "Scrobbling",
                    subtitle = "Envia as músicas ouvidas para o Last.fm",
                    checked = settings.scrobbleEnabled,
                    onCheckedChange = { value ->
                        onUpdate { it.copy(scrobbleEnabled = value) }
                    },
                )
                GroupDivider()
                SettingTextField(
                    title = "API key",
                    value = settings.lastFmApiKey,
                    placeholder = "Sua chave de API",
                    onChange = { value ->
                        onUpdate { it.copy(lastFmApiKey = value.trim()) }
                    },
                )
                SettingTextField(
                    title = "API secret",
                    value = settings.lastFmApiSecret,
                    placeholder = "Seu secret de API",
                    onChange = { value ->
                        onUpdate { it.copy(lastFmApiSecret = value.trim()) }
                    },
                )
                SettingTextField(
                    title = "Session key",
                    value = settings.lastFmSessionKey,
                    placeholder = "Sessão obtida no seu perfil Last.fm",
                    onChange = { value ->
                        onUpdate { it.copy(lastFmSessionKey = value.trim()) }
                    },
                    helper = "O Auris nunca pede sua senha. Use a session key gerada pela API do Last.fm.",
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onTestLastFm) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Validar sessão")
                    }
                }
            }
        }

        item {
            SettingsGroup(title = "Discord") {
                SettingSwitchRow(
                    title = "Presença no Discord",
                    subtitle = "Mostra o que você está ouvindo no seu perfil",
                    checked = settings.discordEnabled,
                    onCheckedChange = { value ->
                        onUpdate { it.copy(discordEnabled = value) }
                    },
                )
                GroupDivider()
                SettingTextField(
                    title = "URL da bridge",
                    value = settings.discordBridgeUrl,
                    placeholder = "http://192.168.0.10:6463/presence",
                    onChange = { value ->
                        onUpdate { it.copy(discordBridgeUrl = value.trim()) }
                    },
                )
                Box(Modifier.padding(14.dp)) {
                    InfoBanner(
                        text = "O Discord não expõe Rich Presence para apps Android de terceiros. " +
                            "O Auris envia a faixa atual para uma bridge que você mantém em um computador " +
                            "com o Discord conectado.",
                        tone = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }

        item {
            SettingsGroup(title = "Demonstração") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.PlayCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (hasDemo) {
                                "Demonstração ativa"
                            } else {
                                "Ouvir sem arquivos locais"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (hasDemo) {
                                "Duas faixas em WAV lossless com capa e letra sincronizada."
                            } else {
                                "Cria duas faixas em WAV lossless, com capa e letra sincronizada."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    if (hasDemo) {
                        TextButton(onClick = { confirmRemoveDemo = true }) {
                            Text("Remover", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Button(onClick = onGenerateDemo) { Text("Gerar") }
                    }
                }
            }
        }

        item {
            AurisCard {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Headphones,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(11.dp))
                        Column {
                            Text(
                                text = "Auris ${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Lossless primeiro  •  sem anúncios  •  sem conta",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Auris não hospeda catálogos. Fontes online dependem das pontes e " +
                            "complementos que você configura, sempre respeitando os termos de uso e " +
                            "a legislação da sua região. Suas músicas locais nunca são enviadas " +
                            "para servidores.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Text(
                text = "Feito para ouvir com calma.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }

    if (showTheme) {
        ChoiceDialog(
            title = "Tema",
            options = listOf(
                "Automático" to "Segue o sistema",
                "Claro" to "Sempre claro",
                "Escuro" to "Sempre escuro",
            ),
            selectedIndex = settings.themeMode.ordinal,
            onSelect = { index ->
                onUpdate { it.copy(themeMode = ThemeMode.entries[index]) }
                showTheme = false
            },
            onDismiss = { showTheme = false },
        )
    }

    if (showStyle) {
        ChoiceDialog(
            title = "Estilo visual",
            options = listOf(
                "Moderno" to "Cantos suaves e cores da capa",
                "iPod clássico" to "Visual retrô com fonte monoespaçada",
            ),
            selectedIndex = if (settings.themeStyle == ThemeStyle.MODERN) 0 else 1,
            onSelect = { index ->
                onUpdate {
                    it.copy(
                        themeStyle = if (index == 0) ThemeStyle.MODERN else ThemeStyle.CLASSIC_IPOD,
                    )
                }
                showStyle = false
            },
            onDismiss = { showStyle = false },
        )
    }

    if (showPreferredQuality) {
        QualityPickerDialog(
            current = settings.preferredQuality,
            title = "Qualidade preferida",
            onSelect = { quality ->
                onUpdate { it.copy(preferredQuality = quality) }
            },
            onDismiss = { showPreferredQuality = false },
        )
    }

    if (showDownloadQuality) {
        QualityPickerDialog(
            current = settings.downloadQuality,
            title = "Qualidade do download",
            onSelect = { quality ->
                onUpdate { it.copy(downloadQuality = quality) }
            },
            onDismiss = { showDownloadQuality = false },
        )
    }

    if (confirmRemoveDemo) {
        AlertDialog(
            onDismissRequest = { confirmRemoveDemo = false },
            title = { Text("Remover demonstração?") },
            text = {
                Text("As duas faixas de exemplo, capas e letras serão apagadas do aparelho.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemoveDemo = false
                        onRemoveDemo()
                    },
                ) {
                    Text("Remover", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveDemo = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun SettingTextField(
    title: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    helper: String? = null,
) {
    var text by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (value != text) text = value
    }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 7.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { updated ->
                text = updated
                onChange(updated)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            label = { Text(title) },
            placeholder = { Text(placeholder) },
        )
        if (helper != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = helper,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelect(index) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = option.first,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (index == selectedIndex) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                },
                            )
                            Text(
                                text = option.second,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (index == selectedIndex) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = "Selecionado",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar") }
        },
    )
}
