package com.karelisio.trame.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.trame.data.db.LibraryItem
import com.karelisio.trame.ui.components.BubbleBadge
import com.karelisio.trame.ui.components.Cover
import com.karelisio.trame.ui.components.cascadeIn
import com.karelisio.trame.ui.components.formatChapter
import com.karelisio.trame.ui.components.sharedBounds
import com.karelisio.trame.ui.components.sharedElement

@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onOpenSeries: (String) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val newChapters by vm.newChapters.collectAsStateWithLifecycle()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(newChapters) {
        if (newChapters > 0) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            snackbar.showSnackbar(if (newChapters == 1) "1 nouveau chapitre !" else "$newChapters nouveaux chapitres !")
            vm.consumeNew()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Trame") },
                subtitle = {
                    val n = items?.size ?: 0
                    val unread = items?.sumOf { it.unread } ?: 0
                    Text(
                        buildString {
                            append(if (n <= 1) "$n série suivie" else "$n séries suivies")
                            if (unread > 0) append(" · $unread non lus")
                        },
                    )
                },
                actions = {
                    IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Rechercher") }
                    IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Réglages") }
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onSearch,
                icon = { Icon(Icons.Rounded.Search, null) },
                text = { Text("Ajouter une série") },
                expanded = scroll.state.collapsedFraction < 0.5f,
                modifier = Modifier.sharedBounds("search-container"),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val ptr = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate); vm.refresh() },
            state = ptr,
            modifier = Modifier.fillMaxSize().padding(padding),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = ptr,
                    isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            val list = items
            when {
                list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { ContainedLoadingIndicator() }
                list.isEmpty() -> EmptyLibrary(onSearch)
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(152.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(list) { index, item ->
                        SeriesCard(
                            item,
                            onClick = { onOpenSeries(item.series.id) },
                            modifier = Modifier.animateItem().cascadeIn(index, item.series.id),
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { SourceFooter() }
                }
            }
        }
    }
}

@Composable
private fun SeriesCard(item: LibraryItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = item.series
    Column(modifier.clip(MaterialTheme.shapes.large).clickable(onClick = onClick)) {
        Box {
            Cover(
                s.coverUrl, s.title,
                Modifier
                    .sharedElement("cover-${s.id}")
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
                    .clip(MaterialTheme.shapes.large),
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = item.unread > 0,
                enter = scaleIn(),
                exit = scaleOut(),
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                BubbleBadge(if (item.unread == 1) "Nouveau !" else "${item.unread} non lus")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            s.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        formatChapter(s.lastKnownChapter)?.let {
            Text(
                "Chapitre $it",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun EmptyLibrary(onSearch: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(120.dp).clip(MaterialShapes.Cookie9Sided.toShape())
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onSearch),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Search, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(16.dp))
        Text("Ta bibliothèque est vide", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Cherche une série pour être prévenu dès qu'un chapitre sort.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SourceFooter(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
        Text(
            "Données : MangaDex · Lecture officielle : MANGA Plus",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
