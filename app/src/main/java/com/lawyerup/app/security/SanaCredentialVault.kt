package com.lawyerup.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SanaCredentials(val nationalCode:String,val personalPassword:String)

object SanaCredentialVault {
    private const val PREFS="sana_credentials_secure"
    private const val BLOB="encrypted_credentials"
    private const val KEY_ALIAS="payesh_sana_credentials_v1"
    private const val AAD="payesh:sana:credentials:v1"

    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing=store.getKey(KEY_ALIAS,null) as? SecretKey
        if(existing!=null) return existing
        val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    fun save(context:Context,nationalCode:String,personalPassword:String) {
        require(nationalCode.matches(Regex("[0-9]{10}"))) { "کد ملی باید ۱۰ رقم باشد." }
        require(personalPassword.isNotBlank()) { "رمز شخصی خالی است." }
        val plain=JSONObject()
            .put("nationalCode",nationalCode)
            .put("personalPassword",personalPassword)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key())
        cipher.updateAAD(AAD.toByteArray(Charsets.UTF_8))
        val encrypted=cipher.iv+cipher.doFinal(plain)
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putString(BLOB,Base64.encodeToString(encrypted,Base64.NO_WRAP))
            .apply()
        plain.fill(0)
    }

    fun load(context:Context):SanaCredentials? = runCatching {
        val encoded=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(BLOB,null) ?: return null
        val all=Base64.decode(encoded,Base64.NO_WRAP)
        require(all.size>28)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,all.copyOfRange(0,12)))
        cipher.updateAAD(AAD.toByteArray(Charsets.UTF_8))
        val plain=cipher.doFinal(all.copyOfRange(12,all.size))
        try {
            val json=JSONObject(String(plain,Charsets.UTF_8))
            val code=json.optString("nationalCode")
            val password=json.optString("personalPassword")
            if(!code.matches(Regex("[0-9]{10}")) || password.isBlank()) null
            else SanaCredentials(code,password)
        } finally {
            plain.fill(0)
        }
    }.getOrNull()

    fun isSaved(context:Context):Boolean =
        !context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(BLOB,null).isNullOrBlank()

    fun clear(context:Context) {
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().remove(BLOB).apply()
    }
}
