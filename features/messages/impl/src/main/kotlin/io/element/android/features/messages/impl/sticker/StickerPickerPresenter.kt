package io.element.android.features.messages.impl.sticker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import chat.schildi.imagepacks.ImagePackService
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class StickerPickerPresenter(
    private val room: JoinedRoom,
    private val imagePackService: ImagePackService,
    private val snackbarDispatcher: SnackbarDispatcher,
) : Presenter<StickerPickerState> {
    // Use a stable scope that won't be cancelled when bottom sheet dismisses
    private val sendScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Composable
    override fun present(): StickerPickerState {
        var isLoading by remember { mutableStateOf(true) }
        var packs by remember { mutableStateOf<ImmutableList<StickerPack>>(persistentListOf()) }
        val hadLoadErrors by imagePackService.hadLoadErrors.collectAsState()

        LaunchedEffect(Unit) {
            try {
                Timber.d("ImagePacks: StickerPickerPresenter loading packs...")
                isLoading = true
                val stickerPacks = imagePackService.getStickersByPack(room)
                Timber.d("ImagePacks: Got ${stickerPacks.size} sticker packs")
                packs = stickerPacks.map { (resolved, images) ->
                    Timber.d("ImagePacks: Pack '${resolved.pack.displayName}' has ${images.size} stickers")
                    StickerPack(
                        name = resolved.pack.displayName ?: "Stickers",
                        avatarUrl = resolved.pack.avatarUrl,
                        stickers = images.map { image ->
                            Sticker(
                                shortcode = image.shortcode,
                                url = image.url,
                                body = image.body,
                                info = image.info?.let { info ->
                                    StickerInfo(
                                        width = info.width,
                                        height = info.height,
                                        size = info.size,
                                        mimetype = info.mimetype,
                                    )
                                },
                            )
                        }.toImmutableList(),
                    )
                }.toImmutableList()
            } catch (e: Exception) {
                Timber.e(e, "ImagePacks: Failed to load sticker packs")
            } finally {
                isLoading = false
            }
        }

        fun handleEvent(event: StickerPickerEvent) {
            when (event) {
                is StickerPickerEvent.SendSticker -> {
                    Timber.d("ImagePacks: SendSticker event received for ${event.sticker.shortcode}")
                    sendScope.launch {
                        sendSticker(event.sticker)
                            .onFailure { e ->
                                Timber.e(e, "ImagePacks: Failed to send sticker")
                                snackbarDispatcher.post(SnackbarMessage(CommonStrings.common_error))
                            }
                    }
                }
                is StickerPickerEvent.Dismiss -> {
                    // handled by parent
                }
            }
        }

        return StickerPickerState(
            packs = packs,
            isLoading = isLoading,
            hadLoadErrors = hadLoadErrors,
            eventSink = ::handleEvent,
        )
    }

    private suspend fun sendSticker(sticker: Sticker): Result<Unit> {
        val stickerJson = buildJsonObject {
            put("body", sticker.body ?: sticker.shortcode)
            put("url", sticker.url)
            putJsonObject("info") {
                sticker.info?.width?.let { put("w", it) }
                sticker.info?.height?.let { put("h", it) }
                sticker.info?.size?.let { put("size", it) }
                sticker.info?.mimetype?.let { put("mimetype", it) }
            }
        }
        val jsonString = Json.encodeToString(stickerJson)
        Timber.d("ImagePacks: Sending sticker to room ${room.roomId}, encrypted=${room.info().isEncrypted}")
        return room.sendRaw("m.sticker", jsonString)
    }
}
