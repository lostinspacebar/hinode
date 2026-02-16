package com.lostinspacebar.hinode.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.lostinspacebar.hinode.db.HinodeDatabase

/**
 * Android implementation of DatabaseDriverFactory
 * Uses Android SQLite driver
 */
actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(
            schema = HinodeDatabase.Schema,
            context = context,
            name = "hinode.db"
        )
    }
}
