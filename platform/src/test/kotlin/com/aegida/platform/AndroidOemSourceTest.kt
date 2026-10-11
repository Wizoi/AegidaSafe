package com.aegida.platform

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import com.aegida.core.oem.Reading
import com.aegida.platform.oem.AndroidOemSource
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowBuild

@RunWith(RobolectricTestRunner::class)
class AndroidOemSourceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val oem get() = AndroidOemSource(context)

    private fun installAutoBlocker(enabled: Boolean) {
        val info = PackageInfo().apply {
            packageName = AUTO_BLOCKER
            applicationInfo = ApplicationInfo().apply { packageName = AUTO_BLOCKER; this.enabled = enabled }
        }
        shadowOf(context.packageManager).installPackage(info)
    }

    @Test fun onAPixelTheMakerFeaturesAreNotPresent() {
        ShadowBuild.setManufacturer("Google")
        assertEquals(Reading.NotPresent, oem.installBlocker())
        assertEquals(Reading.NotPresent, oem.neverSleeping())
    }

    @Test fun onASamsungWithAutoBlockerItIsUnreadableNeverGuessed() {
        ShadowBuild.setManufacturer("samsung")
        installAutoBlocker(enabled = true)
        assertEquals(Reading.Unreadable, oem.installBlocker())
        assertEquals(Reading.Unreadable, oem.neverSleeping())
    }

    /** Kevin, Oct 11: a Galaxy S20+ (One UI 5.1) has no Auto Blocker, so the step must not ask about it (findings #91). */
    @Test fun onASamsungThatHasNoAutoBlockerThereIsNothingToAskAbout() {
        ShadowBuild.setManufacturer("samsung")
        assertEquals(Reading.NotPresent, oem.installBlocker())
        assertEquals("the battery feature is on every Samsung", Reading.Unreadable, oem.neverSleeping())
    }

    @Test fun anAutoBlockerThatIsSwitchedOffAtTheSystemLevelIsNotAskedAboutEither() { // findings #11: the lab phone had it disabled
        ShadowBuild.setManufacturer("samsung")
        installAutoBlocker(enabled = false)
        assertEquals(Reading.NotPresent, oem.installBlocker())
    }

    private companion object {
        const val AUTO_BLOCKER = "com.samsung.android.rampart"
    }
}
