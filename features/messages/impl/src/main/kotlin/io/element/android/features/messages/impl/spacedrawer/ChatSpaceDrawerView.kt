package io.element.android.features.messages.impl.spacedrawer

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import chat.schildi.components.spacedrawer.SpaceDrawerContent
import chat.schildi.components.spacedrawer.SpaceDrawerContentState
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

// SC: Thin wrapper that maps ChatSpaceDrawerState to the shared SpaceDrawerContent.
@Composable
fun ChatSpaceDrawerContent(
    state: ChatSpaceDrawerState,
    onRoomClick: (RoomId) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(
        modifier = modifier.statusBarsPadding(),
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        SpaceDrawerContent(
            state = state.toSpaceDrawerContentState(),
            onAllChatsClick = {},
            onSpaceClick = {},
            onRoomClick = onRoomClick,
        )
    }
}

private fun ChatSpaceDrawerState.toSpaceDrawerContentState(): SpaceDrawerContentState {
    return SpaceDrawerContentState(
        showAllChats = false,
        allChatsSelected = false,
        allChatsUnreadCounts = null,
        pseudoSpaces = pseudoSpaces.toImmutableList(),
        realSpaces = realSpaces.toImmutableList(),
        selectedSpaceHierarchy = persistentListOf(),
        currentRoomId = currentRoomId,
        showRooms = true,
    )
}
