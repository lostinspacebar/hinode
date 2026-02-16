package com.lostinspacebar.hinode.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostinspacebar.hinode.data.TrixnityMatrixRepository
import com.lostinspacebar.hinode.matrix.Message
import com.lostinspacebar.hinode.matrix.Room
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the chat screen
 * Manages rooms, messages, and chat-related operations
 */
class ChatViewModel(
    private val repository: TrixnityMatrixRepository
) : ViewModel() {

    // Room list from repository
    val rooms: StateFlow<List<Room>> = repository.rooms
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Currently selected room
    private val _selectedRoomId = MutableStateFlow<String?>(null)
    val selectedRoomId: StateFlow<String?> = _selectedRoomId.asStateFlow()

    // Messages for the selected room
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    // Message being composed
    private val _messageText = MutableStateFlow("")
    val messageText: StateFlow<String> = _messageText.asStateFlow()

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Error message
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Loading more messages state
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    // Can load more messages
    private val _canLoadMore = MutableStateFlow(false)
    val canLoadMore: StateFlow<Boolean> = _canLoadMore.asStateFlow()

    // Connection status from repository (mapped to string for display)
    val connectionStatus: StateFlow<String> = repository.connectionStatus
        .map { status ->
            when (status) {
                com.lostinspacebar.hinode.data.ConnectionStatus.DISCONNECTED -> "Disconnected"
                com.lostinspacebar.hinode.data.ConnectionStatus.CONNECTING -> "Connecting..."
                com.lostinspacebar.hinode.data.ConnectionStatus.CONNECTED -> "Connected"
                com.lostinspacebar.hinode.data.ConnectionStatus.SYNCING -> "Syncing..."
                com.lostinspacebar.hinode.data.ConnectionStatus.ERROR -> "Error"
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "Disconnected"
        )

    init {
        // Observe selected room changes and load messages
        viewModelScope.launch {
            _selectedRoomId.collect { roomId ->
                if (roomId != null) {
                    loadMessages(roomId)
                }
            }
        }
    }

    /**
     * Select a room to view
     */
    fun selectRoom(roomId: String) {
        _selectedRoomId.value = roomId
        _errorMessage.value = null
        _canLoadMore.value = repository.canLoadMoreMessages(roomId)
    }

    /**
     * Load messages for a specific room
     */
    private fun loadMessages(roomId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.getMessagesForRoom(roomId).collect { messageList ->
                    _messages.value = messageList.sortedBy { it.timestamp }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load messages: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Update the message text being composed
     */
    fun updateMessageText(text: String) {
        _messageText.value = text
    }

    /**
     * Send the current message
     */
    fun sendMessage() {
        val roomId = _selectedRoomId.value ?: return
        val text = _messageText.value.trim()

        if (text.isEmpty()) return

        viewModelScope.launch {
            try {
                repository.sendMessage(roomId, text)
                _messageText.value = ""
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to send message: ${e.message}"
            }
        }
    }

    /**
     * Create a new room
     */
    fun createRoom(name: String, topic: String = "") {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.createRoom(name, topic)
                if (result.isSuccess) {
                    _selectedRoomId.value = result.getOrNull()
                    _errorMessage.value = null
                } else {
                    _errorMessage.value = "Failed to create room: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to create room: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Join an existing room
     */
    fun joinRoom(roomId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.joinRoom(roomId)
                _selectedRoomId.value = roomId
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to join room: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Leave the current room
     */
    fun leaveRoom() {
        val roomId = _selectedRoomId.value ?: return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.leaveRoom(roomId)
                _selectedRoomId.value = null
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to leave room: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }


    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Load more (older) messages for the current room
     */
    fun loadMoreMessages() {
        val roomId = _selectedRoomId.value ?: return

        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val result = repository.loadMoreMessages(roomId)
                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    if (count == 0) {
                        _canLoadMore.value = false
                    } else {
                        _canLoadMore.value = repository.canLoadMoreMessages(roomId)
                    }
                } else {
                    _errorMessage.value = "Failed to load more messages: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error loading messages: ${e.message}"
            } finally {
                _isLoadingMore.value = false
            }
        }
    }
}
