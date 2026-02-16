package io.element.android.features.messages.impl.sticker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.hide
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.ui.media.MediaRequestData
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerPickerBottomSheet(
    state: StickerPickerState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        StickerPickerContent(
            state = state,
            onStickerClick = { sticker ->
                state.eventSink(StickerPickerEvent.SendSticker(sticker))
                sheetState.hide(coroutineScope) { onDismiss() }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StickerPickerContent(
    state: StickerPickerState,
    onStickerClick: (Sticker) -> Unit,
) {
    // Adaptive max height: 45% of screen height, bounded to a reasonable range
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val gridMaxHeight = (screenHeight * 0.45f).coerceIn(250.dp, 500.dp)

    if (state.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 250.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (state.packs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 250.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (state.hadLoadErrors) "Failed to load sticker packs" else "No sticker packs available",
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                )
                if (state.hadLoadErrors) {
                    Text(
                        text = "Check your connection and try again",
                        style = ElementTheme.typography.fontBodySmRegular,
                        color = ElementTheme.colors.textDisabled,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        val gridState = rememberLazyGridState()
        val coroutineScope = rememberCoroutineScope()

        // Compute item indices for each pack's header in the combined grid
        val packHeaderIndices = remember(state.packs) {
            buildList {
                var idx = 0
                for (pack in state.packs) {
                    add(idx)
                    idx += 1 + pack.stickers.size // header + stickers
                }
            }
        }

        if (state.packs.size > 1) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(state.packs) { index, pack ->
                    val iconUrl = pack.avatarUrl ?: pack.stickers.firstOrNull()?.url
                    // Highlight based on which pack section is visible
                    val isSelected = packHeaderIndices.getOrNull(index)?.let { headerIdx ->
                        val nextHeaderIdx = packHeaderIndices.getOrNull(index + 1) ?: Int.MAX_VALUE
                        gridState.firstVisibleItemIndex in headerIdx until nextHeaderIdx
                    } == true
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .then(
                                if (isSelected) {
                                    Modifier.background(ElementTheme.colors.bgSubtlePrimary)
                                } else {
                                    Modifier
                                }
                            )
                            .clickable {
                                coroutineScope.launch {
                                    packHeaderIndices.getOrNull(index)?.let {
                                        gridState.animateScrollToItem(it)
                                    }
                                }
                            }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (iconUrl != null) {
                            AsyncImage(
                                model = MediaRequestData(
                                    MediaSource(iconUrl),
                                    MediaRequestData.Kind.Content,
                                ),
                                contentDescription = pack.name,
                                modifier = Modifier.size(24.dp),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            Text(
                                text = pack.name.take(2),
                                style = ElementTheme.typography.fontBodySmRegular,
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = ElementTheme.colors.borderDisabled)
        }

        // Combined grid with pack headers — adaptive height
        LazyVerticalGrid(
            state = gridState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp, max = gridMaxHeight),
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for ((packIdx, pack) in state.packs.withIndex()) {
                item(
                    key = "header_$packIdx",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    StickerPackHeader(
                        packName = pack.name,
                        avatarUrl = pack.avatarUrl,
                    )
                }
                items(pack.stickers, key = { "sticker_${packIdx}_${it.shortcode}" }) { sticker ->
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clickable { onStickerClick(sticker) }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = MediaRequestData(MediaSource(sticker.url), MediaRequestData.Kind.Content),
                            contentDescription = sticker.body ?: sticker.shortcode,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
        }

        // Load error notice
        if (state.hadLoadErrors && state.packs.isNotEmpty()) {
            Text(
                text = "Some sticker packs failed to load",
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textDisabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

    }
}

@Composable
private fun StickerPackHeader(
    packName: String,
    avatarUrl: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = MediaRequestData(MediaSource(avatarUrl), MediaRequestData.Kind.Content),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                contentScale = ContentScale.Fit,
            )
        }
        Text(
            text = packName,
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textSecondary,
        )
    }
}
