package com.lostinspacebar.hinode.data

import io.ktor.client.*

/**
 * Android implementation - HTTP client SSL configuration
 * Android typically has up-to-date CA certificates, so no special configuration needed
 */
actual fun configureHttpClientForSsl(config: HttpClientConfig<*>) {
    // No special configuration needed for Android
    // Android has proper CA certificates by default
}
