package com.karelisio.trame.ui.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.trame.data.update.AvailableUpdate
import com.karelisio.trame.data.update.DownloadState
import com.karelisio.trame.data.update.UpdateManager
import com.karelisio.trame.permissions.AppPermission
import com.karelisio.trame.permissions.Permissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateUi {
    data object Hidden : UpdateUi
    data object UpToDate : UpdateUi
    data class Available(val update: AvailableUpdate) : UpdateUi
    data class Downloading(val update: AvailableUpdate, val progress: Float) : UpdateUi
    data class Ready(val update: AvailableUpdate, val file: File) : UpdateUi
    data class Failed(val update: AvailableUpdate?, val message: String) : UpdateUi
}

class UpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val manager = UpdateManager(app)
    private val _ui = MutableStateFlow<UpdateUi>(UpdateUi.Hidden)
    val ui = _ui.asStateFlow()

    init { check(force = false) }

    /** Au lancement : au plus toutes les 6 h, silencieux si à jour. Depuis les réglages : forcé. */
    fun check(force: Boolean) {
        viewModelScope.launch {
            runCatching { manager.check(force) }
                .onSuccess { u -> _ui.value = u?.let { UpdateUi.Available(it) } ?: if (force) UpdateUi.UpToDate else UpdateUi.Hidden }
                .onFailure { if (force) _ui.value = UpdateUi.Failed(null, "Impossible de vérifier les mises à jour.") }
        }
    }

    fun download(update: AvailableUpdate) {
        viewModelScope.launch {
            runCatching {
                manager.download(update).collect { s ->
                    _ui.value = when (s) {
                        is DownloadState.Progress -> UpdateUi.Downloading(update, s.fraction)
                        is DownloadState.Done -> UpdateUi.Ready(update, s.file)
                    }
                }
            }.onFailure { _ui.value = UpdateUi.Failed(update, "Téléchargement interrompu.") }
        }
    }

    /** true si l'installation a démarré, false s'il faut d'abord l'autorisation. */
    fun install(file: File): Boolean {
        val ctx = getApplication<Application>()
        if (!Permissions.isGranted(ctx, AppPermission.INSTALL)) {
            Permissions.open(ctx, AppPermission.INSTALL)
            return false
        }
        runCatching { manager.install(file) }.onFailure {
            (_ui.value as? UpdateUi.Ready)?.let { r -> _ui.value = UpdateUi.Failed(r.update, "Installation impossible.") }
        }
        return true
    }

    fun dismiss() { _ui.value = UpdateUi.Hidden }
}
