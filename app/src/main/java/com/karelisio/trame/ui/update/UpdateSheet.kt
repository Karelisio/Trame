package com.karelisio.trame.ui.update

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.trame.BuildConfig
import com.karelisio.trame.ui.components.AnimatedNumber

/** Bottom sheet de mise à jour : changelog, téléchargement avec progression animée, installation. */
@Composable
fun UpdateSheet(vm: UpdateViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    if (ui == UpdateUi.Hidden) return
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = vm::dismiss, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val s = ui) {
                UpdateUi.UpToDate -> {
                    Text("Trame est à jour", style = MaterialTheme.typography.headlineSmall)
                    Text("Version ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = vm::dismiss, modifier = Modifier.align(Alignment.End)) { Text("OK") }
                }
                is UpdateUi.Failed -> {
                    Text(s.message, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    Row(Modifier.align(Alignment.End)) {
                        TextButton(onClick = vm::dismiss) { Text("Fermer") }
                        s.update?.let { u -> Button(onClick = { vm.download(u) }) { Text("Réessayer") } }
                    }
                }
                is UpdateUi.Available, is UpdateUi.Downloading, is UpdateUi.Ready -> {
                    val update = when (s) {
                        is UpdateUi.Available -> s.update
                        is UpdateUi.Downloading -> s.update
                        is UpdateUi.Ready -> s.update
                        else -> return@Column
                    }
                    Text("Nouvelle version ${update.version}", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Tu as la ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (update.changelog.isNotBlank()) {
                        Text(
                            changelog(update.changelog),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                        )
                    }
                    AnimatedContent(
                        targetState = s::class,
                        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith fadeOut() },
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth(),
                        label = "update",
                    ) { kind ->
                        when (kind) {
                            UpdateUi.Downloading::class -> DownloadProgress((ui as? UpdateUi.Downloading)?.progress ?: 1f)
                            UpdateUi.Ready::class -> Button(
                                onClick = { (ui as? UpdateUi.Ready)?.let { vm.install(it.file) } },
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Installer", style = MaterialTheme.typography.titleMedium) }
                            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = vm::dismiss) { Text("Plus tard") }
                                Button(onClick = { vm.download(update) }, shapes = ButtonDefaults.shapes()) {
                                    Text("Mettre à jour" + if (update.size > 0) " (${update.size / 1_048_576} Mo)" else "")
                                }
                            }
                        }
                    }
                }
                UpdateUi.Hidden -> Unit
            }
        }
    }
}

@Composable
private fun DownloadProgress(target: Float) {
    val p by animateFloatAsState(target, label = "dl")
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CircularWavyProgressIndicator(progress = { p }, modifier = Modifier.size(120.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedNumber((target * 100).toLong(), MaterialTheme.typography.headlineMedium)
            Text("%", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Rendu minimal du Markdown des releases : titres et puces. */
private fun changelog(md: String) = buildAnnotatedString {
    md.lines().forEach { raw ->
        val line = raw.trimEnd()
        when {
            line.startsWith("#") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(line.trimStart('#', ' ')) }
            line.startsWith("- ") || line.startsWith("* ") -> append("•  ").also { append(line.drop(2)) }
            else -> append(line)
        }
        append('\n')
    }
}
