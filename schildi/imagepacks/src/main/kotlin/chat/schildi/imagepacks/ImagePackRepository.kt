package chat.schildi.imagepacks

import chat.schildi.imagepacks.model.ImagePack
import chat.schildi.imagepacks.model.ImagePackSource
import chat.schildi.imagepacks.model.PackLoadResult
import chat.schildi.imagepacks.model.ResolvedImagePack
import chat.schildi.imagepacks.parser.ImagePackParser
import dev.zacsweers.metro.Inject
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.BaseRoom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

private const val EVENT_TYPE_USER_EMOTES = "im.ponies.user_emotes"
private const val EVENT_TYPE_ROOM_EMOTES = "im.ponies.room_emotes"
private const val EVENT_TYPE_EMOTE_ROOMS = "im.ponies.emote_rooms"

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

@Inject
class ImagePackRepository(
    private val matrixClient: MatrixClient,
) {
    /**
     * Get the user's personal emoji/sticker pack from account data.
     */
    suspend fun getUserPack(): ResolvedImagePack? {
        val json = matrixClient.getAccountData(EVENT_TYPE_USER_EMOTES)
        if (json == null) {
            Timber.d("ImagePacks: No user emotes account data found")
            return null
        }
        Timber.d("ImagePacks: Got user emotes: ${json.take(200)}")
        val pack = ImagePackParser.parsePackContent(json).getOrThrow()
        Timber.d("ImagePacks: Parsed user pack with ${pack.images.size} images")
        return ResolvedImagePack(
            pack = pack,
            source = ImagePackSource.UserAccount,
            stateKey = null,
        )
    }

    /**
     * Get all image packs from a room's state events.
     * Uses fetchFullRoomState() to get events that sliding sync may not include.
     */
    suspend fun getRoomPacks(room: BaseRoom): List<ResolvedImagePack> {
        return getPacksFromFullState(room, ImagePackSource.Room(room.roomId, room.info().name)) // throws on fetch failure
    }

    /**
     * Get a specific room pack by state key.
     */
    suspend fun getRoomPack(room: BaseRoom, stateKey: String): ResolvedImagePack? {
        val json = room.getRawState(EVENT_TYPE_ROOM_EMOTES, stateKey).getOrNull() ?: return null
        val pack = ImagePackParser.parsePackContent(json).getOrNull() ?: return null
        return ResolvedImagePack(
            pack = pack,
            source = ImagePackSource.Room(room.roomId, room.info().name),
            stateKey = stateKey,
        )
    }

    /**
     * Get the room IDs and state keys referenced in the user's emote rooms account data.
     */
    suspend fun getEmoteRoomIds(): Map<RoomId, Set<String>> {
        val json = matrixClient.getAccountData(EVENT_TYPE_EMOTE_ROOMS)
        if (json == null) {
            Timber.d("ImagePacks: No emote rooms account data found")
            return emptyMap()
        }
        Timber.d("ImagePacks: Got emote rooms: ${json.take(500)}")
        val rooms = ImagePackParser.parseEmoteRooms(json).getOrThrow()
        Timber.d("ImagePacks: Found ${rooms.size} emote rooms: ${rooms.keys}")
        return rooms
    }

    /**
     * Get all packs from emote rooms (global packs referenced by the user).
     * Returns partial results even if some rooms fail to load.
     */
    suspend fun getGlobalPacks(): PackLoadResult {
        val emoteRooms = getEmoteRoomIds()
        val result = mutableListOf<ResolvedImagePack>()
        var hadErrors = false
        for ((roomId, stateKeys) in emoteRooms) {
            val room = matrixClient.getRoom(roomId)
            if (room == null) {
                Timber.w("ImagePacks: Could not get room $roomId for emote pack")
                hadErrors = true
                continue
            }
            room.use { baseRoom ->
                try {
                    val packs = getPacksFromFullState(
                        baseRoom,
                        ImagePackSource.EmoteRoom(roomId, baseRoom.info().name),
                    )
                    // MSC2545: only include packs whose state_key was referenced in im.ponies.emote_rooms.
                    // If stateKeys is empty, include all packs from the room (shouldn't happen per spec,
                    // but defensive).
                    if (stateKeys.isNotEmpty()) {
                        result.addAll(packs.filter { it.stateKey in stateKeys })
                    } else {
                        result.addAll(packs)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "ImagePacks: Failed to fetch packs from emote room $roomId")
                    hadErrors = true
                }
            }
        }
        return PackLoadResult(packs = result, hadErrors = hadErrors)
    }

    /**
     * Fetch full room state and extract im.ponies.room_emotes events.
     * This is necessary because sliding sync doesn't include custom state event types.
     */
    private suspend fun getPacksFromFullState(
        room: BaseRoom,
        source: ImagePackSource,
    ): List<ResolvedImagePack> {
        val allEvents = room.fetchFullRoomState().getOrThrow()
        Timber.d("ImagePacks: fetchFullRoomState returned ${allEvents.size} events for ${room.roomId}")

        val packs = mutableListOf<ResolvedImagePack>()
        for (eventJson in allEvents) {
            // Each event is the full state event JSON: {"type": "...", "state_key": "...", "content": {...}}
            val parsed = try {
                lenientJson.parseToJsonElement(eventJson).jsonObject
            } catch (e: Exception) {
                continue
            }
            val eventType = parsed["type"]?.jsonPrimitive?.content ?: continue
            if (eventType != EVENT_TYPE_ROOM_EMOTES) continue

            val stateKey = parsed["state_key"]?.jsonPrimitive?.content ?: ""
            val content = parsed["content"]?.jsonObject ?: continue
            val contentString = content.toString()

            Timber.d("ImagePacks: Found room_emotes event (key=$stateKey): ${contentString.take(200)}")
            val packResult = ImagePackParser.parsePackContent(contentString)
            if (packResult.isFailure) {
                Timber.w(packResult.exceptionOrNull(), "ImagePacks: Failed to parse pack (key=$stateKey)")
                continue
            }
            val pack = packResult.getOrNull() ?: continue
            Timber.d("ImagePacks: Parsed pack '$stateKey' with ${pack.images.size} images, usage=${pack.usage}")
            packs.add(
                ResolvedImagePack(
                    pack = pack,
                    source = source,
                    stateKey = stateKey,
                )
            )
        }
        return packs
    }

    /**
     * Get all packs from all sources, in priority order:
     * 1. User's personal pack
     * 2. Global packs (from emote rooms)
     * 3. Room-specific packs (if room is provided)
     *
     * Returns a [PackLoadResult] with all successfully loaded packs and a flag
     * indicating whether any source failed to load.
     */
    suspend fun getAllPacks(room: BaseRoom?): PackLoadResult {
        val packs = mutableListOf<ResolvedImagePack>()
        var hadErrors = false

        try {
            getUserPack()?.let { packs.add(it) }
        } catch (e: Exception) {
            Timber.e(e, "ImagePacks: Failed to load user pack")
            hadErrors = true
        }

        try {
            val globalResult = getGlobalPacks()
            packs.addAll(globalResult.packs)
            if (globalResult.hadErrors) hadErrors = true
        } catch (e: Exception) {
            Timber.e(e, "ImagePacks: Failed to load global packs")
            hadErrors = true
        }

        if (room != null) {
            try {
                packs.addAll(getRoomPacks(room))
            } catch (e: Exception) {
                Timber.e(e, "ImagePacks: Failed to load room packs for ${room.roomId}")
                hadErrors = true
            }
        }

        Timber.d("ImagePacks: getAllPacks returned ${packs.size} total packs (hadErrors=$hadErrors)")
        return PackLoadResult(packs = packs, hadErrors = hadErrors)
    }

    /**
     * Save the user's personal pack to account data.
     */
    suspend fun setUserPack(pack: ImagePack) {
        val json = ImagePackParser.serializePackContent(pack)
        matrixClient.setAccountData(EVENT_TYPE_USER_EMOTES, json)
    }

    /**
     * Save a room pack state event.
     */
    suspend fun setRoomPack(room: BaseRoom, stateKey: String, pack: ImagePack): Result<Unit> {
        val json = ImagePackParser.serializePackContent(pack)
        return room.sendRawState(EVENT_TYPE_ROOM_EMOTES, stateKey, json)
    }

    /**
     * Update the emote rooms account data.
     */
    suspend fun setEmoteRooms(rooms: Map<RoomId, Set<String>>) {
        val json = ImagePackParser.serializeEmoteRooms(rooms)
        matrixClient.setAccountData(EVENT_TYPE_EMOTE_ROOMS, json)
    }

    /**
     * Upload an image and return the MXC URI.
     */
    suspend fun uploadImage(mimeType: String, data: ByteArray): Result<String> {
        return matrixClient.uploadMedia(mimeType, data)
    }
}
