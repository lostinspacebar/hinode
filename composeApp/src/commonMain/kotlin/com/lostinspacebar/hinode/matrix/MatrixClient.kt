package com.lostinspacebar.hinode.matrix

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlin.math.min
import kotlin.math.pow

/**
 * Matrix Client for communicating with Matrix homeserver
 */
class MatrixClient(
    private val baseUrl: String
) {
    private val httpClient = createMatrixHttpClient()

    private var accessToken: String? = null
    private var userId: String? = null
    private var syncToken: String? = null

    /**
     * Login to Matrix homeserver
     */
    suspend fun login(username: String, password: String): Result<LoginResponse> {
        return try {
            // Build JSON manually to ensure correct format
            val loginBody = buildMap<String, Any> {
                put("type", "m.login.password")
                put("user", username)
                put("password", password)
            }

            val response = httpClient.post("$baseUrl/_matrix/client/v3/login") {
                contentType(ContentType.Application.Json)
                setBody(loginBody)
            }

            if (response.status.isSuccess()) {
                val loginResponse: LoginResponse = response.body()
                accessToken = loginResponse.accessToken
                userId = loginResponse.userId
                Result.success(loginResponse)
            } else {
                val errorBody = response.bodyAsText()
                val error = try {
                    val matrixError: MatrixError = response.body()
                    "Login failed: ${matrixError.error} (${matrixError.errcode})"
                } catch (e: Exception) {
                    "Login failed: HTTP ${response.status.value} - $errorBody"
                }
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Login error: ${e.message}", e))
        }
    }

    /**
     * Logout from Matrix homeserver
     */
    suspend fun logout(): Result<Unit> {
        return try {
            val response = httpClient.post("$baseUrl/_matrix/client/v3/logout") {
                bearerAuth(accessToken ?: "")
            }

            if (response.status.isSuccess()) {
                accessToken = null
                userId = null
                syncToken = null
                Result.success(Unit)
            } else {
                Result.failure(Exception("Logout failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sync with Matrix homeserver
     * Returns a flow that continuously syncs with incremental updates
     */
    fun sync(timeout: Long = 30000): Flow<Result<SyncResponse>> = flow {
        var retryCount = 0
        val maxRetries = 5

        while (true) {
            try {
                val response = httpClient.get("$baseUrl/_matrix/client/v3/sync") {
                    bearerAuth(accessToken ?: "")
                    parameter("timeout", timeout)
                    if (syncToken != null) {
                        parameter("since", syncToken)
                    }
                }

                if (response.status.isSuccess()) {
                    try {
                        val syncResponse: SyncResponse = response.body()
                        syncToken = syncResponse.nextBatch
                        retryCount = 0 // Reset retry count on success
                        emit(Result.success(syncResponse))
                    } catch (e: Exception) {
                        println("MatrixClient: Failed to parse sync response: ${e.message}")
                        // Continue syncing despite parse errors
                        val backoffDelay = calculateBackoff(retryCount, maxRetries)
                        delay(backoffDelay)
                        retryCount = min(retryCount + 1, maxRetries)
                    }
                } else {
                    val error: MatrixError = response.body()
                    emit(Result.failure(Exception("Sync failed: ${error.error}")))

                    // Exponential backoff
                    val backoffDelay = calculateBackoff(retryCount, maxRetries)
                    delay(backoffDelay)
                    retryCount++
                }
            } catch (e: Exception) {
                // Check if it's a timeout (normal for long polling)
                val isTimeout = e.message?.contains("timeout", ignoreCase = true) == true

                if (isTimeout) {
                    // Timeout is normal for long polling, just retry immediately
                    println("MatrixClient: Sync timeout (normal), continuing...")
                    retryCount = 0
                } else {
                    // Other errors need backoff
                    println("MatrixClient: Sync error: ${e.message}")
                    emit(Result.failure(e))
                    val backoffDelay = calculateBackoff(retryCount, maxRetries)
                    delay(backoffDelay)
                    retryCount = min(retryCount + 1, maxRetries)
                }
            }
        }
    }

    /**
     * Send a text message to a room
     */
    suspend fun sendMessage(roomId: String, message: String): Result<SendMessageResponse> {
        return try {
            val encodedRoomId = roomId.encodeURLPath()
            val txnId = System.currentTimeMillis().toString()

            val response = httpClient.put(
                "$baseUrl/_matrix/client/v3/rooms/$encodedRoomId/send/m.room.message/$txnId"
            ) {
                bearerAuth(accessToken ?: "")
                contentType(ContentType.Application.Json)
                setBody(SendMessageRequest(body = message))
            }

            if (response.status.isSuccess()) {
                val sendResponse: SendMessageResponse = response.body()
                Result.success(sendResponse)
            } else {
                val error: MatrixError = response.body()
                Result.failure(Exception("Send message failed: ${error.error}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a new room
     */
    suspend fun createRoom(
        name: String,
        topic: String? = null,
        isDirect: Boolean = false,
        inviteUsers: List<String> = emptyList()
    ): Result<CreateRoomResponse> {
        return try {
            val response = httpClient.post("$baseUrl/_matrix/client/v3/createRoom") {
                bearerAuth(accessToken ?: "")
                contentType(ContentType.Application.Json)
                setBody(CreateRoomRequest(
                    name = name,
                    topic = topic,
                    isDirect = isDirect,
                    invite = inviteUsers
                ))
            }

            if (response.status.isSuccess()) {
                val createResponse: CreateRoomResponse = response.body()
                Result.success(createResponse)
            } else {
                val error: MatrixError = response.body()
                Result.failure(Exception("Create room failed: ${error.error}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Join a room by ID
     */
    suspend fun joinRoom(roomId: String): Result<Unit> {
        return try {
            val encodedRoomId = roomId.encodeURLPath()
            val response = httpClient.post(
                "$baseUrl/_matrix/client/v3/rooms/$encodedRoomId/join"
            ) {
                bearerAuth(accessToken ?: "")
            }

            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                val error: MatrixError = response.body()
                Result.failure(Exception("Join room failed: ${error.error}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Leave a room
     */
    suspend fun leaveRoom(roomId: String): Result<Unit> {
        return try {
            val encodedRoomId = roomId.encodeURLPath()
            val response = httpClient.post(
                "$baseUrl/_matrix/client/v3/rooms/$encodedRoomId/leave"
            ) {
                bearerAuth(accessToken ?: "")
            }

            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                val error: MatrixError = response.body()
                Result.failure(Exception("Leave room failed: ${error.error}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get paginated messages from a room
     * @param roomId The room ID
     * @param from Pagination token (optional, null for most recent)
     * @param limit Number of messages to fetch (default 50)
     * @param dir Direction: "b" for backwards, "f" for forwards
     */
    suspend fun getMessages(
        roomId: String,
        from: String? = null,
        limit: Int = 50,
        dir: String = "b"
    ): Result<MessagesResponse> {
        return try {
            val encodedRoomId = roomId.encodeURLPath()
            val response = httpClient.get(
                "$baseUrl/_matrix/client/v3/rooms/$encodedRoomId/messages"
            ) {
                bearerAuth(accessToken ?: "")
                parameter("limit", limit)
                parameter("dir", dir)
                if (from != null) {
                    parameter("from", from)
                }
            }

            if (response.status.isSuccess()) {
                val messagesResponse: MessagesResponse = response.body()
                Result.success(messagesResponse)
            } else {
                val error: MatrixError = response.body()
                Result.failure(Exception("Get messages failed: ${error.error}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the current user ID
     */
    fun getCurrentUserId(): String? = userId

    /**
     * Check if user is logged in
     */
    fun isLoggedIn(): Boolean = accessToken != null

    /**
     * Restore session from saved credentials
     */
    fun restoreSession(userId: String, accessToken: String) {
        this.userId = userId
        this.accessToken = accessToken
        this.syncToken = null // Start fresh sync
    }

    /**
     * Calculate exponential backoff delay
     */
    private fun calculateBackoff(retryCount: Int, maxRetries: Int): Long {
        val cappedRetry = min(retryCount, maxRetries)
        val baseDelay = 1000L // 1 second
        val maxDelay = 32000L // 32 seconds
        val calculatedDelay = baseDelay * (2.0.pow(cappedRetry.toDouble())).toLong()
        return min(calculatedDelay, maxDelay)
    }

    /**
     * Close the HTTP client
     */
    fun close() {
        httpClient.close()
    }
}
