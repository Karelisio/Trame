package com.karelisio.trame.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.trame.data.db.LibraryItem
import com.karelisio.trame.data.repo.MangaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(private val repo: MangaRepository) : ViewModel() {
    val items: StateFlow<List<LibraryItem>?> =
        repo.observeLibrary().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    private val _newChapters = MutableStateFlow(0)
    /** Nombre de nouveaux chapitres trouvés au dernier rafraîchissement manuel. */
    val newChapters = _newChapters.asStateFlow()

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            _newChapters.value = repo.refreshAll().values.sumOf { it.size }
            _refreshing.value = false
        }
    }

    fun consumeNew() { _newChapters.value = 0 }
}
