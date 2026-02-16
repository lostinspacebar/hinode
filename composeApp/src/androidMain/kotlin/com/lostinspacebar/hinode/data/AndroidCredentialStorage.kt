package com.lostinspacebar.hinode.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Android implementation of CredentialStorage
 * Uses EncryptedSharedPreferences for secure storage
 */
actual class CredentialStorage(private val context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "hinode_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    actual suspend fun saveCredentials(serverUrl: String, userId: String, accessToken: String) {
        sharedPreferences.edit().apply {
            putString(KEY_SERVER_URL, serverUrl)
            putString(KEY_USER_ID, userId)
            putString(KEY_ACCESS_TOKEN, accessToken)
            apply()
        }
    }

    actual suspend fun loadCredentials(): SavedCredentials? {
        val serverUrl = sharedPreferences.getString(KEY_SERVER_URL, null)
        val userId = sharedPreferences.getString(KEY_USER_ID, null)
        val accessToken = sharedPreferences.getString(KEY_ACCESS_TOKEN, null)

        return if (serverUrl != null && userId != null && accessToken != null) {
            SavedCredentials(serverUrl, userId, accessToken)
        } else {
            null
        }
    }

    actual suspend fun clearCredentials() {
        sharedPreferences.edit().clear().apply()
    }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_ACCESS_TOKEN = "access_token"
    }
}
