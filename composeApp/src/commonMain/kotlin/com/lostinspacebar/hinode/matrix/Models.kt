package com.lostinspacebar.hinode.matrix

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Matrix User representation
 */
@Serializable
data class User(
    @SerialName("user_id") val userId: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val presence: Presence = Presence.OFFLINE
)

/**
 * User presence state
 */
@Serializable
enum class Presence {
    @SerialName("online") ONLINE,
    @SerialName("offline") OFFLINE,
    @SerialName("unavailable") UNAVAILABLE
}

/**
 * Matrix Room representation
 */
@Serializable
data class Room(
    val id: String,
    val name: String,
    val topic: String = "",
    val avatarUrl: String? = null,
    val members: List<User> = emptyList(),
    val isDirect: Boolean = false,
    val isEncrypted: Boolean = false,
    val lastMessage: String? = null,
    val unreadCount: Int = 0,
    val type: RoomType = RoomType.ROOM,
    val parentSpaceId: String? = null
)

/**
 * Room type enum
 */
@Serializable
enum class RoomType {
    @SerialName("room") ROOM,
    @SerialName("space") SPACE
}

/**
 * Matrix Message representation
 */
@Serializable
data class Message(
    val id: String,
    val roomId: String,
    val sender: User,
    val content: MessageContent,
    val timestamp: Long,
    val deliveryStatus: DeliveryStatus = DeliveryStatus.SENT
)

/**
 * Message delivery status
 */
@Serializable
enum class DeliveryStatus {
    @SerialName("pending") PENDING,
    @SerialName("sent") SENT,
    @SerialName("delivered") DELIVERED,
    @SerialName("failed") FAILED
}

/**
 * Message content types
 */
@Serializable
sealed class MessageContent {
    @Serializable
    @SerialName("text")
    data class Text(val body: String, val formatted: String? = null) : MessageContent()

    @Serializable
    @SerialName("image")
    data class Image(val url: String, val body: String = "Image") : MessageContent()

    @Serializable
    @SerialName("file")
    data class File(val url: String, val filename: String, val mimeType: String) : MessageContent()

    @Serializable
    @SerialName("notice")
    data class Notice(val body: String) : MessageContent()
}

/**
 * Matrix Event types
 * Note: room_id is optional because timeline events don't include it (it's in the parent context)
 */
@Serializable
sealed class MatrixEvent {
    abstract val eventId: String
    abstract val sender: String
    abstract val timestamp: Long

    @Serializable
    @SerialName("m.room.message")
    data class MessageEvent(
        @SerialName("event_id") override val eventId: String,
        override val sender: String,
        @SerialName("origin_server_ts") override val timestamp: Long,
        val content: MessageEventContent
    ) : MatrixEvent()

    @Serializable
    @SerialName("m.room.member")
    data class MemberEvent(
        @SerialName("event_id") override val eventId: String,
        override val sender: String,
        @SerialName("origin_server_ts") override val timestamp: Long,
        @SerialName("state_key") val stateKey: String? = null,
        val content: MemberEventContent
    ) : MatrixEvent()

    @Serializable
    @SerialName("m.room.create")
    data class RoomCreateEvent(
        @SerialName("event_id") override val eventId: String,
        override val sender: String,
        @SerialName("origin_server_ts") override val timestamp: Long,
        val content: RoomCreateContent
    ) : MatrixEvent()

    @Serializable
    @SerialName("m.space.child")
    data class SpaceChildEvent(
        @SerialName("event_id") override val eventId: String,
        override val sender: String,
        @SerialName("origin_server_ts") override val timestamp: Long,
        @SerialName("state_key") val childRoomId: String,
        val content: SpaceChildContent
    ) : MatrixEvent()

    @Serializable
    @SerialName("m.room.name")
    data class RoomNameEvent(
        @SerialName("event_id") override val eventId: String,
        override val sender: String,
        @SerialName("origin_server_ts") override val timestamp: Long,
        val content: RoomNameContent
    ) : MatrixEvent()

    @Serializable
    @SerialName("m.room.topic")
    data class RoomTopicEvent(
        @SerialName("event_id") override val eventId: String,
        override val sender: String,
        @SerialName("origin_server_ts") override val timestamp: Long,
        val content: RoomTopicContent
    ) : MatrixEvent()

    @Serializable
    @SerialName("m.room.avatar")
    data class RoomAvatarEvent(
        @SerialName("event_id") override val eventId: String,
        override val sender: String,
        @SerialName("origin_server_ts") override val timestamp: Long,
        val content: RoomAvatarContent
    ) : MatrixEvent()

    @Serializable
    @SerialName("m.room.power_levels")
    data class PowerLevelsEvent(
        @SerialName("event_id") override val eventId: String,
        override val sender: String,
        @SerialName("origin_server_ts") override val timestamp: Long,
        val content: PowerLevelsContent
    ) : MatrixEvent()

    @Serializable
    @SerialName("m.room.join_rules")
    data class JoinRulesEvent(
        @SerialName("event_id") override val eventId: String,
        override val sender: String,
        @SerialName("origin_server_ts") override val timestamp: Long,
        val content: JoinRulesContent
    ) : MatrixEvent()

    @Serializable
    @SerialName("m.room.history_visibility")
    data class HistoryVisibilityEvent(
        @SerialName("event_id") override val eventId: String,
        override val sender: String,
        @SerialName("origin_server_ts") override val timestamp: Long,
        val content: HistoryVisibilityContent
    ) : MatrixEvent()

    @Serializable
    @SerialName("m.room.canonical_alias")
    data class CanonicalAliasEvent(
        @SerialName("event_id") override val eventId: String,
        override val sender: String,
        @SerialName("origin_server_ts") override val timestamp: Long,
        val content: CanonicalAliasContent
    ) : MatrixEvent()

    @Serializable
    @SerialName("m.room.guest_access")
    data class GuestAccessEvent(
        @SerialName("event_id") override val eventId: String,
        override val sender: String,
        @SerialName("origin_server_ts") override val timestamp: Long,
        val content: GuestAccessContent
    ) : MatrixEvent()

    @Serializable
    @SerialName("m.space.parent")
    data class SpaceParentEvent(
        @SerialName("event_id") override val eventId: String,
        override val sender: String,
        @SerialName("origin_server_ts") override val timestamp: Long,
        @SerialName("state_key") val parentSpaceId: String,
        val content: SpaceParentContent
    ) : MatrixEvent()

    @Serializable
    @SerialName("m.room.server_acl")
    data class ServerAclEvent(
        @SerialName("event_id") override val eventId: String,
        override val sender: String,
        @SerialName("origin_server_ts") override val timestamp: Long,
        val content: ServerAclContent
    ) : MatrixEvent()

    @Serializable
    @SerialName("m.room.encryption")
    data class EncryptionEvent(
        @SerialName("event_id") override val eventId: String,
        override val sender: String,
        @SerialName("origin_server_ts") override val timestamp: Long,
        val content: EncryptionContent
    ) : MatrixEvent()

    @Serializable
    @SerialName("m.room.tombstone")
    data class TombstoneEvent(
        @SerialName("event_id") override val eventId: String,
        override val sender: String,
        @SerialName("origin_server_ts") override val timestamp: Long,
        val content: TombstoneContent
    ) : MatrixEvent()

    // Catch-all for unknown event types
    @Serializable
    data class UnknownEvent(
        @SerialName("event_id") override val eventId: String,
        override val sender: String,
        @SerialName("origin_server_ts") override val timestamp: Long,
        val type: String,
        val content: kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.JsonObject(emptyMap())
    ) : MatrixEvent()
}

// Content types for additional events
@Serializable
data class RoomTopicContent(
    val topic: String? = null
)

@Serializable
data class RoomAvatarContent(
    val url: String? = null
)

@Serializable
data class PowerLevelsContent(
    val users: Map<String, Int> = emptyMap(),
    @SerialName("users_default") val usersDefault: Int? = null,
    val events: Map<String, Int> = emptyMap(),
    @SerialName("events_default") val eventsDefault: Int? = null,
    @SerialName("state_default") val stateDefault: Int? = null,
    val ban: Int? = null,
    val kick: Int? = null,
    val redact: Int? = null,
    val invite: Int? = null
)

@Serializable
data class JoinRulesContent(
    @SerialName("join_rule") val joinRule: String? = null
)

@Serializable
data class HistoryVisibilityContent(
    @SerialName("history_visibility") val historyVisibility: String? = null
)

@Serializable
data class CanonicalAliasContent(
    val alias: String? = null,
    @SerialName("alt_aliases") val altAliases: List<String>? = null
)

@Serializable
data class GuestAccessContent(
    @SerialName("guest_access") val guestAccess: String? = null
)

@Serializable
data class SpaceParentContent(
    val via: List<String> = emptyList(),
    val canonical: Boolean? = null
)

@Serializable
data class ServerAclContent(
    val allow: List<String>? = null,
    val deny: List<String>? = null,
    @SerialName("allow_ip_literals") val allowIpLiterals: Boolean? = null
)

@Serializable
data class EncryptionContent(
    val algorithm: String? = null
)

@Serializable
data class TombstoneContent(
    val body: String? = null,
    @SerialName("replacement_room") val replacementRoom: String? = null
)

@Serializable
data class RoomCreateContent(
    val creator: String? = null,
    val type: String? = null  // "m.space" for spaces
)

@Serializable
data class MemberEventContent(
    val membership: String,
    val displayname: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

@Serializable
data class SpaceChildContent(
    val via: List<String> = emptyList(),
    val order: String? = null
)

@Serializable
data class RoomNameContent(
    val name: String
)

/**
 * Message event content from Matrix protocol
 */
@Serializable
data class MessageEventContent(
    val msgtype: String,
    val body: String,
    val format: String? = null,
    @SerialName("formatted_body") val formattedBody: String? = null,
    val url: String? = null
)

/**
 * Login request (newer identifier-based format)
 */
@Serializable
data class LoginRequest(
    @SerialName("type") val type: String = "m.login.password",
    @SerialName("identifier") val identifier: Identifier? = null,
    @SerialName("user") val user: String? = null,
    @SerialName("password") val password: String
)

@Serializable
data class Identifier(
    @SerialName("type") val type: String = "m.id.user",
    @SerialName("user") val user: String
)

/**
 * Login response
 */
@Serializable
data class LoginResponse(
    @SerialName("user_id") val userId: String,
    @SerialName("access_token") val accessToken: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("home_server") val homeServer: String? = null
)

/**
 * Sync response from Matrix /sync endpoint
 */
@Serializable
data class SyncResponse(
    @SerialName("next_batch") val nextBatch: String,
    val rooms: RoomsResponse? = null,
    val presence: PresenceResponse? = null
)

@Serializable
data class RoomsResponse(
    val join: Map<String, JoinedRoomResponse>? = null,
    val invite: Map<String, InvitedRoomResponse>? = null,
    val leave: Map<String, LeftRoomResponse>? = null
)

@Serializable
data class JoinedRoomResponse(
    val timeline: TimelineResponse? = null,
    val state: StateResponse? = null,
    @SerialName("unread_notifications") val unreadNotifications: UnreadNotifications? = null
)

@Serializable
data class InvitedRoomResponse(
    @SerialName("invite_state") val inviteState: StateResponse? = null
)

@Serializable
data class LeftRoomResponse(
    val timeline: TimelineResponse? = null,
    val state: StateResponse? = null
)

@Serializable
data class TimelineResponse(
    // Use JsonElement to handle unknown event types gracefully
    val events: List<kotlinx.serialization.json.JsonElement> = emptyList(),
    val limited: Boolean = false,
    @SerialName("prev_batch") val prevBatch: String? = null
)

@Serializable
data class StateResponse(
    // Use JsonElement to handle unknown event types gracefully
    val events: List<kotlinx.serialization.json.JsonElement> = emptyList()
)

@Serializable
data class UnreadNotifications(
    @SerialName("highlight_count") val highlightCount: Int = 0,
    @SerialName("notification_count") val notificationCount: Int = 0
)

@Serializable
data class PresenceResponse(
    val events: List<PresenceEvent> = emptyList()
)

@Serializable
data class PresenceEvent(
    val sender: String,
    val content: PresenceContent
)

@Serializable
data class PresenceContent(
    val presence: Presence,
    @SerialName("last_active_ago") val lastActiveAgo: Long? = null,
    @SerialName("currently_active") val currentlyActive: Boolean? = null
)

/**
 * Room creation request
 */
@Serializable
data class CreateRoomRequest(
    val name: String? = null,
    val topic: String? = null,
    @SerialName("is_direct") val isDirect: Boolean = false,
    val preset: String? = "private_chat",
    val invite: List<String> = emptyList()
)

/**
 * Room creation response
 */
@Serializable
data class CreateRoomResponse(
    @SerialName("room_id") val roomId: String
)

/**
 * Send message request
 */
@Serializable
data class SendMessageRequest(
    val msgtype: String = "m.text",
    val body: String
)

/**
 * Send message response
 */
@Serializable
data class SendMessageResponse(
    @SerialName("event_id") val eventId: String
)

/**
 * Error response from Matrix API
 */
@Serializable
data class MatrixError(
    val errcode: String,
    val error: String
)

/**
 * Messages pagination response
 */
@Serializable
data class MessagesResponse(
    val start: String,
    val end: String? = null,
    val chunk: List<kotlinx.serialization.json.JsonElement> = emptyList()
)
