package com.aegida.app

import com.aegida.app.ui.PictureBanner
import com.aegida.core.model.Snapshotter
import com.aegida.platform.notify.NotificationPoster
import com.aegida.platform.snapshot.AndroidSnapshotter
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject
import javax.inject.Provider

/**
 * The real Hilt graph, built (findings #144 d): a missing binding after a module split fails here, and the app-list snapshotter and
 * the banner picture are each one instance however many times they are asked for (R5: one 5-minute cache, one picture).
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class HiltGraphTest {
    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var snapshotters: Provider<AndroidSnapshotter>
    @Inject lateinit var banners: Provider<PictureBanner>
    @Inject lateinit var portSnapshotter: Provider<Snapshotter>
    @Inject lateinit var poster: NotificationPoster

    @Test fun theSnapshotterIsOneInstanceNoMatterHowOftenItIsInjected() {
        hilt.inject()
        assertSame(snapshotters.get(), snapshotters.get())
    }

    @Test fun theBannerIsOneInstanceNoMatterHowOftenItIsInjected() {
        hilt.inject()
        assertSame(banners.get(), banners.get())
    }

    @Test fun theWholeGraphBuildsIncludingTheBuildTypeBindings() {
        hilt.inject()
        assertNotNull(portSnapshotter.get())
        assertNotNull(poster)
    }
}
