package com.lawyerup.app.data

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCipher {
    private val header = "LAWYERUP1".toByteArray()
    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        require(password.size >= 12) { "عبارت پشتیبان حداقل ۱۲ نویسه باشد" }
        val spec = PBEKeySpec(password,salt,210000,256)
        try { return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,"AES") }
        finally { spec.clearPassword() }
    }
    fun encrypt(text: String, password: CharArray): ByteArray {
        val salt=ByteArray(16).also(SecureRandom()::nextBytes)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key(password,salt))
        cipher.updateAAD(header)
        return header+salt+cipher.iv+cipher.doFinal(text.toByteArray(Charsets.UTF_8))
    }
    fun decrypt(bytes: ByteArray,password: CharArray): String {
        require(bytes.size in 54..25_000_000 && bytes.copyOfRange(0,9).contentEquals(header))
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(password,bytes.copyOfRange(9,25)),GCMParameterSpec(128,bytes.copyOfRange(25,37)))
        cipher.updateAAD(header)
        return String(cipher.doFinal(bytes.copyOfRange(37,bytes.size)),Charsets.UTF_8)
    }
}
