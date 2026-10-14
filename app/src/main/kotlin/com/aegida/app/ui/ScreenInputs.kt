package com.aegida.app.ui

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** The moment a screen is drawn for: what "today" is, in which time zone and language. */
data class ShownAt(val now: Instant, val zone: ZoneId, val locale: Locale)

/** The four ways out of the noisy-app page. */
data class NoisyAppActions(val onTurnOff: () -> Unit, val onRemove: () -> Unit, val onFine: () -> Unit, val onGotIt: () -> Unit)
