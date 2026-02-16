package chat.schildi.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.element.android.compound.annotations.CoreColorToken
import io.element.android.compound.tokens.generated.SemanticColors

/**
 * Composite alpha blending onto a given background, producing an opaque result.
 */
fun Color.fakeAlphaOn(background: Color, alpha: Float) = Color(
    background.red + alpha * (red - background.red),
    background.green + alpha * (green - background.green),
    background.blue + alpha * (blue - background.blue),
    1f,
)

/**
 * Intermediate base variables derived from a dynamic M3 [ColorScheme],
 * mirroring the SC pattern (fgPrimary, bg, accent, etc.).
 */
private class DynamicBaseColors(colorScheme: ColorScheme, isLight: Boolean) {
    val fgPrimary: Color = colorScheme.onSurface
    val fgSecondary: Color = colorScheme.onSurfaceVariant
    val fgTertiary: Color = colorScheme.outline
    val fgHint: Color = colorScheme.outline
    val fgDisabled: Color = colorScheme.outline
    val bg: Color = colorScheme.surface
    val bgFloating: Color = colorScheme.surfaceContainerHigh
    val bgDarker: Color = if (isLight) colorScheme.surfaceContainerLow else colorScheme.surfaceDim
    val bgDeep: Color = colorScheme.surfaceContainerLowest
    val divider: Color = colorScheme.outlineVariant
    val accent: Color = colorScheme.primary
    val onAccent: Color = colorScheme.onPrimary
    val inverseFg: Color = colorScheme.inverseOnSurface
    val iconAlpha: Float = 0.5f
}

/**
 * Build a full [SemanticColors] from a dynamic M3 [ColorScheme].
 */
@OptIn(CoreColorToken::class)
fun dynamicSemanticColors(colorScheme: ColorScheme, isLight: Boolean): SemanticColors {
    val b = DynamicBaseColors(colorScheme, isLight)
    return SemanticColors(
        // Text
        textPrimary = b.fgPrimary,
        textSecondary = b.fgSecondary,
        textDisabled = b.fgDisabled,
        textActionPrimary = b.fgPrimary,
        textActionAccent = b.accent,
        textLinkExternal = colorScheme.primary,
        textCriticalPrimary = colorScheme.error,
        textSuccessPrimary = colorScheme.tertiary,
        textInfoPrimary = colorScheme.secondary,
        textOnSolidPrimary = b.inverseFg,
        textBadgeInfo = colorScheme.onTertiary,
        textBadgeAccent = colorScheme.onPrimary,

        // Backgrounds – canvas / subtle
        bgSubtlePrimary = b.bgDarker,
        bgSubtleSecondary = if (isLight) b.bgDarker else b.bgFloating,
        bgSubtleSecondaryLevel0 = b.bg,
        bgCanvasDefault = b.bg,
        bgCanvasDefaultLevel1 = b.bgFloating,
        bgCanvasDisabled = b.bgDarker,

        // Backgrounds – actions
        bgActionPrimaryRest = b.fgPrimary,
        bgActionPrimaryHovered = b.fgSecondary,
        bgActionPrimaryPressed = b.fgSecondary,
        bgActionPrimaryDisabled = b.fgHint,
        bgActionSecondaryRest = b.bg,
        bgActionSecondaryHovered = b.bgFloating,
        bgActionSecondaryPressed = b.bgFloating,
        bgActionTertiaryRest = b.bg,
        bgActionTertiaryHovered = b.bgFloating,
        bgActionTertiarySelected = b.bgFloating,

        // Backgrounds – accent: in dark M3 primary is a light pastel — use inversePrimary
        // (the darker saturated variant) so white icon/text remains readable on it.
        bgAccentRest = if (isLight) b.accent else colorScheme.inversePrimary,
        bgAccentSelected = if (isLight) b.accent else colorScheme.inversePrimary,
        bgAccentHovered = if (isLight) b.accent else colorScheme.inversePrimary,
        bgAccentPressed = if (isLight) b.accent else colorScheme.inversePrimary,

        // Backgrounds – critical (error)
        bgCriticalPrimary = colorScheme.error,
        bgCriticalHovered = colorScheme.error.copy(alpha = 0.85f),
        bgCriticalSubtle = colorScheme.errorContainer,
        bgCriticalSubtleHovered = colorScheme.errorContainer.copy(alpha = 0.85f),

        // Backgrounds – success / info
        bgSuccessSubtle = colorScheme.tertiaryContainer,
        bgInfoSubtle = colorScheme.secondaryContainer,

        // Backgrounds – badges: use bold accent colors, not containers (too dark in dark mode)
        bgBadgeAccent = colorScheme.primary,
        bgBadgeDefault = colorScheme.tertiary,
        bgBadgeInfo = colorScheme.tertiary,

        // Borders
        borderAccentSubtle = b.accent,
        borderDisabled = b.divider,
        borderFocused = colorScheme.primary,
        borderInteractivePrimary = b.fgSecondary,
        borderInteractiveSecondary = b.fgTertiary,
        borderInteractiveHovered = b.fgPrimary,
        borderCriticalPrimary = colorScheme.error,
        borderCriticalHovered = colorScheme.error.copy(alpha = 0.85f),
        borderCriticalSubtle = colorScheme.error.copy(alpha = 0.5f),
        borderSuccessSubtle = colorScheme.tertiary,
        borderInfoSubtle = colorScheme.secondary,

        // Icons
        iconPrimary = b.fgPrimary,
        iconSecondary = b.fgSecondary,
        iconTertiary = b.fgSecondary,
        iconQuaternary = b.fgTertiary,
        iconDisabled = b.fgDisabled,
        iconPrimaryAlpha = b.fgPrimary.copy(alpha = b.iconAlpha),
        iconSecondaryAlpha = b.fgSecondary.copy(alpha = b.iconAlpha),
        iconTertiaryAlpha = b.fgTertiary.copy(alpha = b.iconAlpha),
        iconQuaternaryAlpha = b.fgTertiary.copy(alpha = b.iconAlpha),
        iconAccentPrimary = b.accent,
        iconAccentTertiary = colorScheme.tertiary,
        iconCriticalPrimary = colorScheme.error,
        iconSuccessPrimary = colorScheme.tertiary,
        iconInfoPrimary = colorScheme.secondary,
        iconOnSolidPrimary = b.inverseFg,

        // Gradients – action: use the full primary→tertiary spectrum
        gradientActionStop1 = colorScheme.primary.copy(alpha = 0.9f),
        gradientActionStop2 = colorScheme.secondary.copy(alpha = 0.7f),
        gradientActionStop3 = colorScheme.tertiary.copy(alpha = 0.5f),
        gradientActionStop4 = colorScheme.tertiary.copy(alpha = 0.3f),

        // Gradients – info: secondary range
        gradientInfoStop1 = colorScheme.secondary.copy(alpha = 0.4f),
        gradientInfoStop2 = colorScheme.secondary.copy(alpha = 0.3f),
        gradientInfoStop3 = colorScheme.secondary.copy(alpha = 0.2f),
        gradientInfoStop4 = colorScheme.secondary.copy(alpha = 0.1f),
        gradientInfoStop5 = colorScheme.secondary.copy(alpha = 0.05f),
        gradientInfoStop6 = Color.Transparent,

        // Gradients – subtle: primary→tertiary spread
        gradientSubtleStop1 = colorScheme.primary.copy(alpha = 0.4f),
        gradientSubtleStop2 = colorScheme.secondary.copy(alpha = 0.3f),
        gradientSubtleStop3 = colorScheme.tertiary.copy(alpha = 0.2f),
        gradientSubtleStop4 = colorScheme.tertiary.copy(alpha = 0.1f),
        gradientSubtleStop5 = colorScheme.tertiary.copy(alpha = 0.05f),
        gradientSubtleStop6 = Color.Transparent,

        // Decorative (avatars) – cycle through all three container tones
        bgDecorative1 = colorScheme.primaryContainer,
        bgDecorative2 = colorScheme.secondaryContainer,
        bgDecorative3 = colorScheme.tertiaryContainer,
        bgDecorative4 = colorScheme.primaryContainer.copy(alpha = 0.7f),
        bgDecorative5 = colorScheme.secondaryContainer.copy(alpha = 0.7f),
        bgDecorative6 = colorScheme.tertiaryContainer.copy(alpha = 0.7f),
        textDecorative1 = colorScheme.onPrimaryContainer,
        textDecorative2 = colorScheme.onSecondaryContainer,
        textDecorative3 = colorScheme.onTertiaryContainer,
        textDecorative4 = colorScheme.onPrimaryContainer,
        textDecorative5 = colorScheme.onSecondaryContainer,
        textDecorative6 = colorScheme.onTertiaryContainer,

        isLight = isLight,
    )
}

/**
 * Build [ScThemeExposures] from a dynamic M3 [ColorScheme].
 */
fun dynamicScThemeExposures(colorScheme: ColorScheme, isLight: Boolean): ScThemeExposures {
    val b = DynamicBaseColors(colorScheme, isLight)
    return ScThemeExposures(
        isScTheme = true,
        horizontalDividerThickness = 0.dp,
        colorOnAccent = b.onAccent,
        bubbleBgIncoming = if (isLight) {
            colorScheme.surfaceContainerHighest
        } else {
            colorScheme.surfaceContainerHighest
        },
        bubbleBgOutgoing = if (isLight) {
            b.accent.fakeAlphaOn(b.bg, 0.12f)
        } else {
            b.accent.fakeAlphaOn(b.bg, 0.15f)
        },
        // Use primary for unread badges — the main accent, no yellowish tertiary shift
        unreadBadgeColor = colorScheme.primary,
        unreadBadgeOnToolbarColor = colorScheme.primary,
        appBarBg = b.bg,
        bubbleRadius = 10.dp,
        commonLayoutRadius = 10.dp,
        timestampRadius = 6.dp,
        timestampOverlayBg = if (isLight) {
            colorScheme.surface.copy(alpha = 0.7f)
        } else {
            colorScheme.scrim.copy(alpha = 0.5f)
        },
        unreadIndicatorLine = b.accent,
        unreadIndicatorThickness = 2.dp,
        mentionFgLegacy = b.onAccent,
        mentionBgLegacy = colorScheme.error,
        mentionBgOtherLegacy = if (isLight) b.bgFloating else colorScheme.surfaceContainerHighest,
        // Other-mention pills: visible accent tint pill
        mentionFg = b.fgPrimary,
        mentionBg = b.accent.fakeAlphaOn(b.bg, 0.25f),
        // Personal/"you are mentioned" pills: stronger accent, not error-red
        mentionFgHighlight = b.fgPrimary,
        mentionBgHighlight = b.accent.fakeAlphaOn(b.bg, 0.45f),
        greenFg = b.accent,
        greenBg = b.accent.fakeAlphaOn(b.bg, 0.15f),
        messageHighlightBg = b.accent.fakeAlphaOn(b.bg, 0.3f),
        composerBlockBg = if (isLight) null else colorScheme.surfaceContainerHighest,
        composerBlockFg = if (isLight) null else b.fgPrimary,
        spaceBarBg = if (isLight) colorScheme.surfaceContainerHighest else colorScheme.surfaceContainerLow,
    )
}
