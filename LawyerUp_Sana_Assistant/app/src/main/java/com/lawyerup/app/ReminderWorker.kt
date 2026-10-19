package com.lawyerup.app
import android.app.*
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
class ReminderWorker(c:Context,p:WorkerParameters):CoroutineWorker(c,p){ override suspend fun doWork():Result { val ch="deadlines"; val nm=applicationContext.getSystemService(NotificationManager::class.java); if(android.os.Build.VERSION.SDK_INT>=26) nm.createNotificationChannel(NotificationChannel(ch,"یادآوری مواعد",NotificationManager.IMPORTANCE_DEFAULT)); val n=Notification.Builder(applicationContext,ch).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("یادآوری موعد").setContentText(inputData.getString("title") ?: "موعد شما نزدیک است").setAutoCancel(true).build(); if(android.os.Build.VERSION.SDK_INT<33 || applicationContext.checkSelfPermission("android.permission.POST_NOTIFICATIONS")==0) nm.notify(inputData.getInt("id",1),n); return Result.success() }}