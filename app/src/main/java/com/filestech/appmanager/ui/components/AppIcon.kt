package com.filestech.appmanager.ui.components

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

/**
 * Renders the launcher icon of [packageName] at [size], synchronously loaded
 * via [PackageManager.getApplicationIcon]. Memoised by `packageName`, so a
 * LazyColumn scroll does not re-decode the bitmap on every recomposition.
 *
 * Phase V: synchronous load — acceptable for a few hundred packages with
 * Drawable caches in the framework. Phase VI/VIII can swap for a Coil custom
 * fetcher with a `package://` scheme if profile traces show jank on cold
 * scroll over 500+ apps.
 *
 * If the package is uninstalled between scan and render, falls back to the
 * Material Android icon so the row still renders.
 */
@Composable
fun AppIcon(
    packageName: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        try {
            context.packageManager
                .getApplicationIcon(packageName)
                .toBitmap()
                .asImageBitmap()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap            = bitmap,
            contentDescription = null,
            modifier          = modifier.size(size),
        )
    } else {
        Icon(
            imageVector        = Icons.Outlined.Android,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier           = modifier.size(size),
        )
    }
}
