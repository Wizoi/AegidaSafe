package com.aegida.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aegida.app.ui.AppRoot
import com.aegida.app.ui.theme.AegidaTheme
import com.aegida.core.model.FindingKey
import com.aegida.platform.notify.NotificationPoster
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** The warning a tapped "Was that you?" banner asked for, until its question page has been opened (#164). */
    private var asked by mutableStateOf<FindingKey?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ProtectionService.start(this)
        if (savedInstanceState == null) asked = NotificationPoster.questionFrom(intent)
        setContent { AegidaTheme { AppRoot(asked = asked, onAsked = { asked = null }) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        asked = NotificationPoster.questionFrom(intent)
    }
}
