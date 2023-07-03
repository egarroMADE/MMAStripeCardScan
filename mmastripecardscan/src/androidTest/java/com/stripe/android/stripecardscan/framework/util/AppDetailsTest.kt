package com.mademediacorp.mmastripecardscan.framework.util

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppDetailsTest {
    private val testContext = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun appDetails_full() {
        val appDetails = AppDetails.fromContext(testContext)

        assertEquals(
            "com.mademediacorp.mmastripecardscan.test",
            appDetails.appPackageName
        )
        assertEquals("", appDetails.applicationId)
        assertEquals(
            "com.mademediacorp.mmastripecardscan",
            appDetails.libraryPackageName
        )
        assertTrue(
            appDetails.sdkVersion.startsWith("20."),
            "${appDetails.sdkVersion} does not start with \"2.\""
        )
        assertEquals(-1, appDetails.sdkVersionCode)
        assertTrue(appDetails.sdkFlavor.isNotEmpty())
    }
}
