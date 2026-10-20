package com.lawyerup.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.lawyerup.app.MainActivity
import com.lawyerup.app.R
import com.lawyerup.app.domain.WorkItem
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Private, inexact reminders for dates explicitly confirmed by the lawyer.
 * Notifications intentionally contain no client, case, or task details.
 */
object DeadlineReminderScheduler {
    const val PREFERENCE_ENABLED = "deadline_reminders_enabled"
    const val TAG = "payesh-deadline-reminder"
    private const val CHANNEL_ID = "payesh_deadline_reminders"
    private const val ZONE_ID = "Asia/Tehran"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
            .getBoolean(PREFERENCE_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("preferences", Context.MODE_PRIVATE).edit()
            .putBoolean(PREFERENCE_ENABLED, enabled).apply()
        if (!enabled) cancelAll(context)
    }

    fun schedule(context: Context, task: WorkItem) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (!isEnabled(context) || task.done || !task.dateConfirmed || task.due.isBlank()) {
            cancel(context, task.id)
            return
        }
        val dueDate = runCatching { LocalDate.parse(task.due) }.getOrNull()
        if (dueDate == null) {
            cancel(context, task.id)
            return
        }
        val zone = ZoneId.of(ZONE_ID)
        val today = LocalDate.now(zone)
        if (dueDate.isBefore(today)) {
            cancel(context, task.id)
            return
        }

        // A gentle heads-up the day before, plus a same-day reminder at 09:00.
        listOf(1, 0).forEach { daysBefore ->
            val reminderDate = dueDate.minusDays(daysBefore.toLong())
            val name = workName(task.id, daysBefore)
            if (reminderDate.isBefore(today)) {
                workManager.cancelUniqueWork(name)
            } else {
                val scheduledAt = ZonedDateTime.of(reminderDate, LocalTime.of(9, 0), zone).toInstant()
                val delay = Duration.between(java.time.Instant.now(), scheduledAt).toMillis().coerceAtLeast(0L)
                val request = OneTimeWorkRequestBuilder<DeadlineReminderWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setInputData(Data.Builder().putInt("daysBefore", daysBefore).build())
                    .addTag(TAG)
                    .build()
                workManager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
            }
        }
    }

    fun cancel(context: Context, taskId: String) {
        val manager = WorkManager.getInstance(context.applicationContext)
        manager.cancelUniqueWork(workName(taskId, 1))
        manager.cancelUniqueWork(workName(taskId, 0))
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(TAG)
    }

    private fun workName(taskId: String, daysBefore: Int): String =
        "payesh-deadline-${taskId.hashCode()}-$daysBefore"

    internal fun showReminder(context: Context, daysBefore: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "یادآوری‌های پایش", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "یادآوری خصوصی موعدهای تأییدشده"
                }
            )
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 7100 + daysBefore, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = if (daysBefore == 1) {
            "برای بررسی موعد ثبت‌شده، برنامه پایش را باز کنید."
        } else {
            "موعد ثبت‌شده امروز است؛ تاریخ و اقدام لازم را بررسی کنید."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("یادآوری پیگیری پایش")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            runCatching { NotificationManagerCompat.from(context).notify(7100 + daysBefore, notification) }
        }
    }
}

class DeadlineReminderWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val daysBefore = inputData.getInt("daysBefore", 0).coerceIn(0, 1)
        DeadlineReminderScheduler.showReminder(applicationContext, daysBefore)
        return Result.success()
    }
}
