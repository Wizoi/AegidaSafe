package com.aegida.app.ui

import com.aegida.core.model.Finding

/** A page opened from Home or Settings; back returns to the tab. */
internal sealed interface Page {
    data object None : Page
    data object FilterGuide : Page
    data object ChangePicture : Page
    data object ChangeGuardian : Page
    data class Advice(val finding: Finding) : Page
    data class NoisyApp(val finding: Finding) : Page
    data class Confirm(val finding: Finding) : Page

    /** She said Yes to a website that sent fake warnings; it stays hidden and she is told so (findings #149). */
    data object KeptHidden : Page
}
