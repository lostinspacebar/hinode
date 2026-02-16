package com.lostinspacebar.hinode.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.lostinspacebar.hinode.matrix.Message
import com.lostinspacebar.hinode.matrix.Room
import kotlinx.coroutines.launch

/**
 * Main chat screen with room list and message feed
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val rooms by viewModel.rooms.collectAsState()
    val selectedRoomId by viewModel.selectedRoomId.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val messageText by viewModel.messageText.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val canLoadMore by viewModel.canLoadMore.collectAsState()

    var showCreateRoomDialog by remember { mutableStateOf(false) }

    Row(modifier = modifier.fillMaxSize()) {
        // Room list
        RoomList(
            rooms = rooms,
            selectedRoomId = selectedRoomId,
            onRoomSelected = { viewModel.selectRoom(it) },
            onCreateRoom = { showCreateRoomDialog = true },
            connectionStatus = connectionStatus,
            modifier = Modifier.width(280.dp).fillMaxHeight()
        )

        // Message area
        if (selectedRoomId != null) {
            MessageArea(
                messages = messages,
                messageText = messageText,
                onMessageTextChanged = { viewModel.updateMessageText(it) },
                onSendMessage = { viewModel.sendMessage() },
                isLoading = isLoading,
                errorMessage = errorMessage,
                onClearError = { viewModel.clearError() },
                isLoadingMore = isLoadingMore,
                canLoadMore = canLoadMore,
                onLoadMore = { viewModel.loadMoreMessages() },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        } else {
            // Empty state
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Select a room to start chatting",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Create room dialog
    if (showCreateRoomDialog) {
        CreateRoomDialog(
            onDismiss = { showCreateRoomDialog = false },
            onCreateRoom = { name, topic ->
                viewModel.createRoom(name, topic)
                showCreateRoomDialog = false
            }
        )
    }
}

/**
 * Room list panel with space hierarchy
 */
@Composable
private fun RoomList(
    rooms: List<Room>,
    selectedRoomId: String?,
    onRoomSelected: (String) -> Unit,
    onCreateRoom: () -> Unit,
    connectionStatus: String,
    modifier: Modifier = Modifier
) {
    // Group rooms by spaces
    val spaces = rooms.filter { it.type == com.lostinspacebar.hinode.matrix.RoomType.SPACE }
    val roomsWithoutSpace = rooms.filter {
        it.type == com.lostinspacebar.hinode.matrix.RoomType.ROOM && it.parentSpaceId == null
    }
    val roomsBySpace = rooms.filter {
        it.type == com.lostinspacebar.hinode.matrix.RoomType.ROOM && it.parentSpaceId != null
    }.groupBy { it.parentSpaceId }

    var expandedSpaces by remember { mutableStateOf<Set<String>>(emptySet()) }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Rooms & Spaces",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = connectionStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (connectionStatus == "Connected" || connectionStatus == "Syncing...") {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
                IconButton(onClick = onCreateRoom) {
                    Text("➕", style = MaterialTheme.typography.titleLarge)
                }
            }

            Divider()

            // Room/Space list
            if (rooms.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No rooms yet\nClick ➕ to create one",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn {
                    // Spaces with their rooms
                    spaces.forEach { space ->
                        item(key = "space_${space.id}") {
                            SpaceItem(
                                space = space,
                                isExpanded = expandedSpaces.contains(space.id),
                                onToggle = {
                                    expandedSpaces = if (expandedSpaces.contains(space.id)) {
                                        expandedSpaces - space.id
                                    } else {
                                        expandedSpaces + space.id
                                    }
                                }
                            )
                        }

                        // Show rooms in this space if expanded
                        if (expandedSpaces.contains(space.id)) {
                            val spaceRooms = roomsBySpace[space.id] ?: emptyList()
                            items(spaceRooms, key = { "room_${it.id}" }) { room ->
                                RoomItem(
                                    room = room,
                                    isSelected = room.id == selectedRoomId,
                                    onClick = { onRoomSelected(room.id) },
                                    indented = true
                                )
                            }
                        }
                    }

                    // Rooms without a space
                    if (roomsWithoutSpace.isNotEmpty()) {
                        item(key = "divider") {
                            if (spaces.isNotEmpty()) {
                                Divider(modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                        items(roomsWithoutSpace, key = { "room_${it.id}" }) { room ->
                            RoomItem(
                                room = room,
                                isSelected = room.id == selectedRoomId,
                                onClick = { onRoomSelected(room.id) },
                                indented = false
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Space item (expandable/collapsible)
 */
@Composable
private fun SpaceItem(
    space: Room,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (isExpanded) "▼" else "▶",
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = "🏢",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = space.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Individual room item
 */
@Composable
private fun RoomItem(
    room: Room,
    isSelected: Boolean,
    onClick: () -> Unit,
    indented: Boolean = false
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (indented) 48.dp else 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 12.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = room.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (room.topic.isNotEmpty()) {
                    Text(
                        text = room.topic,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1
                    )
                }
            }

            if (room.unreadCount > 0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = room.unreadCount.toString(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

/**
 * Message area with message list and input
 */
@Composable
private fun MessageArea(
    messages: List<Message>,
    messageText: String,
    onMessageTextChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onClearError: () -> Unit,
    isLoadingMore: Boolean,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Error banner
        if (errorMessage != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onClearError) {
                        Text("Dismiss")
                    }
                }
            }
        }

        // Message list
        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Load more button at the top
            if (canLoadMore || isLoadingMore) {
                item(key = "load_more") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoadingMore) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Loading more messages...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            TextButton(onClick = onLoadMore) {
                                Text("Load More Messages")
                            }
                        }
                    }
                }
            }

            items(messages, key = { it.id }) { message ->
                MessageBubble(message = message)
            }
        }

        // Auto-scroll to bottom when new messages arrive (only if already near bottom)
        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty() && listState.layoutInfo.totalItemsCount > 0) {
                val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val totalItems = listState.layoutInfo.totalItemsCount

                // Only auto-scroll if we're near the bottom (within last 5 items)
                if (totalItems - lastVisibleIndex <= 5) {
                    coroutineScope.launch {
                        // Scroll to the last item (which is the last message)
                        listState.animateScrollToItem(totalItems - 1)
                    }
                }
            }
        }

        Divider()

        // Message input
        MessageInput(
            messageText = messageText,
            onMessageTextChanged = onMessageTextChanged,
            onSendMessage = onSendMessage,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Message bubble
 */
@Composable
private fun MessageBubble(message: Message) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        // Sender name
        Text(
            text = message.sender.displayName ?: message.sender.userId,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Message content
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = when (val content = message.content) {
                        is com.lostinspacebar.hinode.matrix.MessageContent.Text -> content.body
                        is com.lostinspacebar.hinode.matrix.MessageContent.Image -> content.body
                        is com.lostinspacebar.hinode.matrix.MessageContent.File -> content.filename
                        is com.lostinspacebar.hinode.matrix.MessageContent.Notice -> content.body
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Timestamp
                Text(
                    text = formatTimestamp(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Message input field
 */
@Composable
private fun MessageInput(
    messageText: String,
    onMessageTextChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(16.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = messageText,
            onValueChange = onMessageTextChanged,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Type a message...") },
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Send
            ),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (messageText.isNotBlank()) {
                        onSendMessage()
                    }
                }
            ),
            maxLines = 4
        )

        IconButton(
            onClick = onSendMessage,
            enabled = enabled && messageText.isNotBlank()
        ) {
            Text(
                text = "📤",
                style = MaterialTheme.typography.titleLarge,
                color = if (enabled && messageText.isNotBlank()) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
        }
    }
}

/**
 * Format timestamp for display
 * Simple implementation - shows milliseconds for now
 * TODO: Implement proper date/time formatting per platform
 */
private fun formatTimestamp(timestamp: Long): String {
    val seconds = timestamp / 1000
    val minutes = (seconds / 60) % 60
    val hours = (seconds / 3600) % 24
    return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
}

/**
 * Create room dialog
 */
@Composable
private fun CreateRoomDialog(
    onDismiss: () -> Unit,
    onCreateRoom: (name: String, topic: String) -> Unit
) {
    var roomName by remember { mutableStateOf("") }
    var roomTopic by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Create New Room")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    label = { Text("Room Name") },
                    placeholder = { Text("General Chat") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = roomTopic,
                    onValueChange = { roomTopic = it },
                    label = { Text("Topic (Optional)") },
                    placeholder = { Text("Discuss anything") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (roomName.isNotBlank()) {
                        onCreateRoom(roomName, roomTopic)
                    }
                },
                enabled = roomName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
