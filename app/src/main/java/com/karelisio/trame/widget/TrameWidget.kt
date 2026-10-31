package com.karelisio.trame.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.karelisio.trame.MainActivity
import com.karelisio.trame.R
import com.karelisio.trame.TrameApp
import com.karelisio.trame.domain.ReleaseEstimate
import com.karelisio.trame.domain.ReleaseEstimator
import com.karelisio.trame.domain.ReleaseStatus
import com.karelisio.trame.notify.Notifier
import com.karelisio.trame.ui.theme.DarkFallback
import com.karelisio.trame.ui.theme.LightFallback

/** Données affichées par le widget. */
data class WidgetData(
    val seriesId: String,
    val title: String,
    val cover: Bitmap?,
    val newChapter: String?,
    val chapterToRead: String?,
    val estimate: ReleaseEstimate?,
)

/**
 * Widget Glance : couverture de la série suivie + compte à rebours,
 * ou « Nouveau ! Chapitre X ». Un tap ouvre directement la lecture.
 */
class TrameWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent {
            val colors = if (Build.VERSION.SDK_INT >= 31) GlanceTheme.colors
            else ColorProviders(light = LightFallback, dark = DarkFallback)
            GlanceTheme(colors = colors) { Content(context, data) }
        }
    }

    private suspend fun load(context: Context): WidgetData? {
        val app = context.applicationContext as TrameApp
        val db = app.database
        val favorites = db.series().favorites()
        if (favorites.isEmpty()) return null
        // Priorité : une série avec un nouveau chapitre, sinon la première suivie (One Piece par défaut).
        val candidates = favorites.map { s ->
            val chapters = db.chapters().forSeries(s.id)
            val readThrough = maxOf(s.baselineChapter ?: -1.0, s.lastReadChapter ?: -1.0)
            val unread = chapters.filter { it.readAt == null && (it.numberValue ?: -1.0) > readThrough }
            Triple(s, chapters, unread)
        }
        val (s, chapters, unread) = candidates.firstOrNull { it.third.isNotEmpty() } ?: candidates.first()
        val toRead = unread.filter { it.externalUrl != null }.minByOrNull { it.numberValue ?: 0.0 }
            ?: chapters.filter { it.externalUrl != null }.maxByOrNull { it.numberValue ?: 0.0 }
        return WidgetData(
            seriesId = s.id,
            title = s.title,
            cover = loadCover(context, s.coverUrl),
            newChapter = unread.maxByOrNull { it.numberValue ?: 0.0 }?.number,
            chapterToRead = toRead?.id,
            estimate = ReleaseEstimator.estimate(chapters.map { it.numberValue to it.publishAt }, s.status),
        )
    }

    private suspend fun loadCover(context: Context, url: String?): Bitmap? {
        url ?: return null
        val req = ImageRequest.Builder(context).data(url).allowHardware(false).size(360, 512).build()
        return (SingletonImageLoader.get(context).execute(req) as? SuccessResult)?.image?.toBitmap()
    }

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 250.dp)

        suspend fun refresh(context: Context) = runCatching { TrameWidget().updateAll(context) }
    }
}

private fun openIntent(context: Context, d: WidgetData?) = Intent(context, MainActivity::class.java).apply {
    if (d != null) {
        action = Notifier.ACTION_READ
        putExtra(Notifier.EXTRA_SERIES, d.seriesId)
        d.chapterToRead?.let { putExtra(Notifier.EXTRA_CHAPTER, it) }
    }
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
}

@androidx.compose.runtime.Composable
private fun Content(context: Context, d: WidgetData?) {
    val size = LocalSize.current
    val base = GlanceModifier.fillMaxSize()
        .appWidgetBackground()
        .background(GlanceTheme.colors.widgetBackground)
        .cornerRadius(android.R.dimen.system_app_widget_background_radius)
        .clickable(actionStartActivity(openIntent(context, d)))
    if (d == null) {
        Box(base.padding(16.dp), contentAlignment = Alignment.Center) {
            Text("Suis une série dans Trame", style = TextStyle(color = GlanceTheme.colors.onSurface))
        }
        return
    }
    val wide = size.width >= TrameWidget.WIDE.width
    if (!wide) {
        // Petit format : couverture plein cadre + bandeau.
        Box(base) {
            CoverImage(d, GlanceModifier.fillMaxSize())
            Column(GlanceModifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.Bottom) {
                Box(
                    GlanceModifier.fillMaxWidth().background(
                        if (d.newChapter != null) GlanceTheme.colors.primary else GlanceTheme.colors.surface,
                    ).cornerRadius(14.dp).padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        headline(d),
                        maxLines = 2,
                        style = TextStyle(
                            fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            color = if (d.newChapter != null) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSurface,
                        ),
                    )
                }
            }
        }
        return
    }
    Row(base.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CoverImage(d, GlanceModifier.width(if (size.height >= TrameWidget.LARGE.height) 120.dp else 72.dp).fillMaxHeight().cornerRadius(16.dp))
        Spacer(GlanceModifier.width(12.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(
                d.title, maxLines = 2,
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = GlanceTheme.colors.onSurface),
            )
            Spacer(GlanceModifier.height(6.dp))
            if (d.newChapter != null) {
                Box(GlanceModifier.background(GlanceTheme.colors.primary).cornerRadius(16.dp).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(
                        "Nouveau ! Chapitre ${d.newChapter}",
                        style = TextStyle(fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onPrimary),
                    )
                }
                Spacer(GlanceModifier.height(4.dp))
                Text("Touchez pour lire", style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant))
            } else {
                Text(headline(d), style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.primary))
                d.estimate?.let { e ->
                    Text(
                        when (e.status) {
                            ReleaseStatus.PAUSE_LIKELY -> "Pause probable · ${e.rhythmLabel.lowercase()}"
                            ReleaseStatus.ENDED -> "Série terminée"
                            else -> "Chapitre ${e.nextChapter ?: "?"} · ${e.rhythmLabel.lowercase()}"
                        },
                        style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
                    )
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun CoverImage(d: WidgetData, modifier: GlanceModifier) {
    Image(
        provider = d.cover?.let { ImageProvider(it) } ?: ImageProvider(R.drawable.ic_launcher_foreground),
        contentDescription = d.title,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(GlanceTheme.colors.secondaryContainer),
    )
}

/** « Nouveau ! Ch. X », « dans 2 j 4 h », « Imminent »… (le widget se met à jour toutes les 30 min). */
private fun headline(d: WidgetData): String {
    d.newChapter?.let { return "Nouveau ! Ch. $it" }
    val e = d.estimate ?: return d.title
    if (e.status == ReleaseStatus.PAUSE_LIKELY) return "Pause probable"
    if (e.status == ReleaseStatus.ENDED) return "Terminé"
    val left = e.nextAt - System.currentTimeMillis()
    if (left <= 0 || e.status == ReleaseStatus.LATE) return "Imminent"
    val d0 = left / 86_400_000; val h = (left % 86_400_000) / 3_600_000; val m = (left % 3_600_000) / 60_000
    return when {
        d0 > 0 -> "dans $d0 j ${h} h"
        h > 0 -> "dans $h h ${m} min"
        else -> "dans $m min"
    }
}

class TrameWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TrameWidget()
}

