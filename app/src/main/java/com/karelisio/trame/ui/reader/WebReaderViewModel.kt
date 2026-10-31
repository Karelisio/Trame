package com.karelisio.trame.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.trame.data.db.ChapterEntity
import com.karelisio.trame.data.repo.MangaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReaderState(
    val current: ChapterEntity? = null,
    /** Chapitre précédent / suivant lisibles officiellement (externalUrl). */
    val previous: ChapterEntity? = null,
    val next: ChapterEntity? = null,
    val seriesTitle: String = "",
    val coverUrl: String? = null,
)

class WebReaderViewModel(
    private val repo: MangaRepository,
    val seriesId: String,
    initialChapterId: String?,
) : ViewModel() {
    private val currentId = MutableStateFlow(initialChapterId)

    val state: StateFlow<ReaderState> = combine(
        repo.observeChapters(seriesId), repo.observeItem(seriesId), currentId,
    ) { chapters, item, id ->
        // Ordre croissant de numéro pour naviguer.
        val readable = chapters.filter { it.externalUrl != null }.sortedBy { it.numberValue ?: 0.0 }
        val current = readable.firstOrNull { it.id == id } ?: chapters.firstOrNull { it.id == id } ?: readable.lastOrNull()
        val idx = readable.indexOfFirst { it.id == current?.id }
        ReaderState(
            current = current,
            previous = if (idx > 0) readable[idx - 1] else null,
            next = if (idx >= 0 && idx < readable.lastIndex) readable[idx + 1] else null,
            seriesTitle = item?.series?.title.orEmpty(),
            coverUrl = item?.series?.coverUrl,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReaderState())

    private val marked = mutableSetOf<String>()

    /** Ouvrir un chapitre = le marquer lu (+ historique). */
    fun onOpened(chapter: ChapterEntity) {
        if (!marked.add(chapter.id)) return
        if (currentId.value == null) currentId.value = chapter.id
        viewModelScope.launch { repo.setRead(chapter, true) }
    }

    fun goTo(chapter: ChapterEntity?) {
        chapter ?: return
        currentId.value = chapter.id
    }
}
