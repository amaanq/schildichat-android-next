# Napkin

## 2026-02-10: Replace Bottom Space Tab Bar with Left-Side Navigation Drawer

### What was done

- **SpaceDrawer.kt** (new): Created `SpaceNavigationDrawer` wrapping content in `ModalNavigationDrawer`. Drawer contains header, "All chats" item, and recursive `LazyColumn` with `spaceDrawerItems()` supporting nested expand/collapse via `expandedPaths` state map. Selection uses M3 `secondaryContainer` background. Chevron icons indicate expandable spaces.
- **SpacesPager.kt** (gutted): Removed entire `SpacesPager` function (~550 lines), all tab/swipe code (`ScrollableTabRow`, `SwipeIndicator`, `SpaceTab`, `ShowAllTab`, etc.). Kept only reusable composables: `AbstractSpaceIcon`, `PseudoSpaceIcon`, `UnreadCountBox`, `GenericSpaceIcon`, `PersistSpaceOnPause`. Made the first three public.
- **HomeView.kt**: Added `DrawerState`, extracted space data from `RoomListContentState.Rooms`, wrapped `Scaffold` in `SpaceNavigationDrawer`. Removed `spaceBarHeight` plumbing and `addSpaceNavPadding` from FAB. Passes `onOpenSpaceDrawer` to `HomeTopBar`.
- **HomeTopBar.kt**: Added `onOpenSpaceDrawer` parameter. Title `Row` becomes clickable when space nav active, with `ArrowDropDown` chevron hint.
- **RoomListContentView.kt**: Removed `onMeasureSpaceBarHeight` parameter from all three functions. Replaced `SpacesPager` wrapper with direct `Box`+`LazyColumn`. Added inline `UpstreamSpaceListItem` check to render `HomeSpacesView`.
- **ScRoomListViewExtensions.kt**: Removed `addSpaceNavPadding` modifier extension.
- **ScPrefs.kt**: Removed `COMPACT_ROOT_SPACES` and `SPACE_SWIPE` preferences and their references in `scTweaks`/`devQuickTweaksOverview`.
- **strings.xml** (all locales): Removed `sc_compact_root_spaces_title`, `sc_compact_root_spaces_summary`, `sc_space_swipe_title`, `sc_space_swipe_summary`.

### Key decisions

- `ScPrefs.PSEUDO_SPACE_ALL_ROOMS.value()` must be hoisted above `LazyColumn` since `LazyListScope` is not `@Composable`
- `UpstreamSpaceListItem` routing moved from SpacesPager into `RoomsViewList` — checks `spaceSelectionHierarchy.firstOrNull()` and renders `HomeSpacesView` directly
- Auto-expand ancestors of current selection via `LaunchedEffect(spaceSelectionHierarchy)` in drawer
- TabRow.kt kept as-is since removing it could have unforeseen build impacts and it exports module-level APIs

### Mistakes caught

- Initial `homeState != null` check was redundant since `homeState: HomeState` is non-nullable — fixed after compiler warning
- `ImmutableList<out ...>` redundant projection in `spaceDrawerItems` — fixed after compiler warning

## 2026-02-14: Space Drawer Visual Polish + Gesture Fix + Back Navigation

### What was done

- **SpaceDrawer.kt**: `gesturesEnabled = true` (kept full gesture support; `drawerState.isOpen` approach broken — M3's conditional `Modifier.anchoredDraggable` doesn't reliably reattach after state change). Replaced `spaceBarBg ?: surface` with `surfaceContainerLow`. Added `statusBarsPadding()` to drawer sheet. Header upgraded to `titleMedium`/`onSurface` with `HorizontalDivider`. Items use `bodyLarge` text, `AvatarSize.SelectParentSpace` (32dp) icons, 14dp vertical padding. Removed `UnreadCountBox` icon overlay in favor of trailing `SpaceDrawerTrailingBadge` pill (primary/onPrimary for notifications, bgCriticalPrimary for mentions, surfaceVariant for silent unreads). Removed unused `searchActive` parameter.
- **HomeTopBar.kt**: ArrowDropDown icon increased from 20dp to 24dp for better tap target visibility.
- **HomeView.kt**: Removed `searchActive` argument from `SpaceNavigationDrawer` call. Added `BackHandler` that navigates from selected space back to all chats on back press (enabled when `spaceSelectionHierarchy.isNotEmpty() && !drawerState.isOpen`).

### Key decisions

- `gesturesEnabled = drawerState.isOpen` DOES NOT WORK — M3's `ModalNavigationDrawer` conditionally applies `Modifier.anchoredDraggable` based on this flag, and toggling it from false→true after programmatic open doesn't reliably reattach the gesture detector. Reverted to `gesturesEnabled = true`
- Chevron expand button is a separate `Box` with its own `clickable` inside the Row — tapping the chevron toggles expand/collapse, tapping the rest of the item selects the space
- **Switch.kt**: Element's `compoundSwitchColors()` overrides M3 defaults (transparent unchecked track, custom accent track color). For SC theme (`ScTheme.exposures.isScTheme`), use plain `SwitchDefaults.colors()` to match system toggle appearance
- Back handler placed before `SpaceNavigationDrawer` in composition so drawer's internal back handler (close drawer) takes priority when drawer is open
- Trailing badge uses `primary`/`onPrimary` for normal notifications, `bgCriticalPrimary`/`colorOnAccent` for mentions — matches existing unread color semantics but in pill form
- `AvatarSize.SelectParentSpace` (32dp) chosen as closest enum value to the desired 28dp icon size

## 2026-02-10: Space Navigation Bar Redesign to Material You

### What was done

- **TabRow.kt**: Added `tabPillIndicatorOffset` modifier (centers vertically via `Alignment.CenterStart` vs `BottomStart`). Reordered `ScrollableTabRowImp` placement: indicator → tabs → divider (indicator draws behind tabs).
- **SpacesPager.kt**: Replaced thin 3dp bottom-line indicator with 64x32dp centered pill using `secondaryContainer`/`primaryContainer` colors. Replaced `Tab()` composable with custom `Box`/`Column` layouts matching M3 NavigationBar style. Updated icon colors to `onSecondaryContainer`/`onSurfaceVariant`, text colors to `onSurface`/`onSurfaceVariant`, typography from `titleSmall` to `labelMedium`. Removed divider via `divider = {}`. Removed unused `Tab` import.

### Key decisions

- The pill indicator is placed behind tabs (z-order change in `ScrollableTabRowImp`) so tab content renders on top of the pill
- `tabPillIndicatorOffset` needed to be a separate modifier because `tabIndicatorOffset` uses `BottomStart` alignment which would place the pill at the bottom
- Default icon colors for `AbstractSpaceIcon` and `PseudoSpaceIcon` changed from `primary` to `onSurfaceVariant` since callers now pass explicit colors based on selection state
- The swipe indicator still uses `inverseOnSurface`/`inverseSurface` which is intentionally different from the tab icons

## 2026-02-16: Five SchildiChat Features (#99, #83, #35, #48, #94)

### What was done

**Feature 1: Hide Voice Message Button (#99)**

- **ScPrefs.kt**: Added `HIDE_VOICE_MESSAGE_BUTTON` bool pref (default false) in timeline section
- **strings.xml**: Added `sc_pref_hide_voice_message_title`/`summary`
- **TextComposer.kt**: `rememberEndButtonParams()` returns `EndButtonParams?` (nullable). Reads pref outside `remember()` block and passes as key. `VoiceMessageState.Idle` branch returns `null` when pref enabled. `StandardLayout` wraps end `IconButton` in `if (endButtonParams != null)` check.

**Feature 2: Swipe-to-Reply Toggle + Direction (#83)**

- **ScPrefs.kt**: Added `SwipeToReplyMode` object with LEFT/RIGHT/OFF constants, `SWIPE_TO_REPLY` as `ScStringListPref` (default "left")
- **arrays.xml**: Added `sc_swipe_to_reply_names` string array
- **strings.xml**: Added pref title/summary/option strings
- **TimelineItemEventRow.kt**: Reads pref, gates `canReply` on mode != OFF. Clamps offset direction: `min(rawOffset, 0f)` for left, `max(rawOffset, 0f)` for right. Places `ReplySwipeIndicator` before content for right-swipe, after content for left-swipe. Passes `reverseDirection = true` for left-swipe.
- **ReplySwipeIndicator.kt**: Added `reverseDirection: Boolean = false` param. Flips `translationX` sign and adds `rotationY = 180f` when reversed.

**Feature 3: Hide Membership Events (#35)**

- **ScPrefs.kt**: Added `HIDE_MEMBERSHIP_EVENTS` bool pref (default false) in timeline section
- **strings.xml**: Added `sc_pref_hide_membership_events_title`/`summary`
- **TimelineView.kt**: Reads pref, derives filtered `timelineItems` via `remember()`. Filters out `TimelineItemRoomMembershipContent` and `TimelineItemProfileChangeContent` from both individual events and `GroupedEvents`. Empty groups removed, partially-filtered groups rebuilt with `copy(events = filtered.toImmutableList())`.

**Feature 4: Mark All Rooms as Read (#48)**

- **strings.xml**: Added `sc_mark_all_as_read`
- **RoomListEvents.kt**: Added `data object MarkAllAsRead : RoomListEvents`
- **RoomListPresenter.kt**: Added `markAllAsRead()` function using `client.getJoinedRoomIds()`, parallelized per-room via nested `launch`. Clears notifications, unsets unread flag, sends read receipt.
- **RoomListTopBarScExtensions.kt**: Added `onMarkAllAsRead` param to `ScRoomListDropdownEntriesTop`, new `DropdownMenuItem` with `CompoundIcons.MarkAsRead()` icon
- **HomeTopBar.kt**: Threaded `onMarkAllAsRead` param through all composable layers and previews
- **HomeView.kt**: Connected callback to `state.eventSink(RoomListEvents.MarkAllAsRead)`

**Feature 5: MSC2010 Spoiler Text (#94)**

- Verified: spoiler support already works end-to-end via messageformat library. `MatrixHtmlParser` detects `data-mx-spoiler`, `MatrixBodyStyledFormatter` adds `CLICK_TO_REVEAL` annotation, `MatrixBodyStyle.drawAboveSpan` renders overlay. `ScTimelineItemContentMessageFactoryExtensions.kt`'s `matrixBodyDrawStyle()` uses `defaultForegroundColor = onSurfaceVariantColor` which is theme-appropriate. No changes needed.

### Key decisions

- `ScPrefs.value()` is `@Composable` — must be called outside `remember()` blocks and passed as keys
- Swipe-to-reply direction clamping: `min(rawOffset, 0f)` for left (negative offset = swipe left), `max(rawOffset, 0f)` for right
- `ReplySwipeIndicator` placement switches between before/after content in the Row based on swipe direction
- Membership event filtering happens in `TimelineView` (composable context for pref read) rather than `TimelineItemGrouper` (non-composable)
- `client.getJoinedRoomIds()` confirmed to exist on `MatrixClient` interface for mark-all-as-read
- `CompoundIcons.MarkAsRead()` confirmed to exist in the design system for the dropdown icon

## 2026-02-16: Four SchildiChat Features (#63, #91, #29, #30)

### What was done

**Feature 1: Animated GIF/WEBP in Fullscreen (#63)**

- **LocalMediaView.kt**: Passed `mimeType` through to `MediaImageView` in the `isMimeTypeImage()` branch
- **MediaImageView.kt**: Added `mimeType: String? = null` param. For animated images (`mimeType.isMimeTypeAnimatedImage()`), uses `coil3.compose.AsyncImage` + `Modifier.zoomable()` instead of `ZoomableAsyncImage`. Sets `isReady` directly since no `zoomableImageState` observer exists for this path. Non-animated path unchanged.

**Feature 2: Tap Avatar to Mention (#91)**

- **MessageComposerEvent.kt**: Added `InsertMention(userId: UserId)` event
- **MarkdownTextEditorState.kt**: Added `insertMentionAtCursor()` method — inserts `"@ "` at cursor, applies `MentionSpan`, moves cursor past trailing space
- **MessageComposerPresenter.kt**: Added handler for `InsertMention` — markdown mode calls `insertMentionAtCursor`, rich text mode uses `permalinkBuilder` + `insertMentionAtSuggestion`
- **MessagesPresenter.kt**: `OnUserClicked` now checks `composerHasFocus` — inserts mention when focused, shows moderation sheet otherwise
- **MessagesView.kt**: `onUserDataClick` skips `hidingKeyboard` wrapper when composer has focus to keep keyboard visible

**Feature 3: Original Resolution Image Sending (#29)**

- **ScPrefs.kt**: Added `SEND_IMAGES_ORIGINAL_QUALITY` bool pref (default false) in conversation section
- **strings.xml**: Added title/summary strings
- **mediaupload/impl/build.gradle.kts**: Added `implementation(projects.schildi.lib)` dependency
- **DefaultMediaOptimizationConfigProvider.kt**: Injected `ScPreferencesStore`, overrides `compressImages = upstream && !scOriginalQuality`

**Feature 4: Show Hidden Events (#30)**

- **TimelineView.kt**: Extended existing membership filtering block to also filter `TimelineItemStateEventContent`. Reads `VIEW_HIDDEN_EVENTS` pref. When false (default), hides state events. Combined both filters into one `remember` block with fast path when no filtering needed.

### Key decisions

- Animated image detection uses `isMimeTypeAnimatedImage()` which treats ALL `image/webp` as animated — acceptable tradeoff (static WebP loses sub-sampling but renders fine)
- `insertMentionAtCursor` follows same span pattern as `insertSuggestion` for `ResolvedSuggestion.Member`
- Original quality pref overrides upstream `doesOptimizeImages()` with `&& !scPref` logic
- State event filtering reuses the existing `remember` block for membership events, just adds another condition

### Mistakes caught

- Feature 4 agent saw transient compile error from Feature 2's `InsertMention` event being added to `MessageComposerEvent.kt` before the handler was added to `MessageComposerPresenter.kt` (agents ran concurrently) — resolved once both agents finished
