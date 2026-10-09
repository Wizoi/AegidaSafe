package com.aegida.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aegida.app.ui.PictureBanner
import com.aegida.core.model.SafetyPicture
import com.aegida.core.model.Setup
import com.aegida.core.store.SetupStore
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
    private class Store(var value: Setup) : SetupStore {
        override suspend fun setup() = value
        override suspend fun save(setup: Setup) { value = setup }
    }

    @Test fun noPictureChosenYetMeansNoBannerArt() {
        assertNull(PictureBanner(context, Store(Setup.NotDone)).bitmap())
    }

    @Test fun herPictureIsDrawnOnceAndRedrawnWhenSheChangesIt() {
        val store = Store(Setup(null, emptySet(), pictureId = SafetyPicture.Owl.id))
        val banner = PictureBanner(context, store)
        val owl = banner.bitmap()
        assertNotNull(owl)
        assertSame(owl, banner.bitmap())
        store.value = store.value.copy(pictureId = SafetyPicture.Fish.id)
        val fish = banner.bitmap()!!
        assertNotEquals(owl!!.getPixel(96, 8), fish.getPixel(96, 8))
    }
}
