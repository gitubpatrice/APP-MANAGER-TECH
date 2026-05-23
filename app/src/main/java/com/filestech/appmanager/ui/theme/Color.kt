package com.filestech.appmanager.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colour palette for App Manager Tech — mirrored on the Files Tech portfolio
 * brand identity (see SMS Tech `ui/theme/Color.kt`).
 *
 * Brand identity (single source of truth — never inline these hex values in
 * screen files; import the constant):
 *
 * - [BrandBlue]      — Light-scheme primary, snackbar default, confirmation CTAs.
 * - [BrandBlueDark]  — Dark-scheme primary (lighter so it reads on dark surfaces).
 * - [BrandDanger]    — Destructive intent ONLY: delete confirmations, warnings,
 *                      uninstall flow, force-stop confirmations. Never use for
 *                      positive confirmations (those go on BrandBlue).
 *
 * WCAG AA verification: White on BrandBlue (#2460AB) = 5.8:1 (PASS),
 * White on BrandDanger (#C62828) = 5.6:1 (PASS) for normal text.
 */

internal val BrandBlue     = Color(0xFF2460AB)
internal val BrandBlueDark = Color(0xFFA9C7FF)
internal val BrandDanger   = Color(0xFFC62828)

/**
 * Slate-blue palette for Snackbar / inverse-surface widgets. Both light and
 * dark schemes share the same pair so a confirmation toast always reads
 * against the same brand identity, regardless of the user's theme.
 */
internal val SnackbarBg = BrandBlue
internal val SnackbarOn = Color.White

private val LightPalette = lightColorScheme(
    primary               = BrandBlue,
    onPrimary             = Color.White,
    primaryContainer      = Color(0xFFD7E3FF),
    onPrimaryContainer    = Color(0xFF001A40),
    secondary             = Color(0xFF555F71),
    onSecondary           = Color.White,
    secondaryContainer    = Color(0xFFD9E3F8),
    onSecondaryContainer  = Color(0xFF121C2B),
    tertiary              = Color(0xFF705574),
    onTertiary            = Color.White,
    tertiaryContainer     = Color(0xFFFAD8FC),
    onTertiaryContainer   = Color(0xFF28132E),
    error                 = Color(0xFFBA1A1A),
    onError               = Color.White,
    errorContainer        = Color(0xFFFFDAD6),
    onErrorContainer      = Color(0xFF410002),
    background            = Color(0xFFFDFCFF),
    onBackground          = Color(0xFF1B1B1F),
    surface               = Color(0xFFFDFCFF),
    onSurface             = Color(0xFF1B1B1F),
    surfaceVariant        = Color(0xFFE0E2EC),
    onSurfaceVariant      = Color(0xFF44464F),
    outline               = Color(0xFF74777F),
    outlineVariant        = Color(0xFFC4C6D0),
    scrim                 = Color.Black,
    inverseSurface        = SnackbarBg,
    inverseOnSurface      = SnackbarOn,
)

private val DarkPalette = darkColorScheme(
    primary               = BrandBlueDark,
    onPrimary             = Color(0xFF00315E),
    primaryContainer      = Color(0xFF004788),
    onPrimaryContainer    = Color(0xFFD7E3FF),
    secondary             = Color(0xFFBDC7DC),
    onSecondary           = Color(0xFF273141),
    secondaryContainer    = Color(0xFF3D4758),
    onSecondaryContainer  = Color(0xFFD9E3F8),
    tertiary              = Color(0xFFDEBCDF),
    onTertiary            = Color(0xFF3F2844),
    tertiaryContainer     = Color(0xFF573E5C),
    onTertiaryContainer   = Color(0xFFFAD8FC),
    error                 = Color(0xFFFFB4AB),
    onError               = Color(0xFF690005),
    errorContainer        = Color(0xFF93000A),
    onErrorContainer      = Color(0xFFFFDAD6),
    background            = Color(0xFF1B1B1F),
    onBackground          = Color(0xFFE3E2E6),
    surface               = Color(0xFF1B1B1F),
    onSurface             = Color(0xFFE3E2E6),
    surfaceVariant        = Color(0xFF44464F),
    onSurfaceVariant      = Color(0xFFC4C6D0),
    outline               = Color(0xFF8E9099),
    outlineVariant        = Color(0xFF44464F),
    scrim                 = Color.Black,
    inverseSurface        = SnackbarBg,
    inverseOnSurface      = SnackbarOn,
)

internal fun lightScheme(): ColorScheme = LightPalette
internal fun darkScheme(): ColorScheme = DarkPalette
