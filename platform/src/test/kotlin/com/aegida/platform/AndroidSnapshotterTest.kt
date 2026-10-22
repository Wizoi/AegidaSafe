package com.aegida.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.aegida.core.model.PrivateDns
import com.aegida.core.oem.OemSource
import com.aegida.core.oem.Reading
import com.aegida.platform.notify.NotificationPoster
import com.aegida.platform.snapshot.AndroidSnapshotter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class AndroidSnapshotterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val listener = ComponentName(context.packageName, "com.aegida.app.AegidaListenerService")
    private val now = Instant.now().minus(Duration.ofSeconds(30))

    private class FixedOem(val blocker: Reading<Boolean>, val sleeping: Reading<Boolean>, val stores: Set<String> = emptySet()) : OemSource {
        override fun installBlocker() = blocker
        override fun neverSleeping() = sleeping
        override fun appStores(): Set<String> = stores
    }

    private fun take(oem: OemSource = FixedOem(Reading.NotPresent, Reading.NotPresent)) =
        AndroidSnapshotter(context, { now }, oem, listener).take()

    private fun setDns(mode: String?, host: String? = null) {
        Settings.Global.putString(context.contentResolver, "private_dns_mode", mode)
        Settings.Global.putString(context.contentResolver, "private_dns_specifier", host)
    }

    // The probe the snapshotter resolves: an ordinary https link, never opened.
    private val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)

    private fun resolvesTo(packageName: String) {
        val info = ResolveInfo().apply { activityInfo = ActivityInfo().apply { this.packageName = packageName; name = "Main" } }
        shadowOf(context.packageManager).addResolveInfoForIntent(probe, info)
    }

    @Test fun anAppsOwnCategoryIsReadAndNoneIsMinusOne() { // finding #92
        fun install(pkg: String, category: Int) = shadowOf(context.packageManager).installPackage(
            android.content.pm.PackageInfo().apply {
                packageName = pkg
                applicationInfo = ApplicationInfo().apply { packageName = pkg; this.category = category }
            },
        )
        install("com.example.pdf", ApplicationInfo.CATEGORY_IMAGE)
        install("com.example.plain", ApplicationInfo.CATEGORY_UNDEFINED)
        val apps = take().apps.associateBy { it.packageName }
        assertEquals(ApplicationInfo.CATEGORY_IMAGE, apps.getValue("com.example.pdf").category)
        assertEquals(-1, apps.getValue("com.example.plain").category)
    }

    @Test fun anAbsentPrivateDnsKeyIsAutomaticNotOff() { // findings #14: a never-set phone has no key at all
        assertEquals(PrivateDns.Automatic, take().privateDns)
    }

    @Test fun privateDnsModesAreReadAsTheyAre() {
        setDns("hostname", "Security.Cloudflare-DNS.com")
        assertEquals(PrivateDns.Hostname("security.cloudflare-dns.com"), take().privateDns)
        setDns("off")
        assertEquals(PrivateDns.Off, take().privateDns)
        setDns("opportunistic")
        assertEquals(PrivateDns.Automatic, take().privateDns)
    }

    @Test fun theDefaultBrowserIsTheAppThatResolvesAWebLink() {
        assertNull(take().browser)
        resolvesTo("com.android.chrome")
        assertEquals("com.android.chrome", take().browser)
    }

    @Test fun theSystemChooserMeansNoDefaultBrowser() {
        resolvesTo("android")
        assertNull(take().browser)
    }

    @Test fun listenerAccessIsReadFromTheSystem() {
        assertFalse(take().listenerEnabled)
        shadowOf(notifications).setNotificationListenerAccessGranted(listener, true)
        assertTrue(take().listenerEnabled)
    }

    @Test fun bannersCanBePostedByDefaultAndWithAnOrdinaryChannel() {
        assertTrue(take().canPostNotifications)
        notifications.createNotificationChannel(NotificationChannel(NotificationPoster.CHANNEL_NEEDS_TAP, "x", NotificationManager.IMPORTANCE_HIGH))
        assertTrue(take().canPostNotifications)
    }

    @Test fun bannersAreBlockedWhenTheAppIsSwitchedOff() {
        shadowOf(notifications).setNotificationsEnabled(false)
        assertFalse(take().canPostNotifications)
    }

    // Only the user can switch a channel off and back on; an app cannot raise it again, so one scenario each.
    @Test fun bannersAreBlockedWhenOwnerSwitchedOffTheNeedsTapChannel() {
        notifications.createNotificationChannel(NotificationChannel(NotificationPoster.CHANNEL_NEEDS_TAP, "x", NotificationManager.IMPORTANCE_NONE))
        assertFalse(take().canPostNotifications)
    }

    @Test fun theTimeIsTheClocksAndOemReadingsPassThroughUnchanged() {
        val snapshot = take(FixedOem(Reading.Unreadable, Reading.Present(true)))
        assertEquals(now, snapshot.takenAt)
        assertEquals(Reading.Unreadable, snapshot.installBlocker)
        assertEquals(Reading.Present(true), snapshot.neverSleeping)
    }

    // Special access, as the emulator's settings strings look (package/Class, colon separated).

    @Test fun appsWithNotificationAccessAreReadWithoutAegidaItself() {
        Settings.Secure.putString(
            context.contentResolver, "enabled_notification_listeners",
            "${context.packageName}/com.aegida.app.AegidaListenerService:org.other.app/org.other.Listener",
        )
        assertEquals(setOf("org.other.app"), take().notificationListeners)
    }

    @Test fun appsWithScreenControlAreReadAndNoneMeansEmpty() {
        assertEquals(emptySet<String>(), take().accessibilityApps)
        val services = "org.helper/org.helper.Service:org.other/org.other.Svc"
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, services)
        assertEquals(setOf("org.helper", "org.other"), take().accessibilityApps)
    }

    @Test fun deviceAdminsAreReadByPackage() {
        val admin = android.content.ComponentName("org.mdm", "org.mdm.Admin")
        shadowOf(context.getSystemService(android.app.admin.DevicePolicyManager::class.java)).setActiveAdmin(admin)
        assertEquals(setOf("org.mdm"), take().deviceAdmins)
    }

    // The default text app and the default assistant are read the way the default browser is: by who a
    // text link / an assist request would open, never launched. Android 17 no longer shows the setting (findings #70).
    private fun resolves(intent: Intent, packageName: String) {
        val info = ResolveInfo().apply { activityInfo = ActivityInfo().apply { this.packageName = packageName; name = "Main" } }
        shadowOf(context.packageManager).addResolveInfoForIntent(intent, info)
    }

    @Test fun theDefaultTextAppIsReadFromWhoWouldOpenATextLink() {
        assertEquals(null, take().smsApp)
        resolves(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:5550100")), "org.texts")
        assertEquals("org.texts", take().smsApp)
    }

    @Test fun theDefaultAssistantIsReadFromWhoWouldAnswerAnAssistRequest() {
        assertEquals(null, take().assistant)
        resolves(Intent(Intent.ACTION_ASSIST), "org.assistant")
        assertEquals("org.assistant", take().assistant)
    }

    // Installed apps

    private fun install(pkg: String, label: String, installer: String?, system: Boolean = false, signedWith: ByteArray? = null) {
        val info = android.content.pm.PackageInfo().apply {
            packageName = pkg
            if (signedWith != null) {
                signingInfo = android.content.pm.SigningInfo().also { shadowOf(it).setSignatures(arrayOf(android.content.pm.Signature(signedWith))) }
            }
            applicationInfo = android.content.pm.ApplicationInfo().apply {
                packageName = pkg
                nonLocalizedLabel = label
                flags = if (system) android.content.pm.ApplicationInfo.FLAG_SYSTEM else 0
            }
        }
        shadowOf(context.packageManager).installPackage(info)
        @Suppress("DEPRECATION") context.packageManager.setInstallerPackageName(pkg, installer)
    }

    /**
     * WN38 (#169): an app with no android:label. Android's own loadLabel (run here by Robolectric) then hands back the Application class
     * name, or the package name; neither is a name, so the label is empty and the owner reads "Aegida can't read this app's name."
     */
    @Test fun anAppWithNoNameOfItsOwnHasNoLabelRatherThanItsPackageOrClassName() {
        fun installNameless(pkg: String, applicationClass: String?) = shadowOf(context.packageManager).installPackage(
            android.content.pm.PackageInfo().apply {
                packageName = pkg
                applicationInfo = ApplicationInfo().apply { packageName = pkg; name = applicationClass; className = applicationClass }
            },
        )
        installNameless("com.evil.app", null)
        installNameless("com.evil.other", "com.evil.other.App")
        val pm = context.packageManager
        assertEquals("com.evil.app", pm.getApplicationLabel(pm.getApplicationInfo("com.evil.app", 0)).toString())
        assertEquals("com.evil.other.App", pm.getApplicationLabel(pm.getApplicationInfo("com.evil.other", 0)).toString())
        val apps = take().apps.associateBy { it.packageName }
        assertEquals("", apps.getValue("com.evil.app").label)
        assertEquals("", apps.getValue("com.evil.other").label)
    }

    @Test fun appsAreReadWithTheirNameTheStoreThatInstalledThemAndWhetherTheyShippedWithThePhone() {
        install("org.play.app", "Play App", "com.android.vending")
        install("org.byhand.app", "By Hand", null)
        install("com.android.phone", "Phone", null, system = true)
        val apps = take().apps.associateBy { it.packageName }
        assertEquals("Play App", apps.getValue("org.play.app").label)
        assertTrue(apps.getValue("org.play.app").fromPlayStore)
        assertFalse(apps.getValue("org.byhand.app").fromPlayStore)
        assertNull(apps.getValue("org.byhand.app").installer)
        assertTrue(apps.getValue("com.android.phone").isSystem)
        assertFalse(apps.getValue("org.byhand.app").isSystem)
    }

    // The phone maker's own store (findings #106, scenarios GS*). Data shape from the Galaxy S25 dump in build/samsung-rtl.

    private val galaxyStore = "com.sec.android.app.samsungapps"

    private fun installFromStore(pkg: String, installing: String?, initiating: String?, system: Boolean = false, enabled: Boolean = true) {
        val info = android.content.pm.PackageInfo().apply {
            packageName = pkg
            applicationInfo = android.content.pm.ApplicationInfo().apply {
                packageName = pkg
                nonLocalizedLabel = pkg
                this.enabled = enabled
                flags = if (system) android.content.pm.ApplicationInfo.FLAG_SYSTEM else 0
            }
        }
        shadowOf(context.packageManager).installPackage(info)
        // This Robolectric shadow does not run on API 36.
        if (android.os.Build.VERSION.SDK_INT >= 30) shadowOf(context.packageManager).setInstallSourceInfo(pkg, initiating, installing)
    }

    private fun takeWithGalaxyStore() = take(FixedOem(Reading.NotPresent, Reading.NotPresent, setOf(galaxyStore)))

    @Test @org.robolectric.annotation.Config(sdk = [34]) fun anAppThatTheMakersStoreInstalledAndAskedForIsFromThatStore() { // GS1
        installFromStore(galaxyStore, null, null, system = true)
        installFromStore("org.temu", galaxyStore, galaxyStore)
        val snapshot = takeWithGalaxyStore()
        assertEquals(setOf(galaxyStore), snapshot.makerStores)
        assertTrue(snapshot.apps.single { it.packageName == "org.temu" }.fromStoreIn(snapshot.makerStores))
    }

    @Test @org.robolectric.annotation.Config(sdk = [34]) fun aStoreThatIsNotASystemAppIsNotTrusted() { // GS6: an impostor with the store's name
        installFromStore(galaxyStore, "com.android.vending", "com.android.vending", system = false)
        installFromStore("org.pushed", galaxyStore, galaxyStore)
        val snapshot = takeWithGalaxyStore()
        assertEquals(emptySet<String>(), snapshot.makerStores)
        assertFalse(snapshot.apps.single { it.packageName == "org.pushed" }.fromStoreIn(snapshot.makerStores))
    }

    // GS6: installer is the store, initiating is the preloaded app
    @Test @org.robolectric.annotation.Config(sdk = [34]) fun aPreloadedInstallerThatOnlyWritesTheStoreNameIsNotTheStore() {
        installFromStore(galaxyStore, null, null, system = true)
        installFromStore("org.pushed", galaxyStore, "com.preloaded.installer")
        val snapshot = takeWithGalaxyStore()
        assertFalse(snapshot.apps.single { it.packageName == "org.pushed" }.fromStoreIn(snapshot.makerStores))
    }

    @Test @org.robolectric.annotation.Config(sdk = [34]) fun anAppOpenedFromAFileOnAPhoneWithTheStoreIsNotFromTheStore() { // GS7
        installFromStore(galaxyStore, null, null, system = true)
        installFromStore("org.file", null, null)
        installFromStore("org.pkg", "com.google.android.packageinstaller", "com.sec.android.app.myfiles")
        val snapshot = takeWithGalaxyStore()
        assertFalse(snapshot.apps.single { it.packageName == "org.file" }.fromStoreIn(snapshot.makerStores))
        assertFalse(snapshot.apps.single { it.packageName == "org.pkg" }.fromStoreIn(snapshot.makerStores))
    }

    // GS10 (Pixel: oem names none), GS11/GS20 (store removed: not in the list)
    @Test @org.robolectric.annotation.Config(sdk = [34]) fun onAPhoneWithoutTheStoreInItsListNothingIsTrusted() {
        installFromStore("org.temu", galaxyStore, galaxyStore)
        assertEquals(emptySet<String>(), take().makerStores)
        assertEquals(emptySet<String>(), takeWithGalaxyStore().makerStores)
    }

    @Test @org.robolectric.annotation.Config(sdk = [34]) fun aDisabledStoreThatIsStillASystemAppKeepsItsAppsTrusted() { // GS20
        installFromStore(galaxyStore, null, null, system = true, enabled = false)
        assertEquals(setOf(galaxyStore), takeWithGalaxyStore().makerStores)
    }

    @Test @org.robolectric.annotation.Config(sdk = [29]) fun beforeAndroid11ThereIsNoInitiatingInstallerSoTheAppIsAQuestion() { // GS19
        installFromStore(galaxyStore, null, null, system = true)
        @Suppress("DEPRECATION") run {
            installFromStore("org.temu", null, null)
            context.packageManager.setInstallerPackageName("org.temu", galaxyStore)
        }
        val snapshot = takeWithGalaxyStore()
        val app = snapshot.apps.single { it.packageName == "org.temu" }
        assertEquals(galaxyStore, app.installer)
        assertNull(app.initiatingInstaller)
        assertFalse(app.fromStoreIn(snapshot.makerStores))
    }

    @Test fun theListIsKeptForAFewMinutesBecauseEveryNotificationRunsACheck() {
        var at = now
        val snapshotter = AndroidSnapshotter(context, { at }, FixedOem(Reading.NotPresent, Reading.NotPresent), listener)
        install("org.first.app", "First", "com.android.vending")
        assertTrue(snapshotter.take().apps.any { it.packageName == "org.first.app" })
        install("org.second.app", "Second", null)
        at = at.plus(Duration.ofMinutes(2))
        assertFalse(snapshotter.take().apps.any { it.packageName == "org.second.app" })
        at = at.plus(Duration.ofMinutes(4))
        assertTrue(snapshotter.take().apps.any { it.packageName == "org.second.app" })
    }

    @Test fun aClockSetBackwardsDoesNotFreezeTheList() { // R14: wall clock set ahead, then corrected
        var at = now.plus(Duration.ofDays(2))
        val snapshotter = AndroidSnapshotter(context, { at }, FixedOem(Reading.NotPresent, Reading.NotPresent), listener)
        install("org.first.app", "First", "com.android.vending")
        assertTrue(snapshotter.take().apps.any { it.packageName == "org.first.app" })
        install("org.second.app", "Second", null)
        at = now // two days earlier than when the list was read
        assertTrue(snapshotter.take().apps.any { it.packageName == "org.second.app" })
    }

    @Test fun anAppsSigningCertificateIsReadAsTheSha1HexEchapListsAndSystemAppsAreNotChecked() {
        val certificate = ByteArray(40) { (it * 7).toByte() }
        val expected = java.security.MessageDigest.getInstance("SHA-1").digest(certificate).joinToString("") { "%02x".format(it) }
        install("org.signed.app", "Signed", null, signedWith = certificate)
        install("com.android.phone", "Phone", null, system = true, signedWith = certificate)
        val apps = take().apps.associateBy { it.packageName }
        assertEquals(setOf(expected), apps.getValue("org.signed.app").certSha1s)
        assertEquals(emptySet<String>(), apps.getValue("com.android.phone").certSha1s)
    }

    // #164: a warning names its app and says where it came from and when, even when the app arrived inside the kept list's 5 minutes.

    private fun installAt(pkg: String, label: String, firstInstalled: Instant, system: Boolean = false) {
        val info = android.content.pm.PackageInfo().apply {
            packageName = pkg
            firstInstallTime = firstInstalled.toEpochMilli()
            applicationInfo = android.content.pm.ApplicationInfo().apply {
                packageName = pkg
                nonLocalizedLabel = label
                flags = if (system) android.content.pm.ApplicationInfo.FLAG_SYSTEM else 0
            }
        }
        shadowOf(context.packageManager).installPackage(info)
    }

    @Test fun whenAnAppWasFirstInstalledIsReadAndAnAppThatCameWithThePhoneHasNone() { // WN29
        val added = now.minus(Duration.ofDays(3))
        installAt("org.byhand.app", "By Hand", added)
        installAt("com.android.phone", "Phone", now.minus(Duration.ofDays(900)), system = true)
        val apps = take().apps.associateBy { it.packageName }
        assertEquals(added.toEpochMilli(), apps.getValue("org.byhand.app").firstInstalled?.toEpochMilli())
        assertNull(apps.getValue("com.android.phone").firstInstalled)
    }

    private fun sharedSnapshotter(clock: () -> Instant) =
        AndroidSnapshotter(context, { clock() }, FixedOem(Reading.NotPresent, Reading.NotPresent), listener)

    private fun giveNotificationAccess(pkg: String) =
        Settings.Secure.putString(context.contentResolver, "enabled_notification_listeners", "$pkg/$pkg.Listener")

    @Test fun anAppInstalledAMinuteAgoThatGetsNotificationAccessIsNamedAtOnce() { // WN30
        var at = now
        val snapshotter = sharedSnapshotter { at }
        snapshotter.take() // the kept list, read before the app arrived
        installAt("org.watch.app", "Pixel Watch", now)
        giveNotificationAccess("org.watch.app")
        at = at.plus(Duration.ofMinutes(1))
        val app = snapshotter.take().apps.singleOrNull { it.packageName == "org.watch.app" }
        assertEquals("Pixel Watch", app?.label)
        assertEquals(now.toEpochMilli(), app?.firstInstalled?.toEpochMilli())
    }

    @Test fun anAppInstalledAMinuteAgoThatBecomesTheBrowserIsNamedAtOnce() { // WN30, the default-app kinds
        var at = now
        val snapshotter = sharedSnapshotter { at }
        snapshotter.take()
        installAt("org.mozilla.firefox", "Firefox", now)
        resolvesTo("org.mozilla.firefox")
        at = at.plus(Duration.ofMinutes(1))
        assertEquals("Firefox", snapshotter.take().apps.singleOrNull { it.packageName == "org.mozilla.firefox" }?.label)
    }

    @Test fun theAppThatJustPostedANoticeIsLookedUpWhenTheKeptListIsOlderThanIt() { // WN30, notices (noisy app, scam link)
        var at = now
        val snapshotter = sharedSnapshotter { at }
        snapshotter.take()
        installAt("org.new.shop", "Shop", now)
        at = at.plus(Duration.ofMinutes(1))
        assertEquals("Shop", snapshotter.take(setOf("org.new.shop")).apps.singleOrNull { it.packageName == "org.new.shop" }?.label)
    }

    @Test fun aPackageThatIsNotThereIsAskedForOnceInFiveMinutesNotEveryCheck() { // WN31: uninstalled, or in the work profile
        var at = now
        val snapshotter = sharedSnapshotter { at }
        giveNotificationAccess("org.gone.app")
        assertFalse(snapshotter.take().apps.any { it.packageName == "org.gone.app" })
        // Installed after the miss: a check within the five minutes does not ask again, so it is still not seen.
        installAt("org.gone.app", "Gone", now)
        at = at.plus(Duration.ofMinutes(2))
        assertFalse(snapshotter.take().apps.any { it.packageName == "org.gone.app" })
        at = at.plus(Duration.ofMinutes(4))
        assertEquals("Gone", snapshotter.take().apps.singleOrNull { it.packageName == "org.gone.app" }?.label)
    }

    @Test fun aMissRememberedUnderAClockThatWasAheadIsAskedAgain() { // WN31 + R14
        val ahead = now.plus(Duration.ofDays(2))
        var at = ahead
        val snapshotter = sharedSnapshotter { at }
        giveNotificationAccess("org.late.app")
        snapshotter.take()
        installAt("org.late.app", "Late", now)
        at = ahead.plus(Duration.ofMinutes(1))
        assertFalse(snapshotter.take().apps.any { it.packageName == "org.late.app" }) // inside the miss's five minutes: not asked
        at = ahead.minus(Duration.ofMinutes(1)) // the clock set back a little: the miss time is in the future, so it no longer counts
        assertEquals("Late", snapshotter.take().apps.singleOrNull { it.packageName == "org.late.app" }?.label)
    }
}
