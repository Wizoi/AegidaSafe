package com.aegida.app

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs only against the release variant (testReleaseUnitTest runs only *ManifestPolicyTest; finding #134): the debug-only way to seed the listener state and the boot, process and
 * install times is not in the release manifest and its classes are not in the release build.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ReleaseManifestPolicyTest {
    @Test fun theDebugReceiverIsNotInTheReleaseManifest() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val receivers = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_RECEIVERS).receivers.orEmpty()
        assertEquals(emptyList<String>(), receivers.map { it.name }.filter { "Debug" in it })
    }

    @Test fun theDebugSeedsAreNotInTheReleaseBuild() {
        for (name in listOf("com.aegida.app.DebugSeeds", "com.aegida.app.DebugListenerReceiver")) {
            assertThrows(name, ClassNotFoundException::class.java) { Class.forName(name) }
        }
    }
}
