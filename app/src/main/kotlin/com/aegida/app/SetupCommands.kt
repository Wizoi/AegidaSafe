package com.aegida.app

import com.aegida.alerts.GuardianAlerts
import com.aegida.alerts.AboutSaved
import com.aegida.alerts.GuardianChange
import com.aegida.alerts.GuardianDetails
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
    private val guardianDetails: GuardianDetails,
    private val banner: PictureBanner,
) {
    /**
     * The About step. Names and phone go through [GuardianDetails] (nothing is written here); a new email for a guardian who already has a
     * channel makes a new guardian, as "Pick a different guardian" does (K9, T5.18).
     */
    suspend fun saveAbout(name: String, guardianName: String, email: String, phone: String) {
        if (guardianDetails.saveAbout(name, guardianName, email, phone) == AboutSaved.NewGuardian) {
            quietly { guardianChange.change(guardianName, email, phone) }
        }
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

    /**
     * Changes the guardian (the owner decides). Returns the new settings to build the invite from, or null when the email is the same
     * as before: the same person, so names and phone were fixed and there is no invitation to send (K9).
     */
    suspend fun changeGuardian(name: String, email: String, phone: String): Guardians? {
        val changed = quietly { guardianChange.change(name, email, phone) }
        return if (changed?.samePerson == true) null else guardianSettings.current()
    }

    suspend fun setAlerts(on: Boolean) {
        quietly {
            guardianAlerts.setAlertsOn(on)
            if (on) guardianDetails.sendHeldPhoneNote() // a phone change made while alerts were off is told now (K8b)
        }
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
