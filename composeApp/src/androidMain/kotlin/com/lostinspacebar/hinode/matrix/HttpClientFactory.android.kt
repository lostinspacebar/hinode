package com.lostinspacebar.hinode.matrix

import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Android-specific HTTP client creation
 */
actual fun createHttpClient(): HttpClient {
    return HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = false
                isLenient = true
                encodeDefaults = false
                explicitNulls = false
                coerceInputValues = true
                classDiscriminator = "type"
            })
        }

        install(WebSockets)
    }
}
