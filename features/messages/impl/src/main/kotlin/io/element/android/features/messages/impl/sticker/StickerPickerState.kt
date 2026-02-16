package io.element.android.features.messages.impl.sticker

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class StickerPickerState(
    val packs: ImmutableList<StickerPack>,
    val isLoading: Boolean,
    val hadLoadErrors: Boolean,
    val eventSink: (StickerPickerEvent) -> Unit,
)

@Immutable
data class StickerPack(
    val name: String,
    val avatarUrl: String?,
    val stickers: ImmutableList<Sticker>,
)

@Immutable
data class Sticker(
    val shortcode: String,
    val url: String,
    val body: String?,
    val info: StickerInfo?,
)

@Immutable
data class StickerInfo(
    val width: Int?,
    val height: Int?,
    val size: Long?,
    val mimetype: String?,
)
