package com.karelisio.trame.ui.history

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.trame.data.db.HistoryRow
import com.karelisio.trame.data.repo.MangaRepository
import com.karelisio.trame.ui.components.Cover
import com.karelisio.trame.ui.components.cascadeIn

@Composable
fun HistoryScreen(repo: MangaRepository, onBack: () -> Unit, onOpen: (HistoryRow) -> Unit) {
    val rows by repo.observeHistory().collectAsStateWithLifecycle(null)
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Historique") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        val list = rows
        if (list != null && list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text("Aucune lecture pour l'instant.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            itemsIndexed(list.orEmpty(), key = { _, r -> r.id }) { i, r ->
                ListItem(
                    onClick = { onOpen(r) },
                    leadingContent = {
                        Cover(r.coverUrl, r.seriesTitle, Modifier.width(48.dp).height(68.dp).clip(MaterialTheme.shapes.small))
                    },
                    supportingContent = {
                        Text(
                            DateUtils.getRelativeTimeSpanString(r.readAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                        )
                    },
                    modifier = Modifier.animateItem().cascadeIn(i, r.id).padding(horizontal = 8.dp),
                ) {
                    Text("${r.seriesTitle} · Ch. ${r.chapterNumber ?: "?"}", style = MaterialTheme.typography.titleMedium)
                }
            }
            item { Box(Modifier.padding(PaddingValues(bottom = 24.dp))) }
        }
    }
}
