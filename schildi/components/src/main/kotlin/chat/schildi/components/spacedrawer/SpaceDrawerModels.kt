package chat.schildi.components.spacedrawer

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
sealed interface DrawerSpaceItem {
    val id: String
    val name: String
    val children: ImmutableList<DrawerSpaceItem>
    val rooms: ImmutableList<DrawerRoomItem>
    val unreadCounts: DrawerUnreadCounts?
}

@Immutable
data class DrawerPseudoSpace(
    override val id: String,
    override val name: String,
    val icon: ImageVector,
    override val children: ImmutableList<DrawerSpaceItem> = persistentListOf(),
    override val rooms: ImmutableList<DrawerRoomItem> = persistentListOf(),
    override val unreadCounts: DrawerUnreadCounts? = null,
) : DrawerSpaceItem

@Immutable
data class DrawerRealSpace(
    override val id: String,
    override val name: String,
    val avatarData: AvatarData,
    override val children: ImmutableList<DrawerSpaceItem>,
    override val rooms: ImmutableList<DrawerRoomItem> = persistentListOf(),
    override val unreadCounts: DrawerUnreadCounts? = null,
) : DrawerSpaceItem

@Immutable
data class DrawerRoomItem(
    val roomId: RoomId,
    val name: String,
    val avatarData: AvatarData,
    val heroes: ImmutableList<AvatarData>,
    val isDirect: Boolean,
    val hasNotifications: Boolean,
)

@Immutable
data class DrawerUnreadCounts(
    val mentionedMessages: Long = 0,
    val notifiedMessages: Long = 0,
    val unreadMessages: Long = 0,
    val mentionedChats: Long = 0,
    val notifiedChats: Long = 0,
    val unreadChats: Long = 0,
    val markedUnreadChats: Long = 0,
)

@Immutable
data class SpaceDrawerContentState(
    val showAllChats: Boolean,
    val allChatsSelected: Boolean,
    val allChatsUnreadCounts: DrawerUnreadCounts?,
    val pseudoSpaces: ImmutableList<DrawerSpaceItem>,
    val realSpaces: ImmutableList<DrawerSpaceItem>,
    val selectedSpaceHierarchy: ImmutableList<String>,
    val currentRoomId: RoomId?,
    val showRooms: Boolean,
)
