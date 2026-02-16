package com.lostinspacebar.hinode

import com.lostinspacebar.hinode.data.CredentialStorage
import com.lostinspacebar.hinode.data.DatabaseDriverFactory
import com.lostinspacebar.hinode.data.TrixnityMatrixRepository
import com.lostinspacebar.hinode.data.createDatabase

/**
 * Web/JS implementation - creates TrixnityMatrixRepository with web-specific dependencies
 */
actual fun createMatrixRepository(serverUrl: String): TrixnityMatrixRepository {
    val driverFactory = DatabaseDriverFactory()
    val database = createDatabase(driverFactory)
    val credentialStorage = CredentialStorage()

    return TrixnityMatrixRepository(database, credentialStorage)
}
