package com.lostinspacebar.hinode.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import com.lostinspacebar.hinode.db.HinodeDatabase
import org.w3c.dom.Worker

/**
 * Web/JS implementation of DatabaseDriverFactory
 * Uses Web Worker driver for better performance in browsers
 */
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        return WebWorkerDriver(
            Worker(
                js("""new URL("@cashapp/sqldelight-sqljs-worker/sqljs.worker.js", import.meta.url)""")
            )
        ).also { driver ->
            // Try to create schema, ignore error if tables already exist
            try {
                HinodeDatabase.Schema.create(driver)
            } catch (e: Exception) {
                // Tables likely already exist, continue
            }
        }
    }
}
