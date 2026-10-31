package com.karelisio.trame.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import com.karelisio.trame.domain.ReleaseEstimate
import com.karelisio.trame.permissions.AppPermission
import com.karelisio.trame.ui.onboarding.rememberPermissionStates
import androidx.compose.material3.Surface
import com.karelisio.trame.domain.ReleaseStatus
import com.karelisio.trame.ui.components.rememberNow
import com.karelisio.trame.ui.components.shortUntil
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.karelisio.trame.ui.components.UnreadBubble
import com.karelisio.trame.ui.components.AnimatedNumber
import com.karelisio.trame.ui.components.pressBounce
import com.karelisio.trame.ui.fx.HalftoneLoader
import com.karelisio.trame.ui.fx.LocalBubbles
import com.karelisio.trame.ui.fx.SpeedLines
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ripple
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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
    onHistory: () -> Unit,
    onLocal: () -> Unit,
    onCalendar: () -> Unit,
    onPermissions: () -> Unit,
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val refreshResult by vm.refreshResult.collectAsStateWithLifecycle()
    val estimates by vm.estimates.collectAsStateWithLifecycle()
    var overflow by remember { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val haptics = LocalHapticFeedback.current
    val bubbles = LocalBubbles.current

    // Nouveaux chapitres : la case « impact » (lignes de vitesse) est déclenchée globalement.
    LaunchedEffect(refreshResult) {
        if (refreshResult == 0) bubbles.show("Tout est à jour", Icons.Rounded.Check)
        if (refreshResult != null) vm.consumeResult()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Trame") },
                subtitle = {
                    val n = items?.size ?: 0
                    val unread = items?.sumOf { it.unread } ?: 0
                    val style = MaterialTheme.typography.titleMedium
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedNumber(n.toLong(), style)
                        Text(if (n <= 1) " série suivie" else " séries suivies", style = style)
                        AnimatedVisibility(unread > 0, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                            Row {
                                Text(" · ", style = style)
                                AnimatedNumber(unread.toLong(), style, color = MaterialTheme.colorScheme.primary)
                                Text(" non lus", style = style, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Rechercher") }
                    IconButton(onClick = onCalendar) { Icon(Icons.Rounded.CalendarMonth, "Calendrier") }
                    Box {
                        IconButton(onClick = { overflow = true }) { Icon(Icons.Rounded.MoreVert, "Plus") }
                        DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                            DropdownMenuItem(
                                text = { Text("Fichiers locaux") },
                                leadingIcon = { Icon(Icons.Rounded.FolderOpen, null) },
                                onClick = { overflow = false; onLocal() },
                            )
                            DropdownMenuItem(
                                text = { Text("Historique") },
                                leadingIcon = { Icon(Icons.Rounded.History, null) },
                                onClick = { overflow = false; onHistory() },
                            )
                            DropdownMenuItem(
                                text = { Text("Réglages") },
                                leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                                onClick = { overflow = false; onSettings() },
                            )
                        }
                    }
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
    ) { padding ->
        val ptr = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate); vm.refresh() },
            state = ptr,
            modifier = Modifier.fillMaxSize().padding(padding),
            indicator = {
                // Lignes de vitesse qui convergent vers l'indicateur à mesure qu'on tire.
                // Bande supérieure, fondue vers le bas, centrée sur l'indicateur.
                SpeedLines(
                    intensity = { if (refreshing) 0.8f else (ptr.distanceFraction * 0.95f).coerceIn(0f, 0.95f) },
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    focus = { Offset(0.5f, 0.16f) },
                    lines = 72,
                    flicker = refreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(280.dp)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.verticalGradient(0f to Color.Black, 0.55f to Color.Black, 1f to Color.Transparent),
                                blendMode = BlendMode.DstIn,
                            )
                        },
                )
                PullToRefreshDefaults.LoadingIndicator(
                    state = ptr,
                    isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            val list = items
            val perms = rememberPermissionStates()
            val batteryMissing = perms[AppPermission.BATTERY] == false
            when {
                list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { HalftoneLoader() }
                list.isEmpty() -> EmptyLibrary(onSearch)
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(152.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (batteryMissing) item(span = { GridItemSpan(maxLineSpan) }) {
                        Surface(
                            onClick = onPermissions,
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.animateItem(),
                        ) {
                            Text(
                                "Vérification en arrière-plan bloquée : touche pour autoriser.",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    itemsIndexed(list) { index, item ->
                        SeriesCard(
                            item,
                            estimates[item.series.id],
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
private fun SeriesCard(item: LibraryItem, estimate: ReleaseEstimate?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = item.series
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .pressBounce(interaction)
            .clip(MaterialTheme.shapes.large)
            .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick),
    ) {
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
                UnreadBubble(item.unread)
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
        estimate?.takeIf { it.status != ReleaseStatus.ENDED }?.let { e ->
            val now = rememberNow(60_000)
            Text(
                if (e.status == ReleaseStatus.PAUSE_LIKELY) "Pause probable"
                else "Ch. ${e.nextChapter ?: "?"} ${shortUntil(e.nextAt, now)}",
                style = MaterialTheme.typography.labelSmall,
                color = if (e.status == ReleaseStatus.PAUSE_LIKELY) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
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
