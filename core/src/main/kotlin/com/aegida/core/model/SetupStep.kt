package com.aegida.core.model

/** The first-run steps in the order the owner does them (product brief, "First run"). */
enum class SetupStep {
    About,
    Picture,
    Invite,
    Alerts,
    Access,
    Filter,

    /** Samsung Auto Blocker. Shown only on a phone where the maker feature may exist (OemSource). */
    Blocker,
    Finish,
    ;

    /** The step after this one, skipping the Samsung step when the phone cannot have it. */
    fun next(blockerRelevant: Boolean): SetupStep? {
        val following = entries.getOrNull(ordinal + 1) ?: return null
        return if (following == Blocker && !blockerRelevant) Finish else following
    }

    companion object {
        /** Where to resume: the first step she has not finished, or null when setup is complete. */
        fun resumeAt(setup: Setup): SetupStep? = entries.getOrNull(setup.stepsDone)
    }
}
