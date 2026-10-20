package com.lawyerup.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.lawyerup.app.data.WorkspaceRepository
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class ReminderWorker(context: Context, params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        return try {
            val data=WorkspaceRepository(applicationContext).snapshot()
            val today=LocalDate.now(ZoneId.of("Asia/Tehran"))
            val due=data.tasks.count { !it.done && it.dateConfirmed && runCatching { LocalDate.parse(it.due)<=today.plusDays(3) }.getOrDefault(false) }
            val unreviewed=data.notices.count { !it.reviewed }
            val manager=applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("followup","یادآور پایش",NotificationManager.IMPORTANCE_DEFAULT))
            if((due>0 || unreviewed>0) && (android.os.Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(applicationContext,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)) {
                val pending=PendingIntent.getActivity(applicationContext,0,Intent(applicationContext,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                manager.notify(1001,NotificationCompat.Builder(applicationContext,"followup").setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("پایش: پیگیری‌های دفتر").setContentText("$due موعد نزدیک یا گذشته؛ $unreviewed ابلاغ بررسی‌نشده در پایش. برای تازه‌سازی ثنا برنامه را باز کنید.")
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(pending).setAutoCancel(true).build())
            }
            Result.success()
        } catch(_:Exception) { if(runAttemptCount<3) Result.retry() else Result.failure() }
    }
    companion object {
        fun schedule(context: Context,enabled: Boolean) {
            val manager=WorkManager.getInstance(context)
            if(!enabled) { manager.cancelUniqueWork("followup"); return }
            manager.enqueueUniquePeriodicWork("followup",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<ReminderWorker>(12,TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build()).build())
        }
    }
}
