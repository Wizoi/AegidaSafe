package com.aegida.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R5 source-level guards. The ViewModel is made by Hilt and a check needs the whole phone, so a test that
 * runs it and looks at the thread is out of reach on the JVM; these pin the three facts that keep the app-list
 * scan off the main thread and cached once.
 */
class OffMainThreadTest {
    private fun source(path: String) = File("src/main/kotlin/com/aegida/app/$path").readText()

    @Test fun thereIsOneSnapshotterSoOneAppListCache() {
        val module = source("di/AppModule.kt")
        assertTrue(Regex("""@Provides\s+@Singleton\s+fun snapshotter\(""").containsMatchIn(module))
    }

    @Test fun everyViewModelLaunchAndSuspendActionRunsOnTheWorkDispatcher() {
        val vm = source("MainViewModel.kt")
        assertEquals("a bare viewModelScope.launch would run on Main", 0, Regex("""viewModelScope\.launch\s*\{""").findAll(vm).count())
        assertTrue("the ViewModel should launch on the work dispatcher", vm.contains("viewModelScope.launch(work)"))
        assertTrue(vm.contains("Dispatchers.Default"))
    }

    @Test fun oneBannerPictureIsSharedBetweenThePosterAndTheViewModel() {
        assertTrue(Regex("""@Provides\s+@Singleton\s+fun pictureBanner\(""").containsMatchIn(source("di/AppModule.kt")))
        assertTrue(source("SetupCommands.kt").contains("banner.set("))
    }
}
