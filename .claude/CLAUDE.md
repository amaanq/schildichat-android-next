# SchildiChat Android Next

SchildiChat (SC) fork of Element X Android. Matrix messaging client built with Jetpack Compose, Appyx navigation, and the Rust Matrix SDK.

## Build & Test

```bash
# Compile a single module (fast iteration)
./gradlew :features:messages:impl:compileDebugKotlin

# Build installable APK (SC default flavor, arm64)
./gradlew :app:assembleFdroidScDefaultDebug
# Output: app/build/outputs/apk/fdroidScDefault/debug/app-fdroid-sc-default-arm64-v8a-debug.apk

# Install to device
adb -s <SERIAL> install -r app/build/outputs/apk/fdroidScDefault/debug/app-fdroid-sc-default-arm64-v8a-debug.apk

# Run unit tests for a module
./gradlew :features:messages:impl:testDebugUnitTest
```

Build variants: `fdroidScDefault`, `fdroidScBeta`, `fdroidScInternal`. ABI splits produce per-arch APKs automatically.

## Repository Structure

```
app/                          # Main application module
features/                     # ~45 feature modules (api/ + impl/ pattern)
  messages/impl/              # Chat screen, composer, reactions, stickers
  home/impl/                  # Home screen, room list
  roomdetails/impl/           # Room settings
  ...
libraries/                    # ~46 shared library modules
  architecture/               # Presenter<State> base, Metro DI
  designsystem/               # Element Compound design system components
  textcomposer/impl/          # Message composer widget
  matrixui/                   # Matrix-specific UI utilities
  matrix/api/ + impl/         # Matrix SDK wrapper
  ...
schildi/                      # SC-specific modules
  lib/                        # ScPrefs, SC utilities, SC string resources
  theme/                      # SC theme (colors, typography overrides)
  imagepacks/                 # MSC2545 image pack support (custom emoji/stickers)
  components/                 # SC Compose components
  matrixcore/                 # SC matrix SDK extensions
  matrixsdk/                  # SC SDK additions (URL preview, etc.)
```

## Architecture

### Presenter Pattern (MVP + Molecule)

Every feature follows: **Presenter** computes state, **View** renders it, **Events** flow back.

```kotlin
// State: immutable data class with eventSink
@Immutable
data class FooState(
    val items: ImmutableList<Item>,
    val isLoading: Boolean,
    val eventSink: (FooEvent) -> Unit,
)

// Events: sealed interface
sealed interface FooEvent {
    data class SelectItem(val id: String) : FooEvent
    data object Refresh : FooEvent
}

// Presenter: @Composable function that returns State
class FooPresenter @Inject constructor(
    private val repository: FooRepository,
) : Presenter<FooState> {
    @Composable
    override fun present(): FooState {
        var items by remember { mutableStateOf<ImmutableList<Item>>(persistentListOf()) }
        var isLoading by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            items = repository.getItems().toImmutableList()
            isLoading = false
        }

        fun handleEvent(event: FooEvent) { /* ... */ }

        return FooState(
            items = items,
            isLoading = isLoading,
            eventSink = ::handleEvent,
        )
    }
}

// View: pure Composable
@Composable
fun FooView(state: FooState, modifier: Modifier = Modifier) {
    // Render state, call state.eventSink(event) for user actions
}
```

### Dependency Injection: Metro

Uses **Metro** (by Zac Sweers), NOT Hilt/Dagger. Key annotations:

- `@Inject` on constructors
- `@ContributesTo(Scope::class)` + `@BindingContainer` for module binding
- `@Binds` / `@Provides` for dependency provision
- `@AssistedInject` + `@Assisted` + `@AssistedFactory` for parameterized creation
- `@DependencyGraph(Scope::class)` for graph roots

**Scopes**: `AppScope` (singleton), `SessionScope` (per-login), `RoomScope` (per-room).

Some presenters (e.g. `EmojiPickerPresenter`) are NOT DI-managed and are constructed manually in their parent composable. In those cases, dependencies are threaded through state objects from a DI-managed parent presenter.

### Navigation: Appyx

Features expose `FeatureEntryPoint` interfaces. Navigation uses `Node` / `FlowNode` classes with `Callback` plugins for cross-feature communication. Back stack managed by Appyx.

### Collections

Always use `kotlinx.collections.immutable`: `ImmutableList`, `ImmutableSet`, `persistentListOf()`, `toImmutableList()`. These enable Compose to skip recomposition when collections haven't changed.

## SC Fork Conventions

### Where SC code lives

1. **Dedicated SC modules** (`schildi/`): New features that don't exist upstream (image packs, SC preferences, SC theme).
2. **Mixed into upstream files**: SC additions marked with `// SC` comments. Look for these markers when modifying upstream code.

```kotlin
data class MessagesState(
    val roomId: RoomId,                                    // upstream
    val showStickerPicker: Boolean = false,                // SC
    val stickerPickerState: StickerPickerState,            // SC
    val eventSink: (MessagesEvent) -> Unit,
)
```

### SC Preferences

`ScPrefs` in `schildi/lib/` manages all SC-specific settings. Each pref has a key, default value, title/summary string resources, and an `upstreamChoice` flag.

```kotlin
val ENABLE_CUSTOM_EMOJIS = ScBoolPref("ENABLE_CUSTOM_EMOJIS", true, ...)
val ENABLE_STICKER_PICKER = ScBoolPref("ENABLE_STICKER_PICKER", true, ...)
```

Read in Composables: `ScPrefs.ENABLE_CUSTOM_EMOJIS.value()` (returns `Boolean`, is `@Composable`).

String resources for SC prefs go in `schildi/lib/src/main/res/values/strings.xml`.

### SC Theme

SC wraps Element's theme via `ScTheme` / `LocalScExposures`. Use `ElementTheme.colors.*` and `ElementTheme.typography.*` for standard tokens. Check `ScTheme.yes` / `ScTheme.exposures.isScTheme` for SC-specific styling branches.

## Key Modules in Detail

### Image Packs (`schildi/imagepacks/`)

MSC2545 support for custom emoji and stickers.

- **ImagePackRepository**: Reads/writes from Matrix account data (`im.ponies.user_emotes`, `im.ponies.emote_rooms`) and room state (`im.ponies.room_emotes`). Uses `fetchFullRoomState()` because sliding sync doesn't include custom state event types.
- **ImagePackService**: Caching layer with `ensurePacks()` — fetches once per room, serves from `MutableStateFlow` cache. Public methods: `getAllEmoticons()`, `getStickersByPack()`, `getEmoticonsByShortcode()`.
- **ImagePackParser**: kotlinx.serialization DTOs for MSC2545 JSON format.

### Reaction Picker (`features/messages/impl/.../customreaction/picker/`)

- **EmojiPickerPresenter**: Loads standard emoji categories + custom emoji packs. Not DI-managed — receives `ImagePackService` and `BaseRoom` via `CustomReactionState`.
- **EmojiPicker.kt**: Main composable. `SecondaryTabRow` for standard categories + freeform/search tabs. `LazyRow` pack row for custom packs. `HorizontalPager` for page content.
- **ScEmojiPickerExtensions.kt**: SC additions — page layout math, custom emoji grid with pack section headers, freeform reaction page, search page.

**Page layout**: `[0..categories) | customPackPage (if packs exist) | freeform | search]`

The custom pack page shows ALL packs in one `LazyVerticalGrid` with full-width `PackHeader` rows using `GridItemSpan(maxLineSpan)`. Pack row icons scroll to sections via `LazyGridState.animateScrollToItem()`.

### Sticker Picker (`features/messages/impl/.../sticker/`)

- **StickerPickerPresenter**: Loads sticker packs from `ImagePackService`. Sends stickers via `room.sendRaw("m.sticker", json)`.
- **StickerPickerView.kt**: Bottom sheet with combined `LazyVerticalGrid` (pack headers + sticker items). Pack row scrolls to sections.

### Text Composer (`libraries/textcomposer/impl/`)

- **TextComposer.kt**: Main composer widget. SC adds sticker button (`onStickerClick`) in `StandardLayout` between text input and send/mic button.
- **MarkdownTextEditorState.kt**: SC adds `processCustomEmojiInMessage()` for shortcode-to-HTML conversion at send time.
- **ResolvedSuggestion.kt**: SC adds `CustomEmoji` variant for `:shortcode:` autocomplete.

### Suggestions/Autocomplete

- **SuggestionsProcessor.kt**: Processes `@mention` and `:emoji:` suggestions. SC extends with custom emoji lookup via `ImagePackService.getEmoticonsByShortcode()`.
- **SuggestionsPickerView.kt**: Renders suggestion chips. SC adds custom emoji image rendering.

## Testing

### Stack

JUnit 4 + Google Truth assertions + Turbine (Flow testing) + custom fakes (no Mockito).

### Patterns

```kotlin
@Test
fun `present - initial state`() = runTest {
    val presenter = createPresenter()
    moleculeFlow(RecompositionMode.Immediate) {
        presenter.present()
    }.test {
        val state = awaitFirstItem()
        assertThat(state.isLoading).isTrue()
        // ...
    }
}
```

**Key utilities**: `lambdaRecorder` for capturing/asserting function calls, `awaitFirstItem()` / `awaitItem()` for state emissions, `consumeItemsUntilTimeout()` for settling.

**Fake convention**: `FakeXxx` classes with injectable lambdas, e.g. `FakeJoinedRoom(toggleReactionLambda = ...)`. Located in `test/` submodules or alongside test files.

When adding constructor parameters to presenters, update corresponding test factory functions (`createXxxPresenter()`) with sensible defaults.

## Gotchas

- **Coil 3, not Coil 2**: Use `coil3.compose.AsyncImage`, not `coil.compose.AsyncImage`. Coil 2 silently renders nothing. MXC URLs are handled by a custom `CoilMediaFetcher`.
- **Sliding sync gaps**: Custom state events (like `im.ponies.room_emotes`) aren't included in sliding sync. Must use `room.fetchFullRoomState()` to get them.
- **SecondaryTabRow crash**: `selectedTabIndex` must be within `[0, tabCount)`. If the pager has more pages than tabs (e.g. custom pack pages), map page index to tab index. Use a custom `indicator` lambda to hide the indicator when no tab corresponds to the current page.
- **M3 gesture pitfalls**: `ModalNavigationDrawer(gesturesEnabled = drawerState.isOpen)` doesn't work — the `anchoredDraggable` modifier doesn't reliably reattach when toggled. Use `gesturesEnabled = true`.
- **`BaseRoom.displayName` doesn't exist**: Use `room.info().name` instead.
- **FakeScPreferencesStore is an object**: Use without `()` constructor.
- **Rust SDK `send_raw()`**: The FFI's `Room::send_raw()` does direct encryption. For proper retry/error UI, patch to use `send_queue().send_raw()`.
- **Presenter coroutine scope**: `rememberCoroutineScope()` dies when a bottom sheet dismisses. For long-lived presenters, use a class-level `CoroutineScope(SupervisorJob() + Dispatchers.Main)`.

## Version Control

This repository uses **jj (Jujutsu)** for version control. Never use git commands directly.
