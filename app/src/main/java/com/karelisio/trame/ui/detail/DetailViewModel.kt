package com.karelisio.trame.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.trame.data.db.ChapterEntity
import com.karelisio.trame.data.db.LibraryItem
import com.karelisio.trame.data.repo.MangaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(private val repo: MangaRepository, val seriesId: String) : ViewModel() {
    val item: StateFlow<LibraryItem?> =
        repo.observeItem(seriesId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val chapters: StateFlow<List<ChapterEntity>?> =
        repo.observeChapters(seriesId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _error = MutableStateFlow(false)
    val error = _error.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = runCatching { repo.refreshSeries(seriesId) }.isFailure
            _loading.value = false
        }
    }

    fun toggleRead(c: ChapterEntity) {
        viewModelScope.launch { repo.setRead(c, c.readAt == null) }
    }

    fun setAllRead(read: Boolean) {
        viewModelScope.launch { repo.setAllRead(seriesId, read) }
    }

    fun toggleFavorite() {
        val fav = item.value?.series?.isFavorite ?: return
        viewModelScope.launch { repo.setFavorite(seriesId, !fav) }
    }
}
