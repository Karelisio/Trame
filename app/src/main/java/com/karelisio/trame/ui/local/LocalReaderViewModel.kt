package com.karelisio.trame.ui.local

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.karelisio.trame.TrameApp
import com.karelisio.trame.data.local.FolderPageSource
import com.karelisio.trame.data.local.PageSource
import com.karelisio.trame.data.local.ZipPageSource
import com.karelisio.trame.data.repo.ReaderPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LocalReaderState {
    data object Loading : LocalReaderState
    data class Ready(val source: PageSource, val name: String, val startPage: Int) : LocalReaderState
    data class Error(val message: String) : LocalReaderState
}

class LocalReaderViewModel(app: Application, val uri: String) : AndroidViewModel(app) {
    private val trame = app as TrameApp
    private val dao = trame.database.localBooks()

    private val _state = MutableStateFlow<LocalReaderState>(LocalReaderState.Loading)
    val state = _state.asStateFlow()
    val prefs = trame.settings.readerPrefs.stateIn(viewModelScope, SharingStarted.Eagerly, ReaderPrefs())

    init {
        viewModelScope.launch {
            val book = dao.get(uri)
            runCatching {
                val u = Uri.parse(uri)
                if (book?.isFolder == true) FolderPageSource.open(app, u) else ZipPageSource.open(app, u)
            }.onSuccess { src ->
                if (src.count == 0) {
                    _state.value = LocalReaderState.Error("Aucune image trouvée.")
                    return@onSuccess
                }
                val cover = src.page(0).toString()
                dao.setInfo(uri, src.count, cover)
                _state.value = LocalReaderState.Ready(src, book?.name ?: "", (book?.lastPage ?: 0).coerceIn(0, src.count - 1))
            }.onFailure {
                _state.value = LocalReaderState.Error("Impossible d'ouvrir ce fichier (${it.message ?: "erreur"}).")
            }
        }
    }

    fun onPage(page: Int) {
        viewModelScope.launch { dao.setProgress(uri, page, System.currentTimeMillis()) }
        preload(page)
    }

    /** Préchargement des 3 pages suivantes (et de la précédente) dans le cache Coil. */
    private fun preload(page: Int) {
        val src = (state.value as? LocalReaderState.Ready)?.source ?: return
        val loader = SingletonImageLoader.get(getApplication())
        viewModelScope.launch {
            ((page - 1)..(page + 3)).filter { it in 0 until src.count && it != page }.forEach { i ->
                loader.enqueue(pageRequest(getApplication(), src.page(i)))
            }
        }
    }

    fun updatePrefs(t: (ReaderPrefs) -> ReaderPrefs) {
        viewModelScope.launch { trame.settings.updateReaderPrefs(t) }
    }

    override fun onCleared() {
        (state.value as? LocalReaderState.Ready)?.source?.close()
    }
}

/** Requête de page : bornée à 2048 px (net en zoom x2,5 sans exploser la mémoire). */
fun pageRequest(context: android.content.Context, data: Any) =
    ImageRequest.Builder(context).data(data).size(2048, 2048).build()
