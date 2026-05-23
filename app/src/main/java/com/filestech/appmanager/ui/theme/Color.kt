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

// ---------------------------------------------------------------------------
// GitHub-style dark palette (v0.1.1 — mirrors Read Files Tech)
// ---------------------------------------------------------------------------
//
// Inspired by github.com/settings/appearance "Dark" theme. Neutral very-dark
// blue-grey background, slightly elevated surfaces, hairline outlines, off-
// white text. Brand accents (BrandDanger for destructive, BrandBlueDark for
// primary actions) are preserved so the Files Tech identity carries over.
//
// Hex values picked to match GitHub's published Primer design tokens
// (canonical-dark theme) — see https://primer.style/foundations/color.
private val GitHubBackground       = Color(0xFF0D1117)
private val GitHubSurface          = Color(0xFF161B22)
private val GitHubSurfaceContainer = Color(0xFF1C2128)
private val GitHubSurfaceHigh      = Color(0xFF21262D)
private val GitHubOutline          = Color(0xFF30363D)
private val GitHubOutlineFaint     = Color(0xFF21262D)
private val GitHubTextPrimary      = Color(0xFFC9D1D9)
private val GitHubTextSecondary    = Color(0xFF8B949E)
private val GitHubAccentBlue       = Color(0xFF58A6FF) // GitHub primary link / button
private val GitHubAccentRed        = Color(0xFFF85149) // GitHub danger text (used for error tier only)

private val DarkPalette = darkColorScheme(
    primary               = GitHubAccentBlue,
    onPrimary             = GitHubBackground,
    primaryContainer      = Color(0xFF1F6FEB),
    onPrimaryContainer    = Color(0xFFE6F0FF),
    secondary             = GitHubTextSecondary,
    onSecondary           = GitHubBackground,
    secondaryContainer    = GitHubSurfaceHigh,
    onSecondaryContainer  = GitHubTextPrimary,
    tertiary              = Color(0xFFBC8CFF),
    onTertiary            = GitHubBackground,
    tertiaryContainer     = Color(0xFF6E40C9),
    onTertiaryContainer   = Color(0xFFEDE0FF),
    error                 = GitHubAccentRed,
    onError               = GitHubBackground,
    errorContainer        = Color(0xFF8E1519),
    onErrorContainer      = Color(0xFFFFDAD6),
    background            = GitHubBackground,
    onBackground          = GitHubTextPrimary,
    surface               = GitHubBackground,
    onSurface             = GitHubTextPrimary,
    surfaceVariant        = GitHubSurfaceHigh,
    onSurfaceVariant      = GitHubTextSecondary,
    surfaceContainerLowest = GitHubBackground,
    surfaceContainerLow   = GitHubSurface,
    surfaceContainer      = GitHubSurfaceContainer,
    surfaceContainerHigh  = GitHubSurfaceHigh,
    surfaceContainerHighest = Color(0xFF2D333B),
    outline               = GitHubOutline,
    outlineVariant        = GitHubOutlineFaint,
    scrim                 = Color.Black,
    inverseSurface        = SnackbarBg,
    inverseOnSurface      = SnackbarOn,
)

internal fun lightScheme(): ColorScheme = LightPalette
internal fun darkScheme(): ColorScheme = DarkPalette
