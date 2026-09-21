package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.PreferencesManager
import com.example.voice.lang.LanguagePackStatus
import com.example.voice.lang.OfflineLanguagePack

data class Country(
    val id: String,
    val name: String,
    val flag: String
)

data class Language(
    val code: String,
    val nativeName: String,
    val flag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelection(
    preferencesManager: PreferencesManager,
    modifier: Modifier = Modifier
) {
    val voiceLanguage by preferencesManager.voiceLanguage.collectAsState()
    val learningLanguage by preferencesManager.learningLanguage.collectAsState()

    // Supported countries for Primary Language selection
    val countries = remember {
        listOf(
            Country("US", "United States", "🇺🇸"),
            Country("IN", "India", "🇮🇳"),
            Country("ES", "Spain", "🇪🇸"),
            Country("JP", "Japan", "🇯🇵"),
            Country("RU", "Russia", "🇷🇺"),
            Country("FR", "France", "🇫🇷"),
            Country("DE", "Germany", "🇩🇪"),
            Country("CN", "China", "🇨🇳"),
            Country("KR", "South Korea", "🇰🇷"),
            Country("PK", "Pakistan", "🇵🇰"),
            Country("NP", "Nepal", "🇳🇵"),
            Country("TH", "Thailand", "🇹🇭")
        )
    }

    // Supported languages grouped by Country code
    val countryLanguages = remember {
        mapOf(
            "US" to listOf(
                Language("en-US", "English (United States)", "🇺🇸")
            ),
            "IN" to listOf(
                Language("hi-IN", "Hindi (हिन्दी)", "🇮🇳"),
                Language("en-IN", "English (India)", "🇮🇳"),
                Language("bn-IN", "Bengali (বাংলা)", "🇮🇳"),
                Language("ta-IN", "Tamil (தமிழ்)", "🇮🇳"),
                Language("te-IN", "Telugu (తెలుగు)", "🇮🇳"),
                Language("ml-IN", "Malayalam (മലയാളം)", "🇮🇳"),
                Language("as-IN", "Assamese (অসমীया)", "🇮🇳"),
                Language("pa-IN", "Punjabi (ਪੰਜਾਬੀ)", "🇮🇳"),
                Language("gu-IN", "Gujarati (ગુજરાતી)", "🇮🇳")
            ),
            "ES" to listOf(
                Language("es-ES", "Spanish (Español)", "🇪🇸")
            ),
            "JP" to listOf(
                Language("ja-JP", "Japanese (日本語)", "🇯🇵")
            ),
            "RU" to listOf(
                Language("ru-RU", "Russian (Русский)", "🇷🇺")
            ),
            "FR" to listOf(
                Language("fr-FR", "French (Français)", "🇫🇷")
            ),
            "DE" to listOf(
                Language("de-DE", "German (Deutsch)", "🇩🇪")
            ),
            "CN" to listOf(
                Language("zh-CN", "Chinese (中文)", "🇨🇳")
            ),
            "KR" to listOf(
                Language("ko-KR", "Korean (한국어)", "🇰🇷")
            ),
            "PK" to listOf(
                Language("ur-PK", "Urdu (اردو)", "🇵🇰")
            ),
            "NP" to listOf(
                Language("ne-NP", "Nepali (नेपाली)", "🇳🇵")
            ),
            "TH" to listOf(
                Language("th-TH", "Thai (ไทย)", "🇹🇭")
            )
        )
    }

    // Extract selected country from current voiceLanguage code
    val selectedCountryId = remember(voiceLanguage) {
        val parts = voiceLanguage.split("-")
        if (parts.size > 1) parts[1].uppercase() else "US"
    }

    val selectedCountry = remember(selectedCountryId) {
        countries.find { it.id == selectedCountryId } ?: countries.first()
    }

    // Filter secondary languages dynamically based on selected country
    val availableLanguages = remember(selectedCountryId) {
        countryLanguages[selectedCountryId] ?: countryLanguages["US"] ?: emptyList()
    }

    val currentLanguage = remember(learningLanguage, availableLanguages) {
        availableLanguages.find { it.code == learningLanguage } ?: availableLanguages.firstOrNull() ?: Language("en-US", "English (United States)", "🇺🇸")
    }

    var primaryExpanded by remember { mutableStateOf(false) }
    var secondaryExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Language Selection Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "First, select your Country in Primary Language. Then, select the spoken native language in Secondary Language to configure Alya's voice engine.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Primary Language: Country Dropdown
            ExposedDropdownMenuBox(
                expanded = primaryExpanded,
                onExpandedChange = { primaryExpanded = !primaryExpanded }
            ) {
                OutlinedTextField(
                    value = "${selectedCountry.flag} ${selectedCountry.name}",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Primary Language (Country)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = primaryExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
                ExposedDropdownMenu(
                    expanded = primaryExpanded,
                    onDismissRequest = { primaryExpanded = false }
                ) {
                    countries.forEach { country ->
                        val isSelected = country.id == selectedCountryId
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${country.flag} ${country.name}",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                val defaultLangs = countryLanguages[country.id] ?: countryLanguages["US"] ?: emptyList()
                                val defaultLang = defaultLangs.first().code
                                preferencesManager.setVoiceLanguage(defaultLang)
                                preferencesManager.setLearningLanguage(defaultLang)
                                primaryExpanded = false
                            }
                        )
                    }
                }
            }

            // Secondary Language: Native Spoken Language Dropdown
            ExposedDropdownMenuBox(
                expanded = secondaryExpanded,
                onExpandedChange = { secondaryExpanded = !secondaryExpanded }
            ) {
                OutlinedTextField(
                    value = "${currentLanguage.flag} ${currentLanguage.nativeName}",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Secondary Language (Spoken)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = secondaryExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
                ExposedDropdownMenu(
                    expanded = secondaryExpanded,
                    onDismissRequest = { secondaryExpanded = false }
                ) {
                    availableLanguages.forEach { language ->
                        val isSelected = language.code == learningLanguage
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${language.flag} ${language.nativeName}",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                preferencesManager.setVoiceLanguage(language.code)
                                preferencesManager.setLearningLanguage(language.code)
                                secondaryExpanded = false
                            }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Manual Download Offline Language Packs
            OfflineLanguagePacksSection()
        }
    }
}

@Composable
fun OfflineLanguagePacksSection(
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val offlineManager = remember { com.example.voice.lang.OfflineLanguageManager.getInstance(context) }
    val packs by offlineManager.languagePacks.collectAsState()
    val isNetworkAvailable by offlineManager.isNetworkAvailable.collectAsState()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text = "Offline Language Packs",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "English is default and pre-installed. Download additional languages for offline speech recognition.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!isNetworkAvailable) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Offline Mode: Pre-installed English and downloaded packs are fully available offline.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        packs.forEach { pack ->
            OfflinePackRow(
                pack = pack,
                onDownload = { offlineManager.startDownload(pack.id) },
                onPause = { offlineManager.pauseDownload(pack.id) },
                onResume = { offlineManager.resumeDownload(pack.id) },
                onRemove = { offlineManager.deleteLanguagePack(pack.id) }
            )
        }

        OutlinedButton(
            onClick = { offlineManager.openSystemTtsInstall(context) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SettingsVoice,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("System Voice Data & Speech Settings", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun OfflinePackRow(
    pack: OfflineLanguagePack,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = pack.flag, style = MaterialTheme.typography.titleMedium)
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = pack.nativeName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (pack.isDefault) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "DEFAULT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (pack.status == LanguagePackStatus.PREINSTALLED) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = "PRE-INSTALLED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${pack.name} • ${pack.sizeFormatted}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // Status & Action button
                when (pack.status) {
                    LanguagePackStatus.NOT_DOWNLOADED -> {
                        Button(
                            onClick = onDownload,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    LanguagePackStatus.DOWNLOADING -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onPause) {
                                Icon(Icons.Default.Pause, contentDescription = "Pause", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    LanguagePackStatus.PAUSED -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onResume) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = onRemove) {
                                Icon(Icons.Default.Delete, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    LanguagePackStatus.INSTALLED, LanguagePackStatus.PREINSTALLED -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Installed",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            if (pack.status != LanguagePackStatus.PREINSTALLED) {
                                IconButton(
                                    onClick = onRemove,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove pack",
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (pack.status == LanguagePackStatus.DOWNLOADING || pack.status == LanguagePackStatus.PAUSED) {
                LinearProgressIndicator(
                    progress = { pack.downloadProgress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                )
                Text(
                    text = if (pack.status == LanguagePackStatus.PAUSED) "Paused" else "Downloading: ${(pack.downloadProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
