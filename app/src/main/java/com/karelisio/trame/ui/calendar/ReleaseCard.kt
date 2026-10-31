package com.karelisio.trame.ui.calendar

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karelisio.trame.domain.ReleaseEstimate
import com.karelisio.trame.domain.ReleaseStatus
import com.karelisio.trame.ui.components.BubbleBadge
import com.karelisio.trame.ui.components.Countdown
import com.karelisio.trame.ui.components.StatusShapeDot
import java.text.DateFormat
import java.util.Date

fun ReleaseEstimate.statusLabel() = when (status) {
    ReleaseStatus.SCHEDULED -> "Prochaine sortie estimée"
    ReleaseStatus.LATE -> "Sortie imminente"
    ReleaseStatus.PAUSE_LIKELY -> "Pause probable"
    ReleaseStatus.ENDED -> "Série terminée"
}

/** Carte "prochain chapitre" : compte à rebours, rythme, détection de pause. */
@Composable
fun ReleaseCard(e: ReleaseEstimate, modifier: Modifier = Modifier) {
    val pause = e.status == ReleaseStatus.PAUSE_LIKELY
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (pause) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusShapeDot(!pause && e.status != ReleaseStatus.ENDED, Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    e.statusLabel() + (e.nextChapter?.takeIf { e.status != ReleaseStatus.ENDED }?.let { " · Ch. $it" } ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (pause) BubbleBadge("Pause ?", container = MaterialTheme.colorScheme.tertiary, content = MaterialTheme.colorScheme.onTertiary)
            }
            if (e.status != ReleaseStatus.ENDED) {
                Countdown(e.nextAt)
                val date = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT).format(Date(e.nextAt))
                Text(
                    (if (pause) "Reprise possible le " else "Vers le ") + date,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "${e.rhythmLabel} · estimation ${if (e.confidence > 0.7f) "fiable" else "approximative"}, d'après les derniers chapitres",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
