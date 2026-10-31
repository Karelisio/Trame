package com.karelisio.trame.ui.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.karelisio.trame.permissions.AppPermission
import com.karelisio.trame.permissions.Permissions
import com.karelisio.trame.ui.components.cascadeIn
import com.karelisio.trame.ui.onboarding.GrantButton
import com.karelisio.trame.ui.onboarding.icon
import com.karelisio.trame.ui.onboarding.rememberPermissionRequester
import com.karelisio.trame.ui.onboarding.rememberPermissionStates

/** Réglages > Autorisations : état en direct, revérifié au retour des réglages système. */
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val states = rememberPermissionStates()
    val request = rememberPermissionRequester()
    val context = LocalContext.current
    Scaffold(
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Autorisations") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppPermission.entries.forEachIndexed { i, p ->
                val granted = states[p] == true
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().cascadeIn(i),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(48.dp)
                                    .clip((if (granted) MaterialShapes.Sunny else MaterialShapes.Cookie6Sided).toShape())
                                    .background(if (granted) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer),
                                contentAlignment = Alignment.Center,
                            ) { Icon(p.icon, null) }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (p.required) "Obligatoire" else "Recommandée",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (p.required && !granted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(p.rationale, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        GrantButton(granted, onClick = { request(p) })
                        if (p == AppPermission.BATTERY) {
                            TextButton(onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Permissions.dontKillMyAppUrl().toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            }) { Text("Conseils pour ${Permissions.manufacturer} (dontkillmyapp.com)") }
                        }
                    }
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}
