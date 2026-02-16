package com.lostinspacebar.hinode

import com.lostinspacebar.hinode.data.TrixnityMatrixRepository

/**
 * Platform-specific factory for creating TrixnityMatrixRepository (migrated from MatrixRepository)
 * Each platform provides its own implementation
 */
expect fun createMatrixRepository(serverUrl: String = "https://matrix.org"): TrixnityMatrixRepository
