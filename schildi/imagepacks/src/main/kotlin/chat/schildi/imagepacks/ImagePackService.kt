package chat.schildi.imagepacks

import chat.schildi.imagepacks.model.ImagePackImage
import chat.schildi.imagepacks.model.ImagePackUsage
import chat.schildi.imagepacks.model.PackLoadResult
import chat.schildi.imagepacks.model.ResolvedImagePack
import dev.zacsweers.metro.Inject
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.BaseRoom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Inject
class ImagePackService(
    private val repository: ImagePackRepository,
) {
    private val _packs = MutableStateFlow<List<ResolvedImagePack>>(emptyList())
    private val _hadLoadErrors = MutableStateFlow(false)
    private val mutex = Mutex()
    private var cachedRoomId: RoomId? = null

    fun observePacks(): StateFlow<List<ResolvedImagePack>> = _packs.asStateFlow()
    val hadLoadErrors: StateFlow<Boolean> = _hadLoadErrors.asStateFlow()

    /**
     * Force-refresh packs from all sources and update the cache.
     * Call this eagerly (e.g. on room entry) to warm the cache before the picker opens.
     */
    suspend fun refreshPacks(room: BaseRoom?) {
        mutex.withLock {
            val result = repository.getAllPacks(room)
            _packs.value = result.packs
            _hadLoadErrors.value = result.hadErrors
            cachedRoomId = room?.roomId
        }
    }

    /**
     * Return cached packs if available for this room, otherwise fetch and cache.
     */
    private suspend fun ensurePacks(room: BaseRoom?): List<ResolvedImagePack> {
        mutex.withLock {
            val roomId = room?.roomId
            if (_packs.value.isNotEmpty() && cachedRoomId == roomId) {
                return _packs.value
            }
            val result = repository.getAllPacks(room)
            _packs.value = result.packs
            _hadLoadErrors.value = result.hadErrors
            cachedRoomId = room?.roomId
            return result.packs
        }
    }

    /**
     * Get emoticons matching a shortcode prefix, for autocomplete.
     * Returns images from all packs that have EMOTICON usage (or no usage restriction).
     */
    suspend fun getEmoticonsByShortcode(prefix: String, room: BaseRoom?): List<ImagePackImage> {
        val packs = ensurePacks(room)
        val lowercasePrefix = prefix.lowercase()
        return packs.flatMap { resolved ->
            resolved.pack.images.values.filter { image ->
                image.isEmoticon(resolved) &&
                    (image.shortcode.lowercase().contains(lowercasePrefix) ||
                        image.body?.lowercase()?.contains(lowercasePrefix) == true)
            }
        }.take(MAX_AUTOCOMPLETE_RESULTS)
    }

    /**
     * Get stickers grouped by pack, for sticker picker.
     */
    suspend fun getStickersByPack(room: BaseRoom?): List<Pair<ResolvedImagePack, List<ImagePackImage>>> {
        val packs = ensurePacks(room)
        return packs.mapNotNull { resolved ->
            val stickers = resolved.pack.images.values.filter { image ->
                image.isSticker(resolved)
            }
            if (stickers.isNotEmpty()) resolved to stickers else null
        }
    }

    /**
     * Get all emoticons grouped by pack, for custom emoji tabs in emoji picker.
     */
    suspend fun getAllEmoticons(room: BaseRoom?): List<Pair<ResolvedImagePack, List<ImagePackImage>>> {
        val packs = ensurePacks(room)
        return packs.mapNotNull { resolved ->
            val emoticons = resolved.pack.images.values.filter { image ->
                image.isEmoticon(resolved)
            }
            if (emoticons.isNotEmpty()) resolved to emoticons else null
        }
    }

    companion object {
        private const val MAX_AUTOCOMPLETE_RESULTS = 20
    }
}

/**
 * An image is considered an emoticon if:
 * - The image has EMOTICON in its usage list, OR
 * - The image has no usage set AND the pack has EMOTICON in its usage list, OR
 * - Neither the image nor the pack have any usage set (treat as both)
 */
private fun ImagePackImage.isEmoticon(resolved: ResolvedImagePack): Boolean {
    return when {
        usage.isNotEmpty() -> ImagePackUsage.EMOTICON in usage
        resolved.pack.usage.isNotEmpty() -> ImagePackUsage.EMOTICON in resolved.pack.usage
        else -> true // No usage specified anywhere means both
    }
}

/**
 * An image is considered a sticker if:
 * - The image has STICKER in its usage list, OR
 * - The image has no usage set AND the pack has STICKER in its usage list, OR
 * - Neither the image nor the pack have any usage set (treat as both)
 */
private fun ImagePackImage.isSticker(resolved: ResolvedImagePack): Boolean {
    return when {
        usage.isNotEmpty() -> ImagePackUsage.STICKER in usage
        resolved.pack.usage.isNotEmpty() -> ImagePackUsage.STICKER in resolved.pack.usage
        else -> true // No usage specified anywhere means both
    }
}
