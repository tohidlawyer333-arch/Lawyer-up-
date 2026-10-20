package com.lawyerup.app.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Client-side encrypted sync protocol shared with Payesh.Desktop.
 * The server stores the envelope only and never receives the passphrase.
 */
object PayeshSyncClient {
    private val header = "PAYESHE1".toByteArray(StandardCharsets.US_ASCII)
    private val aad = "PAYESH-SYNC-V1".toByteArray(StandardCharsets.UTF_8)
    private const val rounds = 600_000

    suspend fun sync(repository: WorkspaceRepository, server: String, userId: String, token: String, passphrase: CharArray): String {
        require(userId.length in 1..100) { "شناسه کاربری معتبر نیست" }
        require(token.length in 24..512) { "توکن دسترسی معتبر نیست" }
        require(passphrase.size >= 12) { "عبارت رمزگذاری حداقل ۱۲ نویسه باشد" }
        val base = server.trim().trimEnd('/')
        val uri = runCatching { URI(base) }.getOrNull() ?: error("نشانی سرویس معتبر نیست")
        require(uri.scheme == "https") {
            "برای سرویس راه دور HTTPS الزامی است"
        }
        val localText = repository.export()
        val response = request(URL("$base/v1/sync/workspace"), userId, token, "GET", null)
        if (response.first == HttpURLConnection.HTTP_NOT_FOUND) {
            val envelope = encrypt(localText.toByteArray(StandardCharsets.UTF_8), passphrase)
            val put = JSONObject().put("expectedRevision", 0).put("payload", android.util.Base64.encodeToString(envelope, android.util.Base64.NO_WRAP)).toString()
            val created = request(URL("$base/v1/sync/workspace"), userId, token, "PUT", put)
            if (created.first !in 200..299) error("ساخت نسخه ابری ناموفق بود (HTTP ${created.first})")
            return "پشتیبان رمزگذاری‌شده اولیه در سرویس ایجاد شد"
        }
        if (response.first !in 200..299) error("دریافت نسخه ابری ناموفق بود (HTTP ${response.first})")
        val remote = JSONObject(response.second)
        val revision = remote.getLong("revision")
        val encoded = android.util.Base64.decode(remote.getString("payload"), android.util.Base64.NO_WRAP)
        val remoteText = String(decrypt(encoded, passphrase), StandardCharsets.UTF_8)
        if (remoteText != localText) repository.restore(remoteText)
        val merged = repository.export()
        if (merged == remoteText) return if (merged == localText) "نسخه محلی و ابری همگام هستند" else "داده‌های ابری با دفتر محلی ادغام شدند"
        val payload = JSONObject().put("expectedRevision", revision)
            .put("payload", android.util.Base64.encodeToString(encrypt(merged.toByteArray(StandardCharsets.UTF_8), passphrase), android.util.Base64.NO_WRAP)).toString()
        val uploaded = request(URL("$base/v1/sync/workspace"), userId, token, "PUT", payload)
        if (uploaded.first == HttpURLConnection.HTTP_CONFLICT) error("نسخه ابری هم‌زمان تغییر کرده؛ دوباره همگام‌سازی کنید")
        if (uploaded.first !in 200..299) error("ارسال نسخه ادغام‌شده ناموفق بود (HTTP ${uploaded.first})")
        return "دفتر ادغام و نسخه رمزگذاری‌شده به سرویس ارسال شد"
    }

    private fun request(url: URL, user: String, token: String, method: String, body: String?): Pair<Int, String> {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 20_000
            instanceFollowRedirects = false
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-Payesh-User", user)
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            return code to text
        } finally { connection.disconnect() }
    }

    private fun key(passphrase: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, rounds, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }

    private fun encrypt(plain: ByteArray, passphrase: CharArray): ByteArray {
        val salt = ByteArray(16).also(java.security.SecureRandom()::nextBytes)
        val nonce = ByteArray(12).also(java.security.SecureRandom()::nextBytes)
        val secret = key(passphrase, salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(aad)
            val combined = cipher.doFinal(plain)
            val data = combined.copyOfRange(0, combined.size - 16)
            val tag = combined.copyOfRange(combined.size - 16, combined.size)
            return header + salt + nonce + tag + data
        } finally { secret.fill(0) }
    }

    private fun decrypt(bytes: ByteArray, passphrase: CharArray): ByteArray {
        require(bytes.size in 52..25_000_000 && bytes.copyOfRange(0, 8).contentEquals(header)) { "قالب نسخه ابری معتبر نیست" }
        val salt = bytes.copyOfRange(8, 24)
        val nonce = bytes.copyOfRange(24, 36)
        val tag = bytes.copyOfRange(36, 52)
        val data = bytes.copyOfRange(52, bytes.size)
        val secret = key(passphrase, salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(aad)
            return cipher.doFinal(data + tag)
        } finally { secret.fill(0) }
    }
}
