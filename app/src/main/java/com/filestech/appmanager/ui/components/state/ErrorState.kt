package com.filestech.appmanager.ui.components.state

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.filestech.appmanager.R
import com.filestech.appmanager.ui.theme.BrandDanger

/**
 * Centred error-state placeholder: warning icon + error message + optional
 * retry button. Used by Outcome.Failure renderings across screens.
 *
 * Why BrandDanger and not `colorScheme.error`: the destructive-intent red
 * brand is consistent across themes; an error here means "something went
 * wrong, the user should care", which is congruent with our destructive
 * intent semantics.
 */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier            = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector        = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint               = BrandDanger,
            modifier           = Modifier.size(64.dp),
        )
        Text(
            text      = message,
            style     = MaterialTheme.typography.bodyLarge,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier  = Modifier.padding(top = 16.dp),
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Button(
                onClick  = onRetry,
                modifier = Modifier.padding(top = 24.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}
