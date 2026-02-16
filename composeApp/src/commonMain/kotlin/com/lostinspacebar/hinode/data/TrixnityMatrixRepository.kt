package com.lostinspacebar.hinode.data

import com.lostinspacebar.hinode.db.HinodeDatabase
import com.lostinspacebar.hinode.matrix.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.loginWithPassword
import net.folivo.trixnity.client.media.createInMemoryMediaStoreModule
import net.folivo.trixnity.client.room.getAllState
import net.folivo.trixnity.client.store.repository.createInMemoryRepositoriesModule
import net.folivo.trixnity.client.store.type
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

/**
 * Platform-specific HTTP client configuration for SSL
 */
expect fun configureHttpClientForSsl(config: io.ktor.client.HttpClientConfig<*>)

/**
 * Repository using Trixnity Matrix SDK (version 4.22.7)
 */
class TrixnityMatrixRepository(
    private val database: HinodeDatabase,
    private val credentialStorage: CredentialStorage
) {
    private val repositoryScope = CoroutineScope(Dispatchers.Default)

    private var matrixClient: MatrixClient? = null

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

    /**
     * Login to Matrix homeserver
     */
    suspend fun login(serverUrl: String, username: String, password: String): Result<Unit> {
        return try {
            _connectionStatus.value = ConnectionStatus.CONNECTING
            println("TrixnityMatrixRepository: Logging in to $serverUrl as $username")

            // Create and login with Trixnity MatrixClient using the correct API
            val loginResult = MatrixClient.loginWithPassword(
                baseUrl = Url(serverUrl),
                identifier = IdentifierType.User(username),
                password = password,
                repositoriesModule = createInMemoryRepositoriesModule(),
                mediaStoreModule = createInMemoryMediaStoreModule(),
                coroutineContext = Dispatchers.Default,
                configuration = {
                    // Configure HTTP client with platform-specific SSL settings
                    httpClientConfig = {
                        configureHttpClientForSsl(this)
                    }
                }
            )

            if (loginResult.isFailure) {
                val error = loginResult.exceptionOrNull() ?: Exception("Login failed")
                println("TrixnityMatrixRepository: Login failed: ${error.message}")
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                return Result.failure(error)
            }

            val client = loginResult.getOrThrow()
            matrixClient = client

            // Get user ID from client
            val userId = client.userId.toString()
            println("TrixnityMatrixRepository: Logged in as $userId")

            // Note: In Trixnity 4.22.7, access tokens are managed internally by the client
            // We'll save minimal credentials for session restoration
            credentialStorage.saveCredentials(
                serverUrl = serverUrl,
                userId = userId,
                accessToken = "" // Trixnity manages tokens internally
            )

            // Update current user
            _currentUser.value = User(
                userId = userId,
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
        } catch (e: Exception) {
            println("TrixnityMatrixRepository: Login exception: ${e.message}")
            e.printStackTrace()
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            Result.failure(e)
        }
    }

    /**
     * Start background sync with Matrix homeserver
     */
    private fun startSync() {
        repositoryScope.launch {
            try {
                val client = matrixClient
                if (client == null) {
                    println("TrixnityMatrixRepository: Cannot start sync - no client")
                    return@launch
                }

                _connectionStatus.value = ConnectionStatus.SYNCING
                println("TrixnityMatrixRepository: Starting sync...")

                // Start sync - this will run indefinitely
                client.startSync()

                // Get stores from DI container
                val roomStore = client.di.get<net.folivo.trixnity.client.store.RoomStore>()
                val roomService = client.di.get<net.folivo.trixnity.client.room.RoomService>()

                // Subscribe to room updates
                launch {
                    roomStore.getAll().collect { roomsMap ->
                        roomsMap.forEach { (roomId, roomFlow) ->
                            launch {
                                roomFlow.collect { trixnityRoom ->
                                    if (trixnityRoom != null) {
                                        val isSpace = trixnityRoom.type?.name == "m.space"

                                        // Convert Trixnity Room to our Room model
                                        val room = Room(
                                            id = trixnityRoom.roomId.full,
                                            name = trixnityRoom.name?.explicitName ?: trixnityRoom.roomId.full,
                                            topic = "",
                                            avatarUrl = trixnityRoom.avatarUrl,
                                            lastMessage = null,
                                            unreadCount = trixnityRoom.unreadMessageCount.toInt(),
                                            type = if (isSpace) RoomType.SPACE else RoomType.ROOM,
                                            parentSpaceId = null // Will be set by space child events
                                        )

                                        // Store in database
                                        database.hinodeDatabaseQueries.insertRoom(
                                            id = room.id,
                                            name = room.name,
                                            topic = room.topic,
                                            avatarUrl = room.avatarUrl,
                                            lastMessage = room.lastMessage,
                                            unreadCount = room.unreadCount.toLong(),
                                            type = if (room.type == RoomType.SPACE) "space" else "room",
                                            parentSpaceId = room.parentSpaceId
                                        )

                                        println("TrixnityMatrixRepository: Updated ${if (isSpace) "space" else "room"}: ${room.name}")

                                        // Subscribe to timeline events for regular rooms (not spaces)
                                        if (!isSpace) {
                                            subscribeToRoomTimeline(roomId, roomService)
                                        }
                                    }
                                }
                            }
                        }

                        // Reload rooms from database
                        loadRoomsFromDatabase()
                    }
                }

                println("TrixnityMatrixRepository: Sync started successfully")

            } catch (e: Exception) {
                println("TrixnityMatrixRepository: Sync error: ${e.message}")
                _syncErrors.value = e.message
                e.printStackTrace()
            }
        }
    }

    // TODO: Implement space hierarchy tracking
    // For now, spaces won't show their child rooms nested

    /**
     * Subscribe to timeline events for a specific room
     */
    private fun subscribeToRoomTimeline(
        roomId: RoomId,
        roomService: net.folivo.trixnity.client.room.RoomService
    ) {
        repositoryScope.launch {
            try {
                println("TrixnityMatrixRepository: Subscribing to timeline for room ${roomId.full}")

                // Get last timeline events for the room
                roomService.getLastTimelineEvents(roomId) {
                    minSize = 20 // Load at least 20 messages
                    maxSize = 50 // But not more than 50
                }.collect { timelineEventsFlowOrNull ->
                    if (timelineEventsFlowOrNull == null) {
                        println("TrixnityMatrixRepository: No timeline events flow for ${roomId.full}")
                        return@collect
                    }

                    println("TrixnityMatrixRepository: Got timeline events flow for ${roomId.full}")

                    // timelineEventsFlowOrNull is Flow<Flow<TimelineEvent>>
                    timelineEventsFlowOrNull.collect { timelineEventFlow ->
                        println("TrixnityMatrixRepository: Processing timeline event flow for ${roomId.full}")

                        // timelineEventFlow is Flow<TimelineEvent> - one flow per message
                        // Launch separate coroutine to collect each message flow
                        launch {
                            timelineEventFlow.collect { timelineEvent ->
                                // Process the timeline event
                                val event = timelineEvent.event
                                val content = timelineEvent.content?.getOrNull()

                                // Debug: log event type
                                println("TrixnityMatrixRepository: Event type in ${roomId.full}: ${event::class.simpleName}")

                                // Only process message events
                                if (event is net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent<*>) {
                                    if (content is net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent) {
                                        val body = content.body

                                        // Save message to database
                                        database.hinodeDatabaseQueries.insertMessage(
                                            id = event.id.full,
                                            roomId = roomId.full,
                                            senderId = event.sender.full,
                                            content = body,
                                            timestamp = event.originTimestamp,
                                            deliveryStatus = DeliveryStatus.DELIVERED.name
                                        )

                                        println("TrixnityMatrixRepository: Saved message in ${roomId.full}: ${body.take(50)}")

                                        // Reload messages from database
                                        loadMessagesFromDatabase()
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                println("TrixnityMatrixRepository: Error subscribing to timeline for ${roomId.full}: ${e.message}")
                e.printStackTrace()
            }
        }
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
        println("TrixnityMatrixRepository: Loaded ${roomsList.size} rooms/spaces from database")
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

        println("TrixnityMatrixRepository: Loaded ${allMessages.size} total messages from database")
        _messages.value = messagesByRoom
    }

    /**
     * Logout from Matrix homeserver
     */
    suspend fun logout(): Result<Unit> {
        return try {
            val client = matrixClient
            if (client != null) {
                client.logout().getOrThrow()
                client.close()
            }
            matrixClient = null
            credentialStorage.clearCredentials()
            _currentUser.value = null
            _rooms.value = emptyList()
            _messages.value = emptyMap()
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            Result.success(Unit)
        } catch (e: Exception) {
            println("TrixnityMatrixRepository: Logout error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Send a message to a room
     */
    suspend fun sendMessage(roomId: String, content: String): Result<Unit> {
        return try {
            val client = matrixClient ?: return Result.failure(Exception("Not logged in"))

            // Access RoomService via DI container
            val roomService = client.di.get<net.folivo.trixnity.client.room.RoomService>()

            // TODO: Use RoomService to send text message to the room
            // For now, just indicate the feature is not yet implemented
            println("TrixnityMatrixRepository: Sending message to room $roomId")
            Result.failure(Exception("Send message not yet fully implemented"))
        } catch (e: Exception) {
            println("TrixnityMatrixRepository: Send message error: ${e.message}")
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
            val client = matrixClient ?: return Result.failure(Exception("Not logged in"))

            // TODO: Use Trixnity's room creation API
            println("TrixnityMatrixRepository: Creating room '$name'")
            Result.failure(Exception("Room creation not yet implemented"))
        } catch (e: Exception) {
            println("TrixnityMatrixRepository: Create room error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Join an existing room
     */
    suspend fun joinRoom(roomId: String): Result<Unit> {
        return try {
            val client = matrixClient ?: return Result.failure(Exception("Not logged in"))

            // TODO: Use Trixnity's room join API
            println("TrixnityMatrixRepository: Joining room $roomId")
            Result.failure(Exception("Room join not yet implemented"))
        } catch (e: Exception) {
            println("TrixnityMatrixRepository: Join room error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Leave a room
     */
    suspend fun leaveRoom(roomId: String): Result<Unit> {
        return try {
            val client = matrixClient ?: return Result.failure(Exception("Not logged in"))

            // TODO: Use Trixnity's room leave API
            println("TrixnityMatrixRepository: Leaving room $roomId")
            Result.failure(Exception("Room leave not yet implemented"))
        } catch (e: Exception) {
            println("TrixnityMatrixRepository: Leave room error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get messages for a specific room
     */
    fun getMessagesForRoom(roomId: String): Flow<List<Message>> {
        return messages.map { messagesMap ->
            val roomMessages = messagesMap[roomId] ?: emptyList()
            println("TrixnityMatrixRepository: getMessagesForRoom($roomId) - returning ${roomMessages.size} messages")
            println("TrixnityMatrixRepository: Available rooms in messages map: ${messagesMap.keys.joinToString()}")
            roomMessages
        }
    }

    /**
     * Load more (older) messages for a room
     */
    suspend fun loadMoreMessages(roomId: String): Result<Int> {
        return try {
            val client = matrixClient ?: return Result.failure(Exception("Not logged in"))

            // TODO: Use Trixnity's pagination API
            println("TrixnityMatrixRepository: Loading more messages for room $roomId")
            Result.success(0)
        } catch (e: Exception) {
            println("TrixnityMatrixRepository: Load messages error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Check if more messages can be loaded for a room
     */
    fun canLoadMoreMessages(roomId: String): Boolean {
        // TODO: Implement with Trixnity - check if room has pagination token
        return false
    }

    /**
     * Check if user is logged in
     */
    fun isLoggedIn(): Boolean = matrixClient != null

    /**
     * Try to restore session from saved credentials
     */
    suspend fun tryRestoreSession(): Result<Unit> {
        return try {
            println("TrixnityMatrixRepository: Attempting to restore session...")
            val savedCredentials = credentialStorage.loadCredentials()

            if (savedCredentials != null) {
                println("TrixnityMatrixRepository: Found saved credentials for ${savedCredentials.userId}")

                // Trixnity stores session data internally
                // For a full implementation, we'd need to persist Trixnity's repositories
                // and restore them. For now, we'll require re-login.

                println("TrixnityMatrixRepository: Session restoration requires re-login with Trixnity")
                Result.failure(Exception("Session restoration not supported - please re-login"))
            } else {
                println("TrixnityMatrixRepository: No saved credentials found")
                Result.failure(Exception("No saved credentials"))
            }
        } catch (e: Exception) {
            println("TrixnityMatrixRepository: Failed to restore session: ${e.message}")
            Result.failure(e)
        }
    }
}
