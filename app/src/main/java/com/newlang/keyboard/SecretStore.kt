package com.newlang.keyboard

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores the API key encrypted with a key that lives in the Android Keystore (never leaves the phone). */
object SecretStore {
    private const val ALIAS = "kb_ai_key"
    private const val PREF = "aiKeyEnc"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val existing = ks.getKey(ALIAS, null)
        if (existing is SecretKey) return existing
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return kg.generateKey()
    }

    fun has(sp: SharedPreferences): Boolean = sp.contains(PREF)

    fun save(sp: SharedPreferences, plain: String) {
        if (plain.isEmpty()) {
            sp.edit().remove(PREF).apply()
            return
        }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        val packed = c.iv + ct
        sp.edit().putString(PREF, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun load(sp: SharedPreferences): String? {
        val s = sp.getString(PREF, null) ?: return null
        return try {
            val data = Base64.decode(s, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, 12))
            String(c.doFinal(data, 12, data.size - 12), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
