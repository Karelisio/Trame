package com.karelisio.trame.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.karelisio.trame.work.ReminderWorker
import java.util.concurrent.TimeUnit

/** "Plus tard" : retire la notif et la reposte dans 3 h. */
class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val seriesId = intent.getStringExtra(Notifier.EXTRA_SERIES) ?: return
        Notifier(context).cancel(intent.getIntExtra(EXTRA_NID, 0))
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(SNOOZE_HOURS, TimeUnit.HOURS)
                .setInputData(workDataOf(ReminderWorker.KEY_SERIES to seriesId))
                .addTag("reminder")
                .build(),
        )
    }

    companion object {
        const val SNOOZE_HOURS = 3L
        private const val EXTRA_NID = "nid"
        fun pendingIntent(context: Context, seriesId: String, nid: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, nid,
                Intent(context, SnoozeReceiver::class.java)
                    .putExtra(Notifier.EXTRA_SERIES, seriesId)
                    .putExtra(EXTRA_NID, nid),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
