package com.filestech.appmanager.ui.components.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.filestech.appmanager.R
import com.filestech.appmanager.domain.model.CriticalCategory
import com.filestech.appmanager.ui.theme.BrandDanger
import kotlinx.coroutines.delay

/**
 * Confirmation dialog for destructive actions on a **critical** app
 * (authenticator, password manager, banking, etc.).
 *
 * Anti-tap-réflexe design: the confirm button requires a **3-second hold**
 * (not a tap). A LinearProgressIndicator fills under the press. Releasing
 * before 3s cancels. This raises the friction enough that a user who just
 * mis-tapped on a list cannot trigger destruction by accident, while a
 * deliberate user can still proceed.
 *
 * Why not a typed CONFIRM? Tested in v0.2.0 against Patrice's UX target:
 * typing on Android is slow + frustrating, and 3-second hold is the same
 * pattern already used by [com.filestech.appmanager.ui.screens.emergency]
 * for SMS emergency trigger (cross-app cognitive consistency).
 *
 * Category drives the body copy via [warningCopyFor].
 *
 * @param actionLabel localized label of the action being attempted
 *   ("Désinstaller", "Mettre en quarantaine", etc.) — surfaced in the dialog
 *   so the user sees what exactly will happen on confirm.
 */
@Composable
fun CriticalWarningDialog(
    appLabel: String,
    actionLabel: String,
    category: CriticalCategory,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val (titleRes, bodyRes) = warningCopyFor(category)

    AlertDialog(
        onDismissRequest = onCancel,
        icon  = {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = BrandDanger,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                text = stringResource(titleRes, appLabel),
                color = BrandDanger,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text  = {
            Column {
                Text(
                    text  = stringResource(bodyRes, actionLabel),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text  = stringResource(R.string.critical_warning_hold_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            HoldToConfirmButton(
                label   = stringResource(R.string.critical_warning_hold_action, actionLabel),
                holdMs  = HOLD_DURATION_MS,
                onHeld  = onConfirm,
            )
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * Hold-to-confirm button: must be held [holdMs] ms continuously to fire
 * [onHeld]. Releases AND drag-outs reset progress.
 *
 * v0.2.0 audit M-5 fix — was using `detectTapGestures` which DOES NOT detect
 * intra-press movement → a finger sliding around the button still fired
 * onHeld at 3s. The new implementation uses raw `awaitPointerEventScope` +
 * tracks finger movement against [androidx.compose.ui.platform.ViewConfiguration.touchSlop],
 * matching the pattern used by SMS Tech's EmergencyHoldButton (cross-app
 * cognitive consistency for hold-3s confirmation).
 */
@Composable
private fun HoldToConfirmButton(
    label: String,
    holdMs: Long,
    onHeld: () -> Unit,
) {
    var isHolding by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val touchSlop = LocalViewConfiguration.current.touchSlop

    LaunchedEffect(isHolding) {
        if (!isHolding) {
            progress = 0f
            return@LaunchedEffect
        }
        val startMs = System.currentTimeMillis()
        while (isHolding) {
            val elapsed = System.currentTimeMillis() - startMs
            progress = (elapsed.toFloat() / holdMs.toFloat()).coerceIn(0f, 1f)
            if (elapsed >= holdMs) {
                isHolding = false
                onHeld()
                return@LaunchedEffect
            }
            delay(16L) // ~60 fps update
        }
    }

    Surface(
        shape    = MaterialTheme.shapes.medium,
        color    = BrandDanger,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .pointerInput(touchSlop, holdMs) {
                awaitPointerEventScope {
                    while (true) {
                        // Wait for finger DOWN.
                        val down = awaitPointerEvent(PointerEventPass.Main)
                            .changes.firstOrNull { it.pressed } ?: continue
                        val startPos = down.position
                        isHolding = true

                        // Track movement + UP until either:
                        //  - finger lifts (cancel — LaunchedEffect handles onHeld
                        //    only if the 3s timer already fired)
                        //  - finger drags beyond touchSlop (cancel — drag-out)
                        //  - the LaunchedEffect fires onHeld and flips isHolding
                        //    to false from outside.
                        var cancelled = false
                        loop@ while (isHolding) {
                            val ev = awaitPointerEvent(PointerEventPass.Main)
                            val change = ev.changes.firstOrNull() ?: break@loop
                            val moved = (change.position - startPos).getDistance()
                            if (moved > touchSlop) {
                                cancelled = true
                                break@loop
                            }
                            if (change.changedToUpIgnoreConsumed()) {
                                break@loop
                            }
                        }
                        // Either way, end the hold session. The LaunchedEffect
                        // sees isHolding = false next tick and resets progress.
                        isHolding = false
                        @Suppress("UNUSED_VARIABLE") val _c = cancelled
                    }
                }
            },
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Progress fill behind the label.
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(3.dp),
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                text       = label,
                modifier   = Modifier.align(Alignment.Center).padding(horizontal = 16.dp),
                color      = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun warningCopyFor(category: CriticalCategory): Pair<Int, Int> = when (category) {
    CriticalCategory.AUTHENTICATION    -> R.string.critical_title_auth to R.string.critical_body_auth
    CriticalCategory.PASSWORD_MANAGERS -> R.string.critical_title_pwd  to R.string.critical_body_pwd
    CriticalCategory.BANKING_FR        -> R.string.critical_title_bank to R.string.critical_body_bank
    CriticalCategory.HEALTH            -> R.string.critical_title_health to R.string.critical_body_health
    CriticalCategory.MESSAGING_E2E     -> R.string.critical_title_msg  to R.string.critical_body_msg
    CriticalCategory.TRANSPORT_FR      -> R.string.critical_title_transport to R.string.critical_body_transport
    CriticalCategory.USER_PROTECTED    -> R.string.critical_title_user to R.string.critical_body_user
}

private const val HOLD_DURATION_MS: Long = 3_000L
