package com.karelisio.trame.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.trame.data.repo.CalendarEntry
import com.karelisio.trame.data.repo.MangaRepository
import com.karelisio.trame.domain.DAY
import com.karelisio.trame.domain.ReleaseStatus
import com.karelisio.trame.ui.components.BubbleBadge
import com.karelisio.trame.ui.components.Countdown
import com.karelisio.trame.ui.components.Cover
import com.karelisio.trame.ui.components.cascadeIn
import com.karelisio.trame.ui.components.sharedElement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun section(e: CalendarEntry, now: Long): String {
    val est = e.estimate ?: return "Rythme inconnu"
    return when {
        est.status == ReleaseStatus.ENDED -> "Terminées"
        est.status == ReleaseStatus.PAUSE_LIKELY -> "Pause probable"
        est.nextAt - now < DAY -> "Dans les 24 h"
        est.nextAt - now < 7 * DAY -> "Cette semaine"
        else -> "Plus tard"
    }
}

@Composable
fun CalendarScreen(repo: MangaRepository, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val entries by repo.observeCalendar().collectAsStateWithLifecycle(emptyList())
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val now = System.currentTimeMillis()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Calendrier") },
                subtitle = { Text("Sorties estimées d'après le rythme de publication") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text("Suis une série pour voir ses prochaines sorties.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        val grouped = entries.groupBy { section(it, now) }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            grouped.forEach { (title, list) ->
                item(key = "h-$title") {
                    Text(
                        title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, start = 4.dp).animateItem(),
                    )
                }
                items(list, key = { it.item.series.id }) { e ->
                    CalendarRow(e, onClick = { onOpen(e.item.series.id) }, modifier = Modifier.animateItem().cascadeIn(entries.indexOf(e), e.item.series.id))
                }
            }
        }
    }
}

@Composable
private fun CalendarRow(e: CalendarEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = e.item.series
    val est = e.estimate
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(s.coverUrl, s.title, Modifier.sharedElement("cover-${s.id}").width(64.dp).height(92.dp).clip(MaterialTheme.shapes.medium))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (e.item.unread > 0) { Spacer(Modifier.width(6.dp)); BubbleBadge("Nouveau !") }
                }
                when {
                    est == null -> Text("Pas assez de chapitres datés", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    est.status == ReleaseStatus.ENDED -> Text("Série terminée", style = MaterialTheme.typography.bodySmall)
                    else -> {
                        Text(
                            buildString {
                                append(est.statusLabel())
                                est.nextChapter?.let { append(" · Ch. $it") }
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (est.status == ReleaseStatus.PAUSE_LIKELY) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        )
                        Countdown(est.nextAt, compact = true)
                        Text(
                            "${est.rhythmLabel} · ${SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(Date(est.nextAt))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
