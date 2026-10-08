package com.aegida.app

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Play policy line from the brief, made executable. Reads the *merged* manifest, so a library that
 * quietly adds a permission fails the build too. A new permission is a one-way choice: ask Kevin, then add
 * it to [APPROVED_PERMISSIONS] with the decision recorded in decisions.md.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ManifestPolicyTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val info = context.packageManager.getPackageInfo(
        context.packageName,
        PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES,
    )
    private val requested = info.requestedPermissions.orEmpty().toSet()

    @Test fun noBannedPermissionIsRequested() {
        assertEquals(emptySet<String>(), requested intersect BANNED_PERMISSIONS)
    }

    @Test fun noPermissionOutsideTheApprovedListIsRequested() {
        val own = "${context.packageName}." // androidx adds one per app for its dynamic receivers
        val unapproved = requested.filterNot { it in APPROVED_PERMISSIONS || it.startsWith(own) }
        assertEquals("ask Kevin before adding a permission", emptyList<String>(), unapproved)
    }

    @Test fun noServiceIsBoundAsAccessibilityOrVpn() {
        val bound = info.services.orEmpty().mapNotNull { it.permission }.toSet()
        assertEquals(emptySet<String>(), bound intersect BANNED_SERVICE_BINDINGS)
    }

    companion object {
        /** Approved in decisions.md. WorkManager adds the first three; the last two arrive with the foreground service. */
        val APPROVED_PERMISSIONS = setOf(
            "android.permission.INTERNET",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.WAKE_LOCK",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
        )

        val BANNED_PERMISSIONS = setOf(
            "android.permission.READ_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.SEND_SMS",
            "android.permission.RECEIVE_MMS",
            "android.permission.RECEIVE_WAP_PUSH",
            "android.permission.RECORD_AUDIO",
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.USE_FULL_SCREEN_INTENT",
            "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
            "android.permission.BIND_VPN_SERVICE",
        )
        val BANNED_SERVICE_BINDINGS = setOf(
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
            "android.permission.BIND_VPN_SERVICE",
        )
    }
}
