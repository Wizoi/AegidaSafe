package com.aegida.app

import com.aegida.alerts.GuardianAlerts
import com.aegida.alerts.GuardianChange
import com.aegida.app.ui.PictureBanner
import com.aegida.core.alert.GuardianSettings
import com.aegida.core.alert.Guardians
import com.aegida.core.model.AutoBlockerAnswer
import com.aegida.core.model.SafetyPicture
import com.aegida.core.model.SetupStep
import com.aegida.core.model.WebsiteFilter
import com.aegida.core.net.NtfyTopic
import com.aegida.core.store.SetupStore
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/** A failed check or send must not break the screen: it still shows what is stored, and HealthDetector reports a check that keeps failing. */
internal suspend fun <T> quietly(block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (ignored: Exception) {
    // Deliberately no reason shown: the screen is reloaded from what is stored.
    null
}

/** First-run setup and My settings: each call writes through the store. MainViewModel runs them off the main thread and reloads after. */
class SetupCommands @Inject constructor(
    private val setupStore: SetupStore,
    private val guardianSettings: GuardianSettings,
    private val guardianAlerts: GuardianAlerts,
    private val guardianChange: GuardianChange,
    private val banner: PictureBanner,
) {
    suspend fun saveAbout(name: String, guardianName: String, email: String, phone: String) {
        val current = guardianSettings.current()
        guardianSettings.save(
            current.copy(
                ownerName = name.trim(),
                firstGuardianName = guardianName.trim(),
                firstGuardianEmail = email.trim(),
                firstGuardianPhone = phone.trim(),
            ),
        )
    }

    suspend fun savePicture(picture: SafetyPicture) {
        setupStore.save(setupStore.setup().copy(pictureId = picture.id))
        banner.set(picture.id) // banners posted from now on carry the new picture, without reading the database
    }

    /** Makes her private alert topic the first time it is needed and returns the guardian settings to build the email from. */
    suspend fun prepareInvite(): Guardians {
        val current = guardianSettings.current()
        if (current.firstTopic != null) return current
        val made = current.copy(firstTopic = NtfyTopic.generate(current.ownerName))
        guardianSettings.save(made)
        return made
    }

    /** Changes the guardian (the owner decides; nothing is sent to the old one). Returns the new settings to build the invite from. */
    suspend fun changeGuardian(name: String, email: String, phone: String): Guardians {
        quietly { guardianChange.change(name, email, phone) }
        return guardianSettings.current()
    }

    suspend fun setAlerts(on: Boolean) {
        quietly { guardianAlerts.setAlertsOn(on) }
    }

    /** With "Send the invitation again": tell the guardian once more that Aegida is set up (findings #95). */
    suspend fun resendWelcome() {
        quietly { guardianAlerts.resendWelcome() }
    }

    suspend fun answerBlocker(answer: AutoBlockerAnswer) {
        setupStore.save(setupStore.setup().copy(autoBlocker = answer))
    }

    /** Remembers the filter she set up, but only once Aegida has read it back as on. */
    suspend fun rememberFilterIfOn(filterOn: Boolean) {
        if (filterOn) setupStore.save(setupStore.setup().copy(websiteFilterHost = WebsiteFilter.HOST))
    }

    /** Marks [step] finished. The Samsung step is passed over on a phone that cannot have it. */
    suspend fun finishStep(step: SetupStep, blockerRelevant: Boolean) {
        val setup = setupStore.setup()
        val next = step.next(blockerRelevant)
        val stepsDone = if (next == null) SetupStep.entries.size else maxOf(setup.stepsDone, next.ordinal)
        setupStore.save(setup.copy(stepsDone = stepsDone))
    }
}
