package com.lostinspacebar.hinode.data

import com.lostinspacebar.hinode.db.HinodeDatabase
import com.lostinspacebar.hinode.matrix.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Repository for Matrix protocol operations
 * Manages data flow between Matrix client, local database, and UI
 */
class MatrixRepository(
    private val database: HinodeDatabase,
    private val matrixClient: MatrixClient,
    private val credentialStorage: CredentialStorage
) {
    private val repositoryScope = CoroutineScope(Dispatchers.Default)

    // JSON parser for manual event parsing
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // StateFlows for reactive UI updates
    private val _rooms = MutableStateFlow<List<Room>>(emptyList())
    val rooms: StateFlow<List<Room>> = _rooms.asStateFlow()

    private val _messages = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    val messages: StateFlow<Map<String, List<Message>>> = _messages.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _syncErrors = MutableStateFlow<String?>(null)
    val syncErrors: StateFlow<String?> = _syncErrors.asStateFlow()

    // Pagination tokens per room (for loading older messages)
    private val paginationTokens = mutableMapOf<String, String>()

    // Track if we've reached the start of a room's history
    private val reachedRoomStart = mutableSetOf<String>()

    /**
     * Login to Matrix homeserver
     */
    suspend fun login(serverUrl: String, username: String, password: String): Result<Unit> {
        return try {
            _connectionStatus.value = ConnectionStatus.CONNECTING

            val loginResult = matrixClient.login(username, password)

            if (loginResult.isSuccess) {
                val loginResponse = loginResult.getOrNull()!!

                // Save credentials securely
                credentialStorage.saveCredentials(
                    serverUrl = serverUrl,
                    userId = loginResponse.userId,
                    accessToken = loginResponse.accessToken
                )

                // Update current user
                _currentUser.value = User(
                    userId = loginResponse.userId,
                    displayName = username
                )

                // Save server config to database
                database.hinodeDatabaseQueries.insertServerConfig(
                    id = "main",
                    serverUrl = serverUrl,
                    federationEnabled = 0,
                    createdAt = System.currentTimeMillis()
                )

                _connectionStatus.value = ConnectionStatus.CONNECTED

                // Start syncing
                startSync()

                Result.success(Unit)
            } else {
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                Result.failure(loginResult.exceptionOrNull() ?: Exception("Login failed"))
            }
        } catch (e: Exception) {
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            Result.failure(e)
        }
    }

    /**
     * Logout from Matrix homeserver
     */
    suspend fun logout(): Result<Unit> {
        return try {
            matrixClient.logout()
            credentialStorage.clearCredentials()
            _currentUser.value = null
            _rooms.value = emptyList()
            _messages.value = emptyMap()
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Start background sync with Matrix homeserver
     */
    private fun startSync() {
        repositoryScope.launch {
            _connectionStatus.value = ConnectionStatus.SYNCING
            println("MatrixRepository: Starting sync...")

            matrixClient.sync()
                .collect { result ->
                    if (result.isSuccess) {
                        val syncResponse = result.getOrNull()!!
                        println("MatrixRepository: Sync successful, processing...")
                        println("MatrixRepository: Rooms in sync: ${syncResponse.rooms?.join?.size ?: 0}")
                        processSyncResponse(syncResponse)
                        _syncErrors.value = null
                    } else {
                        val error = result.exceptionOrNull()?.message
                        println("MatrixRepository: Sync error: $error")
                        _syncErrors.value = error
                    }
                }
        }
    }

    /**
     * Process sync response and update local state
     */
    private suspend fun processSyncResponse(syncResponse: SyncResponse) {
        println("MatrixRepository: Processing sync response with ${syncResponse.rooms?.join?.size ?: 0} joined rooms")
        syncResponse.rooms?.join?.forEach { (roomId, joinedRoom) ->
            try {
                println("MatrixRepository: Processing room: $roomId")

                // Track room metadata
                var isSpace = false
                var roomName: String? = null

                // Process room state - parse events manually to handle unknown types
                joinedRoom.state?.events?.forEach { eventJson ->
                    val event = try {
                        json.decodeFromJsonElement(MatrixEvent.serializer(), eventJson)
                    } catch (e: Exception) {
                        // Skip unknown event types
                        null
                    }

                    event?.let { evt ->
                        when (evt) {
                            is MatrixEvent.RoomCreateEvent -> {
                                // Check if this is a space
                                isSpace = evt.content.type == "m.space"
                                println("MatrixRepository: Room $roomId is ${if (isSpace) "a space" else "a room"}")
                            }
                            is MatrixEvent.RoomNameEvent -> {
                                roomName = evt.content.name
                                println("MatrixRepository: Room $roomId has name: ${evt.content.name}")
                            }
                            is MatrixEvent.SpaceChildEvent -> {
                                // This room (roomId) contains a child (childRoomId)
                                println("MatrixRepository: Space $roomId contains child: ${evt.childRoomId}")
                                database.hinodeDatabaseQueries.updateRoomParentSpace(
                                    parentSpaceId = roomId,
                                    id = evt.childRoomId
                                )
                            }
                            is MatrixEvent.MemberEvent -> {
                                // Member joined/left event
                            }
                            else -> {}
                        }
                    }
                }

            // Insert or update the room/space in database if we don't have it yet
            val existingRoom = try {
                database.hinodeDatabaseQueries.selectRoomById(roomId).executeAsOneOrNull()
            } catch (e: Exception) {
                null
            }

            if (existingRoom == null && roomName != null) {
                println("MatrixRepository: Inserting new ${if (isSpace) "space" else "room"}: $roomName")
                database.hinodeDatabaseQueries.insertRoom(
                    id = roomId,
                    name = roomName,
                    topic = null,
                    avatarUrl = null,
                    lastMessage = null,
                    unreadCount = 0,
                    type = if (isSpace) "space" else "room",
                    parentSpaceId = null
                )
            }

            // Store pagination token for this room
            joinedRoom.timeline?.prevBatch?.let { token ->
                paginationTokens[roomId] = token
                println("MatrixRepository: Stored pagination token for room $roomId")
            }

            // Process room timeline (messages) - parse events manually to handle unknown types
            val messageCount = joinedRoom.timeline?.events?.size ?: 0
            if (messageCount > 0) {
                println("MatrixRepository: Room $roomId has $messageCount timeline events")
            }

            joinedRoom.timeline?.events?.forEach { eventJson ->
                val event = try {
                    json.decodeFromJsonElement(MatrixEvent.serializer(), eventJson)
                } catch (e: Exception) {
                    // Skip unknown event types
                    null
                }

                when (event) {
                    is MatrixEvent.MessageEvent -> {
                        println("MatrixRepository: Saving message in room $roomId: ${event.content.body.take(50)}")
                        val message = Message(
                            id = event.eventId,
                            roomId = roomId,  // Use roomId from parent context
                            sender = User(userId = event.sender),
                            content = MessageContent.Text(body = event.content.body),
                            timestamp = event.timestamp,
                            deliveryStatus = DeliveryStatus.DELIVERED
                        )

                        // Save to database
                        database.hinodeDatabaseQueries.insertMessage(
                            id = message.id,
                            roomId = message.roomId,
                            senderId = message.sender.userId,
                            content = when (val content = message.content) {
                                is MessageContent.Text -> content.body
                                is MessageContent.Image -> content.url
                                is MessageContent.File -> content.filename
                                is MessageContent.Notice -> content.body
                            },
                            timestamp = message.timestamp,
                            deliveryStatus = message.deliveryStatus.name
                        )

                        // Update last message for room
                        database.hinodeDatabaseQueries.updateRoomLastMessage(
                            lastMessage = (message.content as? MessageContent.Text)?.body ?: "",
                            id = roomId
                        )
                    }
                    else -> {
                        // Ignore other timeline events for now
                    }
                }
            }

                // Update unread count
                val unreadCount = joinedRoom.unreadNotifications?.notificationCount ?: 0
                database.hinodeDatabaseQueries.updateRoomUnreadCount(
                    unreadCount = unreadCount.toLong(),
                    id = roomId
                )
            } catch (e: Exception) {
                println("MatrixRepository: Error processing room $roomId: ${e.message}")
                // Continue processing other rooms
            }
        }

        // Reload rooms and messages from database
        loadRoomsFromDatabase()
        loadMessagesFromDatabase()
    }

    /**
     * Send a message to a room
     */
    suspend fun sendMessage(roomId: String, content: String): Result<Unit> {
        return try {
            // Save to database as pending
            val tempId = "temp_${System.currentTimeMillis()}"
            val currentUserId = matrixClient.getCurrentUserId() ?: return Result.failure(Exception("Not logged in"))

            database.hinodeDatabaseQueries.insertMessage(
                id = tempId,
                roomId = roomId,
                senderId = currentUserId,
                content = content,
                timestamp = System.currentTimeMillis(),
                deliveryStatus = DeliveryStatus.PENDING.name
            )

            loadMessagesFromDatabase()

            // Send to server
            val result = matrixClient.sendMessage(roomId, content)

            if (result.isSuccess) {
                val response = result.getOrNull()!!
                // Update with actual event ID
                database.hinodeDatabaseQueries.deleteMessage(tempId)
                database.hinodeDatabaseQueries.insertMessage(
                    id = response.eventId,
                    roomId = roomId,
                    senderId = currentUserId,
                    content = content,
                    timestamp = System.currentTimeMillis(),
                    deliveryStatus = DeliveryStatus.SENT.name
                )

                loadMessagesFromDatabase()
                Result.success(Unit)
            } else {
                // Mark as failed
                database.hinodeDatabaseQueries.updateMessageStatus(
                    deliveryStatus = DeliveryStatus.FAILED.name,
                    id = tempId
                )
                loadMessagesFromDatabase()
                Result.failure(result.exceptionOrNull() ?: Exception("Send failed"))
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
    ): Result<String> {
        return try {
            println("MatrixRepository: Creating room '$name'...")
            val result = matrixClient.createRoom(name, topic, isDirect, inviteUsers)

            if (result.isSuccess) {
                val response = result.getOrNull()!!
                println("MatrixRepository: Room created with ID: ${response.roomId}")
                // Add room to database
                database.hinodeDatabaseQueries.insertRoom(
                    id = response.roomId,
                    name = name,
                    topic = topic,
                    avatarUrl = null,
                    lastMessage = null,
                    unreadCount = 0,
                    type = "room",
                    parentSpaceId = null
                )

                loadRoomsFromDatabase()
                Result.success(response.roomId)
            } else {
                val error = result.exceptionOrNull() ?: Exception("Create room failed")
                println("MatrixRepository: Failed to create room: ${error.message}")
                Result.failure(error)
            }
        } catch (e: Exception) {
            println("MatrixRepository: Exception creating room: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Join an existing room
     */
    suspend fun joinRoom(roomId: String): Result<Unit> {
        return matrixClient.joinRoom(roomId)
    }

    /**
     * Leave a room
     */
    suspend fun leaveRoom(roomId: String): Result<Unit> {
        val result = matrixClient.leaveRoom(roomId)
        if (result.isSuccess) {
            database.hinodeDatabaseQueries.deleteRoom(roomId)
            database.hinodeDatabaseQueries.deleteMessagesByRoom(roomId)
            loadRoomsFromDatabase()
            loadMessagesFromDatabase()
        }
        return result
    }

    /**
     * Load rooms from database
     */
    private fun loadRoomsFromDatabase() {
        val roomsList = database.hinodeDatabaseQueries.selectAllRooms()
            .executeAsList()
            .map { room ->
                Room(
                    id = room.id,
                    name = room.name,
                    topic = room.topic ?: "",
                    avatarUrl = room.avatarUrl,
                    lastMessage = room.lastMessage,
                    unreadCount = room.unreadCount.toInt(),
                    type = when (room.type) {
                        "space" -> RoomType.SPACE
                        else -> RoomType.ROOM
                    },
                    parentSpaceId = room.parentSpaceId
                )
            }
        println("MatrixRepository: Loaded ${roomsList.size} rooms/spaces from database")
        println("MatrixRepository: Spaces: ${roomsList.count { it.type == RoomType.SPACE }}, Rooms: ${roomsList.count { it.type == RoomType.ROOM }}")
        _rooms.value = roomsList
    }

    /**
     * Load messages from database
     */
    private fun loadMessagesFromDatabase() {
        val allMessages = database.hinodeDatabaseQueries.selectAllMessages()
            .executeAsList()

        val messagesByRoom = allMessages.groupBy { it.roomId }
            .mapValues { (_, messages) ->
                messages.map { dbMessage ->
                    Message(
                        id = dbMessage.id,
                        roomId = dbMessage.roomId,
                        sender = User(userId = dbMessage.senderId),
                        content = MessageContent.Text(body = dbMessage.content),
                        timestamp = dbMessage.timestamp,
                        deliveryStatus = DeliveryStatus.valueOf(dbMessage.deliveryStatus)
                    )
                }
            }

        println("MatrixRepository: Loaded ${allMessages.size} total messages from database")
        messagesByRoom.forEach { (roomId, messages) ->
            println("MatrixRepository: Room $roomId has ${messages.size} messages")
        }
        _messages.value = messagesByRoom
    }

    /**
     * Get messages for a specific room
     */
    fun getMessagesForRoom(roomId: String): Flow<List<Message>> {
        return messages.map { it[roomId] ?: emptyList() }
    }

    /**
     * Load more (older) messages for a room
     */
    suspend fun loadMoreMessages(roomId: String): Result<Int> {
        return try {
            // Check if we've reached the start
            if (reachedRoomStart.contains(roomId)) {
                println("MatrixRepository: Already at start of room $roomId")
                return Result.success(0)
            }

            // Get pagination token
            val from = paginationTokens[roomId]
            if (from == null) {
                println("MatrixRepository: No pagination token for room $roomId yet")
                return Result.success(0)
            }

            println("MatrixRepository: Loading more messages for room $roomId from token ${from.take(20)}...")

            val result = matrixClient.getMessages(roomId, from = from, limit = 50)

            if (result.isSuccess) {
                val response = result.getOrNull()!!

                // Update pagination token
                if (response.end != null) {
                    paginationTokens[roomId] = response.end
                } else {
                    // No more messages
                    reachedRoomStart.add(roomId)
                    println("MatrixRepository: Reached start of room $roomId")
                }

                var savedCount = 0

                // Process and save messages
                response.chunk.forEach { eventJson ->
                    val event = try {
                        json.decodeFromJsonElement(MatrixEvent.serializer(), eventJson)
                    } catch (e: Exception) {
                        null
                    }

                    when (event) {
                        is MatrixEvent.MessageEvent -> {
                            // Save to database
                            database.hinodeDatabaseQueries.insertMessage(
                                id = event.eventId,
                                roomId = roomId,
                                senderId = event.sender,
                                content = event.content.body,
                                timestamp = event.timestamp,
                                deliveryStatus = DeliveryStatus.DELIVERED.name
                            )
                            savedCount++
                        }
                        else -> {
                            // Ignore non-message events
                        }
                    }
                }

                println("MatrixRepository: Loaded and saved $savedCount messages")

                // Reload messages from database
                loadMessagesFromDatabase()

                Result.success(savedCount)
            } else {
                val error = result.exceptionOrNull() ?: Exception("Failed to load messages")
                println("MatrixRepository: Error loading messages: ${error.message}")
                Result.failure(error)
            }
        } catch (e: Exception) {
            println("MatrixRepository: Exception loading more messages: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Check if more messages can be loaded for a room
     */
    fun canLoadMoreMessages(roomId: String): Boolean {
        return paginationTokens.containsKey(roomId) && !reachedRoomStart.contains(roomId)
    }

    /**
     * Check if user is logged in
     */
    fun isLoggedIn(): Boolean = matrixClient.isLoggedIn()

    /**
     * Try to restore session from saved credentials
     */
    suspend fun tryRestoreSession(): Result<Unit> {
        return try {
            println("MatrixRepository: Attempting to restore session...")
            val savedCredentials = credentialStorage.loadCredentials()

            if (savedCredentials != null) {
                println("MatrixRepository: Found saved credentials for ${savedCredentials.userId}")

                // Restore the MatrixClient state
                matrixClient.restoreSession(
                    userId = savedCredentials.userId,
                    accessToken = savedCredentials.accessToken
                )

                // Update current user
                _currentUser.value = User(
                    userId = savedCredentials.userId,
                    displayName = savedCredentials.userId.substringAfter("@").substringBefore(":")
                )

                _connectionStatus.value = ConnectionStatus.CONNECTED

                // Start syncing
                startSync()

                println("MatrixRepository: Session restored successfully")
                Result.success(Unit)
            } else {
                println("MatrixRepository: No saved credentials found")
                Result.failure(Exception("No saved credentials"))
            }
        } catch (e: Exception) {
            println("MatrixRepository: Failed to restore session: ${e.message}")
            Result.failure(e)
        }
    }
}

/**
 * Connection status enum
 */
enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    SYNCING,
    ERROR
}

/**
 * Platform-specific credential storage
 * Must be implemented for each platform
 */
expect class CredentialStorage {
    suspend fun saveCredentials(serverUrl: String, userId: String, accessToken: String)
    suspend fun loadCredentials(): SavedCredentials?
    suspend fun clearCredentials()
}

/**
 * Saved credentials data class
 */
data class SavedCredentials(
    val serverUrl: String,
    val userId: String,
    val accessToken: String
)
