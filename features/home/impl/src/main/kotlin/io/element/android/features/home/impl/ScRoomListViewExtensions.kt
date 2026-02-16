package io.element.android.features.home.impl

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import chat.schildi.features.home.spaces.SpaceListDataSource
import chat.schildi.features.home.spaces.resolveSpaceName
import chat.schildi.lib.preferences.ScPrefs
import chat.schildi.lib.preferences.value
import io.element.android.features.home.impl.roomlist.RoomListContentState
import io.element.android.features.home.impl.roomlist.RoomListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun scRoomListScrollBehavior(): TopAppBarScrollBehavior? {
    return if (ScPrefs.ELEMENT_ROOM_LIST_FILTERS.value())
        null
    else
        TopAppBarDefaults.pinnedScrollBehavior()
}

@Composable
fun RoomListState.resolveSpaceName() = (this.contentState as? RoomListContentState.Rooms)?.let { it.spacesList.resolveSpaceName(it.spaceSelectionHierarchy) }

fun RoomListState.isShowingUpstreamSpaceList() = (this.contentState as? RoomListContentState.Rooms)?.let {
    it.spaceSelectionHierarchy.firstOrNull() == SpaceListDataSource.UpstreamSpaceListItem.SPACE_ID
} == true
