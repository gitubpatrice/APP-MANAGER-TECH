package com.filestech.appmanager.core.ext

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * One-shot event channel helper.
 *
 * Usage in a ViewModel:
 * ```kotlin
 * private val _events = oneShotEvents<MyEvent>()
 * val events: Flow<MyEvent> = _events.asFlow()
 *
 * // emit:
 * _events.trySend(MyEvent.NavigateBack)
 * ```
 *
 * Capacity = [Channel.BUFFERED] (64) so events emitted before the collector is
 * ready are not lost. Overflow strategy = [BufferOverflow.DROP_OLDEST] to keep
 * sends non-suspending in the rare case the UI is too slow to catch up — never
 * back-pressure the ViewModel.
 *
 * Design note: [kotlinx.coroutines.flow.SharedFlow] is NOT used here because
 * replaying events after configuration changes causes duplicate navigation /
 * dialogs.
 */
fun <T> oneShotEvents(): Channel<T> =
    Channel(capacity = Channel.BUFFERED, onBufferOverflow = BufferOverflow.DROP_OLDEST)

/** Returns the [Channel] as a [Flow] for consumption in the UI layer. */
fun <T> Channel<T>.asFlow(): Flow<T> = receiveAsFlow()
