package com.karelisio.trame.ui.detail

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.runtime.remember
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.trame.data.db.ChapterEntity
import com.karelisio.trame.ui.components.BubbleBadge
import com.karelisio.trame.ui.components.Cover
import com.karelisio.trame.ui.components.StatusShapeDot
import com.karelisio.trame.ui.components.cascadeIn
import com.karelisio.trame.ui.components.sharedElement
import com.karelisio.trame.ui.components.statusLabel
import com.karelisio.trame.ui.library.SourceFooter
import com.karelisio.trame.ui.calendar.ReleaseCard
import com.karelisio.trame.ui.search.FavoriteMorphButton
import java.text.DateFormat
import java.util.Date

@Composable
fun DetailScreen(vm: DetailViewModel, onBack: () -> Unit, onRead: (chapterId: String) -> Unit) {
    val item by vm.item.collectAsStateWithLifecycle()
    val chapters by vm.chapters.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val estimate by vm.estimate.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    val series = item?.series
    val readThrough = maxOf(series?.baselineChapter ?: -1.0, series?.lastReadChapter ?: -1.0)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        ) {
            item {
                Header(
                    id = vm.seriesId,
                    title = series?.title ?: "",
                    coverUrl = series?.coverUrl,
                    status = series?.status,
                    year = series?.year,
                    favorite = series?.isFavorite == true,
                    onToggleFavorite = vm::toggleFavorite,
                )
            }
            continueChapter(chapters.orEmpty(), readThrough)?.let { c ->
                item {
                    Button(
                        onClick = { onRead(c.id) },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).height(56.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.MenuBook, null)
                        Spacer(Modifier.width(10.dp))
                        Text("Lire le chapitre ${c.number ?: "?"}", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            estimate?.let { e ->
                item(key = "release") { ReleaseCard(e, Modifier.padding(horizontal = 20.dp, vertical = 8.dp).animateItem()) }
            }
            series?.description?.let { desc ->
                item { Synopsis(desc) }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Chapitres" + (chapters?.size?.takeIf { it > 0 }?.let { " ($it)" } ?: ""),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalIconButton(onClick = vm::refresh) { Icon(Icons.Rounded.Refresh, "Actualiser") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Plus") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Tout marquer comme lu") }, onClick = { menu = false; vm.setAllRead(true) })
                                DropdownMenuItem(text = { Text("Tout marquer comme non lu") }, onClick = { menu = false; vm.setAllRead(false) })
                            }
                        }
                    }
                    if (loading) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    if (error && chapters.isNullOrEmpty()) {
                        Text("Hors ligne : impossible de charger les chapitres.", color = MaterialTheme.colorScheme.error)
                    } else if (!loading && chapters?.isEmpty() == true) {
                        Text(
                            "Aucun chapitre dans cette langue sur MangaDex. Change la langue dans les réglages.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            itemsIndexed(chapters.orEmpty(), key = { _, c -> c.id }) { i, c ->
                ChapterRow(
                    c,
                    isNew = c.readAt == null && (c.numberValue ?: -1.0) > readThrough,
                    modifier = Modifier.cascadeIn(i, c.id),
                    onClick = { onRead(c.id) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.toggleRead(c)
                    },
                )
            }
            item { SourceFooter() }
        }
        FilledTonalIconButton(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(8.dp),
        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
    }
}

@Composable
private fun Header(
    id: String,
    title: String,
    coverUrl: String?,
    status: String?,
    year: Int?,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        // Fond : couverture floutée qui se fond dans la surface.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Cover(coverUrl, "", Modifier.matchParentSize().blur(40.dp).alpha(0.55f))
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface)),
            ),
        )
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(top = 56.dp, start = 20.dp, end = 20.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Cover(
                coverUrl, title,
                Modifier
                    .sharedElement("cover-$id")
                    .width(200.dp)
                    .height(286.dp)
                    .clip(MaterialTheme.shapes.extraLarge),
            )
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.displaySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                statusLabel(status)?.let {
                    StatusShapeDot(status == "ongoing", Modifier.size(14.dp))
                    Text(it, style = MaterialTheme.typography.labelLarge)
                }
                year?.let { Text("· $it", style = MaterialTheme.typography.labelLarge) }
                Spacer(Modifier.width(8.dp))
                FavoriteMorphButton(favorite, onToggleFavorite)
                Text(if (favorite) "Suivie" else "Suivre", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun Synopsis(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (expanded) Int.MAX_VALUE else 4,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable { expanded = !expanded }
            .animateContentSize(),
    )
}

@Composable
private fun ChapterRow(c: ChapterEntity, isNew: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val read = c.readAt != null
    ListItem(
        headlineContent = { Text("Chapitre ${c.number ?: "?"}", style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Text(
                listOfNotNull(c.title, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(c.publishAt)))
                    .joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isNew) BubbleBadge("Nouveau")
                if (read) Icon(Icons.Rounded.Check, "Lu", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (c.externalUrl != null) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, "MANGA Plus", tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier
            .padding(horizontal = 8.dp)
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = { if (c.externalUrl != null) onClick() }, onLongClick = onLongClick)
            .alpha(if (read) 0.55f else 1f),
    )
}

/** Prochain chapitre officiel à lire : le premier non lu au-dessus de la progression, sinon le plus récent. */
private fun continueChapter(chapters: List<ChapterEntity>, readThrough: Double): ChapterEntity? {
    val readable = chapters.filter { it.externalUrl != null }.sortedBy { it.numberValue ?: 0.0 }
    return readable.firstOrNull { it.readAt == null && (it.numberValue ?: -1.0) > readThrough } ?: readable.lastOrNull()
}
