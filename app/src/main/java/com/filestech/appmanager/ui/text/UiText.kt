package com.filestech.appmanager.ui.text

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources

/**
 * A text shown to the user, kept as a resource reference until it is displayed.
 *
 * ViewModels have no Context: they emit a [UiText], and the screen resolves it against the resources
 * of the moment, so a snackbar or an error follows the app language like every other string. A plain
 * `String` built in a ViewModel would be frozen in English (or worse, in French) for every user.
 */
sealed interface UiText {

    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Plural(
        @param:PluralsRes val id: Int,
        val count: Int,
        val args: List<Any> = listOf(count),
    ) : UiText
}

/** Shorthand for [UiText.Res]: `uiText(R.string.x, arg1, arg2)`. */
fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

// The platform getters take varargs; the arguments are a handful of values.
@Suppress("SpreadOperator")
fun UiText.resolve(resources: Resources): String = when (this) {
    is UiText.Res -> resources.getString(id, *args.toTypedArray())
    is UiText.Plural -> resources.getQuantityString(id, count, *args.toTypedArray())
}

@Composable
fun UiText.asString(): String = resolve(LocalResources.current)
