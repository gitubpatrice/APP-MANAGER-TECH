package com.filestech.appmanager.di

import javax.inject.Qualifier

/**
 * Hilt qualifier for the IO [kotlinx.coroutines.CoroutineDispatcher].
 *
 * Inject as:
 * ```kotlin
 * class MyRepo @Inject constructor(
 *     @IoDispatcher private val io: CoroutineDispatcher
 * )
 * ```
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
