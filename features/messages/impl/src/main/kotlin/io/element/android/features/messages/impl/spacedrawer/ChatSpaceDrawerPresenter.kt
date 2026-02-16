package io.element.android.features.messages.impl.spacedrawer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import chat.schildi.components.spacedrawer.DrawerPseudoSpace
import chat.schildi.components.spacedrawer.DrawerRealSpace
import chat.schildi.components.spacedrawer.DrawerRoomItem
import chat.schildi.lib.preferences.ScPrefs
import chat.schildi.lib.preferences.value
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.roomlist.RoomListService
import io.element.android.libraries.matrix.api.roomlist.RoomSummary
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

// SC: Manually-instantiated presenter for Discord-like channel drawer in chat rooms.
// Shows spaces as a recursive hierarchy with rooms under each space.
class ChatSpaceDrawerPresenter(
    private val currentRoomId: RoomId,
    private val roomListService: RoomListService,
) {
    @Composable
    fun present(): ChatSpaceDrawerState {
        val spaceNavEnabled = ScPrefs.SPACE_NAV.value()

        if (!spaceNavEnabled) {
            return ChatSpaceDrawerState(
                enabled = false,
                pseudoSpaces = persistentListOf(),
                realSpaces = persistentListOf(),
                currentRoomId = currentRoomId,
                currentSpaceId = "",
            )
        }

        val spaceSummaries by roomListService.allSpaces.summaries
            .collectAsState(initial = emptyList())

        val allRoomSummaries by roomListService.allRooms.summaries
            .collectAsState(initial = emptyList())

        val result = remember(spaceSummaries, allRoomSummaries, currentRoomId) {
            buildHierarchy(spaceSummaries, allRoomSummaries, currentRoomId)
        }

        return ChatSpaceDrawerState(
            enabled = true,
            pseudoSpaces = result.pseudoSpaces,
            realSpaces = result.realSpaces,
            currentRoomId = currentRoomId,
            currentSpaceId = result.currentSpaceId,
        )
    }

    private data class BuildResult(
        val pseudoSpaces: ImmutableList<DrawerPseudoSpace>,
        val realSpaces: ImmutableList<DrawerRealSpace>,
        val currentSpaceId: String,
    )

    private fun buildHierarchy(
        spaceSummaries: List<RoomSummary>,
        allRoomSummaries: List<RoomSummary>,
        roomId: RoomId,
    ): BuildResult {
        val spaceRoomIds = spaceSummaries.map { it.roomId.value }.toSet()
        val allSpaceChildIds = spaceSummaries
            .flatMap { it.info.spaceChildren.map { c -> c.roomId } }.toSet()

        // Build DMs pseudo-space
        val dmRooms = allRoomSummaries.filter { it.info.isDirect && !it.info.isSpace }
        val dmSpace = DrawerPseudoSpace(
            id = SECTION_DM,
            name = "Direct Messages",
            icon = Icons.Default.Person,
            rooms = dmRooms.toDrawerRoomItems(),
        )

        // Build Home pseudo-space (spaceless non-DM rooms)
        val homeRooms = allRoomSummaries.filter {
            !it.info.isDirect && !it.info.isSpace && !allSpaceChildIds.contains(it.roomId.value)
        }
        val homeSpace = DrawerPseudoSpace(
            id = SECTION_HOME,
            name = "Home",
            icon = Icons.Default.Tag,
            rooms = homeRooms.toDrawerRoomItems(),
        )

        val pseudoSpaces = buildList {
            if (dmSpace.rooms.isNotEmpty()) add(dmSpace)
            if (homeSpace.rooms.isNotEmpty()) add(homeSpace)
        }.toImmutableList()

        // Build recursive space hierarchy
        // Find top-level spaces (spaces not listed as child of any other space)
        val topLevelSpaces = spaceSummaries.filter { space ->
            spaceSummaries.none { other ->
                other.info.spaceChildren.any { it.roomId == space.roomId.value }
            }
        }

        fun buildSpaceTree(space: RoomSummary, visited: Set<String> = emptySet()): DrawerRealSpace {
            val childInfos = space.info.spaceChildren
            val childSpaces = childInfos.mapNotNull { child ->
                if (child.roomId in visited) null
                else spaceSummaries.find { it.roomId.value == child.roomId }
            }
            val childRooms = childInfos.mapNotNull { child ->
                if (child.roomId in spaceRoomIds) null
                else allRoomSummaries.find { it.roomId.value == child.roomId }
            }
            val newVisited = visited + space.roomId.value
            return DrawerRealSpace(
                id = space.roomId.value,
                name = space.info.name ?: "Space",
                avatarData = AvatarData(
                    id = space.roomId.value,
                    name = space.info.name,
                    url = space.info.avatarUrl,
                    size = AvatarSize.SelectParentSpace,
                ),
                children = childSpaces.map { buildSpaceTree(it, newVisited) }.toImmutableList(),
                rooms = childRooms.toDrawerRoomItems(),
            )
        }

        val realSpaces = topLevelSpaces
            .map { buildSpaceTree(it) }
            .filter { it.rooms.isNotEmpty() || it.children.isNotEmpty() }
            .toImmutableList()

        // Determine which section the current room belongs to
        val currentSpaceId = findCurrentSection(spaceSummaries, allRoomSummaries, roomId, allSpaceChildIds)

        return BuildResult(
            pseudoSpaces = pseudoSpaces,
            realSpaces = realSpaces,
            currentSpaceId = currentSpaceId,
        )
    }

    private fun findCurrentSection(
        spaceSummaries: List<RoomSummary>,
        allRoomSummaries: List<RoomSummary>,
        roomId: RoomId,
        allSpaceChildIds: Set<String>,
    ): String {
        // Check spaces first
        val parentSpace = spaceSummaries.firstOrNull { space ->
            space.info.spaceChildren.any { it.roomId == roomId.value }
        }
        if (parentSpace != null) return parentSpace.roomId.value

        // Check if DM
        val currentRoom = allRoomSummaries.find { it.roomId == roomId }
        if (currentRoom?.info?.isDirect == true) return SECTION_DM

        // Otherwise Home
        return SECTION_HOME
    }

    private fun List<RoomSummary>.toDrawerRoomItems(): ImmutableList<DrawerRoomItem> {
        return map { summary ->
            val heroAvatars = summary.info.heroes.map { user ->
                AvatarData(
                    id = user.userId.value,
                    name = user.displayName,
                    url = user.avatarUrl,
                    size = AvatarSize.RoomSelectRoomListItem,
                )
            }.toImmutableList()
            DrawerRoomItem(
                roomId = summary.roomId,
                name = summary.info.name ?: "",
                avatarData = AvatarData(
                    id = summary.roomId.value,
                    name = summary.info.name,
                    url = summary.info.avatarUrl,
                    size = AvatarSize.RoomSelectRoomListItem,
                ),
                heroes = heroAvatars,
                isDirect = summary.info.isDirect,
                hasNotifications = summary.info.numUnreadNotifications > 0 || summary.info.numUnreadMentions > 0,
            )
        }.toImmutableList()
    }

    companion object {
        const val SECTION_DM = "p:dm"
        const val SECTION_HOME = "p:spaceless/group"
    }
}
