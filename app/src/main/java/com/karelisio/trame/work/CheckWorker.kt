package com.karelisio.trame.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.BackoffPolicy
import com.karelisio.trame.TrameApp
import com.karelisio.trame.notify.Notifier
import java.util.concurrent.TimeUnit

/** Vérifie périodiquement les nouveaux chapitres de toutes les séries suivies. */
class CheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as TrameApp
        val notifier = Notifier(applicationContext)
        val results = runCatching { app.repository.refreshAll() }.getOrElse { return Result.retry() }
        notifier.syncChannels(results.keys.toList())
        results.forEach { (series, chapters) ->
            // Relire la série pour avoir couverture/titre à jour.
            val fresh = app.database.series().get(series.id) ?: series
            notifier.notifyNewChapters(fresh, chapters)
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "check_new_chapters"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedule(context: Context, intervalHours: Int) {
            val request = PeriodicWorkRequestBuilder<CheckWorker>(intervalHours.toLong(), TimeUnit.HOURS, 20, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .addTag(NAME)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** Vérification immédiate (debug / bouton "vérifier maintenant"). */
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<CheckWorker>().setConstraints(constraints).build(),
            )
        }
    }
}
