package com.lostinspacebar.hinode.data

import kotlinx.browser.localStorage

/**
 * Web/JS implementation of CredentialStorage
 * Uses browser localStorage
 * TODO: Add encryption for production
 */
actual class CredentialStorage {
    actual suspend fun saveCredentials(serverUrl: String, userId: String, accessToken: String) {
        localStorage.setItem(KEY_SERVER_URL, serverUrl)
        localStorage.setItem(KEY_USER_ID, userId)
        localStorage.setItem(KEY_ACCESS_TOKEN, accessToken)
    }

    actual suspend fun loadCredentials(): SavedCredentials? {
        val serverUrl = localStorage.getItem(KEY_SERVER_URL)
        val userId = localStorage.getItem(KEY_USER_ID)
        val accessToken = localStorage.getItem(KEY_ACCESS_TOKEN)

        return if (serverUrl != null && userId != null && accessToken != null) {
            SavedCredentials(serverUrl, userId, accessToken)
        } else {
            null
        }
    }

    actual suspend fun clearCredentials() {
        localStorage.removeItem(KEY_SERVER_URL)
        localStorage.removeItem(KEY_USER_ID)
        localStorage.removeItem(KEY_ACCESS_TOKEN)
    }

    companion object {
        private const val KEY_SERVER_URL = "hinode_server_url"
        private const val KEY_USER_ID = "hinode_user_id"
        private const val KEY_ACCESS_TOKEN = "hinode_access_token"
    }
}
