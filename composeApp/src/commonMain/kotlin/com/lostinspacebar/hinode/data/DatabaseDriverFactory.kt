package com.lostinspacebar.hinode.data

import app.cash.sqldelight.db.SqlDriver
import com.lostinspacebar.hinode.db.HinodeDatabase

/**
 * Platform-specific database driver factory
 * Each platform will provide its own implementation
 */
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

/**
 * Create database instance
 */
fun createDatabase(driverFactory: DatabaseDriverFactory): HinodeDatabase {
    val driver = driverFactory.createDriver()
    return HinodeDatabase(driver)
}
