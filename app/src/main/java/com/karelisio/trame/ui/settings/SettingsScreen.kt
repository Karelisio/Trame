package com.karelisio.trame.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.trame.data.repo.ChapterLanguage
import com.karelisio.trame.data.repo.MangaRepository
import com.karelisio.trame.data.repo.SettingsRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(settings: SettingsRepository, repo: MangaRepository, onBack: () -> Unit) {
    val lang by settings.language.collectAsStateWithLifecycle(ChapterLanguage.EN)
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Réglages") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
        ) {
            Text("Langue des chapitres", style = MaterialTheme.typography.titleMedium)
            Text(
                "Utilisée pour la liste des chapitres et la détection des sorties.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ChapterLanguage.entries.forEachIndexed { i, l ->
                    SegmentedButton(
                        selected = l == lang,
                        onClick = {
                            scope.launch {
                                settings.setLanguage(l)
                                repo.refreshAll()
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, ChapterLanguage.entries.size),
                    ) { Text(l.label) }
                }
            }
            Text(
                "Sources : MangaDex (api.mangadex.org), MangaUpdates en secours. Lecture officielle via MANGA Plus.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 32.dp),
            )
        }
    }
}
