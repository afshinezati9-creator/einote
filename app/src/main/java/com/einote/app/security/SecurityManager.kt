package com.einote.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import android.util.Base64
import java.security.MessageDigest

class SecurityManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("einote_security", Context.MODE_PRIVATE)
    private val keyAlias = "eiNote_security_key"
    private val iterations = 120_000

    fun isLockEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)
    fun setLockEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()

    fun autoLockMinutes(): Int = prefs.getInt(KEY_AUTO_LOCK_MINUTES, 5)
    fun setAutoLockMinutes(value: Int) = prefs.edit().putInt(KEY_AUTO_LOCK_MINUTES, value.coerceIn(1, 60)).apply()

    fun hasPin(): Boolean = prefs.contains(KEY_VERIFIER)

    fun setPin(pin: String) {
        require(pin.length >= 4) { "رمز باید حداقل ۴ رقم باشد." }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val verifier = derive(pin, salt)
        val payload = Base64.encodeToString(salt + verifier, Base64.NO_WRAP)
        prefs.edit().putString(KEY_VERIFIER, encrypt(payload)).apply()
    }

    fun verifyPin(pin: String): Boolean {\n        val now = System.currentTimeMillis()\n        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)\n        if (lockoutUntil > now) return false
        val stored = prefs.getString(KEY_VERIFIER, null) ?: return false
        return runCatching {
            val decoded = Base64.decode(decrypt(stored), Base64.NO_WRAP)
            if (decoded.size != 48) return false
            val salt = decoded.copyOfRange(0, 16)
            val expected = decoded.copyOfRange(16, 48)
            MessageDigest.isEqual(expected, derive(pin, salt))
        }.getOrDefault(false)
    }

    fun removePin() {
        prefs.edit().remove(KEY_VERIFIER).putBoolean(KEY_ENABLED, false).apply()
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded.also { spec.clearPassword() }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.getKey(keyAlias, null)?.let { return it as SecretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
             .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
             .setRandomizedEncryptionRequired(true)
             .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val all = Base64.decode(value, Base64.NO_WRAP)
        val iv = all.copyOfRange(0, 12)
        val data = all.copyOfRange(12, all.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(data).toString(Charsets.UTF_8)
    }

    companion object {
        private const val KEY_ENABLED = "lock_enabled"
        private const val KEY_VERIFIER = "pin_verifier"
        private const val KEY_AUTO_LOCK_MINUTES = "auto_lock_minutes"\n        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"\n        private const val KEY_LOCKOUT_UNTIL = "lockout_until"
    }
}
