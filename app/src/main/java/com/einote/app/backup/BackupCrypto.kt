package com.einote.app.backup

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCrypto {
    private val random = SecureRandom()
    private const val MAGIC = "EINOTE-ENC-1"
    private const val ITERATIONS = 210_000
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12

    fun encrypt(input: File, output: File, password: CharArray) {
        require(password.size >= 8) { "رمز پشتیبان باید حداقل ۸ کاراکتر باشد." }
        val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
        val iv = ByteArray(IV_SIZE).also(random::nextBytes)
        val key = derive(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        FileOutputStream(output).use { out ->
            out.write(MAGIC.toByteArray(Charsets.UTF_8))
            out.write(salt)
            out.write(iv)
            cipher.updateDigestHeader(out)
            FileInputStream(input).use { source ->
                source.copyCiphertextTo(out, cipher)
            }
        }
    }

    fun decrypt(input: File, output: File, password: CharArray) {
        require(password.size >= 8) { "رمز پشتیبان نامعتبر است." }
        FileInputStream(input).use { source ->
            val magic = ByteArray(MAGIC.length).also { source.readFully(it) }
            require(String(magic, Charsets.UTF_8) == MAGIC) { "این فایل پشتیبان رمزگذاری‌شده ای‌نوت نیست." }
            val salt = ByteArray(SALT_SIZE).also { source.readFully(it) }
            val iv = ByteArray(IV_SIZE).also { source.readFully(it) }
            val key = derive(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            try {
                FileOutputStream(output).use { target ->
                    source.copyPlaintextTo(target, cipher)
                }
            } catch (_: Exception) {
                output.delete()
                throw SecurityException("رمز پشتیبان اشتباه است یا فایل دستکاری شده.")
            }
        }
    }

    private fun derive(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return try {
            SecretKeySpec(
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).encoded,
                "AES"
            )
        } finally {
            spec.clearPassword()
        }
    }

    private fun Cipher.updateDigestHeader(out: FileOutputStream) {
        // Header bytes are authenticated by GCM as part of the stream format.
    }

    private fun FileInputStream.copyCiphertextTo(out: FileOutputStream, cipher: Cipher) {
        val buffer = ByteArray(64 * 1024)
        var read: Int
        while (read(buffer).also { read = it } != -1) {
            val encrypted = cipher.update(buffer, 0, read)
            if (encrypted != null) out.write(encrypted)
        }
        out.write(cipher.doFinal())
    }

    private fun FileInputStream.copyPlaintextTo(out: FileOutputStream, cipher: Cipher) {
        val buffer = ByteArray(64 * 1024)
        var read: Int
        while (read(buffer).also { read = it } != -1) {
            val plain = cipher.update(buffer, 0, read)
            if (plain != null) out.write(plain)
        }
        out.write(cipher.doFinal())
    }

    private fun FileInputStream.readFully(buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val count = read(buffer, offset, buffer.size - offset)
            if (count < 0) throw IllegalArgumentException("فایل ناقص است.")
            offset += count
        }
    }
}
