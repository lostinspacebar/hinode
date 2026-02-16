package com.lostinspacebar.hinode.data

import io.ktor.client.*

/**
 * iOS implementation - HTTP client SSL configuration
 * iOS uses system certificates, so no special configuration needed
 */
actual fun configureHttpClientForSsl(config: HttpClientConfig<*>) {
    // No special configuration needed for iOS
    // iOS uses system CA certificates
}
