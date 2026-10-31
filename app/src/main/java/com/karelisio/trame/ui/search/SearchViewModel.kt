package com.karelisio.trame.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.trame.data.db.SeriesSource
import com.karelisio.trame.data.repo.MangaRepository
import com.karelisio.trame.data.repo.SeriesSummary
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<SeriesSummary> = emptyList(),
    val source: SeriesSource = SeriesSource.MANGADEX,
    val error: String? = null,
)

@OptIn(FlowPreview::class)
class SearchViewModel(private val repo: MangaRepository) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state = _state.asStateFlow()
    private val queries = MutableStateFlow("")

    val favorites = repo.observeFavoriteIds().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        viewModelScope.launch {
            queries.map { it.trim() }.debounce(350).distinctUntilChanged().collectLatest { q ->
                if (q.length < 2) {
                    _state.update { it.copy(loading = false, results = emptyList(), error = null) }
                    return@collectLatest
                }
                _state.update { it.copy(loading = true, error = null) }
                runCatching { repo.search(q) }
                    .onSuccess { r -> _state.update { it.copy(loading = false, results = r.results, source = r.source) } }
                    .onFailure { _state.update { it.copy(loading = false, error = "Recherche impossible. Vérifie ta connexion.") } }
            }
        }
    }

    fun onQuery(q: String) {
        _state.update { it.copy(query = q) }
        queries.value = q
    }

    fun toggleFavorite(s: SeriesSummary) {
        viewModelScope.launch { runCatching { repo.setFavorite(s, s.id !in favorites.value) } }
    }

    suspend fun prepareOpen(s: SeriesSummary): String {
        val resolved = repo.resolveToMangaDex(s)
        repo.ensureSeries(resolved)
        return resolved.id
    }
}
