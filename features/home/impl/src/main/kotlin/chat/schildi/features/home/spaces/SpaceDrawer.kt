package chat.schildi.features.home.spaces

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import chat.schildi.components.spacedrawer.DrawerPseudoSpace
import chat.schildi.components.spacedrawer.DrawerRealSpace
import chat.schildi.components.spacedrawer.DrawerSpaceItem
import chat.schildi.components.spacedrawer.DrawerUnreadCounts
import chat.schildi.components.spacedrawer.SpaceDrawerContent
import chat.schildi.components.spacedrawer.SpaceDrawerContentState
import chat.schildi.lib.preferences.ScPrefs
import chat.schildi.lib.preferences.value
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@Composable
fun SpaceNavigationDrawer(
    drawerState: DrawerState,
    spacesList: ImmutableList<SpaceListDataSource.AbstractSpaceHierarchyItem>,
    totalUnreadCounts: SpaceUnreadCountsDataSource.SpaceUnreadCounts?,
    spaceSelectionHierarchy: ImmutableList<String>,
    onSpaceSelected: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!ScPrefs.SPACE_NAV.value() || spacesList.isEmpty()) {
        content()
        return
    }

    val scope = rememberCoroutineScope()
    val showAllChats = ScPrefs.PSEUDO_SPACE_ALL_ROOMS.value()

    val drawerContentState = remember(spacesList, totalUnreadCounts, spaceSelectionHierarchy, showAllChats) {
        buildSpaceDrawerContentState(spacesList, totalUnreadCounts, spaceSelectionHierarchy, showAllChats)
    }

    // SC: Close drawer on back gesture/press
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        modifier = modifier,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.statusBarsPadding(),
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                SpaceDrawerContent(
                    state = drawerContentState,
                    onAllChatsClick = {
                        onSpaceSelected(emptyList())
                        scope.launch { drawerState.close() }
                    },
                    onSpaceClick = { selection ->
                        onSpaceSelected(selection)
                        scope.launch { drawerState.close() }
                    },
                )
            }
        },
        content = content,
    )
}

private fun buildSpaceDrawerContentState(
    spacesList: ImmutableList<SpaceListDataSource.AbstractSpaceHierarchyItem>,
    totalUnreadCounts: SpaceUnreadCountsDataSource.SpaceUnreadCounts?,
    spaceSelectionHierarchy: ImmutableList<String>,
    showAllChats: Boolean,
): SpaceDrawerContentState {
    val pseudoSpaces = spacesList
        .filter { it.selectionId.startsWith("p:") }
        .map { it.toDrawerSpaceItem() }
        .toImmutableList()
    val realSpaces = spacesList
        .filter { it.selectionId.startsWith("s:") }
        .map { it.toDrawerSpaceItem() }
        .toImmutableList()

    return SpaceDrawerContentState(
        showAllChats = showAllChats,
        allChatsSelected = spaceSelectionHierarchy.isEmpty(),
        allChatsUnreadCounts = totalUnreadCounts?.toDrawerUnreadCounts(),
        pseudoSpaces = pseudoSpaces,
        realSpaces = realSpaces,
        selectedSpaceHierarchy = spaceSelectionHierarchy,
        currentRoomId = null,
        showRooms = false,
    )
}

private fun SpaceListDataSource.AbstractSpaceHierarchyItem.toDrawerSpaceItem(): DrawerSpaceItem {
    return when (this) {
        is SpaceListDataSource.SpaceHierarchyItem -> DrawerRealSpace(
            id = selectionId,
            name = name,
            avatarData = info.avatarData,
            children = spaces.map { it.toDrawerSpaceItem() }.toImmutableList(),
            rooms = persistentListOf(),
            unreadCounts = unreadCounts?.toDrawerUnreadCounts(),
        )
        is SpaceListDataSource.PseudoSpaceItem -> DrawerPseudoSpace(
            id = selectionId,
            name = name,
            icon = icon,
            children = persistentListOf(),
            rooms = persistentListOf(),
            unreadCounts = unreadCounts?.toDrawerUnreadCounts(),
        )
    }
}

private fun SpaceUnreadCountsDataSource.SpaceUnreadCounts.toDrawerUnreadCounts(): DrawerUnreadCounts {
    return DrawerUnreadCounts(
        mentionedMessages = mentionedMessages,
        notifiedMessages = notifiedMessages,
        unreadMessages = unreadMessages,
        mentionedChats = mentionedChats,
        notifiedChats = notifiedChats,
        unreadChats = unreadChats,
        markedUnreadChats = markedUnreadChats,
    )
}
