package com.aegida.app

import com.aegida.testkit.FakeSetupStore
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aegida.app.ui.PictureBanner
import com.aegida.core.model.SafetyPicture
import com.aegida.core.model.Setup
import com.aegida.core.store.SetupStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class PictureBannerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    // Unconfined: the first load from the store runs at once, as it would have finished long before a banner in real use.
    private fun banner(store: FakeSetupStore) = PictureBanner(context, store, CoroutineScope(Dispatchers.Unconfined))

    @Test fun noPictureChosenYetMeansNoBannerArt() {
        assertNull(banner(FakeSetupStore(Setup.NotDone)).bitmap())
    }

    @Test fun herStoredPictureIsLoadedOnceAtStart() {
        assertNotNull(banner(FakeSetupStore(Setup(null, emptySet(), pictureId = SafetyPicture.Owl.id))).bitmap())
    }

    @Test fun herPictureIsDrawnOnceAndRedrawnWhenSheChangesIt() {
        val store = FakeSetupStore(Setup(null, emptySet(), pictureId = SafetyPicture.Owl.id))
        val banner = banner(store)
        val owl = banner.bitmap()
        assertNotNull(owl)
        assertSame(owl, banner.bitmap())
        banner.set(SafetyPicture.Fish.id)
        val fish = banner.bitmap()!!
        assertNotEquals(owl!!.getPixel(96, 8), fish.getPixel(96, 8))
    }

    @Test fun bitmapNeverReadsTheStore() {
        val store = object : SetupStore {
            var reads = 0
            override suspend fun setup(): Setup { reads++; return Setup(null, emptySet(), pictureId = SafetyPicture.Owl.id) }
            override suspend fun save(setup: Setup) = Unit
        }
        val banner = PictureBanner(context, store, CoroutineScope(Dispatchers.Unconfined))
        val atStart = store.reads
        repeat(5) { banner.bitmap() }
        assertEquals(atStart, store.reads)
    }

    @Test fun aPickMadeBeforeTheFirstLoadFinishesIsNotOverwrittenByTheOldStoredOne() {
        val gate = CompletableDeferred<Unit>()
        val store = object : SetupStore {
            override suspend fun setup(): Setup { gate.await(); return Setup(null, emptySet(), pictureId = SafetyPicture.Owl.id) }
            override suspend fun save(setup: Setup) = Unit
        }
        val banner = PictureBanner(context, store, CoroutineScope(Dispatchers.Unconfined))
        banner.set(SafetyPicture.Fish.id)
        val fish = banner.bitmap()
        gate.complete(Unit)
        assertSame(fish, banner.bitmap())
    }

    /** R5: posting a banner may happen on the main thread, so it must never block on the database. */
    @Test fun theBannerSourceDoesNotUseRunBlocking() {
        val source = java.io.File("src/main/kotlin/com/aegida/app/ui/PictureBanner.kt").readText()
        assertFalse(source.contains("runBlocking"))
    }
}
