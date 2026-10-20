package com.lawyerup.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class PayeshSyncClientCryptoTest {
    @Test
    fun encryptedEnvelopeRoundTripsPersianWorkspace() {
        val phrase = "عبارت رمزگذاری مشترک پایش ۱۴۰۵".toCharArray()
        val plain = """{"format":1,"records":[],"tasks":[]}""".toByteArray(StandardCharsets.UTF_8)
        val encrypted = PayeshSyncClient.encrypt(plain, phrase)
        assertTrue(String(encrypted.copyOfRange(0, 8), StandardCharsets.US_ASCII) == "PAYESHE1")
        assertArrayEquals(plain, PayeshSyncClient.decrypt(encrypted, phrase))
    }

    @Test
    fun tamperedEnvelopeIsRejected() {
        val phrase = "correct horse battery staple".toCharArray()
        val encrypted = PayeshSyncClient.encrypt("محفوظ".toByteArray(StandardCharsets.UTF_8), phrase)
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { PayeshSyncClient.decrypt(encrypted, phrase) }
    }
}
