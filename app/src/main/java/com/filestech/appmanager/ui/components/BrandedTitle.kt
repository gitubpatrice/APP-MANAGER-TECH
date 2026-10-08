package com.filestech.appmanager.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filestech.appmanager.R

/**
 * Brand title for `TopAppBar { title = { … } }`.
 *
 * Two-line layout:
 *   [damier 28 dp]   App Manager Tech       ← R.string.app_name, SemiBold
 *                    <screen name>          ← passed in via [screenTitle], smaller
 *
 * Used on every screen EXCEPT [com.filestech.appmanager.ui.screens.about.AboutScreen],
 * which keeps a plain title (the About page is itself dedicated to the brand
 * and printing it twice would be redundant).
 *
 * The mark is a [VectorDrawable] (`res/drawable/ic_app_logo.xml`) — rendered
 * directly at any size, zero rasterisation, no memory overhead beyond the path
 * geometry. v0.1.3 audit P-1 fix: it replaced a 500×500 PNG that was being
 * decoded to ~1 MB of RGBA bitmap purely to be drawn at 28 dp.
 */
@Composable
fun BrandedTitle(screenTitle: String) {
    // v0.5.0 — the logo and the app name on one line, as in Notes Tech and Agenda Tech, and the
    // screen's title below both, aligned under the logo. (v0.1.3 stacked the two texts beside it.)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.ic_app_logo),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text       = stringResource(R.string.app_name),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
        }
        // v0.1.3 audit M-3 fix — text kept in its natural casing so TalkBack reads it as words, not
        // letter by letter (`uppercase()` made it spell every title on 16 screens).
        Text(
            text       = screenTitle,
            style      = MaterialTheme.typography.bodyMedium.copy(
                letterSpacing = 0.08.sp,
            ),
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier.padding(top = 6.dp),
        )
    }
}
