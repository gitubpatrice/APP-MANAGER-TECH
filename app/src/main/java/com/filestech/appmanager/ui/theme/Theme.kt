package com.filestech.appmanager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * App Manager Tech Material 3 theme — Files Tech portfolio brand.
 *
 * Colour resolution priority:
 * 1. Dynamic colour (Material You, API 31+) if [dynamicColor] == true. The
 *    OS-provided palette overrides our brand identity by user choice.
 * 2. Static [lightScheme] / [darkScheme] keyed on BrandBlue / BrandBlueDark.
 *
 * Brand identity guarantees ([BrandBlue] / [BrandDanger]) are still enforced
 * at call sites via direct imports — they do NOT depend on the active
 * ColorScheme. A delete dialog's confirm button must use `BrandDanger`
 * explicitly, never `colorScheme.error`, so the destructive intent reads
 * identically under any theme variant including Material You.
 */
@Composable
fun AppManagerTechTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> darkScheme()
        else      -> lightScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AppTypography,
        content     = content,
    )
}
