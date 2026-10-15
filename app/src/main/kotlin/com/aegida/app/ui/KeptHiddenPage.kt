package com.aegida.app.ui

import androidx.compose.runtime.Composable
import com.aegida.core.text.Texts

/** After her Yes to a website that sent fake warnings: it is not allowed, and she is told why in one sentence (findings #149). */
@Composable
internal fun KeptHiddenPage(pictureId: String?, onDone: () -> Unit) {
    AegidaScreen(pictureId) {
        Headline(Texts.Home.SITE_KEPT_HIDDEN)
        BigButton(Texts.Common.DONE, onDone)
    }
}
