package com.mademediacorp.mmastripecardscan.di

import android.app.Application
import com.stripe.android.core.injection.CoroutineContextModule
import com.mademediacorp.mmastripecardscan.cardscan.CardScanActivity
import com.mademediacorp.mmastripecardscan.cardscan.CardScanConfiguration
import com.mademediacorp.mmastripecardscan.cardscan.CardScanEventsReporter
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        CardScanModule::class,
        com.stripe.android.core.injection.CoroutineContextModule::class, 
    ]
)

internal interface CardScanComponent {
    val cardScanEventsReporter: CardScanEventsReporter

    fun inject(activity: CardScanActivity)

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun application(application: Application): Builder

        @BindsInstance
        fun configuration(cardScanConfiguration: CardScanConfiguration): Builder

        fun build(): CardScanComponent
    }
}

