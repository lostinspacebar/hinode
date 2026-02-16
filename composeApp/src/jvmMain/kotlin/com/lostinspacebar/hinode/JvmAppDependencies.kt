package com.lostinspacebar.hinode

import com.lostinspacebar.hinode.data.CredentialStorage
import com.lostinspacebar.hinode.data.DatabaseDriverFactory
import com.lostinspacebar.hinode.data.TrixnityMatrixRepository
import com.lostinspacebar.hinode.data.createDatabase

/**
 * JVM/Desktop implementation - creates TrixnityMatrixRepository with desktop-specific dependencies
 */
actual fun createMatrixRepository(serverUrl: String): TrixnityMatrixRepository {
    val driverFactory = DatabaseDriverFactory()
    val database = createDatabase(driverFactory)
    val credentialStorage = CredentialStorage()

    // Use TrixnityMatrixRepository which uses Trixnity SDK internally
    return TrixnityMatrixRepository(database, credentialStorage)
}
