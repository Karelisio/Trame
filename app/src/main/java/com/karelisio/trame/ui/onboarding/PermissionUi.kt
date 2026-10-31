package com.karelisio.trame.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.karelisio.trame.permissions.AppPermission
import com.karelisio.trame.permissions.Permissions

val AppPermission.icon: ImageVector
    get() = when (this) {
        AppPermission.NOTIFICATIONS -> Icons.Rounded.NotificationsActive
        AppPermission.BATTERY -> Icons.Rounded.BatteryChargingFull
        AppPermission.INSTALL -> Icons.Rounded.SystemUpdate
    }

/** État des autorisations, revérifié à chaque retour dans l'appli (réglages système). */
@Composable
fun rememberPermissionStates(): Map<AppPermission, Boolean> {
    val context = LocalContext.current
    val states = remember { mutableStateMapOf<AppPermission, Boolean>() }
    fun refresh() = AppPermission.entries.forEach { states[it] = Permissions.isGranted(context, it) }
    LaunchedEffect(Unit) { refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }
    return states
}

/** Lance la demande : dialogue système pour les notifs, sinon écran de réglages. */
@Composable
fun rememberPermissionRequester(onResult: () -> Unit = {}): (AppPermission) -> Unit {
    val context = LocalContext.current
    var notifAsked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult() }
    return { p ->
        if (p == AppPermission.NOTIFICATIONS && Build.VERSION.SDK_INT >= 33 && !notifAsked) {
            notifAsked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Permissions.open(context, p)
        }
    }
}

/** Bouton « Autoriser » qui se transforme en coche animée une fois accordé. */
@Composable
fun GrantButton(granted: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    var wasGranted by remember { mutableStateOf(granted) }
    LaunchedEffect(granted) {
        if (granted && !wasGranted) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        wasGranted = granted
    }
    AnimatedContent(
        targetState = granted,
        transitionSpec = {
            (scaleIn(spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMediumLow)) + fadeIn()) togetherWith (scaleOut() + fadeOut())
        },
        contentAlignment = Alignment.Center,
        label = "grant",
        modifier = modifier,
    ) { ok ->
        if (ok) {
            FilledTonalButton(onClick = {}, enabled = false, shapes = ButtonDefaults.shapes(), modifier = Modifier.height(56.dp)) {
                Icon(Icons.Rounded.Check, null, Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Autorisé", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Button(onClick = onClick, shapes = ButtonDefaults.shapes(), modifier = Modifier.height(56.dp)) {
                Text("Autoriser", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
