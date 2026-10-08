package com.aegida.platform

import com.aegida.core.oem.Reading
import com.aegida.platform.oem.AndroidOemSource
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowBuild

@RunWith(RobolectricTestRunner::class)
class AndroidOemSourceTest {
    @Test fun onAPixelTheMakerFeaturesAreNotPresent() {
        ShadowBuild.setManufacturer("Google")
        assertEquals(Reading.NotPresent, AndroidOemSource().installBlocker())
        assertEquals(Reading.NotPresent, AndroidOemSource().neverSleeping())
    }

    @Test fun onASamsungTheyAreUnreadableUntilValidatedNeverGuessed() {
        ShadowBuild.setManufacturer("samsung")
        assertEquals(Reading.Unreadable, AndroidOemSource().installBlocker())
        assertEquals(Reading.Unreadable, AndroidOemSource().neverSleeping())
    }
}
