package chat.schildi.features.home.spaces

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import chat.schildi.lib.preferences.ScAppStateStore
import chat.schildi.lib.preferences.ScPrefs
import chat.schildi.lib.preferences.value
import chat.schildi.lib.util.formatUnreadCount
import chat.schildi.theme.ScTheme
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.theme.unreadIndicator
import io.element.android.libraries.designsystem.utils.OnLifecycleEvent
import kotlinx.coroutines.launch

@Composable
fun AbstractSpaceIcon(
    space: SpaceListDataSource.AbstractSpaceHierarchyItem?,
    size: AvatarSize,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    shape: Shape = CircleShape,
) {
    when(space) {
        is SpaceListDataSource.SpaceHierarchyItem -> Avatar(space.info.avatarData.copy(size = size), avatarType = AvatarType.Sc(shape), modifier = modifier)
        is SpaceListDataSource.PseudoSpaceItem -> PseudoSpaceIcon(imageVector = space.icon, size = size, color = color, modifier = modifier)
        else -> PseudoSpaceIcon(Icons.Filled.Home, AvatarSize.BottomSpaceBar, color = color, modifier = modifier)
    }
}

@Composable
fun PseudoSpaceIcon(
    imageVector: ImageVector,
    size: AvatarSize,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        modifier = modifier.size(size.dp),
        tint = color,
    )
}

@Composable
fun UnreadCountBox(unreadCounts: SpaceUnreadCountsDataSource.SpaceUnreadCounts?, offset: Dp, content: @Composable () -> Unit) {
    val mode = ScPrefs.SPACE_UNREAD_COUNTS.value()
    if (unreadCounts == null || mode == ScPrefs.SpaceUnreadCountMode.HIDE) {
        content()
        return
    }
    val countChats = mode == ScPrefs.SpaceUnreadCountMode.CHATS
    val count: Long
    val badgeColor: Color
    var outlinedBadge = false
    when {
        unreadCounts.notifiedMessages > 0 -> {
            count = if (countChats) unreadCounts.notifiedChats else unreadCounts.notifiedMessages
            badgeColor = if (unreadCounts.mentionedMessages > 0) ElementTheme.colors.bgCriticalPrimary else ElementTheme.colors.unreadIndicator
        }
        unreadCounts.mentionedMessages > 0 -> {
            count = if (countChats) unreadCounts.mentionedChats else unreadCounts.mentionedMessages
            badgeColor = ElementTheme.colors.bgCriticalPrimary
        }
        unreadCounts.markedUnreadChats > 0 -> {
            count = unreadCounts.markedUnreadChats
            badgeColor = ElementTheme.colors.unreadIndicator
            outlinedBadge = true
        }
        unreadCounts.unreadMessages > 0 && ScPrefs.RENDER_SILENT_UNREAD.value() -> {
            count = if (countChats) unreadCounts.unreadChats else unreadCounts.unreadMessages
            badgeColor = ScTheme.exposures.unreadBadgeColor
        }
        else -> {
            // No badge to show
            content()
            return
        }
    }
    Box {
        content()
        Box(
            modifier = Modifier
                .offset(offset, -offset)
                .let {
                    if (outlinedBadge)
                        it
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                            .border(1.dp, badgeColor, RoundedCornerShape(8.dp))
                    else
                        it.background(badgeColor.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                }
                .sizeIn(minWidth = 16.dp, minHeight = 16.dp)
                .align(Alignment.TopEnd)
        ) {
            Text(
                text = formatUnreadCount(count),
                color = if (outlinedBadge) badgeColor else ScTheme.exposures.colorOnAccent,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 2.dp)
            )
        }
        // Keep icon centered
        Spacer(
            Modifier
                .width(offset)
                .offset(-offset, -offset)
                .align(Alignment.TopStart))
    }
}

@Composable
fun PersistSpaceOnPause(scAppStateStore: ScAppStateStore, spaceAwareRoomListDataSource: SpaceAwareRoomListDataSource) {
    val scope = rememberCoroutineScope()
    OnLifecycleEvent { _, event ->
        when (event) {
            Lifecycle.Event.ON_PAUSE -> spaceAwareRoomListDataSource.spaceSelectionHierarchy.value?.let {
                scope.launch { scAppStateStore.persistSpaceSelection(it) }
            }
            else -> Unit
        }
    }
}

// For other places in the UI wanting to render space icons
@Composable
fun GenericSpaceIcon(
    space: SpaceListDataSource.AbstractSpaceHierarchyItem?,
    modifier: Modifier = Modifier,
    size: AvatarSize = AvatarSize.BottomSpaceBar,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    AbstractSpaceIcon(space = space, size = size, shape = shape, modifier = modifier)
}
