package com.lostinspacebar.hinode.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.lostinspacebar.hinode.db.HinodeDatabase
import java.io.File

/**
 * JVM/Desktop implementation of DatabaseDriverFactory
 * Uses JDBC SQLite driver
 */
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        // Store database in user's home directory
        val homeDir = System.getProperty("user.home")
        val appDir = File(homeDir, ".hinode")
        if (!appDir.exists()) {
            appDir.mkdirs()
        }

        val databasePath = File(appDir, "hinode.db")
        val databaseExists = databasePath.exists() && databasePath.length() > 0

        val driver = JdbcSqliteDriver("jdbc:sqlite:${databasePath.absolutePath}")

        // Create tables only if database is new
        if (!databaseExists) {
            HinodeDatabase.Schema.create(driver)
        }

        return driver
    }
}
