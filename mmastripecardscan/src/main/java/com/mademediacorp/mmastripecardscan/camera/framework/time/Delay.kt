package com.mademediacorp.mmastripecardscan.camera.framework.time

import kotlin.time.Duration

/**
 * Allow delaying for a specified duration
 */
internal suspend fun delay(duration: Duration) =
    kotlinx.coroutines.delay(duration.inWholeMilliseconds)
