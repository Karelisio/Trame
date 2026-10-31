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
import kotlinx.coroutines.flow.map
import com.karelisio.trame.domain.ReleaseEstimate

class LibraryViewModel(private val repo: MangaRepository) : ViewModel() {
    val items: StateFlow<List<LibraryItem>?> =
        repo.observeLibrary().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val estimates: StateFlow<Map<String, ReleaseEstimate?>> = repo.observeCalendar()
        .map { list -> list.associate { it.item.series.id to it.estimate } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    private val _refreshResult = MutableStateFlow<Int?>(null)
    /** Nouveaux chapitres trouvés au dernier rafraîchissement manuel (null = rien à signaler). */
    val refreshResult = _refreshResult.asStateFlow()

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            _refreshResult.value = repo.refreshAll().values.sumOf { it.size }
            _refreshing.value = false
        }
    }

    fun consumeResult() { _refreshResult.value = null }
}
