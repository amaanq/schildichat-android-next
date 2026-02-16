/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.customreaction.picker

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import chat.schildi.imagepacks.ImagePackService
import chat.schildi.lib.preferences.ScPrefs
import chat.schildi.lib.preferences.value
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.emojibasebindings.Emoji
import io.element.android.emojibasebindings.EmojibaseStore
import io.element.android.features.messages.impl.R
import io.element.android.features.messages.impl.timeline.components.customreaction.icon
import io.element.android.features.messages.impl.timeline.components.customreaction.title
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.SearchBarResultState
import io.element.android.libraries.matrix.api.room.BaseRoom
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

class EmojiPickerPresenter(
    private val emojibaseStore: EmojibaseStore,
    private val recentEmojis: ImmutableList<String>,
    private val coroutineDispatchers: CoroutineDispatchers,
    private val imagePackService: ImagePackService? = null,
    private val room: BaseRoom? = null,
) : Presenter<EmojiPickerState> {
    @Composable
    override fun present(): EmojiPickerState {
        val queryState = rememberTextFieldState()
        var isSearchActive by remember { mutableStateOf(false) }
        var emojiResults by remember { mutableStateOf<SearchBarResultState<ImmutableList<Emoji>>>(SearchBarResultState.Initial()) }

        val recentEmojiIcon = CompoundIcons.History()
        val categories = remember {
            val providedCategories = emojibaseStore.categories.map { (category, emojis) ->
                EmojiCategory(
                    titleId = category.title,
                    icon = IconSource.Vector(category.icon),
                    emojis = emojis
                )
            }
            if (recentEmojis.isNotEmpty()) {
                val recentEmojis = recentEmojis.mapNotNull { recentEmoji ->
                    emojibaseStore.allEmojis.find { it.unicode == recentEmoji }
                }.toImmutableList()
                val recentCategory =
                    EmojiCategory(
                        titleId = R.string.emoji_picker_category_recent,
                        icon = IconSource.Vector(recentEmojiIcon),
                        emojis = recentEmojis
                    )
                (listOf(recentCategory) + providedCategories).toImmutableList()
            } else {
                providedCategories.toImmutableList()
            }
        }

        // SC: Load custom emoji packs
        val customEmojisEnabled = ScPrefs.ENABLE_CUSTOM_EMOJIS.value()
        var customEmojiPacks by remember { mutableStateOf<ImmutableList<CustomEmojiCategory>>(persistentListOf()) }
        LaunchedEffect(imagePackService, room, customEmojisEnabled) {
            if (imagePackService != null && customEmojisEnabled) {
                withContext(coroutineDispatchers.computation) {
                    val packs = imagePackService.getAllEmoticons(room)
                    customEmojiPacks = packs.map { (resolved, images) ->
                        CustomEmojiCategory(
                            packName = resolved.pack.displayName ?: "Custom",
                            avatarUrl = resolved.pack.avatarUrl,
                            emojis = images.map { image ->
                                CustomEmoji(
                                    shortcode = image.shortcode,
                                    url = image.url,
                                    body = image.body,
                                )
                            }.toImmutableList(),
                        )
                    }.toImmutableList()
                }
            } else {
                customEmojiPacks = persistentListOf()
            }
        }

        val searchQuery = queryState.text.toString()
        var customEmojiSearchResults by remember { mutableStateOf<ImmutableList<CustomEmoji>>(persistentListOf()) }
        LaunchedEffect(searchQuery) {
            emojiResults = if (searchQuery.isEmpty()) {
                customEmojiSearchResults = persistentListOf()
                SearchBarResultState.Initial()
            } else {
                // Add a small delay to avoid doing too many computations when the user is typing quickly
                delay(100.milliseconds)

                val lowercaseQuery = searchQuery.lowercase()
                val results = withContext(coroutineDispatchers.computation) {
                    emojibaseStore.allEmojis
                        .asSequence()
                        .filter { emoji ->
                            emoji.tags.orEmpty().any { it.contains(lowercaseQuery) } ||
                                emoji.shortcodes.any { it.contains(lowercaseQuery) }
                        }
                        .take(60)
                        .toImmutableList()
                }

                // SC: Also search custom emojis
                customEmojiSearchResults = withContext(coroutineDispatchers.computation) {
                    customEmojiPacks.flatMap { category ->
                        category.emojis.filter { emoji ->
                            emoji.shortcode.lowercase().contains(lowercaseQuery) ||
                                emoji.body?.lowercase()?.contains(lowercaseQuery) == true
                        }
                    }.take(60).toImmutableList()
                }

                SearchBarResultState.Results(results)
            }
        }

        val isInPreview = LocalInspectionMode.current
        fun handleEvent(event: EmojiPickerEvent) {
            when (event) {
                // For some reason, in preview mode the SearchBar emits this event with an `isActive = true` value automatically
                is EmojiPickerEvent.ToggleSearchActive -> if (!isInPreview) {
                    isSearchActive = event.isActive
                }
            }
        }

        return EmojiPickerState(
            categories = categories,
            allEmojis = emojibaseStore.allEmojis,
            searchQuery = queryState,
            isSearchActive = isSearchActive,
            searchResults = emojiResults,
            customEmojiPacks = customEmojiPacks,
            customEmojiSearchResults = customEmojiSearchResults,
            eventSink = ::handleEvent,
        )
    }
}
