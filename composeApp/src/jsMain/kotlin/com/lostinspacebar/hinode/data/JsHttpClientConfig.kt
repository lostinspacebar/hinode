package com.lostinspacebar.hinode.data

import io.ktor.client.*

/**
 * JS/Web implementation - HTTP client SSL configuration
 * Browsers handle SSL/TLS automatically, so no special configuration needed
 */
actual fun configureHttpClientForSsl(config: HttpClientConfig<*>) {
    // No special configuration needed for Web/JS
    // Browsers handle SSL/TLS
}
