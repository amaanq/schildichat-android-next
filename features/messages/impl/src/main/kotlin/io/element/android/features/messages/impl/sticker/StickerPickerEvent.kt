package io.element.android.features.messages.impl.sticker

sealed interface StickerPickerEvent {
    data class SendSticker(val sticker: Sticker) : StickerPickerEvent
    data object Dismiss : StickerPickerEvent
}
