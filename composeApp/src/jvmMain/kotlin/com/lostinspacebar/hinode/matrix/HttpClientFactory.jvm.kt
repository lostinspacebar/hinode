package com.lostinspacebar.hinode.matrix

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.plugins.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * JVM-specific HTTP client creation
 *
 * NOTE: This implementation disables SSL certificate validation for development.
 * In production, you should properly configure SSL certificates.
 */
actual fun createHttpClient(): HttpClient {
    return HttpClient(CIO) {
        engine {
            https {
                trustManager = object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate>? = null
                }
            }
        }

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

        install(HttpTimeout) {
            requestTimeoutMillis = 60000  // 60 seconds default
            connectTimeoutMillis = 30000
            socketTimeoutMillis = 60000
        }
    }
}
