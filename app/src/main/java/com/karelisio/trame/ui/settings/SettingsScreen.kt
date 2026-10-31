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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.platform.LocalContext
import com.karelisio.trame.work.CheckWorker
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
fun SettingsScreen(settings: SettingsRepository, repo: MangaRepository, onBack: () -> Unit, onPermissions: () -> Unit, onCheckUpdate: () -> Unit) {
    val lang by settings.language.collectAsStateWithLifecycle(ChapterLanguage.EN)
    val interval by settings.checkIntervalHours.collectAsStateWithLifecycle(2)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
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
            Text("Vérification des nouveautés", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 28.dp))
            Text(
                "Toutes les $interval h, en arrière-plan, dès qu'une connexion est disponible.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                (1..3).forEach { h ->
                    SegmentedButton(
                        selected = h == interval,
                        onClick = { scope.launch { settings.setCheckInterval(h) } },
                        shape = SegmentedButtonDefaults.itemShape(h - 1, 3),
                    ) { Text("$h h") }
                }
            }
            FilledTonalButton(
                onClick = { CheckWorker.runNow(context) },
                modifier = Modifier.padding(top = 12.dp),
            ) { Text("Vérifier maintenant") }
            Text("Système", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 28.dp, bottom = 8.dp))
            FilledTonalButton(onClick = onPermissions) { Text("Autorisations") }
            FilledTonalButton(onClick = onCheckUpdate, modifier = Modifier.padding(top = 8.dp)) { Text("Rechercher une mise à jour") }
            Text(
                "Version ${com.karelisio.trame.BuildConfig.VERSION_NAME} (${com.karelisio.trame.BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "Sources : MangaDex (api.mangadex.org), MangaUpdates en secours. Lecture officielle via MANGA Plus.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 32.dp),
            )
        }
    }
}
