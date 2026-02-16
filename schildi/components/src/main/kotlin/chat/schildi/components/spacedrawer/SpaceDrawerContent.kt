package chat.schildi.components.spacedrawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chat.schildi.lib.preferences.ScPrefs
import chat.schildi.lib.preferences.value
import chat.schildi.lib.util.formatUnreadCount
import chat.schildi.theme.ScTheme
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.first

@Composable
fun SpaceDrawerContent(
    state: SpaceDrawerContentState,
    onAllChatsClick: () -> Unit,
    onSpaceClick: (List<String>) -> Unit,
    onRoomClick: ((RoomId) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val expandedPaths = remember { mutableStateMapOf<String, Boolean>() }
    val listState = rememberLazyListState()

    // Auto-expand ancestors of the selected space hierarchy (home mode)
    LaunchedEffect(state.selectedSpaceHierarchy) {
        if (state.selectedSpaceHierarchy.size > 1) {
            for (i in 0 until state.selectedSpaceHierarchy.size - 1) {
                expandedPaths[state.selectedSpaceHierarchy[i]] = true
            }
        }
    }

    // In chat mode, auto-expand the parent space and scroll to current room.
    // Uses rememberUpdatedState so the coroutine reads the latest (populated)
    // state when data arrives, not the empty initial state from collectAsState.
    // Expansion happens while the drawer is closed (preset like Discord).
    if (state.showRooms && state.currentRoomId != null) {
        val currentState by rememberUpdatedState(state)
        LaunchedEffect(state.currentRoomId) {
            // Wait for data to be populated (flows start with emptyList())
            snapshotFlow {
                currentState.pseudoSpaces.isNotEmpty() || currentState.realSpaces.isNotEmpty()
            }.first { it }

            fun findAndExpandParent(items: List<DrawerSpaceItem>): Boolean {
                for (item in items) {
                    if (item.rooms.any { it.roomId == currentState.currentRoomId }) {
                        expandedPaths[item.id] = true
                        return true
                    }
                    if (findAndExpandParent(item.children)) {
                        expandedPaths[item.id] = true
                        return true
                    }
                }
                return false
            }
            findAndExpandParent(currentState.pseudoSpaces)
            findAndExpandParent(currentState.realSpaces)

            // Compute target scroll index based on expanded state
            val targetIndex = computeCurrentRoomIndex(currentState, expandedPaths)
            if (targetIndex != null && targetIndex > 0) {
                // Wait for LazyColumn to recompose with expanded items
                snapshotFlow { listState.layoutInfo.totalItemsCount }
                    .first { it > targetIndex }
                listState.scrollToItem(targetIndex)
            }
        }
    }

    // Header
    Text(
        text = stringResource(chat.schildi.lib.R.string.sc_pref_category_spaces),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp),
    )
    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

    LazyColumn(state = listState, modifier = modifier) {
        // "All chats" item
        if (state.showAllChats) {
            item(key = "all_chats") {
                SpaceDrawerItemRow(
                    name = stringResource(chat.schildi.lib.R.string.sc_space_all_rooms_title),
                    icon = Icons.Filled.Home,
                    selected = state.allChatsSelected,
                    unreadCounts = state.allChatsUnreadCounts,
                    hasExpandChevron = false,
                    isExpanded = false,
                    depth = 0,
                    onClick = onAllChatsClick,
                    onToggleExpand = {},
                )
            }
        }

        // Pseudo-spaces: DMs first, then Home, then others
        val dmSpace = state.pseudoSpaces.find { it.id == "p:dm" }
        val homeSpace = state.pseudoSpaces.find { it.id == "p:spaceless/group" }
        val otherPseudos = state.pseudoSpaces.filter { it.id != "p:dm" && it.id != "p:spaceless/group" }

        if (dmSpace != null) {
            spaceDrawerItem(
                item = dmSpace,
                state = state,
                expandedPaths = expandedPaths,
                parentSelection = emptyList(),
                depth = 0,
                onSpaceClick = onSpaceClick,
                onRoomClick = onRoomClick,
                onToggleExpand = { expandedPaths[it] = !(expandedPaths[it] ?: false) },
            )
        }
        if (homeSpace != null) {
            spaceDrawerItem(
                item = homeSpace,
                state = state,
                expandedPaths = expandedPaths,
                parentSelection = emptyList(),
                depth = 0,
                onSpaceClick = onSpaceClick,
                onRoomClick = onRoomClick,
                onToggleExpand = { expandedPaths[it] = !(expandedPaths[it] ?: false) },
            )
        }
        otherPseudos.forEach { pseudo ->
            spaceDrawerItem(
                item = pseudo,
                state = state,
                expandedPaths = expandedPaths,
                parentSelection = emptyList(),
                depth = 0,
                onSpaceClick = onSpaceClick,
                onRoomClick = onRoomClick,
                onToggleExpand = { expandedPaths[it] = !(expandedPaths[it] ?: false) },
            )
        }

        // Divider between pseudo-spaces and real spaces
        if (state.realSpaces.isNotEmpty()) {
            item(key = "divider") {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }

        // Real spaces with recursive nesting
        spaceDrawerItems(
            items = state.realSpaces,
            state = state,
            expandedPaths = expandedPaths,
            parentSelection = emptyList(),
            depth = 0,
            onSpaceClick = onSpaceClick,
            onRoomClick = onRoomClick,
            onToggleExpand = { expandedPaths[it] = !(expandedPaths[it] ?: false) },
        )
    }
}

private fun LazyListScope.spaceDrawerItems(
    items: ImmutableList<DrawerSpaceItem>,
    state: SpaceDrawerContentState,
    expandedPaths: MutableMap<String, Boolean>,
    parentSelection: List<String>,
    depth: Int,
    onSpaceClick: (List<String>) -> Unit,
    onRoomClick: ((RoomId) -> Unit)?,
    onToggleExpand: (String) -> Unit,
) {
    items.forEach { item ->
        spaceDrawerItem(
            item = item,
            state = state,
            expandedPaths = expandedPaths,
            parentSelection = parentSelection,
            depth = depth,
            onSpaceClick = onSpaceClick,
            onRoomClick = onRoomClick,
            onToggleExpand = onToggleExpand,
        )
    }
}

private fun LazyListScope.spaceDrawerItem(
    item: DrawerSpaceItem,
    state: SpaceDrawerContentState,
    expandedPaths: MutableMap<String, Boolean>,
    parentSelection: List<String>,
    depth: Int,
    onSpaceClick: (List<String>) -> Unit,
    onRoomClick: ((RoomId) -> Unit)?,
    onToggleExpand: (String) -> Unit,
) {
    val fullSelection = parentSelection + listOf(item.id)
    val isSelected = state.selectedSpaceHierarchy == fullSelection
    val isAncestor = state.selectedSpaceHierarchy.size > fullSelection.size &&
        state.selectedSpaceHierarchy.subList(0, fullSelection.size) == fullSelection
    val hasChildren = item.children.isNotEmpty()
    val hasRooms = state.showRooms && item.rooms.isNotEmpty()
    val hasExpandChevron = hasChildren || hasRooms
    val isExpanded = expandedPaths[item.id] ?: false

    item(key = "space_${item.id}_$depth") {
        when (item) {
            is DrawerPseudoSpace -> SpaceDrawerItemRow(
                name = item.name,
                icon = item.icon,
                selected = isSelected,
                unreadCounts = item.unreadCounts,
                hasExpandChevron = hasExpandChevron,
                isExpanded = isExpanded,
                depth = depth,
                onClick = {
                    if (state.showRooms) {
                        onToggleExpand(item.id)
                    } else {
                        onSpaceClick(fullSelection)
                    }
                },
                onToggleExpand = { onToggleExpand(item.id) },
            )
            is DrawerRealSpace -> SpaceDrawerItemRow(
                name = item.name,
                avatarData = item.avatarData,
                selected = isSelected,
                unreadCounts = item.unreadCounts,
                hasExpandChevron = hasExpandChevron,
                isExpanded = isExpanded,
                depth = depth,
                onClick = {
                    if (state.showRooms) {
                        onToggleExpand(item.id)
                    } else {
                        onSpaceClick(fullSelection)
                    }
                },
                onToggleExpand = { onToggleExpand(item.id) },
            )
        }
    }

    // Render children and rooms if expanded
    if (hasExpandChevron && (isExpanded || isAncestor)) {
        if (isAncestor && !isExpanded) {
            expandedPaths[item.id] = true
        }

        // Sub-spaces first
        if (hasChildren) {
            spaceDrawerItems(
                items = item.children,
                state = state,
                expandedPaths = expandedPaths,
                parentSelection = fullSelection,
                depth = depth + 1,
                onSpaceClick = onSpaceClick,
                onRoomClick = onRoomClick,
                onToggleExpand = onToggleExpand,
            )
        }

        // Rooms under expanded space (chat mode)
        if (hasRooms && onRoomClick != null) {
            item.rooms.forEach { room ->
                item(key = "room_${item.id}_${room.roomId.value}") {
                    SpaceDrawerRoomRow(
                        room = room,
                        isCurrentRoom = room.roomId == state.currentRoomId,
                        depth = depth,
                        onClick = { onRoomClick(room.roomId) },
                    )
                }
            }
        }
    }
}

// Icon variant for pseudo-spaces
@Composable
private fun SpaceDrawerItemRow(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    unreadCounts: DrawerUnreadCounts?,
    hasExpandChevron: Boolean,
    isExpanded: Boolean,
    depth: Int,
    onClick: () -> Unit,
    onToggleExpand: () -> Unit,
) {
    SpaceDrawerItemRowInternal(
        name = name,
        selected = selected,
        unreadCounts = unreadCounts,
        hasExpandChevron = hasExpandChevron,
        isExpanded = isExpanded,
        depth = depth,
        onClick = onClick,
        onToggleExpand = onToggleExpand,
    ) { contentColor ->
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(AvatarSize.SelectParentSpace.dp),
            tint = contentColor,
        )
    }
}

// Avatar variant for real spaces
@Composable
private fun SpaceDrawerItemRow(
    name: String,
    avatarData: AvatarData,
    selected: Boolean,
    unreadCounts: DrawerUnreadCounts?,
    hasExpandChevron: Boolean,
    isExpanded: Boolean,
    depth: Int,
    onClick: () -> Unit,
    onToggleExpand: () -> Unit,
) {
    SpaceDrawerItemRowInternal(
        name = name,
        selected = selected,
        unreadCounts = unreadCounts,
        hasExpandChevron = hasExpandChevron,
        isExpanded = isExpanded,
        depth = depth,
        onClick = onClick,
        onToggleExpand = onToggleExpand,
    ) {
        Avatar(
            avatarData = avatarData.copy(size = AvatarSize.SelectParentSpace),
            avatarType = AvatarType.Sc(RoundedCornerShape(6.dp)),
        )
    }
}

@Composable
private fun SpaceDrawerItemRowInternal(
    name: String,
    selected: Boolean,
    unreadCounts: DrawerUnreadCounts?,
    hasExpandChevron: Boolean,
    isExpanded: Boolean,
    depth: Int,
    onClick: () -> Unit,
    onToggleExpand: () -> Unit,
    leadingIcon: @Composable (Color) -> Unit,
) {
    val bgColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val indentPadding = (depth * 20).dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp + indentPadding, end = 12.dp, top = 2.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = if (hasExpandChevron) 4.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon(contentColor)
        Spacer(Modifier.width(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        SpaceDrawerTrailingBadge(unreadCounts)
        if (hasExpandChevron) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SpaceDrawerRoomRow(
    room: DrawerRoomItem,
    isCurrentRoom: Boolean,
    depth: Int,
    onClick: () -> Unit,
) {
    val bgColor = if (isCurrentRoom) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val indentPadding = (depth * 20).dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp + indentPadding, end = 12.dp, top = 1.dp, bottom = 1.dp)
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            avatarData = room.avatarData,
            avatarType = AvatarType.Room(heroes = room.heroes),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = room.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isCurrentRoom) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (room.hasNotifications) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
            )
        }
    }
}

/**
 * Walk the data model in the same order as the LazyColumn to compute the
 * index of the item for the current room.
 */
private fun computeCurrentRoomIndex(
    state: SpaceDrawerContentState,
    expandedPaths: Map<String, Boolean>,
): Int? {
    val targetRoomId = state.currentRoomId ?: return null
    var index = 0

    if (state.showAllChats) index++ // "all_chats" item

    // Same ordering as the LazyColumn: DMs, Home, other pseudos, divider, real spaces
    val dmSpace = state.pseudoSpaces.find { it.id == "p:dm" }
    val homeSpace = state.pseudoSpaces.find { it.id == "p:spaceless/group" }
    val otherPseudos = state.pseudoSpaces.filter { it.id != "p:dm" && it.id != "p:spaceless/group" }

    val orderedPseudos = buildList {
        if (dmSpace != null) add(dmSpace)
        if (homeSpace != null) add(homeSpace)
        addAll(otherPseudos)
    }

    for (item in orderedPseudos) {
        val result = countToRoom(item, state, expandedPaths, targetRoomId, index)
        if (result.found) return result.index
        index = result.index
    }

    if (state.realSpaces.isNotEmpty()) index++ // divider

    for (item in state.realSpaces) {
        val result = countToRoom(item, state, expandedPaths, targetRoomId, index)
        if (result.found) return result.index
        index = result.index
    }

    return null
}

private data class CountResult(val index: Int, val found: Boolean)

private fun countToRoom(
    item: DrawerSpaceItem,
    state: SpaceDrawerContentState,
    expandedPaths: Map<String, Boolean>,
    targetRoomId: io.element.android.libraries.matrix.api.core.RoomId,
    startIndex: Int,
): CountResult {
    var index = startIndex
    index++ // the space item itself

    val isExpanded = expandedPaths[item.id] ?: false
    if (!isExpanded) return CountResult(index, false)

    // Children first
    for (child in item.children) {
        val result = countToRoom(child, state, expandedPaths, targetRoomId, index)
        if (result.found) return result
        index = result.index
    }

    // Rooms
    if (state.showRooms) {
        for (room in item.rooms) {
            if (room.roomId == targetRoomId) return CountResult(index, true)
            index++
        }
    }

    return CountResult(index, false)
}

@Composable
private fun SpaceDrawerTrailingBadge(
    unreadCounts: DrawerUnreadCounts?,
) {
    val mode = ScPrefs.SPACE_UNREAD_COUNTS.value()
    if (unreadCounts == null || mode == ScPrefs.SpaceUnreadCountMode.HIDE) return

    val countChats = mode == ScPrefs.SpaceUnreadCountMode.CHATS
    val count: Long
    val badgeBg: Color
    val badgeFg: Color
    when {
        unreadCounts.notifiedMessages > 0 -> {
            count = if (countChats) unreadCounts.notifiedChats else unreadCounts.notifiedMessages
            if (unreadCounts.mentionedMessages > 0) {
                badgeBg = ElementTheme.colors.bgCriticalPrimary
                badgeFg = ScTheme.exposures.colorOnAccent
            } else {
                badgeBg = MaterialTheme.colorScheme.primary
                badgeFg = MaterialTheme.colorScheme.onPrimary
            }
        }
        unreadCounts.mentionedMessages > 0 -> {
            count = if (countChats) unreadCounts.mentionedChats else unreadCounts.mentionedMessages
            badgeBg = ElementTheme.colors.bgCriticalPrimary
            badgeFg = ScTheme.exposures.colorOnAccent
        }
        unreadCounts.markedUnreadChats > 0 -> {
            count = unreadCounts.markedUnreadChats
            badgeBg = MaterialTheme.colorScheme.primary
            badgeFg = MaterialTheme.colorScheme.onPrimary
        }
        unreadCounts.unreadMessages > 0 && ScPrefs.RENDER_SILENT_UNREAD.value() -> {
            count = if (countChats) unreadCounts.unreadChats else unreadCounts.unreadMessages
            badgeBg = MaterialTheme.colorScheme.surfaceVariant
            badgeFg = MaterialTheme.colorScheme.onSurfaceVariant
        }
        else -> return
    }

    Spacer(Modifier.width(8.dp))
    Box(
        modifier = Modifier
            .background(badgeBg, RoundedCornerShape(12.dp))
            .sizeIn(minWidth = 24.dp, minHeight = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = formatUnreadCount(count),
            color = badgeFg,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
