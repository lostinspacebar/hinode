package com.lostinspacebar.hinode

import com.lostinspacebar.hinode.data.CredentialStorage
import com.lostinspacebar.hinode.data.DatabaseDriverFactory
import com.lostinspacebar.hinode.data.TrixnityMatrixRepository
import com.lostinspacebar.hinode.data.createDatabase

/**
 * iOS implementation - creates TrixnityMatrixRepository with iOS-specific dependencies
 */
actual fun createMatrixRepository(serverUrl: String): TrixnityMatrixRepository {
    val driverFactory = DatabaseDriverFactory()
    val database = createDatabase(driverFactory)
    val credentialStorage = CredentialStorage()

    return TrixnityMatrixRepository(database, credentialStorage)
}
