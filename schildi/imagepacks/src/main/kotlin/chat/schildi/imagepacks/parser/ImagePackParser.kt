package chat.schildi.imagepacks.parser

import chat.schildi.imagepacks.model.ImagePack
import chat.schildi.imagepacks.model.ImagePackImage
import chat.schildi.imagepacks.model.ImagePackImageInfo
import chat.schildi.imagepacks.model.ImagePackUsage
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = false
}

// region Serialization DTOs

@Serializable
private data class PackContentDto(
    val pack: PackMetaDto? = null,
    val images: Map<String, PackImageDto>? = null,
)

@Serializable
private data class PackMetaDto(
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val usage: List<String>? = null,
)

@Serializable
private data class PackImageDto(
    val url: String,
    val body: String? = null,
    val usage: List<String>? = null,
    val info: PackImageInfoDto? = null,
)

@Serializable
private data class PackImageInfoDto(
    val w: Int? = null,
    val h: Int? = null,
    val size: Long? = null,
    val mimetype: String? = null,
)

@Serializable
private data class EmoteRoomsDto(
    val rooms: Map<String, Map<String, EmoteRoomEntryDto>>? = null,
)

@Serializable
private class EmoteRoomEntryDto

// endregion

object ImagePackParser {
    fun parsePackContent(jsonString: String): Result<ImagePack> = runCatching {
        val dto = json.decodeFromString<PackContentDto>(jsonString)
        dto.toDomain()
    }

    fun parseEmoteRooms(jsonString: String): Result<Map<RoomId, Set<String>>> = runCatching {
        val dto = json.decodeFromString<EmoteRoomsDto>(jsonString)
        dto.rooms.orEmpty().mapKeys { (key, _) -> RoomId(key) }
            .mapValues { (_, stateKeys) -> stateKeys.keys }
    }

    fun serializePackContent(pack: ImagePack): String {
        val dto = pack.toDto()
        return json.encodeToString(dto)
    }

    fun serializeEmoteRooms(rooms: Map<RoomId, Set<String>>): String {
        val dto = EmoteRoomsDto(
            rooms = rooms.mapKeys { (roomId, _) -> roomId.value }
                .mapValues { (_, stateKeys) ->
                    stateKeys.associateWith { EmoteRoomEntryDto() }
                }
        )
        return json.encodeToString(dto)
    }
}

// region Domain conversions

private fun PackContentDto.toDomain(): ImagePack {
    return ImagePack(
        displayName = pack?.displayName,
        avatarUrl = pack?.avatarUrl,
        usage = pack?.usage?.mapNotNull { it.toUsage() }.orEmpty(),
        images = images.orEmpty().map { (shortcode, imageDto) ->
            shortcode to imageDto.toDomain(shortcode)
        }.toMap(),
    )
}

private fun PackImageDto.toDomain(shortcode: String): ImagePackImage {
    return ImagePackImage(
        shortcode = shortcode,
        url = url,
        body = body,
        usage = usage?.mapNotNull { it.toUsage() }.orEmpty(),
        info = info?.toDomain(),
    )
}

private fun PackImageInfoDto.toDomain(): ImagePackImageInfo {
    return ImagePackImageInfo(
        width = w,
        height = h,
        size = size,
        mimetype = mimetype,
    )
}

private fun String.toUsage(): ImagePackUsage? = when (this) {
    "emoticon" -> ImagePackUsage.EMOTICON
    "sticker" -> ImagePackUsage.STICKER
    else -> null
}

private fun ImagePackUsage.toDto(): String = when (this) {
    ImagePackUsage.EMOTICON -> "emoticon"
    ImagePackUsage.STICKER -> "sticker"
}

private fun ImagePack.toDto(): PackContentDto {
    return PackContentDto(
        pack = PackMetaDto(
            displayName = displayName,
            avatarUrl = avatarUrl,
            usage = usage.map { it.toDto() }.ifEmpty { null },
        ),
        images = images.mapValues { (_, image) -> image.toDto() },
    )
}

private fun ImagePackImage.toDto(): PackImageDto {
    return PackImageDto(
        url = url,
        body = body,
        usage = usage.map { it.toDto() }.ifEmpty { null },
        info = info?.toDto(),
    )
}

private fun ImagePackImageInfo.toDto(): PackImageInfoDto {
    return PackImageInfoDto(
        w = width,
        h = height,
        size = size,
        mimetype = mimetype,
    )
}

// endregion
