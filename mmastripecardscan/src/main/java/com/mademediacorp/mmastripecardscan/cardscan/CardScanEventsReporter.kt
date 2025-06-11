package com.mademediacorp.mmastripecardscan.cardscan

import com.mademediacorp.mmastripecardscan.scanui.CancellationReason

internal interface CardScanEventsReporter {
    fun scanStarted()

    fun scanSucceeded()

    fun scanFailed(error: Throwable?)

    fun scanCancelled(reason: CancellationReason)
}
