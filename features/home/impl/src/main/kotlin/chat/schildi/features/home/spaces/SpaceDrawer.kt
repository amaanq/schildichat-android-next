package chat.schildi.features.home.spaces

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import kotlinx.collections.immutable.ImmutableList
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
    val expandedPaths = remember { mutableStateMapOf<String, Boolean>() }

    // Auto-expand ancestors of the current selection
    LaunchedEffect(spaceSelectionHierarchy) {
        if (spaceSelectionHierarchy.size > 1) {
            // Expand all ancestors (all except the last item in the hierarchy)
            for (i in 0 until spaceSelectionHierarchy.size - 1) {
                expandedPaths[spaceSelectionHierarchy[i]] = true
            }
        }
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
                SpaceDrawerHeader()

                val showAllChats = ScPrefs.PSEUDO_SPACE_ALL_ROOMS.value()
                LazyColumn {
                    // "All chats" item
                    if (showAllChats) {
                        item(key = "all_chats") {
                            SpaceDrawerAllChatsItem(
                                unreadCounts = totalUnreadCounts,
                                selected = spaceSelectionHierarchy.isEmpty(),
                                onClick = {
                                    onSpaceSelected(emptyList())
                                    scope.launch { drawerState.close() }
                                },
                            )
                        }
                    }

                    // Recursive space items
                    spaceDrawerItems(
                        spacesList = spacesList,
                        spaceSelectionHierarchy = spaceSelectionHierarchy,
                        parentSelection = emptyList(),
                        expandedPaths = expandedPaths,
                        depth = 0,
                        onSpaceSelected = { selection ->
                            onSpaceSelected(selection)
                            scope.launch { drawerState.close() }
                        },
                        onToggleExpand = { selectionId ->
                            expandedPaths[selectionId] = !(expandedPaths[selectionId] ?: false)
                        },
                    )
                }
            }
        },
        content = content,
    )
}

@Composable
private fun SpaceDrawerHeader() {
    Text(
        text = stringResource(chat.schildi.lib.R.string.sc_pref_category_spaces),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp),
    )
    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun SpaceDrawerAllChatsItem(
    unreadCounts: SpaceUnreadCountsDataSource.SpaceUnreadCounts?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PseudoSpaceIcon(
            imageVector = Icons.Filled.Home,
            size = AvatarSize.SelectParentSpace,
            color = contentColor,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(chat.schildi.lib.R.string.sc_space_all_rooms_title),
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        SpaceDrawerTrailingBadge(unreadCounts)
    }
}

private fun LazyListScope.spaceDrawerItems(
    spacesList: ImmutableList<SpaceListDataSource.AbstractSpaceHierarchyItem>,
    spaceSelectionHierarchy: List<String>,
    parentSelection: List<String>,
    expandedPaths: MutableMap<String, Boolean>,
    depth: Int,
    onSpaceSelected: (List<String>) -> Unit,
    onToggleExpand: (String) -> Unit,
) {
    spacesList.forEach { space ->
        val fullSelection = parentSelection + listOf(space.selectionId)
        val isSelected = spaceSelectionHierarchy == fullSelection
        val isAncestor = spaceSelectionHierarchy.size > fullSelection.size &&
            spaceSelectionHierarchy.subList(0, fullSelection.size) == fullSelection
        val hasChildren = space.spaces.isNotEmpty()
        val isExpanded = expandedPaths[space.selectionId] ?: false

        item(key = "space_${space.selectionId}_$depth") {
            SpaceDrawerItem(
                space = space,
                selected = isSelected,
                hasChildren = hasChildren,
                isExpanded = isExpanded,
                depth = depth,
                onClick = { onSpaceSelected(fullSelection) },
                onToggleExpand = { onToggleExpand(space.selectionId) },
            )
        }

        // Render children if expanded
        if (hasChildren && (isExpanded || isAncestor)) {
            // Ensure it's marked as expanded when it's an ancestor
            if (isAncestor && !isExpanded) {
                expandedPaths[space.selectionId] = true
            }
            spaceDrawerItems(
                spacesList = space.spaces,
                spaceSelectionHierarchy = spaceSelectionHierarchy,
                parentSelection = fullSelection,
                expandedPaths = expandedPaths,
                depth = depth + 1,
                onSpaceSelected = onSpaceSelected,
                onToggleExpand = onToggleExpand,
            )
        }
    }
}

@Composable
private fun SpaceDrawerItem(
    space: SpaceListDataSource.AbstractSpaceHierarchyItem,
    selected: Boolean,
    hasChildren: Boolean,
    isExpanded: Boolean,
    depth: Int,
    onClick: () -> Unit,
    onToggleExpand: () -> Unit,
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
            .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = if (hasChildren) 4.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AbstractSpaceIcon(
            space = space,
            size = AvatarSize.SelectParentSpace,
            color = contentColor,
            shape = RoundedCornerShape(6.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = space.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        SpaceDrawerTrailingBadge(space.unreadCounts)
        if (hasChildren) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
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
private fun SpaceDrawerTrailingBadge(
    unreadCounts: SpaceUnreadCountsDataSource.SpaceUnreadCounts?,
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
