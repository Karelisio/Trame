package com.karelisio.trame

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.karelisio.trame.notify.Notifier
import com.karelisio.trame.ui.TrameNavHost
import com.karelisio.trame.ui.theme.TrameTheme
import kotlinx.coroutines.flow.MutableStateFlow

/** Demande d'ouverture d'un chapitre (notification, widget). */
data class ReadRequest(val seriesId: String, val chapterId: String?)

class MainActivity : ComponentActivity() {
    private val readRequests = MutableStateFlow<ReadRequest?>(null)

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        // Provisoire : l'onboarding (étape 7) prendra le relais.
        if (Build.VERSION.SDK_INT >= 33 && !Notifier(this).canNotify()) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            TrameTheme { TrameNavHost(readRequests) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Notifier.ACTION_READ) return
        val series = intent.getStringExtra(Notifier.EXTRA_SERIES) ?: return
        Notifier(this).cancel(Notifier.notificationId(series))
        readRequests.value = ReadRequest(series, intent.getStringExtra(Notifier.EXTRA_CHAPTER))
    }
}
