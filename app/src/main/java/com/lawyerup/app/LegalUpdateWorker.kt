package com.lawyerup.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.Html
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class LegalUpdateWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    private val prefs=context.getSharedPreferences("preferences",Context.MODE_PRIVATE)

    override suspend fun doWork():Result = try {
        val entries=fetchIndex()
        if(entries.isEmpty()) {
            prefs.edit().putString("legal_watch_error","فهرست رسمی قابل‌خواندن نبود؛ بررسی کامل انجام نشده است.").apply()
            return Result.retry()
        }
        val known=JSONObject(prefs.getString("legal_watch_known","{}") ?: "{}")
        if(!prefs.getBoolean("legal_watch_initialized",false)) {
            entries.forEach { known.put(it.id,it.title) }
            prefs.edit()
                .putString("legal_watch_known",known.toString())
                .putBoolean("legal_watch_initialized",true)
                .putLong("legal_watch_last_check",System.currentTimeMillis())
                .remove("legal_watch_error")
                .apply()
            return Result.success()
        }
        val changed=entries.filter { entry -> !known.has(entry.id) || known.optString(entry.id)!=entry.title }
        entries.forEach { known.put(it.id,it.title) }
        val editor=prefs.edit()
            .putString("legal_watch_known",known.toString())
            .putLong("legal_watch_last_check",System.currentTimeMillis())
            .remove("legal_watch_error")
        if(changed.isNotEmpty()) {
            val updates=JSONArray()
            changed.take(50).forEach { entry ->
                updates.put(JSONObject().put("id",entry.id).put("title",entry.title).put("url",entry.url))
            }
            editor.putString("legal_watch_updates",updates.toString())
        }
        editor.apply()
        if(changed.isNotEmpty()) notifyChanges(changed)
        Result.success()
    } catch(_:Exception) {
        prefs.edit().putString("legal_watch_error","بررسی فهرست رسمی ناموفق بود؛ اتصال یا ساختار صفحه را بررسی کنید.").apply()
        if(runAttemptCount<3) Result.retry() else Result.failure()
    }

    private data class Entry(val id:String,val title:String,val url:String)

    private fun fetchIndex():List<Entry> {
        val connection=URL("https://qavanin.ir/?Report_ID=1").openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects=true
            connection.connectTimeout=20000
            connection.readTimeout=25000
            connection.setRequestProperty("User-Agent","Payesh-Official-Legal-Index-Monitor/2.0")
            connection.setRequestProperty("Accept","text/html,application/xhtml+xml")
            val code=connection.responseCode
            require(code in 200..299) { "HTTP " + code }
            val host=connection.url.host.lowercase()
            require(host=="qavanin.ir" || host.endsWith(".qavanin.ir"))
            val output=ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer=ByteArray(8192)
                while(true) {
                    val count=input.read(buffer)
                    if(count<0) break
                    require(output.size()+count<=8000000)
                    output.write(buffer,0,count)
                }
            }
            val html=String(output.toByteArray(),Charsets.UTF_8)
            val pattern=Regex("(?is)<a\\b[^>]*href\\s*=\\s*['\"]([^'\"]*TreeText[^'\"]*)['\"][^>]*>(.*?)</a>")
            val results=linkedMapOf<String,Entry>()
            pattern.findAll(html).forEach { match ->
                val raw=match.groupValues[1].replace("&amp;","&")
                val id=Regex("[?&]IDS=(\\d+)",RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1) ?: return@forEach
                var title=Html.fromHtml(match.groupValues[2],Html.FROM_HTML_MODE_LEGACY).toString()
                    .replace(Regex("\\s+")," ").trim()
                if(title.length>180) title=title.take(180)
                if(title.isBlank()) return@forEach
                val fullUrl=if(raw.startsWith("http")) raw else "https://qavanin.ir"+if(raw.startsWith("/")) raw else "/"+raw
                results[id]=Entry(id,title,fullUrl)
            }
            return results.values.toList()
        } finally {
            connection.disconnect()
        }
    }

    private fun notifyChanges(entries:List<Entry>) {
        if(!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) return
        val manager=applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel("payesh_legal_updates","تغییرات رسمی قوانین",NotificationManager.IMPORTANCE_DEFAULT))
        }
        val first=entries.first()
        val intent=Intent(Intent.ACTION_VIEW,Uri.parse(first.url))
        val pending=PendingIntent.getActivity(
            applicationContext,first.id.hashCode(),intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val preview=entries.take(3).joinToString("، ") { it.title }
        val body=when {
            entries.size==1 -> preview
            entries.size<=3 -> preview
            else -> preview+" و "+(entries.size-3)+" مورد دیگر"
        }"
        val notification=NotificationCompat.Builder(applicationContext,"payesh_legal_updates")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("پایش: فهرست رسمی قوانین تغییر کرده است")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body+"؛ متن و اعتبار سند را در مرجع رسمی بررسی کنید."))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(8102,notification)
    }

    companion object {
        private const val WORK_NAME="payesh-official-legal-index"
        fun isEnabled(context:Context):Boolean =
            context.getSharedPreferences("preferences",Context.MODE_PRIVATE).getBoolean("legal_watch_enabled",false)

        fun setEnabled(context:Context,enabled:Boolean) {
            context.getSharedPreferences("preferences",Context.MODE_PRIVATE).edit()
                .putBoolean("legal_watch_enabled",enabled).apply()
            val manager=WorkManager.getInstance(context.applicationContext)
            if(!enabled) {
                manager.cancelUniqueWork(WORK_NAME)
                manager.cancelUniquePeriodicWork(WORK_NAME)
                return
            }
            val request=PeriodicWorkRequestBuilder<LegalUpdateWorker>(12,TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            manager.enqueueUniquePeriodicWork(WORK_NAME,ExistingPeriodicWorkPolicy.KEEP,request)
        }

        fun checkNow(context:Context) {
            WorkManager.getInstance(context.applicationContext)
                .enqueue(OneTimeWorkRequestBuilder<LegalUpdateWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build())
        }
    }
}
