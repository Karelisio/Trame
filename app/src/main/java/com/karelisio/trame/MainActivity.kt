package com.karelisio.trame

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.karelisio.trame.notify.Notifier
import com.karelisio.trame.ui.TrameNavHost
import com.karelisio.trame.ui.theme.TrameTheme
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** Demande d'ouverture d'un chapitre (notification, widget). */
data class ReadRequest(val seriesId: String, val chapterId: String?)

class MainActivity : ComponentActivity() {
    private val readRequests = MutableStateFlow<ReadRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        val settings = (application as TrameApp).settings
        var onboarded by mutableStateOf<Boolean?>(null)
        lifecycleScope.launch { onboarded = settings.isOnboarded() }
        splash.setKeepOnScreenCondition { onboarded == null }
        setContent {
            TrameTheme {
                onboarded?.let { ob ->
                    TrameNavHost(readRequests, startOnboarding = !ob, onOnboarded = { lifecycleScope.launch { settings.setOnboarded() } })
                }
            }
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
