package com.aegida.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aegida.core.model.SafetyPicture
import com.aegida.core.text.Texts

/**
 * "What you will see": the last setup step, and the same page opened from the top of My settings (T2). One page, two entry points; [onDone]
 * is the caller's: setup finishes there (under "All set."), My settings only goes back. It shows her picture, says nothing it cannot keep (no notice promised
 * when notices are off, no picture when none can be drawn) and sends or posts nothing.
 */
@Composable
internal fun WhatYouSeePage(pictureId: String?, noticesOn: Boolean, title: String = Texts.Setup.SEE_TITLE, onDone: () -> Unit) {
    val picture = SafetyPicture.fromId(pictureId)
    AegidaScreen(pictureId, pinned = { BigButton(Texts.Common.DONE, onDone) }) {
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        if (picture != null) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PictureTile(picture, 96.dp, Modifier.semantics { contentDescription = Texts.Common.YOUR_PICTURE.format(picture.label) })
            }
        }
        Texts.Setup.seeLines(noticesOn, picture != null).forEach { Body(it, centered = false) }
    }
}
