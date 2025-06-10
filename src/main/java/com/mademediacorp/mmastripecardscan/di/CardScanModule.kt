package com.mademediacorp.mmastripecardscan.di

import android.app.Application
import com.mademediacorp.mmastripecardscan.cardscan.DefaultDurationProvider
import com.mademediacorp.mmastripecardscan.cardscan.DurationProvider
import com.mademediacorp.mmastripecardscan.cardscan.CardScanEventsReporter
import com.mademediacorp.mmastripecardscan.cardscan.DefaultCardScanEventsReporter
import dagger.Binds
import dagger.Module
import dagger.Provides
import javax.inject.Named
import javax.inject.Singleton

const val MM_ENABLE_LOGGING = "mm_enable_logging"
const val MM_PUBLISHABLE_KEY = "mm_publishable_key"

@Module
internal interface CardScanModule {

    @Binds
    @Singleton
    fun bindCardScanEventReporter(cardScanEventsReporter: DefaultCardScanEventsReporter): CardScanEventsReporter

    companion object {
        @Provides
        fun provideDurationProvider(): DurationProvider = DefaultDurationProvider.instance

        @Provides
        @Named(MM_ENABLE_LOGGING)
        fun providesEnableLogging(): Boolean = true

        @Provides
        @Named(MM_PUBLISHABLE_KEY)
        fun providePublishableKey(application: Application): () -> String =
            { "pk_test_..." } // Or load from config

        @Provides
        @Singleton
        fun provideLogger(): (String) -> Unit = { message ->
            android.util.Log.d("MMStripeLogger", message)
        }
    }
}
