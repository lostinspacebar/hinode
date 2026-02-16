package com.lostinspacebar.hinode

import android.content.Context
import com.lostinspacebar.hinode.data.CredentialStorage
import com.lostinspacebar.hinode.data.DatabaseDriverFactory
import com.lostinspacebar.hinode.data.TrixnityMatrixRepository
import com.lostinspacebar.hinode.data.createDatabase

/**
 * Android singleton to hold application context
 */
object AndroidContext {
    lateinit var appContext: Context
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}

/**
 * Android implementation - creates TrixnityMatrixRepository with Android-specific dependencies
 */
actual fun createMatrixRepository(serverUrl: String): TrixnityMatrixRepository {
    val context = AndroidContext.appContext
    val driverFactory = DatabaseDriverFactory(context)
    val database = createDatabase(driverFactory)
    val credentialStorage = CredentialStorage(context)

    return TrixnityMatrixRepository(database, credentialStorage)
}
