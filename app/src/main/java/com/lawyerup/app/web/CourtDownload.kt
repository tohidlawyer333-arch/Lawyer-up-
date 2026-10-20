package com.lawyerup.app.web

import com.lawyerup.app.domain.officialUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

object CourtDownload {
    suspend fun fetch(url: String, cookieForUrl: suspend (String) -> String): ByteArray = withContext(Dispatchers.IO) {
        require(officialUrl(url)) { "نشانی دریافت امن نیست" }
        var current = url
        repeat(4) {
            require(officialUrl(current)) { "تغییر مسیر خارج از دامنه رسمی متوقف شد" }
            val connection = URL(current).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("Cookie", cookieForUrl(current))
            try {
                if(connection.responseCode in listOf(301,302,303,307,308)) {
                    val location = requireNotNull(connection.getHeaderField("Location"))
                    current = URI(current).resolve(location).toString()
                } else {
                    require(connection.responseCode == 200) { "دریافت فایل کامل نشد" }
                    require(connection.contentLengthLong <= 25_000_000) { "فایل بیش از ۲۵ مگابایت است" }
                    val output = ByteArrayOutputStream()
                    connection.inputStream.use { input ->
                        val buffer=ByteArray(8192)
                        while(true) {val count=input.read(buffer);if(count<0) break;require(output.size()+count<=25_000_000);output.write(buffer,0,count)}
                    }
                    val bytes=output.toByteArray()
                    require(bytes.size>=5 && bytes.copyOfRange(0,5).contentEquals("%PDF-".toByteArray())) { "پاسخ، PDF معتبر نیست؛ ممکن است نشست منقضی شده باشد" }
                    return@withContext bytes
                }
            } finally { connection.disconnect() }
        }
        error("تغییر مسیر دریافت بیش از حد مجاز است")
    }
}
