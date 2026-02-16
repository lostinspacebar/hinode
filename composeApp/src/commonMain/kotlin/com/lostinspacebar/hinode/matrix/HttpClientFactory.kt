package com.lostinspacebar.hinode.matrix

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Platform-specific HTTP client creation with plugins already installed
 */
expect fun createHttpClient(): HttpClient

/**
 * Create a configured Matrix HTTP client with all plugins
 */
fun createMatrixHttpClient(): HttpClient {
    return createHttpClient()
}
