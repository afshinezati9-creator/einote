package com.einote.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.einote.app.data.NoteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val blockId = inputData.getLong(KEY_BLOCK_ID, -1L)
        if (blockId <= 0L) return@withContext Result.failure()

        val block = NoteDatabase.get(applicationContext).noteBlockDao().getById(blockId)
            ?: return@withContext Result.success()

        if (block.type != "CHECKLIST" || block.checked || block.reminderAt == null) {
            return@withContext Result.success()
        }

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val channelId = "einote_reminders"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "یادآوری‌های eiNote",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("یادآوری eiNote")
            .setContentText(block.content.ifBlank { "یک کار برایت برنامه‌ریزی شده است." })
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        manager.notify(block.id.toInt(), notification)
        Result.success()
    }

    companion object {
        const val KEY_BLOCK_ID = "block_id"
        const val WORK_PREFIX = "einote_reminder_"
    }
}
