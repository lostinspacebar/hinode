package com.lostinspacebar.hinode.data

import platform.Foundation.NSUserDefaults

/**
 * iOS implementation of CredentialStorage
 * Uses NSUserDefaults for simple storage
 * TODO: Migrate to Keychain for production security
 */
actual class CredentialStorage {
    private val userDefaults = NSUserDefaults.standardUserDefaults

    actual suspend fun saveCredentials(serverUrl: String, userId: String, accessToken: String) {
        userDefaults.setObject(serverUrl, KEY_SERVER_URL)
        userDefaults.setObject(userId, KEY_USER_ID)
        userDefaults.setObject(accessToken, KEY_ACCESS_TOKEN)
        userDefaults.synchronize()
    }

    actual suspend fun loadCredentials(): SavedCredentials? {
        val serverUrl = userDefaults.stringForKey(KEY_SERVER_URL)
        val userId = userDefaults.stringForKey(KEY_USER_ID)
        val accessToken = userDefaults.stringForKey(KEY_ACCESS_TOKEN)

        return if (serverUrl != null && userId != null && accessToken != null) {
            SavedCredentials(serverUrl, userId, accessToken)
        } else {
            null
        }
    }

    actual suspend fun clearCredentials() {
        userDefaults.removeObjectForKey(KEY_SERVER_URL)
        userDefaults.removeObjectForKey(KEY_USER_ID)
        userDefaults.removeObjectForKey(KEY_ACCESS_TOKEN)
        userDefaults.synchronize()
    }

    companion object {
        private const val KEY_SERVER_URL = "com.lostinspacebar.hinode.server_url"
        private const val KEY_USER_ID = "com.lostinspacebar.hinode.user_id"
        private const val KEY_ACCESS_TOKEN = "com.lostinspacebar.hinode.access_token"
    }
}
