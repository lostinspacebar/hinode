package com.lostinspacebar.hinode.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.lostinspacebar.hinode.db.HinodeDatabase

/**
 * iOS implementation of DatabaseDriverFactory
 * Uses Native SQLite driver for iOS
 */
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(
            schema = HinodeDatabase.Schema,
            name = "hinode.db"
        )
    }
}
