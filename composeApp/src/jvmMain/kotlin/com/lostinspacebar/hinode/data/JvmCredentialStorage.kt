package com.lostinspacebar.hinode.data

import java.io.File
import java.util.prefs.Preferences
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import java.util.Base64

/**
 * JVM/Desktop implementation of CredentialStorage
 * Uses Java Preferences API with AES encryption for secure storage
 */
actual class CredentialStorage {
    private val prefs = Preferences.userNodeForPackage(CredentialStorage::class.java)

    private val encryptionKey: SecretKey by lazy {
        val keyBytes = prefs.getByteArray(KEY_ENCRYPTION_KEY, null)
        if (keyBytes != null) {
            SecretKeySpec(keyBytes, "AES")
        } else {
            // Generate new key
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256)
            val key = keyGen.generateKey()
            prefs.putByteArray(KEY_ENCRYPTION_KEY, key.encoded)
            key
        }
    }

    actual suspend fun saveCredentials(serverUrl: String, userId: String, accessToken: String) {
        prefs.put(KEY_SERVER_URL, encrypt(serverUrl))
        prefs.put(KEY_USER_ID, encrypt(userId))
        prefs.put(KEY_ACCESS_TOKEN, encrypt(accessToken))
        prefs.flush()
    }

    actual suspend fun loadCredentials(): SavedCredentials? {
        val serverUrl = prefs.get(KEY_SERVER_URL, null)?.let { decrypt(it) }
        val userId = prefs.get(KEY_USER_ID, null)?.let { decrypt(it) }
        val accessToken = prefs.get(KEY_ACCESS_TOKEN, null)?.let { decrypt(it) }

        return if (serverUrl != null && userId != null && accessToken != null) {
            SavedCredentials(serverUrl, userId, accessToken)
        } else {
            null
        }
    }

    actual suspend fun clearCredentials() {
        prefs.remove(KEY_SERVER_URL)
        prefs.remove(KEY_USER_ID)
        prefs.remove(KEY_ACCESS_TOKEN)
        prefs.flush()
    }

    private fun encrypt(data: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, spec)
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        val combined = iv + encrypted
        return Base64.getEncoder().encodeToString(combined)
    }

    private fun decrypt(data: String): String {
        val combined = Base64.getDecoder().decode(data)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, spec)
        val decrypted = cipher.doFinal(encrypted)
        return String(decrypted, Charsets.UTF_8)
    }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_ENCRYPTION_KEY = "encryption_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
    }
}
