package com.filestech.appmanager.ui.components.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Header for a settings / detail section. Material 3 small-bold title in
 * brand-blue colour, 16dp horizontal padding, 12dp top, 8dp bottom — matches
 * the SMS Tech SettingsScreen rhythm.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text     = title,
        style    = MaterialTheme.typography.titleSmall,
        color    = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(
            start  = 16.dp,
            end    = 16.dp,
            top    = 16.dp,
            bottom = 8.dp,
        ),
    )
}
