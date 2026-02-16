package chat.schildi.imagepacks.model

import io.element.android.libraries.matrix.api.core.RoomId

enum class ImagePackUsage {
    EMOTICON,
    STICKER,
}

data class ImagePackImage(
    val shortcode: String,
    val url: String, // mxc:// URI
    val body: String?,
    val usage: List<ImagePackUsage>,
    val info: ImagePackImageInfo?,
)

data class ImagePackImageInfo(
    val width: Int?,
    val height: Int?,
    val size: Long?,
    val mimetype: String?,
)

data class ImagePack(
    val displayName: String?,
    val avatarUrl: String?, // mxc:// URI
    val usage: List<ImagePackUsage>,
    val images: Map<String, ImagePackImage>,
)

sealed interface ImagePackSource {
    data object UserAccount : ImagePackSource
    data class Room(val roomId: RoomId, val roomName: String?) : ImagePackSource
    data class EmoteRoom(val roomId: RoomId, val roomName: String?) : ImagePackSource
}

data class ResolvedImagePack(
    val pack: ImagePack,
    val source: ImagePackSource,
    val stateKey: String?,
)

/**
 * Result of loading packs from all sources.
 * Contains whatever packs loaded successfully, plus a flag indicating
 * whether any source failed (for UI error/retry feedback).
 */
data class PackLoadResult(
    val packs: List<ResolvedImagePack>,
    val hadErrors: Boolean,
)
