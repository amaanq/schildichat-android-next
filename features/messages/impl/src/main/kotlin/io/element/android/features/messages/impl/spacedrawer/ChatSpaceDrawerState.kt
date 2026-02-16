package io.element.android.features.messages.impl.spacedrawer

import androidx.compose.runtime.Immutable
import chat.schildi.components.spacedrawer.DrawerPseudoSpace
import chat.schildi.components.spacedrawer.DrawerRealSpace
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class ChatSpaceDrawerState(
    val enabled: Boolean,
    val pseudoSpaces: ImmutableList<DrawerPseudoSpace>,
    val realSpaces: ImmutableList<DrawerRealSpace>,
    val currentRoomId: RoomId,
    val currentSpaceId: String,
)
