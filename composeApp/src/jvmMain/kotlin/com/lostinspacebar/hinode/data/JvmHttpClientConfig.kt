package com.lostinspacebar.hinode.data

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.network.tls.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * JVM/Desktop implementation - Configure HTTP client to handle SSL certificates
 *
 * WARNING: This disables SSL certificate validation for DEVELOPMENT ONLY!
 * Do NOT use this in production - it makes your connection vulnerable to man-in-the-middle attacks.
 */
actual fun configureHttpClientForSsl(config: HttpClientConfig<*>) {
    // Install content negotiation for JSON
    config.install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }

    // Configure CIO engine to trust all certificates (DEVELOPMENT ONLY!)
    // This is necessary because some JVMs have outdated CA certificates
    @Suppress("UNCHECKED_CAST")
    (config as? HttpClientConfig<CIOEngineConfig>)?.engine {
        https {
            // Create a trust manager that trusts all certificates
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            // Install the all-trusting trust manager
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())

            trustManager = trustAllCerts[0] as X509TrustManager
        }
    }
}
