package com.karelisio.trame.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.karelisio.trame.TrameApp
import com.karelisio.trame.notify.Notifier

/** Reposte la notification d'une série après "Plus tard", si des chapitres restent non lus. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_SERIES) ?: return Result.failure()
        val db = (applicationContext as TrameApp).database
        val series = db.series().get(id) ?: return Result.success()
        if (!series.isFavorite) return Result.success()
        val readThrough = maxOf(series.baselineChapter ?: -1.0, series.lastReadChapter ?: -1.0)
        val unread = db.chapters().forSeries(id)
            .filter { it.readAt == null && (it.numberValue ?: -1.0) > readThrough }
        Notifier(applicationContext).notifyNewChapters(series, unread)
        return Result.success()
    }

    companion object { const val KEY_SERIES = "series_id" }
}
